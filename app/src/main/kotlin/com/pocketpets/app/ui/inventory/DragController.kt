package com.pocketpets.app.ui.inventory

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.pocketpets.app.domain.behavior.Position

/** Rendered side length of the in-flight drag preview, in dp. */
const val DRAG_PREVIEW_SIZE_DP = 96f

/**
 * Distance, in dp, that the preview icon's centre is lifted above the finger
 * so the user can see what they are dragging. Used by both the overlay
 * renderer and the drop hit-test — they must agree.
 */
const val DRAG_PREVIEW_LIFT_DP = 64f

/**
 * Converts a raw finger position (root-coords, dp) into the on-screen centre
 * of the drag preview. Render the icon centred on this point and resolve
 * drops against this point; the rendered position and the hit-rect always
 * agree by construction.
 */
fun previewCenterFor(finger: Position): Position = Position(finger.x, finger.y - DRAG_PREVIEW_LIFT_DP)

/** Snapshot of an in-flight drag. */
data class DragInFlight(
    val item: Item,
    val position: Position,
)

/**
 * Compose state holder for an in-flight drag-and-drop. Created once per screen
 * via `remember { DragController() }`. Tray slots call [start] / [move] /
 * [end]; the screen's drag overlay reads [inFlight] to render the icon.
 */
class DragController {
    var inFlight: DragInFlight? by mutableStateOf(null)
        private set

    fun start(
        item: Item,
        initialPosition: Position,
    ) {
        inFlight = DragInFlight(item, initialPosition)
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
