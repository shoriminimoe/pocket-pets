package com.pocketpets.app.ui.pet

import com.google.common.collect.Range
import com.google.common.truth.Truth.assertThat
import com.pocketpets.app.domain.behavior.CatState
import com.pocketpets.app.ui.sprite.Direction
import com.pocketpets.app.ui.sprite.requireFacingFits
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

    @Test
    fun `facingFor coerces non-walking states to SOUTH`() {
        for (facing in Direction.values()) {
            assertThat(CatAnimations.facingFor(CatState.Idle, facing)).isEqualTo(Direction.SOUTH)
            assertThat(CatAnimations.facingFor(CatState.Lying, facing)).isEqualTo(Direction.SOUTH)
        }
    }

    @Test
    fun `facingFor preserves the behavior facing while walking`() {
        for (facing in Direction.values()) {
            assertThat(CatAnimations.facingFor(CatState.Walking, facing)).isEqualTo(facing)
        }
    }

    @Test
    fun `every state and behavior facing combination resolves to a renderable cell`() {
        // Regression guard for the crash on non-south arrivals: any (state, facing)
        // pair that PetScreen could feed into AnimatedSprite must pass the renderer's
        // bounds check after going through facingFor.
        for (state in CatState.values()) {
            for (facing in Direction.values()) {
                val anim = CatAnimations.forState(state)
                val renderFacing = CatAnimations.facingFor(state, facing)
                requireFacingFits(anim, renderFacing)
            }
        }
    }
}
