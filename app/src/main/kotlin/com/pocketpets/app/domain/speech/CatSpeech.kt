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
