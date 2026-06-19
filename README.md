# MindFlow

[简体中文](README.zh-CN.md)

MindFlow is a local-first Android idea incubator.

It helps you capture fragile thoughts, keep them alive, find recurring threads,
create useful collisions, and turn mature patterns into reusable personal assets.
It is not a generic note app with an AI chat box attached.

## Why It Exists

Most note apps are optimized for storage. MindFlow is optimized for incubation:

1. Capture a spark before it disappears.
2. Let the local knowledge layer absorb and connect it.
3. Surface a thread, collision, asset, or gap worth acting on.
4. Deepen the material locally or through explicit cloud-assisted work.
5. File useful outputs back into durable local memory.

The product should feel like a pocket lab notebook with long memory, not an AI
dashboard or model playground.

## Product Surfaces

| Surface | Role |
| --- | --- |
| `记录` | Fast capture for text, voice, links, screenshots, and clipped material. |
| `今天` | The active incubation feed: sparks, threads, collisions, assets, and gaps. |
| `回看` | Directed thinking over maintained memory. |
| `图谱` | A shape view of hubs, isolated nodes, dense clusters, and missing links. |
| `设置` | Model controls, cloud configuration, sync/export, and diagnostics. |

Older internal routes may still use names such as `flow/*`, but new product
language should use `今天` and `回看`.

## Current Capabilities

- native capture and editing
- voice-to-text capture
- share-to-capture flows
- launcher shortcuts and quick-capture widget
- search, filters, reminders, and related-note structure
- local knowledge-layer maintenance
- on-device Gemma 4 E4B support through LiteRT-LM
- explicit cloud model configuration
- graph and review surfaces for memory context

## Local-First AI

MindFlow treats AI as one coherent thinking role: capture help, memory
maintenance, thread detection, collision generation, and asset shaping.

The trust boundary is local-first:

- raw capture stays local by default
- background maintenance stays local by default
- cloud use must be explicit or clearly implied by the selected action
- cloud-derived output should carry provenance
- the app should remain useful with cloud disabled

## Project Layout

- `app/src/main/java/com/mindflow/app/data/`: repositories, AI routing, local model, knowledge maintenance, backup/export, and graph planning
- `app/src/main/java/com/mindflow/app/ui/`: Compose screens, navigation, and UI components
- `app/src/main/assets/`: graph and skill runtime assets
- `docs/superpowers/specs/`: design notes
- `docs/superpowers/plans/`: historical implementation plans
- `PROJECT_GUIDE.md`: maintainer guidance, validation rules, and data-preservation constraints

## Build

Requirements:

- Android Studio or Android SDK command-line tools
- JDK 21
- `local.properties` with `sdk.dir`

```properties
sdk.dir=/path/to/android/sdk
```

```bash
export JAVA_HOME=/path/to/jdk-21
export PATH="$JAVA_HOME/bin:$PATH"

./gradlew --no-daemon :app:assembleDebug
./gradlew --no-daemon :app:testDebugUnitTest
./gradlew --no-daemon :app:assembleRelease
```

Release APK:

```text
app/build/outputs/apk/release/app-release.apk
```

## Secrets And Local Files

Do not commit local credentials, signing files, validation data, or personal
exports. These are ignored by default:

- `local.properties`
- `signing.properties`
- `keystore/`
- `.env` files
- `data/`
- `recovery/`
- `tmp/`
- `docs/product/`

## Release Validation Safety

User-visible validation must preserve existing app data and local model files.

For emulator or device validation:

- install the release APK, not a debug APK
- use `adb install -r app/build/outputs/apk/release/app-release.apk`
- do not run `adb uninstall`
- do not run `pm clear`
- do not wipe emulator or AVD data
- do not delete downloaded local model files
- stop if signing does not match

Read `PROJECT_GUIDE.md` before changing release validation, signing, local model,
or recovery workflows.
