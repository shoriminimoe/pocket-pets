package com.pocketpets.app.ui.pet

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SpeechBubblePlacementTest {
    // Defaults representative of an in-app scene: 400dp-wide habitat, 220dp cat
    // sprite, 160dp bubble, 8dp screen-edge padding, 12dp tail margin.
    private val screenWidth = 400f
    private val catWidth = 220f
    private val bubbleWidth = 160f
    private val padding = 8f
    private val tailMargin = 12f

    @Test
    fun `bubble centers over cat when fully on-screen`() {
        // Cat in the middle of the screen: catX=90 → catCenter=200 → bubble at 200-80=120
        val placement =
            computeSpeechBubblePlacement(
                catX = 90f,
                catWidth = catWidth,
                bubbleWidth = bubbleWidth,
                screenWidth = screenWidth,
                horizontalPadding = padding,
                tailMargin = tailMargin,
            )
        assertThat(placement.bubbleX).isEqualTo(120f)
        // Tail anchor is at cat-center relative to the bubble: 200 - 120 = 80 (bubble center).
        assertThat(placement.tailX).isEqualTo(80f)
    }

    @Test
    fun `right edge clamps bubble and re-anchors tail toward cat`() {
        // Cat hugging the right side: catX=170 → catCenter=280 → naive bubbleX=200,
        // bubble right edge = 360, screenWidth - padding = 392, still fits.
        // Push further: catX=200 → catCenter=310 → naive bubbleX=230, right=390 ≤ 392, still fits.
        // catX=300 → catCenter=410 → naive bubbleX=330, right=490 > 392, clamp:
        //   bubbleX = 400 - 8 - 160 = 232. tail = 410 - 232 = 178 (> bubbleWidth-tailMargin=148).
        // Tail must clamp to bubbleWidth - tailMargin = 148.
        val placement =
            computeSpeechBubblePlacement(
                catX = 300f,
                catWidth = catWidth,
                bubbleWidth = bubbleWidth,
                screenWidth = screenWidth,
                horizontalPadding = padding,
                tailMargin = tailMargin,
            )
        assertThat(placement.bubbleX).isEqualTo(232f)
        assertThat(placement.tailX).isEqualTo(bubbleWidth - tailMargin)
    }

    @Test
    fun `left edge clamps bubble and re-anchors tail toward cat`() {
        // Cat hugging the left side: catX=-100 → catCenter=10 → naive bubbleX=-70,
        // left edge < padding (8), clamp bubbleX=8. tail = 10 - 8 = 2, < tailMargin=12.
        // Tail must clamp to tailMargin = 12.
        val placement =
            computeSpeechBubblePlacement(
                catX = -100f,
                catWidth = catWidth,
                bubbleWidth = bubbleWidth,
                screenWidth = screenWidth,
                horizontalPadding = padding,
                tailMargin = tailMargin,
            )
        assertThat(placement.bubbleX).isEqualTo(padding)
        assertThat(placement.tailX).isEqualTo(tailMargin)
    }

    @Test
    fun `bubble wider than available width centers and pins tail at center`() {
        // Degenerate: bubbleWidth (500) > screenWidth - 2*padding (384).
        // Center the bubble at (screenWidth - bubbleWidth)/2 = -50 and pin tail at bubble center.
        val placement =
            computeSpeechBubblePlacement(
                catX = 90f,
                catWidth = catWidth,
                bubbleWidth = 500f,
                screenWidth = screenWidth,
                horizontalPadding = padding,
                tailMargin = tailMargin,
            )
        assertThat(placement.bubbleX).isEqualTo((screenWidth - 500f) / 2f)
        assertThat(placement.tailX).isEqualTo(250f)
    }
}
