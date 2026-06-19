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
- `今天`: active incubation feed and next actions
- `回看`: directed thinking over maintained memory
- `图谱`: shape, hubs, isolation, missing links
- `设置`: model controls, sync, diagnostics

## 今天 / 回看 rules

There is no standalone user-facing `Flow` tab. Older internal route names may still use `flow/*` for compatibility, but user-facing validation and product discussion should refer to `今天` or `回看`.

今天 / 回看 are not:

- an AI brief
- a system status screen
- a direction dashboard
- a maintainer console

今天 / 回看 are:

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
- for emulator validation, install and launch the release APK (`app/build/outputs/apk/release/app-release.apk`) instead of the debug APK, because the user's simulator contains release-signed app data that must be preserved
- unit tests can still run against the debug test target, but any user-visible install, launch, screenshot, or data-preserving verification should use `./gradlew --no-daemon :app:assembleRelease` and the release APK
- MindFlow app data and downloaded local model files are durable validation assets; do not delete, replace, or recreate them during development validation
- before any emulator restart, APK install, signing change, database push, model-file operation, or AVD configuration change, preserve a backup of `/data/user/0/com.mindflow.app`, `/storage/emulated/0/Android/data/com.mindflow.app`, and relevant user export files from `/storage/emulated/0/Download`
- never run `adb uninstall`, `pm clear`, emulator `-wipe-data`, or any AVD launch mode that starts from a fresh data image unless the user explicitly authorizes destructive reset
- do not switch package signing or install a debug APK over the user's release-signed app data; if signing does not match, stop and report instead of forcing a reinstall
- release validation must explicitly check that existing notes and the downloaded Gemma model are still present after install, launch, screenshot, and restart checks
- new automation should be low-frequency and battery-aware
- release behavior matters more than debug-only polish

## Recovery anchors, 2026-05-03

These local recovery folders are important and should not be deleted:

- `recovery/pre-release-install-20260503-215006/`
  - full app-private backup from the release-signed emulator before reinstall work
  - contains `com.mindflow.app/files/notes` with 146 note files
  - contains `com.mindflow.app/files/local-models/gemma-4-E4B-it.litertlm`, the 3.4G Gemma 4 model
  - contains `external-com.mindflow.app` and `Download`
- `recovery/pre-avd-data-resize-20260503-215334/`
  - AVD image/config backup taken before the attempted `/data` resize
- `recovery/md-restore-20260503-213539/`
  - recovery/import workspace for `data/mindflow-20260502-2148.md`
- `recovery/current-appdata-20260503-213038/`
  - earlier app-data snapshot

If emulator app data disappears again, first reinstall `app/build/outputs/apk/release/app-release.apk`, then restore from `recovery/pre-release-install-20260503-215006/com.mindflow.app` into `/data/user/0/com.mindflow.app`, set ownership to the package UID, and run `restorecon -R /data/user/0/com.mindflow.app` if available. Do not restore through uninstall/clear flows.

The `*.xnnpack_cache` file under `files/local-models` is a derived LiteRT cache and may be deleted to recover install space. Do not delete `gemma-4-E4B-it.litertlm`.

## Session reset handoff, 2026-05-03

`PROJECT_GUIDE.md` is the single source of truth. `AGENTS.md` is only a short pointer for environments that expect that filename.

Current emulator/release validation state:

- release APK path: `app/build/outputs/apk/release/app-release.apk`
- emulator package: `com.mindflow.app`
- current release data: 146 private note files
- current local model: `/data/user/0/com.mindflow.app/files/local-models/gemma-4-E4B-it.litertlm`, 3.4G
- important full backup: `recovery/pre-release-install-20260503-215006/`
- important AVD backup: `recovery/pre-avd-data-resize-20260503-215334/`
- do not run debug APK installs, `adb uninstall`, `pm clear`, or AVD wipe flows
- if install space is tight, delete only derived LiteRT `*.xnnpack_cache`, never the `.litertlm` model

Current Review UI task:

- The user selected approach A: align Review surfaces quickly and closely with the reference image, rather than making tiny incremental changes.
- Reference image: local private design reference, intentionally not stored in this repository.
- Current release screenshot after latest changes: `/tmp/mindflow-review-fast-align.png`
- The user still feels the implementation is far from reference and too slow. Continue with larger visual alignment passes.
- Already removed from Review page: `值得回看的`, `还值得翻的`, and `刷新本地知识层`.
- Next focus: make Review home visually match the reference first, then Review chat detail.

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
