package com.mindflow.app.ui.screens.flow

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mindflow.app.data.reviewchat.SavedReviewChatSessionSummary
import com.mindflow.app.ui.components.ActionButton
import com.mindflow.app.ui.components.GhostActionButton
import com.mindflow.app.ui.components.InsightBlock
import com.mindflow.app.ui.components.PanelCard
import com.mindflow.app.ui.theme.AccentBlue
import com.mindflow.app.ui.theme.BorderSoft
import com.mindflow.app.ui.theme.TextMain
import com.mindflow.app.ui.theme.TextSoft
import com.mindflow.app.util.TimeFormatter

@Composable
fun ReviewChatEntryCard(
    latestSavedSummary: SavedReviewChatSessionSummary?,
    onSubmitQuestion: (String) -> Unit,
    onOpenLatestSaved: () -> Unit,
) {
    var draft by rememberSaveable { mutableStateOf("") }

    PanelCard {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "和历史聊聊",
                style = MaterialTheme.typography.titleMedium,
                color = TextMain,
            )
            Text(
                text = "基于你的历史记录和沉淀内容继续聊。",
                style = MaterialTheme.typography.bodySmall,
                color = TextSoft,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 5,
            placeholder = {
                Text(
                    text = "比如：把最近两周关于产品方向的矛盾串一下",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSoft,
                )
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AccentBlue.copy(alpha = 0.6f),
                unfocusedBorderColor = BorderSoft,
            ),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ActionButton(
                text = "开始聊",
                onClick = {
                    val question = draft.trim()
                    if (question.isNotBlank()) {
                        draft = ""
                        onSubmitQuestion(question)
                    }
                },
                enabled = draft.isNotBlank(),
                modifier = Modifier.weight(1f),
            )
            latestSavedSummary?.let {
                GhostActionButton(
                    text = "继续上次保存",
                    onClick = onOpenLatestSaved,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        latestSavedSummary?.let { summary ->
            InsightBlock {
                Text(
                    text = "最近一次保存",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSoft,
                )
                Text(
                    text = summary.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMain,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = TimeFormatter.compact(summary.updatedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSoft,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}
