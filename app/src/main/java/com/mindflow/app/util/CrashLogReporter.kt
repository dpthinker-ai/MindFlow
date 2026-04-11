package com.mindflow.app.util

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class CrashLogReporter(
    context: Context,
) {
    private val logDir = File(
        context.getExternalFilesDir(null) ?: context.filesDir,
        "mindflow-logs",
    )
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { writeCrashLog(thread, throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun writeCrashLog(thread: Thread, throwable: Throwable) {
        logDir.mkdirs()
        val timestamp = System.currentTimeMillis()
        val crashFile = File(logDir, "crash-$timestamp.md")
        val latestFile = File(logDir, "latest-crash.md")
        val content = buildString {
            appendLine("# MindFlow Crash Log")
            appendLine()
            appendLine("- at: ${formatter.format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()))}")
            appendLine("- thread: ${thread.name}")
            appendLine("- type: ${throwable::class.java.name}")
            appendLine("- message: ${throwable.message.orEmpty()}")
            appendLine("- android: ${Build.VERSION.RELEASE} (${Build.VERSION.SDK_INT})")
            appendLine("- device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("- log dir: ${logDir.absolutePath}")
            appendLine()
            appendLine("```")
            appendLine(StringWriter().also { writer ->
                throwable.printStackTrace(PrintWriter(writer))
            }.toString().trim())
            appendLine("```")
        }
        crashFile.writeText(content)
        latestFile.writeText(content)
    }
}
