package com.pocketpets.app.ui.sprite

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SpriteSheetTest {
    @Test
    fun `accepts positive frame dimensions`() {
        val s = SpriteSheet(resId = 1, frameWidth = 32, frameHeight = 32)
        assertThat(s.frameWidth).isEqualTo(32)
        assertThat(s.frameHeight).isEqualTo(32)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects zero frame width`() {
        SpriteSheet(resId = 1, frameWidth = 0, frameHeight = 32)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects negative frame height`() {
        SpriteSheet(resId = 1, frameWidth = 32, frameHeight = -1)
    }
}
