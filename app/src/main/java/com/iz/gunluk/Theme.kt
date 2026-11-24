package com.iz.gunluk

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Immutable
data class IzColors(
    val dark: Boolean,
    val bg: Color, val card: Color, val card2: Color, val line: Color,
    val text: Color, val sub: Color, val faint: Color,
    val accent: Color, val accentSoft: Color, val onAccent: Color,
    val highlight: Color, val danger: Color,
)

object Palette {
    val accents = listOf(
        "Elektrik mavisi" to Color(0xFF2F7BFF),
        "Lavanta" to Color(0xFF8B6CFF),
        "Turkuaz" to Color(0xFF13C2C2),
        "Zümrüt" to Color(0xFF2BB673),
        "Kehribar" to Color(0xFFFFA940),
        "Gül" to Color(0xFFFF5C8A),
    )

    fun colors(theme: String, sysDark: Boolean, accentIdx: Int): IzColors {
        val a = accents.getOrElse(accentIdx) { accents[0] }.second
        val t = if (theme == "system") (if (sysDark) "dark" else "light") else theme
        return when (t) {
            "light" -> IzColors(false, Color(0xFFF4F6FB), Color.White, Color(0xFFEDF1F8), Color(0x14101828),
                Color(0xFF101828), Color(0xFF5B6478), Color(0xFF98A2B3), a, a.copy(alpha = 0.12f), Color.White,
                Color(0xFFFFE58F), Color(0xFFE5484D))
            "amoled" -> IzColors(true, Color.Black, Color(0xFF0C0C0E), Color(0xFF151518), Color(0x1AFFFFFF),
                Color(0xFFF2F4F8), Color(0xFF9AA3B5), Color(0xFF5D6578), a, a.copy(alpha = 0.16f), Color.White,
                Color(0x66FFC53D), Color(0xFFFF6369))
            else -> IzColors(true, Color(0xFF0A0E17), Color(0xFF121A2B), Color(0xFF18223A), Color(0x1AFFFFFF),
                Color(0xFFF2F4F8), Color(0xFF9AA6BF), Color(0xFF5F6B85), a, a.copy(alpha = 0.16f), Color.White,
                Color(0x66FFC53D), Color(0xFFFF6369))
        }
    }

    fun family(f: String): FontFamily = when (f) {
        "serif" -> FontFamily.Serif
        "mono" -> FontFamily.Monospace
        "round" -> FontFamily.Cursive
        else -> FontFamily.SansSerif
    }
}

val LocalIz = staticCompositionLocalOf { Palette.colors("dark", true, 0) }

object Iz {
    val c: IzColors @Composable get() = LocalIz.current
}

@Composable
fun IzTheme(content: @Composable () -> Unit) {
    val c = Palette.colors(Prefs.theme, isSystemInDarkTheme(), Prefs.accent)
    val scheme = if (c.dark) darkColorScheme(
        primary = c.accent, onPrimary = c.onAccent, background = c.bg, surface = c.card, onSurface = c.text,
        onBackground = c.text, surfaceVariant = c.card2, onSurfaceVariant = c.sub, outline = c.line, secondary = c.accent,
        surfaceContainer = c.card, surfaceContainerHigh = c.card2, surfaceContainerHighest = c.card2, surfaceContainerLow = c.card,
        error = c.danger,
    ) else lightColorScheme(
        primary = c.accent, onPrimary = c.onAccent, background = c.bg, surface = c.card, onSurface = c.text,
        onBackground = c.text, surfaceVariant = c.card2, onSurfaceVariant = c.sub, outline = c.line, secondary = c.accent,
        surfaceContainer = c.card, surfaceContainerHigh = c.card2, surfaceContainerHighest = c.card2, surfaceContainerLow = c.card,
        error = c.danger,
    )
    val ff = FontFamily.SansSerif
    val typo = Typography(
        displaySmall = TextStyle(fontFamily = ff, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.5).sp),
        headlineMedium = TextStyle(fontFamily = ff, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.3).sp),
        headlineSmall = TextStyle(fontFamily = ff, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
        titleLarge = TextStyle(fontFamily = ff, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
        titleMedium = TextStyle(fontFamily = ff, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
        bodyLarge = TextStyle(fontFamily = ff, fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = TextStyle(fontFamily = ff, fontSize = 14.sp, lineHeight = 20.sp),
        labelLarge = TextStyle(fontFamily = ff, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
        labelMedium = TextStyle(fontFamily = ff, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.6.sp),
        labelSmall = TextStyle(fontFamily = ff, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.2.sp),
    )
    CompositionLocalProvider(LocalIz provides c) {
        MaterialTheme(colorScheme = scheme, typography = typo, content = content)
    }
}

/** Yazı alanı stili: kullanıcının tipografi ayarlarına göre. */
@Composable
fun writingStyle(): TextStyle {
    val size = 17f * Prefs.fontScale
    return TextStyle(fontFamily = Palette.family(Prefs.font), fontSize = size.sp, lineHeight = (size * Prefs.lineHeight).sp, color = Iz.c.text)
}
