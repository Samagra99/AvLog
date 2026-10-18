package com.samtech.avlog

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Entity(tableName = "duty_records")
data class DayRecordEntity(
    @PrimaryKey val date: String,
    val baCheckDone: Boolean,
    val baCheckTime: Long?,
    val dayTotalSeconds: Long,
    val sortiesJson: String // We'll store the list as a JSON string
)

// The DAO (Data Access Object) - This replaces your storage.ts logic
@Dao
interface DutyDao {
    @Query("SELECT * FROM duty_records ORDER BY date DESC")
    fun getAllRecords(): Flow<List<DayRecordEntity>>

    @Query("SELECT * FROM duty_records WHERE date = :date")
    fun getRecordByDate(date: String): Flow<DayRecordEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: DayRecordEntity)

    @Query("SELECT * FROM duty_records")
    suspend fun getAllRecordsOnce(): List<DayRecordEntity>

    @Query("DELETE FROM duty_records WHERE date = :date")
    suspend fun deleteRecordByDate(date: String)
}

@Entity(tableName = "licenses")
data class LicenseEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val expiryDate: Long, // Stored as Millis
    val warningDays: Int = 30
)

@Dao
interface LicenseDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLicense(license: LicenseEntity)

    @Delete
    suspend fun deleteLicense(license: LicenseEntity)

    @Query("SELECT * FROM licenses ORDER BY expiryDate ASC")
    fun getAllLicenses(): Flow<List<LicenseEntity>>

    @Query("SELECT * FROM licenses")
    suspend fun getAllLicensesSync(): List<LicenseEntity>
}