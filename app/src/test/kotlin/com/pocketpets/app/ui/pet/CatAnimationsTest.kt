package com.pocketpets.app.ui.pet

import com.google.common.collect.Range
import com.google.common.truth.Truth.assertThat
import com.pocketpets.app.domain.behavior.CatState
import org.junit.Test

class CatAnimationsTest {
    @Test
    fun `every state maps to a valid animation`() {
        for (state in CatState.values()) {
            val anim = CatAnimations.forState(state)
            assertThat(anim.frameCount).isGreaterThan(0)
            assertThat(anim.row).isAtLeast(0)
            assertThat(anim.frameMs).isGreaterThan(0L)
        }
    }

    @Test
    fun `walk uses the walk row and is multi-frame`() {
        val a = CatAnimations.forState(CatState.Walking)
        assertThat(a).isEqualTo(CatAnimations.walk)
        assertThat(a.frameCount).isAtLeast(2)
        assertThat(a.frameMs).isIn(Range.closed(50L, 250L))
    }

    @Test
    fun `idle uses sit and lying uses lay`() {
        assertThat(CatAnimations.forState(CatState.Idle)).isEqualTo(CatAnimations.sit)
        assertThat(CatAnimations.forState(CatState.Lying)).isEqualTo(CatAnimations.lay)
    }
}
