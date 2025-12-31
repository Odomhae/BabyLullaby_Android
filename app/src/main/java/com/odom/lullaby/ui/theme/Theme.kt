package com.odom.lullaby.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Purple80, // Soft lavender
    secondary = PurpleGrey80, // Muted purple-grey
    tertiary = Pink80, // Soft pink
    background = Color(0xFF0F0F1A), // Very dark blue-grey background
    surface = Color(0xFF1A1A26), // Dark surface with slight blue tint
    surfaceVariant = SurfaceVariant80, // Lighter variant for cards
    onPrimary = Color(0xFF1A1A26), // Dark text on light primary
    onSecondary = Color(0xFF1A1A26), // Dark text on light secondary
    onTertiary = Color(0xFF1A1A26), // Dark text on light tertiary
    onBackground = Color(0xFFE8E8F0), // Soft white for text on dark background
    onSurface = Color(0xFFE8E8F0), // Soft white for text on dark surface
    onSurfaceVariant = Color(0xFFC4C4D0), // Muted text on surface variant
    error = Color(0xFFFF6B6B), // Soft red for errors
    onError = Color(0xFF1A1A26), // Dark text on error
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40, // Deeper lavender
    secondary = PurpleGrey40, // Muted purple-grey
    tertiary = Pink40, // Soft dusty pink
    background = Color(0xFFFFFBFE), // Warm white background
    surface = Color(0xFFF8F8FA), // Very light grey surface
    surfaceVariant = SurfaceVariant40, // Light blue-grey variant
    onPrimary = Color.White, // White text on dark primary
    onSecondary = Color.White, // White text on dark secondary
    onTertiary = Color.White, // White text on dark tertiary
    onBackground = Color(0xFF1C1B1F), // Dark text on light background
    onSurface = Color(0xFF1C1B1F), // Dark text on light surface
    onSurfaceVariant = Color(0xFF49454F), // Muted dark text on surface variant
    error = Color(0xFFBA1A1A), // Standard red for errors
    onError = Color.White, // White text on error
)

@Composable
fun MyApplicationTheme(
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

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}