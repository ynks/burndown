package com.ynks.burndown.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// fallback for pre android 12 and tests
internal val BaselineLight = lightColorScheme(
    primary = Color(0xFF415F91), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6E3FF), onPrimaryContainer = Color(0xFF001B3E),
    secondary = Color(0xFF565F71), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDAE2F9), onSecondaryContainer = Color(0xFF131C2B),
    tertiary = Color(0xFF705575), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFAD8FD), onTertiaryContainer = Color(0xFF28132E),
    error = Color(0xFFBA1A1A), onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF9F9FF), onBackground = Color(0xFF191C20),
    surface = Color(0xFFF9F9FF), onSurface = Color(0xFF191C20),
    surfaceVariant = Color(0xFFE0E2EC), onSurfaceVariant = Color(0xFF44474E),
    outline = Color(0xFF74777F), outlineVariant = Color(0xFFC4C6D0),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF3F3FA),
    surfaceContainer = Color(0xFFEDEDF4), surfaceContainerHigh = Color(0xFFE7E8EE),
    surfaceContainerHighest = Color(0xFFE2E2E9),
)
internal val BaselineDark = darkColorScheme(
    primary = Color(0xFFAAC7FF), onPrimary = Color(0xFF0A305F),
    primaryContainer = Color(0xFF284777), onPrimaryContainer = Color(0xFFD6E3FF),
    secondary = Color(0xFFBEC6DC), onSecondary = Color(0xFF283141),
    secondaryContainer = Color(0xFF3E4759), onSecondaryContainer = Color(0xFFDAE2F9),
    tertiary = Color(0xFFDDBCE0), onTertiary = Color(0xFF3F2844),
    tertiaryContainer = Color(0xFF573E5C), onTertiaryContainer = Color(0xFFFAD8FD),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF111318), onBackground = Color(0xFFE2E2E9),
    surface = Color(0xFF111318), onSurface = Color(0xFFE2E2E9),
    surfaceVariant = Color(0xFF44474E), onSurfaceVariant = Color(0xFFC4C6D0),
    outline = Color(0xFF8E9099), outlineVariant = Color(0xFF44474E),
    surfaceContainerLowest = Color(0xFF0C0E13), surfaceContainerLow = Color(0xFF191C20),
    surfaceContainer = Color(0xFF1D2024), surfaceContainerHigh = Color(0xFF282A2F),
    surfaceContainerHighest = Color(0xFF33353A),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BurndownTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> BaselineDark
        else -> BaselineLight
    }
    MaterialExpressiveTheme(colorScheme = colors, motionScheme = MotionScheme.expressive(), content = content)
}
