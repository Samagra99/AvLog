package com.samtech.avlog.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.samtech.avlog.fdtl.Severity

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

@Immutable
data class AvLogColors(
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
)

private val LightAvLogColors = AvLogColors(
    WarningLight, OnWarningLight, WarningContainerLight, OnWarningContainerLight,
    SuccessLight, OnSuccessLight, SuccessContainerLight, OnSuccessContainerLight,
)

private val DarkAvLogColors = AvLogColors(
    WarningDark, OnWarningDark, WarningContainerDark, OnWarningContainerDark,
    SuccessDark, OnSuccessDark, SuccessContainerDark, OnSuccessContainerDark,
)

private val LocalAvLogColors = staticCompositionLocalOf { LightAvLogColors }

object AvLogTheme {
    val colors: AvLogColors
        @Composable @ReadOnlyComposable get() = LocalAvLogColors.current
}

/** Container/content colour pair for a severity. */
data class SeverityColors(val accent: Color, val container: Color, val onContainer: Color)

@Composable
@ReadOnlyComposable
fun severityColors(severity: Severity): SeverityColors {
    val cs = MaterialTheme.colorScheme
    val av = AvLogTheme.colors
    return when (severity) {
        Severity.OK -> SeverityColors(cs.primary, cs.surfaceVariant, cs.onSurfaceVariant)
        Severity.APPROACHING -> SeverityColors(av.warning, av.warningContainer, av.onWarningContainer)
        Severity.EXCEEDED -> SeverityColors(cs.error, cs.errorContainer, cs.onErrorContainer)
    }
}

@Composable
fun AvLogTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    CompositionLocalProvider(LocalAvLogColors provides if (darkTheme) DarkAvLogColors else LightAvLogColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}