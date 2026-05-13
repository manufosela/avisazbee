package com.manufosela.avisazbee.app.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = AlertOrange,
    onPrimary = WarmCream,
    secondary = CalmBlue,
    onSecondary = WarmCream,
)

private val DarkColors = darkColorScheme(
    primary = AlertOrangeDark,
    onPrimary = DeepNight,
    secondary = CalmBlueDark,
    onSecondary = DeepNight,
)

@Composable
fun AvisazbeeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}
