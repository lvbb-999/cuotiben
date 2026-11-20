package com.cuotiben.notebook.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val ColorWhite = Color(0xFFFFFFFF)

private val LightColors = lightColorScheme(
    primary = InkGreen,
    onPrimary = ColorWhite,
    primaryContainer = InkGreenLight,
    onPrimaryContainer = Color(0xFF12332A),
    secondary = Terracotta,
    onSecondary = ColorWhite,
    secondaryContainer = TerracottaLight,
    onSecondaryContainer = Color(0xFF4A2412),
    tertiary = Moss,
    onTertiary = ColorWhite,
    tertiaryContainer = Color(0xFFE2E8D8),
    onTertiaryContainer = Color(0xFF2A3320),
    background = Paper,
    onBackground = Ink,
    surface = PaperSurface,
    onSurface = Ink,
    surfaceVariant = PaperVariant,
    onSurfaceVariant = InkSoft,
    surfaceContainerLowest = Color(0xFFFFFDF9),
    surfaceContainerLow = Color(0xFFFBF6EC),
    surfaceContainer = Color(0xFFF5EFE3),
    surfaceContainerHigh = Color(0xFFF0E9DB),
    surfaceContainerHighest = Color(0xFFEAE2D2),
    outline = Linen,
    outlineVariant = LinenLight,
    error = Color(0xFFB3261E),
    onError = ColorWhite,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    inverseSurface = Color(0xFF333029),
    inverseOnSurface = Color(0xFFF7F3EA),
    inversePrimary = DarkGreen,
    scrim = Color(0xFF000000),
)

private val DarkColors = darkColorScheme(
    primary = DarkGreen,
    onPrimary = Color(0xFF0B2E25),
    primaryContainer = DarkGreenContainer,
    onPrimaryContainer = Color(0xFFC7E9DE),
    secondary = DarkTerracotta,
    onSecondary = Color(0xFF3D1B0B),
    secondaryContainer = DarkTerracottaContainer,
    onSecondaryContainer = Color(0xFFFFDBC9),
    tertiary = Color(0xFFB9C9A4),
    onTertiary = Color(0xFF243018),
    tertiaryContainer = Color(0xFF3A4530),
    onTertiaryContainer = Color(0xFFD5E5C2),
    background = DarkPaper,
    onBackground = DarkInk,
    surface = DarkSurface,
    onSurface = DarkInk,
    surfaceVariant = DarkVariant,
    onSurfaceVariant = DarkInkSoft,
    surfaceContainerLowest = Color(0xFF0D110F),
    surfaceContainerLow = Color(0xFF161C18),
    surfaceContainer = Color(0xFF1D2420),
    surfaceContainerHigh = Color(0xFF242C27),
    surfaceContainerHighest = Color(0xFF2C3530),
    outline = Color(0xFF445048),
    outlineVariant = Color(0xFF323B35),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    inverseSurface = Color(0xFFE8E4D9),
    inverseOnSurface = Color(0xFF1A211D),
    inversePrimary = InkGreen,
    scrim = Color(0xFF000000),
)

@Composable
fun CuoTiBenTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? Activity ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(activity.window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
