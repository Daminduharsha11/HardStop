package com.aegis.hardstop.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Google Material 3 Design System - Authentic Cohesive Tones

val GoogleLightPrimary = Color(0xFF00639B)
val GoogleLightOnPrimary = Color(0xFFFFFFFF)
val GoogleLightPrimaryContainer = Color(0xFFCEE5FF)
val GoogleLightOnPrimaryContainer = Color(0xFF001D33)

val GoogleLightSecondary = Color(0xFF51606F)
val GoogleLightOnSecondary = Color(0xFFFFFFFF)
val GoogleLightSecondaryContainer = Color(0xFFD5E4F6)
val GoogleLightOnSecondaryContainer = Color(0xFF0E1D2A)

val GoogleLightTertiary = Color(0xFF68587A)
val GoogleLightOnTertiary = Color(0xFFFFFFFF)
val GoogleLightTertiaryContainer = Color(0xFFEFDCFF)
val GoogleLightOnTertiaryContainer = Color(0xFF231633)

val GoogleLightBackground = Color(0xFFF8F9FA)
val GoogleLightOnBackground = Color(0xFF191C1E)
val GoogleLightSurface = Color(0xFFF8F9FA)
val GoogleLightOnSurface = Color(0xFF191C1E)
val GoogleLightSurfaceVariant = Color(0xFFDEE3EB)
val GoogleLightOnSurfaceVariant = Color(0xFF42474E)
val GoogleLightOutline = Color(0xFF72777F)
val GoogleLightOutlineVariant = Color(0xFFC2C7CF)

val GoogleDarkPrimary = Color(0xFF97CBFF)
val GoogleDarkOnPrimary = Color(0xFF003355)
val GoogleDarkPrimaryContainer = Color(0xFF004A77)
val GoogleDarkOnPrimaryContainer = Color(0xFFCEE5FF)

val GoogleDarkSecondary = Color(0xFFB9C8DA)
val GoogleDarkOnSecondary = Color(0xFF233240)
val GoogleDarkSecondaryContainer = Color(0xFF3A4857)
val GoogleDarkOnSecondaryContainer = Color(0xFFD5E4F6)

val GoogleDarkTertiary = Color(0xFFD3BFE6)
val GoogleDarkOnTertiary = Color(0xFF382A49)
val GoogleDarkTertiaryContainer = Color(0xFF4F4061)
val GoogleDarkOnTertiaryContainer = Color(0xFFEFDCFF)

val GoogleDarkBackground = Color(0xFF111416)
val GoogleDarkOnBackground = Color(0xFFE1E2E5)
val GoogleDarkSurface = Color(0xFF111416)
val GoogleDarkOnSurface = Color(0xFFE1E2E5)
val GoogleDarkSurfaceVariant = Color(0xFF42474E)
val GoogleDarkOnSurfaceVariant = Color(0xFFC2C7CF)
val GoogleDarkOutline = Color(0xFF8C9199)
val GoogleDarkOutlineVariant = Color(0xFF42474E)

val GoogleLightColorScheme = lightColorScheme(
    primary = GoogleLightPrimary,
    onPrimary = GoogleLightOnPrimary,
    primaryContainer = GoogleLightPrimaryContainer,
    onPrimaryContainer = GoogleLightOnPrimaryContainer,
    secondary = GoogleLightSecondary,
    onSecondary = GoogleLightOnSecondary,
    secondaryContainer = GoogleLightSecondaryContainer,
    onSecondaryContainer = GoogleLightOnSecondaryContainer,
    tertiary = GoogleLightTertiary,
    onTertiary = GoogleLightOnTertiary,
    tertiaryContainer = GoogleLightTertiaryContainer,
    onTertiaryContainer = GoogleLightOnTertiaryContainer,
    background = GoogleLightBackground,
    onBackground = GoogleLightOnBackground,
    surface = GoogleLightSurface,
    onSurface = GoogleLightOnSurface,
    surfaceVariant = GoogleLightSurfaceVariant,
    onSurfaceVariant = GoogleLightOnSurfaceVariant,
    outline = GoogleLightOutline,
    outlineVariant = GoogleLightOutlineVariant,
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

val GoogleDarkColorScheme = darkColorScheme(
    primary = GoogleDarkPrimary,
    onPrimary = GoogleDarkOnPrimary,
    primaryContainer = GoogleDarkPrimaryContainer,
    onPrimaryContainer = GoogleDarkOnPrimaryContainer,
    secondary = GoogleDarkSecondary,
    onSecondary = GoogleDarkOnSecondary,
    secondaryContainer = GoogleDarkSecondaryContainer,
    onSecondaryContainer = GoogleDarkOnSecondaryContainer,
    tertiary = GoogleDarkTertiary,
    onTertiary = GoogleDarkOnTertiary,
    tertiaryContainer = GoogleDarkTertiaryContainer,
    onTertiaryContainer = GoogleDarkOnTertiaryContainer,
    background = GoogleDarkBackground,
    onBackground = GoogleDarkOnBackground,
    surface = GoogleDarkSurface,
    onSurface = GoogleDarkOnSurface,
    surfaceVariant = GoogleDarkSurfaceVariant,
    onSurfaceVariant = GoogleDarkOnSurfaceVariant,
    outline = GoogleDarkOutline,
    outlineVariant = GoogleDarkOutlineVariant,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

val GoogleAmoledDarkColorScheme = darkColorScheme(
    primary = GoogleDarkPrimary,
    onPrimary = GoogleDarkOnPrimary,
    primaryContainer = Color(0xFF00385F),
    onPrimaryContainer = GoogleDarkOnPrimaryContainer,
    secondary = GoogleDarkSecondary,
    onSecondary = GoogleDarkOnSecondary,
    secondaryContainer = Color(0xFF1E2833),
    onSecondaryContainer = GoogleDarkOnSecondaryContainer,
    tertiary = GoogleDarkTertiary,
    onTertiary = GoogleDarkOnTertiary,
    tertiaryContainer = Color(0xFF2C2236),
    onTertiaryContainer = GoogleDarkOnTertiaryContainer,
    background = Color.Black,
    onBackground = Color(0xFFE1E2E5),
    surface = Color.Black,
    onSurface = Color(0xFFE1E2E5),
    surfaceVariant = Color(0xFF161616),
    onSurfaceVariant = Color(0xFFB0B3B8),
    outline = Color(0xFF33363B),
    outlineVariant = Color(0xFF222428),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF560005),
    onErrorContainer = Color(0xFFFFDAD6)
)

@Composable
fun DetoxTheme(
    themeMode: Int,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val isSystemDark = isSystemInDarkTheme()

    val isDark = when (themeMode) {
        1 -> false
        2, 3 -> true
        else -> isSystemDark
    }

    val colorScheme: ColorScheme = when {
        themeMode == 3 -> GoogleAmoledDarkColorScheme
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        isDark -> GoogleDarkColorScheme
        else -> GoogleLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
