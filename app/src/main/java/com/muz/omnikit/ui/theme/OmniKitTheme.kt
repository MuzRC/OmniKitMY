package com.muz.omnikit.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.muz.omnikit.core.model.ThemeMode

private val OmniDarkColors = darkColorScheme(
    primary = Color(0xFF38BDF8),
    onPrimary = Color(0xFF042236),
    primaryContainer = Color(0xFF0F3559),
    onPrimaryContainer = Color(0xFFC9ECFF),
    secondary = Color(0xFF3B82F6),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF172D52),
    onSecondaryContainer = Color(0xFFD6E4FF),
    tertiary = Color(0xFFFBBF24),
    onTertiary = Color(0xFF2E1F00),
    background = Color(0xFF0A1222),
    onBackground = Color(0xFFF1F5F9),
    surface = Color(0xFF101B30),
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = Color(0xFF162440),
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF283A5C),
    outlineVariant = Color(0xFF1E2D4A),
    error = Color(0xFFF87171),
    onError = Color(0xFF3B0909)
)

private val OmniLightColors = lightColorScheme(
    primary = Color(0xFF0284C7),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDDF3FF),
    onPrimaryContainer = Color(0xFF06324F),
    secondary = Color(0xFF2563EB),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDBEAFE),
    onSecondaryContainer = Color(0xFF102A66),
    tertiary = Color(0xFFD97706),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF3F6FB),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE7EEF8),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFFE2E8F0),
    error = Color(0xFFDC2626),
    onError = Color(0xFFFFFFFF)
)

@Composable
fun isDarkThemeResolved(themeMode: ThemeMode): Boolean {
    return when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
}

@Composable
fun OmniKitTheme(
    themeMode: ThemeMode,
    content: @Composable () -> Unit
) {
    val dark = isDarkThemeResolved(themeMode)
    MaterialTheme(
        colorScheme = if (dark) OmniDarkColors else OmniLightColors,
        typography = Typography(),
        content = content
    )
}
