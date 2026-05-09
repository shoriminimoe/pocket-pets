package com.pocketpets.app.ui.pet

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.delay

@Composable
fun SpriteView(
    spriteResId: Int,
    frameCount: Int,
    frameMs: Long = 180,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val sheet = remember(spriteResId) {
        BitmapFactory.decodeResource(context.resources, spriteResId, BitmapFactory.Options().apply {
            inScaled = false
        })
    }
    var frame by remember(spriteResId) { mutableIntStateOf(0) }
    LaunchedEffect(spriteResId, frameCount) {
        if (frameCount <= 1) return@LaunchedEffect
        while (true) {
            delay(frameMs)
            frame = (frame + 1) % frameCount
        }
    }
    val image = remember(sheet) { sheet.asImageBitmap() }
    val frameW = sheet.width / frameCount
    val frameH = sheet.height
    Canvas(modifier = modifier) {
        drawSpriteFrame(image, frame, frameW, frameH)
    }
}

private fun DrawScope.drawSpriteFrame(
    image: androidx.compose.ui.graphics.ImageBitmap,
    frame: Int,
    frameW: Int,
    frameH: Int,
) {
    drawImage(
        image = image,
        srcOffset = IntOffset(frame * frameW, 0),
        srcSize = IntSize(frameW, frameH),
        dstOffset = IntOffset(0, 0),
        dstSize = IntSize(size.width.toInt(), size.height.toInt()),
        filterQuality = FilterQuality.None,
    )
}
