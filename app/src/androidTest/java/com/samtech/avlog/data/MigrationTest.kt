package com.samtech.avlog.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.samtech.avlog.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate

/** P2.2: v4 -> v5 against the committed 4.json schema. */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val dbName = "migration-test"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    private fun t(iso: String) = Instant.parse(iso).toEpochMilli()

    private fun openMigrated(): AppDatabase {
        helper.runMigrationsAndValidate(dbName, 5, true, Migration4To5).close()
        return Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java, dbName
        ).addMigrations(*ALL_MIGRATIONS).build().also { helper.closeWhenFinished(it) }
    }

    @Test
    fun emptyDatabase() = runTest {
        helper.createDatabase(dbName, 4).close()
        val db = openMigrated()
        assertTrue(db.dutyDao().getAllSorties().isEmpty())
        assertTrue(db.licenseDao().getAll().isEmpty())
    }

    @Test
    fun normalDataOpenSortieCorruptRowAndLicense() = runTest {
        helper.createDatabase(dbName, 4).apply {
            val ok = """[{"id":"s1","blockOff":${t("2026-10-17T06:00:00Z")},"blockOn":${t("2026-10-17T07:30:00Z")}},""" +
                """{"id":"s2","blockOff":${t("2026-10-17T09:00:00Z")},"blockOn":${t("2026-10-17T10:00:00Z")}}]"""
            val open = """[{"id":"s3","blockOff":${t("2026-10-18T06:00:00Z")}}]"""
            execSQL("INSERT INTO duty_records VALUES ('2026-10-17', 1, ${t("2026-10-17T05:00:00Z")}, 9000, '$ok')")
            execSQL("INSERT INTO duty_records VALUES ('2026-10-18', 1, ${t("2026-10-18T05:00:00Z")}, 0, '$open')")
            execSQL("INSERT INTO duty_records VALUES ('2026-10-16', 1, 1, 0, '{this is not json')")
            // DatePicker stored UTC midnight for 31 Jan 2027.
            execSQL("INSERT INTO licenses VALUES ('l1', 'IR', ${t("2027-01-31T00:00:00Z")}, 30)")
            close()
        }

        val db = openMigrated()
        val dao = db.dutyDao()

        val sorties = dao.getAllSorties()
        assertEquals(setOf("s1", "s2", "s3"), sorties.map { it.id }.toSet())
        assertEquals(LocalDate.of(2026, 10, 17), sorties.first { it.id == "s1" }.dateUtc)

        val open = dao.observeOpenSortie().first()
        assertEquals("s3", open?.id)
        assertNull(open?.blockOn)

        // Corrupt row: sorties skipped, BA day still migrated, nothing crashed.
        assertEquals(3, dao.getDutyDays().size)
        assertEquals(9000L, dao.sumSecondsBetween(LocalDate.of(2026, 10, 17), LocalDate.of(2026, 10, 17)))

        val license = db.licenseDao().getAll().single()
        assertEquals(LocalDate.of(2027, 1, 31), license.expiryDate)
        assertEquals(30, license.warningDays)
        assertNull(license.lastNotifiedThreshold)

        // Old table kept for one release.
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM duty_records_v4_backup").use {
            it.moveToFirst(); assertEquals(3, it.getInt(0))
        }
    }
}
