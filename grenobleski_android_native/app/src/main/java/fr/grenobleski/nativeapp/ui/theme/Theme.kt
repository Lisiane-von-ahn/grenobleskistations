package fr.grenobleski.nativeapp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = BrandPrimary,
    secondary = BrandAccent,
    tertiary = BrandAccent,
    background = ScreenBackground,
    surface = CardBackground,
)

private val DarkColors = darkColorScheme(
    primary = BrandPrimary,
    secondary = BrandAccent,
    tertiary = BrandAccent,
    background = BrandPrimaryDark,
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
        content = content,
    )
}
