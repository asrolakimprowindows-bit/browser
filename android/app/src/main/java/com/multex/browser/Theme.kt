package com.multex.browser

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

/*
 * Colors mirror app/globals.css. They are Compose state so switching theme recomposes
 * everything that reads the palette without threading a theme object through every composable.
 */
object Palette {
    var isLight by mutableStateOf(false)
    var Ink by mutableStateOf(Color(0xFFF4F1FF))
    var InkMuted by mutableStateOf(Color(0xFFA3A1C6))
    var Pink by mutableStateOf(Color(0xFFF79AC8))
    var Lavender by mutableStateOf(Color(0xFFB9A9FF))
    var Sky by mutableStateOf(Color(0xFF8AA8FF))
    var Glass by mutableStateOf(Color(0x0FFFFFFF))
    var GlassStrong by mutableStateOf(Color(0xE01A1B36))
    var GlassBorder by mutableStateOf(Color(0x1AFFFFFF))
    var ScreenTop by mutableStateOf(Color(0xFF11132C))
    var ScreenBottom by mutableStateOf(Color(0xFF0A0B1C))
    var GlowA by mutableStateOf(Color(0xFF2C2456))
    var GlowB by mutableStateOf(Color(0xFF3D1F44))

    /** Text drawn on top of the pink-to-lavender accent gradient. */
    val OnAccent = Color(0xFF2A1236)

    /** Letter inside a LetterTile. */
    val TileInk = Color(0xFF17172F)

    fun apply(theme: ThemeId) {
        if (theme == ThemeId.LIQUID) {
            // Liquid Glass (iOS-style): deep space blue, frosted panels, icy cyan accents.
            isLight = false
            Ink = Color(0xFFF2F7FF); InkMuted = Color(0xFF8FA3C8)
            Pink = Color(0xFF7CD4FD); Lavender = Color(0xFF5E8CE6); Sky = Color(0xFF7CE7F4)
            Glass = Color(0x1FFFFFFF); GlassStrong = Color(0xC2121A30); GlassBorder = Color(0x40FFFFFF)
            ScreenTop = Color(0xFF0B1020); ScreenBottom = Color(0xFF04060F)
            GlowA = Color(0xFF1E3A6E); GlowB = Color(0xFF0E4A5E)
        } else if (theme == ThemeId.SAKURA) {
            isLight = true
            Ink = Color(0xFF2B2140); InkMuted = Color(0xFF7D7297)
            Pink = Color(0xFFE86FAE); Lavender = Color(0xFF7F6BD6); Sky = Color(0xFF5B82E6)
            Glass = Color(0x8CFFFFFF); GlassStrong = Color(0xF0FFFAFD); GlassBorder = Color(0xD9FFFFFF)
            ScreenTop = Color(0xFFFFF3F9); ScreenBottom = Color(0xFFF4EFFF)
            GlowA = Color(0xFFFFD6EA); GlowB = Color(0xFFD9D2FF)
        } else {
            isLight = false
            Ink = Color(0xFFF4F1FF); InkMuted = Color(0xFFA3A1C6)
            Pink = Color(0xFFF79AC8); Lavender = Color(0xFFB9A9FF); Sky = Color(0xFF8AA8FF)
            Glass = Color(0x0FFFFFFF); GlassStrong = Color(0xE01A1B36); GlassBorder = Color(0x1AFFFFFF)
            ScreenTop = Color(0xFF11132C); ScreenBottom = Color(0xFF0A0B1C)
            GlowA = Color(0xFF2C2456); GlowB = Color(0xFF3D1F44)
        }
    }
}

object Fonts {
    // Static instances cut from the variable fonts. The variable files default to ExtraLight (Nunito)
    // and Light (Fredoka), so mapping every weight to one file made all text look thin.
    val Sans = FontFamily(
        Font(R.font.nunito, FontWeight.Normal),
        Font(R.font.nunito_medium, FontWeight.Medium),
        Font(R.font.nunito_semibold, FontWeight.SemiBold),
        Font(R.font.nunito_bold, FontWeight.Bold),
        Font(R.font.nunito_extrabold, FontWeight.ExtraBold),
    )
    val Display = FontFamily(
        Font(R.font.fredoka, FontWeight.Normal),
        Font(R.font.fredoka_medium, FontWeight.Medium),
        Font(R.font.fredoka_semibold, FontWeight.SemiBold),
        Font(R.font.fredoka_bold, FontWeight.Bold),
    )
}

@Composable
fun MultexTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    val isLight = Palette.isLight
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = isLight
            controller.isAppearanceLightNavigationBars = isLight
        }
    }

    val scheme = if (isLight) {
        lightColorScheme(
            primary = Palette.Pink,
            onPrimary = Palette.OnAccent,
            secondary = Palette.Lavender,
            onSecondary = Palette.OnAccent,
            background = Palette.ScreenBottom,
            onBackground = Palette.Ink,
            surface = Palette.ScreenTop,
            onSurface = Palette.Ink,
            onSurfaceVariant = Palette.InkMuted,
            outline = Palette.GlassBorder,
        )
    } else {
        darkColorScheme(
            primary = Palette.Pink,
            onPrimary = Palette.OnAccent,
            secondary = Palette.Lavender,
            onSecondary = Palette.OnAccent,
            background = Palette.ScreenBottom,
            onBackground = Palette.Ink,
            surface = Palette.ScreenTop,
            onSurface = Palette.Ink,
            onSurfaceVariant = Palette.InkMuted,
            outline = Palette.GlassBorder,
        )
    }

    val base = Typography()
    val typography = Typography(
        displayLarge = base.displayLarge.copy(fontFamily = Fonts.Display),
        displayMedium = base.displayMedium.copy(fontFamily = Fonts.Display),
        displaySmall = base.displaySmall.copy(fontFamily = Fonts.Display),
        headlineLarge = base.headlineLarge.copy(fontFamily = Fonts.Display),
        headlineMedium = base.headlineMedium.copy(fontFamily = Fonts.Display),
        headlineSmall = base.headlineSmall.copy(fontFamily = Fonts.Display),
        titleLarge = base.titleLarge.copy(fontFamily = Fonts.Display),
        titleMedium = base.titleMedium.copy(fontFamily = Fonts.Sans),
        titleSmall = base.titleSmall.copy(fontFamily = Fonts.Sans),
        bodyLarge = base.bodyLarge.copy(fontFamily = Fonts.Sans),
        bodyMedium = base.bodyMedium.copy(fontFamily = Fonts.Sans),
        bodySmall = base.bodySmall.copy(fontFamily = Fonts.Sans),
        labelLarge = base.labelLarge.copy(fontFamily = Fonts.Sans),
        labelMedium = base.labelMedium.copy(fontFamily = Fonts.Sans),
        labelSmall = base.labelSmall.copy(fontFamily = Fonts.Sans),
    )

    MaterialTheme(colorScheme = scheme, typography = typography) {
        // Every Text falls back to Nunito + the palette ink unless it says otherwise.
        ProvideTextStyle(TextStyle(fontFamily = Fonts.Sans, color = Palette.Ink)) { content() }
    }
}

/* ---------- Shared modifiers (the .glass / .glass-strong / .screen classes from globals.css) ---------- */

fun Modifier.glass(shape: Shape = RoundedCornerShape(16.dp)): Modifier =
    clip(shape).background(Palette.Glass).border(1.dp, Palette.GlassBorder, shape)

fun Modifier.glassStrong(shape: Shape = RoundedCornerShape(16.dp)): Modifier =
    clip(shape).background(Palette.GlassStrong).border(1.dp, Palette.GlassBorder, shape)

fun Modifier.glass(radius: Dp): Modifier = glass(RoundedCornerShape(radius))
fun Modifier.glassStrong(radius: Dp): Modifier = glassStrong(RoundedCornerShape(radius))

/**
 * Translucent, layered chrome for the browser controls. It deliberately keeps the page visible
 * beneath the address and bottom bars while adding enough tint and edge highlights for legibility.
 */
fun Modifier.liquidGlass(shape: Shape = RoundedCornerShape(16.dp)): Modifier {
    val light = Palette.isLight
    val upper = if (light) {
        Color.White.copy(alpha = 0.74f)
    } else {
        Color(0xFF2C3767).copy(alpha = 0.60f)
    }
    val lower = if (light) {
        Color(0xFFE4E9FF).copy(alpha = 0.50f)
    } else {
        Color(0xFF10172F).copy(alpha = 0.54f)
    }
    val edge = Color.White.copy(alpha = if (light) 0.90f else 0.48f)
    val edgeTint = Palette.Sky.copy(alpha = if (light) 0.28f else 0.30f)
    return shadow(
        elevation = 22.dp,
        shape = shape,
        ambientColor = Palette.Lavender.copy(alpha = 0.16f),
        spotColor = Color.Black.copy(alpha = 0.30f),
    )
        .clip(shape)
        .background(
            Brush.linearGradient(
                colors = listOf(upper, Palette.Glass.copy(alpha = 0.90f), lower),
            ),
        )
        .drawBehind {
            drawRect(
                Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (light) 0.24f else 0.08f),
                        Color.Transparent,
                    ),
                    center = Offset(size.width * 0.20f, size.height * 0.05f),
                    radius = size.maxDimension * 0.90f,
                ),
            )
            drawRect(
                Brush.radialGradient(
                    colors = listOf(
                        Palette.Sky.copy(alpha = if (light) 0.12f else 0.08f),
                        Color.Transparent,
                    ),
                    center = Offset(size.width * 0.90f, size.height),
                    radius = size.maxDimension * 0.80f,
                ),
            )
        }
        .border(1.dp, Brush.linearGradient(listOf(edge, edgeTint, edge)), shape)
}

fun Modifier.liquidGlass(radius: Dp): Modifier = liquidGlass(RoundedCornerShape(radius))

/** Pink-to-lavender accent used for primary buttons, with the soft pink drop shadow from the web build. */
fun Modifier.accent(shape: Shape, elevation: Dp = 10.dp): Modifier =
    shadow(elevation, shape, ambientColor = Palette.Pink, spotColor = Palette.Pink)
        .clip(shape)
        .background(Brush.linearGradient(listOf(Palette.Pink, Palette.Lavender)))

/** Subtle translucent ink fill (bg-ink/10). */
fun Modifier.inkFill(shape: Shape, alpha: Float = 0.10f): Modifier =
    clip(shape).background(Palette.Ink.copy(alpha = alpha))

/** Replicates --scr-bg: two soft radial glows over a vertical gradient. */
@Composable
fun Modifier.screenBackground(): Modifier {
    val top = Palette.ScreenTop
    val bottom = Palette.ScreenBottom
    val glowA = Palette.GlowA
    val glowB = Palette.GlowB
    return drawBehind {
        drawRect(Brush.verticalGradient(listOf(top, bottom)))
        drawRect(
            Brush.radialGradient(
                listOf(glowA, glowA.copy(alpha = 0f)),
                center = Offset(size.width * 0.15f, 0f),
                radius = size.width * 1.05f,
            ),
        )
        drawRect(
            Brush.radialGradient(
                listOf(glowB, glowB.copy(alpha = 0f)),
                center = Offset(size.width, size.height),
                radius = size.width * 0.9f,
            ),
        )
    }
}

/** Solid version of the screen background, for places that cannot draw gradients (e.g. a tab thumbnail). */
val Palette.screenBrush: Brush
    get() = Brush.verticalGradient(listOf(ScreenTop, ScreenBottom))
