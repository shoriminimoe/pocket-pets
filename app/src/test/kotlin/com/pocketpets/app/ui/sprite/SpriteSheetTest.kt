package com.pocketpets.app.ui.sprite

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SpriteSheetTest {
    @Test
    fun `accepts positive frame and grid dimensions`() {
        val s = SpriteSheet(resId = 1, frameWidth = 32, frameHeight = 32, rows = 2, cols = 4)
        assertThat(s.frameWidth).isEqualTo(32)
        assertThat(s.frameHeight).isEqualTo(32)
        assertThat(s.rows).isEqualTo(2)
        assertThat(s.cols).isEqualTo(4)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects zero frame width`() {
        SpriteSheet(resId = 1, frameWidth = 0, frameHeight = 32, rows = 1, cols = 1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects negative frame height`() {
        SpriteSheet(resId = 1, frameWidth = 32, frameHeight = -1, rows = 1, cols = 1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects zero rows`() {
        SpriteSheet(resId = 1, frameWidth = 32, frameHeight = 32, rows = 0, cols = 1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects zero cols`() {
        SpriteSheet(resId = 1, frameWidth = 32, frameHeight = 32, rows = 1, cols = 0)
    }
}
