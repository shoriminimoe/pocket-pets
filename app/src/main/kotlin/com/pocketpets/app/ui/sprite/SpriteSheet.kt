package com.pocketpets.app.ui.sprite

import androidx.annotation.DrawableRes

/**
 * Static description of a sprite-sheet PNG: which resource, the size of one
 * cell, and the layout (rows × cols) that the renderer can address.
 *
 * `rows`/`cols` are not derived from the bitmap — the asset shipper knows the
 * grid and declares it here so out-of-bounds frame/row math fails loudly at
 * construction time rather than silently rendering garbage.
 */
data class SpriteSheet(
    @DrawableRes val resId: Int,
    val frameWidth: Int,
    val frameHeight: Int,
    val rows: Int,
    val cols: Int,
) {
    init {
        require(frameWidth > 0) { "frameWidth must be positive: $frameWidth" }
        require(frameHeight > 0) { "frameHeight must be positive: $frameHeight" }
        require(rows > 0) { "rows must be positive: $rows" }
        require(cols > 0) { "cols must be positive: $cols" }
    }
}
