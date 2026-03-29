package com.mindflow.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = PanelSoft,
    onPrimaryContainer = TextMain,
    secondary = AccentBlue,
    onSecondary = Color.White,
    secondaryContainer = PanelBlue,
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

@Composable
fun MindFlowTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = MindFlowTypography,
        shapes = Shapes(
            small = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
            medium = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
            large = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
        ),
        content = content,
    )
}
