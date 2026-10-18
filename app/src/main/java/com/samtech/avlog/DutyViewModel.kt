package com.samtech.avlog

import android.util.Log
import java.time.LocalDate
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.samtech.avlog.FDTLManager.calculateCumulativeHours
import com.samtech.avlog.ui.theme.Converters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.ZoneOffset
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class DutyViewModel(private val dutyDao: DutyDao) : ViewModel() {

    private val _currentUtcTime = MutableStateFlow(formatTimeUtc(System.currentTimeMillis()))
    val currentUtcTime = _currentUtcTime.asStateFlow()
    // The current state of our duty day
    private val _dayRecord = MutableStateFlow(DayRecord(date = LocalDate.now(ZoneOffset.UTC).toString()))
    val dayRecord = _dayRecord.asStateFlow()

    private val _fdtlWarning = MutableStateFlow<String?>(null)
    val fdtlWarning = _fdtlWarning.asStateFlow()


    init {
        viewModelScope.launch {
            dutyDao.getRecordByDate(LocalDate.now(ZoneOffset.UTC).toString()).collect { entity ->
                if (entity != null) {
                    val sorties = Converters().toSortieList(entity.sortiesJson)
                    _dayRecord.value = DayRecord(
                        date = entity.date,
                        baCheckDone = entity.baCheckDone,
                        baCheckTime = entity.baCheckTime,
                        sorties = sorties
                    )

                    // CHECK FOR ACTIVE TIMER:
                    val activeSortie = sorties.find { it.blockOn == null }
                    if (activeSortie != null) {
                        startTimer() // Resume the ticker if we find a flight in progress
                    }
                }
            }
        }
        viewModelScope.launch(Dispatchers.Default) {
            while (isActive) {
                _currentUtcTime.value = formatTimeUtc(System.currentTimeMillis())
                delay(1000) // Update every second
            }
        }
    }

    // Timer state for the "Airborne" duration
    private val _elapsedSeconds = MutableStateFlow(0L)
    val elapsedSeconds = _elapsedSeconds.asStateFlow()

    private var timerJob: Job? = null

    fun confirmBACheck() {
        _dayRecord.value = _dayRecord.value.copy(
            baCheckDone = true,
            baCheckTime = System.currentTimeMillis()
        )
    }

    fun clearWarning() { _fdtlWarning.value = null }

    fun handleBlockOff() {
        viewModelScope.launch(Dispatchers.IO) {

            val allRecords = dutyDao.getAllRecordsOnce()
            val warning = FDTLManager.getApproachingLimitWarning(allRecords)
            val now = System.currentTimeMillis()
            val newSortie = Sortie(blockOff = now)

            // 1. Update local state
            val updatedSorties = _dayRecord.value.sorties + newSortie

            // 2. Prepare the Entity for Room
            val current = _dayRecord.value
            val entity = DayRecordEntity(
                date = current.date,
                baCheckDone = current.baCheckDone,
                baCheckTime = current.baCheckTime,
                // Calculate current total of completed sorties
                dayTotalSeconds = updatedSorties.sumOf {
                    if (it.blockOn != null) (it.blockOn - it.blockOff) / 1000 else 0L
                },
                sortiesJson = Converters().fromSortieList(updatedSorties)
            )

            // 3. Save to Database immediately
            dutyDao.insertRecord(entity)

            // 4. Start the UI ticker on the Main thread
            withContext(Dispatchers.Main) {
                _fdtlWarning.value = warning
                startTimer()
            }
        }
    }

    fun handleBlockOn() {
        viewModelScope.launch(Dispatchers.IO) {
            val current = _dayRecord.value
            val now = System.currentTimeMillis()

            // 1. Close the active sortie
            val updatedSorties = current.sorties.map {
                if (it.blockOn == null) it.copy(blockOn = now) else it
            }

            // 2. Calculate TOTAL seconds for the whole day
            val totalDaySeconds = updatedSorties.sumOf {
                if (it.blockOn != null) (it.blockOn - it.blockOff) / 1000 else 0L
            }

            val entity = DayRecordEntity(
                date = current.date,
                baCheckDone = current.baCheckDone,
                baCheckTime = current.baCheckTime,
                dayTotalSeconds = totalDaySeconds, // Storing the full day sum
                sortiesJson = Converters().fromSortieList(updatedSorties)
            )

            dutyDao.insertRecord(entity)

            withContext(Dispatchers.Main) {
                _elapsedSeconds.value = 0
                timerJob?.cancel()
            }
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive) {
                val activeSortie = _dayRecord.value.sorties.find { it.blockOn == null }
                activeSortie?.let {
                    _elapsedSeconds.value = (System.currentTimeMillis() - it.blockOff) / 1000
                }
                delay(1000)
            }
        }
    }

    private fun formatTimeUtc(millis: Long): String {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date(millis)) + " Z"
    }

    fun deleteSortie(date: String, sortieId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            // 1. Get the current record from the database
            // (Assuming you have a way to get the specific record,
            // or you can use the one already in your StateFlow)
            val currentRecord = _dayRecord.value

            // 2. Filter out the specific sortie
            val updatedSorties = currentRecord.sorties.filter { it.id != sortieId }

            // 3. Recalculate the total day time
            val newTotalSeconds = updatedSorties.sumOf {
                if (it.blockOn != null) (it.blockOn - it.blockOff) / 1000 else 0L
            }

            // 4. Create the updated Entity
            val updatedEntity = DayRecordEntity(
                date = currentRecord.date,
                baCheckDone = currentRecord.baCheckDone,
                baCheckTime = currentRecord.baCheckTime,
                dayTotalSeconds = newTotalSeconds,
                sortiesJson = Converters().fromSortieList(updatedSorties)
            )

            // 5. Save back to Room
            dutyDao.insertRecord(updatedEntity)
        }
    }
}
