package com.samtech.avlog

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.samtech.avlog.data.DateConverters
import com.samtech.avlog.data.DutyDao
import com.samtech.avlog.data.DutyDayEntity
import com.samtech.avlog.data.DutyRecordV4Backup
import com.samtech.avlog.data.LicenseDao
import com.samtech.avlog.data.LicenseEntity
import com.samtech.avlog.data.SortieEntity

/**
 * Schema history lives in app/schemas. Every version bump needs a Migration in data/Migrations.kt and a
 * MigrationTest; there is deliberately no destructive fallback.
 *
 * Kept in this package because the exported schema folder is keyed by this class's fully-qualified name.
 */
@Database(
    entities = [DutyDayEntity::class, SortieEntity::class, LicenseEntity::class, DutyRecordV4Backup::class],
    version = 5,
)
@TypeConverters(DateConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dutyDao(): DutyDao
    abstract fun licenseDao(): LicenseDao

    companion object {
        const val NAME = "duty_tracker_db"
    }
}