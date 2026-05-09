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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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
    externalScope: CoroutineScope? = null,
) : ViewModel() {

    private val scope: CoroutineScope = externalScope ?: viewModelScope

    private val _phrase = MutableStateFlow<Phrase?>(null)
    val state: StateFlow<PetUiState> = combine(
        repo.observeActive(),
        ticker(60_000L),
        _phrase,
    ) { rawPet, _, phrase ->
        val now = clock.now()
        val ticked = rawPet?.let { StatDecay.tick(it, now) }
        val mood = ticked?.let { Mood.from(it, now, zone) } ?: Mood.IDLE
        val stage = ticked?.let { GrowthStage.fromAge(it.bornAt, now) } ?: GrowthStage.BABY
        PetUiState(ticked, mood, stage, phrase)
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), PetUiState())

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
        scope.launch { block(id) }
    }
}
