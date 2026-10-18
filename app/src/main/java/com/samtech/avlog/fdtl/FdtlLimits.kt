package com.samtech.avlog.fdtl

/**
 * A cumulative flight-time window: at most [limitHours] of block time in any [days] consecutive UTC days.
 */
data class FdtlWindow(val days: Int, val limitHours: Int) {
    val limitSeconds: Long get() = limitHours * 3600L
    val label: String get() = "${days}D"
}

/**
 * The ONLY place FDTL limits are defined.
 *
 * SOURCE: UNVERIFIED. These are the values that were hard-coded in the original app. They have NOT been
 * checked against the current CAR. Before relying on this app, confirm each
 * number and the window definition below against the CAR revision you operate under, and record the
 * clause and revision here.
 *
 * Window definition used throughout the app: N consecutive UTC days ending on, and including, the
 * evaluated date. A sortie belongs to the UTC date of its Block Off.
 */
object FdtlLimits {
    const val VERIFIED = false
    const val SOURCE_NOTE = "Unverified: carried over from the original app; confirm against the current CAR."

    /** Fraction of a limit at which a window is reported as approaching (decision D3). */
    const val WARNING_FRACTION = 0.9

    val windows: List<FdtlWindow> = listOf(
        FdtlWindow(days = 7, limitHours = 35),
        FdtlWindow(days = 14, limitHours = 65),
        FdtlWindow(days = 28, limitHours = 100),
        FdtlWindow(days = 90, limitHours = 300),
        FdtlWindow(days = 365, limitHours = 1000),
    )
}
