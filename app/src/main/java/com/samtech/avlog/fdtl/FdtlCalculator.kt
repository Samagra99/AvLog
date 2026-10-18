package com.samtech.avlog.fdtl

import java.time.LocalDate

enum class Severity { OK, APPROACHING, EXCEEDED }

/** Inclusive range of UTC dates. */
data class DateRange(val from: LocalDate, val to: LocalDate)

/**
 * Result of evaluating one window.
 * - Live mode: [consumedSeconds] covers the N days ending on and including the evaluated date.
 * - Planning mode: [consumedSeconds] covers the N-1 days before the planned date, so [remainingSeconds]
 *   is what can still be flown on the planned date itself.
 */
data class FdtlStatus(
    val window: FdtlWindow,
    val consumedSeconds: Long,
    val severity: Severity,
) {
    val limitSeconds: Long get() = window.limitSeconds
    val remainingSeconds: Long get() = (limitSeconds - consumedSeconds).coerceAtLeast(0L)
    val marginSeconds: Long get() = limitSeconds - consumedSeconds
    val fraction: Float get() = if (limitSeconds == 0L) 1f else consumedSeconds.toFloat() / limitSeconds
}

/** Pure FDTL logic: no Android types, no hidden clock. */
object FdtlCalculator {

    /** Block time of one sortie in whole seconds, never negative; open sorties count as 0. */
    fun durationSeconds(blockOff: Long, blockOn: Long?): Long =
        if (blockOn == null) 0L else ((blockOn - blockOff) / 1000L).coerceAtLeast(0L)

    /** N consecutive days ending on and including [date]. */
    fun liveRange(window: FdtlWindow, date: LocalDate): DateRange =
        DateRange(date.minusDays(window.days - 1L), date)

    /** The N-1 days before [plannedDate]; the planned day itself is what the remaining hours are for. */
    fun planningRange(window: FdtlWindow, plannedDate: LocalDate): DateRange =
        DateRange(plannedDate.minusDays(window.days - 1L), plannedDate.minusDays(1))

    fun severity(consumedSeconds: Long, limitSeconds: Long, warningFraction: Double = FdtlLimits.WARNING_FRACTION): Severity =
        when {
            consumedSeconds >= limitSeconds -> Severity.EXCEEDED
            consumedSeconds >= limitSeconds * warningFraction -> Severity.APPROACHING
            else -> Severity.OK
        }

    /**
     * @param sumSeconds total completed block seconds for an inclusive date range. Live and planning
     * modes both go through this single function so they cannot disagree.
     */
    fun live(
        date: LocalDate,
        sumSeconds: (DateRange) -> Long,
        windows: List<FdtlWindow> = FdtlLimits.windows,
    ): List<FdtlStatus> = windows.map { w ->
        val consumed = sumSeconds(liveRange(w, date))
        FdtlStatus(w, consumed, severity(consumed, w.limitSeconds))
    }

    fun planning(
        plannedDate: LocalDate,
        sumSeconds: (DateRange) -> Long,
        windows: List<FdtlWindow> = FdtlLimits.windows,
    ): List<FdtlStatus> = windows.map { w ->
        val consumed = sumSeconds(planningRange(w, plannedDate))
        FdtlStatus(w, consumed, severity(consumed, w.limitSeconds))
    }

    /**
     * The single most important warning, or null when every window is below the warning threshold.
     * Exceeded beats approaching; within the same severity the smallest margin wins.
     */
    fun mostSevere(statuses: List<FdtlStatus>): FdtlStatus? =
        statuses
            .filter { it.severity != Severity.OK }
            .sortedWith(compareByDescending<FdtlStatus> { it.severity.ordinal }.thenBy { it.marginSeconds })
            .firstOrNull()

    /** Builds a [sumSeconds] function from per-day totals. */
    fun sumFrom(dailyTotals: Map<LocalDate, Long>): (DateRange) -> Long = { range ->
        if (range.to < range.from) 0L
        else dailyTotals.entries.sumOf { (d, s) -> if (d >= range.from && d <= range.to) s else 0L }
    }
}
