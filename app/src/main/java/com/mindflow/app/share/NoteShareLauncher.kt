package com.mindflow.app.share

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.mindflow.app.data.local.entity.NoteEntity

suspend fun shareNoteCard(
    context: Context,
    generator: NoteShareCardGenerator,
    note: NoteEntity,
    style: NoteShareStyle,
) {
    val uri = generator.generate(
        payload = NoteSharePayload(
            topic = note.topic,
            content = note.content,
            tags = note.tags,
            status = note.status,
            timestampMillis = note.updatedAt,
        ),
        style = style,
    )

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(intent, "分享图片").apply {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (context !is Activity) {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
    context.startActivity(chooser)
}
