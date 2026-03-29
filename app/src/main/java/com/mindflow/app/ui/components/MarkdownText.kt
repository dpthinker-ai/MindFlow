package com.mindflow.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.mindflow.app.markdown.MarkdownBlock
import com.mindflow.app.markdown.MarkdownBulletList
import com.mindflow.app.markdown.MarkdownCodeBlock
import com.mindflow.app.markdown.MarkdownHeading
import com.mindflow.app.markdown.MarkdownOrderedList
import com.mindflow.app.markdown.MarkdownParagraph
import com.mindflow.app.markdown.MarkdownQuote
import com.mindflow.app.markdown.SimpleMarkdown
import com.mindflow.app.ui.theme.AccentBlue
import com.mindflow.app.ui.theme.BorderSoft
import com.mindflow.app.ui.theme.WhiteGlass

@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
) {
    val blocks = remember(markdown) {
        runCatching { SimpleMarkdown.parse(markdown) }.getOrNull()
    }
    if (blocks == null) {
        Text(
            text = markdown,
            modifier = modifier,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        return
    }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        blocks.forEach { block ->
            MarkdownBlockView(block = block)
        }
    }
}

@Composable
private fun MarkdownBlockView(
    block: MarkdownBlock,
    modifier: Modifier = Modifier,
) {
    when (block) {
        is MarkdownHeading -> {
            Text(
                text = block.toAnnotatedString(),
                modifier = modifier,
                style = when (block.level) {
                    1 -> MaterialTheme.typography.titleLarge
                    2 -> MaterialTheme.typography.titleMedium
                    else -> MaterialTheme.typography.titleSmall
                },
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        is MarkdownParagraph -> {
            Text(
                text = block.toAnnotatedString(),
                modifier = modifier,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        is MarkdownBulletList -> {
            Column(
                modifier = modifier,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                block.items.forEach { item ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text(
                            text = "•",
                            modifier = Modifier.padding(top = 1.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            item.blocks.forEach { child ->
                                MarkdownBlockView(block = child)
                            }
                        }
                    }
                }
            }
        }
        is MarkdownOrderedList -> {
            Column(
                modifier = modifier,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                block.items.forEachIndexed { index, item ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text(
                            text = "${block.startNumber + index}.",
                            modifier = Modifier.width(24.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            item.blocks.forEach { child ->
                                MarkdownBlockView(block = child)
                            }
                        }
                    }
                }
            }
        }
        is MarkdownQuote -> {
            Row(
                modifier = modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .width(4.dp)
                        .heightIn(min = 24.dp)
                        .background(AccentBlue.copy(alpha = 0.22f)),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    block.blocks.forEach { child ->
                        MarkdownBlockView(block = child)
                    }
                }
            }
        }
        is MarkdownCodeBlock -> {
            Surface(
                modifier = modifier.fillMaxWidth(),
                color = WhiteGlass.copy(alpha = 0.94f),
                shape = MaterialTheme.shapes.medium,
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderSoft),
            ) {
                Text(
                    text = block.text.trim(),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        else -> {
            Text(
                text = SimpleMarkdown.toPlainText(block),
                modifier = modifier,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

private fun MarkdownHeading.toAnnotatedString() = buildAnnotatedString {
    inlines.forEach { segment ->
        if (segment.bold) {
            pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
        }
        if (segment.italic) {
            pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
        }
        if (segment.code) {
            pushStyle(SpanStyle(fontFamily = FontFamily.Monospace))
        }
        if (!segment.linkDestination.isNullOrBlank()) {
            pushStyle(SpanStyle(color = AccentBlue, textDecoration = TextDecoration.Underline))
        }
        append(segment.text)
        repeat(
            listOf(segment.bold, segment.italic, segment.code, !segment.linkDestination.isNullOrBlank())
                .count { it }
        ) { pop() }
    }
}

private fun MarkdownParagraph.toAnnotatedString() = buildAnnotatedString {
    inlines.forEach { segment ->
        if (segment.bold) {
            pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
        }
        if (segment.italic) {
            pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
        }
        if (segment.code) {
            pushStyle(
                SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    background = WhiteGlass,
                )
            )
        }
        if (!segment.linkDestination.isNullOrBlank()) {
            pushStyle(SpanStyle(color = AccentBlue, textDecoration = TextDecoration.Underline))
        }
        append(segment.text)
        repeat(
            listOf(segment.bold, segment.italic, segment.code, !segment.linkDestination.isNullOrBlank())
                .count { it }
        ) { pop() }
    }
}
