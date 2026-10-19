package com.samtech.avlog.data

import com.samtech.avlog.core.Clock
import com.samtech.avlog.core.minuteTicks
import com.samtech.avlog.core.nowMillis
import com.samtech.avlog.core.utcToday
import com.samtech.avlog.fdtl.BlockInterval
import com.samtech.avlog.fdtl.FdtlCalculator
import com.samtech.avlog.fdtl.FdtlLimits
import com.samtech.avlog.fdtl.FdtlStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** BA check rule (from commit f784030, confirmed by the user): a check is valid for 12 hours. */
object BaCheckPolicy {
    const val VALIDITY_MS = 12 * 60 * 60 * 1000L

    fun isValid(baCheckTime: Long?, now: Long): Boolean =
        baCheckTime != null && baCheckTime > 0 && now - baCheckTime in 0 until VALIDITY_MS
}

/** A UTC day's flying for the Records list and CSV. */
data class DayLog(
    val date: LocalDate,
    val baCheckTime: Long?,
    val sorties: List<SortieEntity>,
) {
    val totalSeconds: Long get() = sorties.sumOf { FdtlCalculator.durationSeconds(it.blockOff, it.blockOn) }
}

class DutyRepository(private val dao: DutyDao, private val clock: Clock) {

    val openSortie: Flow<SortieEntity?> = dao.observeOpenSortie()

    val latestBaCheck: Flow<Long?> = dao.observeLatestBaCheck()

    val dailyTotals: Flow<Map<LocalDate, Long>> =
        dao.observeDailyTotals().map { rows -> rows.associate { it.date to it.seconds } }

    /** Days that have at least one sortie, newest first, with the BA check of that day. */
    val dayLogs: Flow<List<DayLog>> = combine(dao.observeAllSorties(), dao.observeDutyDays()) { sorties, days ->
        val baByDate = days.associate { it.date to it.baCheckTime }
        sorties.groupBy { it.dateUtc }
            .map { (date, list) -> DayLog(date, baByDate[date], list.sortedBy { it.blockOff }) }
            .sortedByDescending { it.date }
    }

    /** Completed sorties as intervals, for the rolling 24 h window. */
    val completedIntervals: Flow<List<BlockInterval>> = dao.observeAllSorties().map { it.toIntervals() }

    /** Live FDTL statuses; re-evaluated when data changes and every minute (rolling window, UTC rollover). */
    val liveStatuses: Flow<List<FdtlStatus>> =
        combine(dailyTotals, completedIntervals, clock.minuteTicks()) { totals, intervals, now ->
            FdtlCalculator.live(now, FdtlCalculator.sumFrom(totals), FdtlCalculator.rollingFrom(intervals))
        }

    suspend fun getDayLogs(): List<DayLog> {
        val baByDate = dao.getDutyDays().associate { it.date to it.baCheckTime }
        return dao.getAllSorties().groupBy { it.dateUtc }
            .map { (date, list) -> DayLog(date, baByDate[date], list.sortedBy { it.blockOff }) }
            .sortedByDescending { it.date }
    }

    /** One-shot live evaluation straight from SQL, used at Block Off. */
    suspend fun liveStatusesNow(): List<FdtlStatus> {
        val now = clock.now()
        val today = clock.utcToday()
        val sums = FdtlLimits.windows.filterNot { it.isRolling }.associate { w ->
            val r = FdtlCalculator.liveRange(w, today)
            r to dao.sumSecondsBetween(r.from, r.to)
        }
        val intervals = dao.getAllSorties().toIntervals()
        return FdtlCalculator.live(now, { range -> sums.getValue(range) }, FdtlCalculator.rollingFrom(intervals))
    }

    suspend fun startSortie(): StartResult = dao.startSortie(clock.nowMillis())

    suspend fun endSortie(): SortieEntity? = dao.endSortie(clock.nowMillis())

    suspend fun updateSortie(id: String, blockOff: Long, blockOn: Long?): EditResult =
        dao.updateSortie(id, blockOff, blockOn, clock.nowMillis())

    suspend fun deleteSortie(id: String): SortieEntity? = dao.deleteSortie(id)

    suspend fun restoreSortie(sortie: SortieEntity) = dao.restoreSortie(sortie)

    /** Persists the BA check immediately (not on the first Block Off). */
    suspend fun confirmBaCheck(): Long {
        val now = clock.nowMillis()
        dao.upsertDutyDay(DutyDayEntity(DutyDao.utcDate(now), baCheckDone = true, baCheckTime = now))
        return now
    }

    /** One-time import of the BA time that f784030 kept in SharedPreferences. */
    suspend fun importLegacyBaCheck(prefsTime: Long) {
        if (prefsTime <= 0) return
        val latest = dao.getLatestBaCheck() ?: 0L
        if (prefsTime > latest) {
            val date = DutyDao.utcDate(prefsTime)
            val existing = dao.getDutyDay(date)
            if (existing == null || (existing.baCheckTime ?: 0L) < prefsTime) {
                dao.upsertDutyDay(DutyDayEntity(date, baCheckDone = true, baCheckTime = prefsTime))
            }
        }
    }
}

private fun List<SortieEntity>.toIntervals(): List<BlockInterval> =
    mapNotNull { s -> s.blockOn?.let { BlockInterval(s.blockOff, it) } }

class LicenseRepository(private val dao: LicenseDao) {
    val licenses: Flow<List<LicenseEntity>> = dao.observeAll()

    /** Adds or updates. Keeps warningDays/notification state on update; resets it if the expiry changed. */
    suspend fun save(id: String?, name: String, expiry: LocalDate, warningDays: Int): LicenseEntity {
        val existing = id?.let { dao.get(it) }
        val entity = if (existing == null) {
            LicenseEntity(name = name.trim(), expiryDate = expiry, warningDays = warningDays)
        } else {
            existing.copy(
                name = name.trim(),
                expiryDate = expiry,
                warningDays = warningDays,
                lastNotifiedThreshold = if (existing.expiryDate != expiry || existing.warningDays != warningDays) {
                    null
                } else {
                    existing.lastNotifiedThreshold
                },
            )
        }
        dao.upsert(entity)
        return entity
    }

    suspend fun delete(id: String): LicenseEntity? {
        val existing = dao.get(id) ?: return null
        dao.delete(id)
        return existing
    }

    suspend fun restore(license: LicenseEntity) = dao.upsert(license)
}
