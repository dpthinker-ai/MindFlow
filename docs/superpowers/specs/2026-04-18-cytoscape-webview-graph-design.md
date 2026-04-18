# Cytoscape WebView Graph Design

## Background

The current concept graph page mixes two different problems:

1. Data generation quality in the concept graph pipeline.
2. Graph rendering and interaction quality in the UI.

The current UI implementation renders the graph directly in Compose inside [KnowledgeGraphScreen.kt](/home/dpthinker/MindFlow/app/src/main/java/com/mindflow/app/ui/screens/flow/KnowledgeGraphScreen.kt). It manually manages node positions, label placement, curved edges, and viewport spacing. This has created an expensive iteration loop:

- every visual issue requires hand-tuning slot coordinates and offsets
- graph layouts degrade quickly on real devices
- the result still looks closer to a radial label layout than a graph engine

At the same time, the data model introduced in concept graph v1 remains valid:

- `ConceptGraphSnapshot` is still the source of truth
- Compose still owns business state such as selected center node, relation explanation, and expand-more behavior
- only the rendering layer is being replaced

This design replaces the custom Compose graph canvas with a local offline `WebView + Cytoscape.js` renderer while preserving the existing page shell and interaction model.

## Goals

1. Replace the current custom graph canvas with a mature graph rendering engine.
2. Keep the page shell intact, including the heatmap card, relation explanation area, and switch/expand controls.
3. Preserve the existing Compose-owned business state and viewport selection logic.
4. Run fully offline from APK-bundled assets, with no CDN dependency.
5. Make the graph look and behave like a real node-link network on mobile:
   - nodes and edges are primary
   - labels are secondary
   - pan and zoom are supported
   - layout is stable and center-focused

## Non-Goals

1. Do not redesign the concept graph data pipeline in this change.
2. Do not move business logic into JavaScript.
3. Do not rebuild the full graph page in HTML.
4. Do not add graph editing or freeform node dragging in v1.
5. Do not remove the heatmap card or surrounding Compose UI.

## Product Scope

Only the graph canvas inside the graph card is replaced.

The following remain in Compose:

- page title and shell
- heatmap card above the graph
- relation explanation area
- center switching UI
- expand-more UI and counts
- empty-state and fallback state handling

The following move to WebView rendering:

- node placement
- edge routing
- graph pan and zoom
- visual selection state inside the canvas

## Architecture

The new rendering architecture has four main pieces.

### 1. `KnowledgeGraphScreen`

`KnowledgeGraphScreen` remains the screen-level owner of:

- the current `ConceptGraphSnapshot`
- the current `ConceptGraphViewport`
- the selected center node id
- relation explanation state
- expand-more state
- empty and fallback states

It no longer computes pixel positions, label directions, or edge curves.

### 2. `ConceptGraphViewport`

`ConceptGraphViewport` remains the Compose-side view model for the currently visible local graph slice.

It still defines:

- current center node
- first-hop neighbors
- visible edges for the local slice
- switchable nodes
- hidden neighbor counts

This object remains the boundary between business state and renderer input.

### 3. `WebViewGraphCanvas`

`WebViewGraphCanvas` is a new Compose component that embeds an Android `WebView`.

Its job is limited to:

- loading a local HTML graph page from APK assets
- converting the current viewport into a web payload
- sending render payloads into the web page
- receiving graph click events back from JavaScript

It does not own graph state, persistence, or domain logic.

### 4. `GraphWebMessageBridge`

`GraphWebMessageBridge` is the bridge between JavaScript and Compose.

It is responsible for:

- receiving `nodeClick` events from the web layer
- validating the event payload
- forwarding only the resolved `conceptId` back into Compose callbacks

It is not allowed to mutate repository or planner state directly.

## Data Flow

Data flow remains strictly one-directional.

1. The existing concept graph pipeline produces `ConceptGraphSnapshot`.
2. Compose builds the current `ConceptGraphViewport`.
3. `WebViewGraphCanvas` converts that viewport into a lightweight web payload.
4. JavaScript renders the payload with Cytoscape.
5. The user taps a node in the WebView.
6. JavaScript sends a `nodeClick` event containing only the node id.
7. Compose receives the event and updates the current center node.
8. Compose rebuilds the viewport.
9. The updated viewport is pushed back into the WebView renderer.

JavaScript is therefore a renderer, not a business-state source.

## Asset Strategy

All web renderer assets are bundled locally in the APK.

Planned asset structure:

- `app/src/main/assets/graph/index.html`
- `app/src/main/assets/graph/graph.css`
- `app/src/main/assets/graph/graph.js`
- `app/src/main/assets/graph/vendor/cytoscape.min.js`

This renderer must work fully offline. It must not require:

- CDN-hosted JavaScript
- remote stylesheet loading
- network connectivity

## Rendering Model

The graph renderer only receives the currently visible local graph slice, not the full global graph.

The payload must include:

- `version`
- `centerNodeId`
- `nodes[]`
- `edges[]`

Each node payload should include:

- `id`
- `label`
- `isCenter`
- `accentColor`
- optional semantic weight fields used for styling

Each edge payload should include:

- `id`
- `source`
- `target`
- `relationType`
- `confidence`
- optional selection metadata

The renderer does not infer missing nodes or edges. It renders only the payload it receives.

## Layout Strategy

The first version does not use a fully free force-directed layout.

Instead, it uses a stable center-anchored layout:

- the current center node is pinned near the visual center of the canvas
- first-hop neighbors are laid out around the center using Cytoscape layout support
- layout is adjusted for collision avoidance and visual spacing
- repeated renders for the same viewport should stay visually stable

This produces a graph that feels like a network without the instability of a fully unconstrained simulation.

The graph must support:

- pan
- pinch zoom
- tap selection

The graph must not support:

- user-driven node drag repositioning
- in-canvas expand buttons
- direct mutation of graph structure

## Visual Design

The graph should follow these principles:

- the center node is visibly larger and more prominent
- neighboring nodes are smaller and visually quieter
- edges are neutral and do not dominate the view
- labels are attached to nodes but must not look like large card rows
- the canvas should read as a node-link network, not a labeled radial list

The Compose shell retains the rest of the product styling. The WebView renderer only owns graph-specific visual treatment.

## WebView Integration

`WebViewGraphCanvas` loads the local graph page once and keeps it alive.

It must not reload the full page on every viewport update. Instead, Compose pushes updated graph payloads into the existing page through JavaScript evaluation.

The page lifecycle is:

1. WebView loads local `index.html`
2. JavaScript initializes Cytoscape and signals `viewportReady`
3. Compose sends the latest graph payload
4. JavaScript applies the payload and renders
5. Subsequent viewport changes only send updated render payloads

If Compose generates viewport updates before the page is ready, the latest pending payload should be buffered and flushed after readiness.

## Event Protocol

The bridge protocol uses JSON messages.

### Compose to JavaScript

Compose sends a render payload with:

- `version`
- `centerNodeId`
- `nodes`
- `edges`

### JavaScript to Compose

JavaScript initially only sends:

- `viewportReady`
- `nodeClick`
- `renderError`

`nodeClick` must contain:

- `type`
- `conceptId`

`renderError` may contain:

- `type`
- `message`

No other graph-side business events are required in v1.

## Business-State Ownership

Compose remains the single owner of:

- center selection
- relation explanation text
- expand-more state
- switchable-node state
- empty-state messaging

JavaScript remains the single owner of:

- graph scene rendering
- viewport pan and zoom
- node hit testing inside the graph

This boundary is strict. Business state must not move into JavaScript.

## Error Handling and Fallback

The graph card must never fail as a blank white panel.

Fallback rules:

1. If the WebView page fails to load, Compose shows a visible fallback message.
2. If JavaScript payload parsing fails, the error is surfaced through a bridge event and the Compose shell remains usable.
3. If the current center node has no edges, the page shows the existing empty-state behavior instead of pretending the graph is broken.
4. If the renderer is not ready yet, Compose keeps the latest payload and retries once ready.

Fallback UI should still preserve:

- graph card container
- explanation area
- switchable concept controls when available

## Migration Strategy

The replacement is done incrementally.

### Phase 1: Introduce the renderer shell

- add local graph assets
- add `WebViewGraphCanvas`
- add bridge plumbing
- keep current viewport logic untouched
- verify that a viewport can be rendered and node taps round-trip correctly

### Phase 2: Replace the custom Compose canvas

- remove the current custom canvas from the production path
- keep the surrounding card structure unchanged
- keep viewport construction logic and empty-state logic in Compose

### Phase 3: Polish the graph renderer

- tune Cytoscape styling
- tune fit and zoom behavior
- tune center-focused layout stability
- validate real-device appearance

## Testing Strategy

Testing is split into three levels.

### 1. Unit tests

Keep existing viewport tests and add:

- viewport-to-web-payload mapping tests
- bridge event parsing tests
- fallback-state tests for empty and invalid payloads

### 2. Android UI tests

Verify:

- the heatmap card still exists above the graph
- the graph card still exists
- empty state still renders when there are no relations
- relation explanation area still updates through Compose

### 3. Real-device validation

Validate on a physical device that:

- the graph looks like a network instead of a radial label list
- nodes are not clipped
- the canvas can pan and zoom
- tapping a neighbor changes the center correctly
- heatmap and explanation sections are still present

## Acceptance Criteria

The change is complete when all of the following are true:

1. The graph canvas is rendered by `WebView + Cytoscape.js`, not by the current custom Compose graph layout.
2. The page remains offline-capable with all renderer assets bundled locally.
3. The heatmap card remains above the graph card.
4. Compose still owns center selection, expand state, and explanation text.
5. Tapping a graph node updates the current center node correctly.
6. The graph visually reads as a node-link network on mobile.
7. The graph no longer depends on hard-coded slot geometry in the screen layer.

## Out of Scope Follow-Ups

The following may be considered later, but are not part of this change:

- richer relation gestures inside the graph
- long-press node actions
- second-hop inline expansion inside the canvas
- animated graph transitions between centers
- graph rendering reuse in other pages
