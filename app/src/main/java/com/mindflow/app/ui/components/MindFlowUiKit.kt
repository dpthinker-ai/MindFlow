package com.mindflow.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mindflow.app.ui.theme.Accent
import com.mindflow.app.ui.theme.AccentBlue
import com.mindflow.app.ui.theme.BorderSoft
import com.mindflow.app.ui.theme.Cream
import com.mindflow.app.ui.theme.CreamMid
import com.mindflow.app.ui.theme.CreamTop
import com.mindflow.app.ui.theme.TextMain
import com.mindflow.app.ui.theme.TextSoft
import com.mindflow.app.ui.theme.WhiteGlass

internal val PanelShape = RoundedCornerShape(18.dp)
internal val CardShape = RoundedCornerShape(13.dp)
internal val BottomBarClearance = 108.dp
internal val ScreenHorizontalPadding = 18.dp

@Composable
fun ScreenBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        CreamTop,
                        CreamMid,
                        Cream,
                    ),
                ),
            ),
        content = content,
    )
}

@Composable
fun PanelCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        color = WhiteGlass,
        shape = PanelShape,
        border = BorderStroke(1.dp, BorderSoft),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

@Composable
fun ScreenHeader(
    kicker: String? = null,
    title: String,
    subtitle: String? = null,
    action: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = ScreenHorizontalPadding, top = 6.dp, end = ScreenHorizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (!kicker.isNullOrBlank()) {
                Text(
                    text = kicker,
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSoft,
                    maxLines = 1,
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = TextMain,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSoft,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (action != null) {
            action()
        }
    }
}

@Composable
fun SectionHeader(
    kicker: String? = null,
    title: String,
    headline: String? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(if (kicker.isNullOrBlank()) 0.dp else 2.dp),
        ) {
            if (!kicker.isNullOrBlank()) {
                Text(
                    text = kicker,
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSoft,
                    maxLines = 1,
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = TextMain,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (headline != null) {
            Text(
                text = headline,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                color = TextSoft,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun GridTwo(
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
fun MetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    accent: Color = TextMain,
) {
    Surface(
        color = WhiteGlass.copy(alpha = 0.92f),
        shape = CardShape,
        modifier = modifier.heightIn(min = 64.dp),
        border = BorderStroke(1.dp, BorderSoft),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = TextSoft,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun NeonProgress(
    progress: Float,
    startColor: Color = Accent,
    endColor: Color = AccentBlue,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .background(Color(0x120F172A), RoundedCornerShape(999.dp)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(6.dp)
                .background(
                    Brush.horizontalGradient(listOf(startColor, endColor)),
                    RoundedCornerShape(999.dp),
                ),
        )
    }
}

@Composable
fun ActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 44.dp),
        shape = CardShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Accent,
            contentColor = Color.White,
        ),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null)
            Spacer(Modifier.size(8.dp))
        }
        Text(text = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun GhostActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 44.dp),
        shape = CardShape,
        border = BorderStroke(1.dp, BorderSoft),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.Transparent,
            contentColor = TextMain,
        ),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null)
            Spacer(Modifier.size(8.dp))
        }
        Text(text = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun IconPillButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = AccentBlue,
) {
    Surface(
        modifier = modifier,
        color = WhiteGlass,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, BorderSoft),
        onClick = onClick,
    ) {
        Box(
            modifier = Modifier.size(40.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = accent,
            )
        }
    }
}
