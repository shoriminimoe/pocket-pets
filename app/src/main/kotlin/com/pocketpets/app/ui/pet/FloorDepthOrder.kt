package com.pocketpets.app.ui.pet

/**
 * A floor-level habitat sprite tagged with its top-left Y position in dp.
 * Used by [floorSpriteOrder] to produce a Y-sorted draw list so sprites
 * lower on the screen (larger bottom-Y) render on top.
 *
 * The "bottom Y" — `topLeftY + heightDp` — is the sprite's feet line. A
 * sprite whose feet are visually closer to the viewer (further down the
 * screen) must draw after sprites whose feet are higher up, otherwise it
 * looks like it's behind something that's actually further away.
 *
 * Fixes #30: the bowl previously always drew above the cat regardless of
 * their relative positions because [PetScreen] emitted the bowl after the
 * cat in source order.
 */
sealed class FloorSprite {
    /** Y dp of the sprite's bottom edge — the "feet" line used for depth sort. */
    abstract val bottomY: Float

    data class Cat(
        val topLeftY: Float,
        val spriteSizeDp: Float,
    ) : FloorSprite() {
        override val bottomY: Float get() = topLeftY + spriteSizeDp
    }

    data class Bowl(
        val topLeftY: Float,
    ) : FloorSprite() {
        override val bottomY: Float get() = topLeftY + BOWL_HEIGHT_DP
    }

    data class Toy(
        val topLeftY: Float,
    ) : FloorSprite() {
        override val bottomY: Float get() = topLeftY + TOY_SIZE_DP
    }

    data class Poop(
        val index: Int,
        val topLeftY: Float,
    ) : FloorSprite() {
        override val bottomY: Float get() = topLeftY + POOP_SIZE_DP
    }
}

/** Footprint of the toy sprite as drawn in [PetScreen]. */
const val TOY_SIZE_DP = 48f

/** Footprint of each poop sprite as drawn in [PetScreen]. */
const val POOP_SIZE_DP = 48f

/**
 * Returns [sprites] sorted by their bottom-Y anchor ascending — smaller Y
 * first so it draws underneath, larger Y last so it draws on top. The sort
 * is stable, so sprites at identical Y keep their input order.
 */
fun floorSpriteOrder(sprites: List<FloorSprite>): List<FloorSprite> = sprites.sortedBy { it.bottomY }
