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
 * [facing] picks a row offset for sheets that pack 4-direction animations into 4
 * consecutive rows. Out-of-bounds combinations of `animation.row + facing offset`
 * are caught by [requireFacingFits], which fails fast rather than reading garbage.
 *
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
    requireFacingFits(animation, facing)
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
        if (animation.loop) {
            while (true) {
                delay(animation.frameMs)
                frame = (frame + 1) % animation.frameCount
            }
        } else {
            // Advance through the remaining frames once and stop on the last.
            while (frame < animation.frameCount - 1) {
                delay(animation.frameMs)
                frame++
            }
        }
    }

    Canvas(modifier = modifier) {
        val scaleX = if (flipHorizontal) -1f else 1f
        scale(scaleX = scaleX, scaleY = 1f) {
            val srcOffset = srcOffsetFor(frame, animation, facing)
            drawImage(
                image = image,
                srcOffset = srcOffset,
                srcSize = IntSize(animation.sheet.frameWidth, animation.sheet.frameHeight),
                dstOffset = IntOffset(0, 0),
                dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                filterQuality = FilterQuality.None,
            )
        }
    }
}

/**
 * Pure mapping of (frame index, animation, facing) to the top-left source pixel
 * for that frame on the sprite sheet. Extracted so the math can be unit-tested
 * without touching the Compose renderer.
 */
internal fun srcOffsetFor(
    frame: Int,
    animation: SpriteAnimation,
    facing: Direction,
): IntOffset {
    val row = animation.row + facingRowOffset(facing)
    return IntOffset(
        x = frame * animation.sheet.frameWidth,
        y = row * animation.sheet.frameHeight,
    )
}

/** Row offset added on top of [SpriteAnimation.row] when rendering [facing]. */
internal fun facingRowOffset(facing: Direction): Int =
    when (facing) {
        Direction.SOUTH -> 0
        Direction.NORTH -> 1
        Direction.WEST -> 2
        Direction.EAST -> 3
    }

/**
 * Fail-fast bounds check before drawing. Lets a Phase 3 caller pass
 * Direction.NORTH against a sheet that lacks the required rows and get a clear
 * error instead of a garbage frame.
 */
internal fun requireFacingFits(
    animation: SpriteAnimation,
    facing: Direction,
) {
    val totalRow = animation.row + facingRowOffset(facing)
    require(totalRow < animation.sheet.rows) {
        "facing=$facing on animation.row=${animation.row} reads row=$totalRow which is " +
            "out of bounds for sheet with ${animation.sheet.rows} rows"
    }
}
