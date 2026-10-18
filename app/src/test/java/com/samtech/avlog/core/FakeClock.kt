package com.samtech.avlog.core

import java.time.Duration
import java.time.Instant

/** Test clock whose time only moves when told to. */
class FakeClock(var instant: Instant = Instant.parse("2026-10-18T12:00:00Z")) : Clock {
    override fun now(): Instant = instant
    fun advance(d: Duration) { instant = instant.plus(d) }
    fun set(iso: String) { instant = Instant.parse(iso) }
}
