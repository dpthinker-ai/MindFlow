package com.mindflow.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mindflow.app.data.model.NoteStatus
import com.mindflow.app.ui.theme.Done
import com.mindflow.app.ui.theme.Idea
import com.mindflow.app.ui.theme.InProgress

fun noteStatusAccent(status: NoteStatus): Color = when (status) {
    NoteStatus.IDEA -> Idea
    NoteStatus.IN_PROGRESS -> InProgress
    NoteStatus.DONE -> Done
}

@Composable
fun StatusBadge(
    status: NoteStatus,
    modifier: Modifier = Modifier,
) {
    val accent = noteStatusAccent(status)

    Surface(
        modifier = modifier,
        color = accent.copy(alpha = 0.12f),
        contentColor = accent,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.16f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = status.label,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}
