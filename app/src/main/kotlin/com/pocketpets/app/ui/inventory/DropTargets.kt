package com.pocketpets.app.ui.inventory

import com.pocketpets.app.domain.behavior.HabitatBounds
import com.pocketpets.app.domain.behavior.Position

/**
 * Resolves where a drag-drop landed.
 *
 * Resolution by item:
 *  - [Item.Food]: the bowl rect → [DropTarget.Bowl]; otherwise null.
 *  - [Item.Scoop]: the first poop rect that contains [position] → [DropTarget.Poop]; otherwise null.
 *  - [Item.Toy]: any point inside [bounds] → [DropTarget.Floor]; otherwise null.
 *  - [Item.Brush]: the cat sprite rect → [DropTarget.Cat]; otherwise null.
 */
fun dropTargetAt(
    position: Position,
    item: Item,
    bounds: HabitatBounds,
    bowlRect: DpRect,
    poopRects: List<DpRect>,
    catRect: DpRect,
): DropTarget? =
    when (item) {
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
        Item.Brush -> if (catRect.contains(position)) DropTarget.Cat else null
    }
