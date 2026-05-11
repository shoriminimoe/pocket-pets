package com.pocketpets.app.ui.pet

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HabitatTest {
    @Test
    fun `bounds reserve room for sprite on right edge`() {
        val habitat = computeHabitat(widthDp = 400f, heightDp = 600f, spriteDp = 256f)
        // The sprite renders in a box of [position, position + spriteDp].
        // For the box to fit fully on screen, position must be <= widthDp - spriteDp.
        assertThat(habitat.bounds.maxX + 256f).isAtMost(400f)
    }

    @Test
    fun `bounds reserve room for sprite on bottom edge`() {
        val habitat = computeHabitat(widthDp = 400f, heightDp = 600f, spriteDp = 256f)
        // Floor bottom is 600 * 0.85 = 510. Sprite must end at or above the floor bottom.
        assertThat(habitat.bounds.maxY + 256f).isAtMost(510f)
    }

    @Test
    fun `larger sprite shrinks playable bounds`() {
        val small = computeHabitat(widthDp = 400f, heightDp = 600f, spriteDp = 64f)
        val large = computeHabitat(widthDp = 400f, heightDp = 600f, spriteDp = 256f)
        assertThat(large.bounds.maxX).isLessThan(small.bounds.maxX)
        assertThat(large.bounds.maxY).isLessThan(small.bounds.maxY)
    }

    @Test
    fun `bowl anchor is fully inside playable bounds`() {
        val habitat = computeHabitat(widthDp = 400f, heightDp = 600f, spriteDp = 256f)
        val bowl = habitat.anchors.bowl
        assertThat(bowl.x).isAtLeast(habitat.bounds.minX)
        assertThat(bowl.x).isAtMost(habitat.bounds.maxX)
        assertThat(bowl.y).isAtLeast(habitat.bounds.minY)
        assertThat(bowl.y).isAtMost(habitat.bounds.maxY)
    }

    @Test
    fun `bed anchor is fully inside playable bounds`() {
        val habitat = computeHabitat(widthDp = 400f, heightDp = 600f, spriteDp = 256f)
        val bed = habitat.anchors.bed
        assertThat(bed.x).isAtLeast(habitat.bounds.minX)
        assertThat(bed.x).isAtMost(habitat.bounds.maxX)
        assertThat(bed.y).isAtLeast(habitat.bounds.minY)
        assertThat(bed.y).isAtMost(habitat.bounds.maxY)
    }
}
