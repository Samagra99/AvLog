package com.samtech.avlog.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.json.JSONArray
import java.time.LocalDate
import java.util.UUID

/**
 * v4 -> v5: duty_records(sortiesJson, dayTotalSeconds) become duty_days + sorties rows; license expiry
 * millis become ISO dates.
 *
 * Safety rules:
 * - A corrupt sortiesJson row is skipped and logged, never fatal. Its BA check is still migrated.
 * - The old table is kept as `duty_records_v4_backup` for one release (drop it in v6) so nothing is lost
 *   even if parsing is wrong.
 */
object Migration4To5 : Migration(4, 5) {
    private const val TAG = "AvLogMigration"

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `duty_days` (`date` TEXT NOT NULL, `baCheckDone` INTEGER NOT NULL, " +
                "`baCheckTime` INTEGER, PRIMARY KEY(`date`))"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `sorties` (`id` TEXT NOT NULL, `dateUtc` TEXT NOT NULL, " +
                "`blockOff` INTEGER NOT NULL, `blockOn` INTEGER, `editedAt` INTEGER, PRIMARY KEY(`id`))"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sorties_dateUtc` ON `sorties` (`dateUtc`)")

        db.query("SELECT date, baCheckDone, baCheckTime, sortiesJson FROM duty_records").use { c ->
            while (c.moveToNext()) {
                val date = c.getString(0)
                val baDone = c.getInt(1) != 0
                val baTime = if (c.isNull(2)) null else c.getLong(2)
                val json = if (c.isNull(3)) null else c.getString(3)

                val day = try {
                    LocalDate.parse(date)
                } catch (e: Exception) {
                    Log.e(TAG, "Skipping duty_records row with unparseable date '$date'", e)
                    continue
                }

                db.insert("duty_days", SQLiteDatabase.CONFLICT_REPLACE, ContentValues().apply {
                    put("date", day.toString())
                    put("baCheckDone", if (baDone) 1 else 0)
                    if (baTime != null) put("baCheckTime", baTime) else putNull("baCheckTime")
                })

                val sorties = try {
                    parseSorties(json)
                } catch (e: Exception) {
                    Log.e(TAG, "Skipping corrupt sortiesJson for $date (kept in duty_records_v4_backup)", e)
                    continue
                }
                sorties.forEach { s ->
                    db.insert("sorties", SQLiteDatabase.CONFLICT_IGNORE, ContentValues().apply {
                        put("id", s.id)
                        put("dateUtc", day.toString())
                        put("blockOff", s.blockOff)
                        if (s.blockOn != null) put("blockOn", s.blockOn) else putNull("blockOn")
                        putNull("editedAt")
                    })
                }
            }
        }
        db.execSQL("ALTER TABLE `duty_records` RENAME TO `duty_records_v4_backup`")

        // Licenses: the DatePicker stored UTC-midnight millis, so the UTC date is the date the user picked.
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `licenses_new` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                "`expiryDate` TEXT NOT NULL, `warningDays` INTEGER NOT NULL, `lastNotifiedThreshold` INTEGER, " +
                "PRIMARY KEY(`id`))"
        )
        db.execSQL(
            "INSERT INTO `licenses_new` (id, name, expiryDate, warningDays, lastNotifiedThreshold) " +
                "SELECT id, name, date(expiryDate / 1000, 'unixepoch'), warningDays, NULL FROM `licenses`"
        )
        db.execSQL("DROP TABLE `licenses`")
        db.execSQL("ALTER TABLE `licenses_new` RENAME TO `licenses`")
    }

    data class ParsedSortie(val id: String, val blockOff: Long, val blockOn: Long?)

    /** Parses the Gson-written list `[{"id":"..","blockOff":1,"blockOn":2}]`. Throws on a malformed row. */
    fun parseSorties(json: String?): List<ParsedSortie> {
        if (json.isNullOrBlank()) return emptyList()
        val array = JSONArray(json)
        return List(array.length()) { i ->
            val o = array.getJSONObject(i)
            val id = o.optString("id").ifBlank { UUID.randomUUID().toString() }
            val blockOff = o.getLong("blockOff")
            val blockOn = if (o.has("blockOn") && !o.isNull("blockOn")) o.getLong("blockOn") else null
            ParsedSortie(id, blockOff, blockOn)
        }
    }
}

val ALL_MIGRATIONS = arrayOf<Migration>(Migration4To5)
