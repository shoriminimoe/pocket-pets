package com.pocketpets.app.ui.inventory

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.pocketpets.app.domain.behavior.Position

/** Snapshot of an in-flight drag. */
data class DragInFlight(val item: Item, val position: Position)

/**
 * Compose state holder for an in-flight drag-and-drop. Created once per screen
 * via `remember { DragController() }`. Tray slots call [start] / [move] /
 * [end]; the screen's drag overlay reads [inFlight] to render the icon.
 */
class DragController {
    var inFlight: DragInFlight? by mutableStateOf(null)
        private set

    fun start(item: Item) {
        inFlight = DragInFlight(item, Position(0f, 0f))
    }

    fun move(position: Position) {
        val current = inFlight ?: return
        inFlight = current.copy(position = position)
    }

    fun end(): DragInFlight? {
        val v = inFlight
        inFlight = null
        return v
    }
}
