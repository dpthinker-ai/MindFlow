package com.mindflow.app.ui.components

import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MindFlowUiKitTest {
    @Test
    fun bottomBarClearance_matchesCompactFloatingNavigation() {
        assertThat(BottomBarClearance).isEqualTo(88.dp)
    }
}
