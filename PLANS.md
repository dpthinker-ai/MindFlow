# MindFlow Plans

## Product Direction
MindFlow 2.0 should become a personal AI memory and action system, not only a note-taking app.

Target outcomes:

- capture fragmented ideas quickly
- connect old and new ideas into themes
- surface what matters each day
- convert ideas into concrete actions
- support work innovation, health, and life improvement

## Current Foundation

Already implemented:

- note capture and editing
- AI topic extraction
- AI tag extraction
- AI folder classification
- AI polishing
- markdown rendering
- folder organization
- search and filtering
- heatmap activity view
- share cards
- WebDAV backup
- Today page
- Flow page and thread detail page
- followed directions and thread curation
- AI daily brief
- note-to-next-action extraction
- reminder scheduling hooks
- weekly AI review
- launcher shortcuts
- share-to-capture entry
- home quick capture widget
- voice-to-text capture
- note horizons for short / medium / long implementation windows
- home time bank planning
- direction-stage execution rhythm across Flow / thread / reminders
- thread-level stage history
- research evidence stratification in thread workspace
- horizon-aware reminder cadence
- direction assets from verified and grounded research
- AI information architecture cleanup across Flow / thread / editor
- unified AI result card language across Flow / thread / reminders

## P0: Make It Daily

Goal:
- make the app worth opening every day

Planned items:

1. Today page
- status: implemented in a lighter form
- current shape:
  - one note to continue
  - one AI-generated exploration direction
  - one next action

2. Smarter reminders
- status: first version implemented
- current shape:
  - morning brief
  - evening review
  - low-frequency local notifications
  - main notification tap lands in Flow
  - reminder tap can now open the most relevant note directly
  - contextual quick actions for continue / reconnect / recap
  - can jump directly back into a matching thread when a reminder note belongs to a clear direction
  - when a reminder lands in Flow, it can focus the relevant section (today / reconnect / review / direction)
  - when a reminder note belongs to a direction with live research memory, the morning reminder can now carry one validation step and a direct `记验证` action

3. Faster capture
- status: refined
- current shape:
  - launcher shortcuts
  - launcher voice shortcut
  - home widget
  - home widget voice entry
  - widget can surface the current note to continue or the latest note
  - share-to-capture
  - voice-to-text in editor

## P1: AI Insight Layer

Goal:
- turn stored notes into useful thinking material

Planned items:

1. Daily AI brief
- status: implemented
- current shape:
  - what to continue
  - what to explore

2. Weekly AI review
- status: implemented and refined
- current shape:
  - strongest weekly theme
  - direction worth continuing
  - one stalled direction worth reviving
  - one synthesis / breakthrough suggestion
  - light weekly progress stats
  - capture thread weekly review into a new note

3. Idea linking
- connect similar notes
- detect repeated topics
- group notes into threads
- status: first version implemented
- current shape:
  - related notes
  - thread detail page
  - followed directions in Flow
  - followed directions surface a current focus line
  - followed directions now also explain why to continue now and the smallest next step
  - can jump directly from a followed direction to its current focus note
  - followed directions can now surface one lightweight research cue and a smallest validation step
  - followed directions now use a lighter primary handoff: continue the focus note first, open the direction second
  - editor now hints when a record appears to belong to an existing direction
  - thread focus and lightweight promotion
  - thread-level weekly review
  - followed directions now explain why the current research validation step matters now
  - followed directions can now also hint what to do next if that validation holds

4. Fusion suggestions
- combine multiple notes into one stronger concept
- suggest experiments or product directions

## P2: Action Layer

Goal:
- move from idea storage to idea execution

Planned items:

1. Next action extraction
- status: implemented
- current shape:
  - one concrete next step
  - AI first, rule fallback

2. Progress loop
- light status updates
- move notes from idea to in progress to done naturally
- status: first version implemented in thread workspace
- current shape:
  - focus note inside a thread
  - quick open
  - promote from idea to in progress
  - add a new note directly from the thread workspace
  - capture current thread judgment into a new note
- notes created from a thread preserve that thread's tag or folder context
- short / medium / long horizons now influence reminder copy and execution priority

3. Follow-up prompts
- revisit notes that have stayed untouched too long
- status: first version implemented
- current shape:
  - Flow 中的轻量“重新接上”
  - 晨间提醒里的低频旧记录唤起
  - 会说明为什么现在值得接上
  - 会给一条最小下一步
  - AI 可用时会优先生成更贴近当前主线的桥接理由与接上动作

## P3: Knowledge and Research Layer

Goal:
- help with innovation and frontier exploration

Planned items:

1. Research mode
- enrich selected notes with industry examples
- identify related attempts in the outside world
- summarize opportunity gaps
- status: first version implemented in thread workspace
- current shape:
  - external line of inquiry
  - opportunity-gap prompt
  - directly usable search queries
  - AI / rule source cues
  - capture research findings directly into a new thread note
  - recent research-derived notes stay visible inside the thread as lightweight research memory
  - research can now be compressed into one meaning line and one smallest validation action
  - recent research notes can now be clustered into lightweight research themes
  - grouped research themes now carry lightweight validation loops
  - clustered research can be captured back into a new note as a more stable thread judgment
  - followed directions in Flow can now turn a research validation loop directly into a seeded note
  - thread workspace can now directly capture the most valuable clustered validation loop into a dedicated note
  - reminder payloads can now explain why a validation step matters now before surfacing the concrete validation action
- validation capture seeds now carry a lightweight “if this holds, what to push next” execution prompt
- directions now expose a stage-aware execution rhythm so research and action can stay in one loop

2. Topic maps
- not heavy graph-first UI
- start with theme threads and linked clusters

3. Structured collections
- curated sets for work, product, health, education, and life

4. Knowledge Layer integration
- status: first direction-focused slice implemented locally
- target shape:
  - keep MindFlow database as runtime truth
  - add a separate markdown `MindFlow Knowledge Layer`
  - treat directions as one view inside a larger knowledge system
  - store raw sources, direction pages, concept pages, evidence pages, question pages, method / experiment pages, and stage snapshots
  - maintain the knowledge layer through background agent workflows instead of foreground mobile requests
  - export lightweight knowledge assets back into the app for Flow / thread / reminder surfaces
- intended directory shape:
  - `knowledge-layer/raw/`
  - `knowledge-layer/wiki/index.md`
  - `knowledge-layer/wiki/log.md`
  - `knowledge-layer/wiki/directions/`
  - `knowledge-layer/wiki/concepts/`
  - `knowledge-layer/wiki/evidence/`
  - `knowledge-layer/wiki/questions/`
  - `knowledge-layer/wiki/methods/`
  - `knowledge-layer/wiki/experiments/`
  - `knowledge-layer/wiki/lint/`
  - `knowledge-layer/wiki/snapshots/`
  - `knowledge-layer/AGENTS.md`
- intended workflow:
  - ingest notes, research records, validation records, and important reflections into raw markdown
  - update knowledge objects and index/log
  - lint the knowledge layer for contradictions, stale claims, missing concepts, and weak evidence
  - file useful query results and AI synthesis back into the knowledge layer
  - export app-friendly summaries back into MindFlow
- current shape:
  - local `knowledge-layer/` generated under app files
  - low-frequency background refresh on app stop
  - manual refresh from settings
  - raw source ingestion for notes, research, validations, reflections, review syntheses, and raw index
  - direction assets reflowed back into Flow and thread surfaces
  - conclusion pages generated under `wiki/conclusions/`
  - conclusion file-back reflowed into `Flow / 线程` as `当前结论 / 下一步承接`
  - evidence stratification reflowed back into Flow and thread surfaces
  - concept pages generated from repeated followed-direction tags
  - question / method / experiment pages generated from classified note content
  - synthesized open questions, validation loops, and next-shift handoffs now also file back into question / experiment / method pages
  - research syntheses such as contrarian questions and external hypotheses now also file back into durable question / experiment pages
  - concept pages and knowledge-object pages now maintain lightweight cross-links instead of staying isolated
  - evidence pages now start linking back into concepts and related knowledge objects instead of staying as flat bullet dumps
  - conclusion pages now also link back into concepts and reusable question / method / experiment objects
  - first lint pass exported into markdown and lightweight app-facing health summaries
  - lint now also exports one actionable `建议先补` maintenance line back into Flow / thread
  - lint wording is moving from abstract “补什么” advice toward concrete maintenance targets such as evidence pages, conclusion pages, and reusable objects
  - lint now begins exporting both the maintenance target and the next source type to add, so Flow / thread can reflow more actionable maintenance guidance
  - reusable question / method / experiment pages now also link back into their related direction, conclusion, and evidence pages
  - lint now starts checking contradiction, stale conclusions, weak evidence, and missing handoff
  - longer-lived continuity and trajectory summaries exported back into Flow, thread, and reminders
  - durable snapshot-backed stage history reflowed back into Flow and thread surfaces
- why this matters:
  - stronger external research grounding beyond one-shot model output
  - longer-lived knowledge persistence, not only thread-local reasoning
  - durable assets that support query, reuse, and innovation

## P4: MindFlow x FitEver

Goal:
- connect thinking and health behavior

Planned items:

1. Cross-app idea routing
- health notes can become FitEver experiments
- FitEver trends can feed back into MindFlow reflections

2. Health insight loop
- surface notes connected to sleep, exercise, stress, mood

## Next Build Focus

Next best order:

1. stronger external research grounding beyond the current local knowledge-layer exports
2. broader knowledge maintenance passes that keep filing conclusions, weekly reviews, and synthesis back into the knowledge layer
3. more durable stage persistence and knowledge maintenance beyond the current local snapshot cadence

Current progress:

- knowledge layer integration: first direction-focused slice implemented with local wiki generation, raw source ingestion, review syntheses, knowledge-object pages, cross-linked concept/object maintenance, first lint pass, conclusion pages, manual refresh, low-frequency background refresh, app-facing asset reflow, evidence stratification, concept pages, and snapshot-backed stage history
- execution continuity: first version implemented with continuous execution-loop summaries across Flow / thread / reminders, including recent progress and next check-in
- external grounding: first version implemented with shared external research snapshots, AI 外部视角 surfaces, and richer direction-focused grounding
- stage persistence: first version implemented with stage history, research evidence layers, horizon-aware reminder timing, continuity-aware reminder context, and durable snapshot-backed stage lines
- direction rhythm and trajectory: first version implemented with short / medium / long horizons, direction-stage rhythm, and longer-lived continuity / trajectory summaries
- stable direction assets: first version implemented in thread workspace and followed-direction summaries
- AI information architecture: first version implemented with `今日聚焦 / 方向判断 / 当前判断 / 研究 / 执行 / AI 整理`
- AI result styling: first version implemented with shared source chips and insight blocks across Flow / thread surfaces

## Success Criteria

MindFlow is succeeding when:

- it is opened every day without friction
- older notes become useful again
- AI gives real insight instead of generic summaries
- at least some notes reliably become action
