package com.samtech.avlog.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import java.time.LocalDate
import java.util.UUID

/** One row per UTC duty day; holds the BA check. Flying lives in [SortieEntity]. */
@Entity(tableName = "duty_days")
data class DutyDayEntity(
    @PrimaryKey val date: LocalDate,
    val baCheckDone: Boolean,
    val baCheckTime: Long?,
)

/**
 * One row per sortie. [dateUtc] is the UTC date of Block Off (decision D2); a flight that crosses 00:00Z
 * stays in the day it started. [blockOn] is null while airborne.
 */
@Entity(tableName = "sorties", indices = [Index("dateUtc")])
data class SortieEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val dateUtc: LocalDate,
    val blockOff: Long,
    val blockOn: Long?,
    val editedAt: Long? = null,
)

@Entity(tableName = "licenses")
data class LicenseEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    /** A calendar date with no time zone; stored as ISO-8601 text. */
    val expiryDate: LocalDate,
    val warningDays: Int = DEFAULT_WARNING_DAYS,
    /** Lowest expiry threshold (in days) already notified, or null if none since the date was last set. */
    val lastNotifiedThreshold: Int? = null,
) {
    companion object { const val DEFAULT_WARNING_DAYS = 30 }
}

/** Sum of completed block seconds for one UTC day. */
data class DayTotal(
    @ColumnInfo(name = "dateUtc") val date: LocalDate,
    @ColumnInfo(name = "seconds") val seconds: Long,
)

class DateConverters {
    @TypeConverter
    fun fromLocalDate(date: LocalDate?): String? = date?.toString()

    @TypeConverter
    fun toLocalDate(value: String?): LocalDate? = value?.let(LocalDate::parse)
}

/**
 * The untouched v4 `duty_records` table, renamed by Migration4To5 and kept for one release as a safety net.
 * It must be declared because Room rejects unknown tables after a migration. Remove this entity and add a
 * v5->v6 migration with `DROP TABLE duty_records_v4_backup` in the next schema version.
 */
@Entity(tableName = "duty_records_v4_backup")
data class DutyRecordV4Backup(
    @PrimaryKey val date: String,
    val baCheckDone: Boolean,
    val baCheckTime: Long?,
    val dayTotalSeconds: Long,
    val sortiesJson: String,
)
