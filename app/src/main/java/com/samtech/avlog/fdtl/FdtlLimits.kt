package com.samtech.avlog.fdtl

/**
 * A cumulative flight-time window: at most [limitHours] of block time either in any [days] consecutive UTC
 * days (calendar window) or in any [rollingHours] consecutive hours (rolling window). Exactly one of
 * [days] / [rollingHours] is set.
 */
data class FdtlWindow(val limitHours: Int, val days: Int = 0, val rollingHours: Int = 0) {
    init {
        require((days > 0) xor (rollingHours > 0)) { "Set exactly one of days or rollingHours" }
    }

    val isRolling: Boolean get() = rollingHours > 0
    val limitSeconds: Long get() = limitHours * 3600L

    /** Stable identifier and short label, e.g. "24H", "7D". */
    val key: String get() = if (isRolling) "${rollingHours}H" else "${days}D"
}

/**
 * The ONLY place FDTL limits are defined.
 *
 * Sources:
 * - 7 / 14 / 28 / 90 / 365 days: CAR "Maximum Cumulative Flight Time Limitations", sub-paras 9.1-9.5
 *   (35 / 65 / 100 / 300 / 1000 h), confirmed by the user on 19 Oct 2026. Record the CAR revision here.
 * - 24 hours: 11 h, as specified by the user (not part of the 9.1-9.5 table).
 *
 * Window definitions used throughout the app:
 * - Calendar windows: N consecutive UTC days ending on, and including, the evaluated date. A sortie belongs
 *   to the UTC date of its Block Off.
 * - Rolling 24 h: block time overlapping the 24 hours ending now (sorties partly inside count partly).
 */
object FdtlLimits {
    /** Fraction of a limit at which a window is reported as approaching (decision D3). */
    const val WARNING_FRACTION = 0.9

    val windows: List<FdtlWindow> = listOf(
        FdtlWindow(limitHours = 11, rollingHours = 24),
        FdtlWindow(limitHours = 35, days = 7),     // 9.1
        FdtlWindow(limitHours = 65, days = 14),    // 9.2
        FdtlWindow(limitHours = 100, days = 28),   // 9.3
        FdtlWindow(limitHours = 300, days = 90),   // 9.4
        FdtlWindow(limitHours = 1000, days = 365), // 9.5
    )
}
