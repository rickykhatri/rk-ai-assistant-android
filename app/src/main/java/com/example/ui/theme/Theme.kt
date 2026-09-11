package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.example.data.preferences.ThemeMode

private val DarkColorScheme = darkColorScheme(
    primary = SophisticatedPurplePrimary,
    onPrimary = SophisticatedPurpleOnPrimary,
    primaryContainer = SophisticatedPurpleContainer,
    onPrimaryContainer = SophisticatedPurpleOnContainer,
    secondary = SophisticatedPurplePrimary,
    onSecondary = SophisticatedPurpleOnPrimary,
    secondaryContainer = SophisticatedDarkSurfaceElevated,
    onSecondaryContainer = SophisticatedDarkTextPrimary,
    background = SophisticatedDarkBackground,
    onBackground = SophisticatedDarkTextPrimary,
    surface = SophisticatedDarkSurface,
    onSurface = SophisticatedDarkTextPrimary,
    surfaceVariant = SophisticatedDarkSurfaceVariant,
    onSurfaceVariant = SophisticatedDarkTextSecondary,
    outline = SophisticatedDarkBorder,
    outlineVariant = SophisticatedDarkBorder.copy(alpha = 0.5f),
    error = StatusErrorRed,
    onError = StatusErrorDarkContainer,
    errorContainer = StatusErrorDarkContainer,
    onErrorContainer = StatusErrorRed
)

private val LightColorScheme = lightColorScheme(
    primary = SophisticatedLightPrimary,
    onPrimary = SophisticatedLightOnPrimary,
    primaryContainer = SophisticatedLightPrimaryContainer,
    onPrimaryContainer = SophisticatedLightOnPrimaryContainer,
    secondary = SophisticatedLightPrimary,
    onSecondary = SophisticatedLightOnPrimary,
    secondaryContainer = SophisticatedLightSurfaceElevated,
    onSecondaryContainer = SophisticatedLightTextPrimary,
    background = SophisticatedLightBackground,
    onBackground = SophisticatedLightTextPrimary,
    surface = SophisticatedLightSurface,
    onSurface = SophisticatedLightTextPrimary,
    surfaceVariant = SophisticatedLightSurfaceVariant,
    onSurfaceVariant = SophisticatedLightTextSecondary,
    outline = SophisticatedLightBorder,
    outlineVariant = SophisticatedLightBorder.copy(alpha = 0.5f),
    error = StatusErrorText,
    onError = Color.White
)

@Composable
fun AIChatTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }

    val colorScheme = if (isDark) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = colorScheme.background.toArgb()
                window.navigationBarColor = colorScheme.background.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDark
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !isDark
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
