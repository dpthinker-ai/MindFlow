package com.mindflow.app.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object TimeFormatter {
    private val zoneId: ZoneId
        get() = ZoneId.systemDefault()

    private val compactFormatter =
        DateTimeFormatter.ofPattern("MM-dd HH:mm", Locale.getDefault())

    private val fullFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.getDefault())

    private val fileFormatter =
        DateTimeFormatter.ofPattern("yyyyMMdd-HHmm", Locale.getDefault())

    fun compact(timestamp: Long): String = compactFormatter.format(Instant.ofEpochMilli(timestamp).atZone(zoneId))

    fun full(timestamp: Long): String = fullFormatter.format(Instant.ofEpochMilli(timestamp).atZone(zoneId))

    fun fileStamp(timestamp: Long): String = fileFormatter.format(Instant.ofEpochMilli(timestamp).atZone(zoneId))

    fun parseFull(raw: String): Long? = runCatching {
        fullFormatter.parse(raw.trim()) { parsed ->
            Instant.from(parsed).toEpochMilli()
        }
    }.getOrElse {
        runCatching {
            java.time.LocalDateTime.parse(raw.trim(), fullFormatter).atZone(zoneId).toInstant().toEpochMilli()
        }.getOrNull()
    }
}
