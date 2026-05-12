package com.pocketpets.app.ui.pet

import com.pocketpets.app.domain.behavior.Anchors
import com.pocketpets.app.domain.behavior.HabitatBounds
import com.pocketpets.app.domain.behavior.Position

/** Result of [computeHabitat] — the playable rectangle and the navigable anchor points. */
data class Habitat(
    val bounds: HabitatBounds,
    val anchors: Anchors,
    val defaultBowlPosition: Position,
    val bowlClampBounds: HabitatBounds,
)

const val BOWL_WIDTH_DP = 64f
const val BOWL_HEIGHT_DP = 32f
private const val ANCHOR_INSET_DP = 24f
private const val ANCHOR_BOTTOM_PADDING_DP = 16f
private const val BOWL_DEFAULT_LEFT_DP = 24f
private const val BOWL_DEFAULT_FLOOR_GAP_DP = 16f

/**
 * Builds the playable [HabitatBounds] and navigation [Anchors] for a screen of
 * [widthDp] × [heightDp] when the rendered cat sprite occupies a [spriteDp]-sided
 * box and the caller has measured [topReservedDp] of overlay above the play area
 * (top bar + stat chips) and [bottomReservedDp] of overlay below (inventory tray).
 *
 * The cat's position is its sprite's top-left corner, so [HabitatBounds.maxX] is
 * `widthDp - spriteDp` (and analogously for Y) — that's what keeps the full
 * sprite on-screen. `coerceAtLeast` on both edges keeps the [HabitatBounds.init]
 * `minX < maxX` / `minY < maxY` invariants alive when the screen is too small
 * or the overlays too tall to hold the sprite cleanly.
 */
fun computeHabitat(
    widthDp: Float,
    heightDp: Float,
    topReservedDp: Float,
    bottomReservedDp: Float,
    spriteDp: Float,
): Habitat {
    val floorTopDp = topReservedDp
    val floorBottomDp = heightDp - bottomReservedDp
    val maxX = (widthDp - spriteDp).coerceAtLeast(1f)
    val maxY = (floorBottomDp - spriteDp).coerceAtLeast(floorTopDp + 1f)
    val bounds = HabitatBounds(minX = 0f, minY = floorTopDp, maxX = maxX, maxY = maxY)
    val anchorY =
        (floorBottomDp - spriteDp - ANCHOR_BOTTOM_PADDING_DP)
            .coerceIn(floorTopDp, maxY)
    val anchors =
        Anchors(
            bed =
                Position(
                    x = (widthDp - spriteDp - ANCHOR_INSET_DP).coerceIn(0f, maxX),
                    y = anchorY,
                ),
            bowl =
                Position(
                    x = ANCHOR_INSET_DP.coerceAtMost(maxX),
                    y = anchorY,
                ),
        )
    val defaultBowlPosition =
        Position(
            x = BOWL_DEFAULT_LEFT_DP,
            y = floorBottomDp - BOWL_HEIGHT_DP - BOWL_DEFAULT_FLOOR_GAP_DP,
        )
    val bowlClampBounds = computeBowlBounds(widthDp, floorTopDp, floorBottomDp)
    return Habitat(bounds, anchors, defaultBowlPosition, bowlClampBounds)
}

/**
 * Bounds the bowl's top-left dp position is clamped against. Mirrors how
 * [HabitatBounds] reserves room for the cat sprite, but with the bowl's
 * own (64×32 dp) footprint instead.
 */
fun computeBowlBounds(
    widthDp: Float,
    floorTopDp: Float,
    floorBottomDp: Float,
): HabitatBounds {
    val maxX = (widthDp - BOWL_WIDTH_DP).coerceAtLeast(1f)
    val maxY = (floorBottomDp - BOWL_HEIGHT_DP).coerceAtLeast(floorTopDp + 1f)
    return HabitatBounds(minX = 0f, minY = floorTopDp, maxX = maxX, maxY = maxY)
}

/**
 * Cat destination derived from a bowl top-left in dp. The cat's sprite
 * top-left ends up sharing the bowl's x but sits on the floor anchor line,
 * so the cat appears to stand next to the bowl with its feet at floor level.
 */
fun bowlAnchorFor(
    bowlPosition: Position,
    bounds: HabitatBounds,
    fallback: Position,
): Position =
    Position(
        x = bowlPosition.x.coerceIn(bounds.minX, bounds.maxX),
        y = fallback.y,
    )
