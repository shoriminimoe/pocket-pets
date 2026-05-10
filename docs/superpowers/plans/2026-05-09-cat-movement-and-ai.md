# Cat Movement & Light AI Implementation Plan (Phase 2)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the cat actually walk around the room — random idle wander as a base, with mood-driven destinations (SLEEPY → bed, HUNGRY → bowl) — by adding a pure-Kotlin `CatBehavior` state machine, swapping to a walk-capable LPC sprite, and offsetting the existing sprite Box from a `MutableStateFlow<CatBehavior>` ticked every frame.

**Architecture:** Pure-function `CatBehaviorRules.tick()` advances `(state, position, target, facing, nextWanderAt)` deterministically. ViewModel owns the `MutableStateFlow<CatBehavior>` and runs a Compose-driven 60 FPS ticker that pauses with `WhileSubscribed`. Screen reports its measured floor bounds + bowl/bed anchors back to the ViewModel and reads the position via `Modifier.offset`. The Phase 1 sprite renderer's `facing` parameter handles directional walks — no renderer changes needed.

**Tech Stack:** Kotlin 2.2.0, Jetpack Compose (BOM 2026.05.00), AGP 8.9.3, JDK 17, JUnit 4 + Truth + Robolectric for the existing test conventions, Pillow + numpy for the asset fetch/repack tooling.

**Reference spec:** `docs/superpowers/specs/2026-05-09-cat-movement-and-ai-design.md` — read it before starting. Numerical constants (speed, wander interval, arrival epsilon, mood thresholds) come from there; do not invent new values.

**Out-of-scope reminder:** Don't touch `Pet`, `PetStats`, `Mood`, `StatDecay`, `PetRepository`, `PetCareWorker`, settings, notifications, work scheduling, or the Feed/Clean/Pet/Talk button row. The drag-and-drop tray and toy/scoop/feed-bag interactions are Phase 3.

**File structure (target end state):**

```
app/src/main/kotlin/com/pocketpets/app/domain/behavior/
├── CatState.kt              # NEW — enum
├── Position.kt              # NEW — value class
├── HabitatBounds.kt         # NEW — value class with clamp/init invariants
├── Anchors.kt               # NEW — bed + bowl positions
├── CatBehavior.kt           # NEW — state snapshot
└── CatBehaviorRules.kt      # NEW — pure tick/directionOf/pickTarget

app/src/main/kotlin/com/pocketpets/app/ui/pet/
├── CatAnimations.kt         # MODIFIED — forState(state) replaces forMood
├── PetViewModel.kt          # MODIFIED — owns CatBehavior + frame ticker
└── PetScreen.kt             # MODIFIED — Modifier.offset + onSizeChanged + anchors

app/src/main/res/drawable-nodpi/
└── cat.png                  # MODIFIED — repacked from new LPC source

tools/
└── fetch_cat_sprites.py     # MODIFIED — new LPC candidate + repack_lpc()

ATTRIBUTION.md               # NEW (or modified if it already exists)

app/src/test/kotlin/com/pocketpets/app/domain/behavior/
├── CatBehaviorRulesTest.kt  # NEW — bulk of the spec coverage
└── HabitatBoundsTest.kt     # NEW — invariants + clamp

app/src/test/kotlin/com/pocketpets/app/ui/pet/
├── CatAnimationsTest.kt     # MODIFIED — keys off CatState now
└── PetViewModelTest.kt      # MODIFIED — extends with behavior assertions
```

---

## Conventions

- **Toolchain prep:** before any task, set `JAVA_HOME=$HOME/.local/jdk` and `ANDROID_HOME=$HOME/Android/Sdk` and put `$JAVA_HOME/bin` on `PATH`. (Re-source `/tmp/env.sh` if it's still there.)
- **Tests:** JUnit 4 + Truth + Robolectric, all on JVM via `./gradlew test` or `:app:testDebugUnitTest`. No `androidTest/` source set.
- **After every task:** run `./gradlew ktlintCheck` and the relevant test slice. Commit only if green. Auto-fix is `./gradlew ktlintFormat`.
- **Conventional-commit prefix:** `feat:`, `refactor:`, `chore:`, `test:`, `docs:`, `fix:`. Used by release-please for the next version bump.
- **No `--no-verify`, no rule disables.** The Phase 1 lint policy is locked in (`abortOnError=true`, `warningsAsErrors=true`, only `AndroidGradlePluginVersion` disabled).

---

## Task 1: Extend `tools/fetch_cat_sprites.py` with an LPC candidate

**Files:**
- Modify: `tools/fetch_cat_sprites.py`

This task only modifies the script — running it (with potentially-new bytes coming back from the network) happens in Task 2 so a human can confirm the SHA before committing the new asset.

- [ ] **Step 1: Add LPC candidate to the candidate list**

Open `tools/fetch_cat_sprites.py`. Find the `CANDIDATES = [...]` block. Insert a new entry **at the top** of the list so it's tried first:

```python
PINNED_SHA_LPC = None  # set this after the first run prints the digest

CANDIDATES = [
    (
        "LPC cat (universal-LPC-spritesheet)",
        "https://opengameart.org/sites/default/files/cat_4.png",
        "CC-BY-SA-3.0",
        PINNED_SHA_LPC,
    ),
    # … existing entries unchanged …
]
```

The Surt entry stays as a fallback. Don't delete it — if LPC's URL has rotted, the fetch falls through to Surt and we'd need a new repack path; the fallback is for "asset still produces a valid cat" not "preserves the visual look".

- [ ] **Step 2: Add a `repack_lpc` function**

Above the existing `repack_surt`, add:

```python
def repack_lpc(raw: Image.Image) -> Image.Image:
    """Repack an LPC-style cat sheet into the canonical 4-cols x 6-rows grid:
        row 0..3: walk S/N/W/E (4 frames each, 64x64)
        row 4:    sit (col 0)
        row 5:    lay (col 0)

    LPC sheets typically lay out a character as 13 rows x N cols at 64x64
    (cast/thrust/walk/shoot/hurt etc.) — the cat sheet is a stripped subset.
    Adjust SOURCE_REGIONS below after inspecting the actual sheet's layout in
    Task 2; do not assume the canonical LPC row order without verifying.
    """
    # Source coords are (x0, y0, x1, y1) in the raw sheet. Each region is
    # exactly 4*frameW wide and 1*frameH tall. Confirm in Task 2 before pinning.
    frameW, frameH = 64, 64
    cell = frameW
    cols = 4
    rows = 6

    # PLACEHOLDER coordinates — Task 2 inspects the real sheet and updates these.
    # Set all six to the same dummy region so the script runs end-to-end; Task 2
    # is responsible for replacing them with the right boxes.
    walk_s = (0,           0,          frameW * cols, frameH)
    walk_n = (0, frameH * 1,           frameW * cols, frameH * 2)
    walk_w = (0, frameH * 2,           frameW * cols, frameH * 3)
    walk_e = (0, frameH * 3,           frameW * cols, frameH * 4)
    sit    = (0, frameH * 4,           frameW,        frameH * 5)
    lay    = (0, frameH * 5,           frameW,        frameH * 6)

    out = Image.new("RGBA", (cols * cell, rows * cell), (0, 0, 0, 0))

    def paste_strip(box, dest_row):
        strip = raw.crop(box)
        # Paste verbatim so each frame lands in its column.
        out.paste(strip, (0, dest_row * cell), strip)

    paste_strip(walk_s, 0)
    paste_strip(walk_n, 1)
    paste_strip(walk_w, 2)
    paste_strip(walk_e, 3)
    paste_strip(sit, 4)
    paste_strip(lay, 5)
    return out
```

- [ ] **Step 3: Wire `repack_lpc` into `process_chosen`**

Find `process_chosen` and add a branch:

```python
def process_chosen(name: str, raw_bytes: bytes) -> Image.Image:
    raw = Image.open(io.BytesIO(raw_bytes)).convert("RGBA")
    if name.startswith("LPC cat"):
        return repack_lpc(raw)
    if name == "Cat by Surt":
        return repack_surt(raw)
    return raw
```

- [ ] **Step 4: Commit**

```bash
git add tools/fetch_cat_sprites.py
git commit -m "feat: add LPC cat candidate to fetch_cat_sprites.py with repack stub"
```

---

## Task 2: Fetch + inspect + lock SOURCE_REGIONS + pin SHA

This is **discovery, not coding**. The exact LPC sheet layout determines the source regions in `repack_lpc`.

- [ ] **Step 1: Run the fetch script (it will fail SHA verification because PINNED_SHA_LPC is None)**

```bash
uv run tools/fetch_cat_sprites.py
```

Expected first output: prints the digest of the LPC candidate's bytes, then either succeeds (if expected was None — the script accepts unverified bytes when expected is None per the existing flow) and proceeds to repack, OR proceeds to the Surt fallback. Either way, the printed `Raw SHA256:` line for the LPC entry is what you pin in Step 3.

If LPC's URL `cat_4.png` returns 404 or a non-cat image, search OpenGameArt for "LPC cat" filtered by CC-BY-SA-3.0 / OGA-BY-3.0, replace the URL in the candidate entry, and retry.

- [ ] **Step 2: Inspect the raw LPC sheet to determine actual cell layout**

The repack stub assumes the sheet is exactly 4 cols × 6 rows of 64×64. LPC sheets in practice often have more rows (cast / thrust / walk / shoot / hurt) and the cat may be a stripped subset. Open the raw download in any image viewer:

```bash
# If the script saved the raw bytes somewhere, view them. If not:
uv run --with Pillow python - <<'PY'
from PIL import Image
img = Image.open("app/src/main/res/drawable-nodpi/cat.png")  # currently the repacked output
print("repacked size:", img.size)
PY
```

You also want the **raw** sheet, not the repacked one. Modify the script temporarily (or download the URL with `curl -o /tmp/lpc_raw.png "$URL"`) to save the raw image somewhere you can inspect.

For the typical LPC convention, walk-S is row 8 (rows 0–7 are cast/thrust). Confirm by looking at the raw image. Update the `walk_s/n/w/e/sit/lay` tuples in `repack_lpc` with the right (x0, y0, x1, y1) ranges.

- [ ] **Step 3: Pin the SHA in `tools/fetch_cat_sprites.py`**

Once you've confirmed the bytes you got are the asset you want, replace the `None` in `PINNED_SHA_LPC` with the digest the script printed in Step 1. Subsequent runs will hard-fail on a different upstream.

- [ ] **Step 4: Re-run the fetch script and verify the repacked output**

```bash
uv run tools/fetch_cat_sprites.py
```

Expected: prints `OK: LPC cat (universal-LPC-spritesheet)` with the pinned SHA matching, repacked dimensions of `256x384` (4 cols × 6 rows × 64 px), and writes `app/src/main/res/drawable-nodpi/cat.png`.

Verify visually:

```bash
uv run --with Pillow python - <<'PY'
from PIL import Image
img = Image.open("app/src/main/res/drawable-nodpi/cat.png")
print("repacked:", img.size)
# Expect (256, 384). Each row should hold the right pose; eyeball it.
img.show()  # or save a side-by-side preview
PY
```

If a row contains the wrong pose, fix the corresponding `(x0, y0, x1, y1)` in `repack_lpc` and re-run.

- [ ] **Step 5: Add or update `ATTRIBUTION.md`**

LPC is CC-BY-SA-3.0 / OGA-BY-3.0; attribution is required.

```bash
test -f ATTRIBUTION.md || cat > ATTRIBUTION.md <<'EOF'
# Third-Party Asset Attribution

| Asset | URL | Author | License |
|-------|-----|--------|---------|
EOF
```

Then append (or edit in) the LPC entry:

```markdown
| LPC cat (universal-LPC-spritesheet) | https://opengameart.org/content/lpc-cat | <author from OGA page> | CC-BY-SA-3.0 / OGA-BY-3.0 |
```

The author and exact title come from the OpenGameArt page for the asset; copy them faithfully.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/res/drawable-nodpi/cat.png tools/fetch_cat_sprites.py ATTRIBUTION.md
git commit -m "feat: swap to LPC cat sprite (CC-BY-SA-3.0) with 4-direction walk"
```

---

## Task 3: `CatState` enum

**Files:**
- Create: `app/src/main/kotlin/com/pocketpets/app/domain/behavior/CatState.kt`

- [ ] **Step 1: Write the file**

```kotlin
package com.pocketpets.app.domain.behavior

/** High-level cat behaviour state. Drives sprite selection and tick semantics. */
enum class CatState { Idle, Walking, Lying }
```

- [ ] **Step 2: Build to confirm**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/pocketpets/app/domain/behavior/CatState.kt
git commit -m "feat: add CatState enum (Idle/Walking/Lying)"
```

---

## Task 4: `Position` value class

**Files:**
- Create: `app/src/main/kotlin/com/pocketpets/app/domain/behavior/Position.kt`

- [ ] **Step 1: Write the file**

```kotlin
package com.pocketpets.app.domain.behavior

/**
 * 2D point in habitat-relative dp. (0,0) is the top-left of the floor area;
 * positive x grows rightward, positive y downward (matching Compose).
 */
data class Position(val x: Float, val y: Float)
```

- [ ] **Step 2: Build, commit**

```bash
./gradlew :app:compileDebugKotlin
git add app/src/main/kotlin/com/pocketpets/app/domain/behavior/Position.kt
git commit -m "feat: add Position 2D value class for cat coordinates"
```

---

## Task 5: `HabitatBounds` with `init` invariant + `clamp` (TDD)

**Files:**
- Create: `app/src/main/kotlin/com/pocketpets/app/domain/behavior/HabitatBounds.kt`
- Create: `app/src/test/kotlin/com/pocketpets/app/domain/behavior/HabitatBoundsTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.pocketpets.app.domain.behavior

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HabitatBoundsTest {
    private val b = HabitatBounds(minX = 0f, minY = 0f, maxX = 200f, maxY = 100f)

    @Test fun `clamps x and y inside`() {
        val p = b.clamp(Position(50f, 50f))
        assertThat(p).isEqualTo(Position(50f, 50f))
    }

    @Test fun `clamps below min`() {
        val p = b.clamp(Position(-10f, -5f))
        assertThat(p).isEqualTo(Position(0f, 0f))
    }

    @Test fun `clamps above max`() {
        val p = b.clamp(Position(300f, 999f))
        assertThat(p).isEqualTo(Position(200f, 100f))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects empty bounds`() {
        HabitatBounds(minX = 100f, minY = 0f, maxX = 100f, maxY = 200f)
    }
}
```

- [ ] **Step 2: Run, expect failure**

```bash
./gradlew :app:testDebugUnitTest --tests "com.pocketpets.app.domain.behavior.HabitatBoundsTest"
```

Expected: FAIL with "unresolved reference: HabitatBounds".

- [ ] **Step 3: Implement**

```kotlin
package com.pocketpets.app.domain.behavior

/**
 * Axis-aligned rectangle of dp coordinates that the cat is allowed to occupy.
 * Half-open at the upper bound is fine — `clamp` uses inclusive `coerceIn`
 * because we want positions to land exactly on the wall when clamped.
 */
data class HabitatBounds(
    val minX: Float,
    val minY: Float,
    val maxX: Float,
    val maxY: Float,
) {
    init {
        require(minX < maxX) { "empty bounds: minX=$minX must be < maxX=$maxX" }
        require(minY < maxY) { "empty bounds: minY=$minY must be < maxY=$maxY" }
    }

    fun clamp(p: Position): Position =
        Position(p.x.coerceIn(minX, maxX), p.y.coerceIn(minY, maxY))
}
```

- [ ] **Step 4: Run, expect pass**

```bash
./gradlew :app:testDebugUnitTest --tests "com.pocketpets.app.domain.behavior.HabitatBoundsTest"
```

Expected: PASS, 4 tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/pocketpets/app/domain/behavior/HabitatBounds.kt \
        app/src/test/kotlin/com/pocketpets/app/domain/behavior/HabitatBoundsTest.kt
git commit -m "feat: add HabitatBounds with clamp + empty-bounds invariant"
```

---

## Task 6: `Anchors` and `CatBehavior` data classes

**Files:**
- Create: `app/src/main/kotlin/com/pocketpets/app/domain/behavior/Anchors.kt`
- Create: `app/src/main/kotlin/com/pocketpets/app/domain/behavior/CatBehavior.kt`

These are simple data carriers; no separate tests needed beyond what `CatBehaviorRulesTest` (Task 8) will cover.

- [ ] **Step 1: Write `Anchors.kt`**

```kotlin
package com.pocketpets.app.domain.behavior

/** Fixed habitat positions the cat can navigate to deliberately. */
data class Anchors(val bed: Position, val bowl: Position)
```

- [ ] **Step 2: Write `CatBehavior.kt`**

```kotlin
package com.pocketpets.app.domain.behavior

import com.pocketpets.app.ui.sprite.Direction
import kotlinx.datetime.Instant

/**
 * Snapshot of the cat's behaviour state. Mutations go through
 * [CatBehaviorRules.tick] only.
 */
data class CatBehavior(
    val state: CatState,
    val position: Position,
    val target: Position,
    val facing: Direction,
    val nextWanderAt: Instant,
)
```

- [ ] **Step 3: Build**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/pocketpets/app/domain/behavior/Anchors.kt \
        app/src/main/kotlin/com/pocketpets/app/domain/behavior/CatBehavior.kt
git commit -m "feat: add Anchors + CatBehavior data classes"
```

---

## Task 7: `CatBehaviorRules.directionOf` (TDD)

**Files:**
- Create: `app/src/main/kotlin/com/pocketpets/app/domain/behavior/CatBehaviorRules.kt`
- Create: `app/src/test/kotlin/com/pocketpets/app/domain/behavior/CatBehaviorRulesTest.kt`

We build `CatBehaviorRules` incrementally. This task adds only `directionOf`. Subsequent tasks add `nextWanderInstant`, `pickTarget`, and `tick`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.pocketpets.app.domain.behavior

import com.google.common.truth.Truth.assertThat
import com.pocketpets.app.ui.sprite.Direction
import org.junit.Test

class CatBehaviorRulesTest {
    @Test fun `direction east when target is purely right`() {
        val d = CatBehaviorRules.directionOf(Position(0f, 0f), Position(10f, 0f))
        assertThat(d).isEqualTo(Direction.EAST)
    }

    @Test fun `direction west when target is purely left`() {
        assertThat(CatBehaviorRules.directionOf(Position(0f, 0f), Position(-10f, 0f)))
            .isEqualTo(Direction.WEST)
    }

    @Test fun `direction north when target is purely up`() {
        assertThat(CatBehaviorRules.directionOf(Position(0f, 0f), Position(0f, -10f)))
            .isEqualTo(Direction.NORTH)
    }

    @Test fun `direction south when target is purely down`() {
        assertThat(CatBehaviorRules.directionOf(Position(0f, 0f), Position(0f, 10f)))
            .isEqualTo(Direction.SOUTH)
    }

    @Test fun `tie favours vertical axis`() {
        // |dx| == |dy|; spec says vertical wins
        assertThat(CatBehaviorRules.directionOf(Position(0f, 0f), Position(10f, 10f)))
            .isEqualTo(Direction.SOUTH)
        assertThat(CatBehaviorRules.directionOf(Position(0f, 0f), Position(-10f, -10f)))
            .isEqualTo(Direction.NORTH)
    }

    @Test fun `equal positions return SOUTH as default`() {
        assertThat(CatBehaviorRules.directionOf(Position(5f, 5f), Position(5f, 5f)))
            .isEqualTo(Direction.SOUTH)
    }
}
```

- [ ] **Step 2: Run, expect failure**

```bash
./gradlew :app:testDebugUnitTest --tests "com.pocketpets.app.domain.behavior.CatBehaviorRulesTest"
```

Expected: FAIL with "unresolved reference: CatBehaviorRules".

- [ ] **Step 3: Implement minimal `CatBehaviorRules` with just `directionOf`**

```kotlin
package com.pocketpets.app.domain.behavior

import com.pocketpets.app.ui.sprite.Direction
import kotlin.math.abs

/**
 * Pure transitions for the cat behaviour state machine. All public functions
 * are deterministic given their inputs (Random must be seeded to be
 * deterministic).
 */
object CatBehaviorRules {
    const val DEFAULT_SPEED_DP_PER_SEC = 60f
    const val ARRIVAL_EPSILON_DP = 2f
    const val MIN_WANDER_SECONDS = 30L
    const val MAX_WANDER_SECONDS = 60L

    /**
     * Direction the cat would face if walking from [from] to [to]. When |dx| == |dy|
     * (45° diagonal or no movement), the vertical axis wins; equal positions return
     * SOUTH as the documented default so the renderer always has a valid facing.
     */
    fun directionOf(from: Position, to: Position): Direction {
        val dx = to.x - from.x
        val dy = to.y - from.y
        return if (abs(dy) >= abs(dx)) {
            if (dy < 0f) Direction.NORTH else Direction.SOUTH
        } else {
            if (dx < 0f) Direction.WEST else Direction.EAST
        }
    }
}
```

- [ ] **Step 4: Run, expect pass**

```bash
./gradlew :app:testDebugUnitTest --tests "com.pocketpets.app.domain.behavior.CatBehaviorRulesTest"
```

Expected: PASS, 7 tests (the diagonal test asserts twice).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/pocketpets/app/domain/behavior/CatBehaviorRules.kt \
        app/src/test/kotlin/com/pocketpets/app/domain/behavior/CatBehaviorRulesTest.kt
git commit -m "feat: add CatBehaviorRules.directionOf with tiebreak rule"
```

---

## Task 8: `CatBehaviorRules.nextWanderInstant` (TDD)

**Files:**
- Modify: `app/src/main/kotlin/com/pocketpets/app/domain/behavior/CatBehaviorRules.kt`
- Modify: `app/src/test/kotlin/com/pocketpets/app/domain/behavior/CatBehaviorRulesTest.kt`

- [ ] **Step 1: Add the failing test**

Append to `CatBehaviorRulesTest`:

```kotlin
    @Test fun `next wander instant is in the configured window`() {
        val now = kotlinx.datetime.Instant.parse("2026-05-09T12:00:00Z")
        repeat(50) { seed ->
            val next = CatBehaviorRules.nextWanderInstant(now, kotlin.random.Random(seed.toLong()))
            val deltaSec = (next.toEpochMilliseconds() - now.toEpochMilliseconds()) / 1000L
            assertThat(deltaSec)
                .isIn(com.google.common.collect.Range.closed(
                    CatBehaviorRules.MIN_WANDER_SECONDS,
                    CatBehaviorRules.MAX_WANDER_SECONDS,
                ))
        }
    }
```

- [ ] **Step 2: Run, expect failure**

```bash
./gradlew :app:testDebugUnitTest --tests "com.pocketpets.app.domain.behavior.CatBehaviorRulesTest"
```

Expected: FAIL with "unresolved reference: nextWanderInstant".

- [ ] **Step 3: Implement**

Add to `CatBehaviorRules`:

```kotlin
    /**
     * Returns an Instant in `[now + MIN_WANDER_SECONDS, now + MAX_WANDER_SECONDS]`
     * (inclusive on both ends). Uses [rng] for jitter; deterministic given a
     * seeded Random.
     */
    fun nextWanderInstant(now: kotlinx.datetime.Instant, rng: kotlin.random.Random): kotlinx.datetime.Instant {
        val deltaSec = rng.nextLong(MIN_WANDER_SECONDS, MAX_WANDER_SECONDS + 1)
        return kotlinx.datetime.Instant.fromEpochMilliseconds(
            now.toEpochMilliseconds() + deltaSec * 1000L
        )
    }
```

(Top-of-file imports: add `import kotlinx.datetime.Instant` and `import kotlin.random.Random` to keep the body clean. Adjust the function signatures accordingly. The fully-qualified names above work either way.)

- [ ] **Step 4: Run, expect pass**

```bash
./gradlew :app:testDebugUnitTest --tests "com.pocketpets.app.domain.behavior.CatBehaviorRulesTest"
```

Expected: PASS, 8 tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/pocketpets/app/domain/behavior/CatBehaviorRules.kt \
        app/src/test/kotlin/com/pocketpets/app/domain/behavior/CatBehaviorRulesTest.kt
git commit -m "feat: add CatBehaviorRules.nextWanderInstant with bounded jitter"
```

---

## Task 9: `CatBehaviorRules.pickTarget` (TDD)

**Files:**
- Modify: `app/src/main/kotlin/com/pocketpets/app/domain/behavior/CatBehaviorRules.kt`
- Modify: `app/src/test/kotlin/com/pocketpets/app/domain/behavior/CatBehaviorRulesTest.kt`

- [ ] **Step 1: Add the failing tests**

Append:

```kotlin
    private val bounds = HabitatBounds(0f, 0f, 200f, 100f)
    private val anchors = Anchors(bed = Position(180f, 80f), bowl = Position(20f, 80f))

    @Test fun `pickTarget chooses bed when sleepy`() {
        val t = CatBehaviorRules.pickTarget(
            mood = com.pocketpets.app.domain.Mood.SLEEPY,
            bounds = bounds, anchors = anchors,
            rng = kotlin.random.Random(0),
        )
        assertThat(t).isEqualTo(anchors.bed)
    }

    @Test fun `pickTarget chooses bowl when hungry`() {
        val t = CatBehaviorRules.pickTarget(
            mood = com.pocketpets.app.domain.Mood.HUNGRY,
            bounds = bounds, anchors = anchors,
            rng = kotlin.random.Random(0),
        )
        assertThat(t).isEqualTo(anchors.bowl)
    }

    @Test fun `pickTarget returns a point inside bounds for non-sleepy non-hungry moods`() {
        val nonAnchorMoods = listOf(
            com.pocketpets.app.domain.Mood.IDLE,
            com.pocketpets.app.domain.Mood.HAPPY,
            com.pocketpets.app.domain.Mood.SAD,
            com.pocketpets.app.domain.Mood.GROSSED_OUT,
        )
        for (mood in nonAnchorMoods) {
            for (seed in 0..20) {
                val t = CatBehaviorRules.pickTarget(
                    mood = mood,
                    bounds = bounds, anchors = anchors,
                    rng = kotlin.random.Random(seed.toLong()),
                )
                assertThat(t.x).isAtLeast(bounds.minX)
                assertThat(t.x).isAtMost(bounds.maxX)
                assertThat(t.y).isAtLeast(bounds.minY)
                assertThat(t.y).isAtMost(bounds.maxY)
            }
        }
    }

    @Test fun `pickTarget is deterministic given the same seed`() {
        val a = CatBehaviorRules.pickTarget(
            com.pocketpets.app.domain.Mood.IDLE, bounds, anchors,
            kotlin.random.Random(42),
        )
        val b = CatBehaviorRules.pickTarget(
            com.pocketpets.app.domain.Mood.IDLE, bounds, anchors,
            kotlin.random.Random(42),
        )
        assertThat(a).isEqualTo(b)
    }
```

- [ ] **Step 2: Run, expect failure**

```bash
./gradlew :app:testDebugUnitTest --tests "com.pocketpets.app.domain.behavior.CatBehaviorRulesTest"
```

Expected: FAIL with "unresolved reference: pickTarget".

- [ ] **Step 3: Implement**

Add to `CatBehaviorRules`:

```kotlin
    /**
     * Returns the target the cat should walk to next given current [mood].
     * Mood-driven destinations take priority; otherwise picks a uniform-random
     * point inside [bounds].
     */
    fun pickTarget(
        mood: com.pocketpets.app.domain.Mood,
        bounds: HabitatBounds,
        anchors: Anchors,
        rng: kotlin.random.Random,
    ): Position = when (mood) {
        com.pocketpets.app.domain.Mood.SLEEPY -> anchors.bed
        com.pocketpets.app.domain.Mood.HUNGRY -> anchors.bowl
        else -> Position(
            x = rng.nextFloat(bounds.minX, bounds.maxX),
            y = rng.nextFloat(bounds.minY, bounds.maxY),
        )
    }

    private fun kotlin.random.Random.nextFloat(min: Float, max: Float): Float =
        min + nextFloat() * (max - min)
```

If the `private extension on kotlin.random.Random` isn't allowed inside an `object`, hoist it to a top-level `private` function in the same file:

```kotlin
private fun kotlin.random.Random.nextFloat(min: Float, max: Float): Float =
    min + this.nextFloat() * (max - min)
```

- [ ] **Step 4: Run, expect pass**

```bash
./gradlew :app:testDebugUnitTest --tests "com.pocketpets.app.domain.behavior.CatBehaviorRulesTest"
```

Expected: PASS, 12 tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/pocketpets/app/domain/behavior/CatBehaviorRules.kt \
        app/src/test/kotlin/com/pocketpets/app/domain/behavior/CatBehaviorRulesTest.kt
git commit -m "feat: add CatBehaviorRules.pickTarget with mood priority"
```

---

## Task 10: `CatBehaviorRules.tick` (TDD)

The largest single task in the plan. Tests cover position advancement, arrival behaviour, mood-driven retargeting, scheduling, and edge cases.

**Files:**
- Modify: `app/src/main/kotlin/com/pocketpets/app/domain/behavior/CatBehaviorRules.kt`
- Modify: `app/src/test/kotlin/com/pocketpets/app/domain/behavior/CatBehaviorRulesTest.kt`

- [ ] **Step 1: Add the failing tests**

Append to `CatBehaviorRulesTest`:

```kotlin
    private val t0 = kotlinx.datetime.Instant.parse("2026-05-09T12:00:00Z")

    private fun behavior(
        state: CatState = CatState.Idle,
        x: Float = 100f, y: Float = 50f,
        targetX: Float = 100f, targetY: Float = 50f,
        facing: com.pocketpets.app.ui.sprite.Direction = com.pocketpets.app.ui.sprite.Direction.SOUTH,
        nextWanderAt: kotlinx.datetime.Instant = t0.plusSeconds(45),
    ) = CatBehavior(
        state = state,
        position = Position(x, y),
        target = Position(targetX, targetY),
        facing = facing,
        nextWanderAt = nextWanderAt,
    )

    private fun kotlinx.datetime.Instant.plusSeconds(s: Long) =
        kotlinx.datetime.Instant.fromEpochMilliseconds(toEpochMilliseconds() + s * 1000)

    @Test fun `tick with dt=0 is a no-op`() {
        val b = behavior(state = CatState.Walking, x = 0f, targetX = 100f)
        val out = CatBehaviorRules.tick(
            b = b, now = t0, dtSeconds = 0f,
            mood = com.pocketpets.app.domain.Mood.IDLE,
            bounds = bounds, anchors = anchors,
            rng = kotlin.random.Random(0),
        )
        assertThat(out.position).isEqualTo(b.position)
        assertThat(out.state).isEqualTo(CatState.Walking)
    }

    @Test fun `walking cat advances toward target by speed times dt`() {
        val b = behavior(state = CatState.Walking, x = 0f, y = 0f, targetX = 100f, targetY = 0f)
        val out = CatBehaviorRules.tick(
            b = b, now = t0, dtSeconds = 1f,
            mood = com.pocketpets.app.domain.Mood.IDLE,
            bounds = bounds, anchors = anchors,
            rng = kotlin.random.Random(0),
            speedDpPerSec = 60f,
        )
        assertThat(out.position.x).isWithin(0.01f).of(60f)
        assertThat(out.position.y).isEqualTo(0f)
        assertThat(out.state).isEqualTo(CatState.Walking)
        assertThat(out.facing).isEqualTo(com.pocketpets.app.ui.sprite.Direction.EAST)
    }

    @Test fun `walking cat clamps to target when next step would overshoot`() {
        val b = behavior(state = CatState.Walking, x = 95f, y = 0f, targetX = 100f, targetY = 0f)
        val out = CatBehaviorRules.tick(
            b = b, now = t0, dtSeconds = 1f,
            mood = com.pocketpets.app.domain.Mood.IDLE,
            bounds = bounds, anchors = anchors,
            rng = kotlin.random.Random(0),
            speedDpPerSec = 60f,
        )
        assertThat(out.position).isEqualTo(Position(100f, 0f))
    }

    @Test fun `walking cat that arrives at non-bed target becomes Idle and reschedules wander`() {
        val arrivedAt = Position(40f, 40f)  // not the bed
        val b = behavior(state = CatState.Walking, x = arrivedAt.x, y = arrivedAt.y,
                         targetX = arrivedAt.x, targetY = arrivedAt.y)
        val out = CatBehaviorRules.tick(
            b = b, now = t0, dtSeconds = 0.1f,
            mood = com.pocketpets.app.domain.Mood.IDLE,
            bounds = bounds, anchors = anchors,
            rng = kotlin.random.Random(0),
        )
        assertThat(out.state).isEqualTo(CatState.Idle)
        // Reschedule landed in the future, within the wander window.
        val deltaSec = (out.nextWanderAt.toEpochMilliseconds() - t0.toEpochMilliseconds()) / 1000L
        assertThat(deltaSec).isAtLeast(CatBehaviorRules.MIN_WANDER_SECONDS)
        assertThat(deltaSec).isAtMost(CatBehaviorRules.MAX_WANDER_SECONDS)
    }

    @Test fun `walking cat that arrives at bed becomes Lying`() {
        val b = behavior(state = CatState.Walking, x = anchors.bed.x, y = anchors.bed.y,
                         targetX = anchors.bed.x, targetY = anchors.bed.y)
        val out = CatBehaviorRules.tick(
            b = b, now = t0, dtSeconds = 0.1f,
            mood = com.pocketpets.app.domain.Mood.SLEEPY,
            bounds = bounds, anchors = anchors,
            rng = kotlin.random.Random(0),
        )
        assertThat(out.state).isEqualTo(CatState.Lying)
    }

    @Test fun `idle cat that becomes sleepy walks toward bed`() {
        val b = behavior(state = CatState.Idle, x = 50f, y = 50f, targetX = 50f, targetY = 50f)
        val out = CatBehaviorRules.tick(
            b = b, now = t0, dtSeconds = 0.016f,
            mood = com.pocketpets.app.domain.Mood.SLEEPY,
            bounds = bounds, anchors = anchors,
            rng = kotlin.random.Random(0),
        )
        assertThat(out.state).isEqualTo(CatState.Walking)
        assertThat(out.target).isEqualTo(anchors.bed)
    }

    @Test fun `idle cat that becomes hungry walks toward bowl`() {
        val b = behavior(state = CatState.Idle, x = 50f, y = 50f, targetX = 50f, targetY = 50f)
        val out = CatBehaviorRules.tick(
            b = b, now = t0, dtSeconds = 0.016f,
            mood = com.pocketpets.app.domain.Mood.HUNGRY,
            bounds = bounds, anchors = anchors,
            rng = kotlin.random.Random(0),
        )
        assertThat(out.state).isEqualTo(CatState.Walking)
        assertThat(out.target).isEqualTo(anchors.bowl)
    }

    @Test fun `idle cat starts wandering when nextWanderAt has passed`() {
        val due = t0.plusSeconds(-1)  // already past
        val b = behavior(state = CatState.Idle, nextWanderAt = due)
        val out = CatBehaviorRules.tick(
            b = b, now = t0, dtSeconds = 0.016f,
            mood = com.pocketpets.app.domain.Mood.IDLE,
            bounds = bounds, anchors = anchors,
            rng = kotlin.random.Random(0),
        )
        assertThat(out.state).isEqualTo(CatState.Walking)
        // Target is some random position inside bounds.
        assertThat(out.target.x).isAtLeast(bounds.minX)
        assertThat(out.target.x).isAtMost(bounds.maxX)
    }

    @Test fun `lying cat wakes up and walks when no longer sleepy`() {
        val b = behavior(state = CatState.Lying, x = anchors.bed.x, y = anchors.bed.y,
                         targetX = anchors.bed.x, targetY = anchors.bed.y)
        val out = CatBehaviorRules.tick(
            b = b, now = t0, dtSeconds = 0.016f,
            mood = com.pocketpets.app.domain.Mood.IDLE,
            bounds = bounds, anchors = anchors,
            rng = kotlin.random.Random(0),
        )
        assertThat(out.state).isEqualTo(CatState.Walking)
    }

    @Test fun `mood flip mid-walk preempts current target`() {
        val b = behavior(state = CatState.Walking, x = 50f, y = 50f, targetX = 100f, targetY = 50f)
        val out = CatBehaviorRules.tick(
            b = b, now = t0, dtSeconds = 0.016f,
            mood = com.pocketpets.app.domain.Mood.SLEEPY,
            bounds = bounds, anchors = anchors,
            rng = kotlin.random.Random(0),
        )
        assertThat(out.target).isEqualTo(anchors.bed)
    }

    @Test fun `huge dt does not teleport past the target`() {
        val b = behavior(state = CatState.Walking, x = 95f, y = 0f, targetX = 100f, targetY = 0f)
        val out = CatBehaviorRules.tick(
            b = b, now = t0, dtSeconds = 100f,
            mood = com.pocketpets.app.domain.Mood.IDLE,
            bounds = bounds, anchors = anchors,
            rng = kotlin.random.Random(0),
        )
        assertThat(out.position).isEqualTo(Position(100f, 0f))
    }
```

- [ ] **Step 2: Run, expect failure**

```bash
./gradlew :app:testDebugUnitTest --tests "com.pocketpets.app.domain.behavior.CatBehaviorRulesTest"
```

Expected: FAIL with "unresolved reference: tick".

- [ ] **Step 3: Implement `tick`**

Add to `CatBehaviorRules`:

```kotlin
    /**
     * Pure forward step. Given the current [b]ehavior, produces the next one.
     * The function is the only mutator of [CatBehavior] in the codebase; the
     * ViewModel calls it on a frame ticker and stores the result in a flow.
     */
    fun tick(
        b: CatBehavior,
        now: kotlinx.datetime.Instant,
        dtSeconds: Float,
        mood: com.pocketpets.app.domain.Mood,
        bounds: HabitatBounds,
        anchors: Anchors,
        rng: kotlin.random.Random,
        speedDpPerSec: Float = DEFAULT_SPEED_DP_PER_SEC,
    ): CatBehavior {
        if (dtSeconds <= 0f) return b

        // 1. Decide whether the current target is still valid; mood-driven
        //    moves preempt the current target when the new mood selects a
        //    different anchor.
        val moodTarget = when (mood) {
            com.pocketpets.app.domain.Mood.SLEEPY -> anchors.bed
            com.pocketpets.app.domain.Mood.HUNGRY -> anchors.bowl
            else -> null
        }
        val effectiveTarget = moodTarget ?: when (b.state) {
            CatState.Walking -> b.target
            CatState.Idle -> if (now >= b.nextWanderAt) {
                pickTarget(mood, bounds, anchors, rng)
            } else {
                b.position  // stay
            }
            CatState.Lying -> b.position  // stay unless mood demands movement (handled above)
        }

        // 2. Lying cat with a mood that wants movement: wake up and walk.
        if (b.state == CatState.Lying && moodTarget != null && moodTarget != b.position) {
            return walkingToward(b, effectiveTarget)
        }

        // 3. Idle cat that needs to start moving (mood-driven OR wander timer fired).
        if (b.state == CatState.Idle && effectiveTarget != b.position) {
            return walkingToward(b, effectiveTarget)
        }

        // 4. Walking cat: advance position, possibly arrive.
        if (b.state == CatState.Walking) {
            val advanced = advance(b.position, effectiveTarget, speedDpPerSec, dtSeconds)
            val arrived = isArrived(advanced, effectiveTarget)
            return when {
                !arrived -> b.copy(
                    position = advanced,
                    target = effectiveTarget,
                    facing = directionOf(b.position, effectiveTarget),
                )
                effectiveTarget == anchors.bed -> b.copy(
                    state = CatState.Lying,
                    position = effectiveTarget,
                    target = effectiveTarget,
                    facing = directionOf(b.position, effectiveTarget),
                )
                else -> b.copy(
                    state = CatState.Idle,
                    position = effectiveTarget,
                    target = effectiveTarget,
                    facing = directionOf(b.position, effectiveTarget),
                    nextWanderAt = nextWanderInstant(now, rng),
                )
            }
        }

        // 5. Idle / Lying with no reason to move: leave it alone.
        return b
    }

    private fun walkingToward(b: CatBehavior, target: Position) = b.copy(
        state = CatState.Walking,
        target = target,
        facing = directionOf(b.position, target),
    )

    private fun advance(from: Position, to: Position, speed: Float, dt: Float): Position {
        val dx = to.x - from.x
        val dy = to.y - from.y
        val dist = kotlin.math.sqrt(dx * dx + dy * dy)
        if (dist <= ARRIVAL_EPSILON_DP) return to
        val maxStep = speed * dt
        if (maxStep >= dist) return to
        val ratio = maxStep / dist
        return Position(from.x + dx * ratio, from.y + dy * ratio)
    }

    private fun isArrived(at: Position, target: Position): Boolean {
        val dx = target.x - at.x
        val dy = target.y - at.y
        return kotlin.math.sqrt(dx * dx + dy * dy) <= ARRIVAL_EPSILON_DP
    }
```

- [ ] **Step 4: Run, expect pass**

```bash
./gradlew :app:testDebugUnitTest --tests "com.pocketpets.app.domain.behavior.CatBehaviorRulesTest"
```

Expected: PASS, 22 tests. If a test fails:
- "no-op when dt=0" — make sure the early return is at the top.
- "advances by speed × dt" — check the `advance` math.
- "lying cat wakes up" — make sure the rule fires on `state == Lying` and `mood != SLEEPY` even when no anchor target applies.
- "mood flip mid-walk" — make sure `effectiveTarget` is recomputed before the walking branch runs.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/pocketpets/app/domain/behavior/CatBehaviorRules.kt \
        app/src/test/kotlin/com/pocketpets/app/domain/behavior/CatBehaviorRulesTest.kt
git commit -m "feat: implement CatBehaviorRules.tick state machine"
```

---

## Task 11: Refactor `CatAnimations` to `forState`

**Files:**
- Modify: `app/src/main/kotlin/com/pocketpets/app/ui/pet/CatAnimations.kt`
- Modify: `app/src/test/kotlin/com/pocketpets/app/ui/pet/CatAnimationsTest.kt`

- [ ] **Step 1: Replace `CatAnimations.kt`**

```kotlin
package com.pocketpets.app.ui.pet

import com.pocketpets.app.R
import com.pocketpets.app.domain.behavior.CatState
import com.pocketpets.app.ui.sprite.SpriteAnimation
import com.pocketpets.app.ui.sprite.SpriteSheet

/**
 * Maps each [CatState] to a [SpriteAnimation] on the bundled cat sprite sheet.
 *
 * Sheet layout (after Phase 2 LPC swap, repacked by tools/fetch_cat_sprites.py):
 *  - row 0..3: walk S/N/W/E (4 frames each, 64x64 cells)
 *  - row 4:    sit (col 0)
 *  - row 5:    lay (col 0)
 *
 * The renderer's `facing` parameter adds row offsets 0/1/2/3 for SOUTH/NORTH/
 * WEST/EAST, so a single `walk` SpriteAnimation on row 0 becomes the directional
 * walk for free when AnimatedSprite is called with the right facing.
 */
object CatAnimations {
    private val sheet = SpriteSheet(
        resId = R.drawable.cat,
        frameWidth = 64,
        frameHeight = 64,
        rows = 6,
        cols = 4,
    )

    val walk: SpriteAnimation = SpriteAnimation(sheet, row = 0, frameCount = 4, frameMs = 120, loop = true)
    val sit: SpriteAnimation = SpriteAnimation(sheet, row = 4, frameCount = 1)
    val lay: SpriteAnimation = SpriteAnimation(sheet, row = 5, frameCount = 1)

    fun forState(state: CatState): SpriteAnimation = when (state) {
        CatState.Walking -> walk
        CatState.Idle -> sit
        CatState.Lying -> lay
    }
}
```

- [ ] **Step 2: Update the test**

Replace `CatAnimationsTest.kt`:

```kotlin
package com.pocketpets.app.ui.pet

import com.google.common.truth.Truth.assertThat
import com.pocketpets.app.domain.behavior.CatState
import org.junit.Test

class CatAnimationsTest {
    @Test fun `every state maps to a valid animation`() {
        for (state in CatState.values()) {
            val anim = CatAnimations.forState(state)
            assertThat(anim.frameCount).isGreaterThan(0)
            assertThat(anim.row).isAtLeast(0)
            assertThat(anim.frameMs).isGreaterThan(0L)
        }
    }

    @Test fun `walk uses the walk row and is multi-frame`() {
        val a = CatAnimations.forState(CatState.Walking)
        assertThat(a).isEqualTo(CatAnimations.walk)
        assertThat(a.frameCount).isAtLeast(2)
        assertThat(a.frameMs).isIn(com.google.common.collect.Range.closed(50L, 250L))
    }

    @Test fun `idle uses sit and lying uses lay`() {
        assertThat(CatAnimations.forState(CatState.Idle)).isEqualTo(CatAnimations.sit)
        assertThat(CatAnimations.forState(CatState.Lying)).isEqualTo(CatAnimations.lay)
    }
}
```

- [ ] **Step 3: Run, expect compile errors in `PetScreen.kt`**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: FAIL with "unresolved reference: forMood" because `PetScreen` still calls the old API. We'll fix `PetScreen` in Task 13.

- [ ] **Step 4: Run the new tests in isolation, expect pass**

```bash
./gradlew :app:testDebugUnitTest --tests "com.pocketpets.app.ui.pet.CatAnimationsTest"
```

This test only depends on `CatAnimations` itself; it passes even though the broader app doesn't compile. If it doesn't pass, fix `forState` mappings first before moving on.

- [ ] **Step 5: Commit (broken intermediate state acknowledged)**

```bash
git add app/src/main/kotlin/com/pocketpets/app/ui/pet/CatAnimations.kt \
        app/src/test/kotlin/com/pocketpets/app/ui/pet/CatAnimationsTest.kt
git commit -m "refactor: CatAnimations.forState replaces forMood (PetScreen broken until Task 13)"
```

---

## Task 12: Wire `CatBehavior` into `PetViewModel`

**Files:**
- Modify: `app/src/main/kotlin/com/pocketpets/app/ui/pet/PetViewModel.kt`

This task adds the behavior flow + frame ticker. It does not yet rewire `PetScreen` (Task 13).

- [ ] **Step 1: Read the current PetViewModel**

```bash
sed -n '1,60p' app/src/main/kotlin/com/pocketpets/app/ui/pet/PetViewModel.kt
```

Skim to remember its shape: `state: StateFlow<PetUiState>`, observation of `repo.observeActive()`, `talk()/feed()/clean()/pet()` methods, the chatter ticker.

- [ ] **Step 2: Extend `PetUiState` with the behavior**

In `PetViewModel.kt`, find the `data class PetUiState(...)` and add `behavior`:

```kotlin
import com.pocketpets.app.domain.behavior.Anchors
import com.pocketpets.app.domain.behavior.CatBehavior
import com.pocketpets.app.domain.behavior.CatBehaviorRules
import com.pocketpets.app.domain.behavior.CatState
import com.pocketpets.app.domain.behavior.HabitatBounds
import com.pocketpets.app.domain.behavior.Position
import com.pocketpets.app.ui.sprite.Direction

data class PetUiState(
    val pet: Pet? = null,
    val mood: Mood = Mood.IDLE,
    val stage: GrowthStage = GrowthStage.BABY,
    val activePhrase: Phrase? = null,
    val behavior: CatBehavior? = null,
)
```

- [ ] **Step 3: Add a `behavior` flow and a `setHabitat` method to the ViewModel**

Inside `PetViewModel`:

```kotlin
    private val defaultBounds = HabitatBounds(0f, 0f, 240f, 200f)
    private val defaultAnchors = Anchors(
        bed = Position(180f, 160f),
        bowl = Position(40f, 160f),
    )

    private var habitatBounds: HabitatBounds = defaultBounds
    private var habitatAnchors: Anchors = defaultAnchors
    private var currentMood: Mood = Mood.IDLE

    private val behaviorFlow: MutableStateFlow<CatBehavior> = MutableStateFlow(
        CatBehavior(
            state = CatState.Idle,
            position = Position(120f, 100f),
            target = Position(120f, 100f),
            facing = Direction.SOUTH,
            nextWanderAt = clock.now().plusSeconds(45),
        )
    )

    fun setHabitat(bounds: HabitatBounds, anchors: Anchors) {
        habitatBounds = bounds
        habitatAnchors = anchors
    }

    init {
        // Track latest mood for the frame ticker.
        scope.launch {
            state.collect { ui -> currentMood = ui.mood }
        }
        // Behavior frame ticker — ~60 FPS while subscribed.
        scope.launch {
            var lastFrame = clock.now()
            while (true) {
                kotlinx.coroutines.delay(16)
                val now = clock.now()
                val dtSec = ((now.toEpochMilliseconds() - lastFrame.toEpochMilliseconds()) / 1000f)
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
                    )
                }
            }
        }
    }

    private fun kotlinx.datetime.Instant.plusSeconds(s: Long) =
        kotlinx.datetime.Instant.fromEpochMilliseconds(toEpochMilliseconds() + s * 1000)
```

If the existing `init { ... }` block holds the chatter ticker, append the new launches inside it instead of opening a second `init`. Keep the chatter logic intact.

- [ ] **Step 4: Combine `behaviorFlow` into `state`**

Find the `combine(...)` that produces `state`. Add `behaviorFlow` as a fourth source:

```kotlin
    val state: StateFlow<PetUiState> = combine(
        repo.observeActive(),
        ticker(60_000L),
        currentPhrase,
        behaviorFlow,
    ) { rawPet, _, phrase, behavior ->
        val now = clock.now()
        val ticked = rawPet?.let { StatDecay.tick(it, now) }
        val mood = ticked?.let { Mood.from(it, now, zone) } ?: Mood.IDLE
        val stage = ticked?.let { GrowthStage.fromAge(it.bornAt, now) } ?: GrowthStage.BABY
        PetUiState(ticked, mood, stage, phrase, behavior)
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), PetUiState())
```

- [ ] **Step 5: Build (PetScreen still calls `forMood` — expected failure)**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: still FAIL on `PetScreen.kt:forMood`. We're staging the changes; the next task fixes the screen.

- [ ] **Step 6: Run the existing PetViewModel tests to confirm we didn't break them**

```bash
./gradlew :app:testDebugUnitTest --tests "com.pocketpets.app.ui.pet.PetViewModelTest"
```

The existing `PetViewModelTest` cases use the same fake repo and clock. They should still pass; if any fail because they call `vm.state.first()` and the new `state` shape requires a non-null behavior, update the FakeClock-based tests to wait for `it.behavior != null` instead.

If a regression appears, the most likely cause is the new combine source emitting `null` initially. The default `behaviorFlow` value is non-null, so `it.behavior != null` should be true on first emission.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/com/pocketpets/app/ui/pet/PetViewModel.kt \
        app/src/test/kotlin/com/pocketpets/app/ui/pet/PetViewModelTest.kt
git commit -m "feat: PetViewModel owns CatBehavior + 60 FPS ticker"
```

---

## Task 13: Wire `CatBehavior` into `PetScreen` + report habitat bounds

**Files:**
- Modify: `app/src/main/kotlin/com/pocketpets/app/ui/pet/PetScreen.kt`

- [ ] **Step 1: Replace the sprite-rendering block**

Find the section that currently does:

```kotlin
val animation = CatAnimations.forMood(state.stage, state.mood)
val spriteSize = stageSpriteSize(state.stage)
val breathingScale = rememberBreathingScale()
// ...
AnimatedSprite(animation = animation, ...)
```

Replace with:

```kotlin
val behavior = state.behavior ?: return@Box  // wait until behavior is hydrated
val animation = CatAnimations.forState(behavior.state)
val spriteSize = stageSpriteSize(state.stage)
val breathingScale = rememberBreathingScale()
val applyBreathing = behavior.state != com.pocketpets.app.domain.behavior.CatState.Walking

Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        SpeechBubble(
            phrase = state.activePhrase,
            onDismiss = vm::dismissPhrase,
        )
        Spacer(Modifier.height(8.dp))
        Box(
            modifier =
                Modifier
                    .offset(x = behavior.position.x.dp, y = behavior.position.y.dp)
                    .size(spriteSize)
                    .clickable {
                        vm.talk()
                        vm.pet()
                    },
        ) {
            AnimatedSprite(
                animation = animation,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .let { if (applyBreathing) it.scale(scaleX = 1f, scaleY = breathingScale) else it },
                facing = behavior.facing,
            )
            MoodOverlay(
                mood = state.mood,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
```

Required imports if not already present:

```kotlin
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
```

- [ ] **Step 2: Report habitat bounds + anchors when the floor is laid out**

Find the `Image(painter = painterResource(R.drawable.room_bg), …)` for the background. Add an `onSizeChanged` callback that converts pixel size to dp and calls `vm.setHabitat(bounds, anchors)`. Bowl and bed positions are computed from the floor bounds:

```kotlin
val density = LocalDensity.current
Image(
    painter = painterResource(R.drawable.room_bg),
    contentDescription = null,
    modifier =
        Modifier
            .fillMaxSize()
            .onSizeChanged { sizePx ->
                with(density) {
                    val widthDp = sizePx.width.toDp().value
                    val heightDp = sizePx.height.toDp().value
                    // The floor is the lower 40% of the room background; the cat
                    // walks within it. Numbers picked to keep the cat above the
                    // action button row at the bottom.
                    val floorTopDp = heightDp * 0.40f
                    val floorBottomDp = heightDp * 0.85f
                    val bounds = com.pocketpets.app.domain.behavior.HabitatBounds(
                        minX = 0f,
                        minY = floorTopDp,
                        maxX = widthDp - 64f,  // sprite width
                        maxY = floorBottomDp - 64f,
                    )
                    val anchors = com.pocketpets.app.domain.behavior.Anchors(
                        bed = com.pocketpets.app.domain.behavior.Position(
                            x = widthDp - 64f - 24f,
                            y = floorBottomDp - 64f - 16f,
                        ),
                        bowl = com.pocketpets.app.domain.behavior.Position(
                            x = 24f,
                            y = floorBottomDp - 64f - 16f,
                        ),
                    )
                    vm.setHabitat(bounds, anchors)
                }
            },
    contentScale = ContentScale.FillBounds,
)
```

If the bowl Image is currently positioned at `Modifier.align(Alignment.BottomStart).padding(start = 24.dp, bottom = 100.dp)`, that's where `anchors.bowl` should end up in dp coords. Adjust the constants if the bowl moved. The numbers above are illustrative; verify against the actual bowl location during manual QA.

- [ ] **Step 3: Build to confirm everything compiles**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`. If "unresolved reference: forMood" still appears, search PetScreen.kt for the remaining call site and replace it.

- [ ] **Step 4: Run all tests**

```bash
./gradlew test
```

Expected: `BUILD SUCCESSFUL`. All previously-passing tests still pass (we didn't change any care actions or stat math).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/pocketpets/app/ui/pet/PetScreen.kt
git commit -m "feat: PetScreen renders cat at behavior.position with directional walk"
```

---

## Task 14: Final verification + manual QA prep

- [ ] **Step 1: Full local check**

```bash
./gradlew ktlintCheck :app:lintDebug test :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`. If ktlint complains, run `./gradlew ktlintFormat` and re-run, then commit the format-only changes as `chore: ktlint format`.

- [ ] **Step 2: Sideload + smoke (if a device is available)**

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.pocketpets.app/.MainActivity
```

Walk through the §7 manual QA from the spec:

1. Adopt a pet → cat appears, sits.
2. Wait ~30–60s → cat walks somewhere new and sits.
3. Spam Feed to fill, then Clean, then leave it idle until hunger drops below 30 → cat walks to the bowl.
4. Wait until 22:00–07:00 device time OR debug-speedup energy below 30 → cat walks to the bed and lies down.
5. Tap Talk → speech bubble appears above the cat at its current position.
6. Switch active pet → second pet appears at default; switching back restores prior position for this app session.
7. Background for 30s → on resume the cat resumes walking; no teleport.
8. Sprite faces direction of motion as it walks N/S/E/W.

If no device, document this step as deferred manual QA in the final commit message.

- [ ] **Step 3: If anything visual needs polish**

Tweak the constants:
- `CatBehaviorRules.DEFAULT_SPEED_DP_PER_SEC` (faster/slower walks)
- `CatBehaviorRules.MIN/MAX_WANDER_SECONDS` (more/less frequent wandering)
- `CatAnimations.walk.frameMs` (gait speed)
- The `floorTopDp / floorBottomDp` percentages in `PetScreen` (where the cat is allowed to walk)
- `anchors.bed` / `anchors.bowl` positions

Commit any tweaks as `chore: visual polish on cat movement`.

---

## Self-Review

**1. Spec coverage:**
- Spec §1 in-scope items — covered by Tasks 1–13.
- Spec §2 asset choice + repack — Tasks 1, 2.
- Spec §3 state machine (`CatBehavior`, `CatBehaviorRules`, transition table) — Tasks 3, 4, 5, 6, 7, 8, 9, 10.
- Spec §4 `CatAnimations` refactor — Task 11.
- Spec §5 ViewModel + Screen wiring — Tasks 12, 13.
- Spec §6 testing — Tasks 5, 7, 8, 9, 10, 11; ViewModel-side test note in Task 12 Step 6.
- Spec §7 manual QA — Task 14 Step 2.
- Spec §8 risks — addressed structurally (asset fallbacks; pure tick; bounds default; Phase 3 hookup preserved by pure `tick`).

**2. Placeholder scan:**
- Task 1 deliberately writes a `repack_lpc` with placeholder source coordinates that Task 2 replaces after inspecting the actual sheet — this is the only data-dependent step in the plan and is flagged inline with a `PLACEHOLDER` comment in the code. It's not a TBD; Task 2 makes it concrete based on what the asset actually looks like.
- Task 13 Step 2's bowl/bed constants ("24f", "16f", "0.40f", "0.85f") are illustrative; the implementer should verify against the actual bowl Image position. This is a layout-tuning step that's hard to nail without seeing the rendered screen — flagged inline.
- No "TBD" / "implement later" / "add appropriate error handling" appear anywhere else.

**3. Type consistency:**
- `Position`, `HabitatBounds`, `Anchors`, `CatState`, `CatBehavior` referenced consistently across Tasks 4–13.
- `CatBehaviorRules.tick` signature is the same across Tasks 10 (definition), 12 (ViewModel call site), and the spec §3.
- `CatAnimations.forState` referenced from Task 11 (definition) and Task 13 (PetScreen call). The old `forMood` is removed in Task 11; Task 13 fixes the only remaining caller.
- `Mood`, `GrowthStage`, `Pet`, `PetStats` are existing domain types from `com.pocketpets.app.domain.*`, unchanged.
- `Direction`, `SpriteSheet`, `SpriteAnimation`, `AnimatedSprite` from Phase 1 — unchanged.
