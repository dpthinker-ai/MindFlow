package com.mindflow.app.ui.screens.review

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ReviewHomeRouteContractTest {
    @Test
    fun reviewHomeRouteDoesNotDependOnTodayState() {
        assertThat(reviewHomeRouteDependencyLabels())
            .containsExactly("saved-review-chat-summary", "review-navigation")
            .inOrder()
    }
}
