package com.samtech.avlog.licenses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.samtech.avlog.core.Clock
import com.samtech.avlog.core.dateTicks
import com.samtech.avlog.data.LicenseEntity
import com.samtech.avlog.data.LicenseRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

data class LicenseItem(val license: LicenseEntity, val daysLeft: Long)

data class LicenseUiState(
    val loading: Boolean = true,
    val items: List<LicenseItem> = emptyList(),
    val today: LocalDate = LocalDate.MIN,
)

sealed interface LicenseEvent {
    data object Saved : LicenseEvent
    data class Deleted(val license: LicenseEntity) : LicenseEvent
}

class LicenseViewModel(
    private val repository: LicenseRepository,
    clock: Clock,
) : ViewModel() {

    private val _events = Channel<LicenseEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /** Days remaining are recomputed when the local date changes, not only on database changes. */
    val uiState: StateFlow<LicenseUiState> = combine(
        repository.licenses,
        clock.dateTicks(ZoneId.systemDefault()),
    ) { licenses, today ->
        LicenseUiState(
            loading = false,
            items = licenses.map { LicenseItem(it, ExpiryEvaluator.daysLeft(it.expiryDate, today)) },
            today = today,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LicenseUiState())

    fun save(id: String?, name: String, expiry: LocalDate, warningDays: Int) {
        viewModelScope.launch {
            repository.save(id, name, expiry, warningDays)
            _events.send(LicenseEvent.Saved)
        }
    }

    fun delete(id: String) {
        viewModelScope.launch { repository.delete(id)?.let { _events.send(LicenseEvent.Deleted(it)) } }
    }

    fun undoDelete(license: LicenseEntity) {
        viewModelScope.launch { repository.restore(license) }
    }
}
