package com.pocketpets.app.ui.pet

import com.pocketpets.app.domain.behavior.Anchors
import com.pocketpets.app.domain.behavior.HabitatBounds
import com.pocketpets.app.domain.behavior.Position

/** Result of [computeHabitat] — the playable rectangle and the navigable anchor points. */
data class Habitat(
    val bounds: HabitatBounds,
    val anchors: Anchors,
)

private const val ANCHOR_INSET_DP = 24f
private const val ANCHOR_BOTTOM_PADDING_DP = 16f

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
    return Habitat(bounds, anchors)
}
