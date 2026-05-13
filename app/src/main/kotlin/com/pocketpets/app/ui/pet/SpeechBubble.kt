package com.pocketpets.app.ui.pet

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pocketpets.app.domain.speech.Phrase
import kotlinx.coroutines.delay

/** Result of [computeSpeechBubblePlacement]: where to draw the bubble and where its tail attaches. */
data class SpeechBubblePlacement(
    /** Bubble's top-left x in the same coordinate space as `catX` / `screenWidth`. */
    val bubbleX: Float,
    /** Bubble's top-left y in the same coordinate space as `catY`. */
    val bubbleY: Float,
    /** Tail anchor x, expressed in the bubble's own local coordinate space (0..bubbleWidth). */
    val tailX: Float,
)

/**
 * Pure-function placement for the cat's speech bubble.
 *
 * Horizontally: centers a [bubbleWidth]-wide bubble over the cat's horizontal
 * center ([catX] + [catWidth] / 2), then clamps the bubble within
 * [[horizontalPadding], [screenWidth] - [horizontalPadding]]. The tail's x is
 * re-anchored toward the cat's center so it still points at the cat after the
 * clamp, and is itself kept within [[tailMargin], [bubbleWidth] - [tailMargin]]
 * so the tail never spills past the bubble's rounded corners. Degenerate case:
 * when the bubble is wider than the available width, it's centered (which lets
 * it spill equally on both sides — the only visually reasonable choice) and
 * the tail pins to the bubble's center.
 *
 * Vertically: positions the bubble so the tail tip's y lands at
 * [catY] - [tailTipGap] regardless of [bubbleHeight]. This keeps the tail
 * pointing at the same spot above the cat's head whether the bubble is one
 * line tall or wrapped to several lines after horizontal clamping (issue #37).
 */
fun computeSpeechBubblePlacement(
    catX: Float,
    catY: Float,
    catWidth: Float,
    bubbleWidth: Float,
    bubbleHeight: Float,
    screenWidth: Float,
    horizontalPadding: Float,
    tailMargin: Float,
    tailTipGap: Float,
): SpeechBubblePlacement {
    val catCenter = catX + catWidth / 2f
    val available = screenWidth - 2f * horizontalPadding
    val bubbleX =
        if (bubbleWidth >= available) {
            (screenWidth - bubbleWidth) / 2f
        } else {
            val naive = catCenter - bubbleWidth / 2f
            val minX = horizontalPadding
            val maxX = screenWidth - horizontalPadding - bubbleWidth
            naive.coerceIn(minX, maxX)
        }
    val rawTail = catCenter - bubbleX
    val tailLo = tailMargin.coerceAtMost(bubbleWidth / 2f)
    val tailHi = (bubbleWidth - tailMargin).coerceAtLeast(bubbleWidth / 2f)
    val tailX = rawTail.coerceIn(tailLo, tailHi)
    val bubbleY = catY - tailTipGap - bubbleHeight
    return SpeechBubblePlacement(bubbleX = bubbleX, bubbleY = bubbleY, tailX = tailX)
}

/**
 * Rounded-rectangle speech-bubble shape with a triangular tail centered at
 * [tailX] (measured in the bubble's local px coordinate space). The tail
 * points downward off the bottom edge.
 */
fun speechBubbleShape(
    tailX: Float,
    cornerRadiusPx: Float,
    tailWidthPx: Float,
    tailHeightPx: Float,
): GenericShape =
    GenericShape { size, _ ->
        val bodyBottom = size.height - tailHeightPx
        val r = cornerRadiusPx.coerceAtMost(minOf(size.width, bodyBottom) / 2f)
        // Rounded-rect body
        addRoundRect(
            RoundRect(
                left = 0f,
                top = 0f,
                right = size.width,
                bottom = bodyBottom,
                cornerRadius = CornerRadius(r, r),
            ),
        )
        // Triangular tail, anchored at tailX but kept clear of the rounded corners.
        val halfTail = tailWidthPx / 2f
        val tailCenter = tailX.coerceIn(r + halfTail, size.width - r - halfTail)
        moveTo(tailCenter - halfTail, bodyBottom)
        lineTo(tailCenter, size.height)
        lineTo(tailCenter + halfTail, bodyBottom)
        close()
    }

private val CornerRadiusDp = 12.dp
private val TailWidthDp = 14.dp
private val TailHeightDp = 8.dp

/** dp the speech bubble is held back from the left/right edges of the play area. */
const val SPEECH_BUBBLE_EDGE_PADDING_DP = 8f

/** dp from each end of the bubble that the tail anchor can't cross (keeps it clear of corners). */
const val SPEECH_BUBBLE_TAIL_MARGIN_DP = 16f

/** dp gap between the speech-bubble tail tip and the top of the cat sprite. */
const val SPEECH_BUBBLE_TAIL_TIP_GAP_DP = 8f

@Composable
fun SpeechBubble(
    phrase: Phrase?,
    onDismiss: () -> Unit,
    tailX: Dp,
    modifier: Modifier = Modifier,
    autoDismissMs: Long = 4000,
) {
    var translated by remember(phrase) { mutableStateOf(false) }
    var paused by remember(phrase) { mutableStateOf(false) }
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val density = LocalDensity.current

    LaunchedEffect(phrase) {
        if (phrase == null) return@LaunchedEffect
        var elapsed = 0L
        val tick = 100L
        while (elapsed < autoDismissMs) {
            delay(tick)
            if (!paused) elapsed += tick
        }
        currentOnDismiss()
    }

    AnimatedVisibility(visible = phrase != null, modifier = modifier) {
        if (phrase != null) {
            val shape =
                with(density) {
                    speechBubbleShape(
                        tailX = tailX.toPx(),
                        cornerRadiusPx = CornerRadiusDp.toPx(),
                        tailWidthPx = TailWidthDp.toPx(),
                        tailHeightPx = TailHeightDp.toPx(),
                    )
                }
            Box(
                modifier =
                    Modifier
                        .clip(shape)
                        .background(Color.White, shape)
                        .border(2.dp, Color(0xFF1A1A2E), shape)
                        .clickable {
                            paused = true
                            translated = !translated
                        }.padding(
                            start = 12.dp,
                            end = 12.dp,
                            top = 8.dp,
                            bottom = TailHeightDp + 8.dp,
                        ),
            ) {
                Text(
                    text = if (translated) phrase.translation else phrase.animal,
                    color = Color(0xFF1A1A2E),
                )
            }
        }
    }
}
