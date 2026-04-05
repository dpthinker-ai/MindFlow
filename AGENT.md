# MindFlow Agent Guide

## Mission
MindFlow is not just a note app. It is a personal AI action system for:

- capturing fleeting ideas quickly
- organizing thoughts into durable themes
- turning ideas into next actions
- improving work, health, and life outcomes over time

## Product Principles

1. Content first
- Title and content always stay visually primary.
- Metadata such as tags, folders, status, and timestamps should support the note, not overpower it.

2. Fast capture
- New input must be low friction.
- The shortest path to writing should stay obvious on mobile.

3. AI as augmentation
- AI should assist with topic extraction, tags, folder classification, polishing, and insight generation.
- AI should not silently override user edits.
- User intent always wins over automation.
- AI surfaces should feel like one product role, not many disconnected tools.
- Preferred AI layering:
  - record layer: AI 整理
  - today layer: 今日聚焦
  - direction layer: 当前判断 / 研究 / 执行
  - rhythm layer: reminder cadence and follow-up

4. Daily usefulness
- Every major feature should improve one of these loops:
- capture
- recall
- connect
- act

5. Calm but premium design
- The app should feel simple, direct, and polished.
- Avoid clutter, over-explaining UI, and decorative noise.

## Information Architecture

Core record fields:

- content
- topic
- tags
- status
- horizon
- folder
- createdAt
- updatedAt
- archive state

Primary surfaces:

- Home: capture, overview, recent notes
- Search: filters, tags, folders, result-first retrieval
- Heatmap: contribution-style activity view
- Editor: preview/edit, AI helpers, save flow
- Settings: AI, cloud backup, import/export

## AI Boundaries

AI is allowed to:

- extract topic
- extract tags
- classify folder
- polish content
- generate brief insight later

AI is not allowed to:

- overwrite manual topic, tags, or folder choices without explicit user action
- hide failure states
- block note saving

## Current Folder Model

Fixed top-level folders:

- work
- life
- project
- health

Notes:

- fitness is merged into health
- uncategorized notes remain valid and should be recoverable

## Design Rules

- Keep status bar readable on all light backgrounds.
- Prefer folded controls over long filter walls.
- Swipe actions should reveal buttons first, never perform destructive actions immediately.
- Share visuals should stay elegant and minimal.
- Secondary planning widgets such as time-bank data should stay supportive, never outrank capture.
- AI naming should stay consistent and low-noise across Flow, thread, editor, and reminders.
- AI results should reuse the same visual language across Flow, thread, and reminder copy: one source cue, one primary judgement, and a small set of supportive lines.

## Engineering Rules

- Keep local database as the runtime source of truth.
- Markdown remains the readable interchange format for export, import, and cloud backup.
- A future `MindFlow Knowledge Layer` should become the long-term knowledge asset layer:
  - the app database stays the runtime source of truth for mobile interactions
  - the knowledge layer stores persistent direction pages, concepts, evidence, verified conclusions, open questions, methods, experiments, and stage snapshots
  - it should be maintained by background agent workflows, not by foreground mobile UI logic
- New automation should be low-frequency and battery-aware.
- Release builds matter more than debug-only polish.

## Knowledge Layer

MindFlow should not become a heavy Obsidian clone.

The intended long-term split is:

- MindFlow App
  - fast capture
  - daily focus
  - direction follow-up
  - reminders and lightweight execution
- MindFlow Knowledge Layer
  - long-lived markdown knowledge base for your evolving knowledge system
  - direction pages as one view, not the whole model
  - concept pages
  - evidence pages
  - question pages
  - method / experiment pages
  - verified conclusions and stage snapshots

Knowledge Layer principles:

- raw sources are append-only and never silently rewritten
- the knowledge layer is a compounding artifact maintained by agents
- app-facing summaries must be exported back into MindFlow in a lightweight structured form
- current implementation starts with followed directions, but the long-term layer must extend to concepts, evidence, questions, methods, and experiments
- if the knowledge layer is unavailable, the app must continue working with local summaries and planners

## Near-Term Direction

MindFlow should evolve toward:

- daily AI brief
- weekly review
- idea-to-action extraction
- note linking and theme connection
- long-term personal innovation memory

Current status:

- daily AI brief: implemented
- weekly review: implemented
- idea-to-action extraction: implemented
- morning/evening reminder hooks: implemented
- faster capture entry points: first version implemented
- thread follow and curation: first version implemented
- voice capture in editor: first version implemented
- thread focus and lightweight promotion: first version implemented
- external voice capture entry: first version implemented
- widget-level voice capture: first version implemented
- external research enrichment: first version implemented
- stale note follow-up prompts: first version implemented
- thread-level weekly review: first version implemented
- reminder main tap deep link to Flow: implemented
- reminder main tap contextual note landing: implemented
- richer stale-note reasoning: first version implemented
- thread research structure and source handling: first version implemented
- thread-level action refinement: first version implemented
- research result capture back into threads: first version implemented
- thread-level review/action capture back into notes: first version implemented
- notification quick capture refinement: first version implemented
- richer stale-note reasoning with AI when useful: first version implemented
- thread-level follow-up actions for reminders: first version implemented
- thread-context capture persistence: first version implemented
- reminder-to-flow contextual focus: first version implemented
- thread action summaries inside Flow: first version implemented
- thread-level external research memory: first version implemented
- thread-level research-to-action summaries: first version implemented
- widget-level capture polish: first version implemented
- thread follow-up summaries outside the thread page: first version implemented
- thread research clustering: first version implemented
- richer note-to-thread capture hints: first version implemented
- richer research surfaces outside the thread page: first version implemented
- grouped validation loops for research themes: first version implemented
- lighter Flow-to-thread action handoff: first version implemented
- lighter capture-back from research loops: first version implemented
- thread-aware research-to-action carry-over in reminders: first version implemented
- richer capture-back from clustered research: first version implemented
- thread-aware research follow-up summaries: first version implemented
- reminder summaries that explain why a validation step matters now: first version implemented
- research-derived prompts that bridge from validation back into execution: first version implemented
- shared thread execution summaries across Flow / thread / reminders: first version implemented
- AI external perspective snapshots for directions: first version implemented
- thread page restructured into judgement / research / execution: first version implemented
- note horizons for short / medium / long follow-up: implemented
- direction-stage execution rhythm across Flow / thread / reminders: first version implemented
- home time bank planning card: first version implemented
- thread-level stage history: first version implemented
- research evidence stratification inside thread workspace: first version implemented
- horizon-aware reminder cadence: first version implemented
- direction assets surfaced from verified and grounded research: first version implemented
- local Knowledge Layer generation, raw-source ingest, and app-facing asset reflow: first version implemented as a direction-focused first slice
- richer knowledge grounding with evidence stratification, concept pages, and question / method / experiment pages: first version implemented in the direction-focused first slice
- first knowledge-health lint summaries and app-facing health lines: implemented in the direction-focused first slice
- conclusion pages and conclusion file-back into Flow / thread: first version implemented in the direction-focused first slice
- review syntheses now file back into the knowledge layer as raw sources, and lint begins checking contradiction / stale-conclusion / weak-handoff issues
- concept / question / method / experiment pages now start maintaining cross-links instead of staying as isolated slices
- knowledge-layer lint now also exports one actionable maintenance line so Flow / thread can show what to补 next
- longer-lived direction stage and continuity summaries across Flow / thread / reminders: first version implemented
- direction-level execution summaries that feel more continuous across Flow / thread / reminders: first version implemented
- durable snapshot history exported back into Flow / thread: first version implemented in the direction-focused first slice
- knowledge-layer lint now also points out the weakest maintenance dimension so Flow / thread can show not only what to补, but which knowledge dimension is currently thinnest
- knowledge-layer lint should keep getting more concrete: after target, source, and weakest dimension, it should also identify the specific page or knowledge object that deserves attention first
- synthesized reviews, open questions, validation loops, and next-step handoffs now also file back into question / experiment / method objects, not only into direction conclusion pages
- research syntheses such as contrarian questions and external hypotheses should also be promoted into durable question / experiment objects instead of staying as one-shot thread hints
- evidence pages should behave like first-class knowledge objects: linked to concepts, linked to related questions / methods / experiments, and able to carry maintenance guidance
- conclusion pages should also be treated as maintained knowledge objects, with links back into concepts and reusable question / method / experiment objects
- lint should increasingly speak in terms of concrete maintenance targets such as evidence pages, conclusion pages, and reusable knowledge objects, not only abstract missing-material advice
- app-facing lint reflow should eventually tell the user both what page/object to maintain and what kind of new source to add next
- app-facing lint should also clarify which dimension is weakest right now, such as evidence, question definition, method accumulation, conclusion freshness, recent progress, or object coverage
- reusable knowledge objects should keep linking back to the direction, conclusion, and evidence pages they belong to; otherwise the knowledge layer drifts back into isolated markdown slices
- knowledge-layer maintenance guidance should no longer stop at diagnosis: Flow / thread can now turn that guidance into seeded `补材料` captures with the right page/object/source context prefilled
- search should increasingly become a knowledge query surface, not only a note retriever; the first implemented slice now surfaces matching directions, concepts, questions, methods, experiments, conclusions, and evidence before raw note hits

Next strongest direction:

- stronger external research grounding beyond the current local knowledge-layer exports
- broader maintain + file-back passes that keep writing useful syntheses back into the knowledge layer
