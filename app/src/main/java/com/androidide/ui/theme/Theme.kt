package com.androidide.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val IDEDarkColorScheme = darkColorScheme(
    primary             = Primary,
    onPrimary           = OnPrimary,
    primaryContainer    = PrimaryVariant,
    onPrimaryContainer  = OnBackground,
    secondary           = Secondary,
    onSecondary         = OnPrimary,
    secondaryContainer  = Color(0xFF1C4A1C),
    onSecondaryContainer= Secondary,
    tertiary            = Tertiary,
    onTertiary          = OnPrimary,
    background          = Background,
    onBackground        = OnBackground,
    surface             = Surface,
    onSurface           = OnSurface,
    surfaceVariant      = SurfaceVariant,
    onSurfaceVariant    = OnSurfaceDim,
    outline             = Outline,
    outlineVariant      = OutlineVariant,
    error               = LogError,
    onError             = OnPrimary,
    surfaceContainer        = SurfaceVariant,
    surfaceContainerHigh    = SurfaceElevated,
    surfaceContainerHighest = SurfaceElevated,
)

@Composable
fun AndroidIDETheme(
    darkTheme: Boolean = true, // IDE is always dark by default
    content: @Composable () -> Unit
) {
    val colorScheme = IDEDarkColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
            window.statusBarColor = Background.hashCode()
            window.navigationBarColor = Background.hashCode()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = IDETypography,
        content     = content
    )
}
