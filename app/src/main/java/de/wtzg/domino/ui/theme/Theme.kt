package de.wtzg.domino.ui.theme

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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.wtzg.domino.data.Felt
import de.wtzg.domino.data.ThemeMode
import de.wtzg.domino.data.TileFace

// Brand palette: billiard-table green, warm wood and brass.
private val Light = lightColorScheme(
    primary = Color(0xFF1E6B3A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB4F1C2),
    onPrimaryContainer = Color(0xFF00210D),
    secondary = Color(0xFF7A5634),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD7E8D4),
    onSecondaryContainer = Color(0xFF10301A),
    tertiary = Color(0xFF8A6A00),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE08A),
    onTertiaryContainer = Color(0xFF2B2000),
    background = Color(0xFFF8F6F0),
    onBackground = Color(0xFF1B1C19),
    surface = Color(0xFFF8F6F0),
    onSurface = Color(0xFF1B1C19),
    surfaceVariant = Color(0xFFE6E3D9),
    onSurfaceVariant = Color(0xFF48473F),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF3F1EA),
    surfaceContainer = Color(0xFFEDEBE3),
    surfaceContainerHigh = Color(0xFFE7E5DD),
    surfaceContainerHighest = Color(0xFFE1DFD7),
    outline = Color(0xFF79776E),
)
private val Dark = darkColorScheme(
    primary = Color(0xFF8FD7A0),
    onPrimary = Color(0xFF00391A),
    primaryContainer = Color(0xFF005228),
    onPrimaryContainer = Color(0xFFB4F1C2),
    secondary = Color(0xFFEDBD92),
    onSecondary = Color(0xFF462A0A),
    secondaryContainer = Color(0xFF34503A),
    onSecondaryContainer = Color(0xFFD7E8D4),
    tertiary = Color(0xFFEDC34A),
    onTertiary = Color(0xFF3F2E00),
    tertiaryContainer = Color(0xFF5B4500),
    onTertiaryContainer = Color(0xFFFFE08A),
    background = Color(0xFF121411),
    onBackground = Color(0xFFE3E3DC),
    surface = Color(0xFF121411),
    onSurface = Color(0xFFE3E3DC),
    surfaceVariant = Color(0xFF45483F),
    onSurfaceVariant = Color(0xFFC6C8BC),
    surfaceContainerLowest = Color(0xFF0D0F0C),
    surfaceContainerLow = Color(0xFF1A1C18),
    surfaceContainer = Color(0xFF1E201C),
    surfaceContainerHigh = Color(0xFF282B26),
    surfaceContainerHighest = Color(0xFF333630),
    outline = Color(0xFF90928A),
)

private val base = Typography()
private val AppTypography = base.copy(
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.SemiBold),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = base.labelLarge.copy(letterSpacing = 0.2.sp),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
)

/** Colours of the table felt: a lighter centre fading to a darker edge. */
@Immutable
data class FeltColors(val center: Color, val edge: Color)

fun Felt.colors(): FeltColors = when (this) {
    Felt.GREEN -> FeltColors(Color(0xFF2F8A3A), Color(0xFF1A5523))
    Felt.BLUE -> FeltColors(Color(0xFF2C6E9B), Color(0xFF173E5C))
    Felt.RED -> FeltColors(Color(0xFF8E2F3C), Color(0xFF561A24))
    Felt.CHARCOAL -> FeltColors(Color(0xFF4A4F55), Color(0xFF26292D))
}

val LocalFelt = staticCompositionLocalOf { Felt.GREEN.colors() }

/** How tiles show their numbers: dots, coloured dots or digits. */
val LocalTileFace = staticCompositionLocalOf { TileFace.DOTS }

@Composable
fun DominoTheme(
    theme: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    felt: Felt = Felt.GREEN,
    tileFace: TileFace = TileFace.DOTS,
    content: @Composable () -> Unit,
) {
    val dark = when (theme) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        dark -> Dark
        else -> Light
    }
    CompositionLocalProvider(LocalFelt provides felt.colors(), LocalTileFace provides tileFace) {
        MaterialTheme(colorScheme = colors, typography = AppTypography, shapes = AppShapes, content = content)
    }
}
