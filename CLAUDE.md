# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build, test, run

Single-module Android (Kotlin 2.2 + Jetpack Compose), Gradle 8.11.1, JDK 17, AGP 8.9.3, compileSdk 36, minSdk 24.

| Task | Command |
|---|---|
| Run all unit tests (debug + release variants) | `./gradlew test` |
| Run a single test class | `./gradlew :app:testDebugUnitTest --tests "com.pocketpets.app.domain.StatDecayTest"` |
| Run one test method | `./gradlew :app:testDebugUnitTest --tests "com.pocketpets.app.domain.StatDecayTest.poop spawns 30 minutes after feeding when none scheduled"` |
| Build debug APK | `./gradlew :app:assembleDebug` |
| Build release APK with explicit versionCode | `./gradlew :app:assembleDebug -PreleaseVersionCode=20300` |
| Lint (Kotlin style) | `./gradlew ktlintCheck` |
| Auto-fix Kotlin style | `./gradlew ktlintFormat` |
| Android Lint | `./gradlew :app:lintDebug` |

`JAVA_HOME` must point at a JDK 17 install and `ANDROID_HOME` (or `ANDROID_SDK_ROOT`) at an SDK with `platforms/android-36` + `build-tools/36.0.0`.

The unit-test suite uses Robolectric (`sdk=33` pinned via `app/src/test/resources/robolectric.properties`) so all tests run on the JVM — there is no `androidTest/` source set.

### Regenerating sprites

Two scripts produce art under `app/src/main/res/drawable-nodpi/`. Both are PEP-723 inline-deps Python scripts runnable directly via `uv` — no manual venv needed:

- **`tools/fetch_cat_sprites.py`** — verifier for `cat.png`. The cat sheet is an in-tree, original AI-generated 256×384 RGBA PNG laid out as a 4-col × 6-row LPC-style grid (walk S/N/W/E rows 0-3, sit row 4 col 0, lay row 5 col 0). The script asserts dimensions, pixel mode and SHA256 match pinned values; on mismatch it prints the new digest and exits non-zero. To replace the sheet, drop the new PNG at the canonical path, run the script, and update `PINNED_SHA` to the printed digest in the same commit.
- **`tools/generate_sprites.py`** — deterministic Pillow procedural script for **decor only** (`poop.png`, `room_bg.png`, `bowl.png`). Cat code was removed when the real cat asset shipped.

```bash
uv run tools/fetch_cat_sprites.py    # cat.png
uv run tools/generate_sprites.py     # decor PNGs
```

The committed PNGs are the runtime source of truth; the scripts just regenerate them.

## Architecture

The app is a Tamagotchi-style pet simulator. Read `docs/superpowers/specs/2026-05-08-pocket-pets-design.md` for the product spec and `docs/superpowers/plans/2026-05-08-pocket-pets.md` for the original implementation plan and rationale on every numerical constant.

### Layering
- `domain/` — pure Kotlin, no Android deps. `StatDecay`, `Mood`, `GrowthStage`, `CatSpeech`, `Pet`, `PetStats`. Heavily unit-tested.
- `data/` — Room (`db/`), repository (`repo/PetRepository`), DataStore prefs (`settings/`).
- `ui/` — Compose screens (`pet/`, `adopt/`, `select/`, `settings/`) + `nav/AppNav` single-NavHost.
- `work/` — `PetCareWorker` (periodic), `NotificationHelper`, `WorkScheduler`, `BootReceiver`.
- `di/AppContainer` — manual DI, hung off `PocketPetsApp.container`. No Hilt.

### The single load-bearing pattern: pure-function decay

`StatDecay.tick(pet, now)` is the heart of the app. It's a pure function that takes a `Pet` and the current `Instant` and returns a fresh `Pet` with stats decayed for the elapsed time and at most one new poop spawned (poops fire 30 min after `lastFedAt`, then `lastFedAt` is consumed). It is called from **two** places, intentionally:

1. `PetViewModel` calls it inside a `combine(repo.observeActive(), ticker(60s), _phrase)` flow so the UI shows always-current stats without writing to the DB on every UI tick (recompute-on-read).
2. `PetCareWorker` calls it via `PetRepository.runDecayTick()` every 30 min so background notifications are based on current state.

Care actions (`feed`, `clean`, `pet`, `talk`) all flow through `PetRepository.mutate()`, which runs `StatDecay.tick` first to bring the pet up-to-date, then applies the action, then writes once. Don't bypass this — it's the only path that keeps `lastTickAt` consistent.

### Active-pet exclusivity

Exactly one row in `pets` has `isActive=1`. `PetDao.setActiveExclusive(id)` is `@Transaction` — a SQL `UPDATE pets SET isActive = 0` followed by a targeted update. The UI uses `observeActive(): Flow<Pet?>` everywhere; switching pets goes through `PetRepository.setActive(id)`.

### Notifications: hysteresis, not edge-triggered

`NotificationHelper` reads/writes per-pet per-event flags in DataStore (`notif_flag_<petId>_<kind>`). It fires when a stat crosses `< 25`, sets the flag, and only re-fires after the stat recovers above `25 + 10` (hysteresis). Quiet hours window is checked first. `PocketPetsApp` implements `WorkManager.Configuration.Provider` so the worker initializes on-demand without a manifest provider — required for Robolectric tests too.

## Linting

- **ktlint** runs via the JLLeitschuh Gradle plugin with the default `ktlint_official` style. The Compose-aware ruleset (`io.nlopez.compose.rules:ktlint`) is added so `@Composable` PascalCase coexists with the `function-naming` rule, and `LocalDeepLinkPetId` is on the `compose_allowed_composition_locals` allowlist in `.editorconfig` — add new CompositionLocals there sparingly. ktlint version is pinned to 1.5.0 in `build.gradle.kts` (the latest plugin-bundled version compatible with compose-rules 0.4.22).
- **Android Lint** runs against `:app:lintDebug` with `abortOnError = true` and `warningsAsErrors = true`, no baseline. The single check that's disabled is `AndroidGradlePluginVersion` — bumping past AGP 8.9.x is a major Gradle/Kotlin/Compose ecosystem migration that needs its own dedicated PR, not a "fix lint" patch.
- WorkManager's `androidx.startup` content provider is explicitly removed from the manifest (see the `tools:node="remove"` block) because we self-initialise via `Configuration.Provider`; don't put it back.

## Testing gotchas

- **`PetViewModelTest` does NOT use `TestScope`.** The ViewModel launches an infinite idle-chatter ticker on its scope; if the scope shares a `TestScheduler` with `runTest`, that ticker keeps `runTest` alive forever. Tests instead pass `externalScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)` and cancel it in `finally`. The `externalScope` constructor parameter on `PetViewModel` exists *only* to support this — production code uses the default `viewModelScope`.
- Room/DataStore/repository tests use `@RunWith(RobolectricTestRunner::class)` and `Room.inMemoryDatabaseBuilder`. `PetCareWorkerTest` uses `TestListenableWorkerBuilder` directly — do **not** call `WorkManagerTestInitHelper.initializeTestWorkManager`; it conflicts with the app's own `Configuration.Provider`.
- Time is always injected via `kotlinx.datetime.Clock`. Production wires `Clock.System`; tests use `app/src/test/.../testing/FakeClock.kt`.

## Test-driven development

Follow the `superpowers:test-driven-development` skill for all production code changes (features, bug fixes, refactors).

## Versioning and CI/CD

- Conventional commits drive everything. `feat:` → minor bump (within 0.x), `fix:` → patch, anything else (`build:`, `ci:`, `docs:`, `chore:`) → no release. The repo is in 0.x with `bump-minor-pre-major: true` so a `feat:` correctly bumps the minor.
- `release-please` (config in `release-please-config.json`, manifest in `.release-please-manifest.json`) opens a release PR on every push to `main`. Merging it tags + creates the GitHub release. **All releases are marked pre-release** (`prerelease: true`) until explicitly turned off.
- The `versionName` in `app/build.gradle.kts` is bumped automatically via the `// x-release-please-version` anchor — leave that comment in place.
- `versionCode` is **not** committed-bumped; it's derived at release-build time as `major*10000 + minor*100 + patch` (floored at 100) and passed in as `-PreleaseVersionCode=N`. The committed default of `1` is a dev-build placeholder.
- The release-build job lives **inline** in `.github/workflows/release-please.yml`, not in a separate workflow listening on `release: published`. That's because `GITHUB_TOKEN`-created releases don't trigger downstream workflows (GitHub anti-recursion guard) — keep it inline unless you switch to a PAT or GitHub App token.
