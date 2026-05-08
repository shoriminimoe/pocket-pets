package com.pocketpets.app.domain.speech

import com.google.common.truth.Truth.assertThat
import com.pocketpets.app.domain.Mood
import org.junit.Test
import kotlin.random.Random

class CatSpeechTest {
    @Test fun `every mood has at least 4 phrases`() {
        Mood.values().forEach { mood ->
            assertThat(CatSpeech.forMood(mood).size).isAtLeast(4)
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
