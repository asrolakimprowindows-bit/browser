package com.multex.browser

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.multex.browser.denia.DeniaPalette

@Composable
fun MultexTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = DeniaPalette.Pink,
            onPrimary = DeniaPalette.Midnight,
            secondary = DeniaPalette.Lavender,
            onSecondary = DeniaPalette.Midnight,
            tertiary = DeniaPalette.Sky,
            background = DeniaPalette.Midnight,
            onBackground = DeniaPalette.Ink,
            surface = DeniaPalette.Surface,
            onSurface = DeniaPalette.Ink,
            surfaceVariant = DeniaPalette.MidnightSoft,
            onSurfaceVariant = DeniaPalette.InkMuted,
            outline = Color(0x33FFFFFF),
        ),
        content = content,
    )
}
