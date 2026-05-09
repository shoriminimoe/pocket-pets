# Cat Art Upgrade & Sprite Renderer — Design Spec (Phase 1)

**Status:** Approved 2026-05-09
**Phase:** 1 of 3 in the cat-interactivity initiative
**Goal:** Replace procedural cat art with an artist-made pixel-art cat, and replace the bitmap-sheet renderer with one that supports per-state frame counts, multi-row sheets, and directional facing — preparing the foundation for Phase 2 (interactive inventory) and Phase 3 (cat AI / movement).

## Context

The user requested:
1. Higher-fidelity graphics — "the cat doesn't really look like a cat".
2. More interactive pet (movement, playing with objects) — Phase 3.
3. Direct-manipulation interactions instead of buttons (drag food to bowl, scoop poops, tap-and-hold to pet) — Phase 2.
4. Move beyond the literal Tamagotchi look.

Brainstorming decisions (2026-05-09):
- **Asset source:** bundle a free CC0 (or CC-BY-with-attribution) pixel-art cat sprite sheet from OpenGameArt.org.
- **Renderer:** rebuild it (Approach B), not just adapt the existing one — pays the renderer cost once now so Phase 3 doesn't pay it again.
- **Rollout:** phased. This spec is Phase 1.
- **Phases 2 & 3 outline** (for context, **not implemented in this spec**):
  - Phase 2: drag-and-drop bottom inventory tray (food bag, litter scoop, toy). Touch-and-hold the cat for petting. The Feed/Clean/Pet/Talk button row goes away.
  - Phase 3: light-AI cat behaviour — picks idle wander destinations, walks to bowl when food is present, walks to a toy when one is dropped, ignores poops with a tail flick, etc. State machine: Idle, Walking, Eating, Playing, Sleeping, Reacting.

## 1. Scope

### In scope
- Source and bundle one CC0 (preferred) cat sprite sheet that visibly looks like a cat.
- Replace `SpriteView` with a renderer that handles multi-row sheets, per-animation frame counts, optional directional facing, optional horizontal flip.
- Re-map the existing six moods to animations on the new sheet, with Composable-layer particle overlays (heart / tear / squiggle / Z) so a single base "sit" animation can serve multiple moods.
- Add `tools/fetch_cat_sprites.py` that fetches the chosen asset and verifies SHA256.
- Strip cat-related code from `tools/generate_sprites.py`; keep poop / bowl / room background generation.
- Add `ATTRIBUTION.md` only if the chosen asset is CC-BY (CC0 needs none).

### Out of scope (deferred to Phases 2 & 3)
- Drag-and-drop inventory tray, scoop-the-poop, fill-the-bowl, drop-a-toy interactions.
- Cat AI, movement, walking, reacting to objects.
- Multiple species (still cat-only).
- Behaviour, stats, mood priority, decay rates, notifications, settings — all unchanged.
- Per-stage sprite sheets — one cat asset, scaled visually for baby/juvenile/adult.

## 2. Asset choice

Sourced from OpenGameArt.org, filtered for CC0 first.

**Primary:** [Cat 32×32 by GrafxKid](https://opengameart.org/content/cat-32x32) — CC0. Sit, walk, lay frames in a single sheet.

**Fallbacks (in preference order if primary unavailable or unsuitable):**
1. Surt's "Cat" — CC0, simpler.
2. LPC cat assets — CC-BY-SA-3.0/OGA-BY-3.0 (4-direction walk; requires `ATTRIBUTION.md`).

The implementation plan picks the actual asset at fetch time and pins its URL + SHA256 in the fetch script. If the primary is gone, fall back in order.

The fetched PNG is committed to `app/src/main/res/drawable-nodpi/cat.png`. Game art is small; bundling avoids runtime fetch and keeps offline builds working.

## 3. Mood → animation mapping

The six moods need to be expressed by combinations of (a) one of the available base animations and (b) a Composable particle overlay drawn above the sprite.

| Mood          | Base animation               | Overlay              |
|---------------|------------------------------|----------------------|
| `IDLE`        | sit / sit-blink              | none                 |
| `HAPPY`       | sit / sit-blink              | hearts               |
| `HUNGRY`      | sit (mouth-open if available)| none                 |
| `GROSSED_OUT` | sit                          | squiggle             |
| `SAD`         | sit                          | tear                 |
| `SLEEPY`      | lay / sleep                  | Zs                   |

Exact rows/frame counts are filled in during implementation, after the asset is fetched and inspected.

`CatAnimations.forMood(stage, mood)` is exhaustive on `Mood`; missing entries fail the build (sealed `when`). A unit test asserts every (stage, mood) returns a non-null animation with `frameCount > 0`.

## 4. Renderer architecture

```kotlin
// Static description of a sprite sheet's grid layout.
data class SpriteSheet(
    val resId: Int,
    val frameWidth: Int,
    val frameHeight: Int,
) {
    init {
        require(frameWidth > 0)
        require(frameHeight > 0)
    }
}

// One named animation: which row, how many frames, how fast.
data class SpriteAnimation(
    val sheet: SpriteSheet,
    val row: Int,
    val frameCount: Int,
    val frameMs: Long = 150,
    val loop: Boolean = true,
) {
    init {
        require(row >= 0)
        require(frameCount > 0)
        require(frameMs > 0)
    }
}

enum class Direction { SOUTH, NORTH, EAST, WEST }

@Composable
fun AnimatedSprite(
    animation: SpriteAnimation,
    modifier: Modifier = Modifier,
    facing: Direction = Direction.SOUTH,
    flipHorizontal: Boolean = false,
)
```

**Behaviour contract:**
- Bitmap is decoded with `inScaled = false` and cached via `remember(animation.sheet.resId)`. Changing `animation` (mood swap) does **not** reload the bitmap.
- Frame index advances on a `LaunchedEffect(animation)` ticker every `frameMs`. When `animation` changes, the effect restarts and the index resets to 0.
- If `animation.loop == false`, the effect stops after the last frame and holds it (Phase 1 only uses looping animations, but the param is part of the contract for Phases 2/3).
- `facing` adds a row offset for sheets that pack 4 directions into 4 rows; documented per-asset in `CatAnimations`. Phase 1 always uses `Direction.SOUTH` and a single row per state.
- `flipHorizontal = true` mirrors the rendered frame around the vertical axis. Used for cheaply turning a SOUTH-facing animation into an EAST/WEST one. Phase 1 doesn't use it; Phase 3 will.
- Pixel-perfect: `FilterQuality.None` on `drawImage`.

**Caching note:** the `BitmapFactory.decodeResource` call is cheap relative to a sprite swap, but caching it via `remember(resId)` matters because the swap happens on every mood change.

## 5. File layout

```
app/src/main/kotlin/com/pocketpets/app/ui/sprite/
├── SpriteSheet.kt
├── SpriteAnimation.kt
├── Direction.kt
└── AnimatedSprite.kt

app/src/main/kotlin/com/pocketpets/app/ui/pet/
├── CatAnimations.kt    # NEW: stage+mood → SpriteAnimation
├── MoodOverlay.kt      # NEW: hearts/tear/squiggle/Z particles
├── PetScreen.kt        # MODIFIED: uses AnimatedSprite + MoodOverlay
└── (SpriteView.kt)     # DELETED

app/src/main/res/drawable-nodpi/
├── cat.png             # NEW: bundled, fetched by tools/fetch_cat_sprites.py
├── poop.png            # unchanged (procedural)
├── room_bg.png         # unchanged (procedural)
├── bowl.png            # unchanged (procedural)
└── (cat_*.png)         # DELETED: 18 procedural cat PNGs

tools/
├── generate_sprites.py # MODIFIED: cat code stripped; only decor remains
└── fetch_cat_sprites.py # NEW

ATTRIBUTION.md           # NEW only if asset is CC-BY (skipped if CC0)

app/src/test/kotlin/com/pocketpets/app/ui/sprite/
├── SpriteSheetTest.kt
└── SpriteAnimationTest.kt

app/src/test/kotlin/com/pocketpets/app/ui/pet/
└── CatAnimationsTest.kt
```

## 6. Stage scaling

The cat sheet has one base resolution (likely 32×32). Baby / juvenile / adult sizing is done at the consumer side: `PetScreen` renders `AnimatedSprite` inside a `Box` whose `dp` size depends on `state.stage`. No per-stage sheet, no per-stage animations:

| Stage     | Render size |
|-----------|-------------|
| BABY      | 192.dp      |
| JUVENILE  | 224.dp      |
| ADULT     | 256.dp      |

Integer scaling preserves pixel crispness because the size is a multiple of the source frame size.

## 7. Particle overlays

`MoodOverlay` is a `Composable` that takes a `Mood` and draws nothing for IDLE / HUNGRY, and a small Compose-Canvas particle effect otherwise. Layered above the sprite in a `Box`. Implementation is straightforward Compose Canvas — no new asset dependencies.

| Mood          | Overlay                                           |
|---------------|---------------------------------------------------|
| `HAPPY`       | 1–3 small heart shapes drifting up, fading out    |
| `SAD`         | One blue teardrop sliding down from an eye        |
| `GROSSED_OUT` | Squiggle/wavy line above the head                 |
| `SLEEPY`      | One or two "Z" letters drifting up                |
| `IDLE`        | Nothing                                           |
| `HUNGRY`      | Nothing (the open-mouth animation carries it)     |

## 8. Testing

- **`SpriteSheetTest`**: `init` invariants throw on non-positive frame dimensions.
- **`SpriteAnimationTest`**: `init` invariants throw on negative row, non-positive frame count or frame ms.
- **`CatAnimationsTest`**: every (`GrowthStage`, `Mood`) combination returns a non-null `SpriteAnimation` with valid invariants.
- **No tests on `AnimatedSprite` itself** — it's a Composable that loads bitmaps and animates frames; visual inspection covers it.
- **Existing tests must pass unchanged.** `StatDecayTest`, `MoodTest`, `PetViewModelTest`, `PetRepositoryTest`, `PetDaoTest`, `CatSpeechTest`, `PetCareWorkerTest`, `GrowthStageTest` are all untouched by this work; if any reference deleted code, that's a regression to fix during implementation.

## 9. Acceptance criteria

- `./gradlew test ktlintCheck :app:lintDebug :app:assembleDebug` is green locally and in CI.
- The cat on screen looks recognisably feline (subjective, user judgement during review).
- All six moods trigger the right animation + overlay combo.
- Sprite is pixel-crisp (no bilinear blur), animates smoothly, doesn't flicker on mood swap.
- Tap-to-talk speech bubble still appears in the right position above the cat.
- Pet selector still swaps the active pet correctly.
- Stage scaling visibly differs between baby / juvenile / adult.

## 10. Risks and mitigations

- **Asset unavailable / link rotted.** Fetch script encodes the URL + SHA256; if download fails or hash mismatches, build instructions tell the implementer to fall back to the next candidate in §2 and update the script.
- **Asset's animations don't map cleanly to all 6 moods.** Particle overlays are the safety valve — every mood can fall back to a base "sit" animation plus a distinguishing overlay. `HUNGRY` may need a small Composable overlay too if the asset has no open-mouth pose; OK to add.
- **Renderer perf — bitmap decode on every recomposition.** Mitigated by `remember(resId)`. If the sheet is large enough to cause memory pressure on low-end devices, we can later add a global `BitmapCache`.
- **Phase 2/3 refactor cost.** The renderer interface (`SpriteAnimation` + `Direction` + `flipHorizontal`) is sized to absorb Phase 3's directional walks without changes. If Phase 3 needs more (e.g. animation chaining, transitions), we extend `SpriteAnimation` then.
