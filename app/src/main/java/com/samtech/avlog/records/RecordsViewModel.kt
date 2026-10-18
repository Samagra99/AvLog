package com.samtech.avlog.records

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.samtech.avlog.core.Clock
import com.samtech.avlog.core.dateTicks
import com.samtech.avlog.core.nowMillis
import com.samtech.avlog.core.utcToday
import com.samtech.avlog.data.BackupData
import com.samtech.avlog.data.BackupFormatException
import com.samtech.avlog.data.BackupManager
import com.samtech.avlog.data.CsvExporter
import com.samtech.avlog.data.DayLog
import com.samtech.avlog.data.DutyRepository
import com.samtech.avlog.data.EditResult
import com.samtech.avlog.data.ExportResult
import com.samtech.avlog.data.ImportMode
import com.samtech.avlog.data.SortieEntity
import com.samtech.avlog.fdtl.FdtlCalculator
import com.samtech.avlog.fdtl.FdtlStatus
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class RecordsUiState(
    /** True until the first database emission, so "No records yet" never flashes. */
    val loading: Boolean = true,
    val days: List<DayLog> = emptyList(),
    val stats: List<FdtlStatus> = emptyList(),
    val planningMode: Boolean = false,
    val planningDate: LocalDate = LocalDate.MIN,
    val today: LocalDate = LocalDate.MIN,
    val pendingRestore: BackupData? = null,
)

sealed interface RecordsEvent {
    data class Share(val uri: Uri) : RecordsEvent
    data object NoRecords : RecordsEvent
    data object ExportFailed : RecordsEvent
    data class SortieDeleted(val sortie: SortieEntity) : RecordsEvent
    data class EditFailed(val result: EditResult) : RecordsEvent
    data object EditSaved : RecordsEvent
    data class BackupSaved(val sorties: Int, val licenses: Int) : RecordsEvent
    data class RestoreDone(val sorties: Int, val licenses: Int) : RecordsEvent
    data class Failed(val message: String?) : RecordsEvent
}

class RecordsViewModel(
    private val repository: DutyRepository,
    private val csvExporter: CsvExporter,
    private val backupManager: BackupManager,
    private val clock: Clock,
) : ViewModel() {

    private val planningMode = MutableStateFlow(false)
    private val planningDate = MutableStateFlow(clock.utcToday())
    private val pendingRestore = MutableStateFlow<BackupData?>(null)

    private val _events = Channel<RecordsEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /** P7.3: one flow; the Switch and the date picker only re-run the cheap in-memory sum. */
    private val stats = combine(repository.dailyTotals, planningMode, planningDate, clock.dateTicks()) { totals, planning, date, today ->
        val sum = FdtlCalculator.sumFrom(totals)
        if (planning) FdtlCalculator.planning(date, sum) else FdtlCalculator.live(today, sum)
    }

    val uiState: StateFlow<RecordsUiState> = combine(
        repository.dayLogs, stats, planningMode, planningDate, pendingRestore,
    ) { days, stats, planning, date, restore ->
        RecordsUiState(
            loading = false,
            days = days,
            stats = stats,
            planningMode = planning,
            planningDate = date,
            today = clock.utcToday(),
            pendingRestore = restore,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RecordsUiState())

    fun nowMillis(): Long = clock.nowMillis()

    fun setPlanningMode(enabled: Boolean) { planningMode.value = enabled }
    fun setPlanningDate(date: LocalDate) { planningDate.value = date }

    fun deleteSortie(id: String) {
        viewModelScope.launch {
            repository.deleteSortie(id)?.let { _events.send(RecordsEvent.SortieDeleted(it)) }
        }
    }

    fun undoDelete(sortie: SortieEntity) {
        viewModelScope.launch { repository.restoreSortie(sortie) }
    }

    fun updateSortie(id: String, blockOff: Long, blockOn: Long?) {
        viewModelScope.launch {
            when (val r = repository.updateSortie(id, blockOff, blockOn)) {
                EditResult.Ok -> _events.send(RecordsEvent.EditSaved)
                else -> _events.send(RecordsEvent.EditFailed(r))
            }
        }
    }

    fun exportCsv() {
        viewModelScope.launch {
            _events.send(
                when (val r = csvExporter.export()) {
                    is ExportResult.Success -> RecordsEvent.Share(r.uri)
                    ExportResult.NoRecords -> RecordsEvent.NoRecords
                    ExportResult.Failed -> RecordsEvent.ExportFailed
                }
            )
        }
    }

    fun suggestedBackupName(): String = "AvLog_backup_${clock.utcToday()}.json"

    fun writeBackup(uri: Uri) {
        viewModelScope.launch {
            try {
                val data = backupManager.writeTo(uri)
                _events.send(RecordsEvent.BackupSaved(data.sorties.size, data.licenses.size))
            } catch (e: Exception) {
                _events.send(RecordsEvent.Failed(e.message))
            }
        }
    }

    /** Reads and validates; nothing is imported until the user picks merge or replace. */
    fun readBackup(uri: Uri) {
        viewModelScope.launch {
            try {
                pendingRestore.value = backupManager.readFrom(uri)
            } catch (e: BackupFormatException) {
                _events.send(RecordsEvent.Failed(e.message))
            } catch (e: Exception) {
                _events.send(RecordsEvent.Failed(e.message))
            }
        }
    }

    fun cancelRestore() { pendingRestore.value = null }

    fun confirmRestore(mode: ImportMode) {
        val data = pendingRestore.value ?: return
        pendingRestore.value = null
        viewModelScope.launch {
            try {
                backupManager.import(data, mode)
                _events.send(RecordsEvent.RestoreDone(data.sorties.size, data.licenses.size))
            } catch (e: Exception) {
                _events.send(RecordsEvent.Failed(e.message))
            }
        }
    }
}
