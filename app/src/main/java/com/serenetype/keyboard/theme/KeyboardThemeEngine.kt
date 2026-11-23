package com.serenetype.keyboard.theme

import android.content.Context
import android.os.Build
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.serenetype.keyboard.data.KeyboardConfig
import com.serenetype.keyboard.data.ThemeMode

@Immutable
data class KeyboardPalette(
    val trayBackground: Color,
    val topBarBackground: Color,
    val keyBackground: Color,
    val keyPressedBackground: Color,
    val funcKeyBackground: Color,
    val funcKeyPressedBackground: Color,
    val keyText: Color,
    val secondaryText: Color,
    val keyShadow: Color,
    val keyBorder: Color,
    val accent: Color,
    val accentText: Color,
    val popupBackground: Color,
    val popupText: Color,
    val divider: Color
)

object KeyboardThemeEngine {

    fun resolve(
        context: Context,
        config: KeyboardConfig,
        isSystemDark: Boolean
    ): KeyboardPalette {
        return when (config.themeMode) {
            ThemeMode.IOS_CLASSIC -> iosClassic(isSystemDark)
            ThemeMode.GRAPHITE_SCREENSHOT -> graphiteScreenshot()
            ThemeMode.MATERIAL_YOU -> materialYou(context, isSystemDark)
            ThemeMode.OLED_PURE -> oledPure()
            ThemeMode.CUSTOM -> customTheme(config, isSystemDark)
        }
    }

    private fun iosClassic(dark: Boolean): KeyboardPalette {
        return if (!dark) {
            KeyboardPalette(
                trayBackground = Color(0xFFD1D3D9),
                topBarBackground = Color(0xFFD1D3D9),
                keyBackground = Color(0xFFFFFFFF),
                keyPressedBackground = Color(0xFFE5E7EB),
                funcKeyBackground = Color(0xFFACB3BF),
                funcKeyPressedBackground = Color(0xFFFFFFFF),
                keyText = Color(0xFF000000),
                secondaryText = Color(0xFF3C3C43).copy(alpha = 0.72f),
                keyShadow = Color(0xFF898A8D),
                keyBorder = Color.Transparent,
                accent = Color(0xFF007AFF),
                accentText = Color.White,
                popupBackground = Color(0xFFFFFFFF),
                popupText = Color(0xFF000000),
                divider = Color(0xFF000000).copy(alpha = 0.06f)
            )
        } else {
            KeyboardPalette(
                trayBackground = Color(0xFF1C1C1E),
                topBarBackground = Color(0xFF1C1C1E),
                keyBackground = Color(0xFF565659),
                keyPressedBackground = Color(0xFF76767A),
                funcKeyBackground = Color(0xFF343437),
                funcKeyPressedBackground = Color(0xFF565659),
                keyText = Color(0xFFFFFFFF),
                secondaryText = Color(0xFFEBEBF5).copy(alpha = 0.65f),
                keyShadow = Color(0xFF0A0A0C),
                keyBorder = Color.Transparent,
                accent = Color(0xFF0A84FF),
                accentText = Color.White,
                popupBackground = Color(0xFF666669),
                popupText = Color(0xFFFFFFFF),
                divider = Color(0xFFFFFFFF).copy(alpha = 0.06f)
            )
        }
    }

    private fun graphiteScreenshot(): KeyboardPalette {
        return KeyboardPalette(
            trayBackground = Color(0xFF1C1C1E),
            topBarBackground = Color(0xFF18191E),
            keyBackground = Color(0xFF3A3A3A),
            keyPressedBackground = Color(0xFF525255),
            funcKeyBackground = Color(0xFF2C2C2E),
            funcKeyPressedBackground = Color(0xFF454548),
            keyText = Color(0xFFFFFFFF),
            secondaryText = Color(0xFFD1D1D6),
            keyShadow = Color(0xFF0A0A0C),
            keyBorder = Color.Transparent,
            accent = Color(0xFF0A84FF),
            accentText = Color.White,
            popupBackground = Color(0xFF48484A),
            popupText = Color(0xFFFFFFFF),
            divider = Color(0xFFFFFFFF).copy(alpha = 0.06f)
        )
    }

    private fun materialYou(context: Context, dark: Boolean): KeyboardPalette {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val scheme = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            KeyboardPalette(
                trayBackground = scheme.surfaceContainerLowest,
                topBarBackground = scheme.surfaceContainerLowest,
                keyBackground = scheme.surfaceContainerHigh,
                keyPressedBackground = scheme.surfaceContainerHighest,
                funcKeyBackground = scheme.secondaryContainer,
                funcKeyPressedBackground = scheme.primaryContainer,
                keyText = scheme.onSurface,
                secondaryText = scheme.onSurfaceVariant,
                keyShadow = Color.Black.copy(alpha = if (dark) 0.30f else 0.12f),
                keyBorder = scheme.outlineVariant.copy(alpha = 0.25f),
                accent = scheme.primary,
                accentText = scheme.onPrimary,
                popupBackground = scheme.primaryContainer,
                popupText = scheme.onPrimaryContainer,
                divider = scheme.outlineVariant.copy(alpha = 0.30f)
            )
        } else {
            iosClassic(dark)
        }
    }

    private fun oledPure(): KeyboardPalette {
        return KeyboardPalette(
            trayBackground = Color(0xFF000000),
            topBarBackground = Color(0xFF000000),
            keyBackground = Color(0xFF141416),
            keyPressedBackground = Color(0xFF27272B),
            funcKeyBackground = Color(0xFF0D0D0F),
            funcKeyPressedBackground = Color(0xFF1F1F23),
            keyText = Color(0xFFF4F4F5),
            secondaryText = Color(0xFFA1A1AA),
            keyShadow = Color.Transparent,
            keyBorder = Color(0xFF27272A),
            accent = Color(0xFF0A84FF),
            accentText = Color.White,
            popupBackground = Color(0xFF27272A),
            popupText = Color.White,
            divider = Color(0xFF27272A)
        )
    }

    private fun customTheme(config: KeyboardConfig, dark: Boolean): KeyboardPalette {
        val spec = config.customThemes.find { it.id == config.activeCustomThemeId }
            ?: config.customThemes.first()

        val tray = parseHexColor(spec.trayColorHex, if (dark) Color(0xFF18181B) else Color(0xFFE5E7EB))
        val key = parseHexColor(spec.keyColorHex, if (dark) Color(0xFF27272A) else Color.White)
        val func = parseHexColor(spec.funcKeyColorHex, if (dark) Color(0xFF3F3F46) else Color(0xFFD1D5DB))
        val text = parseHexColor(spec.textColorHex, if (dark) Color.White else Color.Black)
        val accent = parseHexColor(spec.accentColorHex, Color(0xFF0A84FF))
        val accentText = if (accent.luminance() > 0.55f) Color.Black else Color.White

        return KeyboardPalette(
            trayBackground = tray,
            topBarBackground = tray,
            keyBackground = key,
            keyPressedBackground = key.copy(alpha = 0.78f),
            funcKeyBackground = func,
            funcKeyPressedBackground = func.copy(alpha = 0.78f),
            keyText = text,
            secondaryText = text.copy(alpha = 0.65f),
            keyShadow = Color.Black.copy(alpha = 0.25f),
            keyBorder = text.copy(alpha = 0.12f),
            accent = accent,
            accentText = accentText,
            popupBackground = key,
            popupText = text,
            divider = text.copy(alpha = 0.08f)
        )
    }

    fun isValidHexColor(hex: String): Boolean {
        val clean = hex.trim().removePrefix("#")
        return (clean.length == 6 || clean.length == 8) && clean.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
    }

    fun parseHexColor(hex: String, fallback: Color): Color {
        return runCatching {
            val clean = hex.trim().removePrefix("#")
            val parsed = clean.toLong(16)
            when (clean.length) {
                6 -> Color(0xFF000000 or parsed)
                8 -> Color(parsed)
                else -> fallback
            }
        }.getOrDefault(fallback)
    }
}
