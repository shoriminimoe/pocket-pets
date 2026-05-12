# Play-area regions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Measure the actual heights of the top-bar/stat-chip Column and the inventory tray, derive the play-area rect from those measurements, and route a `playAreaRect` through `dropTargetAt` so the cat is fully tappable and toy floor drops accept anywhere visually inside the play area.

**Architecture:** Replace the fractional `0.40f`/`0.85f` constants in `computeHabitat` with measured `topReservedDp` / `bottomReservedDp` parameters. In `PetScreen`, wrap the top bar Row + stat-chip Row in a single Column whose height we capture via `onSizeChanged`; do the same on the tray's outer Box. Decor (bowl, poops) and the bowl drop rect anchor to `playAreaBottom = screenHeightDp - bottomReservedDp`. `dropTargetAt` swaps its `HabitatBounds` parameter for `DpRect playAreaRect`. Done in five small, compile-clean commits: signature change → drop-target signature change → measure top → measure bottom → cleanup.

**Tech Stack:** Kotlin 2.2, Jetpack Compose, Robolectric (JVM unit tests), Truth assertions, JUnit 4.

**Reference:** `docs/superpowers/specs/2026-05-11-play-area-regions-design.md`.

---

## File Structure

- `app/src/main/kotlin/com/pocketpets/app/ui/pet/Habitat.kt` — pure `computeHabitat` factory; drops `FLOOR_TOP_FRACTION` / `FLOOR_BOTTOM_FRACTION` and gains `topReservedDp` / `bottomReservedDp` parameters.
- `app/src/test/kotlin/com/pocketpets/app/ui/pet/HabitatTest.kt` — updated to use the new signature; gains a "huge overlays / tiny screen" coerce case.
- `app/src/main/kotlin/com/pocketpets/app/ui/inventory/DropTargets.kt` — `dropTargetAt` swaps `bounds: HabitatBounds` for `playAreaRect: DpRect`; toy floor target uses it.
- `app/src/test/kotlin/com/pocketpets/app/ui/inventory/DropTargetsTest.kt` — switched to `playAreaRect`; new case for toy drops outside cat-movable bounds but inside the play area.
- `app/src/main/kotlin/com/pocketpets/app/ui/pet/PetScreen.kt` — single source of truth for the wiring: groups top overlays in a Column with `onSizeChanged`, adds `onSizeChanged` to the tray, rekeys the `LaunchedEffect`, repositions bowl/poops to play-area-bottom, builds `playAreaRect` for the drop call, removes the now-dead `habitatBoundsState` field.

No new files. Each task keeps `main` and tests both compiling so the suite is always runnable.

---

## Task 1: `computeHabitat` accepts measured top/bottom reserved heights

**Files:**
- Modify: `app/src/main/kotlin/com/pocketpets/app/ui/pet/Habitat.kt`
- Modify: `app/src/test/kotlin/com/pocketpets/app/ui/pet/HabitatTest.kt`
- Modify: `app/src/main/kotlin/com/pocketpets/app/ui/pet/PetScreen.kt` (only the `computeHabitat` call site)

This task swaps the fractional constants for explicit measured parameters. `PetScreen` keeps the same observable behaviour for now by passing `heightDp * 0.40f` and `heightDp * 0.15f` as the bridge values — those are exactly the values the constants computed.

- [ ] **Step 1: Replace `Habitat.kt` with the new signature**

Overwrite `app/src/main/kotlin/com/pocketpets/app/ui/pet/Habitat.kt` with:

```kotlin
package com.pocketpets.app.ui.pet

import com.pocketpets.app.domain.behavior.Anchors
import com.pocketpets.app.domain.behavior.HabitatBounds
import com.pocketpets.app.domain.behavior.Position

/** Result of [computeHabitat] — the playable rectangle and the navigable anchor points. */
data class Habitat(
    val bounds: HabitatBounds,
    val anchors: Anchors,
)

private const val ANCHOR_INSET_DP = 24f
private const val ANCHOR_BOTTOM_PADDING_DP = 16f

/**
 * Builds the playable [HabitatBounds] and navigation [Anchors] for a screen of
 * [widthDp] × [heightDp] when the rendered cat sprite occupies a [spriteDp]-sided
 * box and the caller has measured [topReservedDp] of overlay above the play area
 * (top bar + stat chips) and [bottomReservedDp] of overlay below (inventory tray).
 *
 * The cat's position is its sprite's top-left corner, so [HabitatBounds.maxX] is
 * `widthDp - spriteDp` (and analogously for Y) — that's what keeps the full
 * sprite on-screen. `coerceAtLeast` on both edges keeps the [HabitatBounds.init]
 * `minX < maxX` / `minY < maxY` invariants alive when the screen is too small
 * or the overlays too tall to hold the sprite cleanly.
 */
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

- [ ] **Step 2: Update the `PetScreen` call site to pass fractional bridge values**

In `app/src/main/kotlin/com/pocketpets/app/ui/pet/PetScreen.kt`, find the `LaunchedEffect(screenWidthDp, screenHeightDp, spriteDp)` block and replace its body with the explicit-arg call. The current block:

```kotlin
    val spriteDp = stageSpriteSize(state.stage).value
    LaunchedEffect(screenWidthDp, screenHeightDp, spriteDp) {
        if (screenWidthDp <= 0f || screenHeightDp <= 0f) return@LaunchedEffect
        val habitat = computeHabitat(screenWidthDp, screenHeightDp, spriteDp)
        habitatBoundsState = habitat.bounds
        vm.setHabitat(habitat.bounds, habitat.anchors)
    }
```

becomes:

```kotlin
    val spriteDp = stageSpriteSize(state.stage).value
    LaunchedEffect(screenWidthDp, screenHeightDp, spriteDp) {
        if (screenWidthDp <= 0f || screenHeightDp <= 0f) return@LaunchedEffect
        val habitat =
            computeHabitat(
                widthDp = screenWidthDp,
                heightDp = screenHeightDp,
                topReservedDp = screenHeightDp * 0.40f,
                bottomReservedDp = screenHeightDp * 0.15f,
                spriteDp = spriteDp,
            )
        habitatBoundsState = habitat.bounds
        vm.setHabitat(habitat.bounds, habitat.anchors)
    }
```

The `0.40f` and `0.15f` are the literal substitutions of the old constants. Task 3 and Task 4 will replace them with measured state.

- [ ] **Step 3: Update existing tests + add the coerce case**

Overwrite `app/src/test/kotlin/com/pocketpets/app/ui/pet/HabitatTest.kt`:

```kotlin
package com.pocketpets.app.ui.pet

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HabitatTest {
    // 600 * 0.40 = 240 ; 600 * 0.15 = 90  (the pre-measurement floor band).
    private val typicalTop = 240f
    private val typicalBottom = 90f

    @Test
    fun `bounds reserve room for sprite on right edge`() {
        val habitat =
            computeHabitat(
                widthDp = 400f,
                heightDp = 600f,
                topReservedDp = typicalTop,
                bottomReservedDp = typicalBottom,
                spriteDp = 256f,
            )
        assertThat(habitat.bounds.maxX + 256f).isAtMost(400f)
    }

    @Test
    fun `bounds reserve room for sprite on bottom edge`() {
        val habitat =
            computeHabitat(
                widthDp = 400f,
                heightDp = 600f,
                topReservedDp = typicalTop,
                bottomReservedDp = typicalBottom,
                spriteDp = 256f,
            )
        // Floor bottom = 600 - 90 = 510. Sprite must end at or above 510.
        assertThat(habitat.bounds.maxY + 256f).isAtMost(510f)
    }

    @Test
    fun `larger sprite shrinks playable bounds`() {
        val small =
            computeHabitat(400f, 600f, topReservedDp = typicalTop, bottomReservedDp = typicalBottom, spriteDp = 64f)
        val large =
            computeHabitat(400f, 600f, topReservedDp = typicalTop, bottomReservedDp = typicalBottom, spriteDp = 256f)
        assertThat(large.bounds.maxX).isLessThan(small.bounds.maxX)
        assertThat(large.bounds.maxY).isLessThan(small.bounds.maxY)
    }

    @Test
    fun `taller bottom overlay shrinks bottom of bounds`() {
        val short =
            computeHabitat(400f, 600f, topReservedDp = typicalTop, bottomReservedDp = 90f, spriteDp = 256f)
        val tall =
            computeHabitat(400f, 600f, topReservedDp = typicalTop, bottomReservedDp = 180f, spriteDp = 256f)
        assertThat(tall.bounds.maxY).isLessThan(short.bounds.maxY)
    }

    @Test
    fun `taller top overlay shrinks top of bounds`() {
        val short =
            computeHabitat(400f, 600f, topReservedDp = 100f, bottomReservedDp = typicalBottom, spriteDp = 256f)
        val tall =
            computeHabitat(400f, 600f, topReservedDp = 240f, bottomReservedDp = typicalBottom, spriteDp = 256f)
        assertThat(tall.bounds.minY).isGreaterThan(short.bounds.minY)
    }

    @Test
    fun `bowl anchor is fully inside playable bounds`() {
        val habitat =
            computeHabitat(400f, 600f, typicalTop, typicalBottom, 256f)
        val bowl = habitat.anchors.bowl
        assertThat(bowl.x).isAtLeast(habitat.bounds.minX)
        assertThat(bowl.x).isAtMost(habitat.bounds.maxX)
        assertThat(bowl.y).isAtLeast(habitat.bounds.minY)
        assertThat(bowl.y).isAtMost(habitat.bounds.maxY)
    }

    @Test
    fun `bed anchor is fully inside playable bounds`() {
        val habitat =
            computeHabitat(400f, 600f, typicalTop, typicalBottom, 256f)
        val bed = habitat.anchors.bed
        assertThat(bed.x).isAtLeast(habitat.bounds.minX)
        assertThat(bed.x).isAtMost(habitat.bounds.maxX)
        assertThat(bed.y).isAtLeast(habitat.bounds.minY)
        assertThat(bed.y).isAtMost(habitat.bounds.maxY)
    }

    @Test
    fun `bounds and anchors stay valid when overlays exceed available height`() {
        // Stress case: top + bottom reserved > screen height, sprite > screen width.
        // HabitatBounds.init would throw if the coerceAtLeast clamps weren't there.
        val habitat =
            computeHabitat(
                widthDp = 200f,
                heightDp = 200f,
                topReservedDp = 180f,
                bottomReservedDp = 100f,
                spriteDp = 192f,
            )
        assertThat(habitat.bounds.minX).isLessThan(habitat.bounds.maxX)
        assertThat(habitat.bounds.minY).isLessThan(habitat.bounds.maxY)
        // Anchors stay inside the (degenerate) bounds.
        val bed = habitat.anchors.bed
        val bowl = habitat.anchors.bowl
        assertThat(bed.x).isAtLeast(habitat.bounds.minX)
        assertThat(bed.x).isAtMost(habitat.bounds.maxX)
        assertThat(bed.y).isAtLeast(habitat.bounds.minY)
        assertThat(bed.y).isAtMost(habitat.bounds.maxY)
        assertThat(bowl.x).isAtLeast(habitat.bounds.minX)
        assertThat(bowl.x).isAtMost(habitat.bounds.maxX)
        assertThat(bowl.y).isAtLeast(habitat.bounds.minY)
        assertThat(bowl.y).isAtMost(habitat.bounds.maxY)
    }
}
```

- [ ] **Step 4: Run the tests**

```bash
./gradlew :app:testDebugUnitTest --tests "com.pocketpets.app.ui.pet.HabitatTest"
```

Expected: all 8 tests PASS. If a numeric assertion fails, check that the bridge values in `PetScreen.kt` are `heightDp * 0.40f` and `heightDp * 0.15f`, not flipped.

- [ ] **Step 5: Run ktlint**

```bash
./gradlew ktlintCheck
```

Expected: SUCCESS. If it fails, run `./gradlew ktlintFormat` and re-check.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/pocketpets/app/ui/pet/Habitat.kt \
        app/src/test/kotlin/com/pocketpets/app/ui/pet/HabitatTest.kt \
        app/src/main/kotlin/com/pocketpets/app/ui/pet/PetScreen.kt
git commit -m "$(cat <<'EOF'
refactor: computeHabitat takes measured topReservedDp/bottomReservedDp

Drops the FLOOR_TOP_FRACTION / FLOOR_BOTTOM_FRACTION constants in
favour of explicit measured-overlay parameters. PetScreen passes
heightDp * 0.40f / heightDp * 0.15f for now — the literal substitution
of the old constants — so behaviour is identical until later tasks
swap in real onSizeChanged measurements.

Refs #12.
EOF
)"
```

---

## Task 2: `dropTargetAt` takes a `playAreaRect` instead of `HabitatBounds`

**Files:**
- Modify: `app/src/main/kotlin/com/pocketpets/app/ui/inventory/DropTargets.kt`
- Modify: `app/src/test/kotlin/com/pocketpets/app/ui/inventory/DropTargetsTest.kt`
- Modify: `app/src/main/kotlin/com/pocketpets/app/ui/pet/PetScreen.kt` (only the `dropTargetAt` call site)

The toy floor target should accept anywhere visually inside the play area, not only where the cat's top-left can sit. Switching the parameter makes the call site honest about that.

- [ ] **Step 1: Replace `DropTargets.kt` with the new signature**

Overwrite `app/src/main/kotlin/com/pocketpets/app/ui/inventory/DropTargets.kt`:

```kotlin
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
 */
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

- [ ] **Step 2: Update the `PetScreen` drop call site**

In `app/src/main/kotlin/com/pocketpets/app/ui/pet/PetScreen.kt`, locate the `onDragEnd` lambda inside the tray's `detectDragGesturesAfterLongPress`. Replace its body. The current block:

```kotlin
                                onDragEnd = {
                                    val ended =
                                        dragController.end() ?: return@detectDragGesturesAfterLongPress
                                    val bounds =
                                        habitatBoundsState ?: return@detectDragGesturesAfterLongPress
                                    val poopRects =
                                        (0 until pet.poopCount).map { i ->
                                            poopRectFor(i, poopOffsets, screenWidthDp, screenHeightDp)
                                        }
                                    val bowlRect =
                                        DpRect(
                                            left = 24f,
                                            top = screenHeightDp - 132f,
                                            right = 24f + 64f,
                                            bottom = screenHeightDp - 132f + 32f,
                                        )
                                    val target =
                                        dropTargetAt(
                                            position = ended.position,
                                            item = ended.item,
                                            bounds = bounds,
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

becomes:

```kotlin
                                onDragEnd = {
                                    val ended =
                                        dragController.end() ?: return@detectDragGesturesAfterLongPress
                                    if (screenWidthDp <= 0f || screenHeightDp <= 0f) {
                                        return@detectDragGesturesAfterLongPress
                                    }
                                    val playAreaRect =
                                        DpRect(
                                            left = 0f,
                                            top = screenHeightDp * 0.40f,
                                            right = screenWidthDp,
                                            bottom = screenHeightDp * 0.85f,
                                        )
                                    val poopRects =
                                        (0 until pet.poopCount).map { i ->
                                            poopRectFor(i, poopOffsets, screenWidthDp, screenHeightDp)
                                        }
                                    val bowlRect =
                                        DpRect(
                                            left = 24f,
                                            top = screenHeightDp - 132f,
                                            right = 24f + 64f,
                                            bottom = screenHeightDp - 132f + 32f,
                                        )
                                    val target =
                                        dropTargetAt(
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

The `habitatBoundsState ?: return` guard is replaced with a `screenWidthDp/heightDp > 0` guard. The `playAreaRect` uses the same `0.40f`/`0.85f` bridge fractions as Task 1; Task 4 will swap these for measured state.

- [ ] **Step 3: Update `DropTargetsTest` fixtures and add the new case**

Overwrite `app/src/test/kotlin/com/pocketpets/app/ui/inventory/DropTargetsTest.kt`:

```kotlin
package com.pocketpets.app.ui.inventory

import com.google.common.truth.Truth.assertThat
import com.pocketpets.app.domain.behavior.Position
import org.junit.Test

class DropTargetsTest {
    private val playAreaRect = DpRect(0f, 0f, 200f, 200f)
    private val bowlRect = DpRect(20f, 160f, 84f, 192f)
    private val poopRects =
        listOf(
            DpRect(80f, 100f, 128f, 148f),
            DpRect(120f, 100f, 168f, 148f),
        )

    private fun resolve(
        item: Item,
        x: Float,
        y: Float,
    ) = dropTargetAt(Position(x, y), item, playAreaRect, bowlRect, poopRects)

    @Test
    fun `food on the bowl resolves to Bowl`() {
        assertThat(resolve(Item.Food, bowlRect.left + 10f, bowlRect.top + 10f))
            .isEqualTo(DropTarget.Bowl)
    }

    @Test
    fun `food off the bowl resolves to null`() {
        assertThat(resolve(Item.Food, 100f, 50f)).isNull()
    }

    @Test
    fun `food on a poop rect still resolves to null (food only goes in bowl)`() {
        val (px, py) = poopRects[0].center()
        assertThat(resolve(Item.Food, px, py)).isNull()
    }

    @Test
    fun `scoop on a poop resolves to that Poop index`() {
        val (px, py) = poopRects[1].center()
        assertThat(resolve(Item.Scoop, px, py)).isEqualTo(DropTarget.Poop(1))
    }

    @Test
    fun `scoop off any poop resolves to null`() {
        assertThat(resolve(Item.Scoop, 10f, 10f)).isNull()
    }

    @Test
    fun `scoop on the bowl resolves to null (scoop only on poops)`() {
        assertThat(resolve(Item.Scoop, bowlRect.left + 10f, bowlRect.top + 10f)).isNull()
    }

    @Test
    fun `toy inside play area and not on bowl resolves to Floor at the drop position`() {
        val out = resolve(Item.Toy, 100f, 50f)
        assertThat(out).isEqualTo(DropTarget.Floor(Position(100f, 50f)))
    }

    @Test
    fun `toy outside play area resolves to null`() {
        assertThat(resolve(Item.Toy, -10f, 50f)).isNull()
        assertThat(resolve(Item.Toy, 1000f, 50f)).isNull()
        assertThat(resolve(Item.Toy, 50f, -10f)).isNull()
        assertThat(resolve(Item.Toy, 50f, 1000f)).isNull()
    }

    @Test
    fun `toy lands on Floor even if dropped on a poop (poop doesn't claim toys)`() {
        val (px, py) = poopRects[0].center()
        val out = resolve(Item.Toy, px, py)
        assertThat(out).isEqualTo(DropTarget.Floor(Position(px, py)))
    }

    @Test
    fun `toy on the bowl resolves to Floor at the bowl coordinates (toy doesn't claim bowl)`() {
        val out = resolve(Item.Toy, bowlRect.left + 10f, bowlRect.top + 10f)
        assertThat(out).isEqualTo(DropTarget.Floor(Position(bowlRect.left + 10f, bowlRect.top + 10f)))
    }

    @Test
    fun `toy dropped near play-area bottom (where cat top-left could not sit) still resolves to Floor`() {
        // The pre-change implementation used HabitatBounds for the toy target, so a drop
        // below `bounds.maxY` (the cat-movable maxY = playArea.bottom - spriteHeight) was
        // rejected. With `playAreaRect`, the drop is accepted because the toy itself is
        // small and the visible floor extends to playAreaRect.bottom.
        val (cx, cy) = playAreaRect.center()
        val nearBottom = Position(cx, playAreaRect.bottom - 1f)
        val out = dropTargetAt(nearBottom, Item.Toy, playAreaRect, bowlRect, poopRects)
        assertThat(out).isEqualTo(DropTarget.Floor(nearBottom))
    }
}
```

- [ ] **Step 4: Run the tests**

```bash
./gradlew :app:testDebugUnitTest --tests "com.pocketpets.app.ui.inventory.DropTargetsTest"
```

Expected: all 11 tests PASS.

- [ ] **Step 5: Run full test suite (sanity check — `PetScreen` still compiles)**

```bash
./gradlew test
```

Expected: BUILD SUCCESSFUL. If `PetScreen.kt` fails to compile, confirm the named-arg `playAreaRect = playAreaRect` (not `bounds = ...`) at the `dropTargetAt` call site.

- [ ] **Step 6: Run ktlint**

```bash
./gradlew ktlintCheck
```

Expected: SUCCESS.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/com/pocketpets/app/ui/inventory/DropTargets.kt \
        app/src/test/kotlin/com/pocketpets/app/ui/inventory/DropTargetsTest.kt \
        app/src/main/kotlin/com/pocketpets/app/ui/pet/PetScreen.kt
git commit -m "$(cat <<'EOF'
refactor: dropTargetAt takes playAreaRect instead of HabitatBounds

Toy floor drops should accept anywhere visually inside the play area —
including the band below `bounds.maxY` where the cat's top-left can't
sit. PetScreen builds the playAreaRect from the same fractional values
the bridge in computeHabitat uses; Task 4 swaps both for measured
state.

Refs #12.
EOF
)"
```

---

## Task 3: Measure the top overlays into `topReservedDp`

**Files:**
- Modify: `app/src/main/kotlin/com/pocketpets/app/ui/pet/PetScreen.kt`

Group the top bar Row and the stat-chip Row inside a single Column aligned `TopStart`. Attach `onSizeChanged` to it and pipe the height into a new `topReservedDp` state, which then drives both `computeHabitat`'s `topReservedDp` and `playAreaRect.top` in the drop call. The stat-chip Row loses its `padding(top = 60.dp)` since it's now positioned by the Column instead of by absolute padding.

- [ ] **Step 1: Add `topReservedDp` state and the wrapping Column**

In `app/src/main/kotlin/com/pocketpets/app/ui/pet/PetScreen.kt`:

(a) Add `topReservedDp` next to the other measurement state:

```kotlin
    var screenWidthDp by remember { mutableFloatStateOf(0f) }
    var screenHeightDp by remember { mutableFloatStateOf(0f) }
    var topReservedDp by remember { mutableFloatStateOf(0f) }
```

(b) Replace the two separate top-of-screen blocks (the "Top bar" Row and the "Stat chips just under the top bar" Row) with a single Column that wraps both and reports its height. The current code:

```kotlin
        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onOpenSelector) {
                Icon(Icons.Default.Menu, contentDescription = "Switch pet")
            }
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(pet?.name ?: "—")
                if (pet != null) {
                    Text(stageLabel(state.stage), color = Color(0xFF555555))
                }
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
        }

        // Stat chips just under the top bar
        if (pet != null) {
            Row(
                modifier =
                    Modifier
                        .padding(top = 60.dp, start = 8.dp, end = 8.dp)
                        .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                StatChip("🍗", pet.stats.hunger, Color(0xFFE6843D))
                StatChip("🛁", pet.stats.cleanliness, Color(0xFF7AB7E8))
                StatChip("💗", pet.stats.happiness, Color(0xFFE86A8D))
                StatChip("⚡", pet.stats.energy, Color(0xFFE8C13D))
            }
        }
```

becomes:

```kotlin
        // Top overlays: top bar + stat chips. Grouped into a single Column so
        // onSizeChanged reports the combined height we reserve at the top of the
        // play area.
        Column(
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .onSizeChanged { sizePx ->
                        with(density) {
                            topReservedDp = sizePx.height.toDp().value
                        }
                    },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onOpenSelector) {
                    Icon(Icons.Default.Menu, contentDescription = "Switch pet")
                }
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(pet?.name ?: "—")
                    if (pet != null) {
                        Text(stageLabel(state.stage), color = Color(0xFF555555))
                    }
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings")
                }
            }
            if (pet != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    StatChip("🍗", pet.stats.hunger, Color(0xFFE6843D))
                    StatChip("🛁", pet.stats.cleanliness, Color(0xFF7AB7E8))
                    StatChip("💗", pet.stats.happiness, Color(0xFFE86A8D))
                    StatChip("⚡", pet.stats.energy, Color(0xFFE8C13D))
                }
            }
        }
```

The stat-chip Row's `padding(top = 60.dp)` is removed — chips now sit immediately under the top bar via the Column's natural stacking.

- [ ] **Step 2: Wire `topReservedDp` into `computeHabitat`**

Find the `LaunchedEffect` and update it. The current call:

```kotlin
    val spriteDp = stageSpriteSize(state.stage).value
    LaunchedEffect(screenWidthDp, screenHeightDp, spriteDp) {
        if (screenWidthDp <= 0f || screenHeightDp <= 0f) return@LaunchedEffect
        val habitat =
            computeHabitat(
                widthDp = screenWidthDp,
                heightDp = screenHeightDp,
                topReservedDp = screenHeightDp * 0.40f,
                bottomReservedDp = screenHeightDp * 0.15f,
                spriteDp = spriteDp,
            )
        habitatBoundsState = habitat.bounds
        vm.setHabitat(habitat.bounds, habitat.anchors)
    }
```

becomes:

```kotlin
    val spriteDp = stageSpriteSize(state.stage).value
    LaunchedEffect(screenWidthDp, screenHeightDp, topReservedDp, spriteDp) {
        if (screenWidthDp <= 0f || screenHeightDp <= 0f || topReservedDp <= 0f) {
            return@LaunchedEffect
        }
        val habitat =
            computeHabitat(
                widthDp = screenWidthDp,
                heightDp = screenHeightDp,
                topReservedDp = topReservedDp,
                bottomReservedDp = screenHeightDp * 0.15f,
                spriteDp = spriteDp,
            )
        habitatBoundsState = habitat.bounds
        vm.setHabitat(habitat.bounds, habitat.anchors)
    }
```

- [ ] **Step 3: Wire `topReservedDp` into the drop call's `playAreaRect`**

In the `onDragEnd` block, replace the bridge top fraction. The current block builds `playAreaRect` with `top = screenHeightDp * 0.40f`. Change that line to:

```kotlin
                                    if (screenWidthDp <= 0f || screenHeightDp <= 0f || topReservedDp <= 0f) {
                                        return@detectDragGesturesAfterLongPress
                                    }
                                    val playAreaRect =
                                        DpRect(
                                            left = 0f,
                                            top = topReservedDp,
                                            right = screenWidthDp,
                                            bottom = screenHeightDp * 0.85f,
                                        )
```

(Note: the gate's `topReservedDp <= 0f` check is added; the `bottom` line still uses the `0.85f` bridge until Task 4.)

- [ ] **Step 4: Run all tests**

```bash
./gradlew test
```

Expected: BUILD SUCCESSFUL. Existing pure-function tests don't exercise PetScreen, so the suite passing only proves no regressions.

- [ ] **Step 5: Run ktlint and android lint**

```bash
./gradlew ktlintCheck :app:lintDebug
```

Expected: SUCCESS on both. If ktlintCheck flags formatting, run `./gradlew ktlintFormat` and re-check.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/pocketpets/app/ui/pet/PetScreen.kt
git commit -m "$(cat <<'EOF'
refactor: PetScreen measures top overlay height into topReservedDp

Top bar Row and stat-chip Row now live in a single TopStart Column
whose height is reported via onSizeChanged. That measurement drives
computeHabitat's topReservedDp and the playAreaRect's top edge, so
the play area is bounded by the actual overlay height rather than a
0.40f fraction of screen height. Drops are gated until top measurement
arrives.

Refs #12.
EOF
)"
```

---

## Task 4: Measure the inventory tray into `bottomReservedDp`; reposition decor

**Files:**
- Modify: `app/src/main/kotlin/com/pocketpets/app/ui/pet/PetScreen.kt`

Add `onSizeChanged` to the tray's outer Box. Define `playAreaBottom = screenHeightDp - bottomReservedDp` and use it for the bowl and poop render positions, the `poopRectFor` rect, the bowl drop rect, and the `playAreaRect.bottom` field. Drop the `0.85f` and `screenHeightDp - 132f` bridge values. The `computeHabitat` call passes measured `bottomReservedDp`.

- [ ] **Step 1: Add `bottomReservedDp` state and `onSizeChanged` on the tray Box**

Add the state field next to the other measurements:

```kotlin
    var screenWidthDp by remember { mutableFloatStateOf(0f) }
    var screenHeightDp by remember { mutableFloatStateOf(0f) }
    var topReservedDp by remember { mutableFloatStateOf(0f) }
    var bottomReservedDp by remember { mutableFloatStateOf(0f) }
```

Find the tray's outer Box. The current modifier chain:

```kotlin
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .onGloballyPositioned { coords ->
                            trayRootOffsetPx = coords.positionInRoot()
                        }.pointerInput(pet.id) {
```

becomes:

```kotlin
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .onSizeChanged { sizePx ->
                            with(density) {
                                bottomReservedDp = sizePx.height.toDp().value
                            }
                        }.onGloballyPositioned { coords ->
                            trayRootOffsetPx = coords.positionInRoot()
                        }.pointerInput(pet.id) {
```

- [ ] **Step 2: Switch `computeHabitat` to measured `bottomReservedDp`**

The `LaunchedEffect` after Task 3:

```kotlin
    LaunchedEffect(screenWidthDp, screenHeightDp, topReservedDp, spriteDp) {
        if (screenWidthDp <= 0f || screenHeightDp <= 0f || topReservedDp <= 0f) {
            return@LaunchedEffect
        }
        val habitat =
            computeHabitat(
                widthDp = screenWidthDp,
                heightDp = screenHeightDp,
                topReservedDp = topReservedDp,
                bottomReservedDp = screenHeightDp * 0.15f,
                spriteDp = spriteDp,
            )
        habitatBoundsState = habitat.bounds
        vm.setHabitat(habitat.bounds, habitat.anchors)
    }
```

becomes:

```kotlin
    LaunchedEffect(screenWidthDp, screenHeightDp, topReservedDp, bottomReservedDp, spriteDp) {
        if (screenWidthDp <= 0f ||
            screenHeightDp <= 0f ||
            topReservedDp <= 0f ||
            bottomReservedDp <= 0f
        ) {
            return@LaunchedEffect
        }
        val habitat =
            computeHabitat(
                widthDp = screenWidthDp,
                heightDp = screenHeightDp,
                topReservedDp = topReservedDp,
                bottomReservedDp = bottomReservedDp,
                spriteDp = spriteDp,
            )
        habitatBoundsState = habitat.bounds
        vm.setHabitat(habitat.bounds, habitat.anchors)
    }
```

- [ ] **Step 3: Update `poopRectFor` to take `playAreaBottom`**

At the top of `PetScreen.kt`, replace:

```kotlin
private fun poopRectFor(
    i: Int,
    offsets: List<Int>,
    screenWidthDp: Float,
    screenHeightDp: Float,
): DpRect {
    val sizeDp = 48f
    val centerX = screenWidthDp / 2f + offsets[i] - sizeDp / 2f
    val bottomMargin = (110 + i * 6).toFloat()
    val top = screenHeightDp - bottomMargin - sizeDp
    return DpRect(
        left = centerX,
        top = top,
        right = centerX + sizeDp,
        bottom = top + sizeDp,
    )
}
```

with:

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

- [ ] **Step 4: Reposition the bowl render**

Find the bowl Image (`if (state.world.bowlFilled) R.drawable.bowl_full else R.drawable.bowl`) and replace its modifier. Current:

```kotlin
            Image(
                painter =
                    painterResource(
                        if (state.world.bowlFilled) R.drawable.bowl_full else R.drawable.bowl,
                    ),
                contentDescription = null,
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 24.dp, bottom = 100.dp)
                        .size(width = 64.dp, height = 32.dp),
            )
```

becomes:

```kotlin
            val playAreaBottom = screenHeightDp - bottomReservedDp
            Image(
                painter =
                    painterResource(
                        if (state.world.bowlFilled) R.drawable.bowl_full else R.drawable.bowl,
                    ),
                contentDescription = null,
                modifier =
                    Modifier
                        .offset(x = 24.dp, y = (playAreaBottom - 32f - 16f).dp)
                        .size(width = 64.dp, height = 32.dp),
            )
```

(The `val playAreaBottom = ...` line is computed once and reused below for the poops.)

- [ ] **Step 5: Reposition the poop renders**

Replace the `repeat(pet.poopCount)` block:

```kotlin
            repeat(pet.poopCount) { i ->
                val xOffset = poopOffsets[i]
                Image(
                    painter = painterResource(R.drawable.poop),
                    contentDescription = null,
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = (110 + i * 6).dp)
                            .offset(x = xOffset.dp)
                            .size(48.dp),
                )
            }
```

with:

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

- [ ] **Step 6: Update the `onDragEnd` drop call to use measured `playAreaBottom`**

Replace the `onDragEnd` lambda's guard, `playAreaRect`, `poopRects`, and `bowlRect`. Current after Task 3:

```kotlin
                                onDragEnd = {
                                    val ended =
                                        dragController.end() ?: return@detectDragGesturesAfterLongPress
                                    if (screenWidthDp <= 0f || screenHeightDp <= 0f || topReservedDp <= 0f) {
                                        return@detectDragGesturesAfterLongPress
                                    }
                                    val playAreaRect =
                                        DpRect(
                                            left = 0f,
                                            top = topReservedDp,
                                            right = screenWidthDp,
                                            bottom = screenHeightDp * 0.85f,
                                        )
                                    val poopRects =
                                        (0 until pet.poopCount).map { i ->
                                            poopRectFor(i, poopOffsets, screenWidthDp, screenHeightDp)
                                        }
                                    val bowlRect =
                                        DpRect(
                                            left = 24f,
                                            top = screenHeightDp - 132f,
                                            right = 24f + 64f,
                                            bottom = screenHeightDp - 132f + 32f,
                                        )
```

becomes:

```kotlin
                                onDragEnd = {
                                    val ended =
                                        dragController.end() ?: return@detectDragGesturesAfterLongPress
                                    if (screenWidthDp <= 0f ||
                                        screenHeightDp <= 0f ||
                                        topReservedDp <= 0f ||
                                        bottomReservedDp <= 0f
                                    ) {
                                        return@detectDragGesturesAfterLongPress
                                    }
                                    val playAreaBottomLocal = screenHeightDp - bottomReservedDp
                                    val playAreaRect =
                                        DpRect(
                                            left = 0f,
                                            top = topReservedDp,
                                            right = screenWidthDp,
                                            bottom = playAreaBottomLocal,
                                        )
                                    val poopRects =
                                        (0 until pet.poopCount).map { i ->
                                            poopRectFor(i, poopOffsets, screenWidthDp, playAreaBottomLocal)
                                        }
                                    val bowlRect =
                                        DpRect(
                                            left = 24f,
                                            top = playAreaBottomLocal - 32f - 16f,
                                            right = 24f + 64f,
                                            bottom = playAreaBottomLocal - 16f,
                                        )
```

Note: the bowl drop rect (`playAreaBottomLocal - 32f - 16f` … `playAreaBottomLocal - 16f`) exactly matches the bowl render `offset(x = 24.dp, y = (playAreaBottom - 32f - 16f).dp).size(64.dp x 32.dp)`. Keep these in sync.

- [ ] **Step 7: Run all tests**

```bash
./gradlew test
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Run ktlint and android lint**

```bash
./gradlew ktlintCheck :app:lintDebug
```

Expected: SUCCESS on both. Run `./gradlew ktlintFormat` if needed.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/kotlin/com/pocketpets/app/ui/pet/PetScreen.kt
git commit -m "$(cat <<'EOF'
fix: PetScreen anchors decor + drops to measured tray height (#12)

Tray's outer Box now reports its height via onSizeChanged into a new
bottomReservedDp state. Bowl, poops, bowl drop rect, poop drop rects,
and playAreaRect.bottom all derive from playAreaBottom = screenHeightDp
- bottomReservedDp. computeHabitat receives the measured bottom too.

The play area is now bounded by the real overlay heights end-to-end,
so the cat sprite is fully contained inside the play area and every
visible pixel of the cat accepts taps and long-presses.

Fixes #12.
EOF
)"
```

---

## Task 5: Remove the now-dead `habitatBoundsState` field and import

**Files:**
- Modify: `app/src/main/kotlin/com/pocketpets/app/ui/pet/PetScreen.kt`

After Task 4, `habitatBoundsState` is only written, never read. Remove it and the `HabitatBounds` import that came with it.

- [ ] **Step 1: Remove the `habitatBoundsState` state declaration**

Delete this line near the top of `PetScreen`:

```kotlin
    var habitatBoundsState by remember { mutableStateOf<HabitatBounds?>(null) }
```

- [ ] **Step 2: Remove the assignment inside the `LaunchedEffect`**

Delete the `habitatBoundsState = habitat.bounds` line so the `LaunchedEffect` body becomes:

```kotlin
        val habitat =
            computeHabitat(
                widthDp = screenWidthDp,
                heightDp = screenHeightDp,
                topReservedDp = topReservedDp,
                bottomReservedDp = bottomReservedDp,
                spriteDp = spriteDp,
            )
        vm.setHabitat(habitat.bounds, habitat.anchors)
```

- [ ] **Step 3: Remove the unused `HabitatBounds` import**

Delete this import from the top of the file:

```kotlin
import com.pocketpets.app.domain.behavior.HabitatBounds
```

- [ ] **Step 4: Run all tests**

```bash
./gradlew test
```

Expected: BUILD SUCCESSFUL. If a `Unresolved reference: HabitatBounds` compile error appears, search for stray uses inside `PetScreen.kt` — every reference should be gone.

- [ ] **Step 5: Run ktlint and android lint**

```bash
./gradlew ktlintCheck :app:lintDebug
```

Expected: SUCCESS on both.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/pocketpets/app/ui/pet/PetScreen.kt
git commit -m "$(cat <<'EOF'
chore: drop dead habitatBoundsState field from PetScreen

After dropTargetAt switched to playAreaRect, habitatBoundsState was
only written, never read. Remove the field, the LaunchedEffect write,
and the unused HabitatBounds import.

Refs #12.
EOF
)"
```

---

## Final verification

- [ ] **Step 1: Full unit-test suite**

```bash
./gradlew test
```

Expected: BUILD SUCCESSFUL, all suites green (HabitatTest 8, DropTargetsTest 11, plus the pre-existing PetViewModelTest / CatBehaviorRulesTest / etc.).

- [ ] **Step 2: Lint**

```bash
./gradlew ktlintCheck :app:lintDebug
```

Expected: SUCCESS on both.

- [ ] **Step 3: Build the debug APK**

```bash
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Smoke-test on a device or emulator**

Install the debug APK. Walk through:
- The cat appears fully inside the play-area band between the stat chips and the inventory tray, at every growth stage (baby/juvenile/adult — `repo.adoptForTesting` or normal growth).
- Long-pressing any visible pixel of the cat triggers a pet (happiness rises; speech bubble may appear).
- Dragging the toy and dropping it near the very bottom of the play area (just above the tray) results in the cat walking to it.
- Dragging the food onto the bowl still fills the bowl; dragging the scoop onto a poop still removes it.

- [ ] **Step 5: Mark PR ready for review**

If this branch is on draft PR #21, run:

```bash
gh pr ready 21
```

Otherwise open a new PR per the project's standard process. Reference: `Fixes #12.`
