package com.pocketpets.app.ui.inventory

import com.pocketpets.app.domain.behavior.Position

/**
 * Axis-aligned rectangle in dp coordinates. Pure Kotlin so [dropTargetAt] is
 * unit-testable without Compose. Inclusive bounds on all four sides.
 */
data class DpRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    fun contains(p: Position): Boolean = p.x in left..right && p.y in top..bottom

    fun center(): Pair<Float, Float> = ((left + right) / 2f) to ((top + bottom) / 2f)
}
