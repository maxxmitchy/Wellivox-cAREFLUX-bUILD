package com.example.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf

object AppThemeManager {
    var isDark by androidx.compose.runtime.mutableStateOf(true)

    val primary: Color
        get() = if (isDark) Color(0xFF00E5FF) else Color(0xFF0D9488) // High contrast medical teal/cyan for light mode

    val secondary: Color
        get() = if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9) // Slate-100 card style background

    val tertiary: Color
        get() = if (isDark) Color(0xFFE2E8F0) else Color(0xFF1E293B) // Dark slate text for high visibility

    val background: Color
        get() = if (isDark) Color(0xFF0B0F19) else Color(0xFFF8FAFC) // Slate-50 soft body background

    val surface: Color
        get() = if (isDark) Color(0xFF131B2A) else Color(0xFFFFFFFF) // Pure white card containers

    val warningRed: Color
        get() = if (isDark) Color(0xFFFF4D4D) else Color(0xFFDC2626)

    val warningRedTitle: Color
        get() = if (isDark) Color(0xFFFFB3B3) else Color(0xFF991B1B)

    val warningRedContainer: Color
        get() = if (isDark) Color(0x33FF0000) else Color(0xFFFEE2E2)

    val warningRedContainerSoft: Color
        get() = if (isDark) Color(0x11FF0000) else Color(0xFFFEF2F2)

    val okGreen: Color
        get() = if (isDark) Color(0xFF00FA9A) else Color(0xFF16A34A)

    val okGreenContainer: Color
        get() = if (isDark) Color(0x3300FA9A) else Color(0xFFDCFCE7)

    val okGreenText: Color
        get() = if (isDark) Color(0xFF80FFC9) else Color(0xFF15803D)

    val pendingOrange: Color
        get() = if (isDark) Color(0xFFFFB74D) else Color(0xFFD97706)

    val pendingOrangeContainer: Color
        get() = if (isDark) Color(0x33FF9800) else Color(0xFFFEF3C7)

    val pendingOrangeBorder: Color
        get() = if (isDark) Color(0xFFFFB74D) else Color(0xFFF59E0B)

    val slateBorderLight: Color
        get() = if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0)

    val unfocusedTextFieldBorder: Color
        get() = if (isDark) Color(0xFF8E9DAE) else Color(0xFF4A5568)

    val slateBackgroundLight: Color
        get() = if (isDark) Color(0xFF0F172A) else Color(0xFFF1F5F9)

    val slateTextMedium: Color
        get() = if (isDark) Color(0xFF94A3B8) else Color(0xFF475569)
}

val TealPrimary: Color get() = AppThemeManager.primary
val TealSecondary: Color get() = AppThemeManager.secondary
val TealTertiary: Color get() = AppThemeManager.tertiary
val TealBackground: Color get() = AppThemeManager.background
val TealSurface: Color get() = AppThemeManager.surface

val TealPrimaryDark = Color(0xFF00B8D4)
val TealSecondaryDark = Color(0xFF334155)
val TealTertiaryDark = Color(0xFFF1F5F9)
val TealBackgroundDark = Color(0xFF0B0F19)
val TealSurfaceDark = Color(0xFF131B2A)

val WarningRed: Color get() = AppThemeManager.warningRed
val WarningRedTitle: Color get() = AppThemeManager.warningRedTitle
val WarningRedContainer: Color get() = AppThemeManager.warningRedContainer
val WarningRedContainerSoft: Color get() = AppThemeManager.warningRedContainerSoft

val OKGreen: Color get() = AppThemeManager.okGreen
val OKGreenContainer: Color get() = AppThemeManager.okGreenContainer
val OKGreenText: Color get() = AppThemeManager.okGreenText

val PendingOrange: Color get() = AppThemeManager.pendingOrange
val PendingOrangeContainer: Color get() = AppThemeManager.pendingOrangeContainer
val PendingOrangeBorder: Color get() = AppThemeManager.pendingOrangeBorder

val SlateBorderLight: Color get() = AppThemeManager.slateBorderLight
val UnfocusedTextFieldBorder: Color get() = AppThemeManager.unfocusedTextFieldBorder
val SlateBackgroundLight: Color get() = AppThemeManager.slateBackgroundLight
val SlateTextMedium: Color get() = AppThemeManager.slateTextMedium
