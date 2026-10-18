package com.samtech.avlog.licenses

import com.samtech.avlog.data.LicenseEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class ExpiryEvaluatorTest {
    private val today = LocalDate.of(2026, 10, 18)
    private fun lic(daysLeft: Long, warn: Int = 30, last: Int? = null) =
        LicenseEntity(id = "x", name = "IR", expiryDate = today.plusDays(daysLeft), warningDays = warn, lastNotifiedThreshold = last)

    @Test fun `thresholds include warningDays, 7, 1, 0 and expired`() {
        assertEquals(listOf(30, 7, 1, 0, -1), ExpiryEvaluator.thresholds(30))
        assertEquals(listOf(5, 1, 0, -1), ExpiryEvaluator.thresholds(5))
        assertEquals(listOf(7, 1, 0, -1), ExpiryEvaluator.thresholds(7))
    }

    @Test fun `nothing before the warning window`() = assertNull(ExpiryEvaluator.evaluate(lic(31), today))

    @Test fun `fires at the warning threshold`() = assertEquals(30, ExpiryEvaluator.evaluate(lic(30), today))

    @Test fun `fires once per threshold, not every day`() {
        assertNull(ExpiryEvaluator.evaluate(lic(29, last = 30), today))
        assertNull(ExpiryEvaluator.evaluate(lic(8, last = 30), today))
        assertEquals(7, ExpiryEvaluator.evaluate(lic(7, last = 30), today))
        assertNull(ExpiryEvaluator.evaluate(lic(6, last = 7), today))
    }

    @Test fun `skipped runs catch up to the lowest crossed threshold`() {
        // Worker didn't run between 30 and 0 days left.
        assertEquals(0, ExpiryEvaluator.evaluate(lic(0, last = 30), today))
        // First run ever happens 3 days after expiry.
        assertEquals(ExpiryEvaluator.EXPIRED, ExpiryEvaluator.evaluate(lic(-3), today))
    }

    @Test fun `expired fires exactly once`() {
        assertEquals(ExpiryEvaluator.EXPIRED, ExpiryEvaluator.evaluate(lic(-1, last = 0), today))
        assertNull(ExpiryEvaluator.evaluate(lic(-2, last = ExpiryEvaluator.EXPIRED), today))
    }

    @Test fun `days left uses calendar dates`() {
        assertEquals(1, ExpiryEvaluator.daysLeft(today.plusDays(1), today))
        assertEquals(0, ExpiryEvaluator.daysLeft(today, today))
    }
}
