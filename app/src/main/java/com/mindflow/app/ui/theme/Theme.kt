package com.mindflow.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = PanelSoft,
    onPrimaryContainer = TextMain,
    secondary = AccentTeal,
    onSecondary = Color.White,
    secondaryContainer = MintWash,
    onSecondaryContainer = TextMain,
    tertiary = AccentWarn,
    onTertiary = Color.White,
    tertiaryContainer = PanelWarm,
    onTertiaryContainer = TextMain,
    error = AccentDanger,
    onError = Color.White,
    background = Cream,
    onBackground = TextMain,
    surface = Panel,
    onSurface = TextMain,
    surfaceVariant = PanelSoft,
    onSurfaceVariant = TextSoft,
    outline = BorderStrong,
    outlineVariant = BorderSoft,
    surfaceTint = Accent,
)

private val DarkBackground = Color(0xFF0B0F15)
private val DarkSurface = Color(0xFF141922)
private val DarkSurfaceSoft = Color(0xFF1D2430)
private val DarkTextMain = Color(0xFFF0F4FA)
private val DarkTextSoft = Color(0xFFAAB3C2)
private val DarkBorder = Color(0xFF2A3342)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF91AAFF),
    onPrimary = Color(0xFF071225),
    primaryContainer = Color(0xFF1A2746),
    onPrimaryContainer = DarkTextMain,
    secondary = Color(0xFF5EDFC7),
    onSecondary = Color(0xFF041B18),
    secondaryContainer = Color(0xFF0D332F),
    onSecondaryContainer = DarkTextMain,
    tertiary = Color(0xFFFFD37A),
    onTertiary = Color(0xFF241704),
    tertiaryContainer = Color(0xFF3C2A0C),
    onTertiaryContainer = DarkTextMain,
    error = Color(0xFFFF9C8A),
    onError = Color(0xFF2B0704),
    background = DarkBackground,
    onBackground = DarkTextMain,
    surface = DarkSurface,
    onSurface = DarkTextMain,
    surfaceVariant = DarkSurfaceSoft,
    onSurfaceVariant = DarkTextSoft,
    outline = DarkBorder,
    outlineVariant = DarkBorder.copy(alpha = 0.78f),
    surfaceTint = Accent,
)

@Composable
fun MindFlowTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = MindFlowTypography,
        shapes = Shapes(
            small = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
            medium = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
            large = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
        ),
        content = content,
    )
}
