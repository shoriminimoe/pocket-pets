package com.pocketpets.app.domain

import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.Test

class MoodTest {
    private fun pet(
        hunger: Float = 80f,
        cleanliness: Float = 80f,
        happiness: Float = 80f,
        energy: Float = 80f,
        poopCount: Int = 0,
    ): Pet =
        Pet(
            id = 1L,
            name = "Test",
            species = Species.CAT,
            bornAt = Instant.parse("2026-01-01T00:00:00Z"),
            stats = PetStats(hunger, cleanliness, happiness, energy),
            lastTickAt = Instant.parse("2026-01-01T00:00:00Z"),
            isActive = true,
            poopCount = poopCount,
            lastFedAt = null,
        )

    private val noon = LocalDateTime(2026, 1, 1, 12, 0).toInstant(TimeZone.UTC)
    private val zone = TimeZone.UTC

    @Test fun `happy when stats good and happiness high`() {
        val m = Mood.from(pet(happiness = 80f), noon, zone)
        assertThat(m).isEqualTo(Mood.HAPPY)
    }

    @Test fun `idle when stats good but happiness mid`() {
        val m = Mood.from(pet(happiness = 50f), noon, zone)
        assertThat(m).isEqualTo(Mood.IDLE)
    }

    @Test fun `hungry beats sad when both apply`() {
        val m = Mood.from(pet(hunger = 20f, happiness = 20f), noon, zone)
        assertThat(m).isEqualTo(Mood.HUNGRY)
    }

    @Test fun `grossed out when low cleanliness`() {
        val m = Mood.from(pet(cleanliness = 20f), noon, zone)
        assertThat(m).isEqualTo(Mood.GROSSED_OUT)
    }

    @Test fun `grossed out when 2 poops even with high cleanliness`() {
        val m = Mood.from(pet(cleanliness = 80f, poopCount = 2), noon, zone)
        assertThat(m).isEqualTo(Mood.GROSSED_OUT)
    }

    @Test fun `hungry beats grossed out`() {
        val m = Mood.from(pet(hunger = 20f, cleanliness = 20f), noon, zone)
        assertThat(m).isEqualTo(Mood.HUNGRY)
    }

    @Test fun `sleepy when low energy`() {
        val m = Mood.from(pet(energy = 20f), noon, zone)
        assertThat(m).isEqualTo(Mood.SLEEPY)
    }

    @Test fun `sleepy during sleep window even at full energy`() {
        val night = LocalDateTime(2026, 1, 1, 23, 0).toInstant(TimeZone.UTC)
        val m = Mood.from(pet(), night, zone)
        assertThat(m).isEqualTo(Mood.SLEEPY)
    }

    @Test fun `sleepy at 6am is true`() {
        val morn = LocalDateTime(2026, 1, 1, 6, 0).toInstant(TimeZone.UTC)
        assertThat(Mood.from(pet(), morn, zone)).isEqualTo(Mood.SLEEPY)
    }

    @Test fun `not sleepy at 7am`() {
        val morn = LocalDateTime(2026, 1, 1, 7, 0).toInstant(TimeZone.UTC)
        assertThat(Mood.from(pet(), morn, zone)).isEqualTo(Mood.HAPPY)
    }

    @Test fun `sad when only happiness low`() {
        val m = Mood.from(pet(happiness = 20f), noon, zone)
        assertThat(m).isEqualTo(Mood.SAD)
    }
}
