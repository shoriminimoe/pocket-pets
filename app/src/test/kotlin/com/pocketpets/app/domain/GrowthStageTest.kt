package com.pocketpets.app.domain

import com.google.common.truth.Truth.assertThat
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlinx.datetime.Instant
import org.junit.Test

class GrowthStageTest {
    private val born = Instant.parse("2026-01-01T00:00:00Z")

    @Test fun `0 days old is BABY`() {
        assertThat(GrowthStage.fromAge(born, born)).isEqualTo(GrowthStage.BABY)
    }

    @Test fun `2 days old is BABY`() {
        assertThat(GrowthStage.fromAge(born, born.plusDur(2.days))).isEqualTo(GrowthStage.BABY)
    }

    @Test fun `exactly 3 days old is JUVENILE`() {
        assertThat(GrowthStage.fromAge(born, born.plusDur(3.days))).isEqualTo(GrowthStage.JUVENILE)
    }

    @Test fun `6 days old is JUVENILE`() {
        assertThat(GrowthStage.fromAge(born, born.plusDur(6.days))).isEqualTo(GrowthStage.JUVENILE)
    }

    @Test fun `exactly 7 days old is ADULT`() {
        assertThat(GrowthStage.fromAge(born, born.plusDur(7.days))).isEqualTo(GrowthStage.ADULT)
    }

    @Test fun `30 days old is ADULT`() {
        assertThat(GrowthStage.fromAge(born, born.plusDur(30.days))).isEqualTo(GrowthStage.ADULT)
    }

    @Test fun `2 days 23 hours is BABY (just under boundary)`() {
        assertThat(GrowthStage.fromAge(born, born.plusDur(2.days + 23.hours))).isEqualTo(GrowthStage.BABY)
    }
}

private fun Instant.plusDur(d: Duration): Instant =
    Instant.fromEpochMilliseconds(this.toEpochMilliseconds() + d.inWholeMilliseconds)
