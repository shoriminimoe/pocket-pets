package com.pocketpets.app.domain.behavior

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HabitatBoundsTest {
    private val b = HabitatBounds(minX = 0f, minY = 0f, maxX = 200f, maxY = 100f)

    @Test
    fun `clamps x and y inside`() {
        assertThat(b.clamp(Position(50f, 50f))).isEqualTo(Position(50f, 50f))
    }

    @Test
    fun `clamps below min`() {
        assertThat(b.clamp(Position(-10f, -5f))).isEqualTo(Position(0f, 0f))
    }

    @Test
    fun `clamps above max`() {
        assertThat(b.clamp(Position(300f, 999f))).isEqualTo(Position(200f, 100f))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects empty bounds`() {
        HabitatBounds(minX = 100f, minY = 0f, maxX = 100f, maxY = 200f)
    }
}
