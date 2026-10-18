package com.samtech.avlog.duty

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.samtech.avlog.core.Clock
import com.samtech.avlog.core.Formatters
import com.samtech.avlog.core.secondTicks
import com.samtech.avlog.data.BaCheckPolicy
import com.samtech.avlog.data.DutyRepository
import com.samtech.avlog.data.EditResult
import com.samtech.avlog.data.SortieEntity
import com.samtech.avlog.data.StartResult
import com.samtech.avlog.fdtl.FdtlCalculator
import com.samtech.avlog.fdtl.FdtlStatus
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DutyUiState(
    val nowMillis: Long = 0,
    val utcClock: String = "",
    /** Time of the BA check if it is still valid (12 h), else null. */
    val validBaCheckTime: Long? = null,
    /** Airborne sortie, independent of the UTC date. */
    val openSortie: SortieEntity? = null,
    val elapsedSeconds: Long = 0,
    /** Device clock is earlier than Block Off: show 00:00:00 and ask the pilot to check the times. */
    val clockBehind: Boolean = false,
    /** Open for longer than [DutyViewModel.STALE_OPEN_MS]: probably a forgotten BLOCK ON. */
    val stale: Boolean = false,
    /** Most severe FDTL window at or over the warning threshold, if any. */
    val fdtlAlert: FdtlStatus? = null,
    val loading: Boolean = true,
)

sealed interface DutyEvent {
    data class FdtlWarning(val status: FdtlStatus) : DutyEvent
    data class EditFailed(val result: EditResult) : DutyEvent
    data object EditSaved : DutyEvent
}

class DutyViewModel(
    private val repository: DutyRepository,
    private val clock: Clock,
) : ViewModel() {

    private val _events = Channel<DutyEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /** Ticks only while the UI is subscribed (collectAsStateWithLifecycle + WhileSubscribed). */
    val uiState: StateFlow<DutyUiState> = combine(
        clock.secondTicks(),
        repository.latestBaCheck,
        repository.openSortie,
        repository.liveStatuses,
    ) { now, latestBa, open, statuses ->
        val nowMs = now.toEpochMilli()
        val rawElapsed = open?.let { nowMs - it.blockOff } ?: 0L
        DutyUiState(
            nowMillis = nowMs,
            utcClock = Formatters.utcClock(now),
            validBaCheckTime = latestBa?.takeIf { BaCheckPolicy.isValid(it, nowMs) },
            openSortie = open,
            elapsedSeconds = (rawElapsed / 1000).coerceAtLeast(0),
            clockBehind = open != null && rawElapsed < 0,
            stale = open != null && rawElapsed > STALE_OPEN_MS,
            fdtlAlert = FdtlCalculator.mostSevere(statuses),
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DutyUiState())

    fun confirmBaCheck() {
        viewModelScope.launch { repository.confirmBaCheck() }
    }

    fun blockOff() {
        viewModelScope.launch {
            if (repository.startSortie() is StartResult.Started) {
                FdtlCalculator.mostSevere(repository.liveStatusesNow())?.let {
                    _events.send(DutyEvent.FdtlWarning(it))
                }
            }
        }
    }

    fun blockOn() {
        viewModelScope.launch { repository.endSortie() }
    }

    fun updateSortie(id: String, blockOff: Long, blockOn: Long?) {
        viewModelScope.launch {
            when (val r = repository.updateSortie(id, blockOff, blockOn)) {
                EditResult.Ok -> _events.send(DutyEvent.EditSaved)
                else -> _events.send(DutyEvent.EditFailed(r))
            }
        }
    }

    companion object {
        const val STALE_OPEN_MS = 12 * 60 * 60 * 1000L
    }
}
