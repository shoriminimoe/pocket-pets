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
