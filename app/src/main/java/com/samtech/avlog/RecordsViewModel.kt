package com.samtech.avlog

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.samtech.avlog.FDTLManager.calculateCumulativeHours
import com.samtech.avlog.ui.theme.Converters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.ZoneOffset

class RecordsViewModel(private val dutyDao: DutyDao) : ViewModel() {
    // This Flow will automatically emit new lists whenever the database changes
    val allRecords: StateFlow<List<DayRecordEntity>> = dutyDao.getAllRecords()
        .map { records ->
            records.filter { record ->
                Converters().toSortieList(record.sortiesJson).isNotEmpty()
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val fdtlStats = allRecords.map { records ->
        listOf(
            FDTLStatus("7 Days", calculateCumulativeHours(records, 7), 35.0, "7D"),
            FDTLStatus("14 Days", calculateCumulativeHours(records, 14), 65.0, "14D"),
            FDTLStatus("28 Days", calculateCumulativeHours(records, 28), 100.0, "28D"),
            FDTLStatus("90 Days", calculateCumulativeHours(records, 90), 300.0, "90D"),
            FDTLStatus("1 Year", calculateCumulativeHours(records, 365), 1000.0, "365D")
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteSortie(date: String, sortieId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            // 1. Get current records directly from database
            val records = dutyDao.getAllRecordsOnce()
            val targetRecord = records.find { it.date == date } ?: return@launch

            // 2. Filter out the specific sortie
            val sorties = Converters().toSortieList(targetRecord.sortiesJson)
            val updatedSorties = sorties.filter { it.id != sortieId }

            if (updatedSorties.isEmpty()) {
                // If no sorties remain, delete the card for this date from Room
                dutyDao.deleteRecordByDate(date)
            } else {
                // 3. Recalculate the total day time
                val newTotalSeconds = updatedSorties.sumOf {
                    if (it.blockOn != null) (it.blockOn - it.blockOff) / 1000 else 0L
                }

                // 4. Create the updated Entity
                val updatedEntity = DayRecordEntity(
                    date = targetRecord.date,
                    baCheckDone = targetRecord.baCheckDone,
                    baCheckTime = targetRecord.baCheckTime,
                    dayTotalSeconds = newTotalSeconds,
                    sortiesJson = Converters().fromSortieList(updatedSorties)
                )

                // 5. Save back to Room
                dutyDao.insertRecord(updatedEntity)
            }
        }
    }

    fun exportToCsv(context: Context, records: List<DayRecordEntity>) {
        val csvHeader = "Date,Sortie,Block Off,Block On,Duration (Sec),Total Day Time\n"
        val csvContent = StringBuilder(csvHeader)

        records.forEach { record ->
            val sorties = Converters().toSortieList(record.sortiesJson)
            sorties.forEachIndexed { index, sortie ->
                val duration = if (sortie.blockOn != null) (sortie.blockOn - sortie.blockOff) / 1000 else 0
                csvContent.append("${record.date},")
                csvContent.append("Sortie ${index + 1},")
                csvContent.append("${formatTime(sortie.blockOff)},")
                csvContent.append("${sortie.blockOn?.let { formatTime(it) } ?: "N/A"},")
                csvContent.append("$duration,")
                csvContent.append("${record.dayTotalSeconds}\n")
            }
        }

        // Create File and Share
        try {
            val fileName = "Flight_Log_${System.currentTimeMillis()}.csv"
            val file = File(context.cacheDir, fileName)
            file.writeText(csvContent.toString())

            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_SUBJECT, "My Flight Records")
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(Intent.createChooser(intent, "Export Logbook"))
        } catch (e: Exception) {
            Log.e("ExportError", "Failed to export", e)
        }
    }

    fun calculateRemainingHours(allRecords: List<DayRecordEntity>, targetDate: LocalDate): List<FDTLStatus> {
        val limits = mapOf(7 to 35.0, 14 to 65.0, 28 to 100.0, 90 to 300.0, 365 to 1000.0)

        return limits.map { (days, limit) ->
            // Look back from the TARGET date, not today
            val cutoff = targetDate.minusDays(days.toLong())

            val consumedSeconds = allRecords
                .filter {
                    val recordDate = LocalDate.parse(it.date)
                    recordDate >= cutoff && recordDate < targetDate
                }
                .sumOf { it.dayTotalSeconds }

            val consumedHours = consumedSeconds / 3600.0
            val remaining = (limit - consumedHours).coerceAtLeast(0.0)

            FDTLStatus(
                period = "$days Days",
                current = remaining, // In this mode, we display the 'Remaining' amount
                limit = limit,
                label = "${days}D"
            )
        }
    }
}
