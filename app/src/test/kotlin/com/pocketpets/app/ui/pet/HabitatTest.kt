package com.pocketpets.app.ui.pet

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HabitatTest {
    // 600 * 0.40 = 240 ; 600 * 0.15 = 90  (the pre-measurement floor band).
    private val typicalTop = 240f
    private val typicalBottom = 90f

    @Test
    fun `bounds reserve room for sprite on right edge`() {
        val habitat =
            computeHabitat(
                widthDp = 400f,
                heightDp = 600f,
                topReservedDp = typicalTop,
                bottomReservedDp = typicalBottom,
                spriteDp = 256f,
            )
        assertThat(habitat.bounds.maxX + 256f).isAtMost(400f)
    }

    @Test
    fun `bounds reserve room for sprite on bottom edge`() {
        val habitat =
            computeHabitat(
                widthDp = 400f,
                heightDp = 600f,
                topReservedDp = typicalTop,
                bottomReservedDp = typicalBottom,
                spriteDp = 256f,
            )
        // Floor bottom = 600 - 90 = 510. Sprite must end at or above 510.
        assertThat(habitat.bounds.maxY + 256f).isAtMost(510f)
    }

    @Test
    fun `larger sprite shrinks playable bounds`() {
        val small =
            computeHabitat(400f, 600f, topReservedDp = typicalTop, bottomReservedDp = typicalBottom, spriteDp = 64f)
        val large =
            computeHabitat(400f, 600f, topReservedDp = typicalTop, bottomReservedDp = typicalBottom, spriteDp = 256f)
        assertThat(large.bounds.maxX).isLessThan(small.bounds.maxX)
        assertThat(large.bounds.maxY).isLessThan(small.bounds.maxY)
    }

    @Test
    fun `taller bottom overlay shrinks bottom of bounds`() {
        val short =
            computeHabitat(400f, 600f, topReservedDp = typicalTop, bottomReservedDp = 90f, spriteDp = 256f)
        val tall =
            computeHabitat(400f, 600f, topReservedDp = typicalTop, bottomReservedDp = 180f, spriteDp = 256f)
        assertThat(tall.bounds.maxY).isLessThan(short.bounds.maxY)
    }

    @Test
    fun `taller top overlay shrinks top of bounds`() {
        val short =
            computeHabitat(400f, 600f, topReservedDp = 100f, bottomReservedDp = typicalBottom, spriteDp = 256f)
        val tall =
            computeHabitat(400f, 600f, topReservedDp = 240f, bottomReservedDp = typicalBottom, spriteDp = 256f)
        assertThat(tall.bounds.minY).isGreaterThan(short.bounds.minY)
    }

    @Test
    fun `bowl anchor is fully inside playable bounds`() {
        val habitat =
            computeHabitat(400f, 600f, typicalTop, typicalBottom, 256f)
        val bowl = habitat.anchors.bowl
        assertThat(bowl.x).isAtLeast(habitat.bounds.minX)
        assertThat(bowl.x).isAtMost(habitat.bounds.maxX)
        assertThat(bowl.y).isAtLeast(habitat.bounds.minY)
        assertThat(bowl.y).isAtMost(habitat.bounds.maxY)
    }

    @Test
    fun `bed anchor is fully inside playable bounds`() {
        val habitat =
            computeHabitat(400f, 600f, typicalTop, typicalBottom, 256f)
        val bed = habitat.anchors.bed
        assertThat(bed.x).isAtLeast(habitat.bounds.minX)
        assertThat(bed.x).isAtMost(habitat.bounds.maxX)
        assertThat(bed.y).isAtLeast(habitat.bounds.minY)
        assertThat(bed.y).isAtMost(habitat.bounds.maxY)
    }

    @Test
    fun `bounds and anchors stay valid when overlays exceed available height`() {
        // Stress case: top + bottom reserved > screen height, sprite > screen width.
        // HabitatBounds.init would throw if the coerceAtLeast clamps weren't there.
        val habitat =
            computeHabitat(
                widthDp = 200f,
                heightDp = 200f,
                topReservedDp = 180f,
                bottomReservedDp = 100f,
                spriteDp = 192f,
            )
        assertThat(habitat.bounds.minX).isLessThan(habitat.bounds.maxX)
        assertThat(habitat.bounds.minY).isLessThan(habitat.bounds.maxY)
        // Anchors stay inside the (degenerate) bounds.
        val bed = habitat.anchors.bed
        val bowl = habitat.anchors.bowl
        assertThat(bed.x).isAtLeast(habitat.bounds.minX)
        assertThat(bed.x).isAtMost(habitat.bounds.maxX)
        assertThat(bed.y).isAtLeast(habitat.bounds.minY)
        assertThat(bed.y).isAtMost(habitat.bounds.maxY)
        assertThat(bowl.x).isAtLeast(habitat.bounds.minX)
        assertThat(bowl.x).isAtMost(habitat.bounds.maxX)
        assertThat(bowl.y).isAtLeast(habitat.bounds.minY)
        assertThat(bowl.y).isAtMost(habitat.bounds.maxY)
    }
}
