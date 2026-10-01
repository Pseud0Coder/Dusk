package com.dusk.app

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---------- Fonts (bundled, OFL licensed; see /licenses) ----------

/** Body text. Wide letter spacing designed for easier reading. */
val Lexend = FontFamily(
    Font(R.font.lexend_regular, FontWeight.Normal),
    Font(R.font.lexend_medium, FontWeight.Medium),
    Font(R.font.lexend_semibold, FontWeight.SemiBold),
)

/** Big numbers and headings. A soft serif. */
val Fraunces = FontFamily(
    Font(R.font.fraunces_light, FontWeight.Light),
    Font(R.font.fraunces_regular, FontWeight.Normal),
    Font(R.font.fraunces_medium, FontWeight.Medium),
)

// ---------- Colors ----------

private val Plum = Color(0xFF6B4A85)
private val Ink = Color(0xFF3B2A4A)
private val Coral = Color(0xFFF28B6B)
private val Mint = Color(0xFF5FA58A)

private val DuskLight = lightColorScheme(
    primary = Plum, onPrimary = Color.White,
    primaryContainer = Color(0xFFEADDF7), onPrimaryContainer = Color(0xFF2E1A40),
    secondary = Coral, onSecondary = Color(0xFF3B1A10),
    secondaryContainer = Color(0xFFFFE3D6), onSecondaryContainer = Color(0xFF5A2410),
    tertiary = Mint, onTertiary = Color.White,
    background = Color(0xFFFBEFF0), onBackground = Ink,
    surface = Color(0xFFFFF8F6), onSurface = Ink,
    surfaceVariant = Color(0xFFF5E6EE), onSurfaceVariant = Color(0xFF6B5577),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFF6F4),
    surfaceContainer = Color(0xFFFCEFF0),
    surfaceContainerHigh = Color(0xFFF8E9EE),
    surfaceContainerHighest = Color(0xFFF3E3EA),
    outline = Color(0xFFCDB5CB), outlineVariant = Color(0xFFE6D3E2),
)

private val DuskDark = darkColorScheme(
    primary = Color(0xFFD9B8F0), onPrimary = Color(0xFF3A2150),
    primaryContainer = Color(0xFF4A3463), onPrimaryContainer = Color(0xFFF0E1FB),
    secondary = Color(0xFFF6A385), onSecondary = Color(0xFF4A1E0E),
    secondaryContainer = Color(0xFF5C3426), onSecondaryContainer = Color(0xFFFFDCCD),
    tertiary = Color(0xFF8FD1B5), onTertiary = Color(0xFF0E3B2A),
    background = Color(0xFF1E1A2B), onBackground = Color(0xFFF3E9F5),
    surface = Color(0xFF262033), onSurface = Color(0xFFF3E9F5),
    surfaceVariant = Color(0xFF342B44), onSurfaceVariant = Color(0xFFC9B8D3),
    surfaceContainerLowest = Color(0xFF17131F),
    surfaceContainerLow = Color(0xFF221C2E),
    surfaceContainer = Color(0xFF282135),
    surfaceContainerHigh = Color(0xFF30283F),
    surfaceContainerHighest = Color(0xFF3A3049),
    outline = Color(0xFF6A5A80), outlineVariant = Color(0xFF453A57),
)

private val LightGradient = listOf(Color(0xFFFFD9C4), Color(0xFFF6C9D6), Color(0xFFDCCFF2))
private val DarkGradient = listOf(Color(0xFF3A2638), Color(0xFF2B2140), Color(0xFF1B1830))

/** Sunset colors for the voice circle. */
val OrbColors = listOf(Color(0xFFFFE6D6), Color(0xFFF6A9B8), Color(0xFFB79AE0))

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

/** Applies the Dusk theme and paints the sunset gradient behind everything. */
@Composable
fun DuskTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) DuskDark else DuskLight,
        typography = DuskTypography,
        shapes = DuskShapes,
    ) {
        Box(
            Modifier.fillMaxSize().background(
                Brush.linearGradient(
                    if (dark) DarkGradient else LightGradient,
                    start = Offset.Zero,
                    end = Offset.Infinite
                )
            )
        ) { content() }
    }
}

// ---------- Building blocks ----------

/** Translucent card color that lets the gradient glow through. */
@Composable
fun cardColor(): Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.74f)

@Composable
fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = cardColor(), shape = RoundedCornerShape(24.dp), modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), content = content)
    }
}

private data class KindColors(val bg: Color, val fg: Color)

private fun kindColors(kind: String, dark: Boolean): KindColors = if (!dark) when (kind) {
    "body" -> KindColors(Color(0xFFFFE3C8), Color(0xFF7A3E10))
    "mind" -> KindColors(Color(0xFFE3DAF7), Color(0xFF45307A))
    "food" -> KindColors(Color(0xFFDCEFE3), Color(0xFF1F5A3C))
    "sleep" -> KindColors(Color(0xFFDCE3F7), Color(0xFF2C3A7A))
    "social" -> KindColors(Color(0xFFF9D9E4), Color(0xFF7A2546))
    else -> KindColors(Color(0xFFEFE7EE), Color(0xFF5A4A5E))
} else when (kind) {
    "body" -> KindColors(Color(0xFF5A3420), Color(0xFFFFD9C0))
    "mind" -> KindColors(Color(0xFF3E3066), Color(0xFFE3DAF7))
    "food" -> KindColors(Color(0xFF23463A), Color(0xFFCDEBDC))
    "sleep" -> KindColors(Color(0xFF2A3460), Color(0xFFD6DEF7))
    "social" -> KindColors(Color(0xFF5A2A3D), Color(0xFFF9D3E0))
    else -> KindColors(Color(0xFF3D3346), Color(0xFFE6DCEA))
}

/** Small pastel tag showing what kind of routine item this is. */
@Composable
fun KindChip(kind: String) {
    if (kind.isBlank()) return
    val k = kindColors(kind, isSystemInDarkTheme())
    Text(
        kind,
        style = MaterialTheme.typography.labelSmall,
        color = k.fg,
        modifier = Modifier
            .background(k.bg, RoundedCornerShape(50))
            .padding(horizontal = 9.dp, vertical = 3.dp)
    )
}
