package com.pocketpets.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = Rose,
    secondary = Mint,
    tertiary = Butter,
    background = Cream,
    surface = Cream,
    onPrimary = Cream,
    onSecondary = Ink,
    onTertiary = Ink,
    onBackground = Ink,
    onSurface = Ink,
)

@Composable
fun PocketPetsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = PocketPetsTypography,
        content = content,
    )
}
