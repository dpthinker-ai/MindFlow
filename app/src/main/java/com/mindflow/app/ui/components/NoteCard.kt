package com.mindflow.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.mindflow.app.data.local.entity.NoteEntity
import com.mindflow.app.data.model.MindFolderCatalog
import com.mindflow.app.data.model.NoteStatus
import com.mindflow.app.markdown.SimpleMarkdown
import com.mindflow.app.ui.theme.AccentSuccess
import com.mindflow.app.ui.theme.BorderSoft
import com.mindflow.app.ui.theme.TextSoft
import com.mindflow.app.ui.theme.WhiteGlass
import com.mindflow.app.util.TimeFormatter
import kotlin.math.roundToInt

@Composable
fun NoteCard(
    note: NoteEntity,
    onOpen: () -> Unit,
    onShare: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    PanelCard(
        modifier = modifier.clickable(onClick = onOpen),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = note.topic.ifBlank { "未命名想法" },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StatusBadge(status = note.status)
                        NoteHorizonBadge(label = note.horizon.label)
                        MindFolderCatalog.fromKey(note.folderKey)?.let { folder ->
                            NoteFolderBadge(
                                name = folder.name,
                                accent = Color(android.graphics.Color.parseColor(folder.colorHex)),
                            )
                        }
                        if (note.isArchived) {
                            Text(
                                text = "已归档",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextSoft,
                            )
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (onShare != null) {
                        Surface(
                            color = Color.Transparent,
                            shape = CircleShape,
                            onClick = onShare,
                        ) {
                            Box(
                                modifier = Modifier.size(30.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Share,
                                    contentDescription = "分享",
                                    modifier = Modifier.size(18.dp),
                                    tint = TextSoft,
                                )
                            }
                        }
                    }
                }
            }

            Text(
                text = SimpleMarkdown.toPlainText(note.content).replace("\n", " "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )

            if (note.tags.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    note.tags.take(2).forEach { tag ->
                        NoteTagBadge(tag = tag)
                    }
                    if (note.tags.size > 2) {
                        OverflowTagBadge(extraCount = note.tags.size - 2)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "更新于 ${TimeFormatter.compact(note.updatedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun OverflowTagBadge(
    extraCount: Int,
) {
    Surface(
        color = Color.Transparent,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, BorderSoft),
    ) {
        Text(
            text = "+$extraCount",
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = TextSoft,
            maxLines = 1,
        )
    }
}

@Composable
private fun NoteFolderBadge(
    name: String,
    accent: Color,
) {
    Surface(
        color = accent.copy(alpha = 0.1f),
        shape = RoundedCornerShape(999.dp),
    ) {
        Text(
            text = name,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = accent,
            maxLines = 1,
        )
    }
}

@Composable
private fun NoteHorizonBadge(
    label: String,
) {
    Surface(
        color = Color.Transparent,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, BorderSoft),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = TextSoft,
            maxLines = 1,
        )
    }
}

@Composable
private fun NoteTagBadge(
    tag: String,
) {
    Surface(
        color = Color.Transparent,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, BorderSoft),
    ) {
        Text(
            text = "#$tag",
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
fun SwipeRevealNoteCard(
    note: NoteEntity,
    onOpen: () -> Unit,
    onToggleArchive: (() -> Unit)?,
    onShare: (() -> Unit)?,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val revealWidth = 92.dp
    val revealWidthPx = with(LocalDensity.current) { revealWidth.toPx() }
    val maxRightReveal = if (onToggleArchive != null) revealWidthPx else 0f
    var offsetX by remember(note.id) { mutableFloatStateOf(0f) }
    val dragState = rememberDraggableState { delta ->
        offsetX = (offsetX + delta * 0.88f).coerceIn(-revealWidthPx, maxRightReveal)
    }

    Box(
        modifier = modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier.matchParentSize(),
        ) {
            DeleteAction(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(revealWidth)
                    .fillMaxHeight(),
                onDelete = {
                    offsetX = 0f
                    onDelete()
                },
            )
        }
        Box(
            modifier = Modifier.matchParentSize(),
        ) {
            if (onToggleArchive != null) {
                ArchiveAction(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .width(revealWidth)
                        .fillMaxHeight(),
                    archived = note.isArchived,
                    onArchive = {
                        offsetX = 0f
                        onToggleArchive()
                    },
                )
            }
        }

        NoteCard(
            note = note,
            onOpen = {
                if (offsetX != 0f) {
                    offsetX = 0f
                } else {
                    onOpen()
                }
            },
            onShare = onShare,
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .draggable(
                    state = dragState,
                    orientation = Orientation.Horizontal,
                    onDragStopped = {
                        offsetX = when {
                            offsetX <= -revealWidthPx * 0.28f -> -revealWidthPx
                            offsetX >= maxRightReveal * 0.28f -> maxRightReveal
                            else -> 0f
                        }
                    },
                ),
        )
    }
}

@Composable
private fun ArchiveAction(
    modifier: Modifier = Modifier,
    archived: Boolean,
    onArchive: () -> Unit,
) {
    Surface(
        modifier = modifier,
        color = AccentSuccess,
        shape = PanelShape,
        onClick = onArchive,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = if (archived) Icons.Outlined.Unarchive else Icons.Outlined.Archive,
                contentDescription = if (archived) "取消归档" else "归档",
                tint = Color.White,
            )
            Text(
                text = if (archived) "取消归档" else "归档",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun DeleteAction(
    modifier: Modifier = Modifier,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = modifier,
        color = Color(0xFFE5484D),
        shape = PanelShape,
        onClick = onDelete,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.DeleteOutline,
                contentDescription = "删除",
                tint = Color.White,
            )
            Text(
                text = "删除",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                maxLines = 1,
            )
        }
    }
}
