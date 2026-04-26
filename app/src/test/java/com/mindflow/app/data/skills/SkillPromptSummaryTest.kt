package com.mindflow.app.data.skills

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SkillPromptSummaryTest {
    @Test
    fun promptLine_usesSkillInstructionsAndOutputTypes() {
        val skill = SkillPackage(
            manifest = SkillManifest(
                id = "history-query",
                name = "History Query",
                description = "Manifest fallback description.",
                version = "1.0.0",
                entry = "scripts/index.html",
                output = setOf(SkillOutputType.TEXT, SkillOutputType.WEBVIEW),
            ),
            instructions = SkillInstructions(
                name = "history-query",
                description = "Query and visualize MindFlow historical notes.",
                body = "Use this for history questions.",
            ),
            assetBasePath = "skills/history-query",
        )

        val line = skill.toPromptSummary().toPromptLine()

        assertThat(line).isEqualTo("- history-query：Query and visualize MindFlow historical notes.（输出：text/webview）")
    }
}
