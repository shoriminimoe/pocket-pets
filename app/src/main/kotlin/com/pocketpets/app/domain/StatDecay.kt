package com.pocketpets.app.domain

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

object StatDecay {
    private const val HUNGER_PER_HOUR = 8f
    private const val CLEAN_PER_HOUR = 3f
    private const val CLEAN_PER_POOP_HOUR = 5f
    private const val HAPPINESS_PER_HOUR = 2f
    private const val ENERGY_PER_HOUR = 4f
    private const val ENERGY_RECOVER_PER_HOUR = 6f
    private const val POOP_DELAY_MS = 30L * 60 * 1000

    private val zone = TimeZone.UTC

    fun tick(pet: Pet, now: Instant): Pet {
        val elapsedMs = now.toEpochMilliseconds() - pet.lastTickAt.toEpochMilliseconds()
        if (elapsedMs <= 0) return pet
        val hours = elapsedMs / 3_600_000.0

        val newHunger = clamp(pet.stats.hunger - (HUNGER_PER_HOUR * hours).toFloat())
        val cleanRate = CLEAN_PER_HOUR + CLEAN_PER_POOP_HOUR * pet.poopCount
        val newCleanliness = clamp(pet.stats.cleanliness - (cleanRate * hours).toFloat())
        val newHappiness = clamp(pet.stats.happiness - (HAPPINESS_PER_HOUR * hours).toFloat())

        // Energy: decays only while awake (mid-window check is good enough at 30-min ticks).
        val midpointMs = pet.lastTickAt.toEpochMilliseconds() + elapsedMs / 2
        val midHour = Instant.fromEpochMilliseconds(midpointMs).toLocalDateTime(zone).hour
        val awake = midHour in 7..21
        val newEnergy = if (awake) {
            clamp(pet.stats.energy - (ENERGY_PER_HOUR * hours).toFloat())
        } else {
            clamp(pet.stats.energy + (ENERGY_RECOVER_PER_HOUR * hours).toFloat())
        }

        // Poop scheduling: spawn one at most when 30 min have passed since lastFedAt.
        // Setting lastFedAt to null after consuming this feeding's poop ensures we
        // don't spawn repeatedly for one feeding.
        val (newPoopCount, newLastFedAt) = if (
            pet.lastFedAt != null &&
            now.toEpochMilliseconds() - pet.lastFedAt.toEpochMilliseconds() >= POOP_DELAY_MS
        ) {
            if (pet.poopCount < Pet.MAX_POOPS) {
                (pet.poopCount + 1) to null
            } else {
                pet.poopCount to null // drop the feeding without spawning if at cap
            }
        } else {
            pet.poopCount to pet.lastFedAt
        }

        return pet.copy(
            stats = PetStats(newHunger, newCleanliness, newHappiness, newEnergy),
            lastTickAt = now,
            poopCount = newPoopCount,
            lastFedAt = newLastFedAt,
        )
    }

    private fun clamp(v: Float): Float = v.coerceIn(0f, 100f)
}
