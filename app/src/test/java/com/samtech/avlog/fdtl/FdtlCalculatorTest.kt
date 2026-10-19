package com.samtech.avlog.fdtl

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class FdtlCalculatorTest {

    private val today = LocalDate.of(2026, 10, 18)
    private val now = Instant.parse("2026-10-18T12:00:00Z")
    private fun h(hours: Double) = (hours * 3600).toLong()
    private fun t(iso: String) = Instant.parse(iso).toEpochMilli()
    private val noRolling: (Long, Long) -> Long = { _, _ -> 0L }
    private val w7 = FdtlWindow(limitHours = 35, days = 7)
    private val w24h = FdtlWindow(limitHours = 11, rollingHours = 24)

    @Test
    fun `limits are the CAR 9_1-9_5 values plus 24h at 11h`() {
        assertEquals(
            listOf("24H" to 11, "7D" to 35, "14D" to 65, "28D" to 100, "90D" to 300, "365D" to 1000),
            FdtlLimits.windows.map { it.key to it.limitHours },
        )
    }

    @Test
    fun `7D window covers exactly 7 days including today`() {
        val range = FdtlCalculator.liveRange(w7, today)
        assertEquals(LocalDate.of(2026, 10, 12), range.from)
        assertEquals(today, range.to)

        // Flying on the 8th day back must not count.
        val totals = mapOf(
            today.minusDays(7) to h(10.0), // outside
            today.minusDays(6) to h(2.0),  // inside (first day)
            today to h(1.0),               // inside (today)
        )
        val status = FdtlCalculator.live(now, FdtlCalculator.sumFrom(totals), noRolling, listOf(w7)).single()
        assertEquals(h(3.0), status.consumedSeconds)
    }

    @Test
    fun `planning range is the N-1 days before the planned date`() {
        val planned = LocalDate.of(2026, 10, 20)
        val range = FdtlCalculator.planningRange(w7, planned)
        assertEquals(LocalDate.of(2026, 10, 14), range.from)
        assertEquals(LocalDate.of(2026, 10, 19), range.to)
    }

    @Test
    fun `planning remaining plus planned-day flying equals live consumption for that day`() {
        val windows = FdtlLimits.windows.filterNot { it.isRolling }
        val totals = mutableMapOf<LocalDate, Long>()
        for (i in 0 until 400) totals[today.minusDays(i.toLong())] = h(1.5)
        val sum = FdtlCalculator.sumFrom(totals)

        val live = FdtlCalculator.live(now, sum, noRolling, windows)
        val plan = FdtlCalculator.planning(today, now, sum, noRolling, windows)
        live.zip(plan).forEach { (l, p) ->
            assertEquals(l.window, p.window)
            assertEquals(l.consumedSeconds, p.consumedSeconds + totals.getValue(today))
        }
    }

    @Test
    fun `rolling 24h counts the last 24 hours across UTC midnight, partially`() {
        val intervals = listOf(
            BlockInterval(t("2026-10-17T08:00:00Z"), t("2026-10-17T10:00:00Z")), // 2h, 1h inside (11:00 cut-off is after) -> 0
            BlockInterval(t("2026-10-17T11:00:00Z"), t("2026-10-17T13:00:00Z")), // straddles 12:00 cut-off -> 1h
            BlockInterval(t("2026-10-17T22:00:00Z"), t("2026-10-18T02:00:00Z")), // crosses midnight -> 4h
            BlockInterval(t("2026-10-18T09:00:00Z"), t("2026-10-18T11:30:00Z")), // 2.5h
        )
        val status = FdtlCalculator.live(now, { 0L }, FdtlCalculator.rollingFrom(intervals), listOf(w24h)).single()
        assertEquals(h(7.5), status.consumedSeconds)
        assertEquals(Severity.OK, status.severity)
    }

    @Test
    fun `rolling 24h severity at 11h limit`() {
        val intervals = listOf(BlockInterval(t("2026-10-18T00:00:00Z"), t("2026-10-18T10:00:00Z"))) // 10h
        val s = FdtlCalculator.live(now, { 0L }, FdtlCalculator.rollingFrom(intervals), listOf(w24h)).single()
        assertEquals(Severity.APPROACHING, s.severity) // 10 >= 9.9
        val over = listOf(BlockInterval(t("2026-10-17T13:00:00Z"), t("2026-10-18T00:00:00Z"))) // 11h
        assertEquals(
            Severity.EXCEEDED,
            FdtlCalculator.live(now, { 0L }, FdtlCalculator.rollingFrom(over), listOf(w24h)).single().severity,
        )
    }

    @Test
    fun `rolling 24h planning uses the 24 hours before 00Z of the planned date`() {
        val intervals = listOf(
            BlockInterval(t("2026-10-19T20:00:00Z"), t("2026-10-19T23:00:00Z")), // 3h, inside
            BlockInterval(t("2026-10-18T22:00:00Z"), t("2026-10-19T01:00:00Z")), // 1h inside (00:00-01:00 on the 19th)
        )
        val s = FdtlCalculator.planning(LocalDate.of(2026, 10, 20), now, { 0L }, FdtlCalculator.rollingFrom(intervals), listOf(w24h)).single()
        assertEquals(h(4.0), s.consumedSeconds)
        assertEquals(h(7.0), s.remainingSeconds)
    }

    @Test
    fun `planning today matches the live 24h window`() {
        // Same situation as the user's report: a long sortie yesterday, nothing yet today.
        val intervals = listOf(BlockInterval(t("2026-10-18T03:00:00Z"), t("2026-10-18T18:42:00Z")))
        val now19 = Instant.parse("2026-10-19T08:30:00Z")
        val rolling = FdtlCalculator.rollingFrom(intervals)
        val live = FdtlCalculator.live(now19, { 0L }, rolling, listOf(w24h)).single()
        val plan = FdtlCalculator.planning(LocalDate.of(2026, 10, 19), now19, { 0L }, rolling, listOf(w24h)).single()
        assertEquals(live.consumedSeconds, plan.consumedSeconds)
        assertEquals(h(10.2), live.consumedSeconds) // 08:30 -> 18:42
    }

    @Test
    fun `window requires exactly one of days or rollingHours`() {
        assertTrue(runCatching { FdtlWindow(limitHours = 1) }.isFailure)
        assertTrue(runCatching { FdtlWindow(limitHours = 1, days = 1, rollingHours = 24) }.isFailure)
    }

    @Test
    fun `severity thresholds at 90 and 100 percent`() {
        val limit = h(35.0)
        assertEquals(Severity.OK, FdtlCalculator.severity(h(31.4), limit))
        assertEquals(Severity.APPROACHING, FdtlCalculator.severity(h(31.5), limit))
        assertEquals(Severity.APPROACHING, FdtlCalculator.severity(limit - 1, limit))
        assertEquals(Severity.EXCEEDED, FdtlCalculator.severity(limit, limit))
    }

    @Test
    fun `mostSevere prefers exceeded over approaching`() {
        val approaching7 = FdtlStatus(w7, h(34.0), Severity.APPROACHING)
        val exceeded365 = FdtlStatus(FdtlWindow(limitHours = 1000, days = 365), h(1001.0), Severity.EXCEEDED)
        assertEquals(exceeded365, FdtlCalculator.mostSevere(listOf(approaching7, exceeded365)))
    }

    @Test
    fun `mostSevere picks the smallest margin within the same severity, not the first window`() {
        val a = FdtlStatus(w7, h(32.0), Severity.APPROACHING)                                  // 3h margin
        val b = FdtlStatus(FdtlWindow(limitHours = 100, days = 28), h(99.0), Severity.APPROACHING) // 1h margin
        assertEquals(b, FdtlCalculator.mostSevere(listOf(a, b)))
    }

    @Test
    fun `mostSevere returns null when everything is OK`() {
        assertNull(FdtlCalculator.mostSevere(listOf(FdtlStatus(w7, 0, Severity.OK))))
    }

    @Test
    fun `negative durations clamp to zero and open sorties count zero`() {
        assertEquals(0L, FdtlCalculator.durationSeconds(blockOff = 10_000, blockOn = 5_000))
        assertEquals(0L, FdtlCalculator.durationSeconds(blockOff = 10_000, blockOn = null))
        assertEquals(90L, FdtlCalculator.durationSeconds(blockOff = 10_000, blockOn = 100_000))
        assertEquals(0L, FdtlCalculator.overlapSeconds(listOf(BlockInterval(10_000, 5_000)), 0, 100_000))
    }

    @Test
    fun `remaining never negative`() {
        assertEquals(0L, FdtlStatus(w7, h(40.0), Severity.EXCEEDED).remainingSeconds)
    }
}
