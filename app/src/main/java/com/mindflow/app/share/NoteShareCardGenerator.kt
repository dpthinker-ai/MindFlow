package com.mindflow.app.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.FileProvider
import com.mindflow.app.data.model.NoteStatus
import com.mindflow.app.markdown.MarkdownBlock
import com.mindflow.app.markdown.MarkdownHeading
import com.mindflow.app.markdown.SimpleMarkdown
import com.mindflow.app.R
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
        val palette = paletteFor(style)
        val outer = 36f
        val corner = 30f
        val namePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.title
            textSize = 29f
            isFakeBoldText = true
        }
        val handlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.meta
            textSize = 23f
        }
        val topicPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.title
            textSize = 50f
            isFakeBoldText = true
        }
        val headingPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.title
            textSize = 36f
            isFakeBoldText = true
        }
        val contentPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.body
            textSize = 32f
        }
        val tagPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.accent
            textSize = 22f
            isFakeBoldText = true
        }
        val metaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.meta
            textSize = 24f
        }
        val statusPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.statusText
            textSize = 24f
            isFakeBoldText = true
        }
        val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.background }
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.card }
        val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.halo }
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

        val topic = buildTopic(payload)
        val markdownBlocks = buildMarkdownBlocks(payload, topic)
        val contentWidth = computeContentWidth(
            topicPaint = topicPaint,
            headingPaint = headingPaint,
            contentPaint = contentPaint,
            topic = topic,
            blocks = markdownBlocks,
        )
        val width = (contentWidth + outer * 2f + 74f).toInt().coerceIn(780, 1080)
        val contentWidthInt = contentWidth.toInt()
        val topicLayout = buildLayout(
            text = topic.ifBlank { "未命名想法" },
            paint = topicPaint,
            width = contentWidthInt,
            maxLines = 2,
            lineSpacing = 1.04f,
        )
        val blockLayouts = markdownBlocks.map { block ->
            val paint = if (block is MarkdownHeading) headingPaint else contentPaint
            buildLayout(
                text = SimpleMarkdown.toSpannable(block),
                paint = paint,
                width = contentWidthInt,
                maxLines = if (block is MarkdownHeading) 2 else 8,
                lineSpacing = if (block is MarkdownHeading) 1.04f else 1.15f,
            )
        }
        val tags = payload.tags.take(3)
        val tagsHeight = if (tags.isNotEmpty()) 40f else 0f
        val blockSpacing = 16
        val blocksHeight = blockLayouts.sumOf { it.height } +
            ((blockLayouts.size - 1).coerceAtLeast(0) * blockSpacing)
        val height = (
            outer * 2f +
                120f +
                topicLayout.height +
                16f +
                blocksHeight +
                tagsHeight +
                108f
            ).toInt().coerceIn(760, 1160)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
        canvas.drawCircle(width - 130f, 120f, 120f, haloPaint)
        canvas.drawCircle(100f, height - 120f, 86f, haloPaint)

        val cardRect = RectF(
            outer,
            outer,
            width - outer,
            height - outer,
        )
        canvas.drawRoundRect(cardRect, corner, corner, cardPaint)
        canvas.drawRoundRect(cardRect, corner, corner, cardBorderPaint)

        val innerLeft = cardRect.left + 34f
        val innerRight = cardRect.right - 34f

        val avatarRadius = 30f
        val avatarCenterX = innerLeft + avatarRadius
        val avatarCenterY = cardRect.top + 70f
        canvas.drawCircle(avatarCenterX, avatarCenterY, avatarRadius, avatarPaint)
        drawAppIconAvatar(
            canvas = canvas,
            centerX = avatarCenterX,
            centerY = avatarCenterY,
            radius = avatarRadius,
        )

        val nameX = avatarCenterX + avatarRadius + 18f
        canvas.drawText("dpthinker", nameX, avatarCenterY - 6f, namePaint)
        canvas.drawText("公众号", nameX, avatarCenterY + 28f, handlePaint)

        drawStatusPill(
            canvas = canvas,
            text = payload.status.label,
            right = innerRight,
            centerY = avatarCenterY,
            backgroundPaint = statusBackgroundPaint,
            textPaint = statusPaint,
        )

        var cursorY = cardRect.top + 136f
        drawLayout(
            canvas = canvas,
            layout = topicLayout,
            x = innerLeft,
            y = cursorY,
        )
        cursorY += topicLayout.height + 16f

        blockLayouts.forEachIndexed { index, layout ->
            drawLayout(
                canvas = canvas,
                layout = layout,
                x = innerLeft,
                y = cursorY,
            )
            cursorY += layout.height
            if (index != blockLayouts.lastIndex) {
                cursorY += blockSpacing.toFloat()
            }
        }

        val chipsTop = (cardRect.bottom - 120f).coerceAtLeast(cursorY + 16f)
        if (tags.isNotEmpty()) {
            drawTagPills(
                canvas = canvas,
                tags = tags,
                startX = innerLeft,
                top = chipsTop,
                maxRight = innerRight,
                fillPaint = statusBackgroundPaint,
                textPaint = tagPaint,
            )
        }

        val dividerY = cardRect.bottom - 74f
        canvas.drawLine(innerLeft, dividerY, innerRight, dividerY, dividerPaint)
        val timeText = formatTimestamp(payload.timestampMillis)
        canvas.drawText(
            timeText,
            innerLeft,
            dividerY + 46f,
            metaPaint,
        )
        val fromText = "From MindFlow"
        canvas.drawText(
            fromText,
            innerRight - metaPaint.measureText(fromText),
            dividerY + 46f,
            metaPaint,
        )

        return bitmap
    }

    private fun buildLayout(
        text: CharSequence,
        paint: TextPaint,
        width: Int,
        maxLines: Int,
        lineSpacing: Float,
    ): StaticLayout {
        val safeText = if (text.isBlank()) "记下一条想法。" else text
        return StaticLayout.Builder
            .obtain(safeText, 0, safeText.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
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
        canvas.drawBitmap(iconBitmap, null, avatarRect, null)
        canvas.restore()
    }

    private fun computeContentWidth(
        topicPaint: TextPaint,
        headingPaint: TextPaint,
        contentPaint: TextPaint,
        topic: String,
        blocks: List<MarkdownBlock>,
    ): Float {
        val topicPreview = topic.take(14).ifBlank { "未命名想法" }
        val blockPreviewWidth = blocks.take(3).maxOfOrNull { block ->
            val preview = SimpleMarkdown.toPlainText(block).replace("\n", " ").take(24)
            val paint = if (block is MarkdownHeading) headingPaint else contentPaint
            if (preview.isBlank()) 0f else paint.measureText(preview)
        } ?: 0f
        val previewWidth = maxOf(
            640f,
            topicPaint.measureText(topicPreview),
            blockPreviewWidth,
        )
        return previewWidth.coerceIn(640f, 920f)
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
        val horizontal = 14f
        val height = 34f
        val gap = 10f
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
                rect.top + 23f,
                textPaint,
            )
            cursorX += width + gap
        }
    }

    private fun drawStatusPill(
        canvas: Canvas,
        text: String,
        right: Float,
        centerY: Float,
        backgroundPaint: Paint,
        textPaint: TextPaint,
    ) {
        val horizontal = 18f
        val height = 42f
        val width = textPaint.measureText(text) + horizontal * 2f
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
            centerY + 8f,
            textPaint,
        )
    }
}

private data class SharePalette(
    val background: Int,
    val card: Int,
    val title: Int,
    val body: Int,
    val meta: Int,
    val accent: Int,
    val halo: Int,
    val border: Int,
    val statusBackground: Int,
    val statusText: Int,
    val divider: Int,
)

private fun paletteFor(style: NoteShareStyle): SharePalette = when (style) {
    NoteShareStyle.LIGHT -> SharePalette(
        background = 0xFFF4F1EA.toInt(),
        card = 0xFFFFFFFF.toInt(),
        title = 0xFF0F172A.toInt(),
        body = 0xFF334155.toInt(),
        meta = 0xFF6B7280.toInt(),
        accent = 0xFF3F7DFF.toInt(),
        halo = 0x164BA3FF,
        border = 0x0F0F172A,
        statusBackground = 0x123F7DFF,
        statusText = 0xFF2F5FD2.toInt(),
        divider = 0x140F172A,
    )
    NoteShareStyle.DARK -> SharePalette(
        background = 0xFF0E1726.toInt(),
        card = 0xFF162235.toInt(),
        title = 0xFFF8FAFC.toInt(),
        body = 0xFFDCE6EF.toInt(),
        meta = 0xFF98A7BA.toInt(),
        accent = 0xFF79A7FF.toInt(),
        halo = 0x183F7DFF,
        border = 0x22FFFFFF,
        statusBackground = 0x1E79A7FF,
        statusText = 0xFFDCE6FF.toInt(),
        divider = 0x26FFFFFF,
    )
}
