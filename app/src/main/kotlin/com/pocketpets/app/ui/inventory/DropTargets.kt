package com.pocketpets.app.ui.inventory

import com.pocketpets.app.domain.behavior.Position

/**
 * How much to expand the bowl's hit zone for [Item.Food] drops on each side beyond the
 * bowl sprite's visible 64-dp width. The bowl sprite stays 64x32 dp, but a finger drop
 * landing within this horizontal margin still counts as "in the bowl".
 */
private const val FOOD_HIT_SIDE_PAD_DP = 32f

/**
 * How much room above the bowl sprite still counts as "in the bowl" for [Item.Food]
 * drops. The inventory tray sits under the play area, so users tend to release the
 * food slightly above the bowl when dragging up out of the tray — this extra vertical
 * slack catches those drops.
 */
private const val FOOD_HIT_ABOVE_PAD_DP = 48f

/**
 * Builds the generous hit zone used for [Item.Food] drops onto the bowl. The rect is
 * the bowl sprite expanded by [FOOD_HIT_SIDE_PAD_DP] horizontally and
 * [FOOD_HIT_ABOVE_PAD_DP] upward, then extended downward to the play area's bottom so
 * any drop in the floor band below the bowl also counts (issue #29). The bowl's
 * visual size in [bowlRect] is unchanged.
 */
internal fun foodHitZoneFor(
    bowlRect: DpRect,
    playAreaRect: DpRect,
): DpRect =
    DpRect(
        left = bowlRect.left - FOOD_HIT_SIDE_PAD_DP,
        top = bowlRect.top - FOOD_HIT_ABOVE_PAD_DP,
        right = bowlRect.right + FOOD_HIT_SIDE_PAD_DP,
        bottom = maxOf(bowlRect.bottom, playAreaRect.bottom),
    )

/**
 * Resolves where a drag-drop landed.
 *
 * Resolution by item:
 *  - [Item.Food]: the expanded bowl hit zone (see [foodHitZoneFor]) → [DropTarget.Bowl];
 *    otherwise null. The hit zone is wider than the visible 64x32 dp bowl sprite so
 *    finger drops near but not exactly on the bowl still count.
 *  - [Item.Scoop]: the first poop rect that contains [position] → [DropTarget.Poop]; otherwise null.
 *  - [Item.Toy]: any point inside [playAreaRect] → [DropTarget.Floor]; otherwise null. The
 *    play-area rect is the visible floor band (between the top overlays and the inventory
 *    tray), not the cat-movable [com.pocketpets.app.domain.behavior.HabitatBounds] — a toy
 *    can be placed anywhere the user can see floor, even if the cat's top-left wouldn't
 *    fit there. The behavior tick clamps the cat's target separately.
 *  - [Item.Brush]: the cat sprite rect → [DropTarget.Cat]; otherwise null.
 */
fun dropTargetAt(
    position: Position,
    item: Item,
    playAreaRect: DpRect,
    bowlRect: DpRect,
    poopRects: List<DpRect>,
    catRect: DpRect,
): DropTarget? =
    when (item) {
        Item.Food -> if (foodHitZoneFor(bowlRect, playAreaRect).contains(position)) DropTarget.Bowl else null
        Item.Scoop -> {
            val idx = poopRects.indexOfFirst { it.contains(position) }
            if (idx >= 0) DropTarget.Poop(idx) else null
        }
        Item.Toy -> if (playAreaRect.contains(position)) DropTarget.Floor(position) else null
        Item.Brush -> if (catRect.contains(position)) DropTarget.Cat else null
    }
