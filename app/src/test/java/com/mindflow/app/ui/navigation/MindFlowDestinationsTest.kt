package com.mindflow.app.ui.navigation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MindFlowDestinationsTest {
    @Test
    fun productNamedTopLevelRoutes_keepLegacyRouteValues() {
        assertThat(MindFlowDestinations.TODAY).isEqualTo("flow/today")
        assertThat(MindFlowDestinations.REVIEW).isEqualTo("flow/review")
        assertThat(MindFlowDestinations.GRAPH).isEqualTo("flow/graph")
        assertThat(MindFlowDestinations.todayRoute()).isEqualTo(MindFlowDestinations.TODAY)
        assertThat(MindFlowDestinations.reviewRoute()).isEqualTo(MindFlowDestinations.REVIEW)
        assertThat(MindFlowDestinations.graphRoute()).isEqualTo(MindFlowDestinations.GRAPH)
    }

    @Test
    fun todaySecondaryRoutes_areStableAndEncodeThreadKeys() {
        assertThat(MindFlowDestinations.TODAY_DISCOVERY).isEqualTo("flow/today/discovery")
        assertThat(MindFlowDestinations.TODAY_TASK_DETAIL).isEqualTo("flow/today/task/{threadKey}")
        assertThat(MindFlowDestinations.TODAY_TASK_DETAIL_ARG).isEqualTo("threadKey")
        assertThat(MindFlowDestinations.todayTaskDetailRoute("work life")).isEqualTo("flow/today/task/work%20life")
    }
}
