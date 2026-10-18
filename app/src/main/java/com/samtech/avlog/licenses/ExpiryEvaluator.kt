package com.samtech.avlog.licenses

import com.samtech.avlog.data.LicenseEntity
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Pure expiry logic. Thresholds (days before expiry): warningDays, 7, 1, 0, then EXPIRED.
 * A license is notified when it has crossed a threshold lower than the last one notified, so a delayed or
 * skipped worker run still catches up and nothing fires twice.
 */
object ExpiryEvaluator {
    const val EXPIRED = -1

    fun thresholds(warningDays: Int): List<Int> =
        (listOf(warningDays, 7, 1, 0).filter { it in 0..warningDays } + EXPIRED).distinct().sortedDescending()

    fun daysLeft(expiry: LocalDate, today: LocalDate): Long = ChronoUnit.DAYS.between(today, expiry)

    /** @return the threshold to notify now, or null. */
    fun evaluate(license: LicenseEntity, today: LocalDate): Int? {
        val days = daysLeft(license.expiryDate, today)
        val lowestCrossed = thresholds(license.warningDays)
            .filter { t -> if (t == EXPIRED) days < 0 else days <= t }
            .minOrNull() ?: return null
        val last = license.lastNotifiedThreshold
        return if (last == null || lowestCrossed < last) lowestCrossed else null
    }
}
