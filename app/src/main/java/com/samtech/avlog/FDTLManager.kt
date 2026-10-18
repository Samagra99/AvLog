package com.samtech.avlog

import java.time.LocalDate
import java.time.ZoneOffset

object FDTLManager {
    // Shared calculation logic
    fun calculateCumulativeHours(allRecords: List<DayRecordEntity>, days: Int, targetDate: LocalDate = LocalDate.now(
        ZoneOffset.UTC)): Double {
        val cutoffDate = targetDate.minusDays(days.toLong())
        val totalSeconds = allRecords
            .filter {
                val recordDate = LocalDate.parse(it.date)
                recordDate >= cutoffDate && recordDate <= targetDate
            }
            .sumOf { it.dayTotalSeconds }

        return totalSeconds / 3600.0
    }

    // New helper to check if ANY limit is being approached
    fun getApproachingLimitWarning(allRecords: List<DayRecordEntity>): String? {
        val limits = mapOf(7 to 35.0, 14 to 65.0, 28 to 100.0, 90 to 300.0, 365 to 1000.0)

        for ((days, limit) in limits) {
            val current = calculateCumulativeHours(allRecords, days)
            if (current >= limit) return "CRITICAL: $days-day limit EXCEEDED ($current/$limit h)"
            if (current >= (limit - 3.0)) return "Warning: $days-day limit approaching ($current/$limit h)"
        }
        return null
    }
}