package com.pocketpets.app.ui.sprite

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.delay

/**
 * Renders one frame of [animation] at a time, advancing every [SpriteAnimation.frameMs]
 * on a `LaunchedEffect`. The bitmap is decoded once per `animation.sheet.resId` and
 * cached via `remember` so swapping animations on the same sheet is free.
 *
 * [facing] adds a row offset for sheets that pack multiple directions per state.
 * [flipHorizontal] mirrors the rendered frame around its vertical axis.
 *
 * For single-frame animations (`frameCount == 1`) the ticker is skipped entirely.
 */
@Composable
fun AnimatedSprite(
    animation: SpriteAnimation,
    modifier: Modifier = Modifier,
    facing: Direction = Direction.SOUTH,
    flipHorizontal: Boolean = false,
) {
    val resources = LocalResources.current
    val bitmap =
        remember(animation.sheet.resId, resources) {
            BitmapFactory.decodeResource(
                resources,
                animation.sheet.resId,
                BitmapFactory.Options().apply { inScaled = false },
            )
        }
    val image = remember(bitmap) { bitmap.asImageBitmap() }

    var frame by remember(animation) { mutableIntStateOf(0) }
    LaunchedEffect(animation) {
        if (animation.frameCount <= 1) return@LaunchedEffect
        while (true) {
            delay(animation.frameMs)
            frame =
                if (animation.loop) {
                    (frame + 1) % animation.frameCount
                } else {
                    (frame + 1).coerceAtMost(animation.frameCount - 1)
                }
            if (!animation.loop && frame == animation.frameCount - 1) break
        }
    }

    val rowOffset =
        when (facing) {
            Direction.SOUTH -> 0
            Direction.NORTH -> 1
            Direction.WEST -> 2
            Direction.EAST -> 3
        }

    Canvas(modifier = modifier) {
        val scaleX = if (flipHorizontal) -1f else 1f
        scale(scaleX = scaleX, scaleY = 1f) {
            drawFrame(
                image = image,
                col = frame,
                row = animation.row + rowOffset,
                frameW = animation.sheet.frameWidth,
                frameH = animation.sheet.frameHeight,
            )
        }
    }
}

private fun DrawScope.drawFrame(
    image: androidx.compose.ui.graphics.ImageBitmap,
    col: Int,
    row: Int,
    frameW: Int,
    frameH: Int,
) {
    drawImage(
        image = image,
        srcOffset = IntOffset(col * frameW, row * frameH),
        srcSize = IntSize(frameW, frameH),
        dstOffset = IntOffset(0, 0),
        dstSize = IntSize(size.width.toInt(), size.height.toInt()),
        filterQuality = FilterQuality.None,
    )
}
