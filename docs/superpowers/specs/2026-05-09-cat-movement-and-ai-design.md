# Cat Movement & Light AI — Design Spec (Phase 2 of 3)

**Status:** Approved 2026-05-09
**Phase:** 2 of 3 in the cat-interactivity initiative (reordered: AI/movement before interactions)
**Goal:** Make the cat actually walk around the room — random idle wander as a base, with mood-driven destinations (sleepy → bed, hungry → bowl). Foundation for the Phase 3 reactive interactions (drag food into bowl, drop a toy, scoop poops).

## Context

The user's original feature ask had three parts: more interactivity, higher-fidelity graphics, direct-manipulation actions. Phase 1 shipped the graphics + a renderer ready for movement (multi-row sheets, per-frame `facing`, bounds-checked row math). This Phase 2 was originally going to be the drag-and-drop interaction layer, but the brainstorming surfaced that interactions are awkward without movement (food in bowl, but cat doesn't go to it). Reordering: do movement + AI first, then build interactions on top.

Brainstorming decisions (2026-05-09):
- **Reordered**: Movement/AI is the new Phase 2; drag-and-drop interactions become Phase 3.
- **Asset**: swap to a walk-capable sheet — LPC cat (Liberated Pixel Cup), CC-BY-SA-3.0 / OGA-BY-3.0. Adds `ATTRIBUTION.md`.
- **Behavior**: random wander base + mood-driven overrides (sleepy → bed, hungry → bowl).
- **State machine**: pure-Kotlin `CatBehavior` separate from `PetViewModel`, owned by the ViewModel, advanced on a Compose frame ticker.
- **Motion model**: 2D habitat-relative dp with straight-line movement at a configurable speed.
- **Phase 3 outline** (for context, not implemented here): drag-and-drop bottom inventory tray (food bag, scoop, toy) + tap-and-hold cat for petting. The current Feed/Clean/Pet/Talk button row goes away when Phase 3 lands.

## 1. Scope

### In scope
- Source and bundle a CC-BY-SA-3.0 LPC cat sprite sheet via `tools/fetch_cat_sprites.py` (extended candidate list, pinned SHA, hard-fail on mismatch). The fetcher repacks into a clean grid documented in §3.
- New `domain/behavior/CatBehavior.kt` package: pure-Kotlin state machine (`Idle`, `Walking`, `Lying`), 2D habitat-dp position, direction tracking, target selection.
- Refactor `CatAnimations` to expose `forState(behaviorState)` returning `sit`/`walk`/`lay` — `walk` is a 4-direction looping animation; `facing` parameter drives the row.
- `PetViewModel` owns the `MutableStateFlow<CatBehavior>` and ticks it on a Compose frame cadence. AI tick-rate decisions (next wander time, target switching) live inside the pure `tick(...)` and require no separate scheduler.
- `PetScreen` reads `behavior.position` and offsets the sprite via `Modifier.offset`. Picks the right `SpriteAnimation` from `behavior.state` and passes `facing = behavior.facing`.
- `PetScreen` measures the floor area on layout and reports `HabitatBounds` + `Anchors` (bed + bowl positions) back to the ViewModel.
- `ATTRIBUTION.md` at repo root for the new CC-BY-SA-3.0 asset.

### Explicitly NOT in this spec
- Drag-and-drop tray, scoop interaction, food-bowl filling, toy item — Phase 3.
- Cat reacting to user actions other than mood changes (e.g. cat walking AWAY from poop). Phase 3.
- Pathfinding around obstacles. Floor is treated as a single obstacle-free rectangle.
- Multi-cat interactions. Still single active pet.
- Pet stats, decay rates, mood priority, repository, notifications, work scheduling — all unchanged.
- The Feed/Clean/Pet/Talk button row, the speech bubble, the pet selector, the adopt flow — all unchanged.

### Acceptance criteria
- A pet adopted and left alone visibly walks to random spots and sits, every ~30–60 seconds.
- Letting hunger drop below 30 makes the cat walk to the bowl spot.
- Letting energy drop below 30 OR entering the device-local 22:00–07:00 window makes the cat walk to the bed spot and lie down.
- Cat sprite faces the direction of motion (`facing` row offset 0/1/2/3 for SOUTH/NORTH/WEST/EAST).
- All existing tests pass; new behavior tests pass; `./gradlew ktlintCheck :app:lintDebug` clean.
- Manual QA checklist (§7) green on a real device.

## 2. Asset choice and repack

LPC cat sprites have well-known layouts. The fetch script gains a new candidate slot for an LPC sheet and a candidate-specific repack function alongside the existing `repack_surt`. The repacked output is a clean 64×64 cell grid, 6 rows × 4 cols, structured for the renderer:

| Row | Animation | Cols used | Notes |
|-----|-----------|-----------|-------|
| 0 | walk-S | 4 | facing=SOUTH (renderer adds offset 0) |
| 1 | walk-N | 4 | facing=NORTH (offset 1) |
| 2 | walk-W | 4 | facing=WEST (offset 2) |
| 3 | walk-E | 4 | facing=EAST (offset 3) |
| 4 | sit | 1 (col 0) | facing-agnostic |
| 5 | lay | 1 (col 0) | facing-agnostic |

`SpriteSheet(R.drawable.cat, frameWidth=64, frameHeight=64, rows=6, cols=4)`. The Phase 1 `requireFacingFits` invariant catches programmer error.

Procurement:
- Add an LPC cat candidate to `CANDIDATES` in `tools/fetch_cat_sprites.py` with the OpenGameArt URL and pinned SHA256.
- Implement `repack_lpc(raw: Image.Image) -> Image.Image` that crops the LPC sheet's known regions and composes the 6×4 grid above. The repack is deterministic.
- Pinned SHA hard-fails on mismatch (Phase 1 review fix; same policy).
- `ATTRIBUTION.md` gets the entry: title, author(s), URL, CC-BY-SA-3.0/OGA-BY-3.0.

If LPC's known URL has rotted or the sheet's actual layout differs, the script falls back to other CC-BY-SA candidates. The implementation plan documents the inspection step for confirming the chosen sheet's row/frame counts before pinning the repack.

## 3. CatBehavior state machine

Pure Kotlin, no Android deps. Lives at `app/src/main/kotlin/com/pocketpets/app/domain/behavior/`.

```kotlin
package com.pocketpets.app.domain.behavior

import com.pocketpets.app.domain.Mood
import com.pocketpets.app.ui.sprite.Direction
import kotlinx.datetime.Instant
import kotlin.random.Random

enum class CatState { Idle, Walking, Lying }

data class Position(val x: Float, val y: Float)

data class HabitatBounds(val minX: Float, val minY: Float, val maxX: Float, val maxY: Float) {
    init {
        require(minX < maxX && minY < maxY) { "empty bounds" }
    }
    fun clamp(p: Position) = Position(p.x.coerceIn(minX, maxX), p.y.coerceIn(minY, maxY))
}

data class Anchors(val bed: Position, val bowl: Position)

data class CatBehavior(
    val state: CatState,
    val position: Position,
    val target: Position,
    val facing: Direction,
    val nextWanderAt: Instant,
)

object CatBehaviorRules {
    const val DEFAULT_SPEED_DP_PER_SEC = 60f
    const val ARRIVAL_EPSILON_DP = 2f
    const val MIN_WANDER_SECONDS = 30L
    const val MAX_WANDER_SECONDS = 60L

    /** Pure forward step. */
    fun tick(
        b: CatBehavior,
        now: Instant,
        dtSeconds: Float,
        mood: Mood,
        bounds: HabitatBounds,
        anchors: Anchors,
        rng: Random,
        speedDpPerSec: Float = DEFAULT_SPEED_DP_PER_SEC,
    ): CatBehavior

    /** Direction enum picked from movement vector; ties resolved deterministically (vertical wins on tie). */
    fun directionOf(from: Position, to: Position): Direction

    /** Mood-driven destination if any, else random within bounds. */
    fun pickTarget(mood: Mood, bounds: HabitatBounds, anchors: Anchors, rng: Random): Position

    /** Schedules the next random wander after the current one resolves. */
    fun nextWanderInstant(now: Instant, rng: Random): Instant
}
```

### Transition table

| From state | Trigger | New state | Notes |
|------------|---------|-----------|-------|
| `Idle` | mood becomes SLEEPY | `Walking` | target = `anchors.bed` |
| `Idle` | mood becomes HUNGRY | `Walking` | target = `anchors.bowl` |
| `Idle` | `now >= nextWanderAt` | `Walking` | target = random position; new `nextWanderAt` scheduled when arriving |
| `Idle` | else | `Idle` | no-op |
| `Walking` | within `ARRIVAL_EPSILON_DP` of target AND target == `anchors.bed` | `Lying` | facing preserved |
| `Walking` | within `ARRIVAL_EPSILON_DP` of target AND target != `anchors.bed` | `Idle` | schedule next wander |
| `Walking` | mood demands a new target | `Walking` | retarget mid-walk; facing recomputed |
| `Walking` | else | `Walking` | advance position by `speed × dtSeconds` toward target |
| `Lying` | mood no longer SLEEPY | `Walking` | target = current `pickTarget(mood,...)` |
| `Lying` | else | `Lying` | no-op |

`tick` is the only mutator. Position updates are linear; facing is recomputed from the (current → target) vector whenever movement happens, preserved otherwise.

### Mood-priority for target selection

Order matches the existing `Mood` priority but only the first two affect movement:

1. SLEEPY → bed
2. HUNGRY → bowl
3. else → random within `bounds`

GROSSED_OUT, SAD, IDLE, HAPPY don't change destination — they're expressed entirely via the existing `MoodOverlay` particles on top of the sit pose.

## 4. CatAnimations refactor

`forMood(stage, mood)` is replaced by `forState(state)`:

```kotlin
object CatAnimations {
    private val sheet = SpriteSheet(
        resId = R.drawable.cat,
        frameWidth = 64, frameHeight = 64,
        rows = 6, cols = 4,
    )
    val walk: SpriteAnimation = SpriteAnimation(sheet, row = 0, frameCount = 4, frameMs = 120, loop = true)
    val sit:  SpriteAnimation = SpriteAnimation(sheet, row = 4, frameCount = 1)
    val lay:  SpriteAnimation = SpriteAnimation(sheet, row = 5, frameCount = 1)

    fun forState(state: CatState): SpriteAnimation = when (state) {
        CatState.Walking -> walk
        CatState.Idle -> sit
        CatState.Lying -> lay
    }
}
```

The `walk` animation uses `row = 0`; the renderer adds `0/1/2/3` for the four facings. So `AnimatedSprite(animation = walk, facing = behavior.facing)` becomes the directional walk for free.

`MoodOverlay` is unchanged — it still keys off `Mood`, layered above whatever the cat is doing.

## 5. ViewModel + Screen wiring

### `PetViewModel`

- Adds `MutableStateFlow<CatBehavior>` to `PetUiState`.
- Initial value is `Idle` at the screen-relative centre, facing SOUTH, `nextWanderAt = clock.now() + jitter()`.
- A new `LaunchedEffect`-equivalent inside the ViewModel scope runs a frame ticker:

  ```kotlin
  scope.launch {
      var lastFrame = clock.now()
      while (isActive) {
          val now = clock.now()
          val dt = ((now.toEpochMilliseconds() - lastFrame.toEpochMilliseconds()) / 1000f)
              .coerceAtMost(0.1f)  // clamp huge gaps after pause
          lastFrame = now
          behaviorFlow.update { b ->
              CatBehaviorRules.tick(
                  b = b,
                  now = now,
                  dtSeconds = dt,
                  mood = currentMood,
                  bounds = currentBounds,
                  anchors = currentAnchors,
                  rng = rng,
              )
          }
          delay(16)  // ~60 FPS
      }
  }
  ```

  The loop only runs while the state flow is collected (the existing `WhileSubscribed(5_000)` policy applies).
- New `setHabitat(bounds: HabitatBounds, anchors: Anchors)` method called from `PetScreen` after layout measures. Stored as ViewModel-internal state for the ticker to read.

### `PetScreen`

- After the floor `Image` is laid out, `Modifier.onSizeChanged` reports the floor box dp dimensions to the ViewModel; the ViewModel converts to `HabitatBounds` and synthesises `Anchors` (bowl at the bowl Image's centre; bed at a fixed offset, e.g. 70% across, 60% down).
- The cat sprite Box uses `Modifier.offset { IntOffset(behavior.position.x.dp.roundToPx(), behavior.position.y.dp.roundToPx()) }`.
- The Phase 1 breathing transform is gated: applied only when `behavior.state != Walking` (a walking cat has motion already; double-puffing reads as wobble).
- The right `SpriteAnimation` is picked from `CatAnimations.forState(behavior.state)` and `AnimatedSprite(animation = ..., facing = behavior.facing)`.
- The clickable area (existing tap → `vm.talk(); vm.pet()`) moves with the cat — wraps the offsetted Box so the tap target tracks the sprite, not a fixed centre.

The Feed/Clean/Pet/Talk button row, speech bubble, stat chips, top bar, bowl decor, poop sprites — all unchanged.

## 6. Testing

### Pure unit tests

- **`CatBehaviorRulesTest`** (covers the bulk of the design):
  - `directionOf`: 4 cardinals + diagonal tiebreak (vertical wins).
  - `pickTarget` priority: SLEEPY → bed; HUNGRY → bowl; otherwise random within bounds (seeded `Random` for determinism).
  - `nextWanderInstant`: returned instant lies in `[now + 30s, now + 60s]`.
  - `tick`:
    - position advances by `speed × dt` toward target;
    - stops within `ARRIVAL_EPSILON_DP` of target;
    - state becomes `Lying` when arriving at bed, `Idle` otherwise;
    - schedules new `nextWanderAt` after arrival;
    - mood flip mid-walk preempts the current target when the new mood selects a different anchor;
    - `dt = 0` is a no-op;
    - `dt = 100` (huge gap, simulating after-pause) doesn't teleport past the target.
- **`HabitatBoundsTest`**: `clamp` correctness on the corners and outside; `init` rejects empty bounds.
- **`CatAnimationsTest`** (replacing today's `forMood` test): every `CatState` returns a non-null animation with valid invariants. `walk.frameMs` in `[50, 250]`.

### ViewModel test

- **`PetViewModelTest`** (extends existing): inject a fake `Clock`. After enough simulated `tick`s with mood SLEEPY, assert `behavior.state == Lying` and `behavior.position` is within epsilon of `anchors.bed`. Doesn't drive the actual frame loop — directly invokes `tick` to validate the integration.

### Not tested

- The Compose-level frame ticker driving (`delay(16)` loop) — visual.
- `Modifier.offset` rendering — visual.
- Sprite-sheet frame swapping — covered by Phase 1 tests.
- The asset-fetch script repack — covered structurally by the byte-deterministic re-run check (existing pattern).

## 7. Manual QA

The unchecked-box on the PR:

1. Adopt a pet → cat appears at default position, sits.
2. Wait ~30–60s → cat walks to a random spot on the floor, sits.
3. Spam `Feed` to fill hunger, then `Clean`, then leave it idle until hunger drops below 30 → cat walks to the bowl.
4. Wait until the device-local 22:00–07:00 sleep window OR debug-speedup energy below 30 → cat walks to bed and lies down.
5. Tap `Talk` → speech bubble appears above the cat at its current position.
6. Switch active pet via the selector → the second pet's behavior flow starts at default position; switching back to the first pet preserves its state during the same app session.
7. Background the app for 30s → on resume, the cat is still where it was (no teleport) and resumes walking to its target.
8. Sprite faces the direction of motion as the cat walks N/S/E/W.

## 8. Risks and mitigations

- **LPC asset URL or layout drift.** Same mitigation as Phase 1: pinned SHA hard-fails; the candidate list is ordered with fallbacks. The implementation plan includes an inspection step after fetch to confirm the cell layout before locking the repack.
- **CC-BY-SA-3.0 attribution requirements.** Adding `ATTRIBUTION.md` at repo root with the author/URL/license entry. Code is unaffected (license applies to the asset only).
- **60 FPS Compose ticker on a single sprite.** Negligible cost; the actual UI-state changes are throttled to position updates that are diff'd by `MutableStateFlow`. Visible repaints are bounded by what the device's surface flinger handles.
- **Position state lost on process death.** Acceptable — cat resets to default position on cold start. Any in-progress walk doesn't matter. (Persistence is a future enhancement, out of scope.)
- **Phase 3 hookup cost.** The `tick` function takes `mood` today; Phase 3 will extend its inputs to include world events (food just placed in bowl, etc.) and add a `Reacting` state. The design space is preserved by keeping `tick` pure — no scheduler entanglement.
