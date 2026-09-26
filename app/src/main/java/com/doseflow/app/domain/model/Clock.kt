package com.doseflow.app.domain.model

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Clock abstraction allowing deterministic time and time-zone control in unit tests
 * and production execution.
 */
interface AppClock {
    fun now(): Instant
    fun zoneId(): ZoneId
    fun zonedDateTime(): ZonedDateTime = now().atZone(zoneId())
}

/**
 * Production clock wrapping system clock.
 */
class SystemAppClock(
    private val zoneIdProvider: () -> ZoneId = { ZoneId.systemDefault() }
) : AppClock {
    override fun now(): Instant = Instant.now()
    override fun zoneId(): ZoneId = zoneIdProvider()
}

/**
 * Controllable test clock for testing recurrence, DST, leap years, and time jumps.
 */
class TestClock(
    private var currentInstant: Instant,
    private var currentZone: ZoneId
) : AppClock {
    override fun now(): Instant = currentInstant
    override fun zoneId(): ZoneId = currentZone

    fun setInstant(instant: Instant) {
        currentInstant = instant
    }

    fun setZone(zoneId: ZoneId) {
        currentZone = zoneId
    }

    fun advanceBy(duration: java.time.Duration) {
        currentInstant = currentInstant.plus(duration)
    }

    fun setZonedDateTime(zonedDateTime: ZonedDateTime) {
        currentInstant = zonedDateTime.toInstant()
        currentZone = zonedDateTime.zone
    }
}
