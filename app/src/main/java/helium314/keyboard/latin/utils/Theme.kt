// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.utils

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The settings UI always renders in the device's own font. Custom fonts are a keyboard
 * feature (see [helium314.keyboard.keyboard.KeyboardTypeface]) and deliberately do not
 * reach these screens, so that settings stay legible whatever the user loaded.
 */
private val SystemFont = FontFamily.Default

/**
 * The full type scale. Sizes follow Material 3; the deviations are deliberate:
 * headings are SemiBold rather than Bold, which reads as less shouty at these sizes,
 * and the large styles carry negative tracking, which Material only applies from
 * headlineLarge down.
 */
private val AppTypography = Typography(
    displayLarge = TextStyle(fontFamily = SystemFont, fontSize = 57.sp, lineHeight = 64.sp,
        fontWeight = FontWeight.Normal, letterSpacing = (-0.5).sp),
    displayMedium = TextStyle(fontFamily = SystemFont, fontSize = 45.sp, lineHeight = 52.sp,
        fontWeight = FontWeight.Normal, letterSpacing = (-0.4).sp),
    displaySmall = TextStyle(fontFamily = SystemFont, fontSize = 36.sp, lineHeight = 44.sp,
        fontWeight = FontWeight.Normal, letterSpacing = (-0.2).sp),

    headlineLarge = TextStyle(fontFamily = SystemFont, fontSize = 32.sp, lineHeight = 40.sp,
        fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp),
    headlineMedium = TextStyle(fontFamily = SystemFont, fontSize = 28.sp, lineHeight = 36.sp,
        fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
    headlineSmall = TextStyle(fontFamily = SystemFont, fontSize = 24.sp, lineHeight = 32.sp,
        fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),

    titleLarge = TextStyle(fontFamily = SystemFont, fontSize = 22.sp, lineHeight = 28.sp,
        fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontFamily = SystemFont, fontSize = 16.sp, lineHeight = 24.sp,
        fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp),
    titleSmall = TextStyle(fontFamily = SystemFont, fontSize = 14.sp, lineHeight = 19.sp,
        fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp),

    // bodyLarge is the preference name, bodyMedium the description underneath it
    bodyLarge = TextStyle(fontFamily = SystemFont, fontSize = 15.sp, lineHeight = 21.sp,
        fontWeight = FontWeight.Normal, letterSpacing = 0.15.sp),
    bodyMedium = TextStyle(fontFamily = SystemFont, fontSize = 13.sp, lineHeight = 18.sp,
        fontWeight = FontWeight.Normal, letterSpacing = 0.2.sp),
    bodySmall = TextStyle(fontFamily = SystemFont, fontSize = 12.sp, lineHeight = 16.sp,
        fontWeight = FontWeight.Normal, letterSpacing = 0.3.sp),

    labelLarge = TextStyle(fontFamily = SystemFont, fontSize = 13.sp, lineHeight = 18.sp,
        fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontFamily = SystemFont, fontSize = 12.sp, lineHeight = 16.sp,
        fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
    labelSmall = TextStyle(fontFamily = SystemFont, fontSize = 11.sp, lineHeight = 16.sp,
        fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

// Seeded from @color/accent (#1A73E8 / #5E97F6). Only used below API 31, where the
// platform has no dynamic palette to borrow; without these every role other than
// primary falls back to Compose's default purple.
private val LightColors = lightColorScheme(
    primary = Color(0xFF1A73E8), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD3E3FD), onPrimaryContainer = Color(0xFF041E49),
    secondary = Color(0xFF565F71), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDAE2F9), onSecondaryContainer = Color(0xFF131C2B),
    tertiary = Color(0xFF705575), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFAD8FD), onTertiaryContainer = Color(0xFF28132E),
    error = Color(0xFFBA1A1A), onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFAF9FD), onBackground = Color(0xFF1A1B20),
    surface = Color(0xFFFAF9FD), onSurface = Color(0xFF1A1B20),
    surfaceVariant = Color(0xFFE1E2EC), onSurfaceVariant = Color(0xFF44474E),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF4F3F7),
    surfaceContainer = Color(0xFFEFEDF1), surfaceContainerHigh = Color(0xFFE9E7EC),
    surfaceContainerHighest = Color(0xFFE3E2E6),
    outline = Color(0xFF74777F), outlineVariant = Color(0xFFC4C6D0),
    inverseSurface = Color(0xFF2F3036), inverseOnSurface = Color(0xFFF1F0F4),
    inversePrimary = Color(0xFFA8C7FA), scrim = Color(0xFF000000),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA8C7FA), onPrimary = Color(0xFF062E6F),
    primaryContainer = Color(0xFF0842A0), onPrimaryContainer = Color(0xFFD3E3FD),
    secondary = Color(0xFFBEC6DC), onSecondary = Color(0xFF283141),
    secondaryContainer = Color(0xFF3E4759), onSecondaryContainer = Color(0xFFDAE2F9),
    tertiary = Color(0xFFDDBCE0), onTertiary = Color(0xFF3F2844),
    tertiaryContainer = Color(0xFF573E5C), onTertiaryContainer = Color(0xFFFAD8FD),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF111318), onBackground = Color(0xFFE2E2E9),
    surface = Color(0xFF111318), onSurface = Color(0xFFE2E2E9),
    surfaceVariant = Color(0xFF44474E), onSurfaceVariant = Color(0xFFC4C6D0),
    surfaceContainerLowest = Color(0xFF0C0E13), surfaceContainerLow = Color(0xFF191C20),
    surfaceContainer = Color(0xFF1D2024), surfaceContainerHigh = Color(0xFF282A2F),
    surfaceContainerHighest = Color(0xFF33353A),
    outline = Color(0xFF8E9099), outlineVariant = Color(0xFF44474E),
    inverseSurface = Color(0xFFE2E2E9), inverseOnSurface = Color(0xFF2F3036),
    inversePrimary = Color(0xFF1A73E8), scrim = Color(0xFF000000),
)

@Composable
fun Theme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(LocalContext.current)
        else dynamicLightColorScheme(LocalContext.current)
    } else {
        if (dark) DarkColors else LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}

const val previewDark = true
