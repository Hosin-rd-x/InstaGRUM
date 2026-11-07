package com.instaos.app.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.instaos.app.model.AccentColor

val ActionBlue = Color(0xFF0095F6)
val IgElevated = Color(0xFF121212)
val IgButton = Color(0xFF262626)
val IgSecondaryText = Color(0xFFA8A8A8)
val IgDivider = Color(0xFF262626)
val IgSeenRing = Color(0xFF4A4A4A)

/** Accent colours (from the HTML preview spec; violet is the default). */
val AccentViolet = Color(0xFF6C5CE7)
val AccentLime = Color(0xFFC7F24E)
val AccentPink = Color(0xFFFF5C7A)
val AccentTeal = Color(0xFF22C3AA)

fun accentColor(accent: AccentColor?): Color = when (accent) {
    AccentColor.LIME -> AccentLime
    AccentColor.PINK -> AccentPink
    AccentColor.TEAL -> AccentTeal
    else -> AccentViolet
}

/** The user's accent colour, for buttons/badges/highlights. */
val LocalAccent = staticCompositionLocalOf { AccentViolet }

/**
 * Phase 3 restyle (iOS Instagram 454.1 §1.12): dark canvas is TRUE BLACK
 * (`0xFF000000` — the old `IgShell 0xFF0C1014` is gone), light canvas pure
 * white. Sheets/cards sit on `0xFF121212` in dark mode.
 */
private fun darkScheme(accent: Color) = darkColorScheme(
    primary = ActionBlue, onPrimary = Color.White,
    secondary = Color(0xFFF5F5F5), onSecondary = Color.Black,
    tertiary = accent, background = Color.Black, surface = IgElevated,
    surfaceVariant = IgButton, onSurface = Color(0xFFF5F5F5),
    onSurfaceVariant = IgSecondaryText, outline = Color(0xFF555555), outlineVariant = IgDivider,
)

private fun lightScheme(accent: Color) = lightColorScheme(
    primary = ActionBlue, onPrimary = Color.White, secondary = Color(0xFF262626),
    tertiary = accent,
    background = Color.White, surface = Color.White, surfaceVariant = Color(0xFFEFEFEF),
    onSurface = Color(0xFF151515), onSurfaceVariant = Color(0xFF737373), outlineVariant = Color(0xFFDBDBDB),
)

private val Standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private val EmphasizedDecelerate = CubicBezierEasing(0.1f, 0.7f, 0.1f, 1f)

object Motion {
    fun <T> screen() = tween<T>(300, easing = Standard)
    fun <T> tab() = tween<T>(200, easing = Standard)
    fun <T> enter() = tween<T>(350, easing = EmphasizedDecelerate)
    fun <T> quick() = tween<T>(150, easing = Standard)
    fun <T> pop() = spring<T>(dampingRatio = 0.6f, stiffness = 800f)
    fun <T> press() = spring<T>(dampingRatio = 0.75f, stiffness = 1400f)
    fun <T> settle() = spring<T>(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = 380f)
}

@Composable
fun InstaTheme(dark: Boolean, accent: AccentColor? = null, content: @Composable () -> Unit) {
    val accentColor = accentColor(accent)
    CompositionLocalProvider(LocalAccent provides accentColor) {
        MaterialTheme(
            colorScheme = if (dark) darkScheme(accentColor) else lightScheme(accentColor),
            content = content,
        )
    }
}
