package com.pocketpets.app.domain

import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.Instant
import org.junit.Test

class StatDecayTest {
    private val t0 = Instant.parse("2026-01-01T12:00:00Z")

    private fun pet(
        hunger: Float = 100f,
        cleanliness: Float = 100f,
        happiness: Float = 100f,
        energy: Float = 100f,
        poopCount: Int = 0,
        lastTickAt: Instant = t0,
        lastFedAt: Instant? = null,
    ) = Pet(
        id = 1,
        name = "Test",
        species = Species.CAT,
        bornAt = t0,
        stats = PetStats(hunger, cleanliness, happiness, energy),
        lastTickAt = lastTickAt,
        isActive = true,
        poopCount = poopCount,
        lastFedAt = lastFedAt,
    )

    private fun Instant.plusHours(h: Int) = Instant.fromEpochMilliseconds(toEpochMilliseconds() + h * 3_600_000L)

    @Test fun `no time elapsed = no change`() {
        val p = pet()
        val out = StatDecay.tick(p, t0)
        assertThat(out.stats).isEqualTo(p.stats)
        assertThat(out.lastTickAt).isEqualTo(t0)
    }

    @Test fun `hunger decays 8 per hour`() {
        val out = StatDecay.tick(pet(), t0.plusHours(1))
        assertThat(out.stats.hunger).isWithin(0.01f).of(92f)
    }

    @Test fun `cleanliness decays 3 per hour with no poops`() {
        val out = StatDecay.tick(pet(poopCount = 0), t0.plusHours(2))
        assertThat(out.stats.cleanliness).isWithin(0.01f).of(94f)
    }

    @Test fun `cleanliness decays faster with poops`() {
        // 3 base + 5 per poop * 2 poops = 13/hr
        val out = StatDecay.tick(pet(poopCount = 2), t0.plusHours(1))
        assertThat(out.stats.cleanliness).isWithin(0.01f).of(87f)
    }

    @Test fun `happiness decays 2 per hour`() {
        val out = StatDecay.tick(pet(), t0.plusHours(3))
        assertThat(out.stats.happiness).isWithin(0.01f).of(94f)
    }

    @Test fun `energy decays 4 per hour while awake`() {
        // t0 = noon UTC, +1h = 1pm — awake.
        val out = StatDecay.tick(pet(), t0.plusHours(1))
        assertThat(out.stats.energy).isWithin(0.01f).of(96f)
    }

    @Test fun `stats clamp at 0`() {
        val out = StatDecay.tick(pet(hunger = 5f), t0.plusHours(2))
        assertThat(out.stats.hunger).isEqualTo(0f)
    }

    @Test fun `lastTickAt advances`() {
        val later = t0.plusHours(5)
        val out = StatDecay.tick(pet(), later)
        assertThat(out.lastTickAt).isEqualTo(later)
    }

    @Test fun `poop spawns 30 minutes after feeding when none scheduled`() {
        val fed = t0
        val now = Instant.fromEpochMilliseconds(t0.toEpochMilliseconds() + 35 * 60_000L)
        val out = StatDecay.tick(pet(poopCount = 0, lastFedAt = fed), now)
        assertThat(out.poopCount).isEqualTo(1)
        assertThat(out.lastFedAt).isNull()
    }

    @Test fun `poop does not spawn before 30 min`() {
        val fed = t0
        val now = Instant.fromEpochMilliseconds(t0.toEpochMilliseconds() + 25 * 60_000L)
        val out = StatDecay.tick(pet(poopCount = 0, lastFedAt = fed), now)
        assertThat(out.poopCount).isEqualTo(0)
    }

    @Test fun `poop count caps at MAX_POOPS`() {
        val fed = t0
        val now = Instant.fromEpochMilliseconds(t0.toEpochMilliseconds() + 24 * 3_600_000L)
        val out = StatDecay.tick(pet(poopCount = Pet.MAX_POOPS, lastFedAt = fed), now)
        assertThat(out.poopCount).isEqualTo(Pet.MAX_POOPS)
    }

    @Test fun `going backwards in time is a no-op`() {
        val earlier = Instant.fromEpochMilliseconds(t0.toEpochMilliseconds() - 1000)
        val out = StatDecay.tick(pet(), earlier)
        assertThat(out).isEqualTo(pet())
    }
}
