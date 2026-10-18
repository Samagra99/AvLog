package com.samtech.avlog

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.samtech.avlog.core.Clock
import com.samtech.avlog.core.Formatters
import com.samtech.avlog.core.nowMillis
import com.samtech.avlog.core.utcToday
import com.samtech.avlog.fdtl.FdtlCalculator
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
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class DutyViewModel(
    private val dutyDao: DutyDao,
    context: Context,
    private val clock: Clock,
) : ViewModel() {

    private val prefs = context.getSharedPreferences("duty_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val TWELVE_HOURS_MS = 12 * 60 * 60 * 1000L // 12 hours in milliseconds
    }

    private fun isBaCheckValid(baCheckTime: Long?): Boolean {
        if (baCheckTime == null || baCheckTime <= 0L) return false
        val elapsed = clock.nowMillis() - baCheckTime
        return elapsed in 0 until TWELVE_HOURS_MS
    }

    private fun getValidBaCheckTime(): Long? {
        val savedTime = prefs.getLong("last_ba_check_time", 0L)
        return if (isBaCheckValid(savedTime)) savedTime else null
    }

    private fun loadInitialDayRecord(): DayRecord {
        val today = clock.utcToday().toString()
        val validBaTime = getValidBaCheckTime()
        val isBaDone = validBaTime != null
        return DayRecord(
            date = today,
            baCheckDone = isBaDone,
            baCheckTime = if (isBaDone) validBaTime else null
        )
    }

    private val _currentUtcTime = MutableStateFlow(formatTimeUtc(clock.nowMillis()))
    val currentUtcTime = _currentUtcTime.asStateFlow()

    // The current state of our duty day - initialized immediately from persistent storage
    private val _dayRecord = MutableStateFlow(loadInitialDayRecord())
    val dayRecord = _dayRecord.asStateFlow()

    private val _fdtlWarning = MutableStateFlow<String?>(null)
    val fdtlWarning = _fdtlWarning.asStateFlow()

    init {
        viewModelScope.launch {
            dutyDao.getAllRecords().collect { records ->
                val today = clock.utcToday().toString()
                val todayRecord = records.find { it.date == today }

                val validBaTimeFromPrefs = getValidBaCheckTime()
                val validBaRecordFromDb = records
                    .filter { it.baCheckDone && isBaCheckValid(it.baCheckTime) }
                    .maxByOrNull { it.baCheckTime ?: 0L }

                val validBaTime = validBaTimeFromPrefs ?: validBaRecordFromDb?.baCheckTime
                val isBaDone = validBaTime != null

                val sorties = if (todayRecord != null) {
                    Converters().toSortieList(todayRecord.sortiesJson)
                } else {
                    emptyList()
                }

                _dayRecord.value = DayRecord(
                    date = today,
                    baCheckDone = isBaDone,
                    baCheckTime = if (isBaDone) validBaTime else null,
                    sorties = sorties
                )

                // CHECK FOR ACTIVE TIMER:
                val activeSortie = sorties.find { it.blockOn == null }
                if (activeSortie != null) {
                    startTimer() // Resume the ticker if we find a flight in progress
                }
            }
        }

        viewModelScope.launch(Dispatchers.Default) {
            while (isActive) {
                val now = clock.nowMillis()
                _currentUtcTime.value = formatTimeUtc(now)

                // Expire BA check automatically if 12 hours have elapsed
                val currentRecord = _dayRecord.value
                if (currentRecord.baCheckDone) {
                    val baTime = currentRecord.baCheckTime
                    if (!isBaCheckValid(baTime)) {
                        _dayRecord.value = currentRecord.copy(
                            baCheckDone = false,
                            baCheckTime = null
                        )
                    }
                }

                delay(1000) // Update every second
            }
        }
    }

    // Timer state for the "Airborne" duration
    private val _elapsedSeconds = MutableStateFlow(0L)
    val elapsedSeconds = _elapsedSeconds.asStateFlow()

    private var timerJob: Job? = null

    fun confirmBACheck() {
        val now = clock.nowMillis()
        prefs.edit().putLong("last_ba_check_time", now).commit()

        _dayRecord.value = _dayRecord.value.copy(
            baCheckDone = true,
            baCheckTime = now
        )

        viewModelScope.launch(Dispatchers.IO) {
            val dateToday = clock.utcToday().toString()
            val allRecords = dutyDao.getAllRecordsOnce()
            val existingRecord = allRecords.find { it.date == dateToday }

            val updatedEntity = if (existingRecord != null) {
                existingRecord.copy(
                    baCheckDone = true,
                    baCheckTime = now
                )
            } else {
                DayRecordEntity(
                    date = dateToday,
                    baCheckDone = true,
                    baCheckTime = now,
                    dayTotalSeconds = 0L,
                    sortiesJson = Converters().fromSortieList(emptyList())
                )
            }

            dutyDao.insertRecord(updatedEntity)
        }
    }

    fun clearWarning() { _fdtlWarning.value = null }

    fun handleBlockOff() {
        viewModelScope.launch(Dispatchers.IO) {

            val allRecords = dutyDao.getAllRecordsOnce()
            val totals = allRecords.groupBy { LocalDate.parse(it.date) }.mapValues { (_, v) -> v.sumOf { it.dayTotalSeconds } }
            val worst = FdtlCalculator.mostSevere(FdtlCalculator.live(clock.utcToday(), FdtlCalculator.sumFrom(totals)))
            val warning = worst?.let {
                "${it.window.label} limit ${it.severity.name.lowercase()}: " +
                    "${Formatters.hMm(it.consumedSeconds, roundUp = true)} / ${it.window.limitHours}:00"
            }
            val now = clock.nowMillis()
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
            val now = clock.nowMillis()

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
                    _elapsedSeconds.value = (clock.nowMillis() - it.blockOff) / 1000
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
            val allRecords = dutyDao.getAllRecordsOnce()
            val targetRecord = allRecords.find { it.date == date } ?: return@launch

            val sorties = Converters().toSortieList(targetRecord.sortiesJson)
            val updatedSorties = sorties.filter { it.id != sortieId }

            if (updatedSorties.isEmpty()) {
                dutyDao.deleteRecordByDate(date)
            } else {
                val newTotalSeconds = updatedSorties.sumOf {
                    if (it.blockOn != null) (it.blockOn - it.blockOff) / 1000 else 0L
                }

                val updatedEntity = DayRecordEntity(
                    date = targetRecord.date,
                    baCheckDone = targetRecord.baCheckDone,
                    baCheckTime = targetRecord.baCheckTime,
                    dayTotalSeconds = newTotalSeconds,
                    sortiesJson = Converters().fromSortieList(updatedSorties)
                )

                dutyDao.insertRecord(updatedEntity)
            }
        }
    }
}
