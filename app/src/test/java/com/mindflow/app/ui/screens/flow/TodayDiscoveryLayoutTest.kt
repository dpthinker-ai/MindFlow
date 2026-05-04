package com.mindflow.app.ui.screens.flow

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TodayDiscoveryLayoutTest {
    @Test
    fun titleSlotReservesTwoLinesSoSourcesShareBaseline() {
        assertThat(TodayDiscoveryTitleSlotHeight.value).isEqualTo(32f)
    }

    @Test
    fun taskDetailSections_doNotRenderSeparateStatusTimelineCard() {
        assertThat(todayTaskDetailSectionKeys(showSplitSteps = false))
            .containsExactly(
                "hero",
                "related_summary",
                "materials",
                "next_suggestion",
                "progress",
            )
            .inOrder()
    }

    @Test
    fun taskTimelineConnectorSpansEveryGapBetweenNodes() {
        assertThat(todayTimelineConnectorSegments(stepCount = 4)).isEqualTo(3)
    }

    @Test
    fun todayFocusReasonStaysCompactOnFirstScreen() {
        assertThat(TodayFocusReasonDetailMaxLines).isEqualTo(2)
        assertThat(TodayFocusReasonIconSize.value).isAtMost(32f)
    }
}
