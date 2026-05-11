package com.pocketpets.app.ui.inventory

import com.pocketpets.app.domain.behavior.Position

/** What a drop landed on. `null` (returned from [dropTargetAt]) means rejected. */
sealed interface DropTarget {
    data object Bowl : DropTarget

    data class Poop(
        val index: Int,
    ) : DropTarget

    data class Floor(
        val position: Position,
    ) : DropTarget

    data object Cat : DropTarget
}
