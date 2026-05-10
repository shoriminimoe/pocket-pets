# Cat interactions — direct manipulation (Phase 3)

**Date:** 2026-05-10
**Phase:** 3 of 3 in the cat-interactivity initiative
**Goal:** Replace the Feed/Clean/Pet/Talk button row with direct-manipulation gestures: drag food onto the bowl, drag the scoop onto each poop, drop a toy on the floor, touch-and-hold the cat to pet. The cat reacts in-world by walking to the food/toy and entering new `Eating` / `Playing` states.

## 1. Why

The original feature ask had three parts: more interactivity, higher-fidelity graphics, direct-manipulation actions. Phase 1 swapped the cat art and rebuilt the renderer. Phase 2 made the cat walk around with mood-driven destinations (bed/bowl). This phase wires up the actual direct manipulations the user asked for, so feeding feels like *putting food in a bowl* instead of pressing a button labelled "Feed".

The button row was always a placeholder — the spec called it out explicitly: "the tamagotchi example was more of a spirit of the idea and less about the actual appearance and behavior."

## 2. Scope

### In

- A bottom-edge **inventory tray** with three draggable items: `Food`, `Scoop`, `Toy`. Items have unlimited supply.
- A **drag-drop layer** that resolves drops to one of: `Bowl`, `Poop[i]`, `Floor`, or rejected.
- **Two new cat states**: `Eating` (5 s at the filled bowl), `Playing` (10 s at the dropped toy).
- A small **`HabitatWorld`** value object holding `bowlFilled: Boolean` and `toy: Position?`, fed into `CatBehaviorRules.tick`. `pickTarget` is extended so a thrown toy preempts mood, and the bowl is only chosen when filled.
- **Touch-and-hold** the cat to pet (long-press → `repo.pet`). Existing diminishing-returns logic in the repo is unchanged.
- The Feed / Clean / Pet / Talk button row is **removed**. Idle chatter (the existing 2-minute mood ticker) stays as the only path to speech bubbles.

### Out

- Item counts, restocking, currency, shop. Items are infinite this phase.
- Multiple toys out at once, food stacking, multiple food bowls.
- New eating / playing sprite art. Eating reuses `sit`; Playing reuses `walk`-in-place. A proper pounce / chow animation is a later asset iteration.
- An on-demand "talk" gesture or button. Speech-bubble chatter remains driven only by the existing 2-minute idle ticker.
- Cat reacting *visually* to interactions beyond the new Eating/Playing states (e.g. tail flick at a poop, looking at the user during pet). Possible follow-up.
- Multi-cat interactions. Single active pet.

### Success criteria

- Dragging the food icon onto the bowl visibly fills the bowl, after which a hungry cat walks over, sits at the bowl for ~5 s, and hunger jumps to 100 (today's `feed()` semantics). The bowl empties when the cat finishes eating.
- Dragging the scoop onto a single poop sprite removes that one poop. Five poops on the floor takes five drags.
- Dragging the toy and dropping it inside the floor band makes the cat walk to that spot, "play" for ~10 s (happiness rises via the existing `pet()` action), then the toy disappears.
- Long-pressing the cat triggers `pet()` (happiness rises). Diminishing returns still cap repeated triggers within a short window.
- The bottom of the screen shows the inventory tray, not buttons.
- All Phase 2 walking, mood routing, and breathing behaviour still works.

## 3. UI architecture

### 3.1 Layer ordering (bottom → top)

1. Room background + floor anchors (existing).
2. Bowl decor — visual state now reflects `world.bowlFilled` (full-bowl drawable variant).
3. Poop sprites (existing, count-driven).
4. Toy sprite — rendered at `world.toy` when non-null.
5. Cat sprite (offset by `behavior.position`, existing).
6. Speech bubble (existing).
7. **Drag overlay** — when the user is mid-drag, the in-flight item icon is rendered at the pointer position with a slight scale/lift. Other UI is dimmed slightly (alpha ~0.85) to make targets read.
8. **Inventory tray** — three slots along the bottom edge of the screen.

The drag overlay is a single full-screen `Box` that is empty when no drag is in flight, so it costs nothing in the resting state.

### 3.2 InventoryTray

```kotlin
@Composable
fun InventoryTray(
    onItemDragStart: (Item) -> Unit,
    onItemDragMove: (Position) -> Unit,
    onItemDragEnd: (Position) -> Unit,
    modifier: Modifier = Modifier,
)
```

Three fixed slots, each rendered as a `Box` with the item's icon. Each slot uses `pointerInput { detectDragGesturesAfterLongPress(...) }`:

- `onDragStart` calls `onItemDragStart(slotItem)`.
- `onDrag` calls `onItemDragMove(currentPositionDp)` where `currentPositionDp` is converted from raw pixels via `LocalDensity`.
- `onDragEnd` / `onDragCancel` calls `onItemDragEnd(lastPositionDp)`.

`detectDragGesturesAfterLongPress` is chosen so the user must press-and-hold to pick up an item — short taps on tray slots do nothing (no accidental drops while scrolling around).

Item icons (`food.png`, `scoop.png`, `toy.png`) are added under `app/src/main/res/drawable-nodpi/`. Generated procedurally by `tools/generate_sprites.py` (which already handles decor PNGs); no external asset fetch.

### 3.3 DragController

A small Compose state holder owned by `PetScreen`:

```kotlin
class DragController {
    var inFlight: DragInFlight? by mutableStateOf(null)
        private set

    fun start(item: Item) { inFlight = DragInFlight(item, Position(0f, 0f)) }
    fun move(pos: Position) { inFlight = inFlight?.copy(position = pos) }
    fun end(): DragInFlight? { val v = inFlight; inFlight = null; return v }
}

data class DragInFlight(val item: Item, val position: Position)
```

The drag overlay reads `dragController.inFlight` and renders the icon at that position when non-null. Tray slots call `start` / `move` / `end` from their pointer-input handlers. `PetScreen` calls `dropTargetAt(position, item, bounds, anchors, poopRects)` (signature in §3.4) on `end()` to resolve where the drop landed and dispatches the right ViewModel callback.

### 3.4 Drop target resolution

```kotlin
sealed interface DropTarget {
    data object Bowl : DropTarget
    data class Poop(val index: Int) : DropTarget
    data class Floor(val position: Position) : DropTarget
}

fun dropTargetAt(
    position: Position,
    item: Item,
    bounds: HabitatBounds,
    anchors: Anchors,
    poopRects: List<Rect>,
): DropTarget?
```

Resolution order, returning the first match (or `null` for a rejected drop):

1. **`Food` + within bowl rect** → `Bowl`. Other items rejected here. The bowl rect is the 64×32 dp `Image` we already render for the bowl decor; in dp coordinates it's `Rect(anchors.bowl.x, anchors.bowl.y, anchors.bowl.x + 64, anchors.bowl.y + 32)`.
2. **`Scoop` + within any `poopRects[i]`** → `Poop(i)`. Other items rejected here. Each `poopRects[i]` is the 48×48 dp box around the rendered poop sprite at index `i`.
3. **`Toy` + within `bounds`** → `Floor(position)`. Other items rejected here (food/scoop on bare floor is a no-op).

A rejected drop just clears the drag state — no action, no visual feedback beyond the icon disappearing. Phase 3 doesn't need a "snap-back" animation; can be added later.

`poopRects` are derived from the existing `poopOffsets + poopCount` math in `PetScreen`. The math is moved into a small pure function so both the renderer and `dropTargetAt` agree on per-poop hit boxes.

### 3.5 Cat tap gesture

The existing `Modifier.clickable { vm.talk(); vm.pet() }` on the cat sprite is replaced by:

```kotlin
.pointerInput(Unit) {
    detectTapGestures(onLongPress = { vm.onCatHeld() })
}
```

Short taps do nothing (we removed the on-demand talk path). Long press → `vm.onCatHeld()` → `repo.pet(id)`.

## 4. Domain layer changes

### 4.1 `HabitatWorld`

```kotlin
data class HabitatWorld(
    val bowlFilled: Boolean = false,
    val toy: Position? = null,
)
```

Lives in `domain/behavior/`. Pure data; no Android deps.

### 4.2 `CatState` extension

```kotlin
enum class CatState { Idle, Walking, Lying, Eating, Playing }
```

`CatAnimations.forState` maps:
- `Eating` → `sit` (re-uses row 4, single frame).
- `Playing` → `walk` (re-uses row 0, animated; renders in place because position is fixed at the toy).

`CatAnimations.facingFor` extends naturally: Eating and Playing are non-directional (like Idle/Lying), so they coerce to SOUTH. The exhaustive-pair test in `CatAnimationsTest` continues to be the regression guard.

### 4.3 `CatBehavior` adds `stateUntil`

```kotlin
data class CatBehavior(
    val state: CatState,
    val position: Position,
    val target: Position,
    val facing: Direction,
    val nextWanderAt: Instant,
    val stateUntil: Instant? = null, // when set, tick exits the current state at >= stateUntil
)
```

Default `null` means "no fixed exit", which is the behaviour for Idle/Walking/Lying today. Eating and Playing set this to `now + 5 s` / `now + 10 s` on entry.

### 4.4 `CatBehaviorRules.tick` extensions

The signature gains a `world: HabitatWorld`:

```kotlin
fun tick(
    b: CatBehavior,
    now: Instant,
    dtSeconds: Float,
    mood: Mood,
    bounds: HabitatBounds,
    anchors: Anchors,
    world: HabitatWorld,           // new
    rng: Random,
    speedDpPerSec: Float = DEFAULT_SPEED_DP_PER_SEC,
): CatBehavior
```

Rule additions:

- **Toy preempts mood.** If `world.toy != null && b.state in {Idle, Walking}`, target = `world.toy`, state = `Walking`. Mood-anchor logic still applies if `world.toy == null`.
- **Hungry only routes to bowl when filled.** If `mood == HUNGRY && world.bowlFilled`, target = `anchors.bowl`. If hungry but bowl is empty, fall through to random/idle wander. (Today the cat walks to the bowl whenever HUNGRY; this fixes a small UX paper-cut where the cat camps on an empty bowl.)
- **Arrival at filled bowl** while hungry → `state = Eating`, `stateUntil = now + 5 s`. The transition itself is what triggers `repo.feed`; the rule function can't call the repo, so it sets the state, and the ViewModel observes and dispatches the side effect (see §5.1).
- **Arrival at toy position** → `state = Playing`, `stateUntil = now + 10 s`.
- **`stateUntil` exit.** When `b.stateUntil != null && now >= b.stateUntil`, transition `Eating` → `Idle` (and the ViewModel empties the bowl), `Playing` → `Idle` (and the ViewModel removes the toy from `world`). Reschedule wander as we already do for arrivals.
- **Lying cat ignores toy/bowl** while still SLEEPY (current behaviour). Stops being a special case once mood ≠ SLEEPY (already handled).

## 5. Wiring

### 5.1 ViewModel

`PetViewModel` grows:

```kotlin
private val _world = MutableStateFlow(HabitatWorld())
val world: StateFlow<HabitatWorld> = _world.asStateFlow()
```

`PetUiState` gains `world: HabitatWorld`. The state `combine` adds `_world` as a source.

The 60 FPS frame ticker passes `world.value` into `CatBehaviorRules.tick`. After the rules return, the ViewModel runs **side-effect dispatch**:

```kotlin
val transitioned = before.state != after.state
when {
    transitioned && after.state == CatState.Eating -> {
        _world.update { it.copy(bowlFilled = false) }   // bowl empties on entry
        scope.launch { withActive { repo.feed(it) } }
    }
    transitioned && after.state == CatState.Playing -> {
        scope.launch { withActive { repo.pet(it) } }    // happiness bump on entry
    }
    transitioned && before.state == CatState.Playing && after.state == CatState.Idle -> {
        _world.update { it.copy(toy = null) }            // toy gone when play ends
    }
}
```

The dispatch is intentionally inside the frame ticker, *after* the pure rules return — the rules stay free of side effects, and we only fire `repo.*` once per state transition.

New callbacks for the UI:

```kotlin
fun onFoodDroppedOnBowl()                       // _world.update { copy(bowlFilled = true) }
fun onScoopDroppedOnPoop(poopIndex: Int)         // withActive { repo.clean(it) } — repo decrements one poop
fun onToyDropped(position: Position)             // _world.update { copy(toy = position) }
fun onCatHeld()                                  // withActive { repo.pet(it) }
```

### 5.2 PetRepo

`feed(id)` and `pet(id)` are unchanged. `clean(id)` already decrements `poopCount` by 1 when there's a poop and bumps cleanliness otherwise (verified in `PetRepository.clean`), so the existing semantics map cleanly to "one scoop drag = one drop = one poop removed". No new repo method needed.

`talk(id)` is no longer reachable from the UI but the method stays — `PetCareWorker` and the existing repo tests don't change, and removing it would be churn for a future-feature reinstate. The button binding in `PetScreen` is the only caller that goes away.

### 5.3 PetScreen

The bottom `Row` of `Button`s is replaced by an `InventoryTray`. The `Box` rendering the cat sprite swaps `clickable` for `pointerInput { detectTapGestures(onLongPress = { vm.onCatHeld() }) }`. A new `Box` over everything renders the drag overlay when `dragController.inFlight != null`.

Bowl rendering reads `state.world.bowlFilled` and swaps between `R.drawable.bowl` and a new `R.drawable.bowl_full`. Toy rendering: when `state.world.toy != null`, an `Image` is composed at that offset.

## 6. Testing

### 6.1 Pure tests (JVM, kotlin-only)

- **`DropTargetsTest`** — every `(Item × target zone × inside/outside bounds)` combination. ~12 cases. Confirms food rejected on poops, scoop rejected on bowl, toy rejected outside floor band, etc.
- **`CatBehaviorRulesTest`** — extended:
  - Toy present + state=Idle → Walking with target=toy, regardless of mood.
  - Hungry + bowl empty → behaves like IDLE (no bowl-camping).
  - Hungry + bowl filled → walks to bowl, becomes Eating on arrival, `stateUntil = now + 5 s`.
  - Eating with `now >= stateUntil` → Idle, wander rescheduled.
  - Playing with `now >= stateUntil` → Idle.
  - Sleepy + toy present → still routes to bed (sleepy beats toy). _(Trade-off: prefer in-bed cats over toy-distracted cats; matches the SLEEPY > anything-else hierarchy already in `pickTarget`.)_
- **`HabitatWorldTest`** — trivial, just confirm defaults and copy-with semantics. (Maybe skip if data-class-only; add only if logic accretes.)

### 6.2 ViewModel tests

`PetViewModelTest` (Robolectric) gains:

- `onFoodDroppedOnBowl()` flips `world.bowlFilled` to true.
- One full frame after the cat reaches the bowl: `repo.feed` was called exactly once and `world.bowlFilled` is false again.
- `onToyDropped(p)` sets `world.toy = p`. After the cat reaches `p`, `repo.pet` was called once. After 10 s (advanced via `FakeClock`), `world.toy = null` and state is `Idle`.
- `onScoopDroppedOnPoop(i)` calls `repo.clean` exactly once.
- `onCatHeld()` calls `repo.pet` once.

These tests use the existing `externalScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)` pattern (per the CLAUDE.md note about the chatter ticker pinning `runTest` — same constraint applies to the new frame ticker).

### 6.3 No new instrumentation tests

Drag-and-drop gestures are not unit-tested at the Compose level (would require `androidTest/`). The drag layer's logic is pulled out into `dropTargetAt` and the `DragController` state machine, which **are** unit-tested. Visual drag-and-drop verification happens in manual QA on device.

## 7. Manual QA checklist

After the CI build is sideloadable:

- Bottom of the screen shows three icons (food, scoop, toy), no buttons.
- Long-press the food icon → it lifts. Drag onto the bowl → bowl shows full. Cat walks over (or was already on the way if hungry), sits at the bowl, hunger refills, bowl empties.
- Drop food anywhere except the bowl → nothing happens; food returns to tray.
- Wait for poops to spawn (or fast-forward via dev settings if available). Long-press scoop, drag onto a single poop → that poop disappears. Repeat for each poop.
- Drop scoop on something other than a poop → nothing happens.
- Drag the toy onto the floor → toy lands at the drop point, cat walks over, "plays" for ~10 s, happiness rises, toy disappears.
- Drop a second toy while cat is still playing — earlier toy is replaced; cat redirects to new toy.
- Long-press the cat → speech / petting feedback (existing diminishing returns may eat the second long-press; that's fine).
- Verify: all Phase 2 behaviour still works — random wander, bed when sleepy, breathing pause while walking, mood overlays.

## 8. Risks and known gotchas

- **Drag pickup vs scrolling.** No scrolling in this screen, so `detectDragGesturesAfterLongPress` is unambiguous. If we ever add a horizontal pan, this will need to coordinate with that gesture detector.
- **Eating animation reuse.** `sit` faces south; the cat will be at the bowl-side anchor (left of screen). Visually it's the cat sitting in front of the bowl, which is plausible. If it looks odd, swap to a profile facing west via `facingFor` override for `Eating` only. Cheap follow-up.
- **Playing animation reuse.** The walk animation looping in place reads as "running on the spot" — funnier than ideal, more like a Looney Tunes cat than a real one. A pounce sprite is a Phase-3.5 art iteration, not a blocker.
- **Dispatching `repo.feed` from inside the frame ticker.** Each call goes through `withActive { repo.feed(it) }` which already serialises through the repo's mutex. The state-transition guard (`before.state != after.state`) ensures we fire exactly once per Eating/Playing entry. Tested explicitly in §6.2.
- **`HabitatWorld.toy` race with frame ticker.** The toy is set in `_world.update {...}` from a UI callback while the ticker reads `_world.value`. `MutableStateFlow` is thread-safe; the worst case is one-frame lag before the cat redirects, which is invisible at 60 FPS.
- **Bowl-camping fix (HUNGRY without filled bowl).** This is a behavioural change to the Phase 2 contract. Today's behaviour: hungry cat walks to bowl regardless. New behaviour: hungry cat only walks to bowl when bowl is filled, otherwise wanders. Update `CatBehaviorRulesTest` to match.

## 9. Open questions for the plan

- Procedural drawable for `bowl_full` / `food` / `scoop` / `toy`: confirm `tools/generate_sprites.py` extension is the right home, or do these belong as separate drawables hand-written in PNG/SVG. Default: extend `generate_sprites.py` (consistency with `bowl`, `poop`, `room_bg`).
- Drag-overlay dimming: a constant 0.85 alpha vs a darker scrim. Default: 0.85, reconsider in QA if it reads poorly.
- Whether to keep `Pet.MAX_POOPS = 5` or raise it now that scooping is per-poop. Default: keep at 5.
