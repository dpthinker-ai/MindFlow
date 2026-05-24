# Cloud AI Provider Runtime Policy Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a generic cloud AI provider layer with DeepSeek support, runtime task policy, cloud usage audit, and low-frequency cloud-use notices without weakening MindFlow's local-first boundary.

**Architecture:** Keep the first implementation Kotlin-native and incremental: provider specs and request adaptation live under `data/ai/cloud`, runtime/task policy lives under `data/ai`, usage audit is a JSONL/file-backed repository like existing trace recording, and UI settings reuse the current Settings screen rather than adding a new navigation surface. Existing direct planner calls are routed through request metadata in `AiServiceClient` so cloud usage can be recorded before the long-term orchestrator migration is complete.

**Tech Stack:** Kotlin, Android DataStore preferences, Compose Settings UI, `HttpURLConnection`, `org.json`, JUnit4, Truth, coroutine test.

---

## File Structure

- Create `app/src/main/java/com/mindflow/app/data/ai/cloud/CloudAiProviderSpec.kt`
  - Defines provider id, protocol, auth scheme, model specs, and request capabilities.
- Create `app/src/main/java/com/mindflow/app/data/ai/cloud/CloudAiProviderRegistry.kt`
  - Provides built-in `zhipu`, `openai`, `deepseek`, and `custom` specs; resolves provider by id or base URL.
- Create `app/src/main/java/com/mindflow/app/data/ai/cloud/CloudAiRequestAdapter.kt`
  - Builds provider-aware chat URL, auth candidates, JSON request body, and normalized model names.
- Modify `app/src/main/java/com/mindflow/app/data/model/AiProviderPreset.kt`
  - Converts the enum into a thin UI facade over the registry and adds `DEEPSEEK`.
- Modify `app/src/main/java/com/mindflow/app/data/model/AiSettings.kt`
  - Adds `providerId` to the cloud settings fingerprint.
- Modify `app/src/main/java/com/mindflow/app/data/settings/PreferencesAiSettingsRepository.kt`
  - Persists provider id and migrates existing installs from base URL.
- Modify `app/src/main/java/com/mindflow/app/data/topic/AiServiceClient.kt`
  - Uses `CloudAiRequestAdapter` and records cloud usage through optional request metadata.
- Create `app/src/main/java/com/mindflow/app/data/ai/AiRuntimeSettings.kt`
  - Defines cloud background permission, notification mode, and daily budget defaults.
- Create `app/src/main/java/com/mindflow/app/data/settings/AiRuntimeSettingsRepository.kt`
  - Repository contract for runtime policy.
- Create `app/src/main/java/com/mindflow/app/data/settings/PreferencesAiRuntimeSettingsRepository.kt`
  - DataStore-backed runtime policy repository with legacy execution-mode migration helper.
- Modify `app/src/main/java/com/mindflow/app/data/settings/OnDeviceExecutionModeCodec.kt`
  - Changes missing-history default to `AUTOMATIC`.
- Create `app/src/main/java/com/mindflow/app/data/ai/AiTaskPolicyRegistry.kt`
  - Encodes provider order, fallback, trigger surface, payload policy, sensitivity, and notification mode.
- Modify `app/src/main/java/com/mindflow/app/data/ai/AiTaskModels.kt`
  - Adds task types for direct cloud planners and request metadata fields.
- Modify `app/src/main/java/com/mindflow/app/data/ai/AiTaskRouter.kt`
  - Reads task policy for provider order and blocks disallowed background cloud use.
- Create `app/src/main/java/com/mindflow/app/data/ai/AiUsageEvent.kt`
  - Defines auditable metadata without raw prompts, API keys, file paths, or note body.
- Create `app/src/main/java/com/mindflow/app/data/ai/AiUsageEventRepository.kt`
  - File-backed JSONL repository with 90-day and 1000-event retention.
- Create `app/src/main/java/com/mindflow/app/data/ai/AiCloudUsageReporter.kt`
  - Records events and emits foreground notice text.
- Create `app/src/main/java/com/mindflow/app/data/ai/CloudUsageNotificationAggregator.kt`
  - Low-frequency aggregation rules: 5-minute earliest flush, 30-minute max window, 3 notifications per day.
- Create `app/src/main/java/com/mindflow/app/data/ai/CloudUsageBudgetGuard.kt`
  - Blocks background cloud after daily request/token thresholds.
- Create `app/src/main/java/com/mindflow/app/data/ai/AiDataSensitivityClassifier.kt`
  - Rule-based high sensitivity blocking for background cloud.
- Modify `app/src/main/java/com/mindflow/app/di/AppContainer.kt`
  - Wires runtime settings, usage repository, reporter, budget guard, and DeepSeek-aware client.
- Modify `app/src/main/java/com/mindflow/app/ui/MindFlowApp.kt`
  - Observes foreground cloud notices and shows Toast.
- Modify `app/src/main/java/com/mindflow/app/ui/screens/settings/SettingsViewModel.kt`
  - Saves provider id, runtime settings, and shows usage summary values.
- Modify `app/src/main/java/com/mindflow/app/ui/screens/settings/SettingsScreen.kt`
  - Adds DeepSeek to provider selector and changes cloud copy to explain low-frequency background notification.
- Create `implementation-notes.html`
  - Records implementation decisions, constraints, changes, and verification state.

## Task 1: Provider Registry and Request Adapter

**Files:**
- Create: `app/src/main/java/com/mindflow/app/data/ai/cloud/CloudAiProviderSpec.kt`
- Create: `app/src/main/java/com/mindflow/app/data/ai/cloud/CloudAiProviderRegistry.kt`
- Create: `app/src/main/java/com/mindflow/app/data/ai/cloud/CloudAiRequestAdapter.kt`
- Modify: `app/src/main/java/com/mindflow/app/data/model/AiProviderPreset.kt`
- Modify: `app/src/main/java/com/mindflow/app/data/model/AiSettings.kt`
- Modify: `app/src/main/java/com/mindflow/app/data/settings/PreferencesAiSettingsRepository.kt`
- Test: `app/src/test/java/com/mindflow/app/data/ai/cloud/CloudAiProviderRegistryTest.kt`
- Test: `app/src/test/java/com/mindflow/app/data/ai/cloud/CloudAiRequestAdapterTest.kt`

- [x] **Step 1: Write failing provider registry tests**

```kotlin
@Test
fun deepSeekProviderUsesCurrentV4Defaults() {
    val spec = CloudAiProviderRegistry.require("deepseek")
    assertThat(spec.baseUrl).isEqualTo("https://api.deepseek.com")
    assertThat(spec.chatPath).isEqualTo("/chat/completions")
    assertThat(spec.defaultModel).isEqualTo("deepseek-v4-flash")
    assertThat(spec.selectableModels.map { it.id }).containsExactly("deepseek-v4-flash", "deepseek-v4-pro").inOrder()
    assertThat(spec.deprecatedModelAliases["deepseek-chat"]).isEqualTo("deepseek-v4-flash")
}
```

- [x] **Step 2: Run registry tests and verify red**

Run:

```bash
./gradlew --no-daemon :app:testDebugUnitTest --tests "com.mindflow.app.data.ai.cloud.CloudAiProviderRegistryTest"
```

Expected: FAIL because `CloudAiProviderRegistry` does not exist.

- [x] **Step 3: Write minimal provider spec and registry**

Add the provider spec types and built-in providers. Keep `CUSTOM` as an OpenAI-compatible spec with blank base/model so current custom UI behavior stays intact.

- [x] **Step 4: Run registry tests and verify green**

Run:

```bash
./gradlew --no-daemon :app:testDebugUnitTest --tests "com.mindflow.app.data.ai.cloud.CloudAiProviderRegistryTest"
```

Expected: PASS.

- [x] **Step 5: Write failing request adapter tests**

```kotlin
@Test
fun deepSeekBuildsChatCompletionWithBearerAuthAndThinking() {
    val request = CloudAiRequestAdapter.build(
        settings = AiSettings(providerId = "deepseek", apiKey = "sk-test", baseUrl = "https://api.deepseek.com", model = "deepseek-v4-pro"),
        systemPrompt = "system",
        userPrompt = "user",
        maxTokens = 64,
        temperature = 0.2,
        thinkingEnabled = true,
    )
    assertThat(request.url).isEqualTo("https://api.deepseek.com/chat/completions")
    assertThat(request.authHeaders).containsExactly("Bearer sk-test")
    assertThat(request.body.getString("model")).isEqualTo("deepseek-v4-pro")
    assertThat(request.body.has("thinking")).isTrue()
}

@Test
fun openAiDoesNotSendThinkingField() {
    val request = CloudAiRequestAdapter.build(
        settings = AiSettings(providerId = "openai", apiKey = "sk-test", baseUrl = "https://api.openai.com/v1", model = "gpt-5.4"),
        systemPrompt = "system",
        userPrompt = "user",
        maxTokens = 64,
        temperature = 0.2,
        thinkingEnabled = true,
    )
    assertThat(request.url).isEqualTo("https://api.openai.com/v1/chat/completions")
    assertThat(request.authHeaders.first()).isEqualTo("Bearer sk-test")
    assertThat(request.body.has("thinking")).isFalse()
}
```

- [x] **Step 6: Run adapter tests and verify red**

Run:

```bash
./gradlew --no-daemon :app:testDebugUnitTest --tests "com.mindflow.app.data.ai.cloud.CloudAiRequestAdapterTest"
```

Expected: FAIL because `CloudAiRequestAdapter` does not exist.

- [x] **Step 7: Implement adapter and settings persistence**

Implement URL joining, auth candidate ordering, model alias replacement, and provider-id migration from existing base URL.

- [x] **Step 8: Run adapter/settings tests and verify green**

Run:

```bash
./gradlew --no-daemon :app:testDebugUnitTest --tests "com.mindflow.app.data.ai.cloud.*"
```

Expected: PASS.

- [ ] **Step 9: Commit provider registry**

```bash
git add app/src/main/java/com/mindflow/app/data/ai/cloud app/src/main/java/com/mindflow/app/data/model/AiProviderPreset.kt app/src/main/java/com/mindflow/app/data/model/AiSettings.kt app/src/main/java/com/mindflow/app/data/settings/PreferencesAiSettingsRepository.kt app/src/test/java/com/mindflow/app/data/ai/cloud implementation-notes.html
git commit -m "feat: add cloud ai provider registry"
```

## Task 2: Runtime Settings and Default Mode Migration

**Files:**
- Create: `app/src/main/java/com/mindflow/app/data/ai/AiRuntimeSettings.kt`
- Create: `app/src/main/java/com/mindflow/app/data/settings/AiRuntimeSettingsRepository.kt`
- Create: `app/src/main/java/com/mindflow/app/data/settings/PreferencesAiRuntimeSettingsRepository.kt`
- Modify: `app/src/main/java/com/mindflow/app/data/settings/OnDeviceExecutionModeCodec.kt`
- Modify: `app/src/main/java/com/mindflow/app/data/settings/PreferencesOnDeviceModelSettingsRepository.kt`
- Test: `app/src/test/java/com/mindflow/app/data/settings/OnDeviceExecutionModeCodecTest.kt`
- Test: `app/src/test/java/com/mindflow/app/data/settings/AiRuntimeSettingsRepositoryTest.kt`

- [ ] **Step 1: Write failing empty-history default test**

```kotlin
@Test
fun missingStoredModeAndMissingLegacyFlagDefaultsToAutomatic() {
    assertThat(OnDeviceExecutionModeCodec.decode(raw = null, legacyPreferOnDevice = null))
        .isEqualTo(AiExecutionMode.AUTOMATIC)
}
```

- [ ] **Step 2: Run codec test and verify red**

Run:

```bash
./gradlew --no-daemon :app:testDebugUnitTest --tests "com.mindflow.app.data.settings.OnDeviceExecutionModeCodecTest"
```

Expected: FAIL because missing legacy flag currently decodes as `CLOUD_ONLY`.

- [ ] **Step 3: Implement codec fix**

Change the no-history branch to `AUTOMATIC`, while keeping `legacyPreferOnDevice = false` as `CLOUD_ONLY` and explicit stored mode as highest priority.

- [ ] **Step 4: Add runtime settings model and repository tests**

```kotlin
@Test
fun defaultsAllowLowFrequencyBackgroundCloudWithAutomaticMode() {
    val settings = AiRuntimeSettings()
    assertThat(settings.executionMode).isEqualTo(AiExecutionMode.AUTOMATIC)
    assertThat(settings.cloudAllowedForBackground).isTrue()
    assertThat(settings.backgroundCloudNotificationMode).isEqualTo(CloudNotificationMode.LOW_FREQUENCY)
    assertThat(settings.dailyBackgroundCloudRequestLimit).isEqualTo(30)
}
```

- [ ] **Step 5: Implement runtime settings files**

Persist `executionMode`, `cloudAllowedForInteractive`, `cloudAllowedForBackground`, `notifyOnCloudUse`, `backgroundCloudNotificationMode`, `dailyBackgroundCloudRequestLimit`, and `dailyBackgroundTokenSoftLimit`.

- [ ] **Step 6: Run runtime tests and verify green**

Run:

```bash
./gradlew --no-daemon :app:testDebugUnitTest --tests "com.mindflow.app.data.settings.*Runtime*"
./gradlew --no-daemon :app:testDebugUnitTest --tests "com.mindflow.app.data.settings.OnDeviceExecutionModeCodecTest"
```

Expected: PASS.

- [ ] **Step 7: Commit runtime settings**

```bash
git add app/src/main/java/com/mindflow/app/data/ai/AiRuntimeSettings.kt app/src/main/java/com/mindflow/app/data/settings/AiRuntimeSettingsRepository.kt app/src/main/java/com/mindflow/app/data/settings/PreferencesAiRuntimeSettingsRepository.kt app/src/main/java/com/mindflow/app/data/settings/OnDeviceExecutionModeCodec.kt app/src/main/java/com/mindflow/app/data/settings/PreferencesOnDeviceModelSettingsRepository.kt app/src/test/java/com/mindflow/app/data/settings implementation-notes.html
git commit -m "feat: add ai runtime settings"
```

## Task 3: Task Policy, Sensitivity, and Budget Guards

**Files:**
- Create: `app/src/main/java/com/mindflow/app/data/ai/AiTaskPolicyRegistry.kt`
- Create: `app/src/main/java/com/mindflow/app/data/ai/AiDataSensitivityClassifier.kt`
- Create: `app/src/main/java/com/mindflow/app/data/ai/CloudUsageBudgetGuard.kt`
- Modify: `app/src/main/java/com/mindflow/app/data/ai/AiTaskModels.kt`
- Modify: `app/src/main/java/com/mindflow/app/data/ai/AiTaskRouter.kt`
- Test: `app/src/test/java/com/mindflow/app/data/ai/AiTaskPolicyRegistryTest.kt`
- Test: `app/src/test/java/com/mindflow/app/data/ai/AiTaskRouterPolicyTest.kt`
- Test: `app/src/test/java/com/mindflow/app/data/ai/AiDataSensitivityClassifierTest.kt`
- Test: `app/src/test/java/com/mindflow/app/data/ai/CloudUsageBudgetGuardTest.kt`

- [ ] **Step 1: Write failing task policy tests**

```kotlin
@Test
fun foregroundPolishPrefersCloudAndUsesToastNotice() {
    val policy = AiTaskPolicyRegistry.policyFor(AiTaskType.POLISH_CONTENT)
    assertThat(policy.providerOrder).containsExactly(AiProvider.CLOUD, AiProvider.ON_DEVICE).inOrder()
    assertThat(policy.noticeMode).isEqualTo(AiCloudNoticeMode.TOAST)
    assertThat(policy.payloadPolicy).isEqualTo(PromptPayloadPolicy.SINGLE_NOTE_EXCERPT)
}

@Test
fun mediaTasksRemainOnDeviceOnly() {
    assertThat(AiTaskPolicyRegistry.policyFor(AiTaskType.TRANSCRIBE_AUDIO).providerOrder)
        .containsExactly(AiProvider.ON_DEVICE)
    assertThat(AiTaskPolicyRegistry.policyFor(AiTaskType.UNDERSTAND_IMAGE).providerOrder)
        .containsExactly(AiProvider.ON_DEVICE)
}
```

- [ ] **Step 2: Run policy tests and verify red**

Run:

```bash
./gradlew --no-daemon :app:testDebugUnitTest --tests "com.mindflow.app.data.ai.AiTaskPolicyRegistryTest"
```

Expected: FAIL because policy registry does not exist.

- [ ] **Step 3: Implement policy registry and request metadata**

Add trigger metadata to `AiTaskRequest`: `triggerSurface`, `triggerMode`, and optional `payloadPolicyOverride`. Keep constructor defaults compatible with existing tests.

- [ ] **Step 4: Write failing sensitivity and budget tests**

```kotlin
@Test
fun highSensitivityBlocksBackgroundCloud() {
    val sensitivity = AiDataSensitivityClassifier.classify(AiTaskInput.NoteText("身份证 110101199003071234 和银行卡 6222"))
    assertThat(sensitivity).isEqualTo(AiDataSensitivity.HIGH)
}

@Test
fun backgroundBudgetBlocksWhenDailyRequestsReachLimit() {
    val decision = CloudUsageBudgetGuard(AiRuntimeSettings(dailyBackgroundCloudRequestLimit = 1))
        .canUseBackgroundCloud(requestsToday = 1, tokensToday = 0)
    assertThat(decision.allowed).isFalse()
    assertThat(decision.reason).isEqualTo("budget_blocked")
}
```

- [ ] **Step 5: Implement sensitivity and budget guards**

Rules: high sensitivity blocks background cloud, low and medium can proceed when runtime allows background cloud, request limit blocks only background cloud.

- [ ] **Step 6: Modify router to use policy order and guards**

`AiTaskRouter` should keep existing behavior for defaults but derive order from `AiTaskPolicyRegistry` when request metadata is not explicit. It should throw `AiTaskRoutingException` with `firstFailureReason = "background_cloud_blocked"` or `budget_blocked` when policy blocks cloud after local failure.

- [ ] **Step 7: Run AI policy/router tests and verify green**

Run:

```bash
./gradlew --no-daemon :app:testDebugUnitTest --tests "com.mindflow.app.data.ai.*Policy*" --tests "com.mindflow.app.data.ai.*Budget*" --tests "com.mindflow.app.data.ai.*Sensitivity*" --tests "com.mindflow.app.data.ai.AiTaskRouterTest"
```

Expected: PASS.

- [ ] **Step 8: Commit policy and guards**

```bash
git add app/src/main/java/com/mindflow/app/data/ai app/src/test/java/com/mindflow/app/data/ai implementation-notes.html
git commit -m "feat: add ai task policy guards"
```

## Task 4: Usage Audit and Low-Frequency Notices

**Files:**
- Create: `app/src/main/java/com/mindflow/app/data/ai/AiUsageEvent.kt`
- Create: `app/src/main/java/com/mindflow/app/data/ai/AiUsageEventRepository.kt`
- Create: `app/src/main/java/com/mindflow/app/data/ai/AiCloudUsageReporter.kt`
- Create: `app/src/main/java/com/mindflow/app/data/ai/CloudUsageNotificationAggregator.kt`
- Modify: `app/src/main/java/com/mindflow/app/data/topic/AiServiceClient.kt`
- Modify: `app/src/main/java/com/mindflow/app/data/ai/CloudAiTaskProvider.kt`
- Test: `app/src/test/java/com/mindflow/app/data/ai/AiUsageEventRepositoryTest.kt`
- Test: `app/src/test/java/com/mindflow/app/data/ai/AiCloudUsageReporterTest.kt`
- Test: `app/src/test/java/com/mindflow/app/data/ai/CloudUsageNotificationAggregatorTest.kt`

- [ ] **Step 1: Write failing usage repository test**

```kotlin
@Test
fun repositoryPersistsMetadataWithoutPromptOrApiKey() = runTest {
    val repository = FileAiUsageEventRepository(Files.createTempDirectory("ai-usage").toFile())
    repository.append(sampleEvent())
    val recent = repository.recent(limit = 20)
    assertThat(recent.single().providerId).isEqualTo("deepseek")
    assertThat(recent.single().payloadPolicy).isEqualTo(PromptPayloadPolicy.SELECTED_SNIPPETS)
    assertThat(repository.rawText()).doesNotContain("sk-test")
    assertThat(repository.rawText()).doesNotContain("完整正文")
}
```

- [ ] **Step 2: Run repository test and verify red**

Run:

```bash
./gradlew --no-daemon :app:testDebugUnitTest --tests "com.mindflow.app.data.ai.AiUsageEventRepositoryTest"
```

Expected: FAIL because usage repository does not exist.

- [ ] **Step 3: Implement usage event and JSONL repository**

Write only metadata fields; enforce retention during append.

- [ ] **Step 4: Write failing notification aggregation tests**

```kotlin
@Test
fun backgroundEventsWaitAtLeastFiveMinutesBeforeFlush() {
    val aggregator = CloudUsageNotificationAggregator()
    val event = sampleEvent(timestamp = 1_000L, triggerMode = AiTriggerMode.BACKGROUND_AUTOMATION)
    assertThat(aggregator.record(event, now = 1_000L)).isNull()
    assertThat(aggregator.flushIfDue(now = 1_000L + 4.minutes.inWholeMilliseconds)).isNull()
    assertThat(aggregator.flushIfDue(now = 1_000L + 5.minutes.inWholeMilliseconds)?.message)
        .contains("DeepSeek")
}
```

- [ ] **Step 5: Implement reporter and aggregator**

Reporter appends events, emits foreground notice immediately, and sends background events into the aggregator.

- [ ] **Step 6: Wire `AiServiceClient` and `CloudAiTaskProvider` to reporter**

Use request metadata to record provider id, provider label, model, task type, trigger mode, payload policy, success/failure, and token count. Do not record prompts or API keys.

- [ ] **Step 7: Run audit and notice tests and verify green**

Run:

```bash
./gradlew --no-daemon :app:testDebugUnitTest --tests "com.mindflow.app.data.ai.*Usage*" --tests "com.mindflow.app.data.ai.*Notification*"
```

Expected: PASS.

- [ ] **Step 8: Commit audit and notices**

```bash
git add app/src/main/java/com/mindflow/app/data/ai app/src/main/java/com/mindflow/app/data/topic/AiServiceClient.kt app/src/main/java/com/mindflow/app/data/ai/CloudAiTaskProvider.kt app/src/test/java/com/mindflow/app/data/ai implementation-notes.html
git commit -m "feat: record cloud ai usage"
```

## Task 5: Settings UI and App Wiring

**Files:**
- Modify: `app/src/main/java/com/mindflow/app/di/AppContainer.kt`
- Modify: `app/src/main/java/com/mindflow/app/ui/MindFlowApp.kt`
- Modify: `app/src/main/java/com/mindflow/app/ui/screens/settings/SettingsViewModel.kt`
- Modify: `app/src/main/java/com/mindflow/app/ui/screens/settings/SettingsScreen.kt`
- Test: `app/src/test/java/com/mindflow/app/ui/screens/settings/SettingsExecutionModePersistenceTest.kt`
- Test: `app/src/test/java/com/mindflow/app/ui/screens/settings/SettingsCloudAiProviderTest.kt`

- [ ] **Step 1: Write failing settings provider test**

```kotlin
@Test
fun deepSeekPresetSetsProviderBaseUrlAndDefaultModel() {
    val state = SettingsUiState().applyPresetForTest(AiProviderPreset.DEEPSEEK)
    assertThat(state.aiProviderPreset).isEqualTo(AiProviderPreset.DEEPSEEK)
    assertThat(state.baseUrl).isEqualTo("https://api.deepseek.com")
    assertThat(state.model).isEqualTo("deepseek-v4-flash")
}
```

- [ ] **Step 2: Run settings test and verify red**

Run:

```bash
./gradlew --no-daemon :app:testDebugUnitTest --tests "com.mindflow.app.ui.screens.settings.SettingsCloudAiProviderTest"
```

Expected: FAIL because DeepSeek preset is not exposed.

- [ ] **Step 3: Implement settings UI/provider wiring**

Expose all non-custom presets in `ProviderPresetSelector`, save `providerId`, update cloud copy to say background cloud can happen in automatic mode with low-frequency notice and usage records.

- [ ] **Step 4: Wire reporter into app root Toast**

Add reporter to `MindFlowApp` parameters and use `LaunchedEffect` to collect foreground notice text and show Toast.

- [ ] **Step 5: Run settings tests and verify green**

Run:

```bash
./gradlew --no-daemon :app:testDebugUnitTest --tests "com.mindflow.app.ui.screens.settings.*"
```

Expected: PASS.

- [ ] **Step 6: Commit UI wiring**

```bash
git add app/src/main/java/com/mindflow/app/di/AppContainer.kt app/src/main/java/com/mindflow/app/ui/MindFlowApp.kt app/src/main/java/com/mindflow/app/ui/screens/settings app/src/test/java/com/mindflow/app/ui/screens/settings implementation-notes.html
git commit -m "feat: expose cloud ai policy settings"
```

## Task 6: Verification and Release Build

**Files:**
- Modify: `implementation-notes.html`

- [ ] **Step 1: Run focused unit tests**

Run:

```bash
./gradlew --no-daemon :app:testDebugUnitTest --tests "com.mindflow.app.data.ai.*" --tests "com.mindflow.app.data.ai.cloud.*" --tests "com.mindflow.app.data.settings.*" --tests "com.mindflow.app.ui.screens.settings.*"
```

Expected: PASS.

- [ ] **Step 2: Run full unit test suite**

Run:

```bash
./gradlew --no-daemon :app:testDebugUnitTest
```

Expected: PASS.

- [ ] **Step 3: Build release APK**

Run:

```bash
./gradlew --no-daemon :app:assembleRelease
```

Expected: PASS and release artifact at `app/build/outputs/apk/release/app-release.apk`.

- [ ] **Step 4: Manual release validation**

Use only the release APK. Do not install debug APK, uninstall, clear app data, wipe emulator, or delete local model files. If a compatible device/emulator is available, install with `adb install -r app/build/outputs/apk/release/app-release.apk`, launch `com.mindflow.app`, and verify settings show DeepSeek plus existing notes/model remain present. If signing mismatch or device unavailable occurs, stop and record the exact blocked state in `implementation-notes.html`.

- [ ] **Step 5: Final implementation notes update**

Record commits, tests, release build result, and any manual validation limitation in `implementation-notes.html`.

- [ ] **Step 6: Final commit if verification changes notes**

```bash
git add implementation-notes.html
git commit -m "docs: finalize cloud ai implementation notes"
```

## Self-Review

- Spec coverage:
  - DeepSeek and generalized provider registry are covered by Task 1.
  - Runtime settings and the default `AUTOMATIC` migration are covered by Task 2.
  - Task policy, sensitivity, payload policy, and budget protection are covered by Task 3.
  - Usage events and low-frequency notification aggregation are covered by Task 4.
  - Settings UI and foreground Toast are covered by Task 5.
  - Release build and non-destructive validation rules are covered by Task 6.
- Scope control:
  - This plan keeps first-version provider support to OpenAI Chat Completions-compatible APIs.
  - It avoids Room migrations by using JSONL for usage audit, matching existing `AiTaskTraceRecorder` operational style.
  - It does not implement native Anthropic/Gemini protocols or upload audio/image files to cloud.
- Placeholder scan:
  - Every task has concrete file paths, test examples, commands, and expected outcomes.
