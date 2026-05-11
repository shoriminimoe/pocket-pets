# Play-area regions — measure overlays, drop where the cat lives

**Date:** 2026-05-11
**Issue:** #12 (stat chips and inventory tray cover the play area, blocking pet interaction).

#11 was resolved by #17, which made `computeHabitat(widthDp, heightDp, spriteDp)` reserve the cat's real stage-dependent sprite size on the right and bottom edges. This spec builds on that and finishes the job for #12.

## 1. Why

`computeHabitat` defines the play area as `(0, heightDp × 0.40)` to `(widthDp, heightDp × 0.85)` — fractional constants that don't reflect the actual heights of the top bar + stat chips or the inventory tray. On a typical phone they happen to leave a wide gap above and below the cat, but they encode no relationship to the real overlay sizes. The system can drift any of three ways:

- Stat chips grow taller (e.g. larger font, accessibility scale) and overlap the cat at the top.
- Inventory tray grows taller (e.g. a fourth slot, more padding) and overlaps the cat at the bottom — its outer `fillMaxWidth()` gesture region would then intercept long-presses meant for the cat.
- The play-area-rect that drives toy floor drops doesn't exist — `dropTargetAt` uses `HabitatBounds`, so a toy can't be dropped where the cat's top-left can't sit, even though the play area visually extends further.

The fix is to measure the actual overlay heights and derive the play-area rect from them, then thread that rect through to the floor drop target.

## 2. Scope

### In

- Group the top bar Row and stat-chip Row into a single `Column` aligned `TopStart`; measure its height (`topReservedDp`) via `onSizeChanged`.
- Add `onSizeChanged` to the inventory tray's outer Box to measure its height (`bottomReservedDp`).
- Replace the `FLOOR_TOP_FRACTION` / `FLOOR_BOTTOM_FRACTION` constants in `Habitat.kt`. `computeHabitat` now takes measured `topReservedDp` and `bottomReservedDp` instead.
- Position bowl and poops relative to the play area's bottom, not the screen bottom.
- Add `playAreaRect: DpRect` to `dropTargetAt`; toy floor drops use it. The old `bounds: HabitatBounds` parameter is removed.
- Update `HabitatTest` to drive the new signature and add a "small screen / huge overlays" coerce case.
- Add toy-floor cases to `DropTargetsTest` that exercise the difference between `bounds` and `playAreaRect`.

### Out

- Refactoring the root `Box(fillMaxSize)` into a `Column`. We keep absolute (root-relative) coordinates so the existing drag controller, drop-rect math, and behavior offsets need no coordinate-space translation. Overlays still draw via `align` and `offset` on the same root Box; they just happen to measure their own size.
- Cat sprite visually overlapping the bowl while eating — pre-existing, separate.
- Speech-bubble overflow when the cat is near the top edge — pre-existing, separate.
- Tightening the tray's outer-Box gesture rect to the slots only. With the play area's bottom derived from the measured tray height, the cat sprite is guaranteed to render above the tray, so its outer gesture region cannot intercept cat taps anymore. (If we ever stop measuring, we'd want to revisit.)

### Success criteria

- The cat's full sprite rect (offset + size for the current stage) is always inside `playAreaRect` on every screen size we test, including a stress-tested small-screen / huge-overlay case.
- Tapping (long-pressing) any pixel of the visible cat fires `vm.onCatHeld()` — the tray below and the stat chips above don't intercept.
- A toy dropped near the bottom edge of `playAreaRect` (below `bounds.maxY` because the cat's top-left can't sit there) lands on the floor as `DropTarget.Floor` and the cat walks toward it.
- Bowl and poops render fully above the inventory tray for any tray height.
- All existing unit tests still pass; new `HabitatTest` and `DropTargetsTest` cases pass.

## 3. Architecture

### 3.1 Measurements in `PetScreen`

```kotlin
var screenWidthDp by remember { mutableFloatStateOf(0f) }
var screenHeightDp by remember { mutableFloatStateOf(0f) }
var topReservedDp by remember { mutableFloatStateOf(0f) }
var bottomReservedDp by remember { mutableFloatStateOf(0f) }
```

- `screenWidthDp` / `screenHeightDp` — `room_bg.onSizeChanged` (unchanged).
- `topReservedDp` — top bar Row and stat-chip Row are wrapped in a single `Column` aligned `TopStart`. Its `onSizeChanged` writes this value. The stat-chip Row's `padding(top = 60.dp)` goes away — chips sit directly below the top bar in the Column.
- `bottomReservedDp` — the tray's outer Box gets `onSizeChanged` alongside the existing `onGloballyPositioned`.

Derived:

```kotlin
val playAreaBottom = screenHeightDp - bottomReservedDp
val playAreaRect = DpRect(
    left = 0f, top = topReservedDp, right = screenWidthDp, bottom = playAreaBottom,
)
```

### 3.2 `Habitat.kt` signature change

Replace the fractional constants:

```kotlin
// removed:
// private const val FLOOR_TOP_FRACTION = 0.40f
// private const val FLOOR_BOTTOM_FRACTION = 0.85f

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
```

`ANCHOR_INSET_DP` (24f) and `ANCHOR_BOTTOM_PADDING_DP` (16f) stay.

### 3.3 LaunchedEffect rekeying

```kotlin
val spriteDp = stageSpriteSize(state.stage).value
LaunchedEffect(screenWidthDp, screenHeightDp, topReservedDp, bottomReservedDp, spriteDp) {
    if (screenWidthDp <= 0f ||
        screenHeightDp <= 0f ||
        topReservedDp <= 0f ||
        bottomReservedDp <= 0f
    ) {
        return@LaunchedEffect
    }
    val habitat = computeHabitat(
        widthDp = screenWidthDp,
        heightDp = screenHeightDp,
        topReservedDp = topReservedDp,
        bottomReservedDp = bottomReservedDp,
        spriteDp = spriteDp,
    )
    vm.setHabitat(habitat.bounds, habitat.anchors)
}
```

Bounds aren't published until both overlays have been measured. On first frame the cat renders at whatever last-stored position the ViewModel has, then snaps into the freshly clamped bounds on the next `tick` (#17 added that snap).

The `habitatBoundsState` field and its only call site (the `onDragEnd` `?: return` early-out) are no longer needed: the drop path now gates on the four measured-state fields directly. Remove the field, its `mutableStateOf<HabitatBounds?>(null)` initialiser, and the now-unused `import com.pocketpets.app.domain.behavior.HabitatBounds`.

### 3.4 Decor positioning

Bowl, poops use `playAreaBottom` instead of `screenHeightDp - constant`. Render and drop-target rect must stay in sync.

Bowl:

```kotlin
Image(
    painter = painterResource(if (state.world.bowlFilled) R.drawable.bowl_full else R.drawable.bowl),
    contentDescription = null,
    modifier =
        Modifier
            .offset(x = 24.dp, y = (playAreaBottom - 32f - 16f).dp)
            .size(width = 64.dp, height = 32.dp),
)
```

Bowl drop rect (built in the tray's `onDragEnd`):

```kotlin
val bowlRect = DpRect(
    left = 24f,
    top = playAreaBottom - 32f - 16f,
    right = 24f + 64f,
    bottom = playAreaBottom - 16f,
)
```

Poops — render and `poopRectFor` switch to play-area-bottom-relative coordinates. The horizontal jitter math is unchanged.

```kotlin
private fun poopRectFor(
    i: Int,
    offsets: List<Int>,
    screenWidthDp: Float,
    playAreaBottom: Float,
): DpRect {
    val sizeDp = 48f
    val centerX = screenWidthDp / 2f + offsets[i] - sizeDp / 2f
    val bottomMargin = 16f + i * 6f
    val top = playAreaBottom - bottomMargin - sizeDp
    return DpRect(
        left = centerX,
        top = top,
        right = centerX + sizeDp,
        bottom = top + sizeDp,
    )
}
```

```kotlin
repeat(pet.poopCount) { i ->
    val xOffset = poopOffsets[i]
    val sizeDp = 48f
    val bottomMargin = 16f + i * 6f
    Image(
        painter = painterResource(R.drawable.poop),
        contentDescription = null,
        modifier =
            Modifier
                .offset(
                    x = (screenWidthDp / 2f + xOffset - sizeDp / 2f).dp,
                    y = (playAreaBottom - bottomMargin - sizeDp).dp,
                )
                .size(sizeDp.dp),
    )
}
```

The `(110 + i * 6)` from-screen-bottom margin becomes a `(16 + i * 6)` from-play-area-bottom margin — the 110 was chosen to clear the tray, which is now done by `playAreaBottom` itself.

### 3.5 `dropTargetAt` signature change

`app/src/main/kotlin/com/pocketpets/app/ui/inventory/DropTargets.kt`:

```kotlin
fun dropTargetAt(
    position: Position,
    item: Item,
    playAreaRect: DpRect,
    bowlRect: DpRect,
    poopRects: List<DpRect>,
): DropTarget? =
    when (item) {
        Item.Food -> if (bowlRect.contains(position)) DropTarget.Bowl else null
        Item.Scoop -> {
            val idx = poopRects.indexOfFirst { it.contains(position) }
            if (idx >= 0) DropTarget.Poop(idx) else null
        }
        Item.Toy -> if (playAreaRect.contains(position)) DropTarget.Floor(position) else null
    }
```

The `HabitatBounds` import is removed; the `bounds` parameter is gone.

### 3.6 Caller update in `PetScreen`

The tray's `onDragEnd` builds the `playAreaRect` locally and passes it through:

```kotlin
onDragEnd = {
    val ended = dragController.end() ?: return@detectDragGesturesAfterLongPress
    if (topReservedDp <= 0f ||
        bottomReservedDp <= 0f ||
        screenWidthDp <= 0f ||
        screenHeightDp <= 0f
    ) {
        return@detectDragGesturesAfterLongPress
    }
    val playAreaBottomLocal = screenHeightDp - bottomReservedDp
    val playAreaRect = DpRect(0f, topReservedDp, screenWidthDp, playAreaBottomLocal)
    val poopRects = (0 until pet.poopCount).map { i ->
        poopRectFor(i, poopOffsets, screenWidthDp, playAreaBottomLocal)
    }
    val bowlRect = DpRect(
        left = 24f,
        top = playAreaBottomLocal - 32f - 16f,
        right = 24f + 64f,
        bottom = playAreaBottomLocal - 16f,
    )
    val target = dropTargetAt(
        position = ended.position,
        item = ended.item,
        playAreaRect = playAreaRect,
        bowlRect = bowlRect,
        poopRects = poopRects,
    ) ?: return@detectDragGesturesAfterLongPress
    when (target) {
        DropTarget.Bowl -> vm.onFoodDroppedOnBowl()
        is DropTarget.Poop -> vm.onScoopDroppedOnPoop(target.index)
        is DropTarget.Floor -> vm.onToyDropped(target.position)
    }
},
```

The pre-#17 `val bounds = habitatBoundsState ?: return` guard is replaced by the measured-state guard above.

## 4. Tests

### 4.1 `HabitatTest`

- Update existing cases to pass `topReservedDp` and `bottomReservedDp`. Pick values matching the old fractions (`heightDp * 0.40f`, `heightDp * 0.15f`) so existing geometry checks keep their numeric expectations.
- Add: tiny screen + huge overlays — assert `computeHabitat(widthDp = 200f, heightDp = 200f, topReservedDp = 180f, bottomReservedDp = 100f, spriteDp = 192f)` returns a `HabitatBounds` whose `init` requirements (`minX < maxX`, `minY < maxY`) are satisfied via the `coerceAtLeast` clamps, with no `IllegalArgumentException`.

### 4.2 `DropTargetsTest`

- Update existing toy-floor cases to construct a `playAreaRect` and pass it instead of `bounds`.
- Add: toy dropped below `bounds.maxY` but above `playAreaRect.bottom` returns `DropTarget.Floor`.
- Add: toy dropped below `playAreaRect.bottom` returns null.

## 5. Files touched

- `app/src/main/kotlin/com/pocketpets/app/ui/pet/Habitat.kt` — drop fractional constants; new `topReservedDp` / `bottomReservedDp` parameters on `computeHabitat`.
- `app/src/main/kotlin/com/pocketpets/app/ui/pet/PetScreen.kt` — Column wrapping top bar + stat chips; `onSizeChanged` on the tray Box; `LaunchedEffect` rekeyed; decor `offset` math; `dropTargetAt` call.
- `app/src/main/kotlin/com/pocketpets/app/ui/inventory/DropTargets.kt` — `playAreaRect` parameter; remove `HabitatBounds` dependency.
- `app/src/test/kotlin/com/pocketpets/app/ui/pet/HabitatTest.kt` — new signature; small-screen coerce case.
- `app/src/test/kotlin/com/pocketpets/app/ui/inventory/DropTargetsTest.kt` — `playAreaRect` toy-floor cases.
