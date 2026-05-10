package com.pocketpets.app.ui.inventory

import com.pocketpets.app.domain.behavior.Anchors
import com.pocketpets.app.domain.behavior.HabitatBounds
import com.pocketpets.app.domain.behavior.Position

/** Width and height in dp of the bowl decor (matches the rendered Image size). */
internal const val BOWL_WIDTH_DP = 64f
internal const val BOWL_HEIGHT_DP = 32f

/**
 * Resolves where a drag-drop landed.
 *
 * Resolution by item:
 *  - [Item.Food]: the bowl rect → [DropTarget.Bowl]; otherwise null.
 *  - [Item.Scoop]: the first poop rect that contains [position] → [DropTarget.Poop]; otherwise null.
 *  - [Item.Toy]: any point inside [bounds] → [DropTarget.Floor]; otherwise null.
 */
fun dropTargetAt(
    position: Position,
    item: Item,
    bounds: HabitatBounds,
    anchors: Anchors,
    poopRects: List<DpRect>,
): DropTarget? {
    val bowlRect =
        DpRect(
            left = anchors.bowl.x,
            top = anchors.bowl.y,
            right = anchors.bowl.x + BOWL_WIDTH_DP,
            bottom = anchors.bowl.y + BOWL_HEIGHT_DP,
        )

    return when (item) {
        Item.Food -> if (bowlRect.contains(position)) DropTarget.Bowl else null
        Item.Scoop -> {
            val idx = poopRects.indexOfFirst { it.contains(position) }
            if (idx >= 0) DropTarget.Poop(idx) else null
        }
        Item.Toy ->
            if (position.x in bounds.minX..bounds.maxX &&
                position.y in bounds.minY..bounds.maxY
            ) {
                DropTarget.Floor(position)
            } else {
                null
            }
    }
}
