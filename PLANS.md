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

3. Faster capture
- status: refined
- current shape:
  - launcher shortcuts
  - launcher voice shortcut
  - home widget
  - home widget voice entry
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
  - thread focus and lightweight promotion
  - thread-level weekly review

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

2. Topic maps
- not heavy graph-first UI
- start with theme threads and linked clusters

3. Structured collections
- curated sets for work, product, health, education, and life

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

1. external research capture back into notes
2. thread-level external research memory
3. widget-level capture polish
4. thread-level research-to-action summaries
5. thread follow-up summaries outside the thread page

## Success Criteria

MindFlow is succeeding when:

- it is opened every day without friction
- older notes become useful again
- AI gives real insight instead of generic summaries
- at least some notes reliably become action
