package com.mindflow.app.data.skills

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SkillMarkdownParserTest {
    @Test
    fun parse_extractsHeaderAndBody() {
        val parsed = SkillMarkdownParser.parse(
            """
            ---
            name: history-query
            description: Query notes.
            ---
            
            # History Query
            
            Use this skill.
            """.trimIndent()
        )

        assertThat(parsed).isNotNull()
        assertThat(parsed!!.name).isEqualTo("history-query")
        assertThat(parsed.description).isEqualTo("Query notes.")
        assertThat(parsed.body).isEqualTo(
            """
            # History Query
            
            Use this skill.
            """.trimIndent(),
        )
    }
}
