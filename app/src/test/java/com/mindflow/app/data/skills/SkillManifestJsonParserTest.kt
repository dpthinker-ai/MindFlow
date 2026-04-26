package com.mindflow.app.data.skills

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SkillManifestJsonParserTest {
    @Test
    fun parse_readsSkillManifest() {
        val manifest = SkillManifestJsonParser.parse(
            """
            {
              "id": "history-query",
              "name": "History Query",
              "description": "Query historical notes.",
              "version": "1.0.0",
              "entry": "scripts/index.html",
              "executor": "js",
              "output": ["text", "webview"],
              "permissions": ["history"],
              "nativeApis": ["history.query", "history.count"],
              "remote": {
                "signatureRequired": true
              }
            }
            """.trimIndent()
        )

        assertThat(manifest.id).isEqualTo("history-query")
        assertThat(manifest.executor).isEqualTo(SkillExecutorType.JS)
        assertThat(manifest.output).contains(SkillOutputType.WEBVIEW)
        assertThat(manifest.nativeApis).contains("history.query")
        assertThat(manifest.remote.signatureRequired).isTrue()
    }
}
