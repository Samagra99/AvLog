package com.samtech.avlog.fdtl

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class FdtlCalculatorTest {

    private val today = LocalDate.of(2026, 10, 18)
    private fun h(hours: Double) = (hours * 3600).toLong()

    @Test
    fun `7D window covers exactly 7 days including today`() {
        val range = FdtlCalculator.liveRange(FdtlWindow(7, 35), today)
        assertEquals(LocalDate.of(2026, 10, 12), range.from)
        assertEquals(today, range.to)

        // Flying on the 8th day back must not count.
        val totals = mapOf(
            today.minusDays(7) to h(10.0), // outside
            today.minusDays(6) to h(2.0),  // inside (first day)
            today to h(1.0),               // inside (today)
        )
        val status = FdtlCalculator.live(today, FdtlCalculator.sumFrom(totals), listOf(FdtlWindow(7, 35))).single()
        assertEquals(h(3.0), status.consumedSeconds)
    }

    @Test
    fun `planning range is the N-1 days before the planned date`() {
        val planned = LocalDate.of(2026, 10, 20)
        val range = FdtlCalculator.planningRange(FdtlWindow(7, 35), planned)
        assertEquals(LocalDate.of(2026, 10, 14), range.from)
        assertEquals(LocalDate.of(2026, 10, 19), range.to)
    }

    @Test
    fun `planning remaining plus planned-day flying equals live consumption for that day`() {
        val windows = FdtlLimits.windows
        val planned = today
        val totals = mutableMapOf<LocalDate, Long>()
        for (i in 0 until 400) totals[today.minusDays(i.toLong())] = h(1.5)
        val sum = FdtlCalculator.sumFrom(totals)

        val live = FdtlCalculator.live(planned, sum, windows)
        val plan = FdtlCalculator.planning(planned, sum, windows)
        live.zip(plan).forEach { (l, p) ->
            assertEquals(l.window, p.window)
            assertEquals(l.consumedSeconds, p.consumedSeconds + totals.getValue(planned))
        }
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
        val approaching7 = FdtlStatus(FdtlWindow(7, 35), h(34.0), Severity.APPROACHING)
        val exceeded365 = FdtlStatus(FdtlWindow(365, 1000), h(1001.0), Severity.EXCEEDED)
        assertEquals(exceeded365, FdtlCalculator.mostSevere(listOf(approaching7, exceeded365)))
    }

    @Test
    fun `mostSevere picks the smallest margin within the same severity, not the first window`() {
        val w7 = FdtlStatus(FdtlWindow(7, 35), h(32.0), Severity.APPROACHING)   // 3h margin
        val w28 = FdtlStatus(FdtlWindow(28, 100), h(99.0), Severity.APPROACHING) // 1h margin
        assertEquals(w28, FdtlCalculator.mostSevere(listOf(w7, w28)))
    }

    @Test
    fun `mostSevere returns null when everything is OK`() {
        assertNull(FdtlCalculator.mostSevere(listOf(FdtlStatus(FdtlWindow(7, 35), 0, Severity.OK))))
    }

    @Test
    fun `negative durations clamp to zero and open sorties count zero`() {
        assertEquals(0L, FdtlCalculator.durationSeconds(blockOff = 10_000, blockOn = 5_000))
        assertEquals(0L, FdtlCalculator.durationSeconds(blockOff = 10_000, blockOn = null))
        assertEquals(90L, FdtlCalculator.durationSeconds(blockOff = 10_000, blockOn = 100_000))
    }

    @Test
    fun `remaining never negative`() {
        val s = FdtlStatus(FdtlWindow(7, 35), h(40.0), Severity.EXCEEDED)
        assertEquals(0L, s.remainingSeconds)
    }
}
