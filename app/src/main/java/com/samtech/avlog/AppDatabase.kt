package com.samtech.avlog

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.samtech.avlog.ui.theme.Converters

@Database(entities = [DayRecordEntity::class, LicenseEntity::class], version = 4)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dutyDao(): DutyDao
    abstract fun licenseDao(): LicenseDao

    companion object {
        const val NAME = "duty_tracker_db"
    }
}