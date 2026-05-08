# Pocket Pets — Design Spec

**Status:** Approved 2026-05-08
**Platform:** Android (min SDK 24, target SDK 34)
**Stack:** Kotlin + Jetpack Compose, Room, DataStore, WorkManager

## 1. Overview

Pocket Pets is a Tamagotchi-style digital-pet app for Android. Users adopt and care for one or more pixel-art pets, switching between them at will. Pets must be fed and cleaned up after, grow from baby to adult over real-world days, can be tapped/petted on screen, and "speak" in their species language with a toggleable silly translation. Cat is the first species; the design supports adding more.

**Non-goals (v1):** multiplayer, cloud sync, in-app purchases, additional species beyond cat, pet death, instrumented UI tests.

## 2. Product behaviour

### 2.1 Pet life

- **Mortality:** pets never die. Sustained neglect drives stats down and changes mood/sprite, but recovery is always possible by resuming care.
- **Growth stages**, computed from `bornAt`:
  - `Baby` — 0 to 3 days old
  - `Juvenile` — 3 to 7 days old
  - `Adult` — 7+ days old
- Growth is real-elapsed-time (wall clock), not playtime.

### 2.2 Stats and decay

Each pet has four stats in `0..100`. Decay rates (per real-time hour):

| Stat | Decay/hr | Notes |
|---|---|---|
| Hunger | -8 | 100 = stuffed, 0 = starving. Full → hungry in ~6h, starving in ~12h. |
| Cleanliness | -3 baseline | Each on-screen poop adds an additional -5/hr while present. |
| Happiness | -2 | Restored by petting and feeding. |
| Energy | -4 while awake | Auto-recovers during the sleep window 22:00–07:00 device-local time. |

### 2.3 Poop mechanic

- Feeding schedules a poop to spawn ~30 min later.
- Each poop is a sprite on the floor at a random position. Up to 4 visible at once (further poops do not spawn until cleaned).
- Cleanliness decays faster while poops are present (see table).
- `clean()` removes one poop per invocation; if no poops are present, `clean()` instead grants +10 cleanliness (a bath).

### 2.4 Care actions

| Action | Effect |
|---|---|
| Feed | +40 hunger (clamped), +5 happiness, schedules a poop ~30 min later |
| Clean | Removes one poop, OR +10 cleanliness if no poops |
| Pet | +5 happiness, capped at 5 successful pets per 10-minute rolling window |
| Talk / tap pet | Triggers a speech bubble, +2 happiness |

### 2.5 Mood (derived, not stored)

Computed from current stats. Drives sprite selection and the speech-bank category to pull from.

- `Hungry` if hunger < 30
- `GrossedOut` if cleanliness < 30 OR poopCount >= 2
- `Sad` if happiness < 30
- `Sleepy` if energy < 30 OR within sleep window
- `Happy` if happiness > 70 and no negative state
- `Idle` otherwise

When multiple negative states qualify, priority order is `Hungry > GrossedOut > Sad > Sleepy`.

### 2.6 Speech and translation

- Pet emits speech bubbles on: tap-to-talk, mood transitions, periodic idle chatter (~once per 2 minutes max while screen is open).
- Initial bubble shows the **animal sound**: `"Mrrowwww?? Mrow mrow!"`.
- Tap the bubble → flips to **translation**: `"I have not eaten in NINE HUNDRED YEARS."`
- Bubble auto-dismisses after 4 s; tapping pauses the dismiss timer.
- Phrases are stored as `Phrase(animal, translation)` pairs in a per-species `SpeechBank` keyed by mood category. Random pick within category. Translations skew silly and dramatic.

### 2.7 Multiple pets

- Exactly one pet is "active" at a time (`isActive` flag in DB).
- Bottom-sheet selector shows all pets with mini sprite, name, age, mood emoji.
- "+ Adopt new pet" opens the adopt flow.

## 3. UI

Single-activity Compose app with Compose Navigation between three screens plus one bottom sheet.

### 3.1 PetScreen (main / habitat)

- Pixel-art background room (tiled wood floor + wallpaper) drawn at integer scale.
- Pet sprite centred, idle-animated (4-frame breathing/blink/tail loop at ~6 fps).
- Up to 4 poop sprites scattered on the floor.
- **Top bar:** name · age · stat chips (hunger/cleanliness/happiness/energy) with mini bars.
- **Top-left:** hamburger icon → `PetSelectorSheet`.
- **Top-right:** gear icon → `SettingsScreen`.
- **Bottom action row:** four chunky pixel buttons — Feed · Clean · Pet · Talk.
- **Tap on pet sprite** = combined pet + talk (heart particle + speech bubble).

### 3.2 PetSelectorSheet (bottom sheet)

- Scrollable list of all pets, with switch-on-tap.
- "+ Adopt new pet" row at the bottom.

### 3.3 AdoptPetScreen

- Species picker. Cat enabled; placeholders for future species are visibly disabled.
- Name field (1–20 chars, required).
- "Adopt" button → creates pet (stats at 100, energy at 100, no poops, `bornAt = now`), marks active, navigates back to `PetScreen`.

### 3.4 SettingsScreen

- Notification master toggle.
- Per-event toggles: Hungry · Dirty · Sad.
- Quiet-hours range picker (default 22:00–07:00).
- Debug section (visible only in debug builds): "Speed up time 100×" toggle.

### 3.5 Visual style — 16-bit-ish

- 64×64 sprites for pets, integer-scaled (2× or 3×) with `FilterQuality.None` for pixel-perfect rendering.
- Palette of ~24–32 colours per pet, allowing real shading and gradient fur tones.
- 4-frame idle, 4-frame eating, 3-frame happy bounce, 4-frame sleep with Z's. Single mood pose for hungry/dirty/sad.
- 16-bit-style background: tiled floor, patterned wallpaper, rug, optional decor (food bowl, litter box).
- Cat needs roughly 30 frames per growth stage × 3 stages — bundled per stage as a sprite sheet PNG in `res/drawable-nodpi`.

## 4. Architecture

### 4.1 Module layout

Single Gradle module `:app`.

```
com.pocketpets.app/
├── MainActivity                              // single activity, hosts NavHost
├── PocketPetsApp                             // Application — DI, WorkManager init
├── data/
│   ├── db/        AppDatabase, PetEntity, PetDao, CareEventEntity, CareEventDao
│   ├── repo/      PetRepository
│   └── settings/  SettingsDataStore
├── domain/
│   ├── Pet, PetStats, GrowthStage, Mood, Species
│   ├── StatDecay                             // pure
│   └── speech/    Phrase, SpeechBank, CatSpeech
├── ui/
│   ├── theme/
│   ├── pet/       PetScreen, PetViewModel, SpriteView, SpeechBubble, StatChip
│   ├── select/    PetSelectorSheet, PetSelectorViewModel
│   ├── adopt/     AdoptPetScreen, AdoptViewModel
│   └── settings/  SettingsScreen, SettingsViewModel
├── work/          PetCareWorker, NotificationHelper, BootReceiver
└── di/            AppContainer
```

### 4.2 Key boundaries

- **`StatDecay` is pure.** Signature: `fun tick(pet: Pet, now: Instant): Pet`. Used by both the ViewModel (recompute-on-read) and the worker. No duplicated decay logic.
- **`SpeechBank` is pure data.** Adding a species means adding a new `XSpeech` object and a `Species` enum case.
- **Repository is the only DB toucher.** ViewModels and the worker both go through `PetRepository`.
- Manual DI via `AppContainer` on `Application`. Hilt is not used.

### 4.3 Recompute-on-read

`PetViewModel.observeActivePet()` returns:

```kotlin
petRepo.observeActive()
    .combine(ticker(60.seconds)) { pet, _ -> StatDecay.tick(pet, Clock.System.now()) }
```

This keeps the displayed stats current without writing to the DB on every UI tick. Care actions (`feed`, `clean`, `pet`) are the only operations that write; they call `tick` first to bring the pet up-to-date, apply the action, and persist with `lastTickAt = now`.

### 4.4 Background worker

- `PeriodicWorkRequest` named `pet_care`, every 30 minutes, `KEEP` policy on enqueue.
- `BootReceiver` (registered for `BOOT_COMPLETED`) re-enqueues on device reboot.
- For each pet: load → `StatDecay.tick(pet, now)` → save → `NotificationHelper.maybeNotify(pet)`.

### 4.5 Notifications

- Single channel `pet_care` (Android 8+).
- `POST_NOTIFICATIONS` runtime permission requested on first launch (Android 13+). If denied, the app falls back to an in-app banner on `PetScreen` and never re-prompts.
- **Thresholds:** hungry < 25, cleanliness < 25 OR poopCount >= 2, happiness < 25.
- **De-dupe:** per-pet per-event boolean in DataStore. Set true when notified, cleared when stat returns above threshold (with a 10-point hysteresis margin so we don't flicker around the line).
- All notifications gated by master toggle, per-event toggle, and quiet-hours window.
- Tap notification → opens `MainActivity` with extras `petId` + `screen=pet`, deep-linked to that pet.

### 4.6 Persistence

- **Room** — pets, care events. `CareEventEntity` is capped at last 100 per pet (older rows pruned on write).
- **DataStore (Preferences)** — notification settings, debug flags, last-seen pet id, per-pet per-event de-dupe flags.
- DB schema versioned from v1.

## 5. Data model

```kotlin
enum class Species { CAT }

enum class GrowthStage { BABY, JUVENILE, ADULT }

enum class Mood { IDLE, HAPPY, HUNGRY, GROSSED_OUT, SAD, SLEEPY }

@Entity(tableName = "pets")
data class PetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val species: Species,
    val bornAt: Instant,
    val hunger: Float,            // 0..100
    val cleanliness: Float,
    val happiness: Float,
    val energy: Float,
    val lastTickAt: Instant,
    val isActive: Boolean,
    val poopCount: Int,
    val lastFedAt: Instant?,      // for poop scheduling
)

@Entity(tableName = "care_events")
data class CareEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val petId: Long,
    val kind: String,             // "feed" | "clean" | "pet" | "talk" | "auto_tick"
    val at: Instant,
)

data class Phrase(val animal: String, val translation: String)
```

## 6. Speech bank example (cat)

```kotlin
object CatSpeech : SpeechBank {
    override val hungry = listOf(
        Phrase("Mrrowwww?? Mrow mrow!", "I have not eaten in NINE HUNDRED YEARS."),
        Phrase("Mrp? Mrp mrp mrp.", "The bowl is, technically, empty. Fix it."),
        // ... ~8 per category
    )
    override val grossedOut = listOf(/* ... */)
    override val sad = listOf(/* ... */)
    override val happy = listOf(/* ... */)
    override val idle = listOf(/* ... */)
    override val sleepy = listOf(/* ... */)
}
```

Translations skew dramatic, silly, and slightly entitled.

## 7. Testing

- **Pure unit tests** (no Android deps): `StatDecay` (decay math, clamping, poop spawning), `GrowthStage` (boundaries), mood derivation (priority order), `SpeechBank` selection.
- **Room DAO tests** using in-memory database.
- **ViewModel tests** with `kotlinx-coroutines-test` and a fake `PetRepository`.
- **Worker tests** via `WorkManagerTestInitHelper`.
- Skip instrumented UI tests for v1; rely on `@Preview` Composables and manual QA.

## 8. Rollout / scope

This spec is a single implementation cycle resulting in a runnable debug-buildable APK with:

- One species (cat) at three growth stages, with all stat/mood/speech behaviour.
- Multi-pet adopt + switch flow.
- Background decay + notifications on real device.
- Debug-only time acceleration toggle.

Out of scope for this spec (defer to follow-ups): additional species, achievements/collection screen, mini-games, sound effects, light/dark themes beyond default, accessibility audit, Play Store assets and listing.
