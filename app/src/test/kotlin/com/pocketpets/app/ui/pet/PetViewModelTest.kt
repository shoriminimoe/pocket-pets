package com.pocketpets.app.ui.pet

import com.google.common.truth.Truth.assertThat
import com.pocketpets.app.data.repo.PetRepo
import com.pocketpets.app.domain.Mood
import com.pocketpets.app.domain.Pet
import com.pocketpets.app.domain.PetStats
import com.pocketpets.app.domain.Species
import com.pocketpets.app.domain.speech.CatSpeech
import com.pocketpets.app.testing.FakeClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import org.junit.Test
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
class PetViewModelTest {
    private val clock = FakeClock(Instant.parse("2026-01-01T12:00:00Z"))
    private val zone = TimeZone.UTC

    private fun samplePet(
        id: Long = 1,
        hunger: Float = 80f,
    ) = Pet(
        id = id,
        name = "Whiskers",
        species = Species.CAT,
        bornAt = clock.now(),
        stats = PetStats(hunger, 80f, 80f, 80f),
        lastTickAt = clock.now(),
        isActive = true,
        poopCount = 0,
        lastFedAt = null,
    )

    private class FakeRepo(
        initial: Pet?,
    ) : PetRepo {
        val activeFlow = MutableStateFlow(initial)
        val calls = mutableListOf<String>()

        override fun observeActive(): Flow<Pet?> = activeFlow

        override fun observeAll(): Flow<List<Pet>> = MutableStateFlow(listOfNotNull(activeFlow.value))

        override suspend fun getById(id: Long) = activeFlow.value?.takeIf { it.id == id }

        override suspend fun adopt(
            name: String,
            species: Species,
        ): Long = error("nyi")

        override suspend fun setActive(id: Long) {}

        override suspend fun feed(id: Long) {
            calls += "feed:$id"
        }

        override suspend fun clean(id: Long) {
            calls += "clean:$id"
        }

        override suspend fun pet(id: Long) {
            calls += "pet:$id"
        }

        override suspend fun talk(id: Long) {
            calls += "talk:$id"
        }

        override suspend fun runDecayTick(id: Long) {
            calls += "tick:$id"
        }
    }

    private fun newVm(
        repo: PetRepo,
        scope: CoroutineScope,
        seed: Int = 0,
    ) = PetViewModel(repo, clock, zone, CatSpeech, rng = Random(seed), externalScope = scope)

    @Test fun `displayed mood reflects stats and time`() =
        runTest {
            val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
            val repo = FakeRepo(samplePet(hunger = 20f))
            val vm = newVm(repo, testScope)
            try {
                val state = vm.state.first { it.pet != null }
                assertThat(state.mood).isEqualTo(Mood.HUNGRY)
            } finally {
                testScope.cancel()
            }
        }

    @Test fun `feed delegates to repo`() =
        runTest {
            val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
            val repo = FakeRepo(samplePet())
            val vm = newVm(repo, testScope)
            try {
                vm.state.first { it.pet != null }
                vm.feed()
                // Unconfined dispatcher: launched coroutines run inline, no scheduling needed.
                assertThat(repo.calls).contains("feed:1")
            } finally {
                testScope.cancel()
            }
        }

    @Test fun `talk emits a phrase from current mood category`() =
        runTest {
            val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
            val repo = FakeRepo(samplePet(hunger = 20f))
            val vm = newVm(repo, testScope, seed = 7)
            try {
                vm.state.first { it.pet != null }
                vm.talk()
                val state = vm.state.first { it.activePhrase != null }
                assertThat(CatSpeech.forMood(Mood.HUNGRY)).contains(state.activePhrase)
            } finally {
                testScope.cancel()
            }
        }

    @Test fun `dismissPhrase clears it`() =
        runTest {
            val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
            val repo = FakeRepo(samplePet(hunger = 20f))
            val vm = newVm(repo, testScope, seed = 7)
            try {
                vm.state.first { it.pet != null }
                vm.talk()
                vm.state.first { it.activePhrase != null }
                vm.dismissPhrase()
                assertThat(vm.state.first().activePhrase).isNull()
            } finally {
                testScope.cancel()
            }
        }

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
                vm.onToyDropped(
                    com.pocketpets.app.domain.behavior
                        .Position(50f, 60f),
                )
                val state = vm.state.first { it.world.toy != null }
                assertThat(state.world.toy)
                    .isEqualTo(
                        com.pocketpets.app.domain.behavior
                            .Position(50f, 60f),
                    )
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
}
