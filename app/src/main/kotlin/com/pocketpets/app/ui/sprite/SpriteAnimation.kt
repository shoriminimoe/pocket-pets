package com.pocketpets.app.ui.sprite

/**
 * One named animation on a sprite sheet: which row to use, how many frames,
 * how fast to advance them, and whether the animation loops.
 */
data class SpriteAnimation(
    val sheet: SpriteSheet,
    val row: Int,
    val frameCount: Int,
    val frameMs: Long = 150L,
    val loop: Boolean = true,
) {
    init {
        require(row >= 0) { "row must be non-negative: $row" }
        require(frameCount > 0) { "frameCount must be positive: $frameCount" }
        require(frameMs > 0) { "frameMs must be positive: $frameMs" }
    }
}
