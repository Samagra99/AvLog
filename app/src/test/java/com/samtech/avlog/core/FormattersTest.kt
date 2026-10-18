package com.samtech.avlog.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.util.Locale

class FormattersTest {

    @Test
    fun `hMm rounds consumed up and remaining down`() {
        val s = 34 * 3600L + 54 * 60 + 1 // 34:54:01
        assertEquals("34:55", Formatters.hMm(s, roundUp = true))
        assertEquals("34:54", Formatters.hMm(s, roundUp = false))
        assertEquals("0:00", Formatters.hMm(-5, roundUp = true))
    }

    @Test
    fun `34_9 hours no longer reads as 34`() {
        assertEquals("34:54", Formatters.hMm((34.9 * 3600).toLong(), roundUp = false))
    }

    @Test
    fun `hms clamps negatives and pads`() {
        assertEquals("00:00:00", Formatters.hms(-10))
        assertEquals("01:02:03", Formatters.hms(3723))
        assertEquals("100:00:00", Formatters.hms(360_000))
    }

    @Test
    fun `formatting ignores locales with non-Latin digits`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("ar-EG"))
            assertEquals("01:02:03", Formatters.hms(3723))
            assertEquals("00:00", Formatters.utcTime(0))
            assertEquals("\u206600:00:05 Z\u2069", Formatters.utcClock(Instant.ofEpochSecond(5)))
        } finally {
            Locale.setDefault(previous)
        }
    }
}
