package com.samtech.avlog.data

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.samtech.avlog.core.Clock
import com.samtech.avlog.core.Formatters
import com.samtech.avlog.core.utcToday
import com.samtech.avlog.fdtl.FdtlCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

sealed interface ExportResult {
    data object NoRecords : ExportResult
    data class Success(val uri: Uri) : ExportResult
    data object Failed : ExportResult
}

/** P7.1: CSV built and written off the main thread into cacheDir/exports only. */
class CsvExporter(
    private val context: Context,
    private val repository: DutyRepository,
    private val clock: Clock,
) {
    suspend fun export(): ExportResult = withContext(Dispatchers.IO) {
        try {
            val logs = repository.getDayLogs()
            if (logs.none { it.sorties.isNotEmpty() }) return@withContext ExportResult.NoRecords
            val dir = File(context.cacheDir, EXPORT_DIR)
            dir.deleteRecursively() // clear old exports
            dir.mkdirs()
            val file = File(dir, "AvLog_${Formatters.isoDate(clock.utcToday())}.csv")
            file.writeText(build(logs))
            ExportResult.Success(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file))
        } catch (e: Exception) {
            Log.e("CsvExporter", "Export failed", e)
            ExportResult.Failed
        }
    }

    companion object {
        const val EXPORT_DIR = "exports"

        val HEADER = listOf(
            "Date (UTC)", "Sortie", "Block Off (UTC)", "Block On (UTC)", "Duration (H:MM)",
            "Duration (s)", "Day Total (H:MM)", "BA Check (UTC)", "Edited",
        )

        /** Pure: newest day first (as given), sorties in time order. RFC 4180 quoting, CRLF line endings. */
        fun build(logs: List<DayLog>): String = buildString {
            appendRow(HEADER)
            logs.forEach { day ->
                val dayTotal = Formatters.hMm(day.totalSeconds, roundUp = false)
                val ba = day.baCheckTime?.let { Formatters.utcTime(it) } ?: ""
                day.sorties.forEachIndexed { i, s ->
                    val secs = FdtlCalculator.durationSeconds(s.blockOff, s.blockOn)
                    appendRow(
                        listOf(
                            Formatters.isoDate(day.date),
                            (i + 1).toString(),
                            Formatters.utcTime(s.blockOff),
                            s.blockOn?.let { Formatters.utcTime(it) } ?: "OPEN",
                            Formatters.hMm(secs, roundUp = false),
                            secs.toString(),
                            dayTotal,
                            ba,
                            if (s.editedAt != null) "yes" else "no",
                        )
                    )
                }
            }
        }

        private fun StringBuilder.appendRow(fields: List<String>) {
            fields.joinTo(this, ",") { quote(it) }
            append("\r\n")
        }

        fun quote(field: String): String = "\"" + field.replace("\"", "\"\"") + "\""
    }
}
