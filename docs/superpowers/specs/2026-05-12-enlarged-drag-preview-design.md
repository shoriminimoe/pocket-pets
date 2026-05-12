# Enlarged drag preview for inventory tools

**Date:** 2026-05-12
**Issue:** #16
**Goal:** Make the drag preview visible while a tool is being held — enlarge it and lift it above the finger so the user can see where it will land. Hit-testing must match the rendered position.

## 1. Why

Today's drag preview is the same 64-dp icon used in the inventory tray, rendered centred on the pointer. The user's finger occludes it, so it is hard to tell where the tool will interact with the pet. Players have to drop blindly to find the bowl / poop / cat hit-rect.

The fix is small but has a known trap: in this codebase the *rendered* position and the *hit-rect* position of dragged or placed sprites are required to agree to the pixel (see `fb9edc0` and `615b1ef`, which fixed earlier mismatches). The design must preserve that invariant.

## 2. Scope

### In

- The in-flight drag overlay renders the tool sprite at **96 dp** (1.5× the 64-dp tray icon).
- The overlay icon is **lifted above the finger** so the icon's centre is `DRAG_PREVIEW_LIFT_DP` (64 dp) above the touch point. With a 96-dp icon, the icon's bottom edge ends ~16 dp above the finger centre — far enough to clear a fingertip.
- The drop hit-test resolves against the icon's centre (i.e. the lifted position), not the raw pointer position. Dropping a toy places it where the icon visually was; dropping food on the bowl requires the icon — not the finger — to be over the bowl.
- All four tools (`Food`, `Scoop`, `Toy`, `Brush`) use the same enlarged preview.

### Out

- Per-item preview sizes. The issue lists this as a "suggested touchpoint" in `Item.kt`, but no tool needs a different size and uniform sizing keeps the gesture math simple. Defer until a concrete need appears.
- Animated grow-on-pickup / shrink-on-drop. The icon snaps to its preview size at `start` and disappears at `end`.
- Dimming or visually highlighting drop targets while a drag is in flight (already discussed in the Phase-3 spec but not in scope here).
- Clamping the icon when the finger is dragged near the top of the screen — the tray is at the bottom and the lift is small enough that this is not reachable in practice.

### Success criteria

- Holding any tool from the tray shows a 96-dp sprite floating above the finger, visible from the moment of touch-down to release.
- Dropping food on the bowl, scoop on a poop, toy on the floor, and brush on the cat continue to work, *and* the rect the user has to land the icon on matches the rect the icon visually covers (no off-by-finger-offset bug).
- Releasing or cancelling the drag returns to the resting tray (no leftover overlay).

## 3. Architecture

### 3.1 The shared transform

The whole feature reduces to one thing: at the gesture-handler boundary, translate the raw pointer position to the icon's centre, and from there treat that translated position as the single source of truth for both rendering and drop resolution.

Add to `app/src/main/kotlin/com/pocketpets/app/ui/inventory/DragController.kt`:

```kotlin
const val DRAG_PREVIEW_SIZE_DP = 96f
const val DRAG_PREVIEW_LIFT_DP = 64f

/**
 * Returns the on-screen centre of the drag preview given the raw finger
 * position (both in dp, root-coords). The preview is rendered lifted above
 * the finger so the icon is not occluded; drop resolution uses this same
 * position so the rendered position and the hit-rect agree.
 */
fun previewCenterFor(finger: Position): Position =
    Position(finger.x, finger.y - DRAG_PREVIEW_LIFT_DP)
```

`DragController` itself does not change. `DragInFlight.position` remains the canonical icon-centre coordinate — exactly what it already represents in the current code.

### 3.2 Gesture-handler wiring (PetScreen.kt)

The existing handler captures the raw pointer position in root-dp coords inside `awaitEachGesture`. The change is one extra step on each pointer event:

```kotlin
val fingerDp = with(density) {
    Position(
        (change.position.x + trayRootOffsetPx.x).toDp().value,
        (change.position.y + trayRootOffsetPx.y).toDp().value,
    )
}
val previewCenter = previewCenterFor(fingerDp)
dragController.move(previewCenter)
```

Same translation is applied at `awaitFirstDown` before `dragController.start(...)`.

The `slotRects.entries.firstOrNull { it.value.contains(startDp) }` pickup lookup uses the **finger** position (the user is picking up the slot they pressed, not the slot under the lifted icon), so the pickup uses `fingerDp` unchanged, not the lifted preview centre.

Drop resolution at `lifted` is unchanged — `ended.position` is already the lifted preview centre, which is what `dropTargetAt(...)` should be checking against.

### 3.3 Overlay rendering

The drag overlay in `PetScreen.kt:365` becomes:

```kotlin
Image(
    painter = painterResource(drawableId),
    contentDescription = null,
    modifier = Modifier
        .offset(
            x = (drag.position.x - DRAG_PREVIEW_SIZE_DP / 2f).dp,
            y = (drag.position.y - DRAG_PREVIEW_SIZE_DP / 2f).dp,
        )
        .size(DRAG_PREVIEW_SIZE_DP.dp),
)
```

The `- SIZE/2` offset converts the centre-of-icon coordinate to the top-left needed by `Modifier.offset`.

## 4. Testing

- Add a `previewCenterFor` test to `DragControllerTest`: for a finger at `(200, 800)` the icon centre is `(200, 800 - 64)`.
- `DragControllerTest` and `DropTargetsTest` need no further changes — neither file changes behaviour.
- Compose UI tests are not part of this project's test suite. The integration (pointer → renderer agreement) is verified manually by running the app, picking up each tool, and dragging it onto its drop target near the edge of the rect.

## 5. Risks

- **Risk:** A test calling `dragController.start(item, fingerPos)` directly (rather than via the gesture handler) might pass a raw pointer position and now visually mismatch. **Mitigation:** the controller's interface is unchanged; existing tests pass `Position(40f, 50f)` style inputs and still describe "the icon centre", which is correct.
- **Risk:** The first frame after `start` could render at the raw finger position if the call order is wrong. **Mitigation:** `start` is given `previewCenterFor(fingerDp)` from the same `awaitFirstDown` block, so the first overlay frame is already lifted.
