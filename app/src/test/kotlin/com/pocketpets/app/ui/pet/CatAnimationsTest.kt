package com.pocketpets.app.ui.pet

import com.google.common.truth.Truth.assertThat
import com.pocketpets.app.domain.GrowthStage
import com.pocketpets.app.domain.Mood
import org.junit.Test

class CatAnimationsTest {
    @Test
    fun `every mood and stage maps to a valid animation`() {
        for (stage in GrowthStage.values()) {
            for (mood in Mood.values()) {
                val anim = CatAnimations.forMood(stage, mood)
                assertThat(anim.frameCount).isGreaterThan(0)
                assertThat(anim.row).isAtLeast(0)
                assertThat(anim.frameMs).isGreaterThan(0L)
            }
        }
    }

    @Test
    fun `sleepy is the lying pose`() {
        for (stage in GrowthStage.values()) {
            assertThat(CatAnimations.forMood(stage, Mood.SLEEPY)).isEqualTo(CatAnimations.lay)
        }
    }

    @Test
    fun `non-sleepy moods all use the sitting pose`() {
        val nonSleepy = Mood.values().filter { it != Mood.SLEEPY }
        for (stage in GrowthStage.values()) {
            for (mood in nonSleepy) {
                assertThat(CatAnimations.forMood(stage, mood)).isEqualTo(CatAnimations.sit)
            }
        }
    }
}
