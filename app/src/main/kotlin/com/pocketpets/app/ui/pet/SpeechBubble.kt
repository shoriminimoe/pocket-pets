package com.pocketpets.app.ui.pet

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pocketpets.app.domain.speech.Phrase
import kotlinx.coroutines.delay

@Composable
fun SpeechBubble(
    phrase: Phrase?,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit,
    autoDismissMs: Long = 4000,
) {
    var translated by remember(phrase) { mutableStateOf(false) }
    var paused by remember(phrase) { mutableStateOf(false) }

    LaunchedEffect(phrase) {
        if (phrase == null) return@LaunchedEffect
        var elapsed = 0L
        val tick = 100L
        while (elapsed < autoDismissMs) {
            delay(tick)
            if (!paused) elapsed += tick
        }
        onDismiss()
    }

    AnimatedVisibility(visible = phrase != null, modifier = modifier) {
        if (phrase != null) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .border(2.dp, Color(0xFF1A1A2E), RoundedCornerShape(12.dp))
                    .clickable {
                        paused = true
                        translated = !translated
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(
                    text = if (translated) phrase.translation else phrase.animal,
                    color = Color(0xFF1A1A2E),
                )
            }
        }
    }
}
