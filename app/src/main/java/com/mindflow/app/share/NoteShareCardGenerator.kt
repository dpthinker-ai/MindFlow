package com.mindflow.app.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.FileProvider
import com.mindflow.app.R
import com.mindflow.app.data.model.NoteStatus
import com.mindflow.app.markdown.MarkdownBlock
import com.mindflow.app.markdown.MarkdownHeading
import com.mindflow.app.markdown.SimpleMarkdown
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.ceil

enum class NoteShareStyle {
    LIGHT,
    DARK,
}

data class NoteSharePayload(
    val topic: String,
    val content: String,
    val tags: List<String>,
    val status: NoteStatus,
    val timestampMillis: Long?,
)

class NoteShareCardGenerator(
    private val context: Context,
) {
    suspend fun generate(
        payload: NoteSharePayload,
        style: NoteShareStyle,
    ) = withContext(Dispatchers.IO) {
        val shareDir = File(context.cacheDir, "shared_cards").apply { mkdirs() }
        cleanupOldFiles(shareDir)

        val bitmap = render(payload, style)
        val output = File(shareDir, "mindflow-share-${System.currentTimeMillis()}.png")
        FileOutputStream(output).use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
        bitmap.recycle()

        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            output,
        )
    }

    private fun render(
        payload: NoteSharePayload,
        style: NoteShareStyle,
    ): Bitmap {
        val renderScale = 2f
        val palette = paletteFor(style)
        val plainContent = SimpleMarkdown.toPlainText(payload.content).trim()
        val compactMode = plainContent.length in 1..72 && plainContent.lineSequence().count() <= 2
        val outer = if (compactMode) 24f else 28f
        val innerHorizontal = if (compactMode) 30f else 34f
        val innerVertical = if (compactMode) 26f else 30f
        val corner = 30f
        val maxBodyWidth = if (compactMode) 640 else 860

        val namePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.title
            textSize = if (compactMode) 24f else 26f
            isFakeBoldText = true
            isSubpixelText = true
            isLinearText = true
        }
        val handlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.meta
            textSize = if (compactMode) 18f else 20f
            isSubpixelText = true
            isLinearText = true
        }
        val topicPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.accent
            textSize = if (compactMode) 20f else 22f
            isFakeBoldText = true
            isSubpixelText = true
            isLinearText = true
        }
        val headingLargePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.title
            textSize = if (compactMode) 36f else 34f
            isFakeBoldText = true
            isSubpixelText = true
            isLinearText = true
        }
        val headingPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.title
            textSize = if (compactMode) 30f else 29f
            isFakeBoldText = true
            isSubpixelText = true
            isLinearText = true
        }
        val contentPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.body
            textSize = if (compactMode) 28f else 25f
            isSubpixelText = true
            isLinearText = true
        }
        val tagPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.accent
            textSize = 18f
            isFakeBoldText = true
            isSubpixelText = true
            isLinearText = true
        }
        val metaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.meta
            textSize = 19f
            isSubpixelText = true
            isLinearText = true
        }
        val sourcePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.metaStrong
            textSize = 18f
            isFakeBoldText = true
            isSubpixelText = true
            isLinearText = true
        }
        val statusPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.statusText
            textSize = 18f
            isFakeBoldText = true
            isSubpixelText = true
            isLinearText = true
        }
        val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isDither = true }
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.card
            isDither = true
        }
        val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.halo
            isDither = true
        }
        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.divider
            strokeWidth = 2f
        }
        val avatarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.accent }
        val cardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.border
            this.style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        val statusBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.statusBackground }
        val sourceBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.sourceBackground }

        val topic = buildTopic(payload)
        val markdownBlocks = buildMarkdownBlocks(payload, topic)
        val measuredLayouts = measureLayouts(
            topic = topic,
            blocks = markdownBlocks,
            topicPaint = topicPaint,
            headingLargePaint = headingLargePaint,
            headingPaint = headingPaint,
            contentPaint = contentPaint,
            maxBodyWidth = maxBodyWidth,
            minimumWidth = measureHeaderWidth(namePaint, handlePaint, sourcePaint),
        )

        val contentWidth = measuredLayouts.contentWidth
        val width = ceil(contentWidth + outer * 2f + innerHorizontal * 2f)
            .toInt()
            .coerceIn(if (compactMode) 430 else 660, 1040)
        val bodyWidth = (width - outer * 2f - innerHorizontal * 2f).toInt()

        val topicLayout = if (topic.isBlank()) {
            null
        } else {
            buildLayout(
                text = topic,
                paint = topicPaint,
                width = bodyWidth,
                maxLines = 2,
                lineSpacing = 1.02f,
                alignment = Layout.Alignment.ALIGN_NORMAL,
            )
        }
        val blockLayouts = markdownBlocks.map { block ->
            buildLayout(
                text = SimpleMarkdown.toSpannable(block),
                paint = paintFor(block, headingLargePaint, headingPaint, contentPaint),
                width = bodyWidth,
                maxLines = maxLinesFor(block),
                lineSpacing = lineSpacingFor(block),
                alignment = Layout.Alignment.ALIGN_NORMAL,
            )
        }

        val tags = payload.tags.take(2)
        val blockSpacing = if (compactMode) 10f else 14f
        val blocksHeight = blockLayouts.sumOf { it.height } +
            ((blockLayouts.size - 1).coerceAtLeast(0) * blockSpacing)
        val headerHeight = if (compactMode) 66f else 72f
        val topicHeight = topicLayout?.height?.toFloat() ?: 0f
        val topicGap = if (topicLayout != null) if (compactMode) 10f else 14f else 0f
        val dividerGap = if (compactMode) 18f else 24f
        val footerHeight = if (compactMode) 46f else 52f
        val height = ceil(
            outer * 2f +
                innerVertical * 2f +
                headerHeight +
                topicHeight +
                topicGap +
                blocksHeight +
                dividerGap +
                footerHeight
        ).toInt().coerceIn(if (compactMode) 360 else 500, 1680)

        val bitmap = Bitmap.createBitmap(
            (width * renderScale).toInt(),
            (height * renderScale).toInt(),
            Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(bitmap)
        canvas.scale(renderScale, renderScale)

        backgroundPaint.shader = LinearGradient(
            0f,
            0f,
            width.toFloat(),
            height.toFloat(),
            palette.backgroundStart,
            palette.backgroundEnd,
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
        canvas.drawCircle(width - 128f, 110f, 136f, haloPaint)
        canvas.drawCircle(108f, height - 118f, 92f, haloPaint)
        drawGlow(canvas, width - 96f, 98f, 176f, palette.glow)

        val cardRect = RectF(
            outer,
            outer,
            width - outer,
            height - outer,
        )
        canvas.drawRoundRect(cardRect, corner, corner, cardPaint)
        canvas.drawRoundRect(cardRect, corner, corner, cardBorderPaint)

        val innerLeft = cardRect.left + innerHorizontal
        val innerRight = cardRect.right - innerHorizontal
        val avatarRadius = 30f
        val avatarCenterX = innerLeft + avatarRadius
        val avatarCenterY = cardRect.top + innerVertical + avatarRadius
        canvas.drawCircle(avatarCenterX, avatarCenterY, avatarRadius, avatarPaint)
        drawAppIconAvatar(
            canvas = canvas,
            centerX = avatarCenterX,
            centerY = avatarCenterY,
            radius = avatarRadius,
        )

        val nameX = avatarCenterX + avatarRadius + 18f
        canvas.drawText("dpthinker", nameX, avatarCenterY - 6f, namePaint)
        canvas.drawText("公众号", nameX, avatarCenterY + 23f, handlePaint)
        drawSourcePill(
            canvas = canvas,
            text = "From MindFlow",
            right = innerRight,
            centerY = avatarCenterY,
            backgroundPaint = sourceBackgroundPaint,
            textPaint = sourcePaint,
        )

        var cursorY = cardRect.top + innerVertical + headerHeight + if (compactMode) 6f else 0f
        if (topicLayout != null) {
            drawLayout(canvas, topicLayout, innerLeft, cursorY)
            cursorY += topicLayout.height + topicGap
        }

        blockLayouts.forEachIndexed { index, layout ->
            drawLayout(canvas, layout, innerLeft, cursorY)
            cursorY += layout.height
            if (index != blockLayouts.lastIndex) {
                cursorY += blockSpacing
            }
        }

        val dividerY = cursorY + dividerGap
        canvas.drawLine(innerLeft, dividerY, innerRight, dividerY, dividerPaint)

        val statusWidth = pillWidth(payload.status.label, statusPaint, horizontal = 14f)
        val tagsStart = innerLeft + statusWidth + if (tags.isNotEmpty()) 10f else 0f
        val footerBaseline = dividerY + 34f
        drawStatusPill(
            canvas = canvas,
            text = payload.status.label,
            left = innerLeft,
            baselineY = footerBaseline,
            backgroundPaint = statusBackgroundPaint,
            textPaint = statusPaint,
        )

        val timeText = formatTimestamp(payload.timestampMillis)
        val timeWidth = metaPaint.measureText(timeText)
        canvas.drawText(
            timeText,
            innerRight - timeWidth,
            footerBaseline,
            metaPaint,
        )

        if (tags.isNotEmpty()) {
            drawTagPills(
                canvas = canvas,
                tags = tags,
                startX = tagsStart,
                top = dividerY + 18f,
                maxRight = innerRight - timeWidth - 18f,
                fillPaint = statusBackgroundPaint,
                textPaint = tagPaint,
            )
        }

        return bitmap
    }

    private fun measureLayouts(
        topic: String,
        blocks: List<MarkdownBlock>,
        topicPaint: TextPaint,
        headingLargePaint: TextPaint,
        headingPaint: TextPaint,
        contentPaint: TextPaint,
        maxBodyWidth: Int,
        minimumWidth: Float,
    ): ShareMeasuredLayouts {
        val provisionalTopic = if (topic.isBlank()) {
            null
        } else {
            buildLayout(
                text = topic,
                paint = topicPaint,
                width = maxBodyWidth,
                maxLines = 2,
                lineSpacing = 1.02f,
                alignment = Layout.Alignment.ALIGN_NORMAL,
            )
        }
        val provisionalBlocks = blocks.map { block ->
            buildLayout(
                text = SimpleMarkdown.toSpannable(block),
                paint = paintFor(block, headingLargePaint, headingPaint, contentPaint),
                width = maxBodyWidth,
                maxLines = maxLinesFor(block),
                lineSpacing = lineSpacingFor(block),
                alignment = Layout.Alignment.ALIGN_NORMAL,
            )
        }
        val widestLine = maxOf(
            minimumWidth,
            provisionalTopic?.maxLineWidth() ?: 0f,
            provisionalBlocks.maxOfOrNull { it.maxLineWidth() } ?: 0f,
        )
        return ShareMeasuredLayouts(contentWidth = (widestLine + 8f).coerceIn(420f, maxBodyWidth.toFloat()))
    }

    private fun buildLayout(
        text: CharSequence,
        paint: TextPaint,
        width: Int,
        maxLines: Int,
        lineSpacing: Float,
        alignment: Layout.Alignment,
    ): StaticLayout {
        val safeText = if (text.isBlank()) "记下一条想法。" else text
        return StaticLayout.Builder
            .obtain(safeText, 0, safeText.length, paint, width)
            .setAlignment(alignment)
            .setIncludePad(false)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setMaxLines(maxLines)
            .setLineSpacing(0f, lineSpacing)
            .build()
    }

    private fun drawLayout(
        canvas: Canvas,
        layout: StaticLayout,
        x: Float,
        y: Float,
    ) {
        canvas.save()
        canvas.translate(x, y)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun buildTopic(payload: NoteSharePayload): String {
        val topic = payload.topic.trim()
        if (topic.isNotBlank()) return topic
        return payload.content
            .lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() }
            ?.removePrefix("#")
            ?.take(24)
            .orEmpty()
    }

    private fun buildMarkdownBlocks(
        payload: NoteSharePayload,
        topic: String,
    ): List<MarkdownBlock> {
        val blocks = SimpleMarkdown.parse(payload.content)
        if (blocks.isEmpty()) {
            return SimpleMarkdown.parse(SimpleMarkdown.toPlainText(payload.content).ifBlank { "记下一条想法。" })
        }

        val firstHeading = blocks.firstOrNull() as? MarkdownHeading
        val firstHeadingText = firstHeading?.inlines?.joinToString(separator = "") { it.text }?.trim()
        return if (!firstHeadingText.isNullOrBlank() && firstHeadingText == topic.trim()) {
            blocks.drop(1).ifEmpty { blocks }
        } else {
            blocks
        }
    }

    private fun formatTimestamp(timestampMillis: Long?): String {
        val formatter = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.CHINA)
        return formatter.format(Date(timestampMillis ?: System.currentTimeMillis()))
    }

    private fun cleanupOldFiles(directory: File) {
        directory.listFiles()
            ?.sortedByDescending { it.lastModified() }
            ?.drop(8)
            ?.forEach { it.delete() }
    }

    private fun drawAppIconAvatar(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        radius: Float,
    ) {
        val iconBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.mindflow_icon) ?: return
        val avatarBitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isFilterBitmap = true
            isDither = true
        }
        val avatarRect = RectF(
            centerX - radius,
            centerY - radius,
            centerX + radius,
            centerY + radius,
        )
        val clipPath = Path().apply {
            addOval(avatarRect, Path.Direction.CW)
        }
        canvas.save()
        canvas.clipPath(clipPath)
        canvas.drawBitmap(iconBitmap, null, avatarRect, avatarBitmapPaint)
        canvas.restore()
        iconBitmap.recycle()
    }

    private fun measureHeaderWidth(
        namePaint: TextPaint,
        handlePaint: TextPaint,
        sourcePaint: TextPaint,
    ): Float {
        val authorWidth = maxOf(
            namePaint.measureText("dpthinker"),
            handlePaint.measureText("公众号"),
        )
        return authorWidth + sourcePaint.measureText("From MindFlow") + 162f
    }

    private fun drawTagPills(
        canvas: Canvas,
        tags: List<String>,
        startX: Float,
        top: Float,
        maxRight: Float,
        fillPaint: Paint,
        textPaint: TextPaint,
    ) {
        var cursorX = startX
        val horizontal = 12f
        val height = 28f
        val gap = 8f
        tags.forEach { tag ->
            val label = "#$tag"
            val width = textPaint.measureText(label) + horizontal * 2f
            if (cursorX + width > maxRight) return@forEach
            val rect = RectF(
                cursorX,
                top,
                cursorX + width,
                top + height,
            )
            canvas.drawRoundRect(rect, 999f, 999f, fillPaint)
            canvas.drawText(
                label,
                rect.left + horizontal,
                rect.top + 20f,
                textPaint,
            )
            cursorX += width + gap
        }
    }

    private fun drawStatusPill(
        canvas: Canvas,
        text: String,
        left: Float,
        baselineY: Float,
        backgroundPaint: Paint,
        textPaint: TextPaint,
    ) {
        val horizontal = 14f
        val width = pillWidth(text, textPaint, horizontal)
        val rect = RectF(
            left,
            baselineY - 22f,
            left + width,
            baselineY + 8f,
        )
        canvas.drawRoundRect(rect, 999f, 999f, backgroundPaint)
        canvas.drawText(
            text,
            rect.left + horizontal,
            baselineY,
            textPaint,
        )
    }

    private fun drawSourcePill(
        canvas: Canvas,
        text: String,
        right: Float,
        centerY: Float,
        backgroundPaint: Paint,
        textPaint: TextPaint,
    ) {
        val horizontal = 14f
        val height = 30f
        val width = pillWidth(text, textPaint, horizontal)
        val rect = RectF(
            right - width,
            centerY - height / 2f,
            right,
            centerY + height / 2f,
        )
        canvas.drawRoundRect(rect, 999f, 999f, backgroundPaint)
        canvas.drawText(
            text,
            rect.left + horizontal,
            centerY + 6f,
            textPaint,
        )
    }

    private fun drawGlow(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        radius: Float,
        color: Int,
    ) {
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                centerX,
                centerY,
                radius,
                intArrayOf(color, color and 0x00FFFFFF),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawCircle(centerX, centerY, radius, glowPaint)
    }
}

private data class ShareMeasuredLayouts(
    val contentWidth: Float,
)

private data class SharePalette(
    val backgroundStart: Int,
    val backgroundEnd: Int,
    val card: Int,
    val title: Int,
    val body: Int,
    val meta: Int,
    val metaStrong: Int,
    val accent: Int,
    val halo: Int,
    val glow: Int,
    val border: Int,
    val statusBackground: Int,
    val statusText: Int,
    val sourceBackground: Int,
    val divider: Int,
)

private fun paletteFor(style: NoteShareStyle): SharePalette = when (style) {
    NoteShareStyle.LIGHT -> SharePalette(
        backgroundStart = 0xFFF6F8FC.toInt(),
        backgroundEnd = 0xFFF1F5FF.toInt(),
        card = 0xFFFFFFFF.toInt(),
        title = 0xFF0F172A.toInt(),
        body = 0xFF243244.toInt(),
        meta = 0xFF6B7280.toInt(),
        metaStrong = 0xFF56687C.toInt(),
        accent = 0xFF3E7BFF.toInt(),
        halo = 0x123E7BFF,
        glow = 0x223E7BFF,
        border = 0x120F172A,
        statusBackground = 0x123E7BFF,
        statusText = 0xFF2E63D4.toInt(),
        sourceBackground = 0x0E3E7BFF,
        divider = 0x140F172A,
    )
    NoteShareStyle.DARK -> SharePalette(
        backgroundStart = 0xFF0F172A.toInt(),
        backgroundEnd = 0xFF12223C.toInt(),
        card = 0xFF132338.toInt(),
        title = 0xFFF8FAFC.toInt(),
        body = 0xFFD9E5F2.toInt(),
        meta = 0xFFA2B3C6.toInt(),
        metaStrong = 0xFFC4D3E6.toInt(),
        accent = 0xFF7DB2FF.toInt(),
        halo = 0x147DB2FF,
        glow = 0x287DB2FF,
        border = 0x22FFFFFF,
        statusBackground = 0x1E79A7FF,
        statusText = 0xFFDCE6FF.toInt(),
        sourceBackground = 0x167DB2FF,
        divider = 0x26FFFFFF,
    )
}

private fun paintFor(
    block: MarkdownBlock,
    headingLargePaint: TextPaint,
    headingPaint: TextPaint,
    contentPaint: TextPaint,
): TextPaint = when (block) {
    is MarkdownHeading -> if (block.level == 1) headingLargePaint else headingPaint
    else -> contentPaint
}

private fun maxLinesFor(block: MarkdownBlock): Int = when (block) {
    is MarkdownHeading -> 3
    else -> 10
}

private fun lineSpacingFor(block: MarkdownBlock): Float = when (block) {
    is MarkdownHeading -> 1.06f
    else -> 1.16f
}

private fun pillWidth(
    text: String,
    paint: TextPaint,
    horizontal: Float,
): Float = paint.measureText(text) + horizontal * 2f

private fun StaticLayout.maxLineWidth(): Float {
    var maxWidth = 0f
    for (index in 0 until lineCount) {
        maxWidth = maxOf(maxWidth, getLineWidth(index))
    }
    return ceil(maxWidth)
}
