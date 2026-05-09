package com.pocketpets.app.testing

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class FakeClock(
    initial: Instant,
) : Clock {
    private var current: Instant = initial

    override fun now(): Instant = current

    fun advanceBy(durationMs: Long) {
        current = Instant.fromEpochMilliseconds(current.toEpochMilliseconds() + durationMs)
    }

    fun setTo(instant: Instant) {
        current = instant
    }
}
