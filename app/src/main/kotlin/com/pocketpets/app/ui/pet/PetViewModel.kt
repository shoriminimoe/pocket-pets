package com.pocketpets.app.ui.pet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketpets.app.data.repo.PetRepo
import com.pocketpets.app.domain.GrowthStage
import com.pocketpets.app.domain.Mood
import com.pocketpets.app.domain.Pet
import com.pocketpets.app.domain.StatDecay
import com.pocketpets.app.domain.behavior.Anchors
import com.pocketpets.app.domain.behavior.CatBehavior
import com.pocketpets.app.domain.behavior.CatBehaviorRules
import com.pocketpets.app.domain.behavior.CatState
import com.pocketpets.app.domain.behavior.HabitatBounds
import com.pocketpets.app.domain.behavior.HabitatWorld
import com.pocketpets.app.domain.behavior.Position
import com.pocketpets.app.domain.speech.Phrase
import com.pocketpets.app.domain.speech.SpeechBank
import com.pocketpets.app.ui.sprite.Direction
import com.pocketpets.app.util.ticker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlin.random.Random

data class PetUiState(
    val pet: Pet? = null,
    val mood: Mood = Mood.IDLE,
    val stage: GrowthStage = GrowthStage.BABY,
    val activePhrase: Phrase? = null,
    val behavior: CatBehavior? = null,
    val world: HabitatWorld = HabitatWorld(),
)

class PetViewModel(
    private val repo: PetRepo,
    private val clock: Clock,
    private val zone: TimeZone,
    private val speech: SpeechBank,
    private val rng: Random = Random.Default,
    externalScope: CoroutineScope? = null,
) : ViewModel() {
    private val scope: CoroutineScope = externalScope ?: viewModelScope

    private val defaultBounds = HabitatBounds(0f, 0f, 240f, 200f)
    private val defaultAnchors =
        Anchors(
            bed = Position(180f, 160f),
            bowl = Position(40f, 160f),
        )

    @Volatile private var habitatBounds: HabitatBounds = defaultBounds

    @Volatile private var habitatAnchors: Anchors = defaultAnchors

    @Volatile private var currentMood: Mood = Mood.IDLE

    private val behaviorFlow: MutableStateFlow<CatBehavior> =
        MutableStateFlow(
            CatBehavior(
                state = CatState.Idle,
                position = Position(120f, 100f),
                target = Position(120f, 100f),
                facing = Direction.SOUTH,
                nextWanderAt = clock.now().plusSeconds(45),
            ),
        )

    private val currentPhrase = MutableStateFlow<Phrase?>(null)
    private val _world: MutableStateFlow<HabitatWorld> = MutableStateFlow(HabitatWorld())
    val world: StateFlow<HabitatWorld> = _world.asStateFlow()

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

    init {
        // Idle chatter: while the screen is active, every 2 minutes, if no
        // bubble is currently shown, surface a phrase from the current mood.
        scope.launch {
            ticker(120_000L).collect {
                val st = state.value
                if (st.pet != null && currentPhrase.value == null) {
                    currentPhrase.value = speech.random(st.mood, rng)
                }
            }
        }
        // Track the latest mood for the frame ticker.
        scope.launch {
            state.collect { ui -> currentMood = ui.mood }
        }
        // Behavior frame ticker: ~60 FPS while subscribed. Pauses with the
        // ViewModel's WhileSubscribed scope.
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
    }

    fun setHabitat(
        bounds: HabitatBounds,
        anchors: Anchors,
    ) {
        habitatBounds = bounds
        habitatAnchors = anchors
    }

    fun feed() = withActive { repo.feed(it) }

    fun clean() = withActive { repo.clean(it) }

    fun pet() = withActive { repo.pet(it) }

    fun talk() =
        withActive { id ->
            val mood = state.value.mood
            val phrase = speech.random(mood, rng)
            currentPhrase.value = phrase
            repo.talk(id)
        }

    fun dismissPhrase() {
        currentPhrase.value = null
    }

    private fun withActive(block: suspend (Long) -> Unit) {
        val id = state.value.pet?.id ?: return
        scope.launch { block(id) }
    }
}

private fun Instant.plusSeconds(s: Long): Instant = Instant.fromEpochMilliseconds(toEpochMilliseconds() + s * 1000)

private fun MutableStateFlow<CatBehavior>.update(transform: (CatBehavior) -> CatBehavior) {
    value = transform(value)
}
