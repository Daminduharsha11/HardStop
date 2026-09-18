package com.aegis.hardstop.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
    surfaceVariant = Color(0xFF1E2125),
    onSurfaceVariant = Color(0xFFC4C7D0),
    surfaceContainer = Color(0xFF121417),
    surfaceContainerHigh = Color(0xFF1B1E22),
    surfaceContainerHighest = Color(0xFF26292E),
    surfaceContainerLow = Color(0xFF0A0C0E),
    surfaceContainerLowest = Color.Black,
    outline = Color(0xFF8C9199),
    outlineVariant = Color(0xFF42474E),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF560005),
    onErrorContainer = Color(0xFFFFDAD6)
)

// Google Font Family - Plus Jakarta Sans
val PlusJakartaSansFamily = FontFamily(
    Font(R.font.plus_jakarta_sans, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans, FontWeight.Bold)
)

val AppTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = PlusJakartaSansFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp
    ),
    displayMedium = TextStyle(
        fontFamily = PlusJakartaSansFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 45.sp,
        lineHeight = 52.sp
    ),
    displaySmall = TextStyle(
        fontFamily = PlusJakartaSansFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 36.sp,
        lineHeight = 44.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = PlusJakartaSansFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 40.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = PlusJakartaSansFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = PlusJakartaSansFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp
    ),
    titleLarge = TextStyle(
        fontFamily = PlusJakartaSansFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontFamily = PlusJakartaSansFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontFamily = PlusJakartaSansFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = PlusJakartaSansFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = PlusJakartaSansFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily = PlusJakartaSansFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),
    labelLarge = TextStyle(
        fontFamily = PlusJakartaSansFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = PlusJakartaSansFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = PlusJakartaSansFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
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
        typography = AppTypography
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            content()
        }
    }
}
