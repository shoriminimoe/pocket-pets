package com.pocketpets.app.ui.sprite

import androidx.annotation.DrawableRes

/** Static description of a sprite-sheet PNG: which resource and the size of one cell. */
data class SpriteSheet(
    @DrawableRes val resId: Int,
    val frameWidth: Int,
    val frameHeight: Int,
) {
    init {
        require(frameWidth > 0) { "frameWidth must be positive: $frameWidth" }
        require(frameHeight > 0) { "frameHeight must be positive: $frameHeight" }
    }
}
