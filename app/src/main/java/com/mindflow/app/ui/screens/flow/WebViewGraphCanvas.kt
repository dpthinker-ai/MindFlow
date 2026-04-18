package com.mindflow.app.ui.screens.flow

import android.annotation.SuppressLint
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import kotlin.math.roundToInt
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView

private const val WebViewGraphLogTag = "MindFlowGraphWeb"

internal fun androidPxToCssPx(sizePx: Int, density: Float): Int {
    if (sizePx <= 0) return 0
    if (density <= 0f) return sizePx
    return (sizePx / density).roundToInt()
}

private fun WebView.pushHostViewportSize() {
    if (width <= 0 || height <= 0) return
    val density = resources.displayMetrics.density
    val cssWidth = androidPxToCssPx(width, density)
    val cssHeight = androidPxToCssPx(height, density)
    Log.d(WebViewGraphLogTag, "sync viewport px=${width}x${height} css=${cssWidth}x${cssHeight} density=$density")
    evaluateJavascript("window.syncHostViewport(${cssWidth}, ${cssHeight});", null)
}

internal class WebViewGraphRenderState {
    private var pendingPayload: WebGraphPayload? = null
    var hasRenderFailure: Boolean = false
        private set
    var failureMessage: String = ""
        private set
    var isPageReady: Boolean = false
        private set

    fun queuePayload(payload: WebGraphPayload) {
        pendingPayload = payload
    }

    fun consumePendingPayload(): WebGraphPayload? = pendingPayload.also { pendingPayload = null }

    fun markPageReady() {
        isPageReady = true
    }

    fun onRenderError(message: String) {
        hasRenderFailure = true
        failureMessage = message
    }

    fun clearError() {
        hasRenderFailure = false
        failureMessage = ""
    }
}

internal class GraphWebMessageBridge(
    private val onEvent: (GraphBridgeEvent) -> Unit,
) {
    @JavascriptInterface
    fun postMessage(raw: String) {
        onEvent(parseGraphBridgeEvent(raw))
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun WebViewGraphCanvas(
    payload: WebGraphPayload,
    modifier: Modifier = Modifier,
    onNodeClick: (String) -> Unit,
    onRenderError: (String) -> Unit,
) {
    val renderState = remember { WebViewGraphRenderState() }
    LaunchedEffect(payload) {
        renderState.queuePayload(payload)
        renderState.clearError()
    }
    AndroidView(
        modifier = modifier.testTag(KnowledgeGraphCanvasTag),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.builtInZoomControls = false
                settings.displayZoomControls = false
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                    val widthChanged = (right - left) != (oldRight - oldLeft)
                    val heightChanged = (bottom - top) != (oldBottom - oldTop)
                    if ((widthChanged || heightChanged) && renderState.isPageReady && !renderState.hasRenderFailure) {
                        post { pushHostViewportSize() }
                    }
                }
                addJavascriptInterface(
                    GraphWebMessageBridge { event ->
                        when (event) {
                            GraphBridgeEvent.ViewportReady -> {
                                renderState.markPageReady()
                                post {
                                    pushHostViewportSize()
                                    renderState.consumePendingPayload()?.let { queued ->
                                        evaluateJavascript("window.renderGraph(${queued.toJavascriptLiteral()});", null)
                                    }
                                }
                            }
                            is GraphBridgeEvent.NodeClick -> onNodeClick(event.conceptId)
                            is GraphBridgeEvent.RenderError -> {
                                renderState.onRenderError(event.message)
                                onRenderError(event.message)
                            }
                            is GraphBridgeEvent.Invalid -> Unit
                        }
                    },
                    "MindFlowGraphBridge",
                )
                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                        Log.d(
                            WebViewGraphLogTag,
                            "console ${consoleMessage.messageLevel()}: ${consoleMessage.message()} @${consoleMessage.sourceId()}:${consoleMessage.lineNumber()}",
                        )
                        return true
                    }
                }
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        Log.d(WebViewGraphLogTag, "page finished: $url")
                        renderState.markPageReady()
                        view?.evaluateJavascript("typeof window.renderGraph") { result ->
                            Log.d(WebViewGraphLogTag, "renderGraph type=$result")
                        }
                        view?.evaluateJavascript("typeof cytoscape") { result ->
                            Log.d(WebViewGraphLogTag, "cytoscape type=$result")
                        }
                        view?.post {
                            view.pushHostViewportSize()
                            renderState.consumePendingPayload()?.let { queued ->
                                Log.d(
                                    WebViewGraphLogTag,
                                    "render queued payload center=${queued.centerNodeId} nodes=${queued.nodes.size} edges=${queued.edges.size}",
                                )
                                view.evaluateJavascript("window.renderGraph(${queued.toJavascriptLiteral()});", null)
                            }
                        }
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?,
                    ) {
                        if (request?.isForMainFrame != false) {
                            val message = error?.description?.toString().orEmpty().ifBlank { "graph_page_load_failed" }
                            renderState.onRenderError(message)
                            onRenderError(message)
                        }
                    }
                }
                loadUrl("file:///android_asset/graph/index.html")
            }
        },
        update = { view ->
            view.post {
                if (renderState.isPageReady && !renderState.hasRenderFailure) {
                    view.pushHostViewportSize()
                    renderState.consumePendingPayload()?.let { queued ->
                        Log.d(
                            WebViewGraphLogTag,
                            "update payload center=${queued.centerNodeId} nodes=${queued.nodes.size} edges=${queued.edges.size}",
                        )
                        view.evaluateJavascript("window.renderGraph(${queued.toJavascriptLiteral()});", null)
                    }
                }
            }
        },
    )
}
