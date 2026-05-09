package com.pocketpets.app.ui.sprite

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SpriteAnimationTest {
    private val sheet = SpriteSheet(resId = 1, frameWidth = 32, frameHeight = 32, rows = 4, cols = 6)

    @Test
    fun `accepts a valid animation`() {
        val a = SpriteAnimation(sheet, row = 0, frameCount = 4)
        assertThat(a.frameMs).isEqualTo(150L)
        assertThat(a.loop).isTrue()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects negative row`() {
        SpriteAnimation(sheet, row = -1, frameCount = 4)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects row past sheet rows`() {
        SpriteAnimation(sheet, row = 4, frameCount = 4)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects zero frameCount`() {
        SpriteAnimation(sheet, row = 0, frameCount = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects frameCount past sheet cols`() {
        SpriteAnimation(sheet, row = 0, frameCount = 7)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects zero frameMs`() {
        SpriteAnimation(sheet, row = 0, frameCount = 4, frameMs = 0)
    }
}
