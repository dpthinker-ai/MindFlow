package com.mindflow.app.ui.screens.flow

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WebViewGraphCanvasStateTest {
    @Test
    fun `renderer buffers latest payload until page is ready`() {
        val state = WebViewGraphRenderState()
        val first = WebGraphPayload(centerNodeId = "work", nodes = emptyList(), edges = emptyList())
        val second = WebGraphPayload(centerNodeId = "learning", nodes = emptyList(), edges = emptyList())

        state.queuePayload(first)
        state.queuePayload(second)

        assertThat(state.consumePendingPayload()).isEqualTo(second)
        assertThat(state.consumePendingPayload()).isNull()
    }


    @Test
    fun `android px converts to css px before syncing viewport`() {
        assertThat(androidPxToCssPx(sizePx = 1040, density = 2.7083333f)).isEqualTo(384)
        assertThat(androidPxToCssPx(sizePx = 1274, density = 3.5f)).isEqualTo(364)
    }

    @Test
    fun `renderer fallback toggles on page load error`() {
        val state = WebViewGraphRenderState()

        state.onRenderError("boom")

        assertThat(state.hasRenderFailure).isTrue()
        assertThat(state.failureMessage).isEqualTo("boom")
    }
}
