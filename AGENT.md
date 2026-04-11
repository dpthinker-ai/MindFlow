# MindFlow Agent Guide

## Mission

MindFlow exists to help the user:

- catch fragile ideas before they disappear
- recognize recurring threads across time
- create useful collisions between old and new thoughts
- grow mature ideas into reusable assets

MindFlow is an `idea incubator`, not a generic AI note app.

## Product invariants

### 1. Capture first

- the shortest path to saving a thought stays obvious
- no mandatory structure before save
- speed beats organization at the moment of capture

### 2. Incubation over inspection

The product should create motion:

- rescue
- expand
- collide
- verify
- reuse

It should not feel like a dashboard to inspect.

### 3. Local-first memory

- the maintained local knowledge layer is the long-term memory source of truth
- raw sources are append-only
- app-facing summaries and insights should read from maintained memory when available

### 4. Cloud is an upgrade path

- local model use is the default
- cloud use should be deliberate and legible
- no silent cloud escalation for background maintenance
- cloud-derived output must file back into local memory with provenance

### 5. One product role

AI should feel like one coherent system role:

- capture help
- memory maintenance
- thread detection
- collision generation
- asset shaping

Not a bag of disconnected AI tricks.

## User-facing object model

Prefer product language built around these objects:

- `Spark`
- `Thread`
- `Collision`
- `Asset`
- `Gap`

Do not lead with backend maintenance terms on user-facing surfaces.

## Primary surfaces

- `记录`: fast capture
- `Flow`: incubation feed
- `查询`: directed thinking over maintained memory
- `图谱`: shape, hubs, isolation, missing links
- `设置`: model controls, sync, diagnostics

## Flow rules

Flow is not:

- an AI brief
- a system status screen
- a direction dashboard
- a maintainer console

Flow is:

- the home of the incubator
- a readable feed of what is alive in the user's idea system

Preferred section logic:

1. fragile spark worth rescuing
2. recurring thread worth naming
3. collision worth trying
4. asset that has matured
5. gap worth feeding

Avoid labels like:

- `当前知识`
- `库存`
- `维护状态`
- `方向资产`

## Query rules

Query should feel like a verb, not a chatbot.

Preferred intents:

- `继续养`
- `撞一下`
- `反驳它`
- `帮我抽象`
- `帮我拉成方案`
- `帮我找证据`

Good query results should be promotable into durable memory.

## Graph rules

The graph should explain pressure and shape, not merely draw topology.

Prefer:

- hubs
- isolated nodes
- densifying clusters
- missing links

Do not default to a raw global note dump.

## Engineering rules

- keep the local database as the runtime interaction layer
- keep the maintained markdown/wiki layer as the compounding long-term memory layer
- local on-device maintenance should ingest sources, update maintained pages, append index/log state, and export readable summaries back to the app
- use `LiteRT-LM` for the on-device Gemma 4 path
- local build and release verification require `JDK 21`
- new automation should be low-frequency and battery-aware
- release behavior matters more than debug-only polish

## Trust boundary

When implementing AI flows, preserve these rules:

- raw capture stays local by default
- background maintenance stays local by default
- cloud use must be explicit or strongly implied by the user's chosen action
- users should be able to understand what left the device and why
- any cloud-assisted result worth keeping must write back locally

## Anti-patterns

Do not steer the product back toward:

- daily AI brief as the primary value proposition
- direction-first framing for every screen
- dashboard language at the top of Flow
- model selection UI in the main workflow
- cloud output that lives only in chat history
