# MindFlow Design

## Product Thesis

MindFlow is not a note app with AI attached.

MindFlow is an `idea incubator`:

- you capture strange, early, unfinished thoughts
- the system keeps them alive
- related fragments get linked over time
- promising threads get pushed into clearer directions
- mature results become reusable personal assets

`LLM Wiki` is the maintenance engine behind this.

It is not the face of the product.

## The Product We Are Actually Building

### What the user wants

The user does not open MindFlow to inspect a knowledge base.

The user opens MindFlow to answer questions like:

1. Which recent idea should not be lost?
2. What have I secretly been thinking about for weeks?
3. Which two old ideas are now worth colliding?
4. What has matured enough to reuse?
5. What should I feed next if I want something to grow?

### What MindFlow is

MindFlow should feel like:

- a pocket idea nursery
- a long-memory thinking companion
- a place where fragments turn into patterns
- a place where patterns turn into methods, bets, and conclusions

### What MindFlow is not

It must not feel like:

- an AI dashboard
- a model playground
- a wiki admin console
- a note analytics screen
- a project status page

## Core System Model

MindFlow has three layers.

### 1. Source layer

This is what the user gives the system:

- text notes
- voice thoughts
- screenshots
- article clips
- links
- reflections
- validations

This layer should stay messy and low-friction.

### 2. Maintenance layer

This is where the local `LLM Wiki` engine works.

It maintains:

- concepts
- questions
- methods
- experiments
- evidence
- conclusions
- contradictions
- links
- index and logs

This layer is the source of truth.

### 3. Incubation layer

This is the user-facing layer.

It should present the knowledge base as five higher-level incubation objects:

1. `Spark`
2. `Thread`
3. `Collision`
4. `Asset`
5. `Gap`

These are not separate storage systems.
They are readable views over the maintained wiki.

## Frontstage and Backstage

### Backstage: `LLM Wiki`

The wiki engine should:

- ingest new material
- maintain structured knowledge pages
- connect old and new material
- identify conflicts and weak links
- file valuable query results back into the wiki
- keep the knowledge layer coherent over time

### Frontstage: `Idea Incubator`

The app should help the user:

- catch sparks quickly
- notice recurring threads
- create promising collisions
- recognize what has matured
- decide what to feed next

This is the fusion.

`LLM Wiki` supplies continuity.
MindFlow supplies meaning and momentum.

## Model Strategy

MindFlow has two model tiers:

1. `Local Gemma 4`
2. `Cloud model`

This should change system behavior, not user-facing product identity.

### Local Gemma 4

This is the default model layer.

Use it for:

- always-on maintenance
- quick capture enrichment
- local summarization
- finding old related fragments
- routine wiki updates
- low-latency recall
- privacy-sensitive material

The local model should feel like the app's breathing.
Quiet, persistent, always available.

### Cloud model

This is the escalation layer.

Use it for:

- deep cross-domain synthesis
- stronger critique
- harder reframing
- more ambitious collisions
- turning a promising thread into a richer proposal
- higher-quality language shaping when the user asks for it

The cloud model should feel like a deliberate upgrade move, not the default runtime.

### Routing rule

The user should almost never choose between "local" and "cloud" from the main workflow.

The user should choose an intention:

- `继续养`
- `撞一下`
- `反驳它`
- `拉成方案`
- `再挖深一点`

The system should route the task to the appropriate model tier.

### File-back rule

No matter which model produced the insight, valuable output must return to the local knowledge layer.

Cloud output that does not file back becomes dead chat residue.

That is not acceptable.

### Trust boundary

These rules should be explicit in both product behavior and UI copy:

- local is the default for raw capture, recall, and background maintenance
- raw sources must not be silently sent to the cloud during maintenance passes
- cloud escalation must be user-invoked or clearly implied by the action the user chose
- cloud-derived output should carry provenance when it becomes durable
- the product should remain useful when cloud access is disabled

## Primary Product Loop

The core loop is:

1. capture a spark
2. let the system maintain and relate it
3. surface a promising thread
4. deepen or collide it
5. mature it into an asset
6. feed the next missing material

This loop matters more than any single screen.

## Primary Navigation

The app should be organized around five tabs:

1. `记录`
2. `Flow`
3. `查询`
4. `图谱`
5. `设置`

This is still a good shell.

The change is what each tab means.

## 1. 记录

### Job

Catch raw material before it disappears.

### Product promise

`10 seconds from thought to saved spark.`

### Inputs

- quick text
- voice note
- pasted link
- screenshot
- clipped excerpt

### Interaction rules

- default to a single primary input field
- never require manual tagging before save
- metadata is optional and secondary
- saving should feel instant
- after save, the user should see that the spark has been safely absorbed

### Useful post-save actions

- `补一句`
- `看看像什么旧东西`
- `先放着`

Not:

- category forms
- complex templates
- schema editing

## 2. Flow

### Job

Show the user what is alive in their idea system right now.

This is not a knowledge dashboard.
This is the incubator feed.

### Primary sections

The Flow home should have exactly five sections:

1. `别丢这颗火花`
2. `你其实一直在想这个`
3. `把这两个点撞一下`
4. `已经长成了`
5. `再喂什么`

### Section meaning

#### 1. 别丢这颗火花

Show one recent thought that is fragile but promising.

It should answer:

- which new idea deserves rescue
- why it is more interesting than it first looked

Preferred signals:

- novelty
- repeat resonance with older material
- unfinished but high-upside thinking

#### 2. 你其实一直在想这个

Show one recurring `Thread`.

It should answer:

- what hidden theme keeps reappearing across weeks or folders

This is how the app reveals long memory.

#### 3. 把这两个点撞一下

Show one `Collision`.

It should answer:

- which two ideas now belong in the same frame
- why the connection could create something new

This is the center of the product's innovation value.

#### 4. 已经长成了

Show one `Asset`.

It should answer:

- what is now stable enough to reuse

Examples:

- a conclusion
- a method
- an experiment pattern
- a durable framing

This section should feel earned, not generated.

#### 5. 再喂什么

Show one `Gap`.

It should answer:

- what kind of material is missing if the user wants one thread to grow

This is not task management.
It is growth guidance.

### Flow writing style

Flow should sound like an editor or research partner.

Prefer:

- `这条最近别丢`
- `你其实反复回到了这个问题`
- `这两个点现在值得撞一下`
- `这条已经长成可复用的东西了`
- `如果你想把它养大，下一口该喂这个`

Avoid:

- `当前知识`
- `库存`
- `维护状态`
- `知识对象`
- `模型已完成`
- `方向资产`

## 3. 查询

### Job

Let the user work on an idea intentionally.

Query is not generic chat.
It is directed thinking over the maintained knowledge layer.

### Primary verbs

The user should feel they are doing one of these:

- `继续养`
- `撞一下`
- `反驳它`
- `帮我抽象`
- `帮我拉成方案`
- `帮我找证据`

### Query rule

High-value query results should be promotable into:

- Thread notes
- Collision candidates
- Assets
- Gap suggestions

That promotion should be first-class.

## 4. 图谱

### Job

Show pressure, clusters, and missing structure.

The graph is not there to impress.
It is there to reveal shape.

### The graph page should answer

1. what is becoming a hub
2. what is still isolated
3. which cluster is getting denser
4. which missing edge looks most valuable

### Default graph lens

Start from:

- threads
- collisions
- assets
- gaps

Not from a raw dump of every note node.

## 5. 设置

### Job

Contain the system-facing controls that should not leak into the main product flow.

This includes:

- local model management
- cloud provider configuration
- download status
- performance modes
- sync and export
- maintenance diagnostics

The app needs these controls.
The homepage does not.

## User-Facing Object Model

The product should teach the user these five objects over time:

### `Spark`

A fresh thought that may be nothing yet, but should not be dropped.

### `Thread`

A recurring line of thinking that keeps resurfacing.

### `Collision`

A proposed connection between ideas that may produce something new.

### `Asset`

A thought that has matured into something reusable.

### `Gap`

A meaningful missing piece blocking growth.

This object model is far more usable than exposing raw wiki maintenance concepts on the home screen.

## Mobile UX Principles

### 1. Capture must beat forgetting

The first battle is not organization.
It is memory loss.

Therefore:

- app open to capture must be fast
- the first input affordance must be obvious
- no mandatory structure before save
- interruptions must not destroy drafts

### 2. The home screen must create motion, not inspection

The user should leave Flow wanting to do one next thing:

- rescue
- expand
- collide
- verify
- reuse

If Flow only informs, it is too static.

### 3. One-thumb reading, one-tap action

Each Flow section should support one obvious next action.

Examples:

- `继续养`
- `撞一下`
- `反驳`
- `展开`
- `存成资产`

Avoid multi-action clutter inside each card.

### 4. Keep the maintenance engine mostly invisible

The user should feel care, not machinery.

Visible signs of maintenance are acceptable only when they help trust:

- `已吸收`
- `已连到旧笔记`
- `已整理进知识层`

Do not expose:

- routing jargon
- maintenance phase labels
- token-like system counters

### 5. Respect mobile energy budgets

Local AI on mobile must feel steady.

That means:

- fast local actions first
- heavy work deferred or backgrounded
- cloud escalation used deliberately
- no front-page dependency on expensive generation

## Visual Direction

### Tone

- thoughtful
- alive
- private
- editorial
- slightly experimental

### Visual archetype

The app should feel closer to:

- a field notebook
- a research studio
- a personal lab book

Not:

- a sci-fi AI cockpit
- a productivity dashboard
- a generic chatbot shell

### Hierarchy

The most important visual contrast should separate:

- a living spark
- a recurring thread
- a mature asset

not different backend object types.

### Motion

Motion should express:

- something got absorbed
- something connected
- something strengthened

Not:

- "the AI is thinking theatrically"

## Anti-Patterns

Do not reintroduce these:

1. model selection on the main screen
2. homepage language centered on `today's AI summary`
3. dashboard labels like `库存` or `当前知识`
4. direction-first framing that makes everything about the current project
5. generic chat as the primary interface
6. graph screens that dump structure without interpretation
7. cloud output that never writes back into local memory

## Success Criteria

The design is working if a user opens MindFlow and feels:

1. my strange ideas are safe here
2. this app remembers more than I do
3. it can show me patterns I would miss alone
4. it helps me turn fragments into something stronger
5. local and cloud intelligence both help me, without forcing me to manage them

The design has failed if the user instead feels:

- I am reading a system dashboard
- I need to understand the architecture to use the app
- the model is the product
- this is just another note app with AI summaries

## Design Decision

MindFlow should be designed as:

`an idea incubator powered by a local-first LLM Wiki, with cloud intelligence as an upgrade path`

That is the product.
