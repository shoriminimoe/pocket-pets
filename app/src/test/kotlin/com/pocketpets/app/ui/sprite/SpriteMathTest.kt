package com.pocketpets.app.ui.sprite

import androidx.compose.ui.unit.IntOffset
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SpriteMathTest {
    private val sheet = SpriteSheet(resId = 1, frameWidth = 32, frameHeight = 32, rows = 4, cols = 6)

    @Test
    fun `frame zero south is the origin`() {
        val a = SpriteAnimation(sheet, row = 0, frameCount = 4)
        assertThat(srcOffsetFor(frame = 0, animation = a, facing = Direction.SOUTH))
            .isEqualTo(IntOffset(0, 0))
    }

    @Test
    fun `frame index advances along x`() {
        val a = SpriteAnimation(sheet, row = 0, frameCount = 4)
        assertThat(srcOffsetFor(frame = 3, animation = a, facing = Direction.SOUTH))
            .isEqualTo(IntOffset(96, 0))
    }

    @Test
    fun `animation row contributes to y`() {
        val a = SpriteAnimation(sheet, row = 2, frameCount = 4)
        assertThat(srcOffsetFor(frame = 1, animation = a, facing = Direction.SOUTH))
            .isEqualTo(IntOffset(32, 64))
    }

    @Test
    fun `north adds row offset 1`() {
        val a = SpriteAnimation(sheet, row = 0, frameCount = 4)
        assertThat(srcOffsetFor(frame = 0, animation = a, facing = Direction.NORTH))
            .isEqualTo(IntOffset(0, 32))
    }

    @Test
    fun `west adds row offset 2 east adds 3`() {
        val a = SpriteAnimation(sheet, row = 0, frameCount = 4)
        assertThat(srcOffsetFor(0, a, Direction.WEST)).isEqualTo(IntOffset(0, 64))
        assertThat(srcOffsetFor(0, a, Direction.EAST)).isEqualTo(IntOffset(0, 96))
    }

    @Test
    fun `requireFacingFits passes when total row is in range`() {
        val a = SpriteAnimation(sheet, row = 0, frameCount = 4)
        // 0 + 3 (EAST) = 3, sheet has 4 rows -> fine.
        requireFacingFits(a, Direction.EAST)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `requireFacingFits throws when facing pushes row out of bounds`() {
        // Sheet has 2 rows; a SOUTH animation on row 1 + facing NORTH (offset 1) = row 2 -> OOB.
        val tinySheet = SpriteSheet(resId = 1, frameWidth = 32, frameHeight = 32, rows = 2, cols = 1)
        val a = SpriteAnimation(tinySheet, row = 1, frameCount = 1)
        requireFacingFits(a, Direction.NORTH)
    }
}
