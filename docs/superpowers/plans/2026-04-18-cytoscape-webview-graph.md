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
adb install -r app/build/outputs/apk/release/app-release.apk
adb shell input keyevent KEYCODE_HOME
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

## Design Review Addendum

### Overall Rating

Initial design completeness for the graph experience: `5/10`

Target after this review: `9/10`

The renderer swap is directionally correct, but the visual and interaction rules were too loose. Without stronger constraints, the graph drifts back into a circular demo layout, a label ring, or a generic relationship widget. The fix is not another round of visual polish. The fix is to hard-code the product behavior and first-frame hierarchy.

### Pass Scores

| Pass | Before | After | Notes |
|------|--------|-------|-------|
| Information Architecture | 4/10 | 9/10 | First-frame composition is now center-clustered, not full-ring |
| Interaction State Coverage | 5/10 | 9/10 | Sparse, loading, and failure states are now explicitly separated |
| User Journey & Emotional Arc | 4/10 | 9/10 | The graph now reads as guided exploration, not renderer refresh |
| AI Slop Risk | 5/10 | 9/10 | Semantic hierarchy replaces decorative graph styling |
| Design System Alignment | 6/10 | 9/10 | The graph now aligns with MindFlow's "idea growth" language |
| Responsive & Accessibility | 5/10 | 9/10 | Gesture priority, hit targets, and non-visual summary are specified |
| Unresolved Decisions | 3 major gaps | 0 deferred | Key behavior choices are now fixed below |

### Locked Design Decisions

#### 1. First Frame Composition

- The graph card must open as a **center cluster**, not a complete circle or orbit.
- The first frame must show only the current center node plus the top `3-5` direct neighbors.
- Secondary and hidden neighbors do not appear in the first frame.
- Hidden neighbors are represented only by a graph-below secondary action: `还有 N 个关联知识点`.

#### 2. Primary Neighbor Selection

- The first-frame `3-5` neighbors are selected by:
  1. relation strength first
  2. structural stability second
  3. recent activity only as a light tie-breaker
- The selection must be deterministic for the same graph state.
- Do not randomize visible neighbors to create "variation".

#### 3. Sparse State

- Sparse graph state still renders the graph.
- If only the center node or a very small number of edges are available, keep the visible graph and explain that connections are still growing.
- Sparse state must not visually resemble renderer failure.

#### 4. Loading / Recenter State

- When the user switches center nodes, the previous graph remains visible in a de-emphasized state.
- The new center takes over smoothly.
- Do not blank the graph or flash a skeleton between center switches.
- Use a minimal "updating" indicator only if needed, and keep it visually secondary.

#### 5. Failure State

- Real renderer failure must look clearly different from sparse state.
- Failure state lives inside the graph card and includes:
  - a short plain-language failure line
  - a retry action
  - the existing explanation and center-switching area kept available below

#### 6. Product Language

- Rename the card title from `信息图谱` to `思路连接`.
- Supporting copy should describe relationships as something that is **growing** or **starting to connect**.
- Keep the tone restrained. Do not drift into brand poetry or technical dashboard language.

#### 7. Visual Hierarchy

- The graph should feel like a thought structure, not a generic graph control.
- Use semantic hierarchy, not decoration:
  - center node most stable and most prominent
  - primary neighbors lighter but still clear
  - weak edges and secondary nodes lower contrast
- Avoid "every node gets its own color" styling.
- Replace hash-driven multicolor emphasis with a restrained single color family plus lightness hierarchy.

#### 8. Label Policy

- Labels are not the default first-frame hero.
- Show full labels for:
  - the center node
  - a small number of primary neighbors
- Secondary neighbors should either hide labels or use very short abbreviated labels until focused.
- The first impression must remain "graph first, labels second".

#### 9. Mobile Gesture Policy

- Single-finger drag defaults to page scrolling, not graph panning.
- The graph may take over gesture handling only when the user is clearly operating the graph, such as explicit focus mode or two-finger zoom.
- The graph must not feel sticky or compete with the page scroll.

#### 10. Tap Targets

- Nodes may stay visually small.
- Actual tap hit areas must be at least `44dp`.
- Do not require zoom before normal node selection becomes reliable.

#### 11. Non-Visual Summary Path

- The explanation area below the graph remains mandatory.
- It must carry a compact non-visual summary of:
  - current center node
  - one primary relationship summary
  - one next-step hint or expansion hint
- The graph must not be the only place where meaning exists.

#### 12. Graph-Below Summary Shape

- The explanation area below the graph should stay light.
- It is fixed as a three-part summary:
  1. current center node
  2. one-line primary relationship summary
  3. one next-step or expand/switch hint
- Do not let this section grow into a second heavy information card.

#### 13. Recenter Motion Model

- When a neighbor becomes the new center:
  - preserve broad direction sense from the prior frame
  - allow local re-layout around the new center
- Do not fully freeze node positions.
- Do not fully regenerate the scene as an unrelated fresh composition.

#### 14. End-of-Branching Tone

- When a node has no further meaningful expansion, use a calm stopping line.
- Preferred behavior: "这个知识点当前先长到这里。你可以切到别的点继续看。"
- Avoid flat dead-end phrasing such as "没有更多连接".

### Required Implementation Adjustments

The implementation plan above should be interpreted with these extra constraints:

1. `WebViewGraphContract.kt`
   - replace full-ring neighbor placement with a center-cluster placement strategy
   - keep first-frame neighbors capped at `3-5`
   - add deterministic hidden-neighbor accounting for the secondary action below the graph

2. `graph.js`
   - stop treating all visible nodes as equally labeled
   - apply single-family restrained styling with semantic lightness
   - separate visual node size from actual hit area
   - support a smooth recenter transition that preserves direction sense

3. `KnowledgeGraphScreen.kt`
   - rename `信息图谱` to `思路连接`
   - replace graph intro and fallback text with growth-oriented copy
   - keep the graph-below summary light and fixed-shape
   - keep hidden-neighbor disclosure below the graph, not inside it

4. Mobile behavior
   - do not let graph panning hijack page scrolling by default
   - retain large hidden tap zones for nodes

### Not in Scope

- Reworking the upstream concept graph data-generation pipeline in this review
- Replacing the heatmap card
- Adding freeform graph editing or graph authoring
- Redesigning the whole graph page shell outside the graph card and its summary

### What Already Exists

- The page shell is already correct in broad structure:
  - heatmap card above
  - graph card below
  - explanation and switch-center affordances in Compose
- `PanelCard` and `SectionHeader` are the right shell primitives to keep
- The `WebView + Cytoscape.js` rendering split is still the correct architectural direction

### Approved Mockups

No visual mockups were generated during this review because the gstack design binary was not available in this environment.

## GSTACK REVIEW REPORT

| Review | Trigger | Why | Runs | Status | Findings |
|--------|---------|-----|------|--------|----------|
| CEO Review | `/plan-ceo-review` | Scope & strategy | 0 | — | — |
| Codex Review | `/codex review` | Independent 2nd opinion | 0 | — | — |
| Eng Review | `/plan-eng-review` | Architecture & tests (required) | 1 | CLEAR | prior review exists on branch; no new eng gate opened by this design pass |
| Design Review | `/plan-design-review` | UI/UX gaps | 1 | CLEAR | score: 5/10 → 9/10, 14 decisions locked |
| DX Review | `/plan-devex-review` | Developer experience gaps | 0 | — | — |

**UNRESOLVED:** 0
**VERDICT:** DESIGN REVIEW CLEARED — implementation should follow the locked decisions above.
