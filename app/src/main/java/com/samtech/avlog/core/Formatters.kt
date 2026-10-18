package com.samtech.avlog.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * All log/CSV formatting uses Locale.ROOT (Latin digits, no locale-specific separators) and cached,
 * thread-safe DateTimeFormatters.
 */
object Formatters {
    private val utcHhMm: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT).withZone(ZoneOffset.UTC)
    private val utcHhMmSs: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT).withZone(ZoneOffset.UTC)
    private val isoDate: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun utcTime(epochMillis: Long): String = utcHhMm.format(Instant.ofEpochMilli(epochMillis))

    fun utcClock(instant: Instant): String = ltr(utcHhMmSs.format(instant) + " Z")

    /** Wraps in a Unicode LTR isolate so RTL locales don't reorder "18:42 – 03:00" or "12:00 Z". UI only. */
    fun ltr(s: String): String = "\u2066$s\u2069"

    /** Latin digits for counts and labels shown next to times (avoids mixed digit systems). */
    fun num(n: Number): String = n.toString()

    fun isoDate(date: LocalDate): String = isoDate.format(date)

    /** Display date for humans (localized month names). */
    fun displayDate(date: LocalDate, locale: Locale = Locale.getDefault()): String =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(date)

    /** HH:MM:SS, negative input clamps to 00:00:00. */
    fun hms(totalSeconds: Long): String {
        val s = totalSeconds.coerceAtLeast(0L)
        return pad2(s / 3600) + ":" + pad2((s % 3600) / 60) + ":" + pad2(s % 60)
    }

    /**
     * H:MM. Consumed time should round UP to the next minute and remaining time round DOWN, so a pilot
     * never sees more margin than they actually have.
     */
    fun hMm(totalSeconds: Long, roundUp: Boolean): String {
        val s = totalSeconds.coerceAtLeast(0L)
        val minutes = if (roundUp) (s + 59) / 60 else s / 60
        return (minutes / 60).toString() + ":" + pad2(minutes % 60)
    }

    private fun pad2(v: Long): String = if (v < 10) "0$v" else v.toString()
}
