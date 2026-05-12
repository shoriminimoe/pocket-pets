# Enlarged Drag Preview Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the inventory drag preview large enough to see and lifted above the finger, keeping the rendered position equal to the drop hit-rect position.

**Architecture:** Add a pure `previewCenterFor(finger: Position): Position` helper plus two dp constants (`DRAG_PREVIEW_SIZE_DP`, `DRAG_PREVIEW_LIFT_DP`) in `DragController.kt`. The gesture handler in `PetScreen.kt` calls `previewCenterFor` to translate every pointer event before it reaches `DragController.start/move`, so the lifted icon centre becomes the single source of truth used by both the overlay renderer and `dropTargetAt`. The drag overlay's `Image` is widened to `DRAG_PREVIEW_SIZE_DP` and offset by `SIZE/2` to centre on `drag.position`.

**Tech Stack:** Kotlin 2.2 + Jetpack Compose, JUnit 4 + Google Truth (no Compose UI tests in this suite).

**Spec:** `docs/superpowers/specs/2026-05-12-enlarged-drag-preview-design.md`.

---

## Task 1: Add `previewCenterFor` helper and preview constants

**Files:**
- Modify: `app/src/main/kotlin/com/pocketpets/app/ui/inventory/DragController.kt`
- Modify: `app/src/test/kotlin/com/pocketpets/app/ui/inventory/DragControllerTest.kt`

The helper is the seam where raw pointer coords become the icon's logical centre. Keep it as a top-level function in the same file as `DragController` so both the gesture handler and the overlay renderer have a single import site.

- [ ] **Step 1: Add the failing test**

Append this test to the existing class in `DragControllerTest.kt` (above the closing brace):

```kotlin
    @Test
    fun `previewCenterFor lifts the icon centre above the finger`() {
        val center = previewCenterFor(Position(200f, 800f))
        assertThat(center).isEqualTo(Position(200f, 800f - DRAG_PREVIEW_LIFT_DP))
    }
```

- [ ] **Step 2: Run the test and verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.pocketpets.app.ui.inventory.DragControllerTest.previewCenterFor lifts the icon centre above the finger"`

Expected: Compilation fails — `previewCenterFor` and `DRAG_PREVIEW_LIFT_DP` are unresolved references.

- [ ] **Step 3: Add the constants and helper**

Replace the contents of `app/src/main/kotlin/com/pocketpets/app/ui/inventory/DragController.kt` with:

```kotlin
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
```

- [ ] **Step 4: Run the test and verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.pocketpets.app.ui.inventory.DragControllerTest"`

Expected: All tests in `DragControllerTest` pass, including the new `previewCenterFor lifts the icon centre above the finger`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/pocketpets/app/ui/inventory/DragController.kt \
        app/src/test/kotlin/com/pocketpets/app/ui/inventory/DragControllerTest.kt
git commit -m "$(cat <<'EOF'
feat: add previewCenterFor helper and drag-preview size/lift constants

Single seam that converts raw pointer position to the icon's on-screen
centre. The overlay renderer and the drop hit-test will both consume it,
keeping the rendered position and the hit-rect in lock-step.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: Wire the gesture handler and enlarge the overlay in PetScreen

**Files:**
- Modify: `app/src/main/kotlin/com/pocketpets/app/ui/pet/PetScreen.kt`

The pickup lookup must still use the raw finger position (the user is touching a tray slot, not the lifted icon). Drop resolution uses the lifted centre — which is exactly what `dragController.inFlight.position` becomes after Step 1, so no change to `dropTargetAt` callers. The overlay renderer reads the new `DRAG_PREVIEW_SIZE_DP` constant.

- [ ] **Step 1: Import the new symbols**

In `PetScreen.kt`, the inventory imports block already includes `DragController`, `DropTarget`, `InventoryTray`, `Item`, and `dropTargetAt`. Extend it with the two new symbols. Replace:

```kotlin
import com.pocketpets.app.ui.inventory.DragController
import com.pocketpets.app.ui.inventory.DropTarget
import com.pocketpets.app.ui.inventory.InventoryTray
import com.pocketpets.app.ui.inventory.Item
import com.pocketpets.app.ui.inventory.dropTargetAt
```

with:

```kotlin
import com.pocketpets.app.ui.inventory.DRAG_PREVIEW_SIZE_DP
import com.pocketpets.app.ui.inventory.DragController
import com.pocketpets.app.ui.inventory.DropTarget
import com.pocketpets.app.ui.inventory.InventoryTray
import com.pocketpets.app.ui.inventory.Item
import com.pocketpets.app.ui.inventory.dropTargetAt
import com.pocketpets.app.ui.inventory.previewCenterFor
```

`DRAG_PREVIEW_LIFT_DP` is not imported here — the lift is encapsulated inside `previewCenterFor` and `PetScreen.kt` never references it directly.

- [ ] **Step 2: Translate the pointer-down to the icon centre before `start`**

In `PetScreen.kt`, find the `awaitEachGesture` block. The current pickup is:

```kotlin
val down = awaitFirstDown(requireUnconsumed = false)
val startDp =
    with(density) {
        Position(
            (down.position.x + trayRootOffsetPx.x).toDp().value,
            (down.position.y + trayRootOffsetPx.y).toDp().value,
        )
    }
val pickedItem =
    slotRects.entries
        .firstOrNull { (_, r) -> r.contains(startDp) }
        ?.key ?: return@awaitEachGesture
dragController.start(pickedItem, startDp)
down.consume()
```

Replace it with:

```kotlin
val down = awaitFirstDown(requireUnconsumed = false)
val fingerStartDp =
    with(density) {
        Position(
            (down.position.x + trayRootOffsetPx.x).toDp().value,
            (down.position.y + trayRootOffsetPx.y).toDp().value,
        )
    }
val pickedItem =
    slotRects.entries
        .firstOrNull { (_, r) -> r.contains(fingerStartDp) }
        ?.key ?: return@awaitEachGesture
dragController.start(pickedItem, previewCenterFor(fingerStartDp))
down.consume()
```

The pickup hit-test still uses `fingerStartDp` (the slot under the finger). Only the value handed to `dragController.start` is lifted.

- [ ] **Step 3: Translate each move event to the icon centre**

In the same `awaitEachGesture` block, the current move loop body is:

```kotlin
val pos =
    with(density) {
        Position(
            (change.position.x + trayRootOffsetPx.x).toDp().value,
            (change.position.y + trayRootOffsetPx.y).toDp().value,
        )
    }
dragController.move(pos)
```

Replace it with:

```kotlin
val fingerDp =
    with(density) {
        Position(
            (change.position.x + trayRootOffsetPx.x).toDp().value,
            (change.position.y + trayRootOffsetPx.y).toDp().value,
        )
    }
dragController.move(previewCenterFor(fingerDp))
```

`dragController.inFlight.position` is now always the icon centre, in root-dp. The drop-resolution call at the bottom of the gesture (`dropTargetAt(position = ended.position, ...)`) needs no change — `ended.position` is already lifted.

- [ ] **Step 4: Enlarge the drag overlay and centre it on `drag.position`**

Find the drag overlay near the end of `PetScreen.kt` (currently the last `dragController.inFlight?.let { drag -> ... }` block). It currently reads:

```kotlin
dragController.inFlight?.let { drag ->
    val drawableId =
        when (drag.item) {
            Item.Food -> R.drawable.food
            Item.Scoop -> R.drawable.scoop
            Item.Toy -> R.drawable.toy
            Item.Brush -> R.drawable.brush
        }
    Image(
        painter = painterResource(drawableId),
        contentDescription = null,
        modifier =
            Modifier
                .offset(x = (drag.position.x - 32f).dp, y = (drag.position.y - 32f).dp)
                .size(64.dp),
    )
}
```

Replace it with:

```kotlin
dragController.inFlight?.let { drag ->
    val drawableId =
        when (drag.item) {
            Item.Food -> R.drawable.food
            Item.Scoop -> R.drawable.scoop
            Item.Toy -> R.drawable.toy
            Item.Brush -> R.drawable.brush
        }
    val half = DRAG_PREVIEW_SIZE_DP / 2f
    Image(
        painter = painterResource(drawableId),
        contentDescription = null,
        modifier =
            Modifier
                .offset(x = (drag.position.x - half).dp, y = (drag.position.y - half).dp)
                .size(DRAG_PREVIEW_SIZE_DP.dp),
    )
}
```

- [ ] **Step 5: Run the unit-test suite**

Run: `./gradlew test`

Expected: BUILD SUCCESSFUL. No new test failures. The existing `DragControllerTest`, `DropTargetsTest`, and `PetViewModelTest` continue to pass because none of their behaviour changed.

- [ ] **Step 6: Run ktlint and Android lint**

Run: `./gradlew ktlintCheck :app:lintDebug`

Expected: BUILD SUCCESSFUL. If ktlint reports a style error, run `./gradlew ktlintFormat` and re-run `ktlintCheck`. If Android Lint reports a new warning, fix it; warnings are treated as errors.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/com/pocketpets/app/ui/pet/PetScreen.kt
git commit -m "$(cat <<'EOF'
feat: enlarge drag preview and lift it above the finger (#16)

The drag overlay renders at 96 dp (1.5x the tray icon) and is centred
DRAG_PREVIEW_LIFT_DP above the finger. The gesture handler converts
every pointer event through previewCenterFor before handing it to
DragController, so the icon's logical centre is the single source of
truth and dropTargetAt naturally hit-tests against the rendered
position. The pickup hit-test still uses the raw finger position
because the user is touching a tray slot, not the lifted icon.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: Build the app and verify manually

**Files:** none changed.

Compose UI is not in the unit-test suite. The integration check is to run the debug APK and confirm the four drop interactions still resolve correctly with the new icon size and lift.

- [ ] **Step 1: Build the debug APK**

Run: `./gradlew :app:assembleDebug`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Install and exercise each tool**

If a device or emulator is wired up, install the APK and:

1. Pick up Food from the tray — confirm the icon is visible above the finger and clearly larger than the tray slot.
2. Drag Food onto the bowl — confirm the bowl fills (icon's visible position lands on the bowl, not the finger).
3. Spawn or wait for a poop, then drag Scoop onto it — confirm the poop disappears when the icon overlaps it.
4. Drag Toy and drop on the floor — confirm the toy is placed where the icon was visually shown (above the release point).
5. Drag Brush onto the cat — confirm cleanliness rises and the brush snaps back at end of drag.

If no device is available, document this in the PR description as a manual-verification gap rather than skipping it silently.

- [ ] **Step 3: No commit needed for this task** — it is purely verification.
