# Cytoscape WebView Graph Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the custom Compose concept graph canvas with an offline `WebView + Cytoscape.js` renderer while keeping heatmap, explanation, center switching, and expand-more behavior in Compose.

**Architecture:** Keep `ConceptGraphSnapshot` and `ConceptGraphViewport` as the business-state source, add a small web payload + JS bridge layer, and swap only the graph canvas in `KnowledgeGraphScreen`. The WebView owns layout, pan, zoom, and hit-testing; Compose continues to own graph state, empty state, and explanatory copy.

**Tech Stack:** Kotlin, Jetpack Compose, Android `WebView`, local APK assets, Cytoscape.js, JUnit4, Truth, Android Compose UI test.

---

## Scope Check

This remains one cohesive implementation plan. Every task exists to achieve one product change: replace the current custom graph renderer with an offline embedded graph engine without changing the surrounding concept-graph business flow.

## Compatibility Decisions

1. Keep `buildConceptGraphViewport(...)` and `buildConceptGraphCenterRelation(...)` as the Compose-side source for current graph state.
2. Keep the heatmap card and the information panel in `KnowledgeGraphScreen`; only the graph drawing surface changes.
3. Keep `KnowledgeGraphCanvasTag` and `KnowledgeGraphInfoPanelTag` so existing tests and QA flows retain stable anchors.
4. Do not push domain logic into JavaScript. The JS layer only renders and returns `nodeClick`, `viewportReady`, and `renderError` events.
5. Bundle Cytoscape locally under `app/src/main/assets/graph/vendor/` so the renderer stays offline and deterministic.

## File Map

### Create

- `app/src/main/java/com/mindflow/app/ui/screens/flow/WebViewGraphContract.kt`
  Purpose: define the web payload, element serialization, and bridge event parsing for the graph renderer.
- `app/src/main/java/com/mindflow/app/ui/screens/flow/WebViewGraphCanvas.kt`
  Purpose: host the Android `WebView`, manage page readiness, send payload updates, and expose graph callbacks to Compose.
- `app/src/main/assets/graph/index.html`
  Purpose: local HTML shell that hosts Cytoscape.
- `app/src/main/assets/graph/graph.css`
  Purpose: graph-only styling for the WebView canvas.
- `app/src/main/assets/graph/graph.js`
  Purpose: initialize Cytoscape, render payloads, fit the center-anchored graph, and forward graph events to Android.
- `app/src/main/assets/graph/vendor/cytoscape.min.js`
  Purpose: vendored Cytoscape runtime, checked into the repo for offline rendering.
- `app/src/test/java/com/mindflow/app/ui/screens/flow/WebViewGraphContractTest.kt`
  Purpose: verify viewport-to-payload mapping and bridge event parsing.
- `app/src/test/java/com/mindflow/app/ui/screens/flow/WebViewGraphCanvasStateTest.kt`
  Purpose: verify readiness buffering and fallback state decisions in the canvas state helpers.

### Modify

- `app/src/main/java/com/mindflow/app/ui/screens/flow/KnowledgeGraphScreen.kt`
  Purpose: remove the custom node/edge canvas from the production path, host `WebViewGraphCanvas`, keep heatmap and explanation UI, and wire click events back into `selectCenter(...)`.
- `app/src/androidTest/java/com/mindflow/app/ui/screens/flow/KnowledgeGraphScreenInstrumentedTest.kt`
  Purpose: keep graph-page behavior covered after the renderer swap.
- `app/src/test/java/com/mindflow/app/ui/screens/flow/KnowledgeGraphScreenTest.kt`
  Purpose: keep viewport and relation selection logic covered while the renderer changes.

## Task 1: Define the Web Contract and Bridge Parser

**Files:**
- Create: `app/src/main/java/com/mindflow/app/ui/screens/flow/WebViewGraphContract.kt`
- Test: `app/src/test/java/com/mindflow/app/ui/screens/flow/WebViewGraphContractTest.kt`

- [ ] **Step 1: Write the failing contract tests**

```kotlin
package com.mindflow.app.ui.screens.flow

import com.google.common.truth.Truth.assertThat
import com.mindflow.app.data.wiki.ConceptGraphEdge
import com.mindflow.app.data.wiki.ConceptGraphNode
import com.mindflow.app.data.wiki.ConceptGraphRelationType
import org.junit.Test

class WebViewGraphContractTest {
    @Test
    fun `viewport payload marks center node and keeps only visible neighbors`() {
        val viewport = ConceptGraphViewport(
            centerNode = ConceptGraphNode(
                conceptId = "work",
                label = "工作系统",
                summary = "围绕执行和复盘。",
            ),
            neighbors = listOf(
                ConceptGraphViewportNeighbor(
                    node = ConceptGraphNode(
                        conceptId = "learning",
                        label = "学习",
                        summary = "把工作拆回练习。",
                    ),
                    relation = ConceptGraphEdge(
                        fromConceptId = "work",
                        toConceptId = "learning",
                        relationType = ConceptGraphRelationType.SUPPORTS,
                        confidence = 0.8,
                    ),
                    relationWord = "支持",
                ),
            ),
        )

        val payload = viewport.toWebPayload()

        assertThat(payload.centerNodeId).isEqualTo("work")
        assertThat(payload.nodes.map { it.id }).containsExactly("work", "learning")
        assertThat(payload.nodes.first { it.id == "work" }.isCenter).isTrue()
        assertThat(payload.edges.single().source).isEqualTo("work")
        assertThat(payload.edges.single().target).isEqualTo("learning")
    }

    @Test
    fun `bridge parser accepts nodeClick and rejects malformed events`() {
        val click = parseGraphBridgeEvent("""
            {"type":"nodeClick","conceptId":"learning"}
        """.trimIndent())
        val malformed = parseGraphBridgeEvent("""{"type":1}""" )

        assertThat(click).isEqualTo(GraphBridgeEvent.NodeClick("learning"))
        assertThat(malformed).isEqualTo(GraphBridgeEvent.Invalid("missing_or_invalid_type"))
    }
}
```

- [ ] **Step 2: Run the contract tests and verify they fail**

Run:

```bash
JAVA_HOME=$HOME/.jdks/temurin-21 ./gradlew --no-daemon :app:testDebugUnitTest --tests com.mindflow.app.ui.screens.flow.WebViewGraphContractTest
```

Expected: FAIL with unresolved references for `toWebPayload`, `GraphBridgeEvent`, and `parseGraphBridgeEvent`.

- [ ] **Step 3: Implement the payload and event contract**

```kotlin
package com.mindflow.app.ui.screens.flow

import com.mindflow.app.data.wiki.ConceptGraphRelationType
import org.json.JSONObject

internal data class WebGraphPayload(
    val version: Int = 1,
    val centerNodeId: String,
    val nodes: List<WebGraphNode>,
    val edges: List<WebGraphEdge>,
)

internal data class WebGraphNode(
    val id: String,
    val label: String,
    val accentColor: String,
    val isCenter: Boolean,
)

internal data class WebGraphEdge(
    val id: String,
    val source: String,
    val target: String,
    val relationType: String,
    val confidence: Double,
)

internal sealed interface GraphBridgeEvent {
    data object ViewportReady : GraphBridgeEvent
    data class NodeClick(val conceptId: String) : GraphBridgeEvent
    data class RenderError(val message: String) : GraphBridgeEvent
    data class Invalid(val reason: String) : GraphBridgeEvent
}

private fun Color.toWebHex(): String = String.format(
    "#%02X%02X%02X",
    (red * 255).toInt(),
    (green * 255).toInt(),
    (blue * 255).toInt(),
)

internal fun ConceptGraphViewport.toWebPayload(): WebGraphPayload {
    val center = centerNode ?: return WebGraphPayload(centerNodeId = "", nodes = emptyList(), edges = emptyList())
    val webNodes = buildList {
        add(
            WebGraphNode(
                id = center.conceptId,
                label = center.label,
                accentColor = conceptNodeAccent(center.conceptId).toWebHex(),
                isCenter = true,
            )
        )
        neighbors.forEach { neighbor ->
            add(
                WebGraphNode(
                    id = neighbor.node.conceptId,
                    label = neighbor.node.label,
                    accentColor = conceptNodeAccent(neighbor.node.conceptId).toWebHex(),
                    isCenter = false,
                )
            )
        }
    }
    val webEdges = neighbors.map { neighbor ->
        WebGraphEdge(
            id = "${neighbor.relation.fromConceptId}->${neighbor.relation.toConceptId}:${neighbor.relation.relationType.wireName}",
            source = neighbor.relation.fromConceptId,
            target = neighbor.relation.toConceptId,
            relationType = neighbor.relation.relationType.wireName,
            confidence = neighbor.relation.confidence,
        )
    }
    return WebGraphPayload(
        centerNodeId = center.conceptId,
        nodes = webNodes.distinctBy { it.id },
        edges = webEdges,
    )
}

internal fun parseGraphBridgeEvent(raw: String): GraphBridgeEvent = runCatching {
    val json = JSONObject(raw)
    when (val type = json.optString("type").trim()) {
        "viewportReady" -> GraphBridgeEvent.ViewportReady
        "nodeClick" -> {
            val conceptId = json.optString("conceptId").trim()
            if (conceptId.isBlank()) GraphBridgeEvent.Invalid("missing_concept_id")
            else GraphBridgeEvent.NodeClick(conceptId)
        }
        "renderError" -> GraphBridgeEvent.RenderError(json.optString("message").trim())
        else -> GraphBridgeEvent.Invalid(if (type.isBlank()) "missing_or_invalid_type" else "unknown_type")
    }
}.getOrElse { GraphBridgeEvent.Invalid("invalid_json") }

internal fun WebGraphPayload.toJavascriptLiteral(): String = JSONObject().apply {
    put("version", version)
    put("centerNodeId", centerNodeId)
    put("nodes", org.json.JSONArray(nodes.map { node ->
        JSONObject().apply {
            put("id", node.id)
            put("label", node.label)
            put("accentColor", node.accentColor)
            put("isCenter", node.isCenter)
        }
    }))
    put("edges", org.json.JSONArray(edges.map { edge ->
        JSONObject().apply {
            put("id", edge.id)
            put("source", edge.source)
            put("target", edge.target)
            put("relationType", edge.relationType)
            put("confidence", edge.confidence)
        }
    }))
}.toString()
```

- [ ] **Step 4: Re-run the contract tests and verify they pass**

Run:

```bash
JAVA_HOME=$HOME/.jdks/temurin-21 ./gradlew --no-daemon :app:testDebugUnitTest --tests com.mindflow.app.ui.screens.flow.WebViewGraphContractTest
```

Expected: PASS with 2 tests successful.

- [ ] **Step 5: Commit the contract layer**

```bash
git add app/src/main/java/com/mindflow/app/ui/screens/flow/WebViewGraphContract.kt         app/src/test/java/com/mindflow/app/ui/screens/flow/WebViewGraphContractTest.kt
git commit -m "feat: add WebView graph payload contract"
```

## Task 2: Add the Offline WebView Graph Renderer

**Files:**
- Create: `app/src/main/java/com/mindflow/app/ui/screens/flow/WebViewGraphCanvas.kt`
- Create: `app/src/main/assets/graph/index.html`
- Create: `app/src/main/assets/graph/graph.css`
- Create: `app/src/main/assets/graph/graph.js`
- Create: `app/src/main/assets/graph/vendor/cytoscape.min.js`
- Test: `app/src/test/java/com/mindflow/app/ui/screens/flow/WebViewGraphCanvasStateTest.kt`

- [ ] **Step 1: Write the failing state-buffering tests**

```kotlin
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
    fun `renderer fallback toggles on page load error`() {
        val state = WebViewGraphRenderState()

        state.onRenderError("boom")

        assertThat(state.hasRenderFailure).isTrue()
        assertThat(state.failureMessage).isEqualTo("boom")
    }
}
```

- [ ] **Step 2: Run the state tests and verify they fail**

Run:

```bash
JAVA_HOME=$HOME/.jdks/temurin-21 ./gradlew --no-daemon :app:testDebugUnitTest --tests com.mindflow.app.ui.screens.flow.WebViewGraphCanvasStateTest
```

Expected: FAIL with unresolved references for `WebViewGraphRenderState`.

- [ ] **Step 3: Implement the WebView host and local assets**

```kotlin
package com.mindflow.app.ui.screens.flow

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceError
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView

internal class WebViewGraphRenderState {
    private var pendingPayload: WebGraphPayload? = null
    var hasRenderFailure: Boolean = false
        private set
    var failureMessage: String = ""
        private set

    fun queuePayload(payload: WebGraphPayload) {
        pendingPayload = payload
    }

    fun consumePendingPayload(): WebGraphPayload? = pendingPayload.also { pendingPayload = null }

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
    }
    AndroidView(
        modifier = modifier.testTag(KnowledgeGraphCanvasTag),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.setSupportZoom(false)
                addJavascriptInterface(
                    GraphWebMessageBridge { event ->
                        when (event) {
                            is GraphBridgeEvent.NodeClick -> onNodeClick(event.conceptId)
                            is GraphBridgeEvent.RenderError -> {
                                renderState.onRenderError(event.message)
                                onRenderError(event.message)
                            }
                            GraphBridgeEvent.ViewportReady -> {
                                renderState.consumePendingPayload()?.let { queued ->
                                    evaluateJavascript("window.renderGraph(${queued.toJavascriptLiteral()});", null)
                                }
                            }
                            is GraphBridgeEvent.Invalid -> Unit
                        }
                    },
                    "MindFlowGraphBridge",
                )
                webChromeClient = WebChromeClient()
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        renderState.consumePendingPayload()?.let { queued ->
                            evaluateJavascript("window.renderGraph(${queued.toJavascriptLiteral()});", null)
                        }
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?,
                    ) {
                        val message = error?.description?.toString().orEmpty().ifBlank { "graph_page_load_failed" }
                        renderState.onRenderError(message)
                        onRenderError(message)
                    }
                }
                loadUrl("file:///android_asset/graph/index.html")
            }
        },
        update = { view ->
            if (!renderState.hasRenderFailure) {
                renderState.consumePendingPayload()?.let { queued ->
                    view.evaluateJavascript("window.renderGraph(${queued.toJavascriptLiteral()});", null)
                }
            }
        },
    )
}
```

```html
<!doctype html>
<html lang="en">
  <head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no" />
    <link rel="stylesheet" href="graph.css" />
  </head>
  <body>
    <div id="graph-root"></div>
    <script src="vendor/cytoscape.min.js"></script>
    <script src="graph.js"></script>
  </body>
</html>
```

```css
html, body, #graph-root {
  margin: 0;
  width: 100%;
  height: 100%;
  background: transparent;
  overflow: hidden;
}
```

```javascript
let cy = null;

function postToAndroid(event) {
  if (window.MindFlowGraphBridge && window.MindFlowGraphBridge.postMessage) {
    window.MindFlowGraphBridge.postMessage(JSON.stringify(event));
  }
}

function ensureGraph() {
  if (cy) return cy;
  cy = cytoscape({
    container: document.getElementById('graph-root'),
    elements: [],
    style: [
      {
        selector: 'node',
        style: {
          'background-color': 'data(accentColor)',
          'label': 'data(label)',
          'color': '#243145',
          'font-size': 11,
          'text-valign': 'bottom',
          'text-halign': 'center',
          'text-margin-y': 10,
          'width': 18,
          'height': 18,
        },
      },
      {
        selector: 'node[isCenter = 1]',
        style: {
          'width': 28,
          'height': 28,
          'font-size': 13,
          'font-weight': 600,
        },
      },
      {
        selector: 'edge',
        style: {
          'curve-style': 'bezier',
          'line-color': '#b6c2d1',
          'width': 'mapData(confidence, 0, 1, 1.5, 3.5)',
          'opacity': 0.85,
        },
      },
    ],
    userZoomingEnabled: true,
    userPanningEnabled: true,
    autoungrabify: true,
  });

  cy.on('tap', 'node', (event) => {
    const node = event.target;
    postToAndroid({ type: 'nodeClick', conceptId: node.id() });
  });

  postToAndroid({ type: 'viewportReady' });
  return cy;
}

window.renderGraph = function renderGraph(payload) {
  try {
    const graph = ensureGraph();
    const elements = [];
    payload.nodes.forEach((node) => {
      elements.push({ data: { ...node, isCenter: node.isCenter ? 1 : 0 } });
    });
    payload.edges.forEach((edge) => {
      elements.push({ data: edge });
    });
    graph.elements().remove();
    graph.add(elements);
    graph.layout({
      name: 'concentric',
      fit: true,
      animate: false,
      concentric: (node) => node.data('isCenter') ? 2 : 1,
      levelWidth: () => 1,
      spacingFactor: 1.15,
    }).run();
  } catch (error) {
    postToAndroid({ type: 'renderError', message: String(error && error.message ? error.message : error) });
  }
};
```

Add the vendored runtime with an explicit command during implementation:

```bash
mkdir -p app/src/main/assets/graph/vendor
curl -L https://unpkg.com/cytoscape@3.30.2/dist/cytoscape.min.js -o app/src/main/assets/graph/vendor/cytoscape.min.js
```

- [ ] **Step 4: Re-run the state tests and debug compile**

Run:

```bash
JAVA_HOME=$HOME/.jdks/temurin-21 ./gradlew --no-daemon   :app:testDebugUnitTest --tests com.mindflow.app.ui.screens.flow.WebViewGraphCanvasStateTest   :app:compileDebugKotlin
```

Expected: PASS for `WebViewGraphCanvasStateTest` and successful Kotlin compilation.

- [ ] **Step 5: Commit the renderer shell**

```bash
git add app/src/main/java/com/mindflow/app/ui/screens/flow/WebViewGraphCanvas.kt         app/src/main/assets/graph/index.html         app/src/main/assets/graph/graph.css         app/src/main/assets/graph/graph.js         app/src/main/assets/graph/vendor/cytoscape.min.js         app/src/test/java/com/mindflow/app/ui/screens/flow/WebViewGraphCanvasStateTest.kt
git commit -m "feat: add offline WebView graph renderer"
```

## Task 3: Integrate the WebView Renderer into `KnowledgeGraphScreen`

**Files:**
- Modify: `app/src/main/java/com/mindflow/app/ui/screens/flow/KnowledgeGraphScreen.kt`
- Modify: `app/src/test/java/com/mindflow/app/ui/screens/flow/KnowledgeGraphScreenTest.kt`
- Modify: `app/src/androidTest/java/com/mindflow/app/ui/screens/flow/KnowledgeGraphScreenInstrumentedTest.kt`

- [ ] **Step 1: Write the failing integration assertions**

```kotlin
@Test
fun `graph page keeps heatmap and info panel while swapping canvas host`() {
    composeRule.setContent {
        MindFlowTheme {
            KnowledgeGraphScreen(
                snapshot = connectedSnapshot(),
                notes = emptyList(),
                onOpenNote = {},
            )
        }
    }

    composeRule.onNodeWithText("记录热度").assertIsDisplayed()
    composeRule.onNodeWithTag(KnowledgeGraphCanvasTag).assertIsDisplayed()
    composeRule.onNodeWithTag(KnowledgeGraphInfoPanelTag).assertTextContains("工作系统")
}
```

```kotlin
@Test
fun `viewport logic remains unchanged after renderer swap`() {
    val viewport = buildConceptGraphViewport(connectedSnapshot().conceptGraph)

    assertThat(viewport.centerNode?.conceptId).isEqualTo("work")
    assertThat(viewport.neighbors.map { it.node.conceptId }).containsExactly("learning")
}
```

- [ ] **Step 2: Run the targeted tests and confirm at least one fails on the old renderer host assumptions**

Run:

```bash
JAVA_HOME=$HOME/.jdks/temurin-21 ./gradlew --no-daemon   :app:testDebugUnitTest --tests com.mindflow.app.ui.screens.flow.KnowledgeGraphScreenTest   :app:compileDebugAndroidTestKotlin
```

Expected: either failing assertions or compile failures once the test starts referencing the new WebView-backed structure.

- [ ] **Step 3: Swap the canvas in `KnowledgeGraphScreen` and preserve existing Compose state**

```kotlin
@Composable
private fun ConceptGraphCard(
    viewport: ConceptGraphViewport,
    relationFromPreviousCenter: ConceptGraphCenterRelation?,
    onSelectNode: (String) -> Unit,
) {
    var renderError by remember { mutableStateOf<String?>(null) }
    val payload = remember(viewport) { viewport.toWebPayload() }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        when {
            viewport.centerNode == null -> {
                Text(
                    text = "知识图谱还在准备中",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSoft,
                )
            }
            renderError != null -> {
                Text(
                    text = renderError.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSoft,
                )
            }
            else -> {
                WebViewGraphCanvas(
                    payload = payload,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(364.dp),
                    onNodeClick = onSelectNode,
                    onRenderError = { renderError = it },
                )
            }
        }

        viewport.centerNode?.let { centerNode ->
            ConceptGraphInfoCard(
                centerNode = centerNode,
                relationFromPreviousCenter = relationFromPreviousCenter,
                visibleNeighborCount = viewport.neighbors.size,
                hiddenNeighborCount = viewport.hiddenNeighborCount,
            )
        }
    }
}
```

In the same task, remove the current production-path use of:

```kotlin
private fun buildConceptGraphCenterPosition(...)
private fun buildConceptGraphNeighborLayouts(...)
@Composable private fun ConceptGraphDotNode(...)
@Composable private fun ConceptGraphViewportCanvas(...)
```

Keep them out of the production path rather than trying to dual-run both renderers.

- [ ] **Step 4: Re-run the renderer integration tests and Android test compile**

Run:

```bash
JAVA_HOME=$HOME/.jdks/temurin-21 ./gradlew --no-daemon   :app:testDebugUnitTest --tests com.mindflow.app.ui.screens.flow.KnowledgeGraphScreenTest   :app:compileDebugAndroidTestKotlin
```

Expected: PASS for the screen unit tests and successful Android test Kotlin compilation.

- [ ] **Step 5: Commit the screen integration**

```bash
git add app/src/main/java/com/mindflow/app/ui/screens/flow/KnowledgeGraphScreen.kt         app/src/test/java/com/mindflow/app/ui/screens/flow/KnowledgeGraphScreenTest.kt         app/src/androidTest/java/com/mindflow/app/ui/screens/flow/KnowledgeGraphScreenInstrumentedTest.kt
git commit -m "feat: render concept graph with WebView canvas"
```

## Task 4: Verify Fallbacks, Real-Device Behavior, and Final Cleanup

**Files:**
- Modify: `app/src/main/java/com/mindflow/app/ui/screens/flow/WebViewGraphCanvas.kt`
- Modify: `app/src/main/assets/graph/graph.js`
- Modify: `app/src/androidTest/java/com/mindflow/app/ui/screens/flow/KnowledgeGraphScreenInstrumentedTest.kt`

- [ ] **Step 1: Add the failing fallback and shell-preservation checks**

```kotlin
@Test
fun `heatmap card remains above graph card after renderer swap`() {
    composeRule.setContent {
        MindFlowTheme {
            KnowledgeGraphScreen(
                snapshot = connectedSnapshot(),
                notes = emptyList(),
                onOpenNote = {},
            )
        }
    }

    composeRule.onNodeWithText("记录热度").assertIsDisplayed()
    composeRule.onNodeWithTag(KnowledgeGraphCanvasTag).assertIsDisplayed()
    composeRule.onNodeWithTag(KnowledgeGraphInfoPanelTag).assertIsDisplayed()
}
```

- [ ] **Step 2: Tighten the renderer fallback behavior**

```kotlin
override fun onReceivedError(
    view: WebView?,
    request: WebResourceRequest?,
    error: WebResourceError?,
) {
    if (request?.isForMainFrame != false) {
        val message = error?.description?.toString().orEmpty().ifBlank { "图谱加载失败" }
        renderState.onRenderError(message)
        onRenderError(message)
    }
}
```

```javascript
window.renderGraph = function renderGraph(payload) {
  try {
    const graph = ensureGraph();
    if (!payload || !payload.centerNodeId) {
      graph.elements().remove();
      return;
    }
    // existing element replacement
  } catch (error) {
    postToAndroid({ type: 'renderError', message: String(error && error.message ? error.message : error) });
  }
};
```

- [ ] **Step 3: Run the full targeted verification set**

Run:

```bash
JAVA_HOME=$HOME/.jdks/temurin-21 ./gradlew --no-daemon   :app:testDebugUnitTest --tests com.mindflow.app.ui.screens.flow.WebViewGraphContractTest   --tests com.mindflow.app.ui.screens.flow.WebViewGraphCanvasStateTest   --tests com.mindflow.app.ui.screens.flow.KnowledgeGraphScreenTest
```

Then run:

```bash
JAVA_HOME=$HOME/.jdks/temurin-21 ./gradlew --no-daemon :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin :app:assembleRelease
```

Expected: all targeted unit tests pass, Kotlin compile passes, Android test compile passes, and `assembleRelease` succeeds.

- [ ] **Step 4: Run real-device validation on the connected phone**

Run:

```bash
/home/dpthinker/.local/bin/adb install -r app/build/outputs/apk/release/app-release.apk
/home/dpthinker/.local/bin/adb shell input keyevent KEYCODE_HOME
```

Then manually verify on device:

- the heatmap card is still above the graph card
- the graph looks like nodes and edges rather than radial label rows
- pinch zoom and pan work
- tapping a neighbor recenters correctly
- no node is clipped at the card boundary

- [ ] **Step 5: Commit the final renderer polish**

```bash
git add app/src/main/java/com/mindflow/app/ui/screens/flow/WebViewGraphCanvas.kt         app/src/main/assets/graph/graph.js         app/src/androidTest/java/com/mindflow/app/ui/screens/flow/KnowledgeGraphScreenInstrumentedTest.kt
git commit -m "fix: polish Cytoscape graph renderer fallback"
```

## Self-Review

### Spec Coverage

- Replace custom Compose renderer: covered by Tasks 2 and 3.
- Keep page shell and heatmap card: covered by Tasks 3 and 4.
- Keep Compose-owned state: covered by Tasks 1 and 3.
- Local offline assets: covered by Task 2.
- Error handling and fallback: covered by Task 4.
- Testing and real-device validation: covered by Task 4.

### Placeholder Scan

The plan does not rely on `TODO`, `TBD`, or "implement later" placeholders. Each task includes exact files, explicit commands, and representative code for the work.

### Type Consistency

The plan uses one stable vocabulary throughout:

- `WebGraphPayload`
- `WebGraphNode`
- `WebGraphEdge`
- `GraphBridgeEvent`
- `WebViewGraphRenderState`
- `WebViewGraphCanvas`

No later task renames these types.
