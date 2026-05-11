package com.pocketpets.app.ui.inventory

import com.pocketpets.app.domain.behavior.Position

/**
 * Resolves where a drag-drop landed.
 *
 * Resolution by item:
 *  - [Item.Food]: the bowl rect → [DropTarget.Bowl]; otherwise null.
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
        Item.Food -> if (bowlRect.contains(position)) DropTarget.Bowl else null
        Item.Scoop -> {
            val idx = poopRects.indexOfFirst { it.contains(position) }
            if (idx >= 0) DropTarget.Poop(idx) else null
        }
        Item.Toy -> if (playAreaRect.contains(position)) DropTarget.Floor(position) else null
        Item.Brush -> if (catRect.contains(position)) DropTarget.Cat else null
    }
