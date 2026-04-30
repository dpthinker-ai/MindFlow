package com.mindflow.app.data.skills

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

class SkillCardAssetContractTest {
    @Test
    fun historyQuery_usesSharedCardKitAndInsightCard() {
        val assetRoot = locateAssets()
        val script = assetRoot.resolve("skills/history-query/scripts/index.html").readText()
        val insightCard = assetRoot.resolve("skills/history-query/assets/insight-card.html").readText()
        val theme = assetRoot.resolve("skills/shared/card-kit/card-theme.css")
        val runtime = assetRoot.resolve("skills/shared/card-kit/card-runtime.js")

        assertThat(script).contains("assets/insight-card.html")
        assertThat(script).contains("payload")
        assertThat(insightCard).contains("skills/shared/card-kit/card-theme.css")
        assertThat(insightCard).contains("skills/shared/card-kit/card-runtime.js")
        assertThat(insightCard).contains("MindFlowCard")
        assertThat(theme.exists()).isTrue()
        assertThat(theme.readText()).doesNotContain("backdrop-filter")
        assertThat(theme.readText()).doesNotContain("filter: blur")
        assertThat(theme.readText()).doesNotContain("font-weight: 930")
        assertThat(runtime.exists()).isTrue()
        assertThat(assetRoot.resolve("skills/history-query/assets/result-card.html").exists()).isTrue()
    }

    private fun locateAssets(): File {
        val candidates = listOf(
            File("src/main/assets"),
            File("app/src/main/assets"),
        )
        return candidates.firstOrNull(File::exists)
            ?: error("Could not locate Android assets from ${File(".").absolutePath}")
    }
}
