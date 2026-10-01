package com.dusk.app

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---------- Fonts (bundled, OFL licensed; see /licenses) ----------

val Lexend = FontFamily(
    Font(R.font.lexend_regular, FontWeight.Normal),
    Font(R.font.lexend_medium, FontWeight.Medium),
    Font(R.font.lexend_semibold, FontWeight.SemiBold),
)

val Fraunces = FontFamily(
    Font(R.font.fraunces_light, FontWeight.Light),
    Font(R.font.fraunces_regular, FontWeight.Normal),
    Font(R.font.fraunces_medium, FontWeight.Medium),
)

// ---------- Island sunset palette ----------

private val SeaInk = Color(0xFF1F3A4D)

private val GoldenHour = lightColorScheme(
    primary = Color(0xFF2F7F86), onPrimary = Color.White,
    primaryContainer = Color(0xFFD6EEEA), onPrimaryContainer = Color(0xFF103F44),
    secondary = Color(0xFFF0765A), onSecondary = Color(0xFF3A140A),
    secondaryContainer = Color(0xFFFFE6CF), onSecondaryContainer = Color(0xFF6A3510),
    tertiary = Color(0xFF4E9A78), onTertiary = Color.White,
    background = Color(0xFFF6E7D3), onBackground = SeaInk,
    surface = Color(0xFFFFE6D6), onSurface = SeaInk,
    surfaceVariant = Color(0xFFFFD9C2), onSurfaceVariant = Color(0xFF4A6272),
    surfaceContainerLowest = Color(0xFFFFEBDD),
    surfaceContainerLow = Color(0xFFFFE6D6),
    surfaceContainer = Color(0xFFFFE0CC),
    surfaceContainerHigh = Color(0xFFFFDAC4),
    surfaceContainerHighest = Color(0xFFFFD3BA),
    outline = Color(0xFFB59C80), outlineVariant = Color(0xFFEAD8C2),
)

private val Twilight = darkColorScheme(
    primary = Color(0xFF8ED1C9), onPrimary = Color(0xFF0E3337),
    primaryContainer = Color(0xFF1E4A50), onPrimaryContainer = Color(0xFFCDEDE8),
    secondary = Color(0xFFFF9F80), onSecondary = Color(0xFF3A140A),
    secondaryContainer = Color(0xFF5A2E1E), onSecondaryContainer = Color(0xFFFFDCCB),
    tertiary = Color(0xFF9ED6B8), onTertiary = Color(0xFF0F3A26),
    background = Color(0xFF14223A), onBackground = Color(0xFFF4EBE1),
    surface = Color(0xFF1C2C46), onSurface = Color(0xFFF4EBE1),
    surfaceVariant = Color(0xFF263A57), onSurfaceVariant = Color(0xFFC3CCD6),
    surfaceContainerLowest = Color(0xFF0F1A2D),
    surfaceContainerLow = Color(0xFF18263E),
    surfaceContainer = Color(0xFF1C2C46),
    surfaceContainerHigh = Color(0xFF233552),
    surfaceContainerHighest = Color(0xFF2B3F5F),
    outline = Color(0xFF7088A8), outlineVariant = Color(0xFF34486A),
)

private val GoldenBackground = listOf(Color(0xFFFFD6BC), Color(0xFFFBCFC2), Color(0xFFF8DCC4))
private val TwilightBackground = listOf(Color(0xFF14223A), Color(0xFF1A2A44), Color(0xFF2A2F3E))

/** The voice circle: a setting sun. */
val OrbColors = listOf(Color(0xFFFFF1C2), Color(0xFFFFD27A), Color(0xFFF0765A))

// ---------- Type and shape ----------

private val base = Typography()

private val DuskTypography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = Fraunces),
    displayMedium = base.displayMedium.copy(fontFamily = Fraunces),
    displaySmall = base.displaySmall.copy(fontFamily = Fraunces),
    headlineLarge = base.headlineLarge.copy(fontFamily = Fraunces),
    headlineMedium = base.headlineMedium.copy(fontFamily = Fraunces),
    headlineSmall = base.headlineSmall.copy(fontFamily = Fraunces),
    titleLarge = base.titleLarge.copy(fontFamily = Lexend, fontWeight = FontWeight.Medium),
    titleMedium = base.titleMedium.copy(fontFamily = Lexend, fontWeight = FontWeight.Medium),
    titleSmall = base.titleSmall.copy(fontFamily = Lexend, fontWeight = FontWeight.Medium),
    bodyLarge = base.bodyLarge.copy(fontFamily = Lexend, lineHeight = 26.sp),
    bodyMedium = base.bodyMedium.copy(fontFamily = Lexend, lineHeight = 22.sp),
    bodySmall = base.bodySmall.copy(fontFamily = Lexend, lineHeight = 18.sp),
    labelLarge = base.labelLarge.copy(fontFamily = Lexend),
    labelMedium = base.labelMedium.copy(fontFamily = Lexend),
    labelSmall = base.labelSmall.copy(fontFamily = Lexend),
)

private val DuskShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/**
 * Applies the theme, paints the sky behind everything, and sets the default text
 * color for every screen so nothing ever falls back to black.
 */
@Composable
fun DuskTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme = if (dark) Twilight else GoldenHour
    MaterialTheme(colorScheme = scheme, typography = DuskTypography, shapes = DuskShapes) {
        CompositionLocalProvider(LocalContentColor provides scheme.onBackground) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(if (dark) TwilightBackground else GoldenBackground)
                )
            ) { content() }
        }
    }
}

// ---------- Building blocks ----------

/** A warm coral (or, at twilight, sea) tint. Never a white box on the colorful sky. */
@Composable
fun cardColor(): Color =
    if (isSystemInDarkTheme()) Color(0xFF4F9AA3).copy(alpha = 0.20f) else Color(0xFFFF9F80).copy(alpha = 0.24f)

/** Card with the right text color built in. */
@Composable
fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = cardColor(),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(24.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp), content = content)
    }
}

data class Duo(val bg: Color, val fg: Color)

private fun d(bg: Long, fg: Long) = Duo(Color(bg), Color(fg))

/** Fridge-magnet tones. Each pairs a fill with a text color that reads on it. */
enum class Tone { Coral, Sea, Sand, Gold, Mint }

@Composable
fun tone(t: Tone): Duo = if (!isSystemInDarkTheme()) when (t) {
    Tone.Coral -> d(0xFFFF9F80, 0xFF3A140A)
    Tone.Sea -> d(0xFF2F7F86, 0xFFFFFFFF)
    Tone.Sand -> d(0xFFCFE3EE, 0xFF1F3A4D)
    Tone.Gold -> d(0xFFFFD27A, 0xFF4A3200)
    Tone.Mint -> d(0xFFD6EEEA, 0xFF1F4E47)
} else when (t) {
    Tone.Coral -> d(0xFF8A4232, 0xFFFFE3D8)
    Tone.Sea -> d(0xFF235A62, 0xFFD9F2EF)
    Tone.Sand -> d(0xFF22344F, 0xFFF4EBE1)
    Tone.Gold -> d(0xFF6E5520, 0xFFFFF0C8)
    Tone.Mint -> d(0xFF22493F, 0xFFD6F0E8)
}

/** Pastel pair for a routine item's kind. */
@Composable
fun kindDuo(kind: String): Duo = if (!isSystemInDarkTheme()) when (kind) {
    "body" -> d(0xFFFFE6CF, 0xFF6A3510)
    "mind" -> d(0xFFD9E8F5, 0xFF24486B)
    "food" -> d(0xFFD6EEEA, 0xFF1F4E47)
    "sleep" -> d(0xFFDDE1F2, 0xFF2A3566)
    "social" -> d(0xFFFBDCD5, 0xFF7A2E22)
    else -> d(0xFFF3E7DA, 0xFF4A4038)
} else when (kind) {
    "body" -> d(0xFF5A3420, 0xFFFFD9C0)
    "mind" -> d(0xFF23405E, 0xFFD6E6F5)
    "food" -> d(0xFF1F4A44, 0xFFCDEDE6)
    "sleep" -> d(0xFF2A3460, 0xFFD6DEF7)
    "social" -> d(0xFF5A2A24, 0xFFF9D3CB)
    else -> d(0xFF33404F, 0xFFE6E0D8)
}

@Composable
fun KindChip(kind: String) {
    if (kind.isBlank()) return
    val k = kindDuo(kind)
    Row(
        Modifier.background(k.bg, RoundedCornerShape(50)).padding(horizontal = 9.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TIcon(kindIcon(kind), size = 14.dp, tint = k.fg)
        Spacer(Modifier.width(4.dp))
        Text(kind, style = MaterialTheme.typography.labelSmall, color = k.fg)
    }
}

/** Colors for the island scene. */
data class SkyColors(
    val sky: List<Color>, val sun: Color, val sea: Color, val wave: Color,
    val sand: Color, val ink: Color, val palm: Color, val trunk: Color, val light: Color
)

@Composable
fun skyColors(): SkyColors = if (isSystemInDarkTheme()) SkyColors(
    sky = listOf(Color(0xFF14223A), Color(0xFF2A3550), Color(0xFFC9735A)),
    sun = Color(0xFFFFC48A), sea = Color(0xFF17344F), wave = Color(0xFF3E6A85),
    sand = Color(0xFF8C6E57), ink = Color(0xFFF4EBE1), palm = Color(0xFF5E9C7E),
    trunk = Color(0xFF6B5A3E), light = Color(0xFFFFF6EC)
) else SkyColors(
    sky = listOf(Color(0xFFFFD2B0), Color(0xFFF6B4AE), Color(0xFFFF9F80)),
    sun = Color(0xFFFFE29A), sea = Color(0xFF4F9AA3), wave = Color(0xFF9FD0D4),
    sand = Color(0xFFE9C9A0), ink = Color(0xFF1F3A4D), palm = Color(0xFF4E9A78),
    trunk = Color(0xFF6B5A3E), light = Color(0xFFFFF6EC)
)
