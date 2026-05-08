package com.pocketpets.app.domain

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

enum class Mood {
    IDLE, HAPPY, HUNGRY, GROSSED_OUT, SAD, SLEEPY;

    companion object {
        // Sleep window is [22:00, 07:00) device-local
        private const val SLEEP_START_HOUR = 22
        private const val WAKE_HOUR = 7

        fun from(pet: Pet, now: Instant, zone: TimeZone): Mood {
            val hour = now.toLocalDateTime(zone).hour
            val inSleepWindow = hour >= SLEEP_START_HOUR || hour < WAKE_HOUR

            // Priority: HUNGRY > GROSSED_OUT > SAD > SLEEPY > HAPPY > IDLE
            return when {
                pet.stats.hunger < 30f -> HUNGRY
                pet.stats.cleanliness < 30f || pet.poopCount >= 2 -> GROSSED_OUT
                pet.stats.happiness < 30f -> SAD
                pet.stats.energy < 30f || inSleepWindow -> SLEEPY
                pet.stats.happiness > 70f -> HAPPY
                else -> IDLE
            }
        }
    }
}
