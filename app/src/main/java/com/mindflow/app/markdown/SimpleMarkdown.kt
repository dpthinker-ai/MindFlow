package com.mindflow.app.markdown

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.LeadingMarginSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.UnderlineSpan
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.Heading
import org.commonmark.node.HardLineBreak
import org.commonmark.node.HtmlInline
import org.commonmark.node.Image
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text
import org.commonmark.node.ThematicBreak
import org.commonmark.parser.Parser

sealed interface MarkdownBlock

data class MarkdownHeading(
    val level: Int,
    val inlines: List<MarkdownSegment>,
) : MarkdownBlock

data class MarkdownParagraph(
    val inlines: List<MarkdownSegment>,
) : MarkdownBlock

data class MarkdownBulletList(
    val items: List<MarkdownListItem>,
) : MarkdownBlock

data class MarkdownOrderedList(
    val startNumber: Int,
    val items: List<MarkdownListItem>,
) : MarkdownBlock

data class MarkdownQuote(
    val blocks: List<MarkdownBlock>,
) : MarkdownBlock

data class MarkdownCodeBlock(
    val text: String,
) : MarkdownBlock

data object MarkdownDivider : MarkdownBlock

data class MarkdownListItem(
    val blocks: List<MarkdownBlock>,
)

data class MarkdownSegment(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val code: Boolean = false,
    val linkDestination: String? = null,
)

object SimpleMarkdown {
    private val parser: Parser = Parser.builder().build()

    fun parse(markdown: String): List<MarkdownBlock> {
        val normalized = markdown.replace("\r\n", "\n").trim()
        if (normalized.isBlank()) return emptyList()
        return parseChildren(parser.parse(normalized))
    }

    fun toPlainText(markdown: String): String =
        parse(markdown)
            .joinToString("\n\n") { toPlainText(it) }
            .trim()

    fun toPlainText(block: MarkdownBlock): String = when (block) {
        is MarkdownHeading -> block.inlines.joinToString(separator = "") { it.text }.trim()
        is MarkdownParagraph -> block.inlines.joinToString(separator = "") { it.text }.trim()
        is MarkdownBulletList -> block.items.joinToString("\n") { item ->
            "• " + item.blocks.joinToString("\n") { toPlainText(it) }.trim()
        }
        is MarkdownOrderedList -> block.items.mapIndexed { index, item ->
            "${block.startNumber + index}. " + item.blocks.joinToString("\n") { toPlainText(it) }.trim()
        }.joinToString("\n")
        is MarkdownQuote -> block.blocks.joinToString("\n") { toPlainText(it) }.trim()
        is MarkdownCodeBlock -> block.text.trim()
        MarkdownDivider -> "────────"
    }

    fun toSpannable(block: MarkdownBlock): CharSequence {
        val builder = SpannableStringBuilder()
        appendBlock(builder, block)
        return builder
    }

    private fun parseChildren(parent: Node): List<MarkdownBlock> {
        val blocks = mutableListOf<MarkdownBlock>()
        var child = parent.firstChild
        while (child != null) {
            parseBlock(child)?.let(blocks::add)
            child = child.next
        }
        return blocks
    }

    private fun parseBlock(node: Node): MarkdownBlock? = when (node) {
        is Heading -> MarkdownHeading(level = node.level, inlines = parseInlineChildren(node))
        is Paragraph -> MarkdownParagraph(inlines = parseInlineChildren(node))
        is BulletList -> MarkdownBulletList(
            items = collectListItems(node),
        )
        is OrderedList -> MarkdownOrderedList(
            startNumber = node.startNumber,
            items = collectListItems(node),
        )
        is BlockQuote -> MarkdownQuote(parseChildren(node))
        is FencedCodeBlock -> MarkdownCodeBlock(node.literal.orEmpty())
        is IndentedCodeBlock -> MarkdownCodeBlock(node.literal.orEmpty())
        is ThematicBreak -> MarkdownDivider
        else -> null
    }

    private fun collectListItems(listNode: Node): List<MarkdownListItem> {
        val items = mutableListOf<MarkdownListItem>()
        var child = listNode.firstChild
        while (child != null) {
            if (child is ListItem) {
                items += MarkdownListItem(parseChildren(child))
            }
            child = child.next
        }
        return items
    }

    private fun parseInlineChildren(parent: Node): List<MarkdownSegment> {
        val segments = mutableListOf<MarkdownSegment>()
        var child = parent.firstChild
        while (child != null) {
            appendInline(
                node = child,
                style = InlineStyle(),
                into = segments,
            )
            child = child.next
        }
        return segments
    }

    private fun appendInline(
        node: Node,
        style: InlineStyle,
        into: MutableList<MarkdownSegment>,
    ) {
        when (node) {
            is Text -> appendSegment(into, node.literal, style)
            is SoftLineBreak, is HardLineBreak -> appendSegment(into, "\n", style)
            is StrongEmphasis -> appendChildren(node, style.copy(bold = true), into)
            is Emphasis -> appendChildren(node, style.copy(italic = true), into)
            is Code -> appendSegment(into, node.literal, style.copy(code = true))
            is Link -> appendChildren(node, style.copy(linkDestination = node.destination), into)
            is Image -> {
                if (node.firstChild != null) {
                    appendChildren(node, style, into)
                } else {
                    appendSegment(into, "图片", style)
                }
            }
            is HtmlInline -> appendSegment(into, node.literal.orEmpty(), style)
            else -> appendChildren(node, style, into)
        }
    }

    private fun appendChildren(
        parent: Node,
        style: InlineStyle,
        into: MutableList<MarkdownSegment>,
    ) {
        var child = parent.firstChild
        while (child != null) {
            appendInline(child, style, into)
            child = child.next
        }
    }

    private fun appendSegment(
        into: MutableList<MarkdownSegment>,
        text: String?,
        style: InlineStyle,
    ) {
        val safeText = text.orEmpty()
        if (safeText.isEmpty()) return

        val last = into.lastOrNull()
        if (last != null &&
            last.bold == style.bold &&
            last.italic == style.italic &&
            last.code == style.code &&
            last.linkDestination == style.linkDestination
        ) {
            into[into.lastIndex] = last.copy(text = last.text + safeText)
        } else {
            into += MarkdownSegment(
                text = safeText,
                bold = style.bold,
                italic = style.italic,
                code = style.code,
                linkDestination = style.linkDestination,
            )
        }
    }

    private fun appendBlock(
        builder: SpannableStringBuilder,
        block: MarkdownBlock,
    ) {
        when (block) {
            is MarkdownHeading -> appendInlineSegments(builder, block.inlines)
            is MarkdownParagraph -> appendInlineSegments(builder, block.inlines)
            is MarkdownBulletList -> {
                block.items.forEachIndexed { index, item ->
                    val start = builder.length
                    builder.append("• ")
                    appendListItem(builder, item)
                    val end = builder.length
                    builder.setSpan(
                        LeadingMarginSpan.Standard(0, 32),
                        start,
                        end,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                    if (index != block.items.lastIndex) builder.append('\n')
                }
            }
            is MarkdownOrderedList -> {
                block.items.forEachIndexed { index, item ->
                    val start = builder.length
                    builder.append("${block.startNumber + index}. ")
                    appendListItem(builder, item)
                    val end = builder.length
                    builder.setSpan(
                        LeadingMarginSpan.Standard(0, 40),
                        start,
                        end,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                    if (index != block.items.lastIndex) builder.append('\n')
                }
            }
            is MarkdownQuote -> {
                block.blocks.forEachIndexed { index, child ->
                    val start = builder.length
                    builder.append("│ ")
                    appendBlock(builder, child)
                    val end = builder.length
                    builder.setSpan(
                        LeadingMarginSpan.Standard(0, 28),
                        start,
                        end,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                    if (index != block.blocks.lastIndex) builder.append('\n')
                }
            }
            is MarkdownCodeBlock -> builder.append(block.text.trim())
            MarkdownDivider -> builder.append("────────")
        }
    }

    private fun appendListItem(
        builder: SpannableStringBuilder,
        item: MarkdownListItem,
    ) {
        item.blocks.forEachIndexed { index, block ->
            appendBlock(builder, block)
            if (index != item.blocks.lastIndex) builder.append('\n')
        }
    }

    private fun appendInlineSegments(
        builder: SpannableStringBuilder,
        segments: List<MarkdownSegment>,
    ) {
        segments.forEach { segment ->
            val start = builder.length
            builder.append(segment.text)
            val end = builder.length
            if (start >= end) return@forEach

            if (segment.bold && segment.italic) {
                builder.setSpan(StyleSpan(Typeface.BOLD_ITALIC), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            } else if (segment.bold) {
                builder.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            } else if (segment.italic) {
                builder.setSpan(StyleSpan(Typeface.ITALIC), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            if (segment.code) {
                builder.setSpan(TypefaceSpan("monospace"), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            if (!segment.linkDestination.isNullOrBlank()) {
                builder.setSpan(UnderlineSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
    }

    private data class InlineStyle(
        val bold: Boolean = false,
        val italic: Boolean = false,
        val code: Boolean = false,
        val linkDestination: String? = null,
    )
}
