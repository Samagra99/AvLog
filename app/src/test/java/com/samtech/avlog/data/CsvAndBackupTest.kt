package com.samtech.avlog.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class CsvAndBackupTest {

    private val d = LocalDate.of(2026, 10, 18)
    private fun t(iso: String) = Instant.parse(iso).toEpochMilli()

    @Test fun `csv has UTC headers, BA time, edited column and quoting`() {
        val day = DayLog(
            date = d,
            baCheckTime = t("2026-10-18T05:30:00Z"),
            sorties = listOf(
                SortieEntity("a", d, t("2026-10-18T06:00:00Z"), t("2026-10-18T07:30:00Z")),
                SortieEntity("b", d, t("2026-10-18T09:00:00Z"), null, editedAt = 1),
            ),
        )
        val lines = CsvExporter.build(listOf(day)).split("\r\n")
        assertEquals(
            "\"Date (UTC)\",\"Sortie\",\"Block Off (UTC)\",\"Block On (UTC)\",\"Duration (H:MM)\",\"Duration (s)\"," +
                "\"Day Total (H:MM)\",\"BA Check (UTC)\",\"Edited\"",
            lines[0],
        )
        assertEquals("\"2026-10-18\",\"1\",\"06:00\",\"07:30\",\"1:30\",\"5400\",\"1:30\",\"05:30\",\"no\"", lines[1])
        assertEquals("\"2026-10-18\",\"2\",\"09:00\",\"OPEN\",\"0:00\",\"0\",\"1:30\",\"05:30\",\"yes\"", lines[2])
    }

    @Test fun `quote escapes embedded quotes`() = assertEquals("\"a\"\"b\"", CsvExporter.quote("a\"b"))

    @Test fun `backup round-trips`() {
        val data = BackupData(
            dutyDays = listOf(DutyDayEntity(d, true, 123L)),
            sorties = listOf(SortieEntity("s1", d, 1000L, 2000L, editedAt = 3000L), SortieEntity("s2", d, 5000L, null)),
            licenses = listOf(LicenseEntity("l1", "IR", LocalDate.of(2027, 1, 31), 45, 7)),
        )
        val decoded = BackupCodec.decode(BackupCodec.encode(data, Instant.EPOCH))
        assertEquals(data, decoded)
    }

    @Test fun `backup rejects foreign or damaged files before import`() {
        assertThrows { BackupCodec.decode("not json") }
        assertThrows { BackupCodec.decode("""{"format":"other","version":1}""") }
        assertThrows { BackupCodec.decode("""{"format":"avlog-backup","version":99}""") }
        assertThrows {
            BackupCodec.decode(
                """{"format":"avlog-backup","version":1,"dutyDays":[],"licenses":[],
                   "sorties":[{"id":"x","dateUtc":"bad-date","blockOff":1,"blockOn":null}]}"""
            )
        }
    }

    @Test fun `BA check valid for 12 hours only`() {
        val now = t("2026-10-19T02:00:00Z")
        assertTrue(BaCheckPolicy.isValid(t("2026-10-18T14:00:01Z"), now)) // across UTC midnight
        assertFalse(BaCheckPolicy.isValid(t("2026-10-18T14:00:00Z"), now))
        assertFalse(BaCheckPolicy.isValid(now + 1000, now)) // future
        assertFalse(BaCheckPolicy.isValid(null, now))
    }

    private fun assertThrows(block: () -> Unit) {
        try {
            block()
        } catch (e: BackupFormatException) {
            return
        }
        throw AssertionError("Expected BackupFormatException")
    }
}
