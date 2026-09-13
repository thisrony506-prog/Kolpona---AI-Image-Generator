package com.kolpona.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.kolpona.app.domain.model.ThemeMode

private val LightColors = lightColorScheme(
    primary = PinkAccent,
    onPrimary = Color.White,
    primaryContainer = PinkSoft,
    onPrimaryContainer = Ink,
    secondary = PinkAccentDark,
    onSecondary = Color.White,
    background = Paper,
    onBackground = Ink,
    surface = SurfaceLight,
    onSurface = Ink,
    surfaceVariant = Color(0xFFF3F3F5),
    onSurfaceVariant = InkSecondary,
    outline = Hairline,
    error = Color(0xFFB3261E)
)

private val DarkColors = darkColorScheme(
    primary = PinkOnDark,
    onPrimary = Ink,
    primaryContainer = Color(0xFF3A1528),
    onPrimaryContainer = DarkInk,
    secondary = PinkMuted,
    onSecondary = Ink,
    background = DarkBg,
    onBackground = DarkInk,
    surface = DarkSurface,
    onSurface = DarkInk,
    surfaceVariant = Color(0xFF1E1E24),
    onSurfaceVariant = DarkSecondary,
    outline = DarkHairline,
    error = Color(0xFFFFB4AB)
)

@Composable
fun KolponaTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !dark
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !dark
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }
        }
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = KolponaTypography,
        content = content
    )
}
