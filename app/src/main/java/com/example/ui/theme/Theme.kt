package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = AppThemeManager.isDark,
  // Dynamic color is disabled by default to use our highly polished custom palette
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val customDarkColorScheme = darkColorScheme(
    primary = TealPrimaryDark,
    onPrimary = Color(0xFF0F172A),
    primaryContainer = Color(0xFF132D37), // Sophisticated deep teal slate
    onPrimaryContainer = Color(0xFF00E5FF), // Brighter teal/cyan
    secondary = TealSecondaryDark,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF1E293B),
    onSecondaryContainer = Color(0xFFCBD5E1),
    tertiary = TealTertiaryDark,
    background = TealBackgroundDark,
    surface = TealSurfaceDark,
    onBackground = Color(0xFFF1F5F9),
    onSurface = Color(0xFFF1F5F9)
  )

  val customLightColorScheme = lightColorScheme(
    primary = Color(0xFF0D9488), // High contrast medical teal
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCCFBF1), // Soft elegant teal-slate container
    onPrimaryContainer = Color(0xFF115E59), // Deep high-contrast teal on container
    secondary = Color(0xFFF1F5F9),
    onSecondary = Color(0xFF1E293B),
    secondaryContainer = Color(0xFFE2E8F0),
    onSecondaryContainer = Color(0xFF334155),
    tertiary = Color(0xFF1E293B),
    background = Color(0xFFF8FAFC),
    surface = Color(0xFFFFFFFF),
    onBackground = Color(0xFF0F172A),
    onSurface = Color(0xFF0F172A)
  )

  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> customDarkColorScheme
      else -> customLightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
