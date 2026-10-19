package com.samtech.avlog.fdtl

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

enum class Severity { OK, APPROACHING, EXCEEDED }

/** Inclusive range of UTC dates. */
data class DateRange(val from: LocalDate, val to: LocalDate)

/** A completed sortie as epoch millis (Block Off, Block On). */
data class BlockInterval(val start: Long, val end: Long)

/**
 * Result of evaluating one window.
 * - Live mode: [consumedSeconds] covers the window ending now (calendar: the N days ending on and including
 *   today; rolling: the last 24 h).
 * - Planning mode: [consumedSeconds] covers what precedes the planned date (calendar: the N-1 days before it;
 *   rolling: the 24 h ending now if the date is today, else the 24 h before 00:00Z of it), so
 *   [remainingSeconds] is what can still be flown that day.
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

    /** Seconds of [intervals] inside [from, to) (epoch millis). Partial overlaps count partially. */
    fun overlapSeconds(intervals: List<BlockInterval>, from: Long, to: Long): Long =
        intervals.sumOf { iv -> ((minOf(iv.end, to) - maxOf(iv.start, from)) / 1000L).coerceAtLeast(0L) }

    fun severity(consumedSeconds: Long, limitSeconds: Long, warningFraction: Double = FdtlLimits.WARNING_FRACTION): Severity =
        when {
            consumedSeconds >= limitSeconds -> Severity.EXCEEDED
            consumedSeconds >= limitSeconds * warningFraction -> Severity.APPROACHING
            else -> Severity.OK
        }

    /**
     * @param sumDays total completed block seconds for an inclusive UTC date range.
     * @param sumRolling completed block seconds overlapping [from, to) in epoch millis.
     * Live and planning modes go through the same two functions so they cannot disagree.
     */
    fun live(
        now: Instant,
        sumDays: (DateRange) -> Long,
        sumRolling: (Long, Long) -> Long,
        windows: List<FdtlWindow> = FdtlLimits.windows,
    ): List<FdtlStatus> {
        val today = now.atZone(ZoneOffset.UTC).toLocalDate()
        val nowMs = now.toEpochMilli()
        return windows.map { w ->
            val consumed = if (w.isRolling) {
                sumRolling(nowMs - w.rollingHours * 3_600_000L, nowMs)
            } else {
                sumDays(liveRange(w, today))
            }
            FdtlStatus(w, consumed, severity(consumed, w.limitSeconds))
        }
    }

    /**
     * Rolling windows end at [now] when [plannedDate] is today (so planning matches the live view), otherwise
     * at 00:00Z of the planned date.
     */
    fun planning(
        plannedDate: LocalDate,
        now: Instant,
        sumDays: (DateRange) -> Long,
        sumRolling: (Long, Long) -> Long,
        windows: List<FdtlWindow> = FdtlLimits.windows,
    ): List<FdtlStatus> {
        val isToday = plannedDate == now.atZone(ZoneOffset.UTC).toLocalDate()
        val rollingEnd = if (isToday) now.toEpochMilli() else plannedDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        return windows.map { w ->
            val consumed = if (w.isRolling) {
                sumRolling(rollingEnd - w.rollingHours * 3_600_000L, rollingEnd)
            } else {
                sumDays(planningRange(w, plannedDate))
            }
            FdtlStatus(w, consumed, severity(consumed, w.limitSeconds))
        }
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

    /** Builds a calendar [sumDays] function from per-day totals. */
    fun sumFrom(dailyTotals: Map<LocalDate, Long>): (DateRange) -> Long = { range ->
        if (range.to < range.from) 0L
        else dailyTotals.entries.sumOf { (d, s) -> if (d >= range.from && d <= range.to) s else 0L }
    }

    /** Builds a rolling [sumRolling] function from completed sorties. */
    fun rollingFrom(intervals: List<BlockInterval>): (Long, Long) -> Long = { from, to ->
        overlapSeconds(intervals, from, to)
    }
}
