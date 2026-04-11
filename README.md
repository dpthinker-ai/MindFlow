# MindFlow

MindFlow is a native Android `idea incubator`.

It is built for one job:

- capture strange, early, unfinished thoughts fast
- keep them alive over time
- surface recurring threads and promising collisions
- turn mature patterns into reusable personal assets

This is not a generic note app with AI bolted on.

## Product thesis

MindFlow has two layers:

1. `frontstage`: an idea incubator for capture, recall, collision, and growth
2. `backstage`: a local-first `LLM Wiki` maintainer that keeps long-term memory coherent

The app should feel like:

- a pocket lab notebook
- a long-memory thinking companion
- a place where weak signals compound

It should not feel like:

- an AI dashboard
- a model playground
- a direction tracker
- a wiki admin console

## Core product loop

1. capture a spark
2. let the system absorb and connect it
3. surface a live thread
4. deepen or collide it
5. grow it into an asset
6. feed the next missing material

## Navigation

MindFlow now organizes around five product surfaces:

- `记录`
- `Flow`
- `查询`
- `图谱`
- `设置`

### 记录

Fast capture beats forgetting.

Primary inputs:

- quick text
- voice thought
- pasted link
- screenshot
- clipped excerpt

### Flow

The home feed for idea incubation.

It should answer:

- which spark should not be lost
- which thread keeps recurring
- which two points are worth colliding
- what has matured enough to reuse
- what to feed next

### 查询

Directed thinking over the maintained knowledge layer.

The user should feel they are doing one of these:

- `继续养`
- `撞一下`
- `反驳它`
- `帮我抽象`
- `帮我拉成方案`
- `帮我找证据`

### 图谱

Not a node dump.

It should show:

- what is becoming a hub
- what is still isolated
- which cluster is getting denser
- which missing edge looks valuable

### 设置

All system-facing controls stay here:

- local model management
- cloud model/provider configuration
- sync and export
- maintenance diagnostics
- performance and battery tradeoffs

## Model strategy

MindFlow supports both:

- `Local Gemma 4`
- `Cloud models`

This is one product, not two product modes.

### Local Gemma 4

Default layer for:

- capture enrichment
- old-note recall
- routine maintenance
- low-latency synthesis
- privacy-sensitive workflows

### Cloud models

Escalation layer for:

- harder critique
- deeper cross-domain synthesis
- stronger reframing
- richer proposal shaping

### Routing rule

The user should choose an intention, not a model.

The system routes the work.

### Trust boundary

This is non-negotiable:

- local is the default for raw capture, recall, and maintenance
- raw sources must not be silently sent to the cloud during background maintenance
- cloud use must be user-invoked or clearly signaled by the action the user chose
- cloud-derived output must be marked with provenance and filed back into local memory
- the product must remain useful with cloud disabled

## Current implementation status

Already in the product:

- fast note capture and editing
- voice-to-text capture
- share-to-capture
- widget and launcher shortcuts
- search and filtering
- thread and related-note structure
- reminder flows
- local knowledge-layer maintenance
- local `Gemma 4 E4B` download and runtime support
- local runtime upgraded to `LiteRT-LM`
- cloud model configuration
- local-first knowledge export back into app surfaces

The current codebase still contains older `AI brief / direction dashboard` language in some places.
The current phase is to realign those surfaces to the idea-incubator thesis above.

## Local-first knowledge layer

The maintained knowledge layer stores durable objects like:

- concepts
- questions
- methods
- experiments
- evidence
- conclusions
- links
- logs and index pages

Raw sources stay append-only.

High-value query results and cloud-assisted outputs must file back into this layer, or they do not compound.

## Build

The local Gemma 4 path now uses `LiteRT-LM`.

Requirements:

- Android SDK configured in `local.properties`
- `JDK 21` for local build and release verification

Example:

```properties
sdk.dir=/your/android/sdk
```

```bash
export JAVA_HOME=/path/to/jdk-21
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew --no-daemon assembleRelease
```
