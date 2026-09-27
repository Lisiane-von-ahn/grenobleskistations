package fr.grenobleski.nativeapp.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = BrandPrimary,
    onPrimary = CardBackground,
    primaryContainer = GlacierBlue,
    onPrimaryContainer = BrandPrimaryDark,
    secondary = BrandAccent,
    onSecondary = CardBackground,
    secondaryContainer = IceBlue,
    onSecondaryContainer = BrandPrimaryDark,
    tertiary = AlpineOrange,
    onTertiary = CardBackground,
    tertiaryContainer = Color(0xFFFFE7C2),
    onTertiaryContainer = Color(0xFF5D3500),
    background = ScreenBackground,
    onBackground = SlateText,
    surface = CardBackground,
    onSurface = SlateText,
    surfaceVariant = IceBlue,
    onSurfaceVariant = MutedText,
    outline = AlpineOutline,
    outlineVariant = Color(0xFFD7E3ED),
    error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = BrandPrimary,
    secondary = BrandAccent,
    tertiary = AlpineOrange,
    background = BrandPrimaryDark,
)

private val AlpineShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun GrenobleSkiNativeTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors

    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography,
        shapes = AlpineShapes,
        content = content,
    )
}
