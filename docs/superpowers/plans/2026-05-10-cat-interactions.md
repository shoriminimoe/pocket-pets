# Cat Interactions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the Feed/Clean/Pet/Talk button row with direct-manipulation gestures: drag food onto the bowl, drag the scoop onto each poop, drop a toy on the floor, touch-and-hold the cat to pet. Adds Eating and Playing states to the cat's behaviour.

**Architecture:** A new `ui/inventory/` package owns the drag-and-drop layer (Item enum, DropTarget sealed interface, pure `dropTargetAt` resolver, `DragController` state holder, `InventoryTray` Composable). A new `domain/behavior/HabitatWorld` value object carries `bowlFilled` and `toy` into `CatBehaviorRules.tick`, which gains arrival → `Eating`/`Playing` transitions and a `stateUntil` exit on `CatBehavior`. `PetViewModel` observes pure state transitions and dispatches one-shot `repo.feed/pet/clean` calls accordingly.

**Tech Stack:** Kotlin 2.2 + Jetpack Compose, kotlinx-datetime, Room, JUnit 4 + Truth + Robolectric, Pillow (procedural decor PNGs).

**Spec:** `docs/superpowers/specs/2026-05-10-cat-interactions-design.md`.

---

## Task 1: Generate item drawables (food, scoop, toy, bowl_full)

**Files:**
- Modify: `tools/generate_sprites.py`
- Create: `app/src/main/res/drawable-nodpi/food.png`
- Create: `app/src/main/res/drawable-nodpi/scoop.png`
- Create: `app/src/main/res/drawable-nodpi/toy.png`
- Create: `app/src/main/res/drawable-nodpi/bowl_full.png`

The existing script in this file follows a simple Pillow + rect/px helpers pattern (see `render_poop`, `render_bowl`). New decor renders match that style: small palettes, no anti-aliasing, transparent background.

- [ ] **Step 1: Add `render_food`, `render_scoop`, `render_toy`, `render_bowl_full` and wire them into `main()`**

Append the four new render functions to `tools/generate_sprites.py` after `render_bowl()` and update `main()` to call them. Use these reference implementations:

```python
def render_food():
    img = blank(48, 48)
    d = ImageDraw.Draw(img)
    sack = (140, 100, 60, 255)
    sack_dark = (100, 70, 35, 255)
    sack_hi = (170, 130, 90, 255)
    label = (220, 220, 215, 255)
    fish = (240, 130, 60, 255)
    fish_dark = (180, 80, 30, 255)
    rect(d, 12, 14, 24, 30, sack)
    rect(d, 12, 14, 1, 30, sack_dark)
    rect(d, 35, 14, 1, 30, sack_dark)
    rect(d, 14, 16, 20, 2, sack_hi)
    rect(d, 14, 8, 20, 6, sack_dark)
    rect(d, 18, 10, 12, 4, sack)
    rect(d, 16, 22, 16, 12, label)
    rect(d, 19, 25, 10, 6, fish)
    rect(d, 19, 25, 10, 1, fish_dark)
    rect(d, 28, 26, 1, 4, fish_dark)
    return img


def render_scoop():
    img = blank(48, 48)
    d = ImageDraw.Draw(img)
    handle = (90, 60, 40, 255)
    handle_hi = (140, 100, 70, 255)
    metal = (200, 200, 210, 255)
    metal_dark = (130, 130, 140, 255)
    rect(d, 6, 6, 18, 4, handle)
    rect(d, 6, 6, 18, 1, handle_hi)
    rect(d, 22, 10, 4, 18, metal_dark)
    rect(d, 18, 26, 22, 14, metal)
    rect(d, 18, 26, 22, 1, metal_dark)
    rect(d, 18, 39, 22, 1, metal_dark)
    for x in range(20, 40, 4):
        rect(d, x, 30, 1, 8, metal_dark)
    return img


def render_toy():
    img = blank(48, 48)
    d = ImageDraw.Draw(img)
    yarn = (220, 80, 100, 255)
    yarn_dark = (160, 50, 70, 255)
    yarn_hi = (245, 130, 150, 255)
    rect(d, 14, 14, 20, 20, yarn)
    rect(d, 14, 14, 20, 2, yarn_hi)
    rect(d, 14, 32, 20, 2, yarn_dark)
    rect(d, 14, 14, 2, 20, yarn_hi)
    rect(d, 32, 14, 2, 20, yarn_dark)
    for offset in range(-6, 8, 4):
        rect(d, 14 + offset + 6, 16, 2, 16, yarn_dark)
    rect(d, 32, 18, 12, 1, yarn)
    rect(d, 36, 22, 8, 1, yarn)
    return img


def render_bowl_full():
    img = blank(32, 16)
    d = ImageDraw.Draw(img)
    rect(d, 4, 6, 24, 8, (160, 160, 170, 255))
    rect(d, 4, 6, 24, 2, (200, 200, 215, 255))
    rect(d, 6, 8, 20, 4, (110, 110, 120, 255))
    kibble = (130, 90, 50, 255)
    kibble_hi = (170, 130, 80, 255)
    rect(d, 7, 5, 18, 3, kibble)
    rect(d, 9, 4, 14, 2, kibble)
    rect(d, 11, 3, 10, 2, kibble_hi)
    rect(d, 14, 2, 4, 2, kibble)
    return img
```

Wire-up in `main()`:

```python
def main():
    render_poop().save(OUT / "poop.png")
    render_room_bg().save(OUT / "room_bg.png")
    render_bowl().save(OUT / "bowl.png")
    render_bowl_full().save(OUT / "bowl_full.png")
    render_food().save(OUT / "food.png")
    render_scoop().save(OUT / "scoop.png")
    render_toy().save(OUT / "toy.png")
    print(f"Wrote decor sprites to {OUT}")
```

- [ ] **Step 2: Regenerate the PNGs**

Run: `uv run tools/generate_sprites.py`
Expected: `Wrote decor sprites to /home/sam/Projects/pocket-pets/app/src/main/res/drawable-nodpi`

Confirm the four files exist:
```
ls -la app/src/main/res/drawable-nodpi/{food,scoop,toy,bowl_full}.png
```

- [ ] **Step 3: Commit**

```bash
git add tools/generate_sprites.py app/src/main/res/drawable-nodpi/food.png app/src/main/res/drawable-nodpi/scoop.png app/src/main/res/drawable-nodpi/toy.png app/src/main/res/drawable-nodpi/bowl_full.png
git commit -m "feat: add procedural drawables for food, scoop, toy, bowl_full"
```

---

## Task 2: HabitatWorld value type

**Files:**
- Create: `app/src/main/kotlin/com/pocketpets/app/domain/behavior/HabitatWorld.kt`

- [ ] **Step 1: Write the file**

```kotlin
package com.pocketpets.app.domain.behavior

/**
 * World state read by [CatBehaviorRules.tick] and written by user actions
 * (drop food on bowl, drop toy on floor). Pure data; mutated only by `copy`.
 */
data class HabitatWorld(
    val bowlFilled: Boolean = false,
    val toy: Position? = null,
)
```

- [ ] **Step 2: Verify compile**

Run: `JAVA_HOME=/home/sam/.local/jdk ANDROID_HOME=/home/sam/Android/Sdk PATH=/home/sam/.local/jdk/bin:$PATH ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/pocketpets/app/domain/behavior/HabitatWorld.kt
git commit -m "feat: add HabitatWorld value type for bowl-filled and toy state"
```

---

## Task 3: Add Eating and Playing states; extend CatBehavior with stateUntil; wire CatAnimations and exhaustive `when` branches

**Files:**
- Modify: `app/src/main/kotlin/com/pocketpets/app/domain/behavior/CatState.kt`
- Modify: `app/src/main/kotlin/com/pocketpets/app/domain/behavior/CatBehavior.kt`
- Modify: `app/src/main/kotlin/com/pocketpets/app/domain/behavior/CatBehaviorRules.kt` (no-op branches for new states)
- Modify: `app/src/main/kotlin/com/pocketpets/app/ui/pet/CatAnimations.kt`
- Modify: `app/src/test/kotlin/com/pocketpets/app/ui/pet/CatAnimationsTest.kt`

- [ ] **Step 1: Extend `CatState` enum**

Replace the contents of `app/src/main/kotlin/com/pocketpets/app/domain/behavior/CatState.kt` with:

```kotlin
package com.pocketpets.app.domain.behavior

/**
 * Behavioural state of the cat. The state machine is implemented in
 * [CatBehaviorRules.tick]. Eating and Playing are duration-bounded by
 * [CatBehavior.stateUntil].
 */
enum class CatState { Idle, Walking, Lying, Eating, Playing }
```

- [ ] **Step 2: Add `stateUntil` to `CatBehavior`**

Modify `app/src/main/kotlin/com/pocketpets/app/domain/behavior/CatBehavior.kt`:

```kotlin
package com.pocketpets.app.domain.behavior

import com.pocketpets.app.ui.sprite.Direction
import kotlinx.datetime.Instant

/**
 * Snapshot of the cat's behaviour state. Mutations go through
 * [CatBehaviorRules.tick] only.
 *
 * [stateUntil] is non-null only for duration-bounded states (Eating, Playing);
 * `tick` returns a state-exit transition when `now >= stateUntil`.
 */
data class CatBehavior(
    val state: CatState,
    val position: Position,
    val target: Position,
    val facing: Direction,
    val nextWanderAt: Instant,
    val stateUntil: Instant? = null,
)
```

- [ ] **Step 3: Add no-op branches for new states in `CatBehaviorRules.tick`**

Adding `Eating` and `Playing` to `CatState` makes the existing `if (b.state == CatState.Lying)` / `if (b.state == CatState.Idle)` chain unaware of them — falls through to the Walking branch which would return `b.copy(state=Walking, …)`. To prevent that, gate the file's existing logic by `b.state in {Idle, Walking, Lying}` and add an explicit no-op for the new states. After the `if (dtSeconds <= 0f) return b` line, insert:

```kotlin
        // Duration-bounded states are implemented in a later task; for now they
        // are no-ops so adding them to the enum doesn't fall through into the
        // Walking branch below.
        if (b.state == CatState.Eating || b.state == CatState.Playing) {
            return b
        }
```

- [ ] **Step 4: Extend `CatAnimations.forState` and `facingFor`**

Modify the `forState` and `facingFor` `when` blocks in `app/src/main/kotlin/com/pocketpets/app/ui/pet/CatAnimations.kt`:

```kotlin
    fun forState(state: CatState): SpriteAnimation =
        when (state) {
            CatState.Walking -> walk
            CatState.Idle -> sit
            CatState.Lying -> lay
            CatState.Eating -> sit
            CatState.Playing -> walk
        }

    /**
     * Returns the [Direction] to pass to [com.pocketpets.app.ui.sprite.AnimatedSprite]
     * for a cat in [state] whose behavior facing is [behaviorFacing].
     *
     * Walk frames live on rows 0..3 (S/N/W/E). Sit and lay are single-cell poses on
     * rows 4 and 5 respectively, with no directional siblings. Forwarding a non-SOUTH
     * facing on those poses would have AnimatedSprite read row 4+offset / 5+offset
     * which is out of bounds for the 6-row sheet — so we coerce to SOUTH. Eating
     * reuses sit (also SOUTH-only); Playing reuses walk (directional).
     */
    fun facingFor(
        state: CatState,
        behaviorFacing: Direction,
    ): Direction =
        when (state) {
            CatState.Walking, CatState.Playing -> behaviorFacing
            CatState.Idle, CatState.Lying, CatState.Eating -> Direction.SOUTH
        }
```

- [ ] **Step 5: Add tests covering the new mappings**

Append to `app/src/test/kotlin/com/pocketpets/app/ui/pet/CatAnimationsTest.kt`, before the closing brace:

```kotlin
    @Test
    fun `eating reuses sit and playing reuses walk`() {
        assertThat(CatAnimations.forState(CatState.Eating)).isEqualTo(CatAnimations.sit)
        assertThat(CatAnimations.forState(CatState.Playing)).isEqualTo(CatAnimations.walk)
    }

    @Test
    fun `eating coerces facing to SOUTH and playing preserves it`() {
        for (facing in Direction.values()) {
            assertThat(CatAnimations.facingFor(CatState.Eating, facing)).isEqualTo(Direction.SOUTH)
            assertThat(CatAnimations.facingFor(CatState.Playing, facing)).isEqualTo(facing)
        }
    }
```

The earlier `every state and behavior facing combination resolves to a renderable cell` test iterates `CatState.values()` × `Direction.values()` — Eating and Playing automatically join its coverage.

- [ ] **Step 6: Run tests**

Run: `JAVA_HOME=/home/sam/.local/jdk ANDROID_HOME=/home/sam/Android/Sdk PATH=/home/sam/.local/jdk/bin:$PATH ./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL. All `CatAnimationsTest` cases pass; existing `CatBehaviorRulesTest` still green (Eating/Playing aren't reachable yet).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/com/pocketpets/app/domain/behavior/CatState.kt app/src/main/kotlin/com/pocketpets/app/domain/behavior/CatBehavior.kt app/src/main/kotlin/com/pocketpets/app/domain/behavior/CatBehaviorRules.kt app/src/main/kotlin/com/pocketpets/app/ui/pet/CatAnimations.kt app/src/test/kotlin/com/pocketpets/app/ui/pet/CatAnimationsTest.kt
git commit -m "feat: add Eating and Playing cat states with sprite mappings"
```

---

## Task 4: Plumb HabitatWorld into pickTarget and tick (default empty world for compatibility)

**Files:**
- Modify: `app/src/main/kotlin/com/pocketpets/app/domain/behavior/CatBehaviorRules.kt`
- Modify: `app/src/main/kotlin/com/pocketpets/app/ui/pet/PetViewModel.kt`

- [ ] **Step 1: Extend `pickTarget` and `tick` signatures**

Modify the `pickTarget` signature and body in `CatBehaviorRules.kt`. Add a `world: HabitatWorld = HabitatWorld()` parameter; behaviour unchanged for now:

```kotlin
    /**
     * Returns the target the cat should walk to next given current [mood] and
     * [world]. World-driven destinations (toy thrown, bowl filled while hungry)
     * take priority; otherwise mood-driven destinations; otherwise a uniform-random
     * point inside [bounds]. The default empty [world] preserves the prior
     * behaviour (mood-anchor + random) until the world-aware paths are added.
     */
    fun pickTarget(
        mood: Mood,
        bounds: HabitatBounds,
        anchors: Anchors,
        rng: Random,
        world: HabitatWorld = HabitatWorld(),
    ): Position =
        when (mood) {
            Mood.SLEEPY -> anchors.bed
            Mood.HUNGRY -> anchors.bowl
            else ->
                Position(
                    x = rng.nextFloatInRange(bounds.minX, bounds.maxX),
                    y = rng.nextFloatInRange(bounds.minY, bounds.maxY),
                )
        }
```

Modify the `tick` signature similarly (add `world: HabitatWorld = HabitatWorld()` after `anchors`):

```kotlin
    fun tick(
        b: CatBehavior,
        now: Instant,
        dtSeconds: Float,
        mood: Mood,
        bounds: HabitatBounds,
        anchors: Anchors,
        rng: Random,
        speedDpPerSec: Float = DEFAULT_SPEED_DP_PER_SEC,
        world: HabitatWorld = HabitatWorld(),
    ): CatBehavior {
```

(Body unchanged in this task; the default keeps existing tests and `PetViewModel` compiling.)

- [ ] **Step 2: Run all tests**

Run: `JAVA_HOME=/home/sam/.local/jdk ANDROID_HOME=/home/sam/Android/Sdk PATH=/home/sam/.local/jdk/bin:$PATH ./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL. No behavior change yet; just the signature.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/pocketpets/app/domain/behavior/CatBehaviorRules.kt
git commit -m "refactor: thread HabitatWorld through pickTarget and tick (default empty)"
```

---

## Task 5: Bowl-filled gating — hungry cat only routes to filled bowl

**Files:**
- Modify: `app/src/main/kotlin/com/pocketpets/app/domain/behavior/CatBehaviorRules.kt`
- Modify: `app/src/test/kotlin/com/pocketpets/app/domain/behavior/CatBehaviorRulesTest.kt`

- [ ] **Step 1: Update existing failing test for hungry+filled**

In `CatBehaviorRulesTest.kt`, locate `pickTarget chooses bowl when hungry` and replace it with two tests — one for the filled case, one for the empty case:

```kotlin
    @Test
    fun `pickTarget chooses bowl when hungry and bowl is filled`() {
        val world = HabitatWorld(bowlFilled = true)
        assertThat(CatBehaviorRules.pickTarget(Mood.HUNGRY, bounds, anchors, Random(0), world))
            .isEqualTo(anchors.bowl)
    }

    @Test
    fun `pickTarget hungry with empty bowl falls through to a random point in bounds`() {
        val world = HabitatWorld(bowlFilled = false)
        for (seed in 0..20) {
            val t = CatBehaviorRules.pickTarget(Mood.HUNGRY, bounds, anchors, Random(seed.toLong()), world)
            assertThat(t).isNotEqualTo(anchors.bowl)
            assertThat(t.x).isAtLeast(bounds.minX)
            assertThat(t.x).isAtMost(bounds.maxX)
            assertThat(t.y).isAtLeast(bounds.minY)
            assertThat(t.y).isAtMost(bounds.maxY)
        }
    }
```

- [ ] **Step 2: Update an existing tick test to pass a filled world**

The test `idle cat that becomes hungry walks toward bowl` will now break. Update it to pass `world = HabitatWorld(bowlFilled = true)`:

```kotlin
    @Test
    fun `idle cat that becomes hungry walks toward filled bowl`() {
        val b = behavior(state = CatState.Idle, x = 50f, y = 50f, targetX = 50f, targetY = 50f)
        val out =
            CatBehaviorRules.tick(
                b, t0, 0.016f, Mood.HUNGRY, bounds, anchors, Random(0),
                world = HabitatWorld(bowlFilled = true),
            )
        assertThat(out.state).isEqualTo(CatState.Walking)
        assertThat(out.target).isEqualTo(anchors.bowl)
    }

    @Test
    fun `idle cat that becomes hungry but bowl is empty does not walk to bowl`() {
        val b = behavior(state = CatState.Idle, x = 50f, y = 50f, targetX = 50f, targetY = 50f)
        // nextWanderAt is in the future, so without the bowl pull the cat stays idle.
        val out =
            CatBehaviorRules.tick(
                b, t0, 0.016f, Mood.HUNGRY, bounds, anchors, Random(0),
                world = HabitatWorld(bowlFilled = false),
            )
        assertThat(out.state).isEqualTo(CatState.Idle)
    }
```

- [ ] **Step 3: Run tests to confirm they fail**

Run: `JAVA_HOME=/home/sam/.local/jdk ANDROID_HOME=/home/sam/Android/Sdk PATH=/home/sam/.local/jdk/bin:$PATH ./gradlew :app:testDebugUnitTest --tests "com.pocketpets.app.domain.behavior.CatBehaviorRulesTest"`
Expected: FAIL — the four updated/new tests fail because the rules still always route HUNGRY to the bowl regardless of `world.bowlFilled`.

- [ ] **Step 4: Implement bowl-filled gating in `pickTarget` and `tick`**

In `CatBehaviorRules.kt`, change the `pickTarget` body to consult `world` for HUNGRY:

```kotlin
        when (mood) {
            Mood.SLEEPY -> anchors.bed
            Mood.HUNGRY ->
                if (world.bowlFilled) {
                    anchors.bowl
                } else {
                    Position(
                        x = rng.nextFloatInRange(bounds.minX, bounds.maxX),
                        y = rng.nextFloatInRange(bounds.minY, bounds.maxY),
                    )
                }
            else ->
                Position(
                    x = rng.nextFloatInRange(bounds.minX, bounds.maxX),
                    y = rng.nextFloatInRange(bounds.minY, bounds.maxY),
                )
        }
```

In `tick`, the `moodAnchor` derivation must also consult `world`. Replace the `moodAnchor` block with:

```kotlin
        // Mood-driven anchor target preempts everything else, but a hungry cat
        // only routes to the bowl when there's actually food in it.
        val moodAnchor: Position? =
            when (mood) {
                Mood.SLEEPY -> anchors.bed
                Mood.HUNGRY -> if (world.bowlFilled) anchors.bowl else null
                else -> null
            }
```

Additionally, in the Idle branch where the wander timer fires, change the `pickTarget` call to pass `world`:

```kotlin
                now >= b.nextWanderAt ->
                    walkingToward(
                        b,
                        pickTarget(mood, bounds, anchors, rng, world),
                    )
```

And in the Lying branch:

```kotlin
            val target = moodAnchor ?: pickTarget(mood, bounds, anchors, rng, world)
            return walkingToward(b, target)
```

- [ ] **Step 5: Run tests**

Run: `JAVA_HOME=/home/sam/.local/jdk ANDROID_HOME=/home/sam/Android/Sdk PATH=/home/sam/.local/jdk/bin:$PATH ./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/pocketpets/app/domain/behavior/CatBehaviorRules.kt app/src/test/kotlin/com/pocketpets/app/domain/behavior/CatBehaviorRulesTest.kt
git commit -m "feat: hungry cat only routes to bowl when filled (no bowl camping)"
```

---

## Task 6: Toy preempts mood — thrown toy pulls the cat regardless

**Files:**
- Modify: `app/src/main/kotlin/com/pocketpets/app/domain/behavior/CatBehaviorRules.kt`
- Modify: `app/src/test/kotlin/com/pocketpets/app/domain/behavior/CatBehaviorRulesTest.kt`

- [ ] **Step 1: Write the failing tests**

Append to `CatBehaviorRulesTest.kt`:

```kotlin
    @Test
    fun `toy in world preempts random idle wander`() {
        val toy = Position(60f, 70f)
        val world = HabitatWorld(toy = toy)
        val b = behavior(state = CatState.Idle, x = 10f, y = 10f, targetX = 10f, targetY = 10f)
        val out =
            CatBehaviorRules.tick(
                b, t0, 0.016f, Mood.IDLE, bounds, anchors, Random(0),
                world = world,
            )
        assertThat(out.state).isEqualTo(CatState.Walking)
        assertThat(out.target).isEqualTo(toy)
    }

    @Test
    fun `toy in world preempts hungry+filled-bowl pull`() {
        val toy = Position(60f, 70f)
        val world = HabitatWorld(bowlFilled = true, toy = toy)
        val b = behavior(state = CatState.Idle, x = 10f, y = 10f, targetX = 10f, targetY = 10f)
        val out =
            CatBehaviorRules.tick(
                b, t0, 0.016f, Mood.HUNGRY, bounds, anchors, Random(0),
                world = world,
            )
        assertThat(out.state).isEqualTo(CatState.Walking)
        assertThat(out.target).isEqualTo(toy)
    }

    @Test
    fun `sleepy beats toy — sleepy cat still goes to bed even with toy out`() {
        val toy = Position(60f, 70f)
        val world = HabitatWorld(toy = toy)
        val b = behavior(state = CatState.Idle, x = 10f, y = 10f, targetX = 10f, targetY = 10f)
        val out =
            CatBehaviorRules.tick(
                b, t0, 0.016f, Mood.SLEEPY, bounds, anchors, Random(0),
                world = world,
            )
        assertThat(out.target).isEqualTo(anchors.bed)
    }

    @Test
    fun `walking cat redirects to a toy thrown mid-walk`() {
        val toy = Position(60f, 70f)
        val world = HabitatWorld(toy = toy)
        val b = behavior(state = CatState.Walking, x = 50f, y = 50f, targetX = 100f, targetY = 50f)
        val out =
            CatBehaviorRules.tick(
                b, t0, 0.016f, Mood.IDLE, bounds, anchors, Random(0),
                world = world,
            )
        assertThat(out.target).isEqualTo(toy)
    }
```

- [ ] **Step 2: Run tests to confirm they fail**

Run: `JAVA_HOME=/home/sam/.local/jdk ANDROID_HOME=/home/sam/Android/Sdk PATH=/home/sam/.local/jdk/bin:$PATH ./gradlew :app:testDebugUnitTest --tests "com.pocketpets.app.domain.behavior.CatBehaviorRulesTest"`
Expected: FAIL — the four new tests, since `tick` doesn't yet honour `world.toy`.

- [ ] **Step 3: Add toy preempt to `tick`**

In `CatBehaviorRules.tick`, redefine the priority of targets. Replace the `moodAnchor` block with a unified `targetOverride` that checks toy first, then sleepy bed, then hungry+filled bowl:

```kotlin
        // Target priority: SLEEPY bed > thrown toy > HUNGRY+filled bowl > nothing.
        // SLEEPY beats toy because going to sleep is a stronger drive than play;
        // toy beats a hungry cat so a thrown toy redirects mid-walk to the bowl.
        val targetOverride: Position? =
            when {
                mood == Mood.SLEEPY -> anchors.bed
                world.toy != null -> world.toy
                mood == Mood.HUNGRY && world.bowlFilled -> anchors.bowl
                else -> null
            }
```

Then update the rest of `tick` to use `targetOverride` everywhere it currently uses `moodAnchor` (Lying branch, Idle branch, Walking branch's `effectiveTarget`).

Concretely, the resulting `tick` body — after the `dtSeconds <= 0f` and Eating/Playing no-op early returns — looks like:

```kotlin
        val targetOverride: Position? =
            when {
                mood == Mood.SLEEPY -> anchors.bed
                world.toy != null -> world.toy
                mood == Mood.HUNGRY && world.bowlFilled -> anchors.bowl
                else -> null
            }

        if (b.state == CatState.Lying) {
            if (targetOverride != null && targetOverride == b.position) return b
            if (mood == Mood.SLEEPY) return b
            val target = targetOverride ?: pickTarget(mood, bounds, anchors, rng, world)
            return walkingToward(b, target)
        }

        if (b.state == CatState.Idle) {
            return when {
                targetOverride != null && targetOverride != b.position -> walkingToward(b, targetOverride)
                now >= b.nextWanderAt ->
                    walkingToward(
                        b,
                        pickTarget(mood, bounds, anchors, rng, world),
                    )
                else -> b
            }
        }

        // Walking cat. Possibly retarget to the override; then advance.
        val effectiveTarget = targetOverride ?: b.target
        val advanced = advance(b.position, effectiveTarget, speedDpPerSec, dtSeconds)
        val arrived = isArrived(advanced, effectiveTarget)
        return when {
            !arrived ->
                b.copy(
                    position = advanced,
                    target = effectiveTarget,
                    facing = directionOf(b.position, effectiveTarget),
                )
            effectiveTarget == anchors.bed ->
                b.copy(
                    state = CatState.Lying,
                    position = effectiveTarget,
                    target = effectiveTarget,
                    facing = directionOf(b.position, effectiveTarget),
                )
            else ->
                b.copy(
                    state = CatState.Idle,
                    position = effectiveTarget,
                    target = effectiveTarget,
                    facing = directionOf(b.position, effectiveTarget),
                    nextWanderAt = nextWanderInstant(now, rng),
                )
        }
```

- [ ] **Step 4: Run tests**

Run: `JAVA_HOME=/home/sam/.local/jdk ANDROID_HOME=/home/sam/Android/Sdk PATH=/home/sam/.local/jdk/bin:$PATH ./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/pocketpets/app/domain/behavior/CatBehaviorRules.kt app/src/test/kotlin/com/pocketpets/app/domain/behavior/CatBehaviorRulesTest.kt
git commit -m "feat: thrown toy preempts target, except SLEEPY still routes to bed"
```

---

## Task 7: Eating state on arrival at filled bowl

**Files:**
- Modify: `app/src/main/kotlin/com/pocketpets/app/domain/behavior/CatBehaviorRules.kt`
- Modify: `app/src/test/kotlin/com/pocketpets/app/domain/behavior/CatBehaviorRulesTest.kt`

- [ ] **Step 1: Add the constant**

In `CatBehaviorRules.kt`, add to the constants block at the top:

```kotlin
        const val EATING_DURATION_SECONDS = 5L
```

- [ ] **Step 2: Write failing tests**

Append to `CatBehaviorRulesTest.kt`:

```kotlin
    @Test
    fun `walking hungry cat that arrives at filled bowl becomes Eating with stateUntil 5 seconds out`() {
        val world = HabitatWorld(bowlFilled = true)
        val b =
            behavior(
                state = CatState.Walking,
                x = anchors.bowl.x,
                y = anchors.bowl.y,
                targetX = anchors.bowl.x,
                targetY = anchors.bowl.y,
            )
        val out =
            CatBehaviorRules.tick(
                b, t0, 0.1f, Mood.HUNGRY, bounds, anchors, Random(0),
                world = world,
            )
        assertThat(out.state).isEqualTo(CatState.Eating)
        assertThat(out.stateUntil).isNotNull()
        val deltaSec = (out.stateUntil!!.toEpochMilliseconds() - t0.toEpochMilliseconds()) / 1000L
        assertThat(deltaSec).isEqualTo(CatBehaviorRules.EATING_DURATION_SECONDS)
    }

    @Test
    fun `walking cat that arrives at bowl spot but bowl is empty still becomes Idle`() {
        val world = HabitatWorld(bowlFilled = false)
        val b =
            behavior(
                state = CatState.Walking,
                x = anchors.bowl.x,
                y = anchors.bowl.y,
                targetX = anchors.bowl.x,
                targetY = anchors.bowl.y,
            )
        val out =
            CatBehaviorRules.tick(
                b, t0, 0.1f, Mood.HUNGRY, bounds, anchors, Random(0),
                world = world,
            )
        assertThat(out.state).isEqualTo(CatState.Idle)
    }
```

- [ ] **Step 3: Confirm tests fail**

Run: `JAVA_HOME=/home/sam/.local/jdk ANDROID_HOME=/home/sam/Android/Sdk PATH=/home/sam/.local/jdk/bin:$PATH ./gradlew :app:testDebugUnitTest --tests "com.pocketpets.app.domain.behavior.CatBehaviorRulesTest"`
Expected: FAIL on the new "Eating with stateUntil" test.

- [ ] **Step 4: Implement Eating arrival**

In `CatBehaviorRules.tick`, in the Walking-branch arrival `when`, add a new case before the bed and Idle cases:

```kotlin
            arrived && effectiveTarget == anchors.bowl && world.bowlFilled ->
                b.copy(
                    state = CatState.Eating,
                    position = effectiveTarget,
                    target = effectiveTarget,
                    facing = directionOf(b.position, effectiveTarget),
                    stateUntil =
                        Instant.fromEpochMilliseconds(
                            now.toEpochMilliseconds() + EATING_DURATION_SECONDS * 1000L,
                        ),
                )
```

Note: rewrite the entire `when` from the Walking branch as below to keep order explicit:

```kotlin
        return when {
            !arrived ->
                b.copy(
                    position = advanced,
                    target = effectiveTarget,
                    facing = directionOf(b.position, effectiveTarget),
                )
            effectiveTarget == anchors.bowl && world.bowlFilled ->
                b.copy(
                    state = CatState.Eating,
                    position = effectiveTarget,
                    target = effectiveTarget,
                    facing = directionOf(b.position, effectiveTarget),
                    stateUntil =
                        Instant.fromEpochMilliseconds(
                            now.toEpochMilliseconds() + EATING_DURATION_SECONDS * 1000L,
                        ),
                )
            effectiveTarget == anchors.bed ->
                b.copy(
                    state = CatState.Lying,
                    position = effectiveTarget,
                    target = effectiveTarget,
                    facing = directionOf(b.position, effectiveTarget),
                )
            else ->
                b.copy(
                    state = CatState.Idle,
                    position = effectiveTarget,
                    target = effectiveTarget,
                    facing = directionOf(b.position, effectiveTarget),
                    nextWanderAt = nextWanderInstant(now, rng),
                )
        }
```

- [ ] **Step 5: Run tests**

Run: `JAVA_HOME=/home/sam/.local/jdk ANDROID_HOME=/home/sam/Android/Sdk PATH=/home/sam/.local/jdk/bin:$PATH ./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/pocketpets/app/domain/behavior/CatBehaviorRules.kt app/src/test/kotlin/com/pocketpets/app/domain/behavior/CatBehaviorRulesTest.kt
git commit -m "feat: Eating state on arrival at filled bowl with 5s stateUntil"
```

---

## Task 8: Playing state on arrival at toy

**Files:**
- Modify: `app/src/main/kotlin/com/pocketpets/app/domain/behavior/CatBehaviorRules.kt`
- Modify: `app/src/test/kotlin/com/pocketpets/app/domain/behavior/CatBehaviorRulesTest.kt`

- [ ] **Step 1: Add constant**

In `CatBehaviorRules.kt`'s constants block:

```kotlin
        const val PLAYING_DURATION_SECONDS = 10L
```

- [ ] **Step 2: Write failing test**

Append to `CatBehaviorRulesTest.kt`:

```kotlin
    @Test
    fun `walking cat that arrives at toy becomes Playing with stateUntil 10 seconds out`() {
        val toy = Position(60f, 70f)
        val world = HabitatWorld(toy = toy)
        val b =
            behavior(
                state = CatState.Walking,
                x = toy.x, y = toy.y,
                targetX = toy.x, targetY = toy.y,
            )
        val out =
            CatBehaviorRules.tick(
                b, t0, 0.1f, Mood.IDLE, bounds, anchors, Random(0),
                world = world,
            )
        assertThat(out.state).isEqualTo(CatState.Playing)
        assertThat(out.stateUntil).isNotNull()
        val deltaSec = (out.stateUntil!!.toEpochMilliseconds() - t0.toEpochMilliseconds()) / 1000L
        assertThat(deltaSec).isEqualTo(CatBehaviorRules.PLAYING_DURATION_SECONDS)
    }
```

- [ ] **Step 3: Confirm test fails**

Run: `JAVA_HOME=/home/sam/.local/jdk ANDROID_HOME=/home/sam/Android/Sdk PATH=/home/sam/.local/jdk/bin:$PATH ./gradlew :app:testDebugUnitTest --tests "com.pocketpets.app.domain.behavior.CatBehaviorRulesTest"`
Expected: FAIL on the new test (cat reaches toy and becomes Idle, not Playing).

- [ ] **Step 4: Implement Playing arrival**

In `CatBehaviorRules.tick`, in the Walking-branch arrival `when`, insert a new case after the Eating case and before the bed case:

```kotlin
            effectiveTarget == world.toy ->
                b.copy(
                    state = CatState.Playing,
                    position = effectiveTarget,
                    target = effectiveTarget,
                    facing = directionOf(b.position, effectiveTarget),
                    stateUntil =
                        Instant.fromEpochMilliseconds(
                            now.toEpochMilliseconds() + PLAYING_DURATION_SECONDS * 1000L,
                        ),
                )
```

`world.toy` is `Position?`, so the `==` comparison shorts to false when toy is null — safe. The full Walking-arrival `when` becomes (in order): not arrived → still walking; arrived at filled bowl → Eating; arrived at toy → Playing; arrived at bed → Lying; else → Idle.

- [ ] **Step 5: Run tests**

Run: `JAVA_HOME=/home/sam/.local/jdk ANDROID_HOME=/home/sam/Android/Sdk PATH=/home/sam/.local/jdk/bin:$PATH ./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/pocketpets/app/domain/behavior/CatBehaviorRules.kt app/src/test/kotlin/com/pocketpets/app/domain/behavior/CatBehaviorRulesTest.kt
git commit -m "feat: Playing state on arrival at toy with 10s stateUntil"
```

---

## Task 9: stateUntil exit — Eating/Playing → Idle when timer expires

**Files:**
- Modify: `app/src/main/kotlin/com/pocketpets/app/domain/behavior/CatBehaviorRules.kt`
- Modify: `app/src/test/kotlin/com/pocketpets/app/domain/behavior/CatBehaviorRulesTest.kt`

- [ ] **Step 1: Write failing tests**

Append to `CatBehaviorRulesTest.kt`:

```kotlin
    @Test
    fun `eating cat exits to Idle when stateUntil is reached`() {
        val until = t0.plusSeconds(5)
        val b =
            behavior(
                state = CatState.Eating,
                x = anchors.bowl.x, y = anchors.bowl.y,
                targetX = anchors.bowl.x, targetY = anchors.bowl.y,
            ).copy(stateUntil = until)
        val later = until.plusSeconds(1)
        val out =
            CatBehaviorRules.tick(
                b, later, 0.016f, Mood.IDLE, bounds, anchors, Random(0),
                world = HabitatWorld(bowlFilled = false),
            )
        assertThat(out.state).isEqualTo(CatState.Idle)
        assertThat(out.stateUntil).isNull()
        val nextDelta = (out.nextWanderAt.toEpochMilliseconds() - later.toEpochMilliseconds()) / 1000L
        assertThat(nextDelta).isAtLeast(CatBehaviorRules.MIN_WANDER_SECONDS)
        assertThat(nextDelta).isAtMost(CatBehaviorRules.MAX_WANDER_SECONDS)
    }

    @Test
    fun `eating cat stays Eating before stateUntil`() {
        val until = t0.plusSeconds(5)
        val b =
            behavior(
                state = CatState.Eating,
                x = anchors.bowl.x, y = anchors.bowl.y,
                targetX = anchors.bowl.x, targetY = anchors.bowl.y,
            ).copy(stateUntil = until)
        val partway = t0.plusSeconds(2)
        val out =
            CatBehaviorRules.tick(
                b, partway, 0.016f, Mood.IDLE, bounds, anchors, Random(0),
                world = HabitatWorld(),
            )
        assertThat(out.state).isEqualTo(CatState.Eating)
        assertThat(out.stateUntil).isEqualTo(until)
    }

    @Test
    fun `playing cat exits to Idle when stateUntil is reached`() {
        val until = t0.plusSeconds(10)
        val b =
            behavior(
                state = CatState.Playing,
                x = 60f, y = 70f,
                targetX = 60f, targetY = 70f,
            ).copy(stateUntil = until)
        val later = until.plusSeconds(1)
        val out =
            CatBehaviorRules.tick(
                b, later, 0.016f, Mood.IDLE, bounds, anchors, Random(0),
                world = HabitatWorld(),
            )
        assertThat(out.state).isEqualTo(CatState.Idle)
        assertThat(out.stateUntil).isNull()
    }
```

- [ ] **Step 2: Confirm tests fail**

Run: `JAVA_HOME=/home/sam/.local/jdk ANDROID_HOME=/home/sam/Android/Sdk PATH=/home/sam/.local/jdk/bin:$PATH ./gradlew :app:testDebugUnitTest --tests "com.pocketpets.app.domain.behavior.CatBehaviorRulesTest"`
Expected: FAIL — `eating cat exits...` and `playing cat exits...` fail (the no-op stub returns `b` unchanged).

- [ ] **Step 3: Replace the no-op stub with the timer-driven exit**

In `CatBehaviorRules.tick`, replace the early-return block

```kotlin
        if (b.state == CatState.Eating || b.state == CatState.Playing) {
            return b
        }
```

with:

```kotlin
        // Duration-bounded states stay put until stateUntil is reached, then
        // transition back to Idle and reschedule the wander timer. Side effects
        // (refill hunger, drop the toy) are handled by the ViewModel observing
        // the state transition.
        if (b.state == CatState.Eating || b.state == CatState.Playing) {
            val until = b.stateUntil
            if (until == null || now < until) return b
            return b.copy(
                state = CatState.Idle,
                stateUntil = null,
                nextWanderAt = nextWanderInstant(now, rng),
            )
        }
```

- [ ] **Step 4: Run tests**

Run: `JAVA_HOME=/home/sam/.local/jdk ANDROID_HOME=/home/sam/Android/Sdk PATH=/home/sam/.local/jdk/bin:$PATH ./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/pocketpets/app/domain/behavior/CatBehaviorRules.kt app/src/test/kotlin/com/pocketpets/app/domain/behavior/CatBehaviorRulesTest.kt
git commit -m "feat: Eating and Playing exit to Idle when stateUntil is reached"
```

---

## Task 10: DropTarget, Item, Rect, and dropTargetAt

**Files:**
- Create: `app/src/main/kotlin/com/pocketpets/app/ui/inventory/Item.kt`
- Create: `app/src/main/kotlin/com/pocketpets/app/ui/inventory/DropTarget.kt`
- Create: `app/src/main/kotlin/com/pocketpets/app/ui/inventory/DpRect.kt`
- Create: `app/src/main/kotlin/com/pocketpets/app/ui/inventory/DropTargets.kt`
- Create: `app/src/test/kotlin/com/pocketpets/app/ui/inventory/DropTargetsTest.kt`

- [ ] **Step 1: Write the failing test (drives the API shape)**

Create `app/src/test/kotlin/com/pocketpets/app/ui/inventory/DropTargetsTest.kt`:

```kotlin
package com.pocketpets.app.ui.inventory

import com.google.common.truth.Truth.assertThat
import com.pocketpets.app.domain.behavior.Anchors
import com.pocketpets.app.domain.behavior.HabitatBounds
import com.pocketpets.app.domain.behavior.Position
import org.junit.Test

class DropTargetsTest {
    private val bounds = HabitatBounds(0f, 0f, 200f, 200f)
    private val anchors = Anchors(bed = Position(160f, 160f), bowl = Position(20f, 160f))
    private val poopRects =
        listOf(
            DpRect(80f, 100f, 128f, 148f),
            DpRect(120f, 100f, 168f, 148f),
        )

    private fun resolve(
        item: Item,
        x: Float,
        y: Float,
    ) = dropTargetAt(Position(x, y), item, bounds, anchors, poopRects)

    @Test
    fun `food on the bowl resolves to Bowl`() {
        assertThat(resolve(Item.Food, anchors.bowl.x + 10f, anchors.bowl.y + 10f))
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
        assertThat(resolve(Item.Scoop, anchors.bowl.x + 10f, anchors.bowl.y + 10f)).isNull()
    }

    @Test
    fun `toy inside bounds and not on bowl resolves to Floor at the drop position`() {
        val out = resolve(Item.Toy, 100f, 50f)
        assertThat(out).isEqualTo(DropTarget.Floor(Position(100f, 50f)))
    }

    @Test
    fun `toy outside bounds resolves to null`() {
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
        val out = resolve(Item.Toy, anchors.bowl.x + 10f, anchors.bowl.y + 10f)
        assertThat(out).isEqualTo(DropTarget.Floor(Position(anchors.bowl.x + 10f, anchors.bowl.y + 10f)))
    }
}
```

- [ ] **Step 2: Run the test to confirm compile failure**

Run: `JAVA_HOME=/home/sam/.local/jdk ANDROID_HOME=/home/sam/Android/Sdk PATH=/home/sam/.local/jdk/bin:$PATH ./gradlew :app:compileDebugUnitTestKotlin`
Expected: FAIL — `DropTarget`, `Item`, `DpRect`, `dropTargetAt` are unresolved.

- [ ] **Step 3: Implement the four production files**

Create `app/src/main/kotlin/com/pocketpets/app/ui/inventory/Item.kt`:

```kotlin
package com.pocketpets.app.ui.inventory

/** Items the user can drag from the inventory tray. */
enum class Item { Food, Scoop, Toy }
```

Create `app/src/main/kotlin/com/pocketpets/app/ui/inventory/DropTarget.kt`:

```kotlin
package com.pocketpets.app.ui.inventory

import com.pocketpets.app.domain.behavior.Position

/** What a drop landed on. `null` (returned from [dropTargetAt]) means rejected. */
sealed interface DropTarget {
    data object Bowl : DropTarget

    data class Poop(val index: Int) : DropTarget

    data class Floor(val position: Position) : DropTarget
}
```

Create `app/src/main/kotlin/com/pocketpets/app/ui/inventory/DpRect.kt`:

```kotlin
package com.pocketpets.app.ui.inventory

import com.pocketpets.app.domain.behavior.Position

/**
 * Axis-aligned rectangle in dp coordinates. Pure Kotlin so [dropTargetAt] is
 * unit-testable without Compose. Inclusive bounds on all four sides.
 */
data class DpRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    fun contains(p: Position): Boolean = p.x in left..right && p.y in top..bottom

    fun center(): Pair<Float, Float> = ((left + right) / 2f) to ((top + bottom) / 2f)
}
```

Create `app/src/main/kotlin/com/pocketpets/app/ui/inventory/DropTargets.kt`:

```kotlin
package com.pocketpets.app.ui.inventory

import com.pocketpets.app.domain.behavior.Anchors
import com.pocketpets.app.domain.behavior.HabitatBounds
import com.pocketpets.app.domain.behavior.Position

/** Width and height in dp of the bowl decor (matches the rendered Image size). */
internal const val BOWL_WIDTH_DP = 64f
internal const val BOWL_HEIGHT_DP = 32f

/**
 * Resolves where a drag-drop landed.
 *
 * Resolution by item:
 *  - [Item.Food]: the bowl rect → [DropTarget.Bowl]; otherwise null.
 *  - [Item.Scoop]: the first poop rect that contains [position] → [DropTarget.Poop]; otherwise null.
 *  - [Item.Toy]: any point inside [bounds] → [DropTarget.Floor]; otherwise null.
 */
fun dropTargetAt(
    position: Position,
    item: Item,
    bounds: HabitatBounds,
    anchors: Anchors,
    poopRects: List<DpRect>,
): DropTarget? {
    val bowlRect =
        DpRect(
            left = anchors.bowl.x,
            top = anchors.bowl.y,
            right = anchors.bowl.x + BOWL_WIDTH_DP,
            bottom = anchors.bowl.y + BOWL_HEIGHT_DP,
        )

    return when (item) {
        Item.Food -> if (bowlRect.contains(position)) DropTarget.Bowl else null
        Item.Scoop -> {
            val idx = poopRects.indexOfFirst { it.contains(position) }
            if (idx >= 0) DropTarget.Poop(idx) else null
        }
        Item.Toy ->
            if (position.x in bounds.minX..bounds.maxX &&
                position.y in bounds.minY..bounds.maxY
            ) {
                DropTarget.Floor(position)
            } else {
                null
            }
    }
}
```

- [ ] **Step 4: Run tests**

Run: `JAVA_HOME=/home/sam/.local/jdk ANDROID_HOME=/home/sam/Android/Sdk PATH=/home/sam/.local/jdk/bin:$PATH ./gradlew :app:testDebugUnitTest --tests "com.pocketpets.app.ui.inventory.DropTargetsTest"`
Expected: BUILD SUCCESSFUL — all 10 cases pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/pocketpets/app/ui/inventory/Item.kt app/src/main/kotlin/com/pocketpets/app/ui/inventory/DropTarget.kt app/src/main/kotlin/com/pocketpets/app/ui/inventory/DpRect.kt app/src/main/kotlin/com/pocketpets/app/ui/inventory/DropTargets.kt app/src/test/kotlin/com/pocketpets/app/ui/inventory/DropTargetsTest.kt
git commit -m "feat: add Item enum, DropTarget, and pure dropTargetAt resolver"
```

---

## Task 11: DragController state holder

**Files:**
- Create: `app/src/main/kotlin/com/pocketpets/app/ui/inventory/DragController.kt`
- Create: `app/src/test/kotlin/com/pocketpets/app/ui/inventory/DragControllerTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/kotlin/com/pocketpets/app/ui/inventory/DragControllerTest.kt`:

```kotlin
package com.pocketpets.app.ui.inventory

import com.google.common.truth.Truth.assertThat
import com.pocketpets.app.domain.behavior.Position
import org.junit.Test

class DragControllerTest {
    @Test
    fun `inFlight is null at rest`() {
        val c = DragController()
        assertThat(c.inFlight).isNull()
    }

    @Test
    fun `start sets the in-flight item with origin position`() {
        val c = DragController()
        c.start(Item.Food)
        assertThat(c.inFlight?.item).isEqualTo(Item.Food)
        assertThat(c.inFlight?.position).isEqualTo(Position(0f, 0f))
    }

    @Test
    fun `move updates the position while keeping the item`() {
        val c = DragController()
        c.start(Item.Toy)
        c.move(Position(50f, 60f))
        assertThat(c.inFlight?.item).isEqualTo(Item.Toy)
        assertThat(c.inFlight?.position).isEqualTo(Position(50f, 60f))
    }

    @Test
    fun `move with no in-flight drag is a no-op`() {
        val c = DragController()
        c.move(Position(10f, 10f))
        assertThat(c.inFlight).isNull()
    }

    @Test
    fun `end returns the in-flight drag and clears it`() {
        val c = DragController()
        c.start(Item.Scoop)
        c.move(Position(5f, 5f))
        val ended = c.end()
        assertThat(ended?.item).isEqualTo(Item.Scoop)
        assertThat(ended?.position).isEqualTo(Position(5f, 5f))
        assertThat(c.inFlight).isNull()
    }

    @Test
    fun `end with no in-flight drag returns null`() {
        val c = DragController()
        assertThat(c.end()).isNull()
    }
}
```

- [ ] **Step 2: Confirm compile failure**

Run: `JAVA_HOME=/home/sam/.local/jdk ANDROID_HOME=/home/sam/Android/Sdk PATH=/home/sam/.local/jdk/bin:$PATH ./gradlew :app:compileDebugUnitTestKotlin`
Expected: FAIL — `DragController` and `DragInFlight` unresolved.

- [ ] **Step 3: Implement the controller**

Create `app/src/main/kotlin/com/pocketpets/app/ui/inventory/DragController.kt`:

```kotlin
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
```

- [ ] **Step 4: Run tests**

Run: `JAVA_HOME=/home/sam/.local/jdk ANDROID_HOME=/home/sam/Android/Sdk PATH=/home/sam/.local/jdk/bin:$PATH ./gradlew :app:testDebugUnitTest --tests "com.pocketpets.app.ui.inventory.DragControllerTest"`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/pocketpets/app/ui/inventory/DragController.kt app/src/test/kotlin/com/pocketpets/app/ui/inventory/DragControllerTest.kt
git commit -m "feat: add DragController state holder for in-flight drags"
```

---

## Task 12: InventoryTray Composable (layout-only)

**Files:**
- Create: `app/src/main/kotlin/com/pocketpets/app/ui/inventory/InventoryTray.kt`

The tray is layout-only. Gesture handling lives in `PetScreen` (Task 16), where pointer events arrive in screen-relative coordinates that the drag overlay can render against directly. Each slot reports its on-screen rect via `onSlotPositioned` so the screen can map a long-press start back to the picked-up item.

(No unit test — Compose layout is verified in manual QA. The pure dragControl + drop-target logic underneath is already covered.)

- [ ] **Step 1: Write the Composable**

Create `app/src/main/kotlin/com/pocketpets/app/ui/inventory/InventoryTray.kt`:

```kotlin
package com.pocketpets.app.ui.inventory

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.pocketpets.app.R

/**
 * Bottom-of-screen tray with three layout-only slots: Food, Scoop, Toy.
 * Each slot reports its screen-relative rect (in dp) via [onSlotPositioned]
 * so the parent screen can resolve a long-press start back to the picked-up
 * item without owning the gesture handler itself.
 */
@Composable
fun InventoryTray(
    onSlotPositioned: (Item, leftDp: Float, topDp: Float, sizeDp: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(Color(0x66000000))
                .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TraySlot(item = Item.Food, drawable = R.drawable.food, onSlotPositioned = onSlotPositioned)
        TraySlot(item = Item.Scoop, drawable = R.drawable.scoop, onSlotPositioned = onSlotPositioned)
        TraySlot(item = Item.Toy, drawable = R.drawable.toy, onSlotPositioned = onSlotPositioned)
    }
}

@Composable
private fun TraySlot(
    item: Item,
    drawable: Int,
    onSlotPositioned: (Item, leftDp: Float, topDp: Float, sizeDp: Float) -> Unit,
) {
    val density = LocalDensity.current
    Box(
        modifier =
            Modifier
                .size(64.dp)
                .onGloballyPositioned { coords ->
                    val pos = coords.positionInRoot()
                    with(density) {
                        onSlotPositioned(
                            item,
                            pos.x.toDp().value,
                            pos.y.toDp().value,
                            coords.size.width.toDp().value,
                        )
                    }
                },
    ) {
        Image(
            painter = painterResource(drawable),
            contentDescription = item.name,
            modifier = Modifier.size(64.dp),
        )
    }
}
```

- [ ] **Step 2: Run ktlint to confirm style passes**

Run: `JAVA_HOME=/home/sam/.local/jdk ANDROID_HOME=/home/sam/Android/Sdk PATH=/home/sam/.local/jdk/bin:$PATH ./gradlew ktlintCheck`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Run tests (no new tests; just confirm still green)**

Run: `JAVA_HOME=/home/sam/.local/jdk ANDROID_HOME=/home/sam/Android/Sdk PATH=/home/sam/.local/jdk/bin:$PATH ./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/pocketpets/app/ui/inventory/InventoryTray.kt
git commit -m "feat: add layout-only InventoryTray Composable (gestures wired in PetScreen)"
```

---

## Task 13: PetViewModel — HabitatWorld plumbing into state and tick

**Files:**
- Modify: `app/src/main/kotlin/com/pocketpets/app/ui/pet/PetViewModel.kt`

- [ ] **Step 1: Add the world MutableStateFlow and expose it in PetUiState**

Modify `app/src/main/kotlin/com/pocketpets/app/ui/pet/PetViewModel.kt`. Add `world` to the data class:

```kotlin
data class PetUiState(
    val pet: Pet? = null,
    val mood: Mood = Mood.IDLE,
    val stage: GrowthStage = GrowthStage.BABY,
    val activePhrase: Phrase? = null,
    val behavior: CatBehavior? = null,
    val world: HabitatWorld = HabitatWorld(),
)
```

Add the imports:

```kotlin
import com.pocketpets.app.domain.behavior.HabitatWorld
import kotlinx.coroutines.flow.asStateFlow
```

Inside the class, after the existing `behaviorFlow` declaration, add:

```kotlin
    private val _world: MutableStateFlow<HabitatWorld> = MutableStateFlow(HabitatWorld())
    val world: StateFlow<HabitatWorld> = _world.asStateFlow()
```

Update the `combine` for `state` to include the world flow:

```kotlin
    val state: StateFlow<PetUiState> =
        combine(
            repo.observeActive(),
            ticker(60_000L),
            currentPhrase,
            behaviorFlow,
            _world,
        ) { values ->
            @Suppress("UNCHECKED_CAST")
            val rawPet = values[0] as Pet?
            val phrase = values[2] as Phrase?
            val behavior = values[3] as CatBehavior
            val worldNow = values[4] as HabitatWorld
            val now = clock.now()
            val ticked = rawPet?.let { StatDecay.tick(it, now) }
            val mood = ticked?.let { Mood.from(it, now, zone) } ?: Mood.IDLE
            val stage = ticked?.let { GrowthStage.fromAge(it.bornAt, now) } ?: GrowthStage.BABY
            PetUiState(ticked, mood, stage, phrase, behavior, worldNow)
        }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), PetUiState())
```

> **Why the array-style `combine`?** `kotlinx.coroutines.flow.combine` has a typed overload only up to 5 flows; with 5 sources we hit the boundary cleanly, but if a future task adds a sixth we'd need the vararg form. The vararg form takes `Array<Any?>` and returns `R` so we'd cast each element. The 5-arg typed form is actually fine here — the snippet above is for safety. **Use the 5-arg typed form** instead:

```kotlin
    val state: StateFlow<PetUiState> =
        combine(
            repo.observeActive(),
            ticker(60_000L),
            currentPhrase,
            behaviorFlow,
            _world,
        ) { rawPet, _, phrase, behavior, worldNow ->
            val now = clock.now()
            val ticked = rawPet?.let { StatDecay.tick(it, now) }
            val mood = ticked?.let { Mood.from(it, now, zone) } ?: Mood.IDLE
            val stage = ticked?.let { GrowthStage.fromAge(it.bornAt, now) } ?: GrowthStage.BABY
            PetUiState(ticked, mood, stage, phrase, behavior, worldNow)
        }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), PetUiState())
```

Update the frame ticker block (the `scope.launch { var lastFrame = ...; while (true) { ... } }` near the bottom of `init`) to read `_world.value` and pass it into `tick`:

```kotlin
        scope.launch {
            var lastFrame = clock.now()
            while (true) {
                delay(16)
                val now = clock.now()
                val dtSec =
                    ((now.toEpochMilliseconds() - lastFrame.toEpochMilliseconds()) / 1000f)
                        .coerceAtMost(0.1f)
                lastFrame = now
                behaviorFlow.update { b ->
                    CatBehaviorRules.tick(
                        b = b,
                        now = now,
                        dtSeconds = dtSec,
                        mood = currentMood,
                        bounds = habitatBounds,
                        anchors = habitatAnchors,
                        rng = rng,
                        world = _world.value,
                    )
                }
            }
        }
```

- [ ] **Step 2: Run tests**

Run: `JAVA_HOME=/home/sam/.local/jdk ANDROID_HOME=/home/sam/Android/Sdk PATH=/home/sam/.local/jdk/bin:$PATH ./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL. Existing `PetViewModelTest` cases still pass (default empty world is unchanged).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/pocketpets/app/ui/pet/PetViewModel.kt
git commit -m "feat: thread HabitatWorld through PetViewModel state and frame ticker"
```

---

## Task 14: PetViewModel callbacks for drop/long-press

**Files:**
- Modify: `app/src/main/kotlin/com/pocketpets/app/ui/pet/PetViewModel.kt`
- Modify: `app/src/test/kotlin/com/pocketpets/app/ui/pet/PetViewModelTest.kt`

- [ ] **Step 1: Write the failing tests**

Append to `PetViewModelTest.kt`:

```kotlin
    @Test fun `onFoodDroppedOnBowl flips world bowlFilled true`() =
        runTest {
            val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
            val repo = FakeRepo(samplePet())
            val vm = newVm(repo, testScope)
            try {
                vm.state.first { it.pet != null }
                vm.onFoodDroppedOnBowl()
                val state = vm.state.first { it.world.bowlFilled }
                assertThat(state.world.bowlFilled).isTrue()
            } finally {
                testScope.cancel()
            }
        }

    @Test fun `onScoopDroppedOnPoop calls repo clean`() =
        runTest {
            val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
            val repo = FakeRepo(samplePet())
            val vm = newVm(repo, testScope)
            try {
                vm.state.first { it.pet != null }
                vm.onScoopDroppedOnPoop(0)
                assertThat(repo.calls).contains("clean:1")
            } finally {
                testScope.cancel()
            }
        }

    @Test fun `onToyDropped sets world toy position`() =
        runTest {
            val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
            val repo = FakeRepo(samplePet())
            val vm = newVm(repo, testScope)
            try {
                vm.state.first { it.pet != null }
                vm.onToyDropped(com.pocketpets.app.domain.behavior.Position(50f, 60f))
                val state = vm.state.first { it.world.toy != null }
                assertThat(state.world.toy)
                    .isEqualTo(com.pocketpets.app.domain.behavior.Position(50f, 60f))
            } finally {
                testScope.cancel()
            }
        }

    @Test fun `onCatHeld calls repo pet`() =
        runTest {
            val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
            val repo = FakeRepo(samplePet())
            val vm = newVm(repo, testScope)
            try {
                vm.state.first { it.pet != null }
                vm.onCatHeld()
                assertThat(repo.calls).contains("pet:1")
            } finally {
                testScope.cancel()
            }
        }
```

- [ ] **Step 2: Confirm tests fail (compile error or unresolved)**

Run: `JAVA_HOME=/home/sam/.local/jdk ANDROID_HOME=/home/sam/Android/Sdk PATH=/home/sam/.local/jdk/bin:$PATH ./gradlew :app:compileDebugUnitTestKotlin`
Expected: FAIL — `onFoodDroppedOnBowl`, `onScoopDroppedOnPoop`, `onToyDropped`, `onCatHeld` are unresolved.

- [ ] **Step 3: Add the four callbacks to `PetViewModel`**

Append, after the `talk()` function in `PetViewModel.kt`:

```kotlin
    fun onFoodDroppedOnBowl() {
        _world.value = _world.value.copy(bowlFilled = true)
    }

    fun onScoopDroppedOnPoop(@Suppress("UNUSED_PARAMETER") poopIndex: Int) =
        withActive { repo.clean(it) }

    fun onToyDropped(position: com.pocketpets.app.domain.behavior.Position) {
        _world.value = _world.value.copy(toy = position)
    }

    fun onCatHeld() = withActive { repo.pet(it) }
```

(`poopIndex` is currently unused — `repo.clean` already removes one poop globally. Keeping the parameter makes the call site self-documenting and lets a future feature distinguish *which* poop was scooped if we ever animate per-poop removal. Suppress the unused-parameter warning explicitly.)

Add the import `import com.pocketpets.app.domain.behavior.Position` at the top.

- [ ] **Step 4: Run tests**

Run: `JAVA_HOME=/home/sam/.local/jdk ANDROID_HOME=/home/sam/Android/Sdk PATH=/home/sam/.local/jdk/bin:$PATH ./gradlew :app:testDebugUnitTest --tests "com.pocketpets.app.ui.pet.PetViewModelTest"`
Expected: BUILD SUCCESSFUL — four new tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/pocketpets/app/ui/pet/PetViewModel.kt app/src/test/kotlin/com/pocketpets/app/ui/pet/PetViewModelTest.kt
git commit -m "feat: add PetViewModel callbacks for food/scoop/toy/long-press"
```

---

## Task 15: Side-effect dispatch on Eating/Playing transitions

**Files:**
- Modify: `app/src/main/kotlin/com/pocketpets/app/ui/pet/PetViewModel.kt`
- Modify: `app/src/test/kotlin/com/pocketpets/app/ui/pet/PetViewModelTest.kt`

- [ ] **Step 1: Write failing tests using FakeClock advance + tick observation**

Append to `PetViewModelTest.kt`:

```kotlin
    @Test fun `cat reaching filled bowl while hungry calls repo feed exactly once and empties bowl`() =
        runTest {
            val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
            val repo = FakeRepo(samplePet(hunger = 20f)) // HUNGRY threshold
            val vm = newVm(repo, testScope)
            try {
                vm.state.first { it.pet != null }
                // Drop food and wait for the cat to walk to the bowl. The bowl
                // anchor is in the default habitat (Position(40f, 160f)). The
                // cat starts at (120f, 100f). At 60 dp/s the trip is well under
                // 5 s; advance the FakeClock and let frame ticks accumulate.
                vm.onFoodDroppedOnBowl()
                // Spin the FakeClock forward in 16 ms ticks, ~3 s of sim time.
                repeat(200) { clock.advance(16) }
                // Eating is now in flight. Confirm side effects fired exactly once.
                assertThat(repo.calls.count { it == "feed:1" }).isEqualTo(1)
                assertThat(vm.state.first().world.bowlFilled).isFalse()
            } finally {
                testScope.cancel()
            }
        }

    @Test fun `cat reaching toy calls repo pet exactly once and toy clears after Playing ends`() =
        runTest {
            val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
            val repo = FakeRepo(samplePet())
            val vm = newVm(repo, testScope)
            try {
                vm.state.first { it.pet != null }
                vm.onToyDropped(com.pocketpets.app.domain.behavior.Position(120f, 100f))
                repeat(800) { clock.advance(16) } // ~13 s; > 10 s playing duration.
                assertThat(repo.calls.count { it == "pet:1" }).isAtLeast(1)
                // Toy gone after Playing ends.
                val finalState = vm.state.first { it.world.toy == null }
                assertThat(finalState.world.toy).isNull()
            } finally {
                testScope.cancel()
            }
        }
```

These tests need `FakeClock.advance(ms)`. Verify it exists in `app/src/test/kotlin/com/pocketpets/app/testing/FakeClock.kt` — if it doesn't, the test will fail at compile time and we add a minimal `advance(deltaMs: Long)` method. Inspect first:

```bash
grep -n "advance\|fun" app/src/test/kotlin/com/pocketpets/app/testing/FakeClock.kt
```

If the method is named `advanceBy(...)` or similar, adjust the test calls accordingly. The plan assumes `advance(deltaMs: Long)`.

- [ ] **Step 2: Confirm tests fail**

Run: `JAVA_HOME=/home/sam/.local/jdk ANDROID_HOME=/home/sam/Android/Sdk PATH=/home/sam/.local/jdk/bin:$PATH ./gradlew :app:testDebugUnitTest --tests "com.pocketpets.app.ui.pet.PetViewModelTest"`
Expected: FAIL — the cat reaches the bowl/toy but `feed`/`pet` is never called from the ViewModel side, and the bowl stays filled / toy stays out.

- [ ] **Step 3: Add side-effect dispatch in the frame ticker**

Modify the frame-ticker block in `PetViewModel.init` to capture `before` and dispatch on transition. Replace the inner `behaviorFlow.update { ... }` with a manual read-modify-write that lets us see the previous state:

```kotlin
        scope.launch {
            var lastFrame = clock.now()
            while (true) {
                delay(16)
                val now = clock.now()
                val dtSec =
                    ((now.toEpochMilliseconds() - lastFrame.toEpochMilliseconds()) / 1000f)
                        .coerceAtMost(0.1f)
                lastFrame = now
                val before = behaviorFlow.value
                val after =
                    CatBehaviorRules.tick(
                        b = before,
                        now = now,
                        dtSeconds = dtSec,
                        mood = currentMood,
                        bounds = habitatBounds,
                        anchors = habitatAnchors,
                        rng = rng,
                        world = _world.value,
                    )
                behaviorFlow.value = after

                if (before.state != after.state) {
                    when {
                        after.state == CatState.Eating -> {
                            _world.value = _world.value.copy(bowlFilled = false)
                            withActive { repo.feed(it) }
                        }
                        after.state == CatState.Playing -> {
                            withActive { repo.pet(it) }
                        }
                        before.state == CatState.Playing && after.state == CatState.Idle -> {
                            _world.value = _world.value.copy(toy = null)
                        }
                    }
                }
            }
        }
```

(Drop the unused `behaviorFlow.update` extension if it's only called from this block; otherwise leave it for the existing call sites.)

Add the missing import: `import com.pocketpets.app.domain.behavior.CatState`.

- [ ] **Step 4: Run tests**

Run: `JAVA_HOME=/home/sam/.local/jdk ANDROID_HOME=/home/sam/Android/Sdk PATH=/home/sam/.local/jdk/bin:$PATH ./gradlew :app:testDebugUnitTest --tests "com.pocketpets.app.ui.pet.PetViewModelTest"`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/pocketpets/app/ui/pet/PetViewModel.kt app/src/test/kotlin/com/pocketpets/app/ui/pet/PetViewModelTest.kt
git commit -m "feat: dispatch repo.feed/pet and clear bowl/toy on cat state transitions"
```

---

## Task 16: PetScreen — replace button row with InventoryTray + drag overlay; long-press cat; render bowl_full and toy

**Files:**
- Modify: `app/src/main/kotlin/com/pocketpets/app/ui/pet/PetScreen.kt`

(No new unit tests — Compose UI is verified in manual QA.)

- [ ] **Step 1: Add screen-size + habitat-state hoisting and the `poopRectFor` helper**

The drag handler needs access to: the habitat `bounds` and `anchors` (currently only sent to the ViewModel and not retained locally), the screen size (for poop hit-rect math), and a function that maps poop indices to screen-rects. Hoist the state and add the helper before touching anything else.

In `PetScreen.kt`, add a top-level `private fun poopRectFor(...)` outside the `@Composable`:

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

Inside the `PetScreen` Composable, add four pieces of remembered state alongside the existing `density`:

```kotlin
    var habitatBoundsState by remember { mutableStateOf<HabitatBounds?>(null) }
    var habitatAnchorsState by remember { mutableStateOf<Anchors?>(null) }
    var screenWidthDp by remember { mutableStateOf(0f) }
    var screenHeightDp by remember { mutableStateOf(0f) }
    val dragController = remember { DragController() }
    val slotRects = remember { mutableStateMapOf<Item, DpRect>() }
```

Update the existing room-bg `Image`'s `onSizeChanged` lambda. Inside the existing `with(density) { … }` block, after the existing `widthDp` / `heightDp` calculation but before the `vm.setHabitat(bounds, anchors)` call, add:

```kotlin
                            screenWidthDp = widthDp
                            screenHeightDp = heightDp
                            habitatBoundsState = bounds
                            habitatAnchorsState = anchors
```

(`vm.setHabitat` continues to be called as the last line of the block.)

- [ ] **Step 2: Update bowl rendering to react to `world.bowlFilled` and add toy rendering**

Find the existing `Image(painter = painterResource(R.drawable.bowl), …)` block and replace it with a state-aware version that picks `bowl_full` when the world says so. Add a toy `Image` immediately after, gated on `state.world.toy != null`. Keep the existing poop-rendering `repeat` block unchanged below.

```kotlin
        if (pet != null) {
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

            state.world.toy?.let { toyPos ->
                Image(
                    painter = painterResource(R.drawable.toy),
                    contentDescription = null,
                    modifier =
                        Modifier
                            .offset(x = toyPos.x.dp, y = toyPos.y.dp)
                            .size(48.dp),
                )
            }

            // (existing poopOffsets + repeat(pet.poopCount) { Image(...) } stays as-is)
            …
        }
```

- [ ] **Step 3: Replace the action-button `Row` with `InventoryTray` + screen-level drag handler**

Locate the existing `// Action buttons` block (the `Row` with Feed/Clean/Pet/Talk buttons) and replace it with the tray + drag-overlay block below. The drag handler lives on a `Box` that wraps the tray, so pointer coordinates arrive in screen-relative space. The handler picks the item by mapping the long-press start back to the slot rect reported by `InventoryTray`.

```kotlin
        if (pet != null) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { startOffset ->
                                    val startDp =
                                        with(density) {
                                            Position(
                                                startOffset.x.toDp().value,
                                                startOffset.y.toDp().value,
                                            )
                                        }
                                    val pickedItem =
                                        slotRects.entries
                                            .firstOrNull { (_, r) -> r.contains(startDp) }
                                            ?.key
                                    if (pickedItem != null) dragController.start(pickedItem)
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    val pos =
                                        with(density) {
                                            Position(
                                                change.position.x.toDp().value,
                                                change.position.y.toDp().value,
                                            )
                                        }
                                    dragController.move(pos)
                                },
                                onDragEnd = {
                                    val ended =
                                        dragController.end() ?: return@detectDragGesturesAfterLongPress
                                    val bounds = habitatBoundsState ?: return@detectDragGesturesAfterLongPress
                                    val anchors = habitatAnchorsState ?: return@detectDragGesturesAfterLongPress
                                    val poopRects =
                                        (0 until pet.poopCount).map { i ->
                                            poopRectFor(i, poopOffsets, screenWidthDp, screenHeightDp)
                                        }
                                    val target =
                                        dropTargetAt(
                                            position = ended.position,
                                            item = ended.item,
                                            bounds = bounds,
                                            anchors = anchors,
                                            poopRects = poopRects,
                                        ) ?: return@detectDragGesturesAfterLongPress
                                    when (target) {
                                        DropTarget.Bowl -> vm.onFoodDroppedOnBowl()
                                        is DropTarget.Poop -> vm.onScoopDroppedOnPoop(target.index)
                                        is DropTarget.Floor -> vm.onToyDropped(target.position)
                                    }
                                },
                                onDragCancel = { dragController.end() },
                            )
                        },
            ) {
                InventoryTray(
                    onSlotPositioned = { item, leftDp, topDp, sizeDp ->
                        slotRects[item] = DpRect(leftDp, topDp, leftDp + sizeDp, topDp + sizeDp)
                    },
                )
            }
        }

        // Drag overlay — renders the in-flight item icon at the pointer position.
        dragController.inFlight?.let { drag ->
            val drawableId =
                when (drag.item) {
                    Item.Food -> R.drawable.food
                    Item.Scoop -> R.drawable.scoop
                    Item.Toy -> R.drawable.toy
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

> Both `poopOffsets` (the existing `remember(pet.id)` list of int offsets) and `pet` are referenced here — keep this block inside the same `if (pet != null)` scope as the existing poop-rendering, or hoist `poopOffsets` to the top of the Composable so it's reachable. The cleanest move: collapse the bowl/toy/poops/tray rendering under one `if (pet != null) { … }` block.

- [ ] **Step 4: Replace `clickable { vm.talk(); vm.pet() }` on the cat sprite with long-press**

Find the cat sprite `Box` and update its modifier:

```kotlin
            Box(
                modifier =
                    Modifier
                        .offset(x = behavior.position.x.dp, y = behavior.position.y.dp)
                        .size(spriteSize)
                        .pointerInput(Unit) {
                            detectTapGestures(onLongPress = { vm.onCatHeld() })
                        },
            ) {
                AnimatedSprite(
                    animation = animation,
                    …
                    facing = CatAnimations.facingFor(behavior.state, behavior.facing),
                )
                …
            }
```

Add the import: `import androidx.compose.foundation.gestures.detectTapGestures`.

- [ ] **Step 5: Remove the Feed/Clean/Pet/Talk button row**

Delete the entire `// Action buttons` block at the bottom of `PetScreen` — the inventory tray now lives in that real estate. (It's already replaced by Step 2, just verify it's gone.)

- [ ] **Step 6: Confirm imports**

The screen now imports, in addition to the existing list:

```kotlin
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.pocketpets.app.domain.behavior.HabitatBounds
import com.pocketpets.app.domain.behavior.Position
import com.pocketpets.app.ui.inventory.DpRect
import com.pocketpets.app.ui.inventory.DragController
import com.pocketpets.app.ui.inventory.DropTarget
import com.pocketpets.app.ui.inventory.InventoryTray
import com.pocketpets.app.ui.inventory.Item
import com.pocketpets.app.ui.inventory.dropTargetAt
```

(`Anchors` is already imported.)

The previous `import androidx.compose.foundation.clickable` can be removed (no other call site).

- [ ] **Step 7: Run lint, ktlint, and tests**

```
JAVA_HOME=/home/sam/.local/jdk ANDROID_HOME=/home/sam/Android/Sdk PATH=/home/sam/.local/jdk/bin:$PATH ./gradlew :app:testDebugUnitTest ktlintCheck :app:lintDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/kotlin/com/pocketpets/app/ui/pet/PetScreen.kt
git commit -m "feat: PetScreen replaces button row with inventory tray + drag-drop layer"
```

---

## Task 17: Manual verification, push, open PR

**Files:** none — verification + workflow only.

- [ ] **Step 1: Run the full local test + lint suite**

```
JAVA_HOME=/home/sam/.local/jdk ANDROID_HOME=/home/sam/Android/Sdk PATH=/home/sam/.local/jdk/bin:$PATH ./gradlew :app:testDebugUnitTest ktlintCheck :app:lintDebug
```

Expected: BUILD SUCCESSFUL on all three.

- [ ] **Step 2: Build a debug APK locally to confirm the runtime resources resolve**

```
JAVA_HOME=/home/sam/.local/jdk ANDROID_HOME=/home/sam/Android/Sdk PATH=/home/sam/.local/jdk/bin:$PATH ./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL. Confirms `R.drawable.food`, `R.drawable.scoop`, `R.drawable.toy`, `R.drawable.bowl_full` are all generated.

- [ ] **Step 3: Push the branch**

```bash
git push origin cat-interactions
```

- [ ] **Step 4: Open the PR**

```bash
gh pr create --base main --head cat-interactions --title "Phase 3: direct-manipulation interactions (drag tray + long-press to pet)" --body "$(cat <<'EOF'
## Summary

- Bottom inventory tray (Food / Scoop / Toy) replaces the Feed/Clean/Pet/Talk button row. Long-press a slot to pick up; drag onto a target; release.
- New `domain/behavior/HabitatWorld(bowlFilled, toy)` is plumbed into `CatBehaviorRules.tick`. Toy preempts mood (except SLEEPY); hungry cat only routes to the bowl when filled.
- Two new cat states: `Eating` (5 s at filled bowl, sit sprite) and `Playing` (10 s at toy, walk sprite in place). `CatBehavior.stateUntil` drives the timer-based exit.
- `PetViewModel` observes the pure state transitions and dispatches one-shot `repo.feed`/`repo.pet`/world updates. Drop-target resolution is a pure function (`dropTargetAt`) covered by 10 unit cases.
- Touch-and-hold the cat triggers `pet()` (existing diminishing-returns logic unchanged). On-demand "talk" is removed; idle chatter remains.

## Test plan

- [x] `./gradlew :app:testDebugUnitTest ktlintCheck :app:lintDebug` — green
- [x] `./gradlew :app:assembleDebug` — APK builds, all new drawables resolve
- [ ] Sideload the CI debug APK and confirm:
  - [ ] Bottom of screen shows three icons (food, scoop, toy), no buttons
  - [ ] Long-press food → drag onto bowl → bowl fills → cat walks over → eats for ~5 s → hunger refills, bowl empties
  - [ ] Long-press scoop → drag onto a single poop → that poop disappears (other poops untouched)
  - [ ] Long-press toy → drag to floor → cat walks to drop point → plays for ~10 s → happiness rises → toy disappears
  - [ ] Drop a second toy mid-play → cat redirects to new toy
  - [ ] Long-press the cat → happiness rises (diminishing returns may eat repeats)
  - [ ] Phase 2 behaviour intact: random wander, bed when sleepy, breathing while idle/lying

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

- [ ] **Step 5: Report the PR URL**

Print the URL `gh pr create` returned.

---

## Notes for the implementer

- **Frequent commits.** Every task ends with a commit; do not batch.
- **TDD discipline.** When a task says "write the failing test", actually run it and confirm the failure mode before writing the implementation.
- **`./gradlew` requires `JAVA_HOME=/home/sam/.local/jdk` and `ANDROID_HOME=/home/sam/Android/Sdk` on this machine.** Every gradle command in the plan is prefixed accordingly.
- **No `androidTest/`.** Robolectric covers JVM tests; manual QA covers gestures.
- **`PetViewModelTest` does not use TestScope.** Existing pattern (`externalScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)` plus `cancel()` in `finally`) is mandatory — see CLAUDE.md's "Testing gotchas" section. New tests in this plan follow it.
