package com.mindflow.app.ui.navigation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MindFlowDestinationsTest {
    @Test
    fun todaySecondaryRoutes_areStableAndEncodeThreadKeys() {
        assertThat(MindFlowDestinations.TODAY_DISCOVERY).isEqualTo("flow/today/discovery")
        assertThat(MindFlowDestinations.TODAY_TASK_DETAIL).isEqualTo("flow/today/task/{threadKey}")
        assertThat(MindFlowDestinations.TODAY_TASK_DETAIL_ARG).isEqualTo("threadKey")
        assertThat(MindFlowDestinations.todayTaskDetailRoute("work life")).isEqualTo("flow/today/task/work%20life")
    }
}
