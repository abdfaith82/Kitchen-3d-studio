package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val StudioDarkColorScheme = darkColorScheme(
    primary = CyanCADHighlight,
    onPrimary = Color.Black,
    primaryContainer = CyanCAD,
    onPrimaryContainer = Color.White,
    secondary = AmberWarm,
    onSecondary = Color.Black,
    tertiary = EmeraldSnap,
    onTertiary = Color.Black,
    background = SlateDark950,
    onBackground = SlateLight100,
    surface = SlateDark900,
    onSurface = SlateLight100,
    surfaceVariant = SlateDark800,
    onSurfaceVariant = SlateMuted400,
    outline = SlateDark700,
    error = CoralError
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Kitchen CAD studio defaults to precision dark theme
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = StudioDarkColorScheme,
        typography = Typography,
        content = content
    )
}
