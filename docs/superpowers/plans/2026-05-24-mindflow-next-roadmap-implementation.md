# 2026-05-24 MindFlow Next RoadMap Implementation Plan

## Scope

Implement the first RoadMap slice that can be validated safely in this workspace:

1. Review chat trust summary:
   - Show what time range was queried.
   - Show hit count and whether a status filter was applied.
   - Distinguish "no records in scope" from "records existed but filters removed them".
   - Mark local/cloud/on-device source in the current answer.
2. Real-question regressions:
   - Cover the user-reported questions:
     - 本周我记录了哪些内容？
     - 最近一个月有哪些未推进的想法？
     - 我最近在关注什么？
     - 哪些想法一直没动？
     - 最近两周的矛盾帮我串一下
3. Review chat persistence and UI:
   - Persist the trust summary in saved sessions.
   - Keep saved previews human-readable, not raw JSON.
4. AI output local deposition:
   - Rename the summary action into an Asset-oriented action.
   - Seed editable capture content with provenance: source, provider, query material summary, and referenced records.

## Out Of Scope For This Slice

- Full Flow information architecture rebuild.
- Real cloud provider network checks for DeepSeek/Zhipu/OpenAI/Custom.
- Verifying actual cloud credentials or real-phone notification behavior.

These will be marked as requiring real-device or credential-backed validation after emulator checks.

## Verification

Run targeted unit tests first, then broader debug unit tests if practical:

- `./gradlew --no-daemon :app:testDebugUnitTest --tests 'com.mindflow.app.data.reviewchat.*' --tests 'com.mindflow.app.ui.screens.reviewchat.*'`

Build and install only the release APK for user-visible emulator validation:

- `./gradlew --no-daemon :app:assembleRelease`
- `adb install -r app/build/outputs/apk/release/app-release.apk`

Do not uninstall, clear app data, wipe the AVD, or replace local model files.

## 2026-05-24 Implementation Result

- Implemented review chat answer trace metadata across parser, corpus context, turn result, UI message, saved conversation repository, and Room migration v4.
- Added regression coverage for:
  - `本周我记录了哪些内容？`
  - `最近一个月有哪些未推进的想法？`
  - `我最近在关注什么？`
  - `哪些想法一直没动？`
  - `最近两周的矛盾帮我串一下`
- Converted the review-chat deposition action from a generic summary record into an editable `回看资产` capture seed with provenance.
- Updated Flow card language toward `Asset` and `Gap` actions: `整理成方案` and `找证据`.
- Clarified cloud AI usage statistics as provider-scoped in Settings.
- Verified:
  - `./gradlew --no-daemon :app:testDebugUnitTest`
  - `./gradlew --no-daemon :app:assembleRelease`
  - release APK installed with `adb -s emulator-5554 install -r app/build/outputs/apk/release/app-release.apk`
  - app launched on emulator from `.SplashActivity`
  - notes count remained `148`
  - `review-chat.db` migrated to user version `4` and contains `answerTraceDisplayLine` / `answerTraceEmptyReason`
- Emulator screenshots:
  - `/tmp/mindflow-roadmap-final-launch.png`
  - `/tmp/mindflow-roadmap-flow-final.png`
- Not fully verified on emulator:
  - Real cloud provider connection for DeepSeek / Zhipu / OpenAI / Custom, because that requires real API keys and network calls.
  - Real foreground toast / low-frequency notification behavior on a physical device.
  - Full generated AI answer quality for the real questions, because this emulator has no local model file in `files/local-models` and live cloud calls were intentionally not triggered.
