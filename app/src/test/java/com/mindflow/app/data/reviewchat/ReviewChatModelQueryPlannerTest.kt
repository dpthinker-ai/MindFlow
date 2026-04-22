package com.mindflow.app.data.reviewchat

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ReviewChatModelQueryPlannerTest {
    @Test
    fun parse_validJsonPlan_returnsStructuredPlan() {
        val plan = ReviewChatModelQueryPlanner.parse(
            """
            {
              "operation":"list",
              "entity_terms":[],
              "wants_categories":true,
              "wants_examples":false,
              "wants_links":false
            }
            """.trimIndent()
        )

        assertThat(plan).isNotNull()
        assertThat(plan?.operation).isEqualTo(ReviewChatQueryOperation.LIST)
        assertThat(plan?.entityTerms).isEmpty()
        assertThat(plan?.wantsCategories).isTrue()
    }

    @Test
    fun parse_invalidContent_returnsNull() {
        val plan = ReviewChatModelQueryPlanner.parse("不是 JSON")
        assertThat(plan).isNull()
    }
}
