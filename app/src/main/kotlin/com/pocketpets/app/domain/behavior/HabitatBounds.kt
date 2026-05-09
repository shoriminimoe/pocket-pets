package com.pocketpets.app.domain.behavior

/**
 * Axis-aligned rectangle of dp coordinates that the cat is allowed to occupy.
 * Inclusive on both ends — `clamp` uses `coerceIn` so positions snap exactly
 * to the wall when out-of-range.
 */
data class HabitatBounds(
    val minX: Float,
    val minY: Float,
    val maxX: Float,
    val maxY: Float,
) {
    init {
        require(minX < maxX) { "empty bounds: minX=$minX must be < maxX=$maxX" }
        require(minY < maxY) { "empty bounds: minY=$minY must be < maxY=$maxY" }
    }

    fun clamp(p: Position): Position = Position(p.x.coerceIn(minX, maxX), p.y.coerceIn(minY, maxY))
}
