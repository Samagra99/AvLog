package com.samtech.avlog.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.samtech.avlog.AppDatabase
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate

/** P2.3/P2.4: transactional DAO behaviour against an in-memory database. */
@RunWith(AndroidJUnit4::class)
class DutyDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: DutyDao

    private fun t(iso: String) = Instant.parse(iso).toEpochMilli()

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java).build()
        dao = db.dutyDao()
    }

    @After fun tearDown() = db.close()

    @Test fun doubleTapBlockOffCreatesOneSortie() = runTest {
        val now = t("2026-10-18T06:00:00Z")
        val results = (1..5).map { async { dao.startSortie(now) } }.awaitAll()
        assertEquals(1, results.count { it is StartResult.Started })
        assertEquals(1, dao.getAllSorties().size)
    }

    @Test fun doubleTapBlockOnClosesOnce() = runTest {
        dao.startSortie(t("2026-10-18T06:00:00Z"))
        val first = dao.endSortie(t("2026-10-18T07:00:00Z"))
        val second = dao.endSortie(t("2026-10-18T07:00:05Z"))
        assertNotNull(first)
        assertNull(second)
        assertEquals(t("2026-10-18T07:00:00Z"), dao.getAllSorties().single().blockOn)
    }

    @Test fun sortieCrossingMidnightStaysOnStartDayAndStaysOpen() = runTest {
        dao.startSortie(t("2026-10-18T23:59:30Z"))
        val open = dao.observeOpenSortie().first()
        assertEquals(LocalDate.of(2026, 10, 18), open!!.dateUtc)
        dao.endSortie(t("2026-10-19T00:30:00Z"))
        assertEquals(1830L, dao.sumSecondsBetween(LocalDate.of(2026, 10, 18), LocalDate.of(2026, 10, 18)))
        assertEquals(0L, dao.sumSecondsBetween(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 19)))
    }

    @Test fun negativeDurationIsFlooredAtZero() = runTest {
        dao.startSortie(t("2026-10-18T06:00:00Z"))
        dao.endSortie(t("2026-10-18T05:00:00Z")) // clock jumped back
        assertEquals(0L, dao.sumSecondsBetween(LocalDate.of(2026, 10, 18), LocalDate.of(2026, 10, 18)))
    }

    @Test fun editValidation() = runTest {
        val now = t("2026-10-18T20:00:00Z")
        dao.startSortie(t("2026-10-18T06:00:00Z")); dao.endSortie(t("2026-10-18T07:00:00Z"))
        dao.startSortie(t("2026-10-18T09:00:00Z")); dao.endSortie(t("2026-10-18T10:00:00Z"))
        val (a, b) = dao.getAllSorties().sortedBy { it.blockOff }

        assertEquals(EditResult.EndBeforeStart, dao.updateSortie(a.id, t("2026-10-18T07:00:00Z"), t("2026-10-18T06:00:00Z"), now))
        assertEquals(EditResult.InFuture, dao.updateSortie(a.id, t("2026-10-18T06:00:00Z"), t("2026-10-18T21:00:00Z"), now))
        assertEquals(EditResult.Overlaps, dao.updateSortie(a.id, t("2026-10-18T06:00:00Z"), t("2026-10-18T09:30:00Z"), now))
        assertEquals(EditResult.Ok, dao.updateSortie(a.id, t("2026-10-18T06:10:00Z"), t("2026-10-18T07:20:00Z"), now))
        val edited = dao.getSortie(a.id)!!
        assertEquals(now, edited.editedAt)
        assertEquals(b, dao.getSortie(b.id))
    }

    @Test fun deleteReturnsEntityForUndo() = runTest {
        dao.startSortie(t("2026-10-18T06:00:00Z")); dao.endSortie(t("2026-10-18T07:00:00Z"))
        val s = dao.getAllSorties().single()
        val deleted = dao.deleteSortie(s.id)
        assertTrue(dao.getAllSorties().isEmpty())
        dao.restoreSortie(deleted!!)
        assertEquals(listOf(s), dao.getAllSorties())
    }

    @Test fun dailyTotalsMatchSumBetween() = runTest {
        dao.startSortie(t("2026-10-17T06:00:00Z")); dao.endSortie(t("2026-10-17T08:00:00Z"))
        dao.startSortie(t("2026-10-18T06:00:00Z")); dao.endSortie(t("2026-10-18T07:00:00Z"))
        dao.startSortie(t("2026-10-18T09:00:00Z")) // open: not counted
        val totals = dao.observeDailyTotals().first().associate { it.date to it.seconds }
        assertEquals(mapOf(LocalDate.of(2026, 10, 17) to 7200L, LocalDate.of(2026, 10, 18) to 3600L), totals)
        assertEquals(10800L, dao.sumSecondsBetween(LocalDate.of(2026, 10, 17), LocalDate.of(2026, 10, 18)))
    }
}
