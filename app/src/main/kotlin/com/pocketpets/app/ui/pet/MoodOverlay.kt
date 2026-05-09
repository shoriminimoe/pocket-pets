package com.pocketpets.app.ui.pet

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.pocketpets.app.domain.Mood

/**
 * Particle / glyph overlay drawn above the cat sprite to disambiguate moods that
 * share a base animation. Returns nothing visible for moods that don't need it.
 *
 * The overlay fills its parent and draws relative to its own bounds, so size
 * it to match the sprite Box.
 */
@Composable
fun MoodOverlay(
    mood: Mood,
    modifier: Modifier = Modifier,
) {
    // Moods that draw nothing don't need an animation ticker. Bail before
    // rememberInfiniteTransition so we're not recomposing a no-op canvas.
    if (mood == Mood.IDLE || mood == Mood.HUNGRY) return

    val transition = rememberInfiniteTransition(label = "mood-overlay")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 1800, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "phase",
    )
    Canvas(modifier = modifier) {
        when (mood) {
            Mood.HAPPY -> drawHearts(phase)
            Mood.SAD -> drawTear(phase)
            Mood.GROSSED_OUT -> drawSquiggle(phase)
            Mood.SLEEPY -> drawZs(phase)
            Mood.IDLE, Mood.HUNGRY -> Unit // unreachable, handled above
        }
    }
}

private fun DrawScope.drawHearts(phase: Float) {
    val color = Color(0xFFE86A8D)
    val baseY = size.height * 0.55f
    repeat(2) { i ->
        val staggered = (phase + i * 0.5f) % 1f
        val y = baseY * (1f - staggered)
        val x = size.width * (0.55f + 0.12f * i)
        val s = size.minDimension * 0.05f * (1f - staggered * 0.4f)
        val alpha = (1f - staggered).coerceIn(0f, 1f)
        drawHeart(Offset(x, y), s, color.copy(alpha = alpha))
    }
}

private fun DrawScope.drawHeart(
    centre: Offset,
    size: Float,
    color: Color,
) {
    val path =
        Path().apply {
            moveTo(centre.x, centre.y + size * 0.6f)
            cubicTo(
                centre.x - size * 1.2f,
                centre.y - size * 0.2f,
                centre.x - size * 0.4f,
                centre.y - size * 1.2f,
                centre.x,
                centre.y - size * 0.4f,
            )
            cubicTo(
                centre.x + size * 0.4f,
                centre.y - size * 1.2f,
                centre.x + size * 1.2f,
                centre.y - size * 0.2f,
                centre.x,
                centre.y + size * 0.6f,
            )
            close()
        }
    drawPath(path, color)
}

private fun DrawScope.drawTear(phase: Float) {
    val color = Color(0xFF7AB7E8)
    val x = size.width * 0.42f
    val y = size.height * (0.45f + 0.3f * phase)
    val r = size.minDimension * 0.025f
    drawCircle(color = color, radius = r, center = Offset(x, y))
}

private fun DrawScope.drawSquiggle(phase: Float) {
    val color = Color(0xFF1A1A2E)
    val baseY = size.height * 0.32f
    val amplitude = size.height * 0.025f
    val step = size.width / 12f
    val path =
        Path().apply {
            moveTo(size.width * 0.30f, baseY)
            for (i in 1..8) {
                val x = size.width * 0.30f + i * step
                val y = baseY + amplitude * if ((i + (phase * 8).toInt()) % 2 == 0) 1f else -1f
                lineTo(x, y)
            }
        }
    drawPath(path, color, style = Stroke(width = 2f))
}

private fun DrawScope.drawZs(phase: Float) {
    val color = Color(0xFF555571)
    val baseX = size.width * 0.65f
    repeat(2) { i ->
        val staggered = (phase + i * 0.5f) % 1f
        val y = size.height * (0.5f - 0.35f * staggered)
        val x = baseX + i * 6f
        val s = size.minDimension * 0.035f * (1f - staggered * 0.3f)
        val alpha = (1f - staggered).coerceIn(0f, 1f)
        drawZ(Offset(x, y), s, color.copy(alpha = alpha))
    }
}

private fun DrawScope.drawZ(
    centre: Offset,
    size: Float,
    color: Color,
) {
    val path =
        Path().apply {
            moveTo(centre.x - size, centre.y - size)
            lineTo(centre.x + size, centre.y - size)
            lineTo(centre.x - size, centre.y + size)
            lineTo(centre.x + size, centre.y + size)
        }
    drawPath(path, color, style = Stroke(width = 2f))
}
