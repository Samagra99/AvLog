package com.samtech.avlog.core

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Single source of "now". Production code must never call System.currentTimeMillis(),
 * LocalDate.now() or similar directly, so that time-dependent logic can be unit-tested.
 */
interface Clock {
    fun now(): Instant
}

object SystemClock : Clock {
    override fun now(): Instant = Instant.now()
}

fun Clock.nowMillis(): Long = now().toEpochMilli()

fun Clock.utcToday(): LocalDate = now().atZone(ZoneOffset.UTC).toLocalDate()

fun Clock.localToday(zone: ZoneId = ZoneId.systemDefault()): LocalDate = now().atZone(zone).toLocalDate()

/**
 * Emits the current instant aligned to wall-clock second boundaries. Cold and infinite; share it with
 * `stateIn(scope, SharingStarted.WhileSubscribed(5000), ...)` so it stops ticking in the background.
 */
fun Clock.secondTicks(): Flow<Instant> = flow {
    while (true) {
        val now = now()
        emit(now)
        delay(1000L - Math.floorMod(now.toEpochMilli(), 1000L))
    }
}

/** Emits now, then on every wall-clock minute boundary. Used by the rolling 24 h FDTL window. */
fun Clock.minuteTicks(): Flow<Instant> = flow {
    while (true) {
        val now = now()
        emit(now)
        delay(60_000L - Math.floorMod(now.toEpochMilli(), 60_000L))
    }
}

/**
 * "DayTicker": emits the current date in [zone] and again whenever it changes. Re-checks at least once a
 * minute because coroutine delays don't advance while the device is in deep sleep.
 */
fun Clock.dateTicks(zone: ZoneId = ZoneOffset.UTC): Flow<LocalDate> = flow {
    while (true) {
        val now = now()
        val today = now.atZone(zone).toLocalDate()
        emit(today)
        val nextMidnight = today.plusDays(1).atStartOfDay(zone).toInstant()
        val untilMidnight = nextMidnight.toEpochMilli() - now.toEpochMilli()
        delay(untilMidnight.coerceIn(1L, 60_000L))
    }
}.distinctUntilChanged()
