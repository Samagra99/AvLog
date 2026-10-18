package com.samtech.avlog.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.Instant

sealed interface StartResult {
    data class Started(val sortieId: String) : StartResult
    data class AlreadyOpen(val sortieId: String) : StartResult
}

sealed interface EditResult {
    data object Ok : EditResult
    data object NotFound : EditResult
    data object EndBeforeStart : EditResult
    data object InFuture : EditResult
    data object Overlaps : EditResult
    data object AnotherSortieOpen : EditResult
}

/**
 * All multi-step writes are @Transaction methods, so read-modify-write races (double taps, two coroutines)
 * can't produce duplicate or lost sorties.
 */
@Dao
abstract class DutyDao {

    // ---- Sorties: reads -------------------------------------------------------------------------

    /** The airborne sortie regardless of date, so UTC midnight or a restart never orphans a flight. */
    @Query("SELECT * FROM sorties WHERE blockOn IS NULL ORDER BY blockOff DESC LIMIT 1")
    abstract fun observeOpenSortie(): Flow<SortieEntity?>

    @Query("SELECT * FROM sorties WHERE blockOn IS NULL ORDER BY blockOff DESC LIMIT 1")
    abstract suspend fun getOpenSortie(): SortieEntity?

    @Query("SELECT * FROM sorties ORDER BY dateUtc DESC, blockOff ASC")
    abstract fun observeAllSorties(): Flow<List<SortieEntity>>

    @Query("SELECT * FROM sorties ORDER BY dateUtc DESC, blockOff ASC")
    abstract suspend fun getAllSorties(): List<SortieEntity>

    @Query("SELECT * FROM sorties WHERE id = :id")
    abstract suspend fun getSortie(id: String): SortieEntity?

    /** Completed sorties only; each duration floored at 0 so a backwards clock can't subtract time. */
    @Query(
        """SELECT COALESCE(SUM(MAX(0, (blockOn - blockOff) / 1000)), 0) FROM sorties
           WHERE blockOn IS NOT NULL AND dateUtc BETWEEN :from AND :to"""
    )
    abstract suspend fun sumSecondsBetween(from: LocalDate, to: LocalDate): Long

    @Query(
        """SELECT dateUtc, COALESCE(SUM(MAX(0, (blockOn - blockOff) / 1000)), 0) AS seconds FROM sorties
           WHERE blockOn IS NOT NULL GROUP BY dateUtc"""
    )
    abstract fun observeDailyTotals(): Flow<List<DayTotal>>

    @Query(
        """SELECT COUNT(*) FROM sorties
           WHERE id != :id AND blockOff < :end AND COALESCE(blockOn, :openEnd) > :start"""
    )
    protected abstract suspend fun countOverlapping(id: String, start: Long, end: Long, openEnd: Long): Int

    // ---- Sorties: raw writes --------------------------------------------------------------------

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertSortie(sortie: SortieEntity)

    @Update
    protected abstract suspend fun updateSortieRow(sortie: SortieEntity)

    @Query("DELETE FROM sorties WHERE id = :id")
    protected abstract suspend fun deleteSortieRow(id: String): Int

    @Upsert
    abstract suspend fun upsertSorties(sorties: List<SortieEntity>)

    @Query("DELETE FROM sorties")
    abstract suspend fun deleteAllSorties()

    // ---- Sorties: transactional operations ------------------------------------------------------

    /** Opens a sortie unless one is already open (this also absorbs a double tap on BLOCK OFF). */
    @Transaction
    open suspend fun startSortie(now: Long): StartResult {
        getOpenSortie()?.let { return StartResult.AlreadyOpen(it.id) }
        val sortie = SortieEntity(dateUtc = utcDate(now), blockOff = now, blockOn = null)
        insertSortie(sortie)
        return StartResult.Started(sortie.id)
    }

    /** Closes the open sortie; returns null (and does nothing) if none is open, so a second tap is harmless. */
    @Transaction
    open suspend fun endSortie(now: Long): SortieEntity? {
        val open = getOpenSortie() ?: return null
        val closed = open.copy(blockOn = now)
        updateSortieRow(closed)
        return closed
    }

    @Transaction
    open suspend fun updateSortie(id: String, blockOff: Long, blockOn: Long?, now: Long): EditResult {
        val existing = getSortie(id) ?: return EditResult.NotFound
        if (blockOff > now || (blockOn != null && blockOn > now)) return EditResult.InFuture
        if (blockOn != null && blockOn < blockOff) return EditResult.EndBeforeStart
        if (blockOn == null) {
            val open = getOpenSortie()
            if (open != null && open.id != id) return EditResult.AnotherSortieOpen
        }
        if (countOverlapping(id, blockOff, blockOn ?: now, now) > 0) return EditResult.Overlaps
        updateSortieRow(
            existing.copy(dateUtc = utcDate(blockOff), blockOff = blockOff, blockOn = blockOn, editedAt = now)
        )
        return EditResult.Ok
    }

    /** Deletes and returns the sortie so the caller can offer Undo. */
    @Transaction
    open suspend fun deleteSortie(id: String): SortieEntity? {
        val existing = getSortie(id) ?: return null
        deleteSortieRow(id)
        return existing
    }

    /** Re-inserts a deleted sortie (Undo). */
    @Upsert
    abstract suspend fun restoreSortie(sortie: SortieEntity)

    // ---- Duty days ------------------------------------------------------------------------------

    @Upsert
    abstract suspend fun upsertDutyDay(day: DutyDayEntity)

    @Upsert
    abstract suspend fun upsertDutyDays(days: List<DutyDayEntity>)

    @Query("SELECT * FROM duty_days WHERE date = :date")
    abstract suspend fun getDutyDay(date: LocalDate): DutyDayEntity?

    @Query("SELECT * FROM duty_days ORDER BY date DESC")
    abstract fun observeDutyDays(): Flow<List<DutyDayEntity>>

    @Query("SELECT * FROM duty_days ORDER BY date DESC")
    abstract suspend fun getDutyDays(): List<DutyDayEntity>

    @Query("SELECT MAX(baCheckTime) FROM duty_days WHERE baCheckDone = 1")
    abstract fun observeLatestBaCheck(): Flow<Long?>

    @Query("SELECT MAX(baCheckTime) FROM duty_days WHERE baCheckDone = 1")
    abstract suspend fun getLatestBaCheck(): Long?

    @Query("DELETE FROM duty_days")
    abstract suspend fun deleteAllDutyDays()

    companion object {
        fun utcDate(epochMillis: Long): LocalDate = Instant.ofEpochMilli(epochMillis).atZone(ZoneOffset.UTC).toLocalDate()
    }
}

@Dao
interface LicenseDao {
    @Upsert
    suspend fun upsert(license: LicenseEntity)

    @Upsert
    suspend fun upsertAll(licenses: List<LicenseEntity>)

    @Query("DELETE FROM licenses WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM licenses")
    suspend fun deleteAll()

    @Query("SELECT * FROM licenses ORDER BY expiryDate ASC, name ASC")
    fun observeAll(): Flow<List<LicenseEntity>>

    @Query("SELECT * FROM licenses ORDER BY expiryDate ASC, name ASC")
    suspend fun getAll(): List<LicenseEntity>

    @Query("SELECT * FROM licenses WHERE id = :id")
    suspend fun get(id: String): LicenseEntity?

    @Query("UPDATE licenses SET lastNotifiedThreshold = :threshold WHERE id = :id")
    suspend fun setLastNotifiedThreshold(id: String, threshold: Int?)
}
