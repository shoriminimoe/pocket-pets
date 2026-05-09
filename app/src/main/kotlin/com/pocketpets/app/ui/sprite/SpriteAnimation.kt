package com.pocketpets.app.ui.sprite

/**
 * One named animation on a sprite sheet: which row to use, how many frames,
 * how fast to advance them, and whether the animation loops.
 *
 * Validates against the parent [SpriteSheet]'s grid so a mistyped row or
 * frame count fails at construction time rather than rendering garbage.
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
        require(row < sheet.rows) {
            "row $row is out of bounds for sheet with ${sheet.rows} rows"
        }
        require(frameCount > 0) { "frameCount must be positive: $frameCount" }
        require(frameCount <= sheet.cols) {
            "frameCount $frameCount exceeds sheet's ${sheet.cols} columns"
        }
        require(frameMs > 0) { "frameMs must be positive: $frameMs" }
    }
}
