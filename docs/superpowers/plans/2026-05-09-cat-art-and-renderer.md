# Cat Art Upgrade & Sprite Renderer Implementation Plan (Phase 1)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the procedural Pillow-generated cat sprites with a bundled CC0/CC-BY pixel-art cat sheet, and replace the rigid single-row `SpriteView` with a multi-row, per-state-frame-count, directional-aware `AnimatedSprite` renderer — without changing any pet behaviour, stats, or actions.

**Architecture:** Pure-Kotlin sprite primitives (`SpriteSheet`, `SpriteAnimation`, `Direction`) consumed by an `AnimatedSprite` Composable that decodes the sheet once and animates frames on a `LaunchedEffect` ticker. Mood → animation lives in `CatAnimations.forMood(stage, mood)` with a Compose-Canvas `MoodOverlay` for hearts/tear/squiggle/Z particles so a single base "sit" animation can serve multiple moods.

**Tech Stack:** Kotlin 2.2.0, Jetpack Compose (BOM 2026.05.00), AGP 8.9.3, JDK 17, JUnit 4 + Truth + Robolectric for the existing test conventions, Pillow for the procedural decor script.

**Reference spec:** `docs/superpowers/specs/2026-05-09-cat-art-and-renderer-design.md` — read it before starting.

**Out of scope reminder:** Don't touch `Pet`, `PetStats`, `Mood`, `StatDecay`, `PetRepository`, `PetCareWorker`, settings, notifications, work scheduling, the action button row, the speech bubble, the pet selector, the adopt flow. If a test references any of those, it stays untouched.

**File structure (target end state):**

```
app/src/main/kotlin/com/pocketpets/app/ui/sprite/
├── Direction.kt                # NEW
├── SpriteSheet.kt              # NEW
├── SpriteAnimation.kt          # NEW
└── AnimatedSprite.kt           # NEW

app/src/main/kotlin/com/pocketpets/app/ui/pet/
├── CatAnimations.kt            # NEW
├── MoodOverlay.kt              # NEW
├── PetScreen.kt                # MODIFIED — uses AnimatedSprite
├── SpeechBubble.kt             # unchanged
├── StatChip.kt                 # unchanged
├── PetViewModel.kt             # unchanged
└── (SpriteView.kt)             # DELETED

app/src/main/res/drawable-nodpi/
├── cat.png                     # NEW (committed; produced by fetch script)
├── poop.png                    # unchanged
├── room_bg.png                 # unchanged
├── bowl.png                    # unchanged
└── (cat_baby_*.png + ...)      # DELETED (18 files)

tools/
├── generate_sprites.py         # MODIFIED — cat code removed
└── fetch_cat_sprites.py        # NEW

ATTRIBUTION.md                  # NEW only if asset is CC-BY (skip for CC0)

app/src/test/kotlin/com/pocketpets/app/ui/sprite/
├── SpriteSheetTest.kt          # NEW
└── SpriteAnimationTest.kt      # NEW

app/src/test/kotlin/com/pocketpets/app/ui/pet/
└── CatAnimationsTest.kt        # NEW
```

---

## Conventions

- **Toolchain prep:** before any task, `source /tmp/env.sh` if available, otherwise set `JAVA_HOME=$HOME/.local/jdk` and `ANDROID_HOME=$HOME/Android/Sdk` and add `$JAVA_HOME/bin` to `PATH`. Pillow lives in `~/.local/spritegen-venv/bin/python`.
- **Tests:** JUnit 4 + Truth + Robolectric, all on JVM via `./gradlew test` or `:app:testDebugUnitTest`. No `androidTest/` source set.
- **After every task:** run `./gradlew ktlintCheck` (formatting) and the relevant test slice. Commit only if green.
- **Conventional-commit prefix:** `feat:`, `refactor:`, `chore:`, `test:`, `docs:`. Used by release-please for the next version bump.
- **No `--no-verify`, no rule disables.**

---

## Task 1: Write `tools/fetch_cat_sprites.py`

**Files:**
- Create: `tools/fetch_cat_sprites.py`

This task only writes the script. Running it happens in Task 2 so we can react to download/license outcomes.

- [ ] **Step 1: Write the script**

```python
#!/usr/bin/env python3
"""Fetch and verify the bundled cat sprite sheet.

Tries candidates in priority order. CC0 preferred; CC-BY accepted with
ATTRIBUTION.md. Writes the chosen PNG to
app/src/main/res/drawable-nodpi/cat.png and prints the asset's
dimensions so the implementer can wire CatAnimations.

Each candidate is a (name, url, license, expected_sha256_or_None).
Set expected_sha256 to None for the first run, then re-run after
inspecting the file to pin the hash.
"""
from __future__ import annotations
import hashlib
import sys
import urllib.request
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).parent.parent
OUT = ROOT / "app" / "src" / "main" / "res" / "drawable-nodpi" / "cat.png"

CANDIDATES = [
    # (name, url, license_id, expected_sha256_or_None)
    (
        "Cat 32x32 by GrafxKid",
        "https://opengameart.org/sites/default/files/cat_1.png",
        "CC0",
        None,
    ),
    (
        "Cat by Surt",
        "https://opengameart.org/sites/default/files/cats_0.png",
        "CC0",
        None,
    ),
    (
        "LPC cat",
        "https://opengameart.org/sites/default/files/cat_4.png",
        "CC-BY-SA-3.0",
        None,
    ),
]

def fetch(url: str) -> bytes:
    req = urllib.request.Request(url, headers={"User-Agent": "pocket-pets-fetch/1.0"})
    with urllib.request.urlopen(req, timeout=30) as resp:
        return resp.read()

def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()

def main() -> int:
    OUT.parent.mkdir(parents=True, exist_ok=True)
    for name, url, license_id, expected in CANDIDATES:
        try:
            print(f"Trying: {name} ({license_id}) -> {url}")
            data = fetch(url)
        except Exception as e:
            print(f"  Failed: {e}")
            continue
        digest = sha256(data)
        if expected is not None and digest != expected:
            print(f"  SHA256 mismatch: got {digest}, expected {expected}")
            continue
        OUT.write_bytes(data)
        try:
            img = Image.open(OUT)
            w, h = img.size
        except Exception as e:
            print(f"  Not a valid image: {e}")
            OUT.unlink(missing_ok=True)
            continue
        print()
        print(f"OK: {name}")
        print(f"  License: {license_id}")
        print(f"  URL:     {url}")
        print(f"  SHA256:  {digest}")
        print(f"  Size:    {w}x{h} px")
        print(f"  Wrote:   {OUT.relative_to(ROOT)}")
        if license_id != "CC0":
            print()
            print("  NOTE: This asset requires attribution. Add an entry to")
            print("  ATTRIBUTION.md with the name, URL, license, and author.")
        return 0
    print("All candidates failed. Edit CANDIDATES to add another asset.", file=sys.stderr)
    return 1

if __name__ == "__main__":
    sys.exit(main())
```

- [ ] **Step 2: Mark executable**

```bash
chmod +x tools/fetch_cat_sprites.py
```

- [ ] **Step 3: Commit**

```bash
git add tools/fetch_cat_sprites.py
git commit -m "feat: add cat-sprite fetch script with CC0-first candidate list"
```

---

## Task 2: Run the fetcher and inspect the result

This task is **discovery, not coding**. The asset's actual rows / columns / frame size determine `CatAnimations` in Task 7.

- [ ] **Step 1: Run the fetcher**

```bash
~/.local/spritegen-venv/bin/python tools/fetch_cat_sprites.py
```

Expected: prints `OK: <name>` with size and SHA256 lines, and writes `app/src/main/res/drawable-nodpi/cat.png`.

If **all candidates fail** (404s, network blocked), stop here and add a candidate to `tools/fetch_cat_sprites.py`. Search OpenGameArt for "cat sprite" filtered by CC0 first, then by CC-BY-SA-3.0/OGA-BY-3.0. Re-run.

- [ ] **Step 2: Inspect the sheet visually + numerically**

```bash
~/.local/spritegen-venv/bin/python - <<'PY'
from PIL import Image
img = Image.open("app/src/main/res/drawable-nodpi/cat.png")
print("size:", img.size, "mode:", img.mode)
PY
```

Look at the PNG (open in any image viewer). Determine:
- **Frame width × height** (commonly 32×32 or 16×16; sometimes 24×24 or 64×64).
- **Number of rows.** Each row is typically one animation state (sit, walk, sleep, …).
- **Number of frames per row** (might differ row to row).
- **What each row depicts** (sit / walk / lay / yowl / clean / etc.).

- [ ] **Step 3: Pin the SHA256 in the script**

Edit `CANDIDATES` in `tools/fetch_cat_sprites.py` so the chosen entry's `expected_sha256_or_None` is the actual hash from Step 1's output. This makes future runs fail loudly if the upstream URL serves different bytes.

- [ ] **Step 4: Write a one-line layout note**

Append to the chosen candidate's docstring block in the script (a comment immediately above CANDIDATES is fine), recording exactly what you observed:

```python
# Chosen: <name>
# Layout: <W>x<H> per frame; <N> rows. Row 0 = sit (4 frames), row 1 = walk (6 frames), ...
```

- [ ] **Step 5: If the asset is CC-BY (any non-CC0 license), create ATTRIBUTION.md**

```markdown
# Third-Party Asset Attribution

| Asset            | URL                         | Author       | License           |
|------------------|-----------------------------|--------------|-------------------|
| <name>           | <url>                       | <author>     | <license-id>      |
```

If CC0, skip this step. CC0 requires no attribution.

- [ ] **Step 6: Verify the build still works (resource just sits there for now)**

```bash
./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`. The new PNG is just an extra resource at this point; nothing references it yet.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/res/drawable-nodpi/cat.png tools/fetch_cat_sprites.py
# If you created ATTRIBUTION.md:
git add ATTRIBUTION.md
git commit -m "feat: bundle cat sprite sheet (<name>, <license>)"
```

---

## Task 3: `Direction` enum

**Files:**
- Create: `app/src/main/kotlin/com/pocketpets/app/ui/sprite/Direction.kt`

- [ ] **Step 1: Write the file**

```kotlin
package com.pocketpets.app.ui.sprite

/** Compass facing for directional sprite animations. Phase 1 always uses SOUTH. */
enum class Direction { SOUTH, NORTH, EAST, WEST }
```

- [ ] **Step 2: Build to confirm it compiles**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/pocketpets/app/ui/sprite/Direction.kt
git commit -m "feat: add Direction enum for sprite facing"
```

---

## Task 4: `SpriteSheet` data class with invariants (TDD)

**Files:**
- Create: `app/src/main/kotlin/com/pocketpets/app/ui/sprite/SpriteSheet.kt`
- Create: `app/src/test/kotlin/com/pocketpets/app/ui/sprite/SpriteSheetTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.pocketpets.app.ui.sprite

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SpriteSheetTest {
    @Test fun `accepts positive frame dimensions`() {
        val s = SpriteSheet(resId = 1, frameWidth = 32, frameHeight = 32)
        assertThat(s.frameWidth).isEqualTo(32)
        assertThat(s.frameHeight).isEqualTo(32)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects zero frame width`() {
        SpriteSheet(resId = 1, frameWidth = 0, frameHeight = 32)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects negative frame height`() {
        SpriteSheet(resId = 1, frameWidth = 32, frameHeight = -1)
    }
}
```

- [ ] **Step 2: Run test, expect failure**

```bash
./gradlew :app:testDebugUnitTest --tests "com.pocketpets.app.ui.sprite.SpriteSheetTest"
```

Expected: FAIL with "unresolved reference: SpriteSheet".

- [ ] **Step 3: Write the implementation**

```kotlin
package com.pocketpets.app.ui.sprite

import androidx.annotation.DrawableRes

/** Static description of a sprite-sheet PNG: which resource and the size of one cell. */
data class SpriteSheet(
    @DrawableRes val resId: Int,
    val frameWidth: Int,
    val frameHeight: Int,
) {
    init {
        require(frameWidth > 0) { "frameWidth must be positive: $frameWidth" }
        require(frameHeight > 0) { "frameHeight must be positive: $frameHeight" }
    }
}
```

- [ ] **Step 4: Run, expect pass**

```bash
./gradlew :app:testDebugUnitTest --tests "com.pocketpets.app.ui.sprite.SpriteSheetTest"
```

Expected: PASS, 3 tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/pocketpets/app/ui/sprite/SpriteSheet.kt \
        app/src/test/kotlin/com/pocketpets/app/ui/sprite/SpriteSheetTest.kt
git commit -m "feat: add SpriteSheet primitive with dimension invariants"
```

---

## Task 5: `SpriteAnimation` data class with invariants (TDD)

**Files:**
- Create: `app/src/main/kotlin/com/pocketpets/app/ui/sprite/SpriteAnimation.kt`
- Create: `app/src/test/kotlin/com/pocketpets/app/ui/sprite/SpriteAnimationTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.pocketpets.app.ui.sprite

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SpriteAnimationTest {
    private val sheet = SpriteSheet(resId = 1, frameWidth = 32, frameHeight = 32)

    @Test fun `accepts a valid animation`() {
        val a = SpriteAnimation(sheet, row = 0, frameCount = 4)
        assertThat(a.frameMs).isEqualTo(150L)
        assertThat(a.loop).isTrue()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects negative row`() {
        SpriteAnimation(sheet, row = -1, frameCount = 4)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects zero frameCount`() {
        SpriteAnimation(sheet, row = 0, frameCount = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects zero frameMs`() {
        SpriteAnimation(sheet, row = 0, frameCount = 4, frameMs = 0)
    }
}
```

- [ ] **Step 2: Run, expect failure**

```bash
./gradlew :app:testDebugUnitTest --tests "com.pocketpets.app.ui.sprite.SpriteAnimationTest"
```

Expected: FAIL with "unresolved reference: SpriteAnimation".

- [ ] **Step 3: Write the implementation**

```kotlin
package com.pocketpets.app.ui.sprite

/**
 * One named animation on a sprite sheet: which row to use, how many frames,
 * how fast to advance them, and whether the animation loops.
 */
data class SpriteAnimation(
    val sheet: SpriteSheet,
    val row: Int,
    val frameCount: Int,
    val frameMs: Long = 150L,
    val loop: Boolean = true,
) {
    init {
        require(row >= 0) { "row must be non-negative: $row" }
        require(frameCount > 0) { "frameCount must be positive: $frameCount" }
        require(frameMs > 0) { "frameMs must be positive: $frameMs" }
    }
}
```

- [ ] **Step 4: Run, expect pass**

```bash
./gradlew :app:testDebugUnitTest --tests "com.pocketpets.app.ui.sprite.SpriteAnimationTest"
```

Expected: PASS, 4 tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/pocketpets/app/ui/sprite/SpriteAnimation.kt \
        app/src/test/kotlin/com/pocketpets/app/ui/sprite/SpriteAnimationTest.kt
git commit -m "feat: add SpriteAnimation primitive with invariants"
```

---

## Task 6: `AnimatedSprite` Composable

**Files:**
- Create: `app/src/main/kotlin/com/pocketpets/app/ui/sprite/AnimatedSprite.kt`

No unit test — it's a Composable that loads bitmaps; covered by manual visual inspection in Task 11.

- [ ] **Step 1: Write the file**

```kotlin
package com.pocketpets.app.ui.sprite

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.delay

/**
 * Renders one frame of `animation` at a time, advancing every `animation.frameMs`
 * on a `LaunchedEffect`. The bitmap is decoded once per `animation.sheet.resId` and
 * cached via `remember` so swapping animations on the same sheet is free.
 *
 * `facing` adds a row offset for sheets that pack multiple directions per state.
 * `flipHorizontal = true` mirrors the rendered frame around its vertical axis.
 */
@Composable
fun AnimatedSprite(
    animation: SpriteAnimation,
    modifier: Modifier = Modifier,
    facing: Direction = Direction.SOUTH,
    flipHorizontal: Boolean = false,
) {
    val resources = LocalResources.current
    val bitmap = remember(animation.sheet.resId, resources) {
        BitmapFactory.decodeResource(
            resources,
            animation.sheet.resId,
            BitmapFactory.Options().apply { inScaled = false },
        )
    }
    val image = remember(bitmap) { bitmap.asImageBitmap() }

    var frame by remember(animation) { mutableIntStateOf(0) }
    LaunchedEffect(animation) {
        if (animation.frameCount <= 1) return@LaunchedEffect
        while (true) {
            delay(animation.frameMs)
            frame = if (animation.loop) {
                (frame + 1) % animation.frameCount
            } else {
                (frame + 1).coerceAtMost(animation.frameCount - 1)
            }
            if (!animation.loop && frame == animation.frameCount - 1) break
        }
    }

    val rowOffset = when (facing) {
        Direction.SOUTH -> 0
        Direction.NORTH -> 1
        Direction.WEST -> 2
        Direction.EAST -> 3
    }

    Canvas(modifier = modifier) {
        val scaleX = if (flipHorizontal) -1f else 1f
        scale(scaleX = scaleX, scaleY = 1f) {
            drawFrame(
                image = image,
                col = frame,
                row = animation.row + rowOffset,
                frameW = animation.sheet.frameWidth,
                frameH = animation.sheet.frameHeight,
            )
        }
    }
}

private fun DrawScope.drawFrame(
    image: androidx.compose.ui.graphics.ImageBitmap,
    col: Int,
    row: Int,
    frameW: Int,
    frameH: Int,
) {
    drawImage(
        image = image,
        srcOffset = IntOffset(col * frameW, row * frameH),
        srcSize = IntSize(frameW, frameH),
        dstOffset = IntOffset(0, 0),
        dstSize = IntSize(size.width.toInt(), size.height.toInt()),
        filterQuality = FilterQuality.None,
    )
}
```

- [ ] **Step 2: Build**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/pocketpets/app/ui/sprite/AnimatedSprite.kt
git commit -m "feat: add AnimatedSprite composable with directional + flip support"
```

---

## Task 7: `CatAnimations` mood-to-animation map (TDD)

This is the only task whose **content** depends on what was discovered in Task 2. The **structure** is fixed: an exhaustive `when` over `Mood` that returns a `SpriteAnimation`. Fill in row/frameCount/frameMs from the chosen sheet's actual layout.

**Files:**
- Create: `app/src/main/kotlin/com/pocketpets/app/ui/pet/CatAnimations.kt`
- Create: `app/src/test/kotlin/com/pocketpets/app/ui/pet/CatAnimationsTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.pocketpets.app.ui.pet

import com.google.common.truth.Truth.assertThat
import com.pocketpets.app.domain.GrowthStage
import com.pocketpets.app.domain.Mood
import org.junit.Test

class CatAnimationsTest {
    @Test fun `every mood and stage maps to a valid animation`() {
        for (stage in GrowthStage.values()) {
            for (mood in Mood.values()) {
                val anim = CatAnimations.forMood(stage, mood)
                assertThat(anim.frameCount).isGreaterThan(0)
                assertThat(anim.row).isAtLeast(0)
                assertThat(anim.frameMs).isGreaterThan(0L)
            }
        }
    }
}
```

- [ ] **Step 2: Run test, expect failure**

```bash
./gradlew :app:testDebugUnitTest --tests "com.pocketpets.app.ui.pet.CatAnimationsTest"
```

Expected: FAIL with "unresolved reference: CatAnimations".

- [ ] **Step 3: Write the implementation**

Use the row/frameCount values you observed in Task 2. The example below assumes the GrafxKid Cat 32×32 layout (row 0 = sit 4 frames, row 1 = lay 2 frames, row 2 = walk 6 frames). **If your actual sheet differs, adjust the integers — keep the structure**:

```kotlin
package com.pocketpets.app.ui.pet

import com.pocketpets.app.R
import com.pocketpets.app.domain.GrowthStage
import com.pocketpets.app.domain.Mood
import com.pocketpets.app.ui.sprite.SpriteAnimation
import com.pocketpets.app.ui.sprite.SpriteSheet

/**
 * Maps each [Mood] to a [SpriteAnimation] on the bundled cat sprite sheet.
 * GrowthStage is accepted for future per-stage variation but currently
 * returns the same animation regardless of stage — stage-specific scaling
 * happens in PetScreen by sizing the AnimatedSprite's parent Box.
 *
 * Particle distinctions (heart for HAPPY, tear for SAD, etc.) are layered
 * on top by MoodOverlay, so several moods can share the same base sit.
 */
object CatAnimations {
    private val sheet = SpriteSheet(
        resId = R.drawable.cat,
        frameWidth = 32,
        frameHeight = 32,
    )

    val sit = SpriteAnimation(sheet, row = 0, frameCount = 4, frameMs = 220)
    val lay = SpriteAnimation(sheet, row = 1, frameCount = 2, frameMs = 600)
    val walk = SpriteAnimation(sheet, row = 2, frameCount = 6, frameMs = 100)

    fun forMood(stage: GrowthStage, mood: Mood): SpriteAnimation = when (mood) {
        Mood.SLEEPY -> lay
        Mood.IDLE,
        Mood.HAPPY,
        Mood.HUNGRY,
        Mood.GROSSED_OUT,
        Mood.SAD -> sit
    }
}
```

If the real sheet has a distinct "yowl" or "alarm" pose, point `HUNGRY` or `GROSSED_OUT` at it instead of `sit` and update the test/comments accordingly.

- [ ] **Step 4: Run, expect pass**

```bash
./gradlew :app:testDebugUnitTest --tests "com.pocketpets.app.ui.pet.CatAnimationsTest"
```

Expected: PASS, 1 test (covers all 18 stage×mood combinations).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/pocketpets/app/ui/pet/CatAnimations.kt \
        app/src/test/kotlin/com/pocketpets/app/ui/pet/CatAnimationsTest.kt
git commit -m "feat: map moods to cat sprite animations"
```

---

## Task 8: `MoodOverlay` Composable

**Files:**
- Create: `app/src/main/kotlin/com/pocketpets/app/ui/pet/MoodOverlay.kt`

No unit tests; visual.

- [ ] **Step 1: Write the file**

```kotlin
package com.pocketpets.app.ui.pet

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.pocketpets.app.domain.Mood

/**
 * Particle / glyph overlay drawn above the cat sprite to disambiguate moods that
 * share a base animation. Returns nothing visible for moods that don't need it.
 */
@Composable
fun MoodOverlay(
    mood: Mood,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "mood-overlay")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase",
    )
    Canvas(modifier = modifier.size(64.dp)) {
        when (mood) {
            Mood.HAPPY -> drawHearts(phase)
            Mood.SAD -> drawTear(phase)
            Mood.GROSSED_OUT -> drawSquiggle(phase)
            Mood.SLEEPY -> drawZs(phase)
            Mood.IDLE, Mood.HUNGRY -> Unit
        }
    }
}

private fun DrawScope.drawHearts(phase: Float) {
    val color = Color(0xFFE86A8D)
    val baseY = size.height * 0.9f
    repeat(2) { i ->
        val staggered = (phase + i * 0.5f) % 1f
        val y = baseY * (1f - staggered)
        val x = size.width * (0.4f + 0.2f * i)
        val s = size.minDimension * 0.08f * (1f - staggered * 0.4f)
        val alpha = (1f - staggered).coerceIn(0f, 1f)
        drawHeart(Offset(x, y), s, color.copy(alpha = alpha))
    }
}

private fun DrawScope.drawHeart(centre: Offset, size: Float, color: Color) {
    val path = Path().apply {
        moveTo(centre.x, centre.y + size * 0.6f)
        cubicTo(
            centre.x - size * 1.2f, centre.y - size * 0.2f,
            centre.x - size * 0.4f, centre.y - size * 1.2f,
            centre.x, centre.y - size * 0.4f,
        )
        cubicTo(
            centre.x + size * 0.4f, centre.y - size * 1.2f,
            centre.x + size * 1.2f, centre.y - size * 0.2f,
            centre.x, centre.y + size * 0.6f,
        )
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.drawTear(phase: Float) {
    val color = Color(0xFF7AB7E8)
    val x = size.width * 0.4f
    val y = size.height * (0.3f + 0.6f * phase)
    val r = size.minDimension * 0.04f
    drawCircle(color = color, radius = r, center = Offset(x, y))
}

private fun DrawScope.drawSquiggle(phase: Float) {
    val color = Color(0xFF1A1A2E)
    val baseY = size.height * 0.2f
    val amplitude = size.height * 0.04f
    val step = size.width / 12f
    val path = Path().apply {
        moveTo(size.width * 0.25f, baseY)
        for (i in 1..8) {
            val x = size.width * 0.25f + i * step
            val y = baseY + amplitude * if ((i + (phase * 8).toInt()) % 2 == 0) 1f else -1f
            lineTo(x, y)
        }
    }
    drawPath(path, color, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
}

private fun DrawScope.drawZs(phase: Float) {
    val color = Color(0xFF555571)
    val baseX = size.width * 0.7f
    repeat(2) { i ->
        val staggered = (phase + i * 0.5f) % 1f
        val y = size.height * (0.7f - 0.5f * staggered)
        val x = baseX + i * 8f
        val s = size.minDimension * 0.05f * (1f - staggered * 0.3f)
        val alpha = (1f - staggered).coerceIn(0f, 1f)
        drawZ(Offset(x, y), s, color.copy(alpha = alpha))
    }
}

private fun DrawScope.drawZ(centre: Offset, size: Float, color: Color) {
    val path = Path().apply {
        moveTo(centre.x - size, centre.y - size)
        lineTo(centre.x + size, centre.y - size)
        lineTo(centre.x - size, centre.y + size)
        lineTo(centre.x + size, centre.y + size)
    }
    drawPath(path, color, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
}
```

- [ ] **Step 2: Build**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/pocketpets/app/ui/pet/MoodOverlay.kt
git commit -m "feat: add MoodOverlay particle composable for hearts/tear/squiggle/Z"
```

---

## Task 9: Wire `PetScreen` to use `AnimatedSprite` + `MoodOverlay`

**Files:**
- Modify: `app/src/main/kotlin/com/pocketpets/app/ui/pet/PetScreen.kt`

- [ ] **Step 1: Replace the sprite block**

Open `PetScreen.kt` and find the section that renders the sprite (currently `SpriteView(spriteResId = ..., frameCount = ...)`). Replace it as follows.

**At the top of the file**, add imports:

```kotlin
import com.pocketpets.app.ui.sprite.AnimatedSprite
```

Remove the `import` for `SpriteView` (will be deleted in Task 10).

**Replace the rendering block** that currently does:

```kotlin
val spriteRes = spriteFor(pet.species, state.stage, state.mood)
val frames = frameCountFor(state.mood)
// ...
SpriteView(
    spriteResId = spriteRes,
    frameCount = frames,
    modifier = Modifier.fillMaxSize(),
)
```

with:

```kotlin
val animation = CatAnimations.forMood(state.stage, state.mood)
// ...
Box(modifier = Modifier.fillMaxSize()) {
    AnimatedSprite(
        animation = animation,
        modifier = Modifier.fillMaxSize(),
    )
    MoodOverlay(
        mood = state.mood,
        modifier = Modifier.fillMaxSize(),
    )
}
```

**Stage scaling** moves to the parent Box that wraps the sprite. Find the `Box(modifier = Modifier.size(256.dp))` that holds the sprite and replace `.size(256.dp)` with:

```kotlin
.size(
    when (state.stage) {
        com.pocketpets.app.domain.GrowthStage.BABY -> 192.dp
        com.pocketpets.app.domain.GrowthStage.JUVENILE -> 224.dp
        com.pocketpets.app.domain.GrowthStage.ADULT -> 256.dp
    }
)
```

(If GrowthStage is already imported via another usage in the file, drop the FQN.)

**Delete** the now-unused `private fun spriteFor(...)` and `private fun frameCountFor(...)` helpers at the bottom of the file. They're replaced by `CatAnimations.forMood(...)`.

- [ ] **Step 2: Build**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`. If it fails on missing references, you likely forgot an import (`com.pocketpets.app.ui.pet.CatAnimations`, `com.pocketpets.app.ui.pet.MoodOverlay`, or `com.pocketpets.app.ui.sprite.AnimatedSprite`).

- [ ] **Step 3: Run all tests to confirm nothing else broke**

```bash
./gradlew test
```

Expected: `BUILD SUCCESSFUL`. Existing tests must all pass — no behaviour has changed.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/pocketpets/app/ui/pet/PetScreen.kt
git commit -m "refactor: render cat via AnimatedSprite + MoodOverlay; size by stage"
```

---

## Task 10: Delete `SpriteView` and the old procedural cat PNGs

**Files:**
- Delete: `app/src/main/kotlin/com/pocketpets/app/ui/pet/SpriteView.kt`
- Delete: `app/src/main/res/drawable-nodpi/cat_*.png` (18 files)

- [ ] **Step 1: Delete the files**

```bash
rm app/src/main/kotlin/com/pocketpets/app/ui/pet/SpriteView.kt
rm app/src/main/res/drawable-nodpi/cat_*.png
```

- [ ] **Step 2: Build to confirm nothing references them**

```bash
./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`. If it fails with "unresolved reference: SpriteView" or "Resource not found: cat_baby_idle", grep for the symbol and fix the stragglers, then re-run.

- [ ] **Step 3: Commit**

```bash
git add -A app/src/main/kotlin/com/pocketpets/app/ui/pet/SpriteView.kt \
            app/src/main/res/drawable-nodpi/
git commit -m "refactor: remove obsolete SpriteView and procedural cat PNGs"
```

---

## Task 11: Strip cat code from `tools/generate_sprites.py`

**Files:**
- Modify: `tools/generate_sprites.py`

The script no longer needs to draw cats. It should still produce `poop.png`, `room_bg.png`, `bowl.png`.

- [ ] **Step 1: Edit the script**

Open `tools/generate_sprites.py`. Delete:
- The `PAL_CAT` palette constant.
- `def draw_cat_body(...)`.
- `def render_pet_sheet(...)`.
- The cat-rendering loop in `main()`:

```python
stages = ["baby", "juvenile", "adult"]
moods = ["idle", "happy", "sleep", "hungry", "dirty", "sad"]
for stage in stages:
    for mood in moods:
        sheet = render_pet_sheet(stage, mood)
        sheet.save(OUT / f"cat_{stage}_{mood}.png")
```

Keep:
- `def render_poop():`
- `def render_room_bg():`
- `def render_bowl():`
- The save lines for `poop.png`, `room_bg.png`, `bowl.png`.
- Any helpers used by those (`blank`, `rect`, `px`, the OUT path).

The remaining `main()` should look approximately like:

```python
def main():
    render_poop().save(OUT / "poop.png")
    render_room_bg().save(OUT / "room_bg.png")
    render_bowl().save(OUT / "bowl.png")
    print(f"Wrote decor sprites to {OUT}")
```

Update the module docstring at the top to reflect that it produces decor only (no longer cats).

- [ ] **Step 2: Re-run to verify the decor still regenerates byte-identically**

```bash
~/.local/spritegen-venv/bin/python tools/generate_sprites.py
git diff --stat app/src/main/res/drawable-nodpi/poop.png \
                app/src/main/res/drawable-nodpi/room_bg.png \
                app/src/main/res/drawable-nodpi/bowl.png
```

Expected: `Wrote decor sprites to ...` and zero diff stats (script is deterministic; same bytes).

- [ ] **Step 3: Commit**

```bash
git add tools/generate_sprites.py
git commit -m "chore: drop cat-rendering code from generate_sprites; decor only"
```

---

## Task 12: Final verification (build, lint, tests, manual visual)

- [ ] **Step 1: Run the full local check**

```bash
./gradlew ktlintCheck :app:lintDebug test :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`. If ktlint flags formatting on any of the new files, run `./gradlew ktlintFormat`, re-run check, and commit the format fixes as `chore: ktlint format`.

- [ ] **Step 2: Manual visual smoke (if a device or emulator is available)**

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.pocketpets.app/.MainActivity
```

Walk through:
1. Adopt a new pet → screen shows the new cat sprite.
2. Each mood path: starve the pet to HUNGRY (or use the debug speedup), let it get GROSSED_OUT, etc., and verify the right base animation + overlay combo.
3. Tap the cat — speech bubble still appears in the right position.
4. Open the selector sheet — switch between two pets, sprites swap correctly.

If no device is available, document this step as deferred manual QA in the final commit.

- [ ] **Step 3: Final commit (if anything trailing remains)**

If the manual smoke caught anything visual (sprite too small, overlay misaligned, animation too fast/slow), tweak the `dp` sizes in `PetScreen` or the `frameMs` in `CatAnimations`, run Step 1 again, and commit:

```bash
git add app/src/main/kotlin/com/pocketpets/app/ui/pet/
git commit -m "chore: visual polish on cat sprite sizing/timing"
```

If nothing trailed, no commit is needed.

---

## Self-Review

**1. Spec coverage:**
- §1 Scope (in-scope items) — covered by Tasks 1–11.
- §2 Asset choice (CC0 first, fallback list, fetch + verify) — Tasks 1, 2.
- §3 Mood mapping (with overlay safety valve) — Task 7 + Task 8.
- §4 Renderer architecture (`SpriteSheet`, `SpriteAnimation`, `Direction`, `AnimatedSprite`) — Tasks 3, 4, 5, 6.
- §5 File layout — matches the plan's "File structure" header.
- §6 Stage scaling (192/224/256 dp) — Task 9 Step 1.
- §7 Particle overlays — Task 8.
- §8 Testing strategy (`SpriteSheetTest`, `SpriteAnimationTest`, `CatAnimationsTest`, no test on `AnimatedSprite`, existing tests untouched) — Tasks 4, 5, 7; Task 9 Step 3 verifies existing tests still pass.
- §9 Acceptance criteria (build green, recognisably feline, six moods, pixel-crisp, speech bubble works, selector swaps, stage sizing differs) — Task 12.
- §10 Risks and mitigations (asset unavailable handled by candidate list; mood mapping handled by overlay; perf handled by `remember(resId)`) — covered structurally.

**2. Placeholder scan:** No `TBD` / `TODO` / `implement later` / `add appropriate error handling` patterns. The Task 7 implementation explicitly says "if your sheet differs, adjust the integers — keep the structure"; this is necessary because the asset's actual layout is data-dependent on Task 2's outcome, and the example values are the GrafxKid Cat 32×32 layout (the primary candidate).

**3. Type consistency:** `SpriteSheet`, `SpriteAnimation`, `Direction`, `AnimatedSprite`, `CatAnimations.forMood`, `MoodOverlay` — all referenced consistently across tasks. `Mood`, `GrowthStage`, `Pet` are the existing domain types from `com.pocketpets.app.domain.*` and are unchanged.

The single content-dependent task is Task 7's row/frameCount integers, which depend on Task 2's discovery — flagged inline.
