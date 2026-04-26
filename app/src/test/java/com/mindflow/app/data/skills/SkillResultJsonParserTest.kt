package com.mindflow.app.data.skills

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SkillResultJsonParserTest {
    @Test
    fun parse_resolvesRelativeWebViewUrlsAgainstSkillAssets() {
        val skill = SkillPackage(
            manifest = SkillManifest(
                id = "history-query",
                name = "History Query",
                description = "Query history",
                version = "1.0.0",
                entry = "scripts/index.html",
            ),
            instructions = SkillInstructions(
                name = "history-query",
                description = "Query history",
                body = "Use this skill.",
            ),
            assetBasePath = "skills/history-query",
        )

        val result = SkillResultJsonParser.parse(
            skill = skill,
            raw = """
                {
                  "result": "命中 3 条记录",
                  "webview": {
                    "url": "assets/result-card.html",
                    "iframe": false,
                    "aspectRatio": 1.5
                  },
                  "metadata": {
                    "matchedCount": "3"
                  }
                }
            """.trimIndent(),
        )

        assertThat(result.result).isEqualTo("命中 3 条记录")
        assertThat(result.webview).isNotNull()
        assertThat(result.webview!!.url)
            .isEqualTo("file:///android_asset/skills/history-query/assets/result-card.html")
        assertThat(result.webview!!.aspectRatio).isEqualTo(1.5f)
        assertThat(result.metadata["matchedCount"]).isEqualTo("3")
    }
}
