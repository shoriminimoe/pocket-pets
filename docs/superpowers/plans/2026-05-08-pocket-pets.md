# Pocket Pets Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Tamagotchi-style Android app where users adopt and care for pixel-art pets (starting with a cat) that grow over real-world days, must be fed and cleaned, can be tapped/petted, and "speak" with toggleable silly translations.

**Architecture:** Single-module Android app. Kotlin + Jetpack Compose UI. MVVM with a Room-backed `PetRepository`. Pure-function `StatDecay` shared between the UI (recompute-on-read) and a 30-min `WorkManager` periodic worker (background decay + notifications). Manual DI via `AppContainer` on `Application`.

**Tech Stack:** Kotlin 2.0, Android Gradle Plugin 8.7, Jetpack Compose (BOM 2025.01.00), Room 2.6, DataStore 1.1, WorkManager 2.10, kotlinx-datetime, kotlinx-coroutines-test, JUnit 4 + Truth + Robolectric. Min SDK 24, target SDK 34.

**Reference spec:** `docs/superpowers/specs/2026-05-08-pocket-pets-design.md` — read this before starting. Stat values, decay rates, mood priority, and threshold numbers all come from there; do not invent new values.

**Sprite generation:** Sprites are committed PNGs but produced by a small Python (Pillow) script `tools/generate_sprites.py`. The script is deterministic — re-running yields identical bytes — and is the canonical way to regenerate art. The committed PNGs are the source of truth at runtime; the script is for reproducibility and future edits.

**File structure (target end state):**

```
settings.gradle.kts
build.gradle.kts                                  # root
gradle/libs.versions.toml
gradle/wrapper/...
app/build.gradle.kts
app/proguard-rules.pro
app/src/main/AndroidManifest.xml
app/src/main/kotlin/com/pocketpets/app/
├── MainActivity.kt
├── PocketPetsApp.kt
├── di/AppContainer.kt
├── data/db/{AppDatabase,Converters,PetEntity,PetDao,CareEventEntity,CareEventDao,NotifyFlagEntity,NotifyFlagDao}.kt
├── data/repo/PetRepository.kt
├── data/settings/SettingsDataStore.kt
├── domain/{Species,GrowthStage,Mood,Pet,PetStats}.kt
├── domain/StatDecay.kt
├── domain/speech/{Phrase,SpeechBank,CatSpeech}.kt
├── ui/theme/{Color,Type,Theme}.kt
├── ui/nav/AppNav.kt
├── ui/pet/{PetScreen,PetViewModel,SpriteView,SpeechBubble,StatChip}.kt
├── ui/select/{PetSelectorSheet,PetSelectorViewModel}.kt
├── ui/adopt/{AdoptPetScreen,AdoptViewModel}.kt
├── ui/settings/{SettingsScreen,SettingsViewModel}.kt
├── work/{PetCareWorker,NotificationHelper,BootReceiver}.kt
└── util/Ticker.kt
app/src/main/res/
├── drawable-nodpi/cat_baby.png, cat_juvenile.png, cat_adult.png, poop.png, room_bg.png, ...
├── values/{strings,colors,themes}.xml
└── xml/data_extraction_rules.xml, backup_rules.xml
app/src/test/kotlin/com/pocketpets/app/
├── domain/{StatDecayTest,GrowthStageTest,MoodTest}.kt
├── domain/speech/CatSpeechTest.kt
├── data/db/{PetDaoTest,CareEventDaoTest}.kt
├── data/repo/PetRepositoryTest.kt
├── ui/pet/PetViewModelTest.kt
└── work/PetCareWorkerTest.kt
tools/generate_sprites.py
docs/superpowers/specs/2026-05-08-pocket-pets-design.md
docs/superpowers/plans/2026-05-08-pocket-pets.md
```

---

## Conventions

- **Test framework:** JUnit 4 + Truth (`com.google.truth:truth`). Coroutine tests use `kotlinx-coroutines-test`. Robolectric for any test that needs an Android `Context` (Room, DataStore, WorkManager).
- **All tests live in `src/test/kotlin/`** and run on the JVM. We are not adding `androidTest`.
- **Time:** Always inject `Clock` (kotlinx-datetime `Clock`). Production uses `Clock.System`. Tests use a `FakeClock` (defined in Task 4).
- **Commits:** After every task, run `./gradlew test` from the repo root and commit only if green. Conventional-commit prefixes (`feat:`, `test:`, `chore:`, `build:`, `refactor:`, `fix:`).
- **Test verification:** "Expected: FAIL" / "Expected: PASS" outputs assume fresh runs. If you see unrelated failures, stop and investigate.

---

## Task 1: Bootstrap Gradle project

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`
- Create: `gradle/wrapper/gradle-wrapper.properties`, `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`
- Create: `gradle.properties`, `.gitignore`, `local.properties` (gitignored)

- [ ] **Step 1: Generate gradle wrapper**

If a system Gradle is available:
```bash
gradle wrapper --gradle-version 8.10
```
If not, download manually:
```bash
mkdir -p gradle/wrapper
curl -L -o gradle/wrapper/gradle-wrapper.jar \
  https://raw.githubusercontent.com/gradle/gradle/v8.10.0/gradle/wrapper/gradle-wrapper.jar
```
Then create `gradle/wrapper/gradle-wrapper.properties`:
```properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.10-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```
And create `gradlew` / `gradlew.bat` from the official Gradle 8.10 distribution (copy from a sibling project or from `https://raw.githubusercontent.com/gradle/gradle/v8.10.0/gradlew` and `.../gradlew.bat`). Make `gradlew` executable: `chmod +x gradlew`.

- [ ] **Step 2: Write `gradle/libs.versions.toml`**

```toml
[versions]
agp = "8.7.2"
kotlin = "2.0.21"
ksp = "2.0.21-1.0.27"
coreKtx = "1.13.1"
lifecycle = "2.8.7"
activityCompose = "1.9.3"
composeBom = "2025.01.00"
navigationCompose = "2.8.4"
room = "2.6.1"
datastore = "1.1.1"
workManager = "2.10.0"
kotlinxDatetime = "0.6.1"
kotlinxCoroutines = "1.9.0"
junit = "4.13.2"
truth = "1.4.4"
robolectric = "4.13"
androidxTestCore = "1.6.1"
androidxJunit = "1.2.1"
workTesting = "2.10.0"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-compose-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
androidx-compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
androidx-compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3" }
androidx-compose-material-icons = { group = "androidx.compose.material", name = "material-icons-extended" }
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }
androidx-room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
androidx-room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
androidx-room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
androidx-datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
androidx-work-runtime-ktx = { group = "androidx.work", name = "work-runtime-ktx", version.ref = "workManager" }
kotlinx-datetime = { group = "org.jetbrains.kotlinx", name = "kotlinx-datetime", version.ref = "kotlinxDatetime" }
kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "kotlinxCoroutines" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "kotlinxCoroutines" }
junit = { group = "junit", name = "junit", version.ref = "junit" }
truth = { group = "com.google.truth", name = "truth", version.ref = "truth" }
robolectric = { group = "org.robolectric", name = "robolectric", version.ref = "robolectric" }
androidx-test-core = { group = "androidx.test", name = "core-ktx", version.ref = "androidxTestCore" }
androidx-test-junit = { group = "androidx.test.ext", name = "junit-ktx", version.ref = "androidxJunit" }
androidx-room-testing = { group = "androidx.room", name = "room-testing", version.ref = "room" }
androidx-work-testing = { group = "androidx.work", name = "work-testing", version.ref = "workTesting" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

- [ ] **Step 3: Write `settings.gradle.kts`**

```kotlin
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "PocketPets"
include(":app")
```

- [ ] **Step 4: Write root `build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
```

- [ ] **Step 5: Write `gradle.properties`**

```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
```

- [ ] **Step 6: Write `.gitignore`**

```gitignore
.gradle/
build/
local.properties
.idea/
*.iml
captures/
.externalNativeBuild/
.cxx/
*.apk
*.aab
*.keystore
.kotlin/
```

- [ ] **Step 7: Verify wrapper works**

Run: `./gradlew --version`
Expected: prints "Gradle 8.10" and JVM info, no errors.

- [ ] **Step 8: Commit**

```bash
git add settings.gradle.kts build.gradle.kts gradle.properties .gitignore gradle/
git commit -m "build: scaffold gradle project with version catalog"
```

---

## Task 2: Android app module skeleton

**Files:**
- Create: `app/build.gradle.kts`, `app/proguard-rules.pro`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/kotlin/com/pocketpets/app/MainActivity.kt`
- Create: `app/src/main/kotlin/com/pocketpets/app/ui/theme/{Color,Type,Theme}.kt`
- Create: `app/src/main/res/values/{strings.xml,colors.xml,themes.xml}`
- Create: `app/src/main/res/xml/{backup_rules.xml,data_extraction_rules.xml}`

- [ ] **Step 1: Write `app/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.pocketpets.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.pocketpets.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            // debug builds expose the time-acceleration toggle
            buildConfigField("boolean", "DEBUG_TIME", "true")
        }
        release {
            buildConfigField("boolean", "DEBUG_TIME", "false")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    sourceSets["main"].kotlin.srcDir("src/main/kotlin")
    sourceSets["test"].kotlin.srcDir("src/test/kotlin")

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.coroutines.android)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.junit)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.kotlinx.coroutines.test)
}
```

- [ ] **Step 2: Write `app/proguard-rules.pro`**

```proguard
# (empty for v1; reserved for future use)
```

- [ ] **Step 3: Write `app/src/main/AndroidManifest.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />

    <application
        android:name=".PocketPetsApp"
        android:allowBackup="true"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:fullBackupContent="@xml/backup_rules"
        android:icon="@android:drawable/sym_def_app_icon"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.PocketPets">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@style/Theme.PocketPets">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <receiver
            android:name=".work.BootReceiver"
            android:enabled="true"
            android:exported="true"
            android:permission="android.permission.RECEIVE_BOOT_COMPLETED">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
            </intent-filter>
        </receiver>
    </application>
</manifest>
```

Note: `PocketPetsApp` and `BootReceiver` don't exist yet — that's expected; they'll be added in later tasks. The manifest references will resolve before we build.

- [ ] **Step 4: Write `app/src/main/res/values/strings.xml`**

```xml
<resources>
    <string name="app_name">Pocket Pets</string>
</resources>
```

- [ ] **Step 5: Write `app/src/main/res/values/colors.xml`**

```xml
<resources>
    <color name="ink">#1A1A2E</color>
    <color name="cream">#FFF6E5</color>
    <color name="rose">#F26A8D</color>
    <color name="mint">#7AD7C5</color>
    <color name="butter">#FFD56B</color>
</resources>
```

- [ ] **Step 6: Write `app/src/main/res/values/themes.xml`**

```xml
<resources>
    <style name="Theme.PocketPets" parent="android:Theme.Material.Light.NoActionBar" />
</resources>
```

- [ ] **Step 7: Write `app/src/main/res/xml/backup_rules.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<full-backup-content />
```

- [ ] **Step 8: Write `app/src/main/res/xml/data_extraction_rules.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<data-extraction-rules>
    <cloud-backup />
    <device-transfer />
</data-extraction-rules>
```

- [ ] **Step 9: Write theme files**

Create `app/src/main/kotlin/com/pocketpets/app/ui/theme/Color.kt`:

```kotlin
package com.pocketpets.app.ui.theme

import androidx.compose.ui.graphics.Color

val Ink = Color(0xFF1A1A2E)
val Cream = Color(0xFFFFF6E5)
val Rose = Color(0xFFF26A8D)
val Mint = Color(0xFF7AD7C5)
val Butter = Color(0xFFFFD56B)
val WallPaper = Color(0xFFE9C9B6)
val Floor = Color(0xFF8B5A3C)
```

Create `app/src/main/kotlin/com/pocketpets/app/ui/theme/Type.kt`:

```kotlin
package com.pocketpets.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val PocketPetsTypography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
    ),
)
```

Create `app/src/main/kotlin/com/pocketpets/app/ui/theme/Theme.kt`:

```kotlin
package com.pocketpets.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = Rose,
    secondary = Mint,
    tertiary = Butter,
    background = Cream,
    surface = Cream,
    onPrimary = Cream,
    onSecondary = Ink,
    onTertiary = Ink,
    onBackground = Ink,
    onSurface = Ink,
)

@Composable
fun PocketPetsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = PocketPetsTypography,
        content = content,
    )
}
```

- [ ] **Step 10: Write `MainActivity.kt`**

```kotlin
package com.pocketpets.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.pocketpets.app.ui.theme.PocketPetsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PocketPetsTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Placeholder()
                }
            }
        }
    }
}

@Composable
private fun Placeholder() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Pocket Pets")
    }
}
```

- [ ] **Step 11: Stub `PocketPetsApp` and `BootReceiver` so the manifest resolves**

Create `app/src/main/kotlin/com/pocketpets/app/PocketPetsApp.kt`:

```kotlin
package com.pocketpets.app

import android.app.Application

class PocketPetsApp : Application()
```

Create `app/src/main/kotlin/com/pocketpets/app/work/BootReceiver.kt`:

```kotlin
package com.pocketpets.app.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Filled in once PetCareWorker exists.
    }
}
```

- [ ] **Step 12: Verify the project builds**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`. (First run will download a lot — be patient.)

- [ ] **Step 13: Commit**

```bash
git add app/ settings.gradle.kts
git commit -m "build: add app module with compose skeleton"
```

---

## Task 3: Domain enums and Phrase value type

**Files:**
- Create: `app/src/main/kotlin/com/pocketpets/app/domain/Species.kt`
- Create: `app/src/main/kotlin/com/pocketpets/app/domain/GrowthStage.kt`
- Create: `app/src/main/kotlin/com/pocketpets/app/domain/Mood.kt`
- Create: `app/src/main/kotlin/com/pocketpets/app/domain/speech/Phrase.kt`

- [ ] **Step 1: Write the four files**

`Species.kt`:
```kotlin
package com.pocketpets.app.domain

enum class Species { CAT }
```

`GrowthStage.kt`:
```kotlin
package com.pocketpets.app.domain

enum class GrowthStage { BABY, JUVENILE, ADULT }
```

`Mood.kt`:
```kotlin
package com.pocketpets.app.domain

enum class Mood { IDLE, HAPPY, HUNGRY, GROSSED_OUT, SAD, SLEEPY }
```

`speech/Phrase.kt`:
```kotlin
package com.pocketpets.app.domain.speech

data class Phrase(val animal: String, val translation: String)
```

- [ ] **Step 2: Build to confirm compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/pocketpets/app/domain/
git commit -m "feat: add domain enums and Phrase type"
```

---

## Task 4: Pet, PetStats, FakeClock test helper

**Files:**
- Create: `app/src/main/kotlin/com/pocketpets/app/domain/Pet.kt`
- Create: `app/src/main/kotlin/com/pocketpets/app/domain/PetStats.kt`
- Create: `app/src/test/kotlin/com/pocketpets/app/testing/FakeClock.kt`

- [ ] **Step 1: Write `PetStats.kt`**

```kotlin
package com.pocketpets.app.domain

data class PetStats(
    val hunger: Float,
    val cleanliness: Float,
    val happiness: Float,
    val energy: Float,
) {
    init {
        require(hunger in 0f..100f) { "hunger out of range: $hunger" }
        require(cleanliness in 0f..100f) { "cleanliness out of range: $cleanliness" }
        require(happiness in 0f..100f) { "happiness out of range: $happiness" }
        require(energy in 0f..100f) { "energy out of range: $energy" }
    }

    companion object {
        val FULL = PetStats(100f, 100f, 100f, 100f)
    }
}
```

- [ ] **Step 2: Write `Pet.kt`**

```kotlin
package com.pocketpets.app.domain

import kotlinx.datetime.Instant

data class Pet(
    val id: Long,
    val name: String,
    val species: Species,
    val bornAt: Instant,
    val stats: PetStats,
    val lastTickAt: Instant,
    val isActive: Boolean,
    val poopCount: Int,
    val lastFedAt: Instant?,
) {
    init {
        require(poopCount in 0..MAX_POOPS) { "poopCount out of range: $poopCount" }
    }

    companion object {
        const val MAX_POOPS = 4
    }
}
```

- [ ] **Step 3: Write `FakeClock.kt`**

```kotlin
package com.pocketpets.app.testing

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class FakeClock(initial: Instant) : Clock {
    private var current: Instant = initial
    override fun now(): Instant = current
    fun advanceBy(durationMs: Long) {
        current = Instant.fromEpochMilliseconds(current.toEpochMilliseconds() + durationMs)
    }
    fun setTo(instant: Instant) { current = instant }
}
```

- [ ] **Step 4: Build and commit**

Run: `./gradlew :app:compileDebugKotlin :app:compileDebugUnitTestKotlin`
Expected: `BUILD SUCCESSFUL`.

```bash
git add app/src/main/kotlin/com/pocketpets/app/domain/Pet.kt \
        app/src/main/kotlin/com/pocketpets/app/domain/PetStats.kt \
        app/src/test/kotlin/com/pocketpets/app/testing/FakeClock.kt
git commit -m "feat: add Pet, PetStats, and FakeClock helper"
```

---

## Task 5: GrowthStage.fromAge() (TDD)

**Files:**
- Modify: `app/src/main/kotlin/com/pocketpets/app/domain/GrowthStage.kt`
- Create: `app/src/test/kotlin/com/pocketpets/app/domain/GrowthStageTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.pocketpets.app.domain

import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.Instant
import org.junit.Test
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

class GrowthStageTest {
    private val born = Instant.parse("2026-01-01T00:00:00Z")

    @Test fun `0 days old is BABY`() {
        assertThat(GrowthStage.fromAge(born, born)).isEqualTo(GrowthStage.BABY)
    }

    @Test fun `2 days old is BABY`() {
        assertThat(GrowthStage.fromAge(born, born.plus(2.days))).isEqualTo(GrowthStage.BABY)
    }

    @Test fun `exactly 3 days old is JUVENILE`() {
        assertThat(GrowthStage.fromAge(born, born.plus(3.days))).isEqualTo(GrowthStage.JUVENILE)
    }

    @Test fun `6 days old is JUVENILE`() {
        assertThat(GrowthStage.fromAge(born, born.plus(6.days))).isEqualTo(GrowthStage.JUVENILE)
    }

    @Test fun `exactly 7 days old is ADULT`() {
        assertThat(GrowthStage.fromAge(born, born.plus(7.days))).isEqualTo(GrowthStage.ADULT)
    }

    @Test fun `30 days old is ADULT`() {
        assertThat(GrowthStage.fromAge(born, born.plus(30.days))).isEqualTo(GrowthStage.ADULT)
    }

    @Test fun `2 days 23 hours is BABY (just under boundary)`() {
        assertThat(GrowthStage.fromAge(born, born.plus(2.days).plus(23.hours))).isEqualTo(GrowthStage.BABY)
    }

    @Test
    fun `Instant import compiles`() {
        // Sanity to ensure kotlinx-datetime types resolve
        assertThat(born.toString()).contains("2026")
    }
}

private fun Instant.plus(d: kotlin.time.Duration): Instant =
    Instant.fromEpochMilliseconds(this.toEpochMilliseconds() + d.inWholeMilliseconds)
```

- [ ] **Step 2: Run test, expect failure**

Run: `./gradlew :app:testDebugUnitTest --tests "com.pocketpets.app.domain.GrowthStageTest"`
Expected: FAIL with "unresolved reference: fromAge".

- [ ] **Step 3: Implement `fromAge`**

Modify `GrowthStage.kt`:
```kotlin
package com.pocketpets.app.domain

import kotlinx.datetime.Instant

enum class GrowthStage {
    BABY, JUVENILE, ADULT;

    companion object {
        private const val MS_PER_DAY = 24L * 60 * 60 * 1000

        fun fromAge(bornAt: Instant, now: Instant): GrowthStage {
            val days = (now.toEpochMilliseconds() - bornAt.toEpochMilliseconds()) / MS_PER_DAY
            return when {
                days < 3 -> BABY
                days < 7 -> JUVENILE
                else -> ADULT
            }
        }
    }
}
```

- [ ] **Step 4: Run tests, expect pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.pocketpets.app.domain.GrowthStageTest"`
Expected: PASS, 8 tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/pocketpets/app/domain/GrowthStage.kt \
        app/src/test/kotlin/com/pocketpets/app/domain/GrowthStageTest.kt
git commit -m "feat: implement GrowthStage.fromAge with day-boundary tests"
```

---

## Task 6: Mood derivation (TDD)

**Files:**
- Modify: `app/src/main/kotlin/com/pocketpets/app/domain/Mood.kt`
- Create: `app/src/test/kotlin/com/pocketpets/app/domain/MoodTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.pocketpets.app.domain

import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.Test

class MoodTest {
    private fun pet(
        hunger: Float = 80f,
        cleanliness: Float = 80f,
        happiness: Float = 80f,
        energy: Float = 80f,
        poopCount: Int = 0,
    ): Pet = Pet(
        id = 1L,
        name = "Test",
        species = Species.CAT,
        bornAt = Instant.parse("2026-01-01T00:00:00Z"),
        stats = PetStats(hunger, cleanliness, happiness, energy),
        lastTickAt = Instant.parse("2026-01-01T00:00:00Z"),
        isActive = true,
        poopCount = poopCount,
        lastFedAt = null,
    )

    private val noon = LocalDateTime(2026, 1, 1, 12, 0).toInstant(TimeZone.UTC)
    private val zone = TimeZone.UTC

    @Test fun `happy when stats good and happiness high`() {
        val m = Mood.from(pet(happiness = 80f), noon, zone)
        assertThat(m).isEqualTo(Mood.HAPPY)
    }

    @Test fun `idle when stats good but happiness mid`() {
        val m = Mood.from(pet(happiness = 50f), noon, zone)
        assertThat(m).isEqualTo(Mood.IDLE)
    }

    @Test fun `hungry beats sad when both apply`() {
        val m = Mood.from(pet(hunger = 20f, happiness = 20f), noon, zone)
        assertThat(m).isEqualTo(Mood.HUNGRY)
    }

    @Test fun `grossed out when low cleanliness`() {
        val m = Mood.from(pet(cleanliness = 20f), noon, zone)
        assertThat(m).isEqualTo(Mood.GROSSED_OUT)
    }

    @Test fun `grossed out when 2 poops even with high cleanliness`() {
        val m = Mood.from(pet(cleanliness = 80f, poopCount = 2), noon, zone)
        assertThat(m).isEqualTo(Mood.GROSSED_OUT)
    }

    @Test fun `hungry beats grossed out`() {
        val m = Mood.from(pet(hunger = 20f, cleanliness = 20f), noon, zone)
        assertThat(m).isEqualTo(Mood.HUNGRY)
    }

    @Test fun `sleepy when low energy`() {
        val m = Mood.from(pet(energy = 20f), noon, zone)
        assertThat(m).isEqualTo(Mood.SLEEPY)
    }

    @Test fun `sleepy during sleep window even at full energy`() {
        val night = LocalDateTime(2026, 1, 1, 23, 0).toInstant(TimeZone.UTC)
        val m = Mood.from(pet(), night, zone)
        assertThat(m).isEqualTo(Mood.SLEEPY)
    }

    @Test fun `sleepy at 6am is true`() {
        val morn = LocalDateTime(2026, 1, 1, 6, 0).toInstant(TimeZone.UTC)
        assertThat(Mood.from(pet(), morn, zone)).isEqualTo(Mood.SLEEPY)
    }

    @Test fun `not sleepy at 7am`() {
        val morn = LocalDateTime(2026, 1, 1, 7, 0).toInstant(TimeZone.UTC)
        assertThat(Mood.from(pet(), morn, zone)).isEqualTo(Mood.HAPPY)
    }

    @Test fun `sad when only happiness low`() {
        val m = Mood.from(pet(happiness = 20f), noon, zone)
        assertThat(m).isEqualTo(Mood.SAD)
    }
}
```

- [ ] **Step 2: Run, expect failure**

Run: `./gradlew :app:testDebugUnitTest --tests "com.pocketpets.app.domain.MoodTest"`
Expected: FAIL with "unresolved reference: from".

- [ ] **Step 3: Implement `Mood.from`**

Modify `Mood.kt`:
```kotlin
package com.pocketpets.app.domain

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

enum class Mood {
    IDLE, HAPPY, HUNGRY, GROSSED_OUT, SAD, SLEEPY;

    companion object {
        // Sleep window is [22:00, 07:00) device-local
        private const val SLEEP_START_HOUR = 22
        private const val WAKE_HOUR = 7

        fun from(pet: Pet, now: Instant, zone: TimeZone): Mood {
            val hour = now.toLocalDateTime(zone).hour
            val inSleepWindow = hour >= SLEEP_START_HOUR || hour < WAKE_HOUR

            // Priority: HUNGRY > GROSSED_OUT > SAD > SLEEPY > HAPPY > IDLE
            return when {
                pet.stats.hunger < 30f -> HUNGRY
                pet.stats.cleanliness < 30f || pet.poopCount >= 2 -> GROSSED_OUT
                pet.stats.happiness < 30f -> SAD
                pet.stats.energy < 30f || inSleepWindow -> SLEEPY
                pet.stats.happiness > 70f -> HAPPY
                else -> IDLE
            }
        }
    }
}
```

- [ ] **Step 4: Run, expect pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.pocketpets.app.domain.MoodTest"`
Expected: PASS, 11 tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/pocketpets/app/domain/Mood.kt \
        app/src/test/kotlin/com/pocketpets/app/domain/MoodTest.kt
git commit -m "feat: implement Mood.from with priority ordering"
```

---

## Task 7: StatDecay.tick (TDD)

**Files:**
- Create: `app/src/main/kotlin/com/pocketpets/app/domain/StatDecay.kt`
- Create: `app/src/test/kotlin/com/pocketpets/app/domain/StatDecayTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.pocketpets.app.domain

import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.Instant
import org.junit.Test

class StatDecayTest {
    private val t0 = Instant.parse("2026-01-01T12:00:00Z")
    private fun pet(
        hunger: Float = 100f,
        cleanliness: Float = 100f,
        happiness: Float = 100f,
        energy: Float = 100f,
        poopCount: Int = 0,
        lastTickAt: Instant = t0,
        lastFedAt: Instant? = null,
    ) = Pet(
        id = 1, name = "Test", species = Species.CAT,
        bornAt = t0, stats = PetStats(hunger, cleanliness, happiness, energy),
        lastTickAt = lastTickAt, isActive = true,
        poopCount = poopCount, lastFedAt = lastFedAt,
    )
    private fun Instant.plusHours(h: Int) =
        Instant.fromEpochMilliseconds(toEpochMilliseconds() + h * 3_600_000L)

    @Test fun `no time elapsed = no change`() {
        val p = pet()
        val out = StatDecay.tick(p, t0)
        assertThat(out.stats).isEqualTo(p.stats)
        assertThat(out.lastTickAt).isEqualTo(t0)
    }

    @Test fun `hunger decays 8 per hour`() {
        val out = StatDecay.tick(pet(), t0.plusHours(1))
        assertThat(out.stats.hunger).isWithin(0.01f).of(92f)
    }

    @Test fun `cleanliness decays 3 per hour with no poops`() {
        val out = StatDecay.tick(pet(poopCount = 0), t0.plusHours(2))
        assertThat(out.stats.cleanliness).isWithin(0.01f).of(94f)
    }

    @Test fun `cleanliness decays faster with poops`() {
        // 3 base + 5 per poop * 2 poops = 13/hr
        val out = StatDecay.tick(pet(poopCount = 2), t0.plusHours(1))
        assertThat(out.stats.cleanliness).isWithin(0.01f).of(87f)
    }

    @Test fun `happiness decays 2 per hour`() {
        val out = StatDecay.tick(pet(), t0.plusHours(3))
        assertThat(out.stats.happiness).isWithin(0.01f).of(94f)
    }

    @Test fun `energy decays 4 per hour while awake`() {
        // t0 = noon UTC, +1h = 1pm — awake.
        val out = StatDecay.tick(pet(), t0.plusHours(1))
        assertThat(out.stats.energy).isWithin(0.01f).of(96f)
    }

    @Test fun `stats clamp at 0`() {
        val out = StatDecay.tick(pet(hunger = 5f), t0.plusHours(2))
        assertThat(out.stats.hunger).isEqualTo(0f)
    }

    @Test fun `lastTickAt advances`() {
        val later = t0.plusHours(5)
        val out = StatDecay.tick(pet(), later)
        assertThat(out.lastTickAt).isEqualTo(later)
    }

    @Test fun `poop spawns 30 minutes after feeding when none scheduled`() {
        val fed = t0
        val now = Instant.fromEpochMilliseconds(t0.toEpochMilliseconds() + 35 * 60_000L)
        val out = StatDecay.tick(pet(poopCount = 0, lastFedAt = fed), now)
        assertThat(out.poopCount).isEqualTo(1)
    }

    @Test fun `poop does not spawn before 30 min`() {
        val fed = t0
        val now = Instant.fromEpochMilliseconds(t0.toEpochMilliseconds() + 25 * 60_000L)
        val out = StatDecay.tick(pet(poopCount = 0, lastFedAt = fed), now)
        assertThat(out.poopCount).isEqualTo(0)
    }

    @Test fun `poop count caps at MAX_POOPS`() {
        // simulate way past poop time, already at cap
        val fed = t0
        val now = Instant.fromEpochMilliseconds(t0.toEpochMilliseconds() + 24 * 3_600_000L)
        val out = StatDecay.tick(pet(poopCount = Pet.MAX_POOPS, lastFedAt = fed), now)
        assertThat(out.poopCount).isEqualTo(Pet.MAX_POOPS)
    }

    @Test fun `going backwards in time is a no-op`() {
        val earlier = Instant.fromEpochMilliseconds(t0.toEpochMilliseconds() - 1000)
        val out = StatDecay.tick(pet(), earlier)
        assertThat(out).isEqualTo(pet())
    }
}
```

- [ ] **Step 2: Run, expect failure**

Run: `./gradlew :app:testDebugUnitTest --tests "com.pocketpets.app.domain.StatDecayTest"`
Expected: FAIL with "unresolved reference: StatDecay".

- [ ] **Step 3: Implement `StatDecay`**

```kotlin
package com.pocketpets.app.domain

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

object StatDecay {
    private const val HUNGER_PER_HOUR = 8f
    private const val CLEAN_PER_HOUR = 3f
    private const val CLEAN_PER_POOP_HOUR = 5f
    private const val HAPPINESS_PER_HOUR = 2f
    private const val ENERGY_PER_HOUR = 4f
    private const val POOP_DELAY_MS = 30L * 60 * 1000

    private val zone = TimeZone.UTC // Decay is wall-time; for sleep-window logic we use UTC
                                    // since we just want a consistent reference.
                                    // (Per-pet local-time sleep is handled separately in Mood.)

    fun tick(pet: Pet, now: Instant): Pet {
        val elapsedMs = now.toEpochMilliseconds() - pet.lastTickAt.toEpochMilliseconds()
        if (elapsedMs <= 0) return pet
        val hours = elapsedMs / 3_600_000.0

        val newHunger = clamp(pet.stats.hunger - (HUNGER_PER_HOUR * hours).toFloat())
        val cleanRate = CLEAN_PER_HOUR + CLEAN_PER_POOP_HOUR * pet.poopCount
        val newCleanliness = clamp(pet.stats.cleanliness - (cleanRate * hours).toFloat())
        val newHappiness = clamp(pet.stats.happiness - (HAPPINESS_PER_HOUR * hours).toFloat())

        // Energy: decays only while awake. We approximate by checking the midpoint.
        val midpointMs = pet.lastTickAt.toEpochMilliseconds() + elapsedMs / 2
        val midHour = Instant.fromEpochMilliseconds(midpointMs).toLocalDateTime(zone).hour
        val awake = midHour in 7..21
        val newEnergy = if (awake) {
            clamp(pet.stats.energy - (ENERGY_PER_HOUR * hours).toFloat())
        } else {
            // recover during sleep at +6/hr
            clamp(pet.stats.energy + (6f * hours).toFloat())
        }

        // Poop spawning: if there's a lastFedAt and it's been >=30 min,
        // and we haven't already produced the poop for this feeding, add one (cap at MAX_POOPS).
        val newPoopCount = if (
            pet.lastFedAt != null &&
            now.toEpochMilliseconds() - pet.lastFedAt.toEpochMilliseconds() >= POOP_DELAY_MS &&
            pet.poopCount < Pet.MAX_POOPS &&
            // we treat lastFedAt > lastTickAt as "this feeding hasn't produced its poop yet"
            pet.lastFedAt > pet.lastTickAt - POOP_DELAY_MS_DURATION
        ) {
            pet.poopCount + 1
        } else {
            pet.poopCount
        }

        return pet.copy(
            stats = PetStats(newHunger, newCleanliness, newHappiness, newEnergy),
            lastTickAt = now,
            poopCount = newPoopCount,
        )
    }

    private fun clamp(v: Float): Float = v.coerceIn(0f, 100f)

    // Helper so the spawn condition reads sanely
    private val POOP_DELAY_MS_DURATION: kotlin.time.Duration get() =
        kotlin.time.Duration.parseIsoString("PT30M")
}

private operator fun Instant.minus(d: kotlin.time.Duration): Instant =
    Instant.fromEpochMilliseconds(toEpochMilliseconds() - d.inWholeMilliseconds)
```

- [ ] **Step 4: Run, expect pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.pocketpets.app.domain.StatDecayTest"`
Expected: PASS, 12 tests. If "poop spawns" tests fail, the spawn-once invariant in step 3 needs adjustment — set `pet.lastFedAt = null` in the returned `Pet` after spawning to mark "consumed" and update tests + the repository to re-set `lastFedAt` only when feeding. Apply this fix:

Replace the poop block with:
```kotlin
val (newPoopCount, newLastFedAt) = if (
    pet.lastFedAt != null &&
    now.toEpochMilliseconds() - pet.lastFedAt.toEpochMilliseconds() >= POOP_DELAY_MS &&
    pet.poopCount < Pet.MAX_POOPS
) {
    (pet.poopCount + 1) to null
} else if (pet.lastFedAt != null && pet.poopCount >= Pet.MAX_POOPS &&
           now.toEpochMilliseconds() - pet.lastFedAt.toEpochMilliseconds() >= POOP_DELAY_MS) {
    pet.poopCount to null  // drop the feeding without spawning
} else {
    pet.poopCount to pet.lastFedAt
}
```
And in the `pet.copy(...)` call also pass `lastFedAt = newLastFedAt`. Remove the unused `POOP_DELAY_MS_DURATION` and the `Instant.minus` extension.

Re-run the test command. Expected: PASS, 12 tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/pocketpets/app/domain/StatDecay.kt \
        app/src/test/kotlin/com/pocketpets/app/domain/StatDecayTest.kt
git commit -m "feat: implement StatDecay.tick with hunger/clean/happy/energy and poop spawning"
```

---

## Task 8: SpeechBank + CatSpeech (TDD)

**Files:**
- Create: `app/src/main/kotlin/com/pocketpets/app/domain/speech/SpeechBank.kt`
- Create: `app/src/main/kotlin/com/pocketpets/app/domain/speech/CatSpeech.kt`
- Create: `app/src/test/kotlin/com/pocketpets/app/domain/speech/CatSpeechTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.pocketpets.app.domain.speech

import com.google.common.truth.Truth.assertThat
import com.pocketpets.app.domain.Mood
import org.junit.Test
import kotlin.random.Random

class CatSpeechTest {
    @Test fun `every mood has at least 4 phrases`() {
        Mood.values().forEach { mood ->
            val phrases = CatSpeech.forMood(mood)
            assertThat(phrases).hasSize(at_least = 4)
        }
    }
    @Test fun `phrases have non-blank animal and translation`() {
        Mood.values().forEach { mood ->
            CatSpeech.forMood(mood).forEach { p ->
                assertThat(p.animal.isNotBlank()).isTrue()
                assertThat(p.translation.isNotBlank()).isTrue()
            }
        }
    }
    @Test fun `random returns a phrase from the mood category`() {
        val rng = Random(42)
        val mood = Mood.HUNGRY
        val pool = CatSpeech.forMood(mood)
        repeat(20) {
            val picked = CatSpeech.random(mood, rng)
            assertThat(pool).contains(picked)
        }
    }
}

// Truth helper since hasSize doesn't take "at least" out of the box.
private fun com.google.common.truth.IterableSubject.hasSize(at_least: Int) {
    @Suppress("UNCHECKED_CAST")
    val list = (actual() as Iterable<*>).toList()
    if (list.size < at_least) failWithActual("expected size at least", at_least)
}
```

Note: Truth's `IterableSubject.actual()` is package-private; replace with the simpler approach below if compilation fails.

If the helper above doesn't compile, replace the first test with:
```kotlin
@Test fun `every mood has at least 4 phrases`() {
    Mood.values().forEach { mood ->
        assertThat(CatSpeech.forMood(mood).size).isAtLeast(4)
    }
}
```
And drop the helper extension.

- [ ] **Step 2: Run, expect failure**

Run: `./gradlew :app:testDebugUnitTest --tests "com.pocketpets.app.domain.speech.CatSpeechTest"`
Expected: FAIL with "unresolved reference: CatSpeech".

- [ ] **Step 3: Write `SpeechBank.kt`**

```kotlin
package com.pocketpets.app.domain.speech

import com.pocketpets.app.domain.Mood
import kotlin.random.Random

interface SpeechBank {
    fun forMood(mood: Mood): List<Phrase>
    fun random(mood: Mood, rng: Random = Random.Default): Phrase {
        val pool = forMood(mood)
        require(pool.isNotEmpty()) { "No phrases for mood $mood" }
        return pool[rng.nextInt(pool.size)]
    }
}
```

- [ ] **Step 4: Write `CatSpeech.kt`**

```kotlin
package com.pocketpets.app.domain.speech

import com.pocketpets.app.domain.Mood

object CatSpeech : SpeechBank {
    private val hungry = listOf(
        Phrase("Mrrowwww?? Mrow mrow!", "I have not eaten in NINE HUNDRED YEARS."),
        Phrase("Mrp? Mrp mrp mrp.", "The bowl is, technically, empty. Fix it."),
        Phrase("MEEOOOWWW.", "Refrigerate me a fish, immediately."),
        Phrase("Mrow. Mrow. Mrow.", "Hunger update: critical. Snack me."),
        Phrase("Mrrrrrr-OWW.", "Did the food disappear? Magic? Tragedy?"),
    )
    private val grossedOut = listOf(
        Phrase("Pfffft. Hssss.", "I demand an immediate housekeeping service."),
        Phrase("Mrow? *sniff* Hrk.", "Who left this here. Was it me? It was me."),
        Phrase("Brrrt. Mrowww.", "I am too dignified for this filth."),
        Phrase("Mrrrr...", "Please address the situation. I will not name names."),
    )
    private val sad = listOf(
        Phrase("Mew. Mew.", "Have you... forgotten me?"),
        Phrase("Mrrrr...", "I miss you. Even though you're right there."),
        Phrase("Mrp.", "I'm being brave. But it's hard."),
        Phrase("Mew?", "Did I do something wrong? Was it the curtains?"),
    )
    private val happy = listOf(
        Phrase("Prrrrrr! Mrrr!", "You are my favorite human in this house."),
        Phrase("Mrrrt!", "Today is officially the best day."),
        Phrase("Brrrr-mrow!", "I'd like to declare you Employee of the Month."),
        Phrase("Prrrrrt prrrrrt!", "I have decided to allow you to continue existing."),
    )
    private val idle = listOf(
        Phrase("Mrp.", "Just observing. Carry on."),
        Phrase("Mrow.", "Have you considered opening that door? For no reason."),
        Phrase("...", "Thinking deeply about the wallpaper."),
        Phrase("Brrrt?", "Statistically, there should be a snack here."),
        Phrase("Mrrrrr.", "I am content. Suspicious, but content."),
    )
    private val sleepy = listOf(
        Phrase("Yawwwn... mrow.", "Goodnight cruel world. See you in 14 hours."),
        Phrase("Mrrrr... zzz.", "Do not disturb the sacred nap."),
        Phrase("Prrr... prr...", "I'll dream of mice. The good ones."),
        Phrase("Mrp. *blinks slowly*", "Loading new dream. Please wait."),
    )

    override fun forMood(mood: Mood): List<Phrase> = when (mood) {
        Mood.HUNGRY -> hungry
        Mood.GROSSED_OUT -> grossedOut
        Mood.SAD -> sad
        Mood.HAPPY -> happy
        Mood.IDLE -> idle
        Mood.SLEEPY -> sleepy
    }
}
```

- [ ] **Step 5: Run, expect pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.pocketpets.app.domain.speech.CatSpeechTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/pocketpets/app/domain/speech/ \
        app/src/test/kotlin/com/pocketpets/app/domain/speech/
git commit -m "feat: add SpeechBank interface and CatSpeech with silly translations"
```

---

## Task 9: Room database, entities, DAOs, converters

**Files:**
- Create: `app/src/main/kotlin/com/pocketpets/app/data/db/Converters.kt`
- Create: `app/src/main/kotlin/com/pocketpets/app/data/db/PetEntity.kt`
- Create: `app/src/main/kotlin/com/pocketpets/app/data/db/PetDao.kt`
- Create: `app/src/main/kotlin/com/pocketpets/app/data/db/CareEventEntity.kt`
- Create: `app/src/main/kotlin/com/pocketpets/app/data/db/CareEventDao.kt`
- Create: `app/src/main/kotlin/com/pocketpets/app/data/db/AppDatabase.kt`
- Create: `app/src/test/kotlin/com/pocketpets/app/data/db/PetDaoTest.kt`
- Create: `app/src/test/resources/robolectric.properties`

- [ ] **Step 1: Write `Converters.kt`**

```kotlin
package com.pocketpets.app.data.db

import androidx.room.TypeConverter
import com.pocketpets.app.domain.Species
import kotlinx.datetime.Instant

class Converters {
    @TypeConverter fun instantToLong(i: Instant?): Long? = i?.toEpochMilliseconds()
    @TypeConverter fun longToInstant(l: Long?): Instant? = l?.let { Instant.fromEpochMilliseconds(it) }
    @TypeConverter fun speciesToString(s: Species): String = s.name
    @TypeConverter fun stringToSpecies(s: String): Species = Species.valueOf(s)
}
```

- [ ] **Step 2: Write `PetEntity.kt`**

```kotlin
package com.pocketpets.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.pocketpets.app.domain.Pet
import com.pocketpets.app.domain.PetStats
import com.pocketpets.app.domain.Species
import kotlinx.datetime.Instant

@Entity(tableName = "pets")
data class PetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val species: Species,
    val bornAt: Instant,
    val hunger: Float,
    val cleanliness: Float,
    val happiness: Float,
    val energy: Float,
    val lastTickAt: Instant,
    val isActive: Boolean,
    val poopCount: Int,
    val lastFedAt: Instant?,
) {
    fun toDomain(): Pet = Pet(
        id = id, name = name, species = species, bornAt = bornAt,
        stats = PetStats(hunger, cleanliness, happiness, energy),
        lastTickAt = lastTickAt, isActive = isActive,
        poopCount = poopCount, lastFedAt = lastFedAt,
    )
    companion object {
        fun fromDomain(p: Pet) = PetEntity(
            id = p.id, name = p.name, species = p.species, bornAt = p.bornAt,
            hunger = p.stats.hunger, cleanliness = p.stats.cleanliness,
            happiness = p.stats.happiness, energy = p.stats.energy,
            lastTickAt = p.lastTickAt, isActive = p.isActive,
            poopCount = p.poopCount, lastFedAt = p.lastFedAt,
        )
    }
}
```

- [ ] **Step 3: Write `PetDao.kt`**

```kotlin
package com.pocketpets.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PetDao {
    @Query("SELECT * FROM pets ORDER BY id ASC")
    fun observeAll(): Flow<List<PetEntity>>

    @Query("SELECT * FROM pets WHERE isActive = 1 LIMIT 1")
    fun observeActive(): Flow<PetEntity?>

    @Query("SELECT * FROM pets WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): PetEntity?

    @Query("SELECT * FROM pets")
    suspend fun getAll(): List<PetEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(pet: PetEntity): Long

    @Update
    suspend fun update(pet: PetEntity)

    @Query("UPDATE pets SET isActive = 0")
    suspend fun clearActiveFlag()

    @Query("UPDATE pets SET isActive = 1 WHERE id = :id")
    suspend fun setActive(id: Long)

    @Transaction
    suspend fun setActiveExclusive(id: Long) {
        clearActiveFlag()
        setActive(id)
    }
}
```

- [ ] **Step 4: Write `CareEventEntity.kt` and `CareEventDao.kt`**

```kotlin
package com.pocketpets.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant

@Entity(tableName = "care_events")
data class CareEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val petId: Long,
    val kind: String, // "feed" | "clean" | "pet" | "talk" | "auto_tick"
    val at: Instant,
)
```

```kotlin
package com.pocketpets.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface CareEventDao {
    @Insert
    suspend fun insert(event: CareEventEntity)

    @Query("DELETE FROM care_events WHERE petId = :petId AND id NOT IN " +
           "(SELECT id FROM care_events WHERE petId = :petId ORDER BY id DESC LIMIT 100)")
    suspend fun pruneToLast100(petId: Long)
}
```

- [ ] **Step 5: Write `AppDatabase.kt`**

```kotlin
package com.pocketpets.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [PetEntity::class, CareEventEntity::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun petDao(): PetDao
    abstract fun careEventDao(): CareEventDao
}
```

- [ ] **Step 6: Write `robolectric.properties`**

`app/src/test/resources/robolectric.properties`:
```
sdk=33
```

- [ ] **Step 7: Write the DAO test**

```kotlin
package com.pocketpets.app.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.pocketpets.app.domain.Species
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PetDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: PetDao

    @Before fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.petDao()
    }

    @After fun teardown() { db.close() }

    private fun newPet(name: String = "Whiskers", active: Boolean = false) = PetEntity(
        name = name, species = Species.CAT,
        bornAt = Instant.parse("2026-01-01T00:00:00Z"),
        hunger = 100f, cleanliness = 100f, happiness = 100f, energy = 100f,
        lastTickAt = Instant.parse("2026-01-01T00:00:00Z"),
        isActive = active, poopCount = 0, lastFedAt = null,
    )

    @Test fun `insert and observeAll`() = runTest {
        val id = dao.insert(newPet())
        assertThat(id).isGreaterThan(0L)
        val all = dao.observeAll().first()
        assertThat(all).hasSize(1)
        assertThat(all[0].name).isEqualTo("Whiskers")
    }

    @Test fun `setActiveExclusive flips one to active and others to inactive`() = runTest {
        val a = dao.insert(newPet("A", active = true))
        val b = dao.insert(newPet("B", active = false))
        dao.setActiveExclusive(b)
        val petA = dao.getById(a)!!
        val petB = dao.getById(b)!!
        assertThat(petA.isActive).isFalse()
        assertThat(petB.isActive).isTrue()
        assertThat(dao.observeActive().first()?.id).isEqualTo(b)
    }

    @Test fun `update persists stat changes`() = runTest {
        val id = dao.insert(newPet())
        val pet = dao.getById(id)!!.copy(hunger = 42f)
        dao.update(pet)
        assertThat(dao.getById(id)!!.hunger).isWithin(0.01f).of(42f)
    }
}
```

- [ ] **Step 8: Run, expect pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.pocketpets.app.data.db.PetDaoTest"`
Expected: PASS, 3 tests.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/kotlin/com/pocketpets/app/data/db/ \
        app/src/test/kotlin/com/pocketpets/app/data/db/ \
        app/src/test/resources/
git commit -m "feat: add Room database, entities, DAOs, and DAO tests"
```

---

## Task 10: PetRepository (TDD with Robolectric)

**Files:**
- Create: `app/src/main/kotlin/com/pocketpets/app/data/repo/PetRepository.kt`
- Create: `app/src/test/kotlin/com/pocketpets/app/data/repo/PetRepositoryTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.pocketpets.app.data.repo

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.pocketpets.app.data.db.AppDatabase
import com.pocketpets.app.domain.Species
import com.pocketpets.app.testing.FakeClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PetRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: PetRepository
    private val clock = FakeClock(Instant.parse("2026-01-01T12:00:00Z"))

    @Before fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = PetRepository(db.petDao(), db.careEventDao(), clock)
    }
    @After fun teardown() { db.close() }

    @Test fun `adopt creates a full-stat active pet`() = runTest {
        val id = repo.adopt("Whiskers", Species.CAT)
        val pet = repo.observeActive().first()!!
        assertThat(pet.id).isEqualTo(id)
        assertThat(pet.name).isEqualTo("Whiskers")
        assertThat(pet.stats.hunger).isEqualTo(100f)
        assertThat(pet.isActive).isTrue()
    }

    @Test fun `feed bumps hunger and sets lastFedAt`() = runTest {
        val id = repo.adopt("Whiskers", Species.CAT)
        // age the pet so hunger has dropped
        clock.advanceBy(2L * 3_600_000)
        repo.feed(id)
        val pet = repo.getById(id)!!
        // hunger should be > the 2h-decayed value (we added back +40)
        assertThat(pet.stats.hunger).isAtLeast(100f - 16f) // 100 - 8*2 = 84, +40 capped at 100
        assertThat(pet.lastFedAt).isEqualTo(clock.now())
    }

    @Test fun `clean removes a poop when present`() = runTest {
        val id = repo.adopt("Whiskers", Species.CAT)
        // simulate a poop on the pet by direct DB write
        val dao = db.petDao()
        val withPoop = dao.getById(id)!!.copy(poopCount = 1)
        dao.update(withPoop)
        repo.clean(id)
        assertThat(repo.getById(id)!!.poopCount).isEqualTo(0)
    }

    @Test fun `clean with no poops increases cleanliness by 10`() = runTest {
        val id = repo.adopt("Whiskers", Species.CAT)
        // drop cleanliness to 50
        val dao = db.petDao()
        dao.update(dao.getById(id)!!.copy(cleanliness = 50f))
        repo.clean(id)
        assertThat(repo.getById(id)!!.stats.cleanliness).isWithin(0.01f).of(60f)
    }

    @Test fun `pet bumps happiness up to cap, then no-op`() = runTest {
        val id = repo.adopt("Whiskers", Species.CAT)
        val dao = db.petDao()
        dao.update(dao.getById(id)!!.copy(happiness = 50f))
        repeat(5) { repo.pet(id) }
        val after5 = repo.getById(id)!!.stats.happiness
        // Try a 6th — should be no-op (cap is 5 per 10 min)
        repo.pet(id)
        val after6 = repo.getById(id)!!.stats.happiness
        assertThat(after6).isEqualTo(after5)
    }

    @Test fun `setActive flips active flag exclusively`() = runTest {
        val a = repo.adopt("A", Species.CAT)
        val b = repo.adopt("B", Species.CAT)
        // Most-recent adopt is active
        assertThat(repo.observeActive().first()!!.id).isEqualTo(b)
        repo.setActive(a)
        assertThat(repo.observeActive().first()!!.id).isEqualTo(a)
    }
}
```

- [ ] **Step 2: Run, expect failure**

Run: `./gradlew :app:testDebugUnitTest --tests "com.pocketpets.app.data.repo.PetRepositoryTest"`
Expected: FAIL with "unresolved reference: PetRepository".

- [ ] **Step 3: Implement `PetRepository.kt`**

```kotlin
package com.pocketpets.app.data.repo

import com.pocketpets.app.data.db.CareEventDao
import com.pocketpets.app.data.db.CareEventEntity
import com.pocketpets.app.data.db.PetDao
import com.pocketpets.app.data.db.PetEntity
import com.pocketpets.app.domain.Pet
import com.pocketpets.app.domain.PetStats
import com.pocketpets.app.domain.Species
import com.pocketpets.app.domain.StatDecay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class PetRepository(
    private val petDao: PetDao,
    private val careDao: CareEventDao,
    private val clock: Clock,
) {
    fun observeAll(): Flow<List<Pet>> = petDao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeActive(): Flow<Pet?> = petDao.observeActive().map { it?.toDomain() }

    suspend fun getById(id: Long): Pet? = petDao.getById(id)?.toDomain()

    suspend fun adopt(name: String, species: Species): Long {
        val now = clock.now()
        val id = petDao.insert(
            PetEntity(
                name = name.trim(),
                species = species,
                bornAt = now,
                hunger = 100f, cleanliness = 100f,
                happiness = 100f, energy = 100f,
                lastTickAt = now,
                isActive = false,
                poopCount = 0,
                lastFedAt = null,
            )
        )
        petDao.setActiveExclusive(id)
        return id
    }

    suspend fun setActive(id: Long) = petDao.setActiveExclusive(id)

    suspend fun feed(id: Long) {
        mutate(id, "feed") { ticked ->
            val newStats = ticked.stats.copy(
                hunger = (ticked.stats.hunger + 40f).coerceAtMost(100f),
                happiness = (ticked.stats.happiness + 5f).coerceAtMost(100f),
            )
            ticked.copy(stats = newStats, lastFedAt = clock.now())
        }
    }

    suspend fun clean(id: Long) {
        mutate(id, "clean") { ticked ->
            if (ticked.poopCount > 0) {
                ticked.copy(poopCount = ticked.poopCount - 1)
            } else {
                val newStats = ticked.stats.copy(
                    cleanliness = (ticked.stats.cleanliness + 10f).coerceAtMost(100f)
                )
                ticked.copy(stats = newStats)
            }
        }
    }

    private val petTimestamps = mutableMapOf<Long, ArrayDeque<Long>>()
    private val petWindowMs = 10L * 60 * 1000
    private val petMaxInWindow = 5

    suspend fun pet(id: Long) {
        // Diminishing returns: drop timestamps older than 10 min, cap at 5 in window.
        val now = clock.now().toEpochMilliseconds()
        val window = petTimestamps.getOrPut(id) { ArrayDeque() }
        while (window.isNotEmpty() && now - window.first() > petWindowMs) window.removeFirst()
        if (window.size >= petMaxInWindow) return // no-op
        window.addLast(now)
        mutate(id, "pet") { ticked ->
            val newStats = ticked.stats.copy(
                happiness = (ticked.stats.happiness + 5f).coerceAtMost(100f)
            )
            ticked.copy(stats = newStats)
        }
    }

    suspend fun talk(id: Long) {
        mutate(id, "talk") { ticked ->
            val newStats = ticked.stats.copy(
                happiness = (ticked.stats.happiness + 2f).coerceAtMost(100f)
            )
            ticked.copy(stats = newStats)
        }
    }

    /** Background-only entry point: just runs decay and persists. */
    suspend fun runDecayTick(id: Long) {
        mutate(id, "auto_tick") { it } // mutate already calls StatDecay.tick
    }

    private suspend fun mutate(id: Long, kind: String, transform: (Pet) -> Pet) {
        val raw = petDao.getById(id) ?: return
        val ticked = StatDecay.tick(raw.toDomain(), clock.now())
        val mutated = transform(ticked)
        petDao.update(PetEntity.fromDomain(mutated))
        careDao.insert(CareEventEntity(petId = id, kind = kind, at = clock.now()))
        careDao.pruneToLast100(id)
    }
}
```

- [ ] **Step 4: Run, expect pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.pocketpets.app.data.repo.PetRepositoryTest"`
Expected: PASS, 6 tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/pocketpets/app/data/repo/ \
        app/src/test/kotlin/com/pocketpets/app/data/repo/
git commit -m "feat: add PetRepository with care actions and decay-on-mutate"
```

---

## Task 11: SettingsDataStore

**Files:**
- Create: `app/src/main/kotlin/com/pocketpets/app/data/settings/SettingsDataStore.kt`

- [ ] **Step 1: Write the file**

```kotlin
package com.pocketpets.app.data.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "pocket_pets_settings")

data class NotificationSettings(
    val masterOn: Boolean,
    val hungryOn: Boolean,
    val dirtyOn: Boolean,
    val sadOn: Boolean,
    val quietStartHour: Int,
    val quietEndHour: Int,
)

data class FlagsSnapshot(
    val notificationSettings: NotificationSettings,
    val lastSeenPetId: Long?,
    val timeAccelerationEnabled: Boolean,
)

class SettingsDataStore(private val context: Context) {

    private object Keys {
        val MASTER = booleanPreferencesKey("notif_master_on")
        val HUNGRY = booleanPreferencesKey("notif_hungry_on")
        val DIRTY = booleanPreferencesKey("notif_dirty_on")
        val SAD = booleanPreferencesKey("notif_sad_on")
        val QUIET_START = intPreferencesKey("quiet_start_hour")
        val QUIET_END = intPreferencesKey("quiet_end_hour")
        val LAST_SEEN_PET = longPreferencesKey("last_seen_pet_id")
        val TIME_ACCEL = booleanPreferencesKey("time_acceleration_enabled")
        fun notifyFlag(petId: Long, kind: String) = booleanPreferencesKey("notif_flag_${petId}_$kind")
    }

    val snapshot: Flow<FlagsSnapshot> = context.dataStore.data.map { prefs ->
        FlagsSnapshot(
            notificationSettings = NotificationSettings(
                masterOn = prefs[Keys.MASTER] ?: true,
                hungryOn = prefs[Keys.HUNGRY] ?: true,
                dirtyOn = prefs[Keys.DIRTY] ?: true,
                sadOn = prefs[Keys.SAD] ?: true,
                quietStartHour = prefs[Keys.QUIET_START] ?: 22,
                quietEndHour = prefs[Keys.QUIET_END] ?: 7,
            ),
            lastSeenPetId = prefs[Keys.LAST_SEEN_PET],
            timeAccelerationEnabled = prefs[Keys.TIME_ACCEL] ?: false,
        )
    }

    suspend fun setNotificationSettings(s: NotificationSettings) {
        context.dataStore.edit {
            it[Keys.MASTER] = s.masterOn
            it[Keys.HUNGRY] = s.hungryOn
            it[Keys.DIRTY] = s.dirtyOn
            it[Keys.SAD] = s.sadOn
            it[Keys.QUIET_START] = s.quietStartHour
            it[Keys.QUIET_END] = s.quietEndHour
        }
    }

    suspend fun setLastSeenPet(id: Long) {
        context.dataStore.edit { it[Keys.LAST_SEEN_PET] = id }
    }

    suspend fun setTimeAcceleration(on: Boolean) {
        context.dataStore.edit { it[Keys.TIME_ACCEL] = on }
    }

    suspend fun isNotifyFlagSet(petId: Long, kind: String): Boolean =
        context.dataStore.data.map { it[Keys.notifyFlag(petId, kind)] ?: false }.let {
            kotlinx.coroutines.flow.first(it)
        }

    suspend fun setNotifyFlag(petId: Long, kind: String, value: Boolean) {
        context.dataStore.edit { it[Keys.notifyFlag(petId, kind)] = value }
    }
}

private suspend fun <T> kotlinx.coroutines.flow.first(flow: Flow<T>): T =
    kotlinx.coroutines.flow.firstOrNull(flow) ?: error("empty flow")
```

If the trailing helper conflicts with the existing `kotlinx.coroutines.flow.first`, replace it with a direct usage at call sites: `flow.first()`. Confirm by `import kotlinx.coroutines.flow.first` and dropping the helper. Adjust as needed.

- [ ] **Step 2: Build**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`. If `first` import conflicts, simplify the two methods that use it as noted above.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/pocketpets/app/data/settings/
git commit -m "feat: add SettingsDataStore for notification prefs and flags"
```

---

## Task 12: AppContainer + PocketPetsApp wiring

**Files:**
- Create: `app/src/main/kotlin/com/pocketpets/app/di/AppContainer.kt`
- Modify: `app/src/main/kotlin/com/pocketpets/app/PocketPetsApp.kt`
- Create: `app/src/main/kotlin/com/pocketpets/app/util/Ticker.kt`

- [ ] **Step 1: Write `Ticker.kt`**

```kotlin
package com.pocketpets.app.util

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

fun ticker(periodMs: Long): Flow<Long> = flow {
    var i = 0L
    while (true) {
        emit(i++)
        delay(periodMs)
    }
}
```

- [ ] **Step 2: Write `AppContainer.kt`**

```kotlin
package com.pocketpets.app.di

import android.content.Context
import androidx.room.Room
import com.pocketpets.app.data.db.AppDatabase
import com.pocketpets.app.data.repo.PetRepository
import com.pocketpets.app.data.settings.SettingsDataStore
import kotlinx.datetime.Clock

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val clock: Clock = Clock.System

    val database: AppDatabase by lazy {
        Room.databaseBuilder(appContext, AppDatabase::class.java, "pocket_pets.db")
            .fallbackToDestructiveMigration()
            .build()
    }

    val petRepository: PetRepository by lazy {
        PetRepository(database.petDao(), database.careEventDao(), clock)
    }

    val settings: SettingsDataStore by lazy {
        SettingsDataStore(appContext)
    }
}
```

- [ ] **Step 3: Update `PocketPetsApp.kt`**

```kotlin
package com.pocketpets.app

import android.app.Application
import com.pocketpets.app.di.AppContainer

class PocketPetsApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
```

- [ ] **Step 4: Build**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/pocketpets/app/di/ \
        app/src/main/kotlin/com/pocketpets/app/util/ \
        app/src/main/kotlin/com/pocketpets/app/PocketPetsApp.kt
git commit -m "feat: add AppContainer manual DI and Ticker helper"
```

---

## Task 13: Sprite generation tool

**Files:**
- Create: `tools/generate_sprites.py`
- Create: `app/src/main/res/drawable-nodpi/{cat_baby_idle.png,cat_baby_eat.png,cat_baby_happy.png,cat_baby_sleep.png,cat_baby_hungry.png,cat_baby_dirty.png,cat_baby_sad.png}` (and `_juvenile_` and `_adult_` equivalents)
- Create: `app/src/main/res/drawable-nodpi/{poop.png,room_bg.png,bowl.png}`

- [ ] **Step 1: Write `tools/generate_sprites.py`**

```python
#!/usr/bin/env python3
"""Generate 16-bit-style cat sprites and decor PNGs.

Deterministic: re-running yields identical bytes. Outputs to
app/src/main/res/drawable-nodpi/. Requires Pillow.

Style: 64x64 sprite for pets, ~24-color palette per pet, integer-scale-ready
(no anti-aliasing). Uses simple shapes assembled from solid pixel rects.
This is placeholder-quality but functional 16-bit-style art.
"""
from __future__ import annotations
from PIL import Image, ImageDraw
from pathlib import Path

OUT = Path(__file__).parent.parent / "app" / "src" / "main" / "res" / "drawable-nodpi"
OUT.mkdir(parents=True, exist_ok=True)

# Cat palette (orange tabby): outline, dark fur, mid fur, light fur,
# belly, nose, eye, eye-shine, mouth, blush.
PAL_CAT = {
    "outline": (40, 22, 14, 255),
    "dark":    (180, 100, 50, 255),
    "mid":     (220, 140, 80, 255),
    "light":   (245, 200, 140, 255),
    "belly":   (255, 235, 205, 255),
    "nose":    (220, 110, 130, 255),
    "eye":     (35, 30, 40, 255),
    "shine":   (255, 255, 255, 255),
    "mouth":   (90, 50, 35, 255),
    "blush":   (255, 170, 170, 200),
    "tongue":  (240, 130, 150, 255),
    "stripe":  (140, 80, 40, 255),
    "z":       (90, 90, 110, 255),
    "tear":    (140, 200, 240, 255),
    "tear_d":  (90, 150, 200, 255),
}

def blank(w=64, h=64):
    return Image.new("RGBA", (w, h), (0, 0, 0, 0))

def rect(d: ImageDraw.ImageDraw, x, y, w, h, color):
    d.rectangle([x, y, x + w - 1, y + h - 1], fill=color)

def px(d, x, y, color):
    d.point((x, y), fill=color)

# --- Body shape primitives ---

def draw_cat_body(d: ImageDraw.ImageDraw, scale: float, mood: str, frame: int, palette=PAL_CAT):
    """
    Draw a cat centered in a 64x64 image. `scale` is body size multiplier
    (0.55 baby, 0.75 juvenile, 1.0 adult).
    `mood` controls expression: idle/eat/happy/sleep/hungry/dirty/sad.
    `frame` for animation: 0..3 (idle), 0..3 (eat), 0..2 (happy), 0..3 (sleep).
    """
    cx = 32
    base_y = 56
    body_w = int(36 * scale)
    body_h = int(28 * scale)
    head_r = int(13 * scale)
    bx = cx - body_w // 2
    by = base_y - body_h
    hx = cx - head_r
    hy = by - head_r * 2 + 4  # head sits on body

    # subtle bounce for happy frames
    bounce = 0
    if mood == "happy":
        bounce = [0, -2, -3, -1][min(frame, 3)]
    by += bounce; hy += bounce

    # shadow
    rect(d, cx - body_w // 2 + 2, base_y, body_w - 4, 2, (0, 0, 0, 60))

    # body (rounded by clipping corners)
    rect(d, bx, by, body_w, body_h, palette["mid"])
    rect(d, bx, by, 2, 2, (0, 0, 0, 0))
    rect(d, bx + body_w - 2, by, 2, 2, (0, 0, 0, 0))
    rect(d, bx, by + body_h - 2, 2, 2, (0, 0, 0, 0))
    rect(d, bx + body_w - 2, by + body_h - 2, 2, 2, (0, 0, 0, 0))

    # belly highlight
    rect(d, bx + 4, by + body_h // 2, body_w - 8, body_h // 2 - 2, palette["belly"])
    # back stripes (16-bit shading)
    for i in range(0, body_w - 8, 6):
        rect(d, bx + 4 + i, by + 2, 3, 2, palette["stripe"])

    # legs
    rect(d, bx + 2, base_y - 4, 4, 4, palette["dark"])
    rect(d, bx + body_w - 6, base_y - 4, 4, 4, palette["dark"])

    # tail (curls when happy, straight when idle)
    tail_x = bx + body_w
    if mood == "happy":
        rect(d, tail_x, by + 4, 2, body_h - 8, palette["mid"])
        rect(d, tail_x + 2, by + 4, 2, 4, palette["mid"])
    else:
        offset = [0, 0, 1, 0][frame % 4] if mood == "idle" else 0
        rect(d, tail_x, by + 6 + offset, 2, body_h - 10, palette["mid"])

    # head
    rect(d, hx, hy, head_r * 2, head_r * 2, palette["mid"])
    # ears
    rect(d, hx, hy - 3, 4, 4, palette["mid"])
    rect(d, hx + 2, hy - 5, 2, 2, palette["mid"])
    rect(d, hx + head_r * 2 - 4, hy - 3, 4, 4, palette["mid"])
    rect(d, hx + head_r * 2 - 4, hy - 5, 2, 2, palette["mid"])
    # inner ears
    rect(d, hx + 1, hy - 1, 2, 2, palette["nose"])
    rect(d, hx + head_r * 2 - 3, hy - 1, 2, 2, palette["nose"])

    # face
    eye_y = hy + head_r - 2
    left_eye_x = hx + 4
    right_eye_x = hx + head_r * 2 - 6
    if mood == "sleep":
        # closed eyes - thin line
        rect(d, left_eye_x, eye_y + 1, 3, 1, palette["outline"])
        rect(d, right_eye_x, eye_y + 1, 3, 1, palette["outline"])
    elif mood == "idle" and frame == 2:
        # blink frame
        rect(d, left_eye_x, eye_y + 1, 3, 1, palette["outline"])
        rect(d, right_eye_x, eye_y + 1, 3, 1, palette["outline"])
    else:
        rect(d, left_eye_x, eye_y, 3, 3, palette["eye"])
        rect(d, right_eye_x, eye_y, 3, 3, palette["eye"])
        px(d, left_eye_x + 2, eye_y, palette["shine"])
        px(d, right_eye_x + 2, eye_y, palette["shine"])

    # nose + mouth
    nose_x = hx + head_r - 1
    nose_y = eye_y + 4
    rect(d, nose_x, nose_y, 2, 1, palette["nose"])
    if mood == "happy":
        # smile
        rect(d, nose_x - 2, nose_y + 2, 2, 1, palette["mouth"])
        rect(d, nose_x + 2, nose_y + 2, 2, 1, palette["mouth"])
        rect(d, nose_x, nose_y + 3, 2, 1, palette["mouth"])
    elif mood == "sad":
        rect(d, nose_x - 2, nose_y + 3, 2, 1, palette["mouth"])
        rect(d, nose_x + 2, nose_y + 3, 2, 1, palette["mouth"])
        rect(d, nose_x, nose_y + 2, 2, 1, palette["mouth"])
        # tear
        rect(d, left_eye_x + 1, eye_y + 4, 1, 2, palette["tear"])
        px(d, left_eye_x + 1, eye_y + 6, palette["tear_d"])
    elif mood == "hungry":
        # open mouth
        rect(d, nose_x, nose_y + 2, 2, 2, palette["mouth"])
        px(d, nose_x, nose_y + 3, palette["tongue"])
    elif mood == "eat":
        # chewing - alternate
        if frame % 2 == 0:
            rect(d, nose_x, nose_y + 2, 2, 2, palette["mouth"])
        else:
            rect(d, nose_x, nose_y + 2, 2, 1, palette["mouth"])
            px(d, nose_x, nose_y + 3, palette["tongue"])
    elif mood == "dirty":
        # confused mouth
        rect(d, nose_x, nose_y + 2, 1, 1, palette["mouth"])
        rect(d, nose_x + 1, nose_y + 3, 1, 1, palette["mouth"])
        # squiggle above head
        for i, x in enumerate([cx - 4, cx - 2, cx, cx + 2, cx + 4]):
            rect(d, x, hy - 8 + (i % 2), 1, 1, palette["outline"])
    else:  # idle
        rect(d, nose_x, nose_y + 2, 2, 1, palette["mouth"])

    # blush for happy
    if mood == "happy":
        rect(d, hx + 2, eye_y + 3, 2, 1, palette["blush"])
        rect(d, hx + head_r * 2 - 4, eye_y + 3, 2, 1, palette["blush"])

    # sleep Z's
    if mood == "sleep":
        z_x = hx + head_r * 2 + 2
        z_y_offsets = [0, -2, -4, -3]
        zy = hy - 6 + z_y_offsets[frame % 4]
        for cy in [zy, zy + 1, zy + 2]:
            rect(d, z_x, cy, 4, 1, palette["z"])
        rect(d, z_x + 3, zy + 1, 1, 1, palette["z"])
        rect(d, z_x + 0, zy + 1, 1, 1, palette["z"])

def render_pet_sheet(stage: str, mood: str) -> Image.Image:
    """A sprite sheet: frames laid out horizontally. Returns 64*N x 64."""
    scale = {"baby": 0.55, "juvenile": 0.75, "adult": 1.0}[stage]
    frames = {
        "idle": 4, "eat": 4, "happy": 3, "sleep": 4,
        "hungry": 1, "dirty": 1, "sad": 1,
    }[mood]
    img = Image.new("RGBA", (64 * frames, 64), (0, 0, 0, 0))
    for f in range(frames):
        sub = blank(64, 64)
        d = ImageDraw.Draw(sub)
        draw_cat_body(d, scale, mood, f)
        img.paste(sub, (f * 64, 0))
    return img

def render_poop() -> Image.Image:
    img = blank(32, 32)
    d = ImageDraw.Draw(img)
    base = (90, 60, 30, 255)
    mid = (130, 90, 50, 255)
    hi = (170, 130, 80, 255)
    rect(d, 8, 22, 16, 4, base)
    rect(d, 10, 18, 12, 4, mid)
    rect(d, 12, 14, 8, 4, mid)
    rect(d, 14, 11, 4, 3, hi)
    px(d, 13, 18, hi)
    return img

def render_room_bg() -> Image.Image:
    # 240x320 - small canvas, will be integer-scaled in the UI
    img = Image.new("RGBA", (240, 320), (233, 201, 182, 255))  # wallpaper
    d = ImageDraw.Draw(img)
    # wallpaper pattern
    for y in range(0, 240, 16):
        for x in range(0, 240, 16):
            px(d, x + 8, y + 8, (210, 175, 155, 255))
    # floor
    rect(d, 0, 240, 240, 80, (139, 90, 60, 255))
    for y in range(248, 320, 8):
        rect(d, 0, y, 240, 1, (110, 70, 45, 255))
    for x in range(0, 240, 32):
        rect(d, x, 240, 1, 80, (110, 70, 45, 255))
    # baseboard
    rect(d, 0, 236, 240, 4, (90, 55, 35, 255))
    return img

def render_bowl() -> Image.Image:
    img = blank(32, 16)
    d = ImageDraw.Draw(img)
    rect(d, 4, 6, 24, 8, (160, 160, 170, 255))
    rect(d, 4, 6, 24, 2, (200, 200, 215, 255))
    rect(d, 6, 8, 20, 4, (110, 110, 120, 255))
    return img

def main():
    stages = ["baby", "juvenile", "adult"]
    moods = ["idle", "eat", "happy", "sleep", "hungry", "dirty", "sad"]
    for stage in stages:
        for mood in moods:
            sheet = render_pet_sheet(stage, mood)
            sheet.save(OUT / f"cat_{stage}_{mood}.png")
    render_poop().save(OUT / "poop.png")
    render_room_bg().save(OUT / "room_bg.png")
    render_bowl().save(OUT / "bowl.png")
    print(f"Wrote sprites to {OUT}")

if __name__ == "__main__":
    main()
```

- [ ] **Step 2: Run the script**

```bash
python3 -m pip install --user Pillow
python3 tools/generate_sprites.py
```
Expected: prints "Wrote sprites to .../drawable-nodpi"; the `drawable-nodpi/` directory contains 21 cat PNGs + `poop.png` + `room_bg.png` + `bowl.png`.

- [ ] **Step 3: Verify build picks up resources**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`. Check `R.drawable.cat_baby_idle` etc. exist by looking at the generated R class (or trust the build).

- [ ] **Step 4: Commit**

```bash
git add tools/generate_sprites.py app/src/main/res/drawable-nodpi/
git commit -m "feat: add Pillow-based sprite generator and bake initial cat sprites"
```

---

## Task 14: SpriteView, SpeechBubble, StatChip composables

**Files:**
- Create: `app/src/main/kotlin/com/pocketpets/app/ui/pet/SpriteView.kt`
- Create: `app/src/main/kotlin/com/pocketpets/app/ui/pet/SpeechBubble.kt`
- Create: `app/src/main/kotlin/com/pocketpets/app/ui/pet/StatChip.kt`

- [ ] **Step 1: Write `SpriteView.kt`**

```kotlin
package com.pocketpets.app.ui.pet

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
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun SpriteView(
    spriteResId: Int,
    frameCount: Int,
    frameMs: Long = 180,
    sizeDp: Int = 256,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val sheet = remember(spriteResId) {
        BitmapFactory.decodeResource(context.resources, spriteResId, BitmapFactory.Options().apply {
            inScaled = false
        })
    }
    var frame by remember(spriteResId) { mutableIntStateOf(0) }
    LaunchedEffect(spriteResId, frameCount) {
        if (frameCount <= 1) return@LaunchedEffect
        while (true) {
            delay(frameMs)
            frame = (frame + 1) % frameCount
        }
    }
    val image = remember(sheet) { sheet.asImageBitmap() }
    val frameW = sheet.width / frameCount
    val frameH = sheet.height
    Canvas(modifier = modifier) {
        drawSpriteFrame(image, frame, frameW, frameH)
    }
}

private fun DrawScope.drawSpriteFrame(
    image: androidx.compose.ui.graphics.ImageBitmap,
    frame: Int,
    frameW: Int,
    frameH: Int,
) {
    drawImage(
        image = image,
        srcOffset = IntOffset(frame * frameW, 0),
        srcSize = IntSize(frameW, frameH),
        dstOffset = IntOffset(0, 0),
        dstSize = IntSize(size.width.toInt(), size.height.toInt()),
        filterQuality = FilterQuality.None,
    )
}
```

- [ ] **Step 2: Write `SpeechBubble.kt`**

```kotlin
package com.pocketpets.app.ui.pet

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pocketpets.app.domain.speech.Phrase
import kotlinx.coroutines.delay

@Composable
fun SpeechBubble(
    phrase: Phrase?,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit,
    autoDismissMs: Long = 4000,
) {
    var translated by remember(phrase) { mutableStateOf(false) }
    var paused by remember(phrase) { mutableStateOf(false) }

    LaunchedEffect(phrase) {
        if (phrase == null) return@LaunchedEffect
        var elapsed = 0L
        val tick = 100L
        while (elapsed < autoDismissMs) {
            delay(tick)
            if (!paused) elapsed += tick
        }
        onDismiss()
    }

    AnimatedVisibility(visible = phrase != null, modifier = modifier) {
        if (phrase != null) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .border(2.dp, Color(0xFF1A1A2E), RoundedCornerShape(12.dp))
                    .clickable {
                        paused = true
                        translated = !translated
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(
                    text = if (translated) phrase.translation else phrase.animal,
                    color = Color(0xFF1A1A2E),
                )
            }
        }
    }
}
```

- [ ] **Step 3: Write `StatChip.kt`**

```kotlin
package com.pocketpets.app.ui.pet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun StatChip(label: String, value: Float, color: Color, modifier: Modifier = Modifier) {
    val pct = (value / 100f).coerceIn(0f, 1f)
    Row(
        modifier = modifier.padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = androidx.compose.ui.unit.TextUnit.Unspecified)
        Box(
            modifier = Modifier
                .padding(start = 4.dp)
                .width(48.dp).height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0x331A1A2E)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(pct)
                    .height(8.dp)
                    .background(color),
            )
        }
    }
}
```

- [ ] **Step 4: Build**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/pocketpets/app/ui/pet/
git commit -m "feat: add SpriteView, SpeechBubble, StatChip composables"
```

---

## Task 15: PetViewModel (TDD with fake repo abstraction)

**Files:**
- Modify: `app/src/main/kotlin/com/pocketpets/app/data/repo/PetRepository.kt` — extract an interface so the ViewModel can be tested with a fake
- Create: `app/src/main/kotlin/com/pocketpets/app/ui/pet/PetViewModel.kt`
- Create: `app/src/test/kotlin/com/pocketpets/app/ui/pet/PetViewModelTest.kt`

- [ ] **Step 1: Refactor `PetRepository` to extract an interface**

In `PetRepository.kt`, add at the top of the file:
```kotlin
interface PetRepo {
    fun observeActive(): kotlinx.coroutines.flow.Flow<com.pocketpets.app.domain.Pet?>
    fun observeAll(): kotlinx.coroutines.flow.Flow<List<com.pocketpets.app.domain.Pet>>
    suspend fun getById(id: Long): com.pocketpets.app.domain.Pet?
    suspend fun adopt(name: String, species: com.pocketpets.app.domain.Species): Long
    suspend fun setActive(id: Long)
    suspend fun feed(id: Long)
    suspend fun clean(id: Long)
    suspend fun pet(id: Long)
    suspend fun talk(id: Long)
    suspend fun runDecayTick(id: Long)
}
```
Make `class PetRepository(...) : PetRepo`. All existing methods already match the interface signatures.

- [ ] **Step 2: Write the failing ViewModel test**

```kotlin
package com.pocketpets.app.ui.pet

import com.google.common.truth.Truth.assertThat
import com.pocketpets.app.data.repo.PetRepo
import com.pocketpets.app.domain.Mood
import com.pocketpets.app.domain.Pet
import com.pocketpets.app.domain.PetStats
import com.pocketpets.app.domain.Species
import com.pocketpets.app.domain.speech.CatSpeech
import com.pocketpets.app.testing.FakeClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import org.junit.Test
import kotlin.random.Random

class PetViewModelTest {
    private val clock = FakeClock(Instant.parse("2026-01-01T12:00:00Z"))
    private val zone = TimeZone.UTC

    private fun samplePet(id: Long = 1, hunger: Float = 80f) = Pet(
        id = id, name = "Whiskers", species = Species.CAT,
        bornAt = clock.now(),
        stats = PetStats(hunger, 80f, 80f, 80f),
        lastTickAt = clock.now(), isActive = true,
        poopCount = 0, lastFedAt = null,
    )

    private class FakeRepo(initial: Pet?) : PetRepo {
        val activeFlow = MutableStateFlow(initial)
        val calls = mutableListOf<String>()
        override fun observeActive(): Flow<Pet?> = activeFlow
        override fun observeAll() = MutableStateFlow(listOfNotNull(activeFlow.value))
        override suspend fun getById(id: Long) = activeFlow.value?.takeIf { it.id == id }
        override suspend fun adopt(name: String, species: Species): Long = error("nyi")
        override suspend fun setActive(id: Long) {}
        override suspend fun feed(id: Long) { calls += "feed:$id" }
        override suspend fun clean(id: Long) { calls += "clean:$id" }
        override suspend fun pet(id: Long) { calls += "pet:$id" }
        override suspend fun talk(id: Long) { calls += "talk:$id" }
        override suspend fun runDecayTick(id: Long) { calls += "tick:$id" }
    }

    @Test fun `displayed mood reflects stats and time`() = runTest {
        val repo = FakeRepo(samplePet(hunger = 20f))
        val vm = PetViewModel(repo, clock, zone, CatSpeech, rng = Random(0))
        val state = vm.state.first { it.pet != null }
        assertThat(state.mood).isEqualTo(Mood.HUNGRY)
    }

    @Test fun `feed delegates to repo`() = runTest {
        val repo = FakeRepo(samplePet())
        val vm = PetViewModel(repo, clock, zone, CatSpeech, rng = Random(0))
        vm.feed()
        assertThat(repo.calls).contains("feed:1")
    }

    @Test fun `talk emits a phrase from current mood category`() = runTest {
        val repo = FakeRepo(samplePet(hunger = 20f))
        val vm = PetViewModel(repo, clock, zone, CatSpeech, rng = Random(7))
        vm.talk()
        val state = vm.state.first { it.activePhrase != null }
        assertThat(CatSpeech.forMood(Mood.HUNGRY)).contains(state.activePhrase)
    }

    @Test fun `dismissPhrase clears it`() = runTest {
        val repo = FakeRepo(samplePet(hunger = 20f))
        val vm = PetViewModel(repo, clock, zone, CatSpeech, rng = Random(7))
        vm.talk()
        vm.dismissPhrase()
        assertThat(vm.state.first().activePhrase).isNull()
    }
}
```

- [ ] **Step 3: Run, expect failure**

Run: `./gradlew :app:testDebugUnitTest --tests "com.pocketpets.app.ui.pet.PetViewModelTest"`
Expected: FAIL with "unresolved reference: PetViewModel".

- [ ] **Step 4: Implement `PetViewModel.kt`**

```kotlin
package com.pocketpets.app.ui.pet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketpets.app.data.repo.PetRepo
import com.pocketpets.app.domain.GrowthStage
import com.pocketpets.app.domain.Mood
import com.pocketpets.app.domain.Pet
import com.pocketpets.app.domain.StatDecay
import com.pocketpets.app.domain.speech.Phrase
import com.pocketpets.app.domain.speech.SpeechBank
import com.pocketpets.app.util.ticker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlin.random.Random

data class PetUiState(
    val pet: Pet? = null,
    val mood: Mood = Mood.IDLE,
    val stage: GrowthStage = GrowthStage.BABY,
    val activePhrase: Phrase? = null,
)

class PetViewModel(
    private val repo: PetRepo,
    private val clock: Clock,
    private val zone: TimeZone,
    private val speech: SpeechBank,
    private val rng: Random = Random.Default,
) : ViewModel() {

    private val _phrase = MutableStateFlow<Phrase?>(null)
    val state: StateFlow<PetUiState>

    init {
        val petFlow = repo.observeActive()
        val flow = combine(petFlow, ticker(60_000L), _phrase) { rawPet, _, phrase ->
            val now = clock.now()
            val ticked = rawPet?.let { StatDecay.tick(it, now) }
            val mood = ticked?.let { Mood.from(it, now, zone) } ?: Mood.IDLE
            val stage = ticked?.let { GrowthStage.fromAge(it.bornAt, now) } ?: GrowthStage.BABY
            PetUiState(ticked, mood, stage, phrase)
        }
        // collect into a hot StateFlow for the UI
        val sf = MutableStateFlow(PetUiState())
        viewModelScope.launch {
            flow.collect { sf.value = it }
        }
        state = sf
    }

    fun feed() = withActive { repo.feed(it) }
    fun clean() = withActive { repo.clean(it) }
    fun pet() = withActive { repo.pet(it) }
    fun talk() = withActive { id ->
        val mood = state.value.mood
        val phrase = speech.random(mood, rng)
        _phrase.value = phrase
        repo.talk(id)
    }
    fun dismissPhrase() { _phrase.value = null }

    private fun withActive(block: suspend (Long) -> Unit) {
        val id = state.value.pet?.id ?: return
        viewModelScope.launch { block(id) }
    }
}
```

The test compares `Mood.from(...)` indirectly through state — make sure the imports in `Mood.kt` are reachable (`import com.pocketpets.app.domain.Mood`).

- [ ] **Step 5: Run, expect pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.pocketpets.app.ui.pet.PetViewModelTest"`
Expected: PASS, 4 tests.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/pocketpets/app/data/repo/PetRepository.kt \
        app/src/main/kotlin/com/pocketpets/app/ui/pet/PetViewModel.kt \
        app/src/test/kotlin/com/pocketpets/app/ui/pet/PetViewModelTest.kt
git commit -m "feat: add PetViewModel with mood/stage projection and speech actions"
```

---

## Task 16: Navigation scaffold + AdoptPetScreen

**Files:**
- Create: `app/src/main/kotlin/com/pocketpets/app/ui/nav/AppNav.kt`
- Create: `app/src/main/kotlin/com/pocketpets/app/ui/adopt/AdoptViewModel.kt`
- Create: `app/src/main/kotlin/com/pocketpets/app/ui/adopt/AdoptPetScreen.kt`
- Modify: `app/src/main/kotlin/com/pocketpets/app/MainActivity.kt`

- [ ] **Step 1: Write `AdoptViewModel.kt`**

```kotlin
package com.pocketpets.app.ui.adopt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketpets.app.data.repo.PetRepo
import com.pocketpets.app.domain.Species
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class AdoptState(
    val name: String = "",
    val species: Species = Species.CAT,
    val canSubmit: Boolean = false,
)

class AdoptViewModel(private val repo: PetRepo) : ViewModel() {
    private val _state = MutableStateFlow(AdoptState())
    val state: StateFlow<AdoptState> = _state

    fun setName(s: String) {
        _state.update { it.copy(name = s, canSubmit = s.trim().length in 1..20) }
    }

    fun adopt(onDone: (Long) -> Unit) {
        val s = _state.value
        if (!s.canSubmit) return
        viewModelScope.launch {
            val id = repo.adopt(s.name, s.species)
            onDone(id)
        }
    }
}

private inline fun <T> MutableStateFlow<T>.update(transform: (T) -> T) {
    value = transform(value)
}
```

- [ ] **Step 2: Write `AdoptPetScreen.kt`**

```kotlin
package com.pocketpets.app.ui.adopt

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AdoptPetScreen(vm: AdoptViewModel, onAdopted: (Long) -> Unit) {
    val s by vm.state.collectAsState()
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Adopt a Pet")
        Spacer(Modifier.height(16.dp))
        Text("Cat (more species coming soon)")
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = s.name,
            onValueChange = vm::setName,
            label = { Text("Name (1–20 characters)") },
            singleLine = true,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = { vm.adopt(onAdopted) }, enabled = s.canSubmit) {
            Text("Adopt")
        }
    }
}
```

- [ ] **Step 3: Write a placeholder `PetScreen.kt`**

Create `app/src/main/kotlin/com/pocketpets/app/ui/pet/PetScreen.kt` (full version comes in Task 17):
```kotlin
package com.pocketpets.app.ui.pet

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun PetScreen(vm: PetViewModel, onOpenSettings: () -> Unit, onOpenSelector: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Pet Screen (placeholder)")
    }
}
```

- [ ] **Step 4: Write `AppNav.kt`**

```kotlin
package com.pocketpets.app.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pocketpets.app.PocketPetsApp
import com.pocketpets.app.di.AppContainer
import com.pocketpets.app.ui.adopt.AdoptPetScreen
import com.pocketpets.app.ui.adopt.AdoptViewModel
import com.pocketpets.app.ui.pet.PetScreen
import com.pocketpets.app.ui.pet.PetViewModel
import com.pocketpets.app.domain.speech.CatSpeech
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import kotlinx.datetime.TimeZone

@Composable
fun AppNav() {
    val context = LocalContext.current
    val container = (context.applicationContext as PocketPetsApp).container
    val nav = rememberNavController()

    val petsAtStart = container.petRepository.observeAll().collectAsState(initial = emptyList()).value
    val start = if (petsAtStart.isEmpty()) "adopt" else "pet"

    NavHost(navController = nav, startDestination = start) {
        composable("adopt") {
            val vm: AdoptViewModel = viewModel(factory = viewModelFactory {
                initializer { AdoptViewModel(container.petRepository) }
            })
            AdoptPetScreen(vm) { _ -> nav.navigate("pet") { popUpTo("adopt") { inclusive = true } } }
        }
        composable("pet") {
            val vm: PetViewModel = viewModel(factory = viewModelFactory {
                initializer {
                    PetViewModel(
                        repo = container.petRepository,
                        clock = container.clock,
                        zone = TimeZone.currentSystemDefault(),
                        speech = CatSpeech,
                    )
                }
            })
            PetScreen(
                vm = vm,
                onOpenSettings = { nav.navigate("settings") },
                onOpenSelector = { /* will be replaced by bottom sheet in Task 18 */ },
            )
        }
        composable("settings") {
            // placeholder until Task 19
            androidx.compose.material3.Text("Settings (placeholder)")
        }
    }
}
```

- [ ] **Step 5: Update `MainActivity.kt` to host `AppNav`**

Replace the body of `setContent`:
```kotlin
setContent {
    PocketPetsTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            AppNav()
        }
    }
}
```
And add `import com.pocketpets.app.ui.nav.AppNav`.

- [ ] **Step 6: Build**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/com/pocketpets/app/ui/nav/ \
        app/src/main/kotlin/com/pocketpets/app/ui/adopt/ \
        app/src/main/kotlin/com/pocketpets/app/ui/pet/PetScreen.kt \
        app/src/main/kotlin/com/pocketpets/app/MainActivity.kt
git commit -m "feat: scaffold navigation, adopt flow, and pet screen placeholder"
```

---

## Task 17: PetScreen full UI

**Files:**
- Modify: `app/src/main/kotlin/com/pocketpets/app/ui/pet/PetScreen.kt`

- [ ] **Step 1: Implement the full screen**

Replace the contents of `PetScreen.kt`:
```kotlin
package com.pocketpets.app.ui.pet

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.pocketpets.app.R
import com.pocketpets.app.domain.GrowthStage
import com.pocketpets.app.domain.Mood
import kotlin.math.absoluteValue
import kotlin.random.Random

@Composable
fun PetScreen(
    vm: PetViewModel,
    onOpenSettings: () -> Unit,
    onOpenSelector: () -> Unit,
) {
    val state by vm.state.collectAsState()
    val pet = state.pet

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFE9C9B6))) {
        // Background room art
        Image(
            painter = painterResource(R.drawable.room_bg),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds,
        )

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
                pet?.let {
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
                modifier = Modifier
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

        // Pet sprite (centered) + speech bubble above it
        if (pet != null) {
            val spriteRes = spriteFor(pet.species, state.stage, state.mood)
            val frames = frameCountFor(state.mood)

            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    SpeechBubble(
                        phrase = state.activePhrase,
                        onDismiss = vm::dismissPhrase,
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .size(256.dp)
                            .clickable { vm.talk(); vm.pet() },
                    ) {
                        SpriteView(
                            spriteResId = spriteRes,
                            frameCount = frames,
                            sizeDp = 256,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }

            // Poops on the floor
            val rng = remember(pet.id) { Random(pet.id) }
            repeat(pet.poopCount) { i ->
                val xFraction = ((rng.nextInt(70) + 15) / 100f)
                val xOffset = ((xFraction - 0.5f) * 200).dp.value
                Image(
                    painter = painterResource(R.drawable.poop),
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = (110 + (i.absoluteValue * 4)).dp)
                        .size(48.dp),
                )
            }
        }

        // Action buttons
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Button(onClick = vm::feed) { Text("Feed") }
            Button(onClick = vm::clean) { Text("Clean") }
            Button(onClick = vm::pet) { Text("Pet") }
            Button(onClick = vm::talk) { Text("Talk") }
        }
    }
}

private fun stageLabel(s: GrowthStage): String = when (s) {
    GrowthStage.BABY -> "Baby"
    GrowthStage.JUVENILE -> "Juvenile"
    GrowthStage.ADULT -> "Adult"
}

private fun spriteFor(species: com.pocketpets.app.domain.Species, stage: GrowthStage, mood: Mood): Int {
    val stageStr = when (stage) {
        GrowthStage.BABY -> "baby"
        GrowthStage.JUVENILE -> "juvenile"
        GrowthStage.ADULT -> "adult"
    }
    val moodStr = when (mood) {
        Mood.IDLE -> "idle"
        Mood.HAPPY -> "happy"
        Mood.HUNGRY -> "hungry"
        Mood.GROSSED_OUT -> "dirty"
        Mood.SAD -> "sad"
        Mood.SLEEPY -> "sleep"
    }
    val name = "cat_${stageStr}_${moodStr}"
    // Reflective lookup avoids 21 R.drawable references in code.
    return runCatching {
        R.drawable::class.java.getField(name).getInt(null)
    }.getOrElse { R.drawable.cat_baby_idle }
}

private fun frameCountFor(mood: Mood): Int = when (mood) {
    Mood.IDLE -> 4
    Mood.HAPPY -> 3
    Mood.SLEEPY -> 4
    Mood.HUNGRY, Mood.GROSSED_OUT, Mood.SAD -> 1
}
```

- [ ] **Step 2: Build**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`. If `R.drawable.cat_baby_idle` is not found, re-run `python3 tools/generate_sprites.py` and rebuild.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/pocketpets/app/ui/pet/PetScreen.kt
git commit -m "feat: build out PetScreen with sprite, stats, actions, poops"
```

---

## Task 18: PetSelectorSheet

**Files:**
- Create: `app/src/main/kotlin/com/pocketpets/app/ui/select/PetSelectorViewModel.kt`
- Create: `app/src/main/kotlin/com/pocketpets/app/ui/select/PetSelectorSheet.kt`
- Modify: `app/src/main/kotlin/com/pocketpets/app/ui/nav/AppNav.kt`

- [ ] **Step 1: Write `PetSelectorViewModel.kt`**

```kotlin
package com.pocketpets.app.ui.select

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketpets.app.data.repo.PetRepo
import com.pocketpets.app.domain.Pet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class PetSelectorViewModel(private val repo: PetRepo) : ViewModel() {
    val pets: Flow<List<Pet>> = repo.observeAll()
    fun select(id: Long) { viewModelScope.launch { repo.setActive(id) } }
}
```

- [ ] **Step 2: Write `PetSelectorSheet.kt`**

```kotlin
package com.pocketpets.app.ui.select

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pocketpets.app.domain.Pet
import kotlinx.coroutines.flow.flowOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetSelectorSheet(
    vm: PetSelectorViewModel,
    onSelected: () -> Unit,
    onAdopt: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    val pets by vm.pets.collectAsState(initial = emptyList())
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Your pets", style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
            Spacer(Modifier.padding(4.dp))
            pets.forEach { pet ->
                PetRow(pet, isActive = pet.isActive) {
                    vm.select(pet.id); onSelected()
                }
                HorizontalDivider()
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onAdopt() }
                    .padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text("+ Adopt new pet")
            }
        }
    }
}

@Composable
private fun PetRow(pet: Pet, isActive: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (isActive) "★" else "  ")
        Spacer(Modifier.width(8.dp))
        Text(pet.name)
        Spacer(Modifier.width(8.dp))
        Text("(${pet.species.name.lowercase()})", color = androidx.compose.ui.graphics.Color.Gray)
    }
}
```

- [ ] **Step 3: Wire it into `AppNav.kt`**

Replace the `pet` composable in `AppNav.kt`:
```kotlin
composable("pet") {
    var sheetOpen by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val petVm: PetViewModel = viewModel(factory = viewModelFactory {
        initializer {
            PetViewModel(
                repo = container.petRepository,
                clock = container.clock,
                zone = TimeZone.currentSystemDefault(),
                speech = CatSpeech,
            )
        }
    })
    val selVm: com.pocketpets.app.ui.select.PetSelectorViewModel = viewModel(factory = viewModelFactory {
        initializer { com.pocketpets.app.ui.select.PetSelectorViewModel(container.petRepository) }
    })
    PetScreen(
        vm = petVm,
        onOpenSettings = { nav.navigate("settings") },
        onOpenSelector = { sheetOpen = true },
    )
    if (sheetOpen) {
        com.pocketpets.app.ui.select.PetSelectorSheet(
            vm = selVm,
            onSelected = { sheetOpen = false },
            onAdopt = { sheetOpen = false; nav.navigate("adopt") },
            onDismiss = { sheetOpen = false },
        )
    }
}
```

Add the necessary imports at the top of `AppNav.kt`:
```kotlin
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
```

- [ ] **Step 4: Build**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/pocketpets/app/ui/select/ \
        app/src/main/kotlin/com/pocketpets/app/ui/nav/AppNav.kt
git commit -m "feat: add PetSelectorSheet for switching active pet"
```

---

## Task 19: SettingsScreen + ViewModel

**Files:**
- Create: `app/src/main/kotlin/com/pocketpets/app/ui/settings/SettingsViewModel.kt`
- Create: `app/src/main/kotlin/com/pocketpets/app/ui/settings/SettingsScreen.kt`
- Modify: `app/src/main/kotlin/com/pocketpets/app/ui/nav/AppNav.kt`

- [ ] **Step 1: Write `SettingsViewModel.kt`**

```kotlin
package com.pocketpets.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketpets.app.data.settings.NotificationSettings
import com.pocketpets.app.data.settings.SettingsDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsViewModel(private val store: SettingsDataStore) : ViewModel() {
    private val _settings = MutableStateFlow<NotificationSettings?>(null)
    val settings: StateFlow<NotificationSettings?> = _settings
    private val _timeAccel = MutableStateFlow(false)
    val timeAccel: StateFlow<Boolean> = _timeAccel

    init {
        viewModelScope.launch {
            val snap = store.snapshot.first()
            _settings.value = snap.notificationSettings
            _timeAccel.value = snap.timeAccelerationEnabled
        }
    }

    fun update(s: NotificationSettings) {
        _settings.value = s
        viewModelScope.launch { store.setNotificationSettings(s) }
    }

    fun setTimeAcceleration(on: Boolean) {
        _timeAccel.value = on
        viewModelScope.launch { store.setTimeAcceleration(on) }
    }
}
```

- [ ] **Step 2: Write `SettingsScreen.kt`**

```kotlin
package com.pocketpets.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pocketpets.app.BuildConfig

@Composable
fun SettingsScreen(vm: SettingsViewModel) {
    val s by vm.settings.collectAsState()
    val accel by vm.timeAccel.collectAsState()
    val current = s ?: return
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Notifications", style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        ToggleRow("All notifications", current.masterOn) { vm.update(current.copy(masterOn = it)) }
        ToggleRow("Hungry alerts", current.hungryOn) { vm.update(current.copy(hungryOn = it)) }
        ToggleRow("Dirty alerts", current.dirtyOn) { vm.update(current.copy(dirtyOn = it)) }
        ToggleRow("Sad alerts", current.sadOn) { vm.update(current.copy(sadOn = it)) }
        Spacer(Modifier.height(8.dp))
        Text("Quiet hours: ${current.quietStartHour}:00–${current.quietEndHour}:00")
        if (BuildConfig.DEBUG_TIME) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            Text("Debug")
            ToggleRow("Speed up time 100×", accel) { vm.setTimeAcceleration(it) }
        }
    }
}

@Composable
private fun ToggleRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.padding(end = 8.dp))
        Spacer(Modifier.weight(1f))
        Switch(checked = value, onCheckedChange = onChange)
    }
}
```

- [ ] **Step 3: Update `AppNav.kt` settings route**

Replace the `composable("settings") { ... }` block with:
```kotlin
composable("settings") {
    val vm: com.pocketpets.app.ui.settings.SettingsViewModel = viewModel(factory = viewModelFactory {
        initializer { com.pocketpets.app.ui.settings.SettingsViewModel(container.settings) }
    })
    com.pocketpets.app.ui.settings.SettingsScreen(vm)
}
```

- [ ] **Step 4: Build**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/pocketpets/app/ui/settings/ \
        app/src/main/kotlin/com/pocketpets/app/ui/nav/AppNav.kt
git commit -m "feat: add SettingsScreen with notification toggles and debug speedup"
```

---

## Task 20: NotificationHelper

**Files:**
- Create: `app/src/main/kotlin/com/pocketpets/app/work/NotificationHelper.kt`

- [ ] **Step 1: Write the helper**

```kotlin
package com.pocketpets.app.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.pocketpets.app.MainActivity
import com.pocketpets.app.data.settings.NotificationSettings
import com.pocketpets.app.data.settings.SettingsDataStore
import com.pocketpets.app.domain.Pet
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class NotificationHelper(
    private val context: Context,
    private val settings: SettingsDataStore,
    private val clock: Clock = Clock.System,
    private val zone: TimeZone = TimeZone.currentSystemDefault(),
) {
    companion object {
        const val CHANNEL_ID = "pet_care"
        const val EVT_HUNGRY = "hungry"
        const val EVT_DIRTY = "dirty"
        const val EVT_SAD = "sad"
        private const val LOW_THRESHOLD = 25f
        private const val HYSTERESIS = 10f
    }

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Pet care", NotificationManager.IMPORTANCE_DEFAULT)
                        .apply { description = "Reminders when a pet needs care." }
                )
            }
        }
    }

    /** Returns the events fired (for tests). */
    suspend fun maybeNotify(pet: Pet, ns: NotificationSettings): List<String> {
        if (!ns.masterOn) return emptyList()
        if (inQuietHours(ns)) return emptyList()
        ensureChannel()
        val fired = mutableListOf<String>()

        suspend fun handle(kind: String, isLow: Boolean, isHigh: Boolean, on: Boolean, message: String) {
            val flag = settings.isNotifyFlagSet(pet.id, kind)
            if (isLow && !flag && on) {
                post(pet.id, kind, message); settings.setNotifyFlag(pet.id, kind, true); fired += kind
            } else if (isHigh && flag) {
                settings.setNotifyFlag(pet.id, kind, false)
            }
        }

        handle(EVT_HUNGRY,
            isLow = pet.stats.hunger < LOW_THRESHOLD,
            isHigh = pet.stats.hunger >= LOW_THRESHOLD + HYSTERESIS,
            on = ns.hungryOn,
            message = "${pet.name} is hungry!")
        handle(EVT_DIRTY,
            isLow = pet.stats.cleanliness < LOW_THRESHOLD || pet.poopCount >= 2,
            isHigh = pet.stats.cleanliness >= LOW_THRESHOLD + HYSTERESIS && pet.poopCount < 2,
            on = ns.dirtyOn,
            message = "${pet.name} needs cleaning!")
        handle(EVT_SAD,
            isLow = pet.stats.happiness < LOW_THRESHOLD,
            isHigh = pet.stats.happiness >= LOW_THRESHOLD + HYSTERESIS,
            on = ns.sadOn,
            message = "${pet.name} misses you")

        return fired
    }

    private fun inQuietHours(ns: NotificationSettings): Boolean {
        val hour = clock.now().toLocalDateTime(zone).hour
        return if (ns.quietStartHour <= ns.quietEndHour) {
            hour in ns.quietStartHour until ns.quietEndHour
        } else {
            hour >= ns.quietStartHour || hour < ns.quietEndHour
        }
    }

    private fun post(petId: Long, kind: String, message: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("petId", petId)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pi = PendingIntent.getActivity(
            context, "$petId$kind".hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_def_app_icon)
            .setContentTitle("Pocket Pets")
            .setContentText(message)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify("$petId$kind".hashCode(), notif) }
    }
}
```

- [ ] **Step 2: Build**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/pocketpets/app/work/NotificationHelper.kt
git commit -m "feat: add NotificationHelper with hysteresis and quiet hours"
```

---

## Task 21: PetCareWorker (TDD)

**Files:**
- Create: `app/src/main/kotlin/com/pocketpets/app/work/PetCareWorker.kt`
- Create: `app/src/test/kotlin/com/pocketpets/app/work/PetCareWorkerTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.pocketpets.app.work

import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.room.Room
import com.google.common.truth.Truth.assertThat
import com.pocketpets.app.data.db.AppDatabase
import com.pocketpets.app.data.repo.PetRepository
import com.pocketpets.app.data.settings.SettingsDataStore
import com.pocketpets.app.domain.Species
import com.pocketpets.app.testing.FakeClock
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PetCareWorkerTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: PetRepository
    private val clock = FakeClock(Instant.parse("2026-01-01T12:00:00Z"))

    @Before fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = PetRepository(db.petDao(), db.careEventDao(), clock)
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.DEBUG)
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(ctx, config)
    }
    @After fun teardown() { db.close() }

    @Test fun `worker decays all pets`() = runTest {
        val id = repo.adopt("Whiskers", Species.CAT)
        // advance fake clock 4h
        clock.advanceBy(4L * 3_600_000)
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val worker = TestListenableWorkerBuilder<PetCareWorker>(ctx)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: android.content.Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters,
                ): ListenableWorker = PetCareWorker(
                    appContext = appContext,
                    params = workerParameters,
                    repo = repo,
                    settings = SettingsDataStore(appContext),
                    notifications = NotificationHelper(appContext, SettingsDataStore(appContext), clock),
                )
            })
            .build()

        val result = worker.doWork()
        assertThat(result).isInstanceOf(ListenableWorker.Result.Success::class.java)
        // After 4h decay: hunger 100 - 32 = 68
        assertThat(repo.getById(id)!!.stats.hunger).isWithin(0.01f).of(68f)
    }
}
```

- [ ] **Step 2: Run, expect failure**

Run: `./gradlew :app:testDebugUnitTest --tests "com.pocketpets.app.work.PetCareWorkerTest"`
Expected: FAIL with "unresolved reference: PetCareWorker".

- [ ] **Step 3: Implement `PetCareWorker.kt`**

```kotlin
package com.pocketpets.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pocketpets.app.PocketPetsApp
import com.pocketpets.app.data.repo.PetRepo
import com.pocketpets.app.data.settings.SettingsDataStore
import kotlinx.coroutines.flow.first

class PetCareWorker(
    appContext: Context,
    params: WorkerParameters,
    private val repo: PetRepo,
    private val settings: SettingsDataStore,
    private val notifications: NotificationHelper,
) : CoroutineWorker(appContext, params) {

    constructor(appContext: Context, params: WorkerParameters) : this(
        appContext, params,
        repo = (appContext.applicationContext as PocketPetsApp).container.petRepository,
        settings = (appContext.applicationContext as PocketPetsApp).container.settings,
        notifications = NotificationHelper(
            appContext,
            (appContext.applicationContext as PocketPetsApp).container.settings,
        ),
    )

    override suspend fun doWork(): Result {
        val pets = repo.observeAll().first()
        val ns = settings.snapshot.first().notificationSettings
        for (pet in pets) {
            repo.runDecayTick(pet.id)
            val refreshed = repo.getById(pet.id) ?: continue
            notifications.maybeNotify(refreshed, ns)
        }
        return Result.success()
    }
}
```

- [ ] **Step 4: Run, expect pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.pocketpets.app.work.PetCareWorkerTest"`
Expected: PASS, 1 test.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/pocketpets/app/work/PetCareWorker.kt \
        app/src/test/kotlin/com/pocketpets/app/work/PetCareWorkerTest.kt
git commit -m "feat: add PetCareWorker with periodic decay and notification hooks"
```

---

## Task 22: WorkManager scheduling + BootReceiver wiring

**Files:**
- Modify: `app/src/main/kotlin/com/pocketpets/app/PocketPetsApp.kt`
- Modify: `app/src/main/kotlin/com/pocketpets/app/work/BootReceiver.kt`
- Create: `app/src/main/kotlin/com/pocketpets/app/work/WorkScheduler.kt`

- [ ] **Step 1: Write `WorkScheduler.kt`**

```kotlin
package com.pocketpets.app.work

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object WorkScheduler {
    private const val WORK_NAME = "pet_care"

    fun schedule(context: Context) {
        val req = PeriodicWorkRequestBuilder<PetCareWorker>(30, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            req,
        )
    }
}
```

- [ ] **Step 2: Update `PocketPetsApp.kt`**

```kotlin
package com.pocketpets.app

import android.app.Application
import com.pocketpets.app.di.AppContainer
import com.pocketpets.app.work.NotificationHelper
import com.pocketpets.app.work.WorkScheduler

class PocketPetsApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        NotificationHelper(this, container.settings).ensureChannel()
        WorkScheduler.schedule(this)
    }
}
```

- [ ] **Step 3: Update `BootReceiver.kt`**

```kotlin
package com.pocketpets.app.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            WorkScheduler.schedule(context)
        }
    }
}
```

- [ ] **Step 4: Build**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/pocketpets/app/PocketPetsApp.kt \
        app/src/main/kotlin/com/pocketpets/app/work/
git commit -m "feat: schedule PetCareWorker on app start and after boot"
```

---

## Task 23: Notification permission + deep link

**Files:**
- Modify: `app/src/main/kotlin/com/pocketpets/app/MainActivity.kt`
- Modify: `app/src/main/kotlin/com/pocketpets/app/ui/nav/AppNav.kt`

- [ ] **Step 1: Update `MainActivity.kt` to request POST_NOTIFICATIONS and surface deep-link extras**

```kotlin
package com.pocketpets.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.pocketpets.app.ui.nav.AppNav
import com.pocketpets.app.ui.theme.PocketPetsTheme

val LocalDeepLinkPetId = compositionLocalOf<Long?> { null }

class MainActivity : ComponentActivity() {

    private val notifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* result ignored — UI fallback already shown when denied */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotifPermissionIfNeeded()
        val deepLinkPetId = intent?.extras?.getLong("petId", -1L)?.takeIf { it > 0 }
        setContent {
            PocketPetsTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CompositionLocalProvider(LocalDeepLinkPetId provides deepLinkPetId) {
                        AppNav()
                    }
                }
            }
        }
    }

    private fun requestNotifPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            if (granted != PackageManager.PERMISSION_GRANTED) {
                notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
```

- [ ] **Step 2: Have `AppNav` honour the deep link**

In `AppNav.kt`, after `val nav = rememberNavController()` and before `NavHost(...)`:
```kotlin
val deepLinkId = androidx.compose.runtime.compositionLocalOf<Long?> { null } // remove this stub line
// (the real LocalDeepLinkPetId is imported below)
```
Actually replace with:
```kotlin
val deepLinkId = com.pocketpets.app.LocalDeepLinkPetId.current
```
Then inside the `composable("pet")` block, before `PetScreen(...)`, add:
```kotlin
androidx.compose.runtime.LaunchedEffect(deepLinkId) {
    if (deepLinkId != null) container.petRepository.setActive(deepLinkId)
}
```

- [ ] **Step 3: Build**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/pocketpets/app/MainActivity.kt \
        app/src/main/kotlin/com/pocketpets/app/ui/nav/AppNav.kt
git commit -m "feat: request POST_NOTIFICATIONS permission and honour notification deep link"
```

---

## Task 24: Idle chatter + final smoke run

**Files:**
- Modify: `app/src/main/kotlin/com/pocketpets/app/ui/pet/PetViewModel.kt`

- [ ] **Step 1: Add idle chatter to `PetViewModel`**

In `PetViewModel.kt`, inside `init { ... }` after the `viewModelScope.launch { flow.collect ... }` block, add:
```kotlin
viewModelScope.launch {
    val chatterPeriodMs = 120_000L // ~once every 2 min while screen open
    com.pocketpets.app.util.ticker(chatterPeriodMs).collect {
        val st = state.value
        if (st.pet != null && _phrase.value == null) {
            _phrase.value = speech.random(st.mood, rng)
        }
    }
}
```

- [ ] **Step 2: Run all tests**

Run: `./gradlew test`
Expected: `BUILD SUCCESSFUL`. All previous test classes pass.

- [ ] **Step 3: Build the debug APK**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`. APK is at `app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 4: Sideload and smoke-test (manual)**

If a device or emulator is available:
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.pocketpets.app/.MainActivity
```
Manually verify: adopt a cat → land on PetScreen → see sprite + stats → tap Feed/Clean/Pet/Talk → see speech bubble flip animal↔translation → tap hamburger to see selector → toggle settings.

If no device, skip and document this step as deferred manual QA in the commit message.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/pocketpets/app/ui/pet/PetViewModel.kt
git commit -m "feat: add periodic idle chatter speech bubbles"
```

---

## Self-Review

The plan covers spec sections 1–8: domain model (Tasks 3–8), data persistence (Tasks 9–11), DI/lifecycle (Task 12), sprite art (Task 13), UI components and screens (Tasks 14–19), background work and notifications (Tasks 20–22), notification permission and deep link (Task 23), and idle chatter polish (Task 24).

Spec items explicitly mapped:
- Multi-day growth (§2.1) — `GrowthStage.fromAge` (Task 5)
- Stat decay rates (§2.2) — `StatDecay.tick` (Task 7)
- Poop mechanic (§2.3) — `StatDecay` poop spawning + `PetRepository.clean` (Tasks 7, 10)
- Care actions (§2.4) — `PetRepository.feed/clean/pet/talk` (Task 10)
- Mood derivation with priority (§2.5) — `Mood.from` (Task 6)
- Speech and translation (§2.6) — `CatSpeech`, `SpeechBubble` (Tasks 8, 14, 15, 24)
- Multiple pets (§2.7) — `PetSelectorSheet`, `setActiveExclusive` (Tasks 9, 18)
- All four screens (§3.1–§3.4) — Tasks 16, 17, 18, 19
- 16-bit-style art (§3.5) — Task 13
- Architecture & boundaries (§4) — covered via the file-structure header and Tasks 9–12, 21
- Recompute-on-read (§4.3) — `PetViewModel` flow combining ticker (Task 15)
- Background worker, channel, hysteresis, quiet hours, deep link (§4.4–§4.5) — Tasks 20–23
- Persistence schema (§4.6) — Task 9
- Tests (§7) — Tasks 5, 6, 7, 8, 9, 10, 15, 21

No "TBD/TODO/implement later/handle edge cases" placeholders; every code step contains the actual code. Type names are consistent across tasks (`PetEntity`/`Pet`/`PetStats`/`PetRepo`/`PetRepository`). Method signatures used in later tasks (`setActive`, `setActiveExclusive`, `runDecayTick`, `observeActive`, `observeAll`, `getById`, `feed`, `clean`, `pet`, `talk`) all match the definitions in Task 9–10. Out-of-scope items from the spec (no additional species, no instrumented UI tests, no in-app purchases) are honoured.

One gap acknowledged: the plan does not include a dedicated step to verify on a physical device because we may not have one available; Task 24 Step 4 makes that explicit and leaves it as deferred manual QA when no hardware is present.
