package com.samtech.avlog.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.samtech.avlog.AppDatabase
import com.samtech.avlog.core.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate

data class BackupData(
    val dutyDays: List<DutyDayEntity>,
    val sorties: List<SortieEntity>,
    val licenses: List<LicenseEntity>,
)

class BackupFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

enum class ImportMode { MERGE, REPLACE }

/** P7.2: versioned JSON backup format. Pure (org.json only) so it is unit-testable. */
object BackupCodec {
    const val FORMAT = "avlog-backup"
    const val VERSION = 1

    fun encode(data: BackupData, exportedAt: Instant): String = JSONObject().apply {
        put("format", FORMAT)
        put("version", VERSION)
        put("exportedAt", exportedAt.toString())
        put("dutyDays", JSONArray().apply {
            data.dutyDays.forEach { d ->
                put(JSONObject().apply {
                    put("date", d.date.toString())
                    put("baCheckDone", d.baCheckDone)
                    put("baCheckTime", d.baCheckTime ?: JSONObject.NULL)
                })
            }
        })
        put("sorties", JSONArray().apply {
            data.sorties.forEach { s ->
                put(JSONObject().apply {
                    put("id", s.id)
                    put("dateUtc", s.dateUtc.toString())
                    put("blockOff", s.blockOff)
                    put("blockOn", s.blockOn ?: JSONObject.NULL)
                    put("editedAt", s.editedAt ?: JSONObject.NULL)
                })
            }
        })
        put("licenses", JSONArray().apply {
            data.licenses.forEach { l ->
                put(JSONObject().apply {
                    put("id", l.id)
                    put("name", l.name)
                    put("expiryDate", l.expiryDate.toString())
                    put("warningDays", l.warningDays)
                    put("lastNotifiedThreshold", l.lastNotifiedThreshold ?: JSONObject.NULL)
                })
            }
        })
    }.toString(2)

    /** Parses and fully validates before anything touches the database. */
    fun decode(text: String): BackupData {
        val root = try { JSONObject(text) } catch (e: Exception) { throw BackupFormatException("Not a valid JSON file", e) }
        if (root.optString("format") != FORMAT) throw BackupFormatException("Not an AvLog backup")
        val version = root.optInt("version", -1)
        if (version < 1 || version > VERSION) throw BackupFormatException("Unsupported backup version $version")

        try {
            val days = root.getJSONArray("dutyDays").objects().map { o ->
                DutyDayEntity(
                    date = LocalDate.parse(o.getString("date")),
                    baCheckDone = o.getBoolean("baCheckDone"),
                    baCheckTime = o.optLongOrNull("baCheckTime"),
                )
            }
            val sorties = root.getJSONArray("sorties").objects().map { o ->
                val off = o.getLong("blockOff")
                val on = o.optLongOrNull("blockOn")
                require(off > 0) { "Invalid Block Off" }
                require(on == null || on > 0) { "Invalid Block On" }
                SortieEntity(
                    id = o.getString("id").also { require(it.isNotBlank()) { "Missing sortie id" } },
                    dateUtc = LocalDate.parse(o.getString("dateUtc")),
                    blockOff = off,
                    blockOn = on,
                    editedAt = o.optLongOrNull("editedAt"),
                )
            }
            require(sorties.count { it.blockOn == null } <= 1) { "More than one open sortie" }
            val licenses = root.getJSONArray("licenses").objects().map { o ->
                LicenseEntity(
                    id = o.getString("id").also { require(it.isNotBlank()) { "Missing license id" } },
                    name = o.getString("name").trim().also { require(it.isNotEmpty()) { "Blank license name" } },
                    expiryDate = LocalDate.parse(o.getString("expiryDate")),
                    warningDays = o.getInt("warningDays").also { require(it in 0..3650) { "Invalid warning days" } },
                    lastNotifiedThreshold = o.optLongOrNull("lastNotifiedThreshold")?.toInt(),
                )
            }
            return BackupData(days, sorties, licenses)
        } catch (e: BackupFormatException) {
            throw e
        } catch (e: Exception) {
            throw BackupFormatException("Backup is damaged: ${e.message}", e)
        }
    }

    private fun JSONArray.objects(): List<JSONObject> = List(length()) { getJSONObject(it) }
    private fun JSONObject.optLongOrNull(key: String): Long? = if (!has(key) || isNull(key)) null else getLong(key)
}

class BackupManager(
    private val context: Context,
    private val db: AppDatabase,
    private val clock: Clock,
) {
    suspend fun snapshot(): BackupData = BackupData(
        dutyDays = db.dutyDao().getDutyDays(),
        sorties = db.dutyDao().getAllSorties(),
        licenses = db.licenseDao().getAll(),
    )

    suspend fun writeTo(uri: Uri): BackupData = withContext(Dispatchers.IO) {
        val data = snapshot()
        val text = BackupCodec.encode(data, clock.now())
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            ?: throw java.io.IOException("Cannot open $uri")
        data
    }

    suspend fun readFrom(uri: Uri): BackupData = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: throw java.io.IOException("Cannot open $uri")
        BackupCodec.decode(text)
    }

    suspend fun import(data: BackupData, mode: ImportMode) {
        db.withTransaction {
            val duty = db.dutyDao()
            val lic = db.licenseDao()
            if (mode == ImportMode.REPLACE) {
                duty.deleteAllSorties()
                duty.deleteAllDutyDays()
                lic.deleteAll()
            }
            duty.upsertDutyDays(data.dutyDays)
            duty.upsertSorties(data.sorties)
            lic.upsertAll(data.licenses)
        }
    }
}
