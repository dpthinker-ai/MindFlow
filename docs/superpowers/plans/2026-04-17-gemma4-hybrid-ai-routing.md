# Gemma 4 Hybrid AI Routing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Gemma 4 the default provider for polish, topic/tag/folder extraction, and concept-graph generation, with typed automatic cloud fallback and one global execution mode.

**Architecture:** Add a typed `AiTaskRouter` between feature code and providers, then migrate feature slices one by one. Keep deterministic rule fallbacks in existing extractors and graph fallbacks, but move all AI decisions behind router-managed execution mode, quality gates, and trace logging.

**Tech Stack:** Kotlin, Android ViewModel, DataStore, kotlinx.coroutines, JUnit4, Truth, LiteRt LM, existing `AiServiceClient` HTTP client.

---

## Scope Check

This spec is still one cohesive subsystem. The settings work, router work, metadata extraction work, and graph work all exist to make the same runtime decision: `which provider should serve this task, and when do we fall back?` Do not split this into separate implementation plans.

## Compatibility Decisions

1. Keep deterministic rule fallbacks in `CombinedTopicExtractor`, `CombinedTagExtractor`, `CombinedFolderClassifier`, and `ConceptGraphPlanner`. This plan changes AI routing, not the last local fallback.
2. Keep `AiSettings.aiEnabled` temporarily as the compatibility bit for “cloud provider may be used”. The new `AiExecutionMode` becomes the source of truth for routing order.
3. Migrate old `preferOnDevice` data to the new execution mode codec instead of deleting it blindly.
4. Do not unify the two DataStore repositories in this implementation. Unify the user-facing setting and runtime resolver first.
5. Router failures must preserve execution mode and first failure reason so `仅端侧` can surface an explicit local-only failure message.

## File Map

### Create

- `app/src/main/java/com/mindflow/app/data/ai/AiExecutionMode.kt`
  Purpose: three execution modes and provider enum.
- `app/src/main/java/com/mindflow/app/data/ai/AiTaskModels.kt`
  Purpose: typed task request, typed payloads, task result meta, quality signal container, and routing failure type.
- `app/src/main/java/com/mindflow/app/data/ai/AiTaskRouter.kt`
  Purpose: execute provider order, run structure/quality gates, decide fallback, emit task meta.
- `app/src/main/java/com/mindflow/app/data/ai/CloudAiTaskProvider.kt`
  Purpose: wrap `AiServiceClient` into typed task payloads.
- `app/src/main/java/com/mindflow/app/data/ai/OnDeviceAiTaskProvider.kt`
  Purpose: wrap `OnDeviceAiClient` into typed task payloads.
- `app/src/main/java/com/mindflow/app/data/ai/AiTaskTraceRecorder.kt`
  Purpose: append JSONL traces for routed AI tasks and keep a latest-successful-provider snapshot by task.
- `app/src/main/java/com/mindflow/app/data/settings/OnDeviceExecutionModeCodec.kt`
  Purpose: map stored strings and legacy `preferOnDevice` values into `AiExecutionMode`.
- `app/src/main/java/com/mindflow/app/data/localmodel/GemmaTaskPromptFactory.kt`
  Purpose: Gemma 4 prompts for topic, tags, folder, polish, graph extraction, graph canonicalization, graph relations.
- `app/src/main/java/com/mindflow/app/data/topic/ContentPolishPlanner.kt`
  Purpose: hide router + normalization details from `NoteEditorViewModel`.

### Modify

- `app/src/main/java/com/mindflow/app/data/model/OnDeviceModelSettings.kt`
  Purpose: add `executionMode`, keep compatibility accessor for old call sites during migration.
- `app/src/main/java/com/mindflow/app/data/settings/PreferencesOnDeviceModelSettingsRepository.kt`
  Purpose: persist `executionMode` and migrate old `preferOnDevice` values.
- `app/src/main/java/com/mindflow/app/data/localmodel/OnDeviceAiClient.kt`
  Purpose: expose generic on-device task methods beyond Flow-only prompts.
- `app/src/main/java/com/mindflow/app/data/topic/AiServiceClient.kt`
  Purpose: add cloud graph stage methods and keep existing cloud task methods behind provider adapter.
- `app/src/main/java/com/mindflow/app/data/topic/AiTopicExtractor.kt`
  Purpose: switch topic extraction to router-backed execution.
- `app/src/main/java/com/mindflow/app/data/topic/AiTagExtractor.kt`
  Purpose: switch tag extraction to router-backed execution.
- `app/src/main/java/com/mindflow/app/data/topic/AiFolderClassifier.kt`
  Purpose: switch folder classification to router-backed execution.
- `app/src/main/java/com/mindflow/app/ui/screens/editor/NoteEditorViewModel.kt`
  Purpose: delegate polish to `ContentPolishPlanner` instead of calling `AiServiceClient` directly.
- `app/src/main/java/com/mindflow/app/data/wiki/ConceptGraphPlanner.kt`
  Purpose: replace single-shot cloud generation with staged router-driven execution.
- `app/src/main/java/com/mindflow/app/di/AppContainer.kt`
  Purpose: wire providers, codec, router, trace recorder, and new planner dependencies.
- `app/src/main/java/com/mindflow/app/ui/screens/settings/SettingsViewModel.kt`
  Purpose: expose three execution modes and save them.
- `app/src/main/java/com/mindflow/app/ui/screens/settings/SettingsScreen.kt`
  Purpose: replace the old on-device preference toggle with explicit three-mode selection.

### Test

- `app/src/test/java/com/mindflow/app/data/ai/AiTaskRouterTest.kt`
- `app/src/test/java/com/mindflow/app/data/settings/OnDeviceExecutionModeCodecTest.kt`
- `app/src/test/java/com/mindflow/app/data/localmodel/GemmaTaskPromptFactoryTest.kt`
- `app/src/test/java/com/mindflow/app/data/topic/AiTaskBackedMetadataExtractorsTest.kt`
- `app/src/test/java/com/mindflow/app/data/topic/ContentPolishPlannerTest.kt`
- `app/src/test/java/com/mindflow/app/data/ai/AiTaskTraceRecorderTest.kt`
- `app/src/test/java/com/mindflow/app/data/wiki/ConceptGraphPlannerTest.kt`
- `app/src/test/java/com/mindflow/app/data/wiki/DirectionWikiCoordinatorConceptGraphTest.kt`

### Task 1: Add the Typed Router Core

**Files:**
- Create: `app/src/main/java/com/mindflow/app/data/ai/AiExecutionMode.kt`
- Create: `app/src/main/java/com/mindflow/app/data/ai/AiTaskModels.kt`
- Create: `app/src/main/java/com/mindflow/app/data/ai/AiTaskRouter.kt`
- Test: `app/src/test/java/com/mindflow/app/data/ai/AiTaskRouterTest.kt`

- [ ] **Step 1: Write the failing router tests**

```kotlin
package com.mindflow.app.data.ai

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class AiTaskRouterTest {
    @Test
    fun `automatic mode keeps on-device result when quality gate passes`() = runTest {
        val router = AiTaskRouter(
            resolveMode = { AiExecutionMode.AUTOMATIC },
            onDeviceProvider = FakeProvider(
                AiTaskPayload.Topic(topic = "睡眠恢复", confidence = 0.86f)
            ),
            cloudProvider = FakeProvider(
                AiTaskPayload.Topic(topic = "云端标题", confidence = 0.95f)
            ),
        )

        val result = router.run(
            AiTaskRequest(
                type = AiTaskType.EXTRACT_TOPIC,
                input = AiTaskInput.NoteText("睡眠影响恢复"),
                validate = { payload ->
                    val topic = payload as AiTaskPayload.Topic
                    topic.topic.isNotBlank()
                },
            )
        )

        assertThat((result.payload as AiTaskPayload.Topic).topic).isEqualTo("睡眠恢复")
        assertThat(result.meta.providerUsed).isEqualTo(AiProvider.ON_DEVICE)
        assertThat(result.meta.fallbackOccurred).isFalse()
    }

    @Test
    fun `automatic mode falls back to cloud when on-device payload fails quality gate`() = runTest {
        val router = AiTaskRouter(
            resolveMode = { AiExecutionMode.AUTOMATIC },
            onDeviceProvider = FakeProvider(
                AiTaskPayload.Topic(topic = "记录", confidence = 0.22f)
            ),
            cloudProvider = FakeProvider(
                AiTaskPayload.Topic(topic = "睡眠与恢复", confidence = 0.91f)
            ),
        )

        val result = router.run(
            AiTaskRequest(
                type = AiTaskType.EXTRACT_TOPIC,
                input = AiTaskInput.NoteText("睡眠影响恢复"),
                validate = { payload ->
                    val topic = payload as AiTaskPayload.Topic
                    topic.topic.length >= 3 && topic.topic != "记录"
                },
            )
        )

        assertThat((result.payload as AiTaskPayload.Topic).topic).isEqualTo("睡眠与恢复")
        assertThat(result.meta.providerUsed).isEqualTo(AiProvider.CLOUD)
        assertThat(result.meta.fallbackOccurred).isTrue()
        assertThat(result.meta.fallbackReason).isEqualTo("quality_gate_failed")
    }
}
```

- [ ] **Step 2: Run the router tests and verify they fail**

Run:

```bash
JAVA_HOME=$HOME/.jdks/temurin-21 ./gradlew --no-daemon :app:testDebugUnitTest --tests com.mindflow.app.data.ai.AiTaskRouterTest
```

Expected: FAIL with unresolved references for `AiExecutionMode`, `AiTaskRouter`, `AiTaskRequest`, `AiTaskPayload`, and `AiProvider`.

- [ ] **Step 3: Implement the router primitives and fallback logic**

```kotlin
package com.mindflow.app.data.ai

enum class AiExecutionMode {
    AUTOMATIC,
    ON_DEVICE_ONLY,
    CLOUD_ONLY,
}

enum class AiProvider {
    ON_DEVICE,
    CLOUD,
}

enum class AiTaskType {
    POLISH_CONTENT,
    EXTRACT_TOPIC,
    EXTRACT_TAGS,
    CLASSIFY_CATEGORY,
    GRAPH_EXTRACT_CONCEPTS,
    GRAPH_CANONICALIZE_CONCEPTS,
    GRAPH_GENERATE_RELATIONS,
}

sealed interface AiTaskInput {
    data class NoteText(val content: String) : AiTaskInput
    data class GraphContext(val contextSummary: String) : AiTaskInput
}

sealed interface AiTaskPayload {
    data class Topic(val topic: String, val confidence: Float) : AiTaskPayload
    data class Tags(val tags: List<String>, val primaryCategory: String? = null) : AiTaskPayload
    data class Folder(val folderKey: String, val confidence: Float) : AiTaskPayload
    data class Polish(val polishedText: String, val changeSummary: String) : AiTaskPayload
    data class GraphConcepts(val concepts: List<String>) : AiTaskPayload
    data class GraphCanonicalization(val canonical: Map<String, List<String>>) : AiTaskPayload
    data class GraphRelations(val relations: List<GraphRelation>) : AiTaskPayload
}

data class GraphRelation(
    val fromConceptId: String,
    val toConceptId: String,
    val relationType: String,
    val reasonLine: String,
    val confidence: Float,
)

data class AiTaskMeta(
    val providerUsed: AiProvider,
    val fallbackOccurred: Boolean,
    val fallbackReason: String? = null,
    val latencyMs: Long,
    val qualitySignals: Map<String, String> = emptyMap(),
)

data class AiTaskResult<T : AiTaskPayload>(
    val payload: T,
    val meta: AiTaskMeta,
)

class AiTaskRoutingException(
    val mode: AiExecutionMode,
    val taskType: AiTaskType,
    val firstFailureReason: String?,
) : IllegalStateException("No provider produced a valid payload for $taskType in $mode")

data class AiTaskRequest<T : AiTaskPayload>(
    val type: AiTaskType,
    val input: AiTaskInput,
    val validate: (T) -> Boolean,
)

fun interface AiTaskProvider {
    suspend fun <T : AiTaskPayload> run(request: AiTaskRequest<T>): T?
}

class AiTaskRouter(
    private val resolveMode: suspend () -> AiExecutionMode,
    private val onDeviceProvider: AiTaskProvider,
    private val cloudProvider: AiTaskProvider,
) {
    suspend fun <T : AiTaskPayload> run(request: AiTaskRequest<T>): AiTaskResult<T> {
        val mode = resolveMode()
        val startedAt = System.currentTimeMillis()
        val providers = when (mode) {
            AiExecutionMode.AUTOMATIC -> listOf(AiProvider.ON_DEVICE, AiProvider.CLOUD)
            AiExecutionMode.ON_DEVICE_ONLY -> listOf(AiProvider.ON_DEVICE)
            AiExecutionMode.CLOUD_ONLY -> listOf(AiProvider.CLOUD)
        }

        var firstFailureReason: String? = null
        for ((index, provider) in providers.withIndex()) {
            val payload = when (provider) {
                AiProvider.ON_DEVICE -> onDeviceProvider.run(request)
                AiProvider.CLOUD -> cloudProvider.run(request)
            }
            if (payload == null) {
                if (firstFailureReason == null) firstFailureReason = "empty_payload"
                continue
            }
            if (!request.validate(payload)) {
                if (firstFailureReason == null) firstFailureReason = "quality_gate_failed"
                continue
            }
            return AiTaskResult(
                payload = payload,
                meta = AiTaskMeta(
                    providerUsed = provider,
                    fallbackOccurred = index > 0,
                    fallbackReason = if (index > 0) firstFailureReason else null,
                    latencyMs = System.currentTimeMillis() - startedAt,
                ),
            )
        }

        throw AiTaskRoutingException(mode = mode, taskType = request.type, firstFailureReason = firstFailureReason)
    }
}
```

- [ ] **Step 4: Re-run the router tests and verify they pass**

Run:

```bash
JAVA_HOME=$HOME/.jdks/temurin-21 ./gradlew --no-daemon :app:testDebugUnitTest --tests com.mindflow.app.data.ai.AiTaskRouterTest
```

Expected: PASS with 2 tests.

- [ ] **Step 5: Commit the router core**

```bash
git add app/src/main/java/com/mindflow/app/data/ai/AiExecutionMode.kt   app/src/main/java/com/mindflow/app/data/ai/AiTaskModels.kt   app/src/main/java/com/mindflow/app/data/ai/AiTaskRouter.kt   app/src/test/java/com/mindflow/app/data/ai/AiTaskRouterTest.kt

git commit -m "feat: add typed AI task router core"
```

### Task 2: Persist the Global Execution Mode and Surface It in Settings

**Files:**
- Create: `app/src/main/java/com/mindflow/app/data/settings/OnDeviceExecutionModeCodec.kt`
- Modify: `app/src/main/java/com/mindflow/app/data/model/OnDeviceModelSettings.kt`
- Modify: `app/src/main/java/com/mindflow/app/data/settings/PreferencesOnDeviceModelSettingsRepository.kt`
- Modify: `app/src/main/java/com/mindflow/app/ui/screens/settings/SettingsViewModel.kt`
- Modify: `app/src/main/java/com/mindflow/app/ui/screens/settings/SettingsScreen.kt`
- Test: `app/src/test/java/com/mindflow/app/data/settings/OnDeviceExecutionModeCodecTest.kt`

- [ ] **Step 1: Write the failing codec migration tests**

```kotlin
package com.mindflow.app.data.settings

import com.google.common.truth.Truth.assertThat
import com.mindflow.app.data.ai.AiExecutionMode
import org.junit.Test

class OnDeviceExecutionModeCodecTest {
    @Test
    fun `legacy preferOnDevice true migrates to automatic`() {
        assertThat(OnDeviceExecutionModeCodec.decode(raw = null, legacyPreferOnDevice = true))
            .isEqualTo(AiExecutionMode.AUTOMATIC)
    }

    @Test
    fun `legacy preferOnDevice false migrates to cloud only`() {
        assertThat(OnDeviceExecutionModeCodec.decode(raw = null, legacyPreferOnDevice = false))
            .isEqualTo(AiExecutionMode.CLOUD_ONLY)
    }

    @Test
    fun `explicit stored mode wins over legacy flag`() {
        assertThat(OnDeviceExecutionModeCodec.decode(raw = "ON_DEVICE_ONLY", legacyPreferOnDevice = false))
            .isEqualTo(AiExecutionMode.ON_DEVICE_ONLY)
    }
}
```

- [ ] **Step 2: Run the codec tests and verify they fail**

Run:

```bash
JAVA_HOME=$HOME/.jdks/temurin-21 ./gradlew --no-daemon :app:testDebugUnitTest --tests com.mindflow.app.data.settings.OnDeviceExecutionModeCodecTest
```

Expected: FAIL with unresolved references for `OnDeviceExecutionModeCodec` and `AiExecutionMode` imports in settings code.

- [ ] **Step 3: Implement execution-mode persistence and settings UI state**

```kotlin
package com.mindflow.app.data.settings

import com.mindflow.app.data.ai.AiExecutionMode

object OnDeviceExecutionModeCodec {
    fun decode(raw: String?, legacyPreferOnDevice: Boolean?): AiExecutionMode {
        raw?.trim()?.takeIf { it.isNotBlank() }?.let { candidate ->
            return runCatching { AiExecutionMode.valueOf(candidate) }.getOrElse { AiExecutionMode.AUTOMATIC }
        }
        return if (legacyPreferOnDevice == true) {
            AiExecutionMode.AUTOMATIC
        } else {
            AiExecutionMode.CLOUD_ONLY
        }
    }
}
```

```kotlin
data class OnDeviceModelSettings(
    val modelLabel: String = DEFAULT_MODEL_LABEL,
    val modelDownloadUrl: String = DEFAULT_MODEL_DOWNLOAD_URL,
    val executionMode: AiExecutionMode = AiExecutionMode.AUTOMATIC,
    val localModelPath: String = "",
    // existing fields unchanged
) {
    @Deprecated("Use executionMode")
    val preferOnDevice: Boolean
        get() = executionMode != AiExecutionMode.CLOUD_ONLY
}
```

```kotlin
// PreferencesOnDeviceModelSettingsRepository.kt
OnDeviceModelSettings(
    modelLabel = preferences[MODEL_LABEL] ?: OnDeviceModelSettings.DEFAULT_MODEL_LABEL,
    modelDownloadUrl = OnDeviceModelSettings.normalizeDownloadUrl(
        preferences[MODEL_DOWNLOAD_URL]?.takeIf { it.isNotBlank() }
            ?: OnDeviceModelSettings.DEFAULT_MODEL_DOWNLOAD_URL,
    ),
    executionMode = OnDeviceExecutionModeCodec.decode(
        raw = preferences[EXECUTION_MODE],
        legacyPreferOnDevice = preferences[PREFER_ON_DEVICE],
    ),
    localModelPath = preferences[LOCAL_MODEL_PATH].orEmpty(),
    // existing fields unchanged
)
```

```kotlin
// SettingsViewModel.kt
val aiExecutionMode: AiExecutionMode = AiExecutionMode.AUTOMATIC

fun onAiExecutionModeChange(value: AiExecutionMode) {
    _uiState.update { it.copy(aiExecutionMode = value) }
}
```

```kotlin
// SettingsScreen.kt
FilterChip(
    selected = uiState.aiExecutionMode == AiExecutionMode.AUTOMATIC,
    onClick = { onAiExecutionModeChange(AiExecutionMode.AUTOMATIC) },
    label = { Text("自动") },
)
FilterChip(
    selected = uiState.aiExecutionMode == AiExecutionMode.ON_DEVICE_ONLY,
    onClick = { onAiExecutionModeChange(AiExecutionMode.ON_DEVICE_ONLY) },
    label = { Text("仅端侧") },
)
FilterChip(
    selected = uiState.aiExecutionMode == AiExecutionMode.CLOUD_ONLY,
    onClick = { onAiExecutionModeChange(AiExecutionMode.CLOUD_ONLY) },
    label = { Text("仅云侧") },
)
```

- [ ] **Step 4: Re-run the codec tests and compile the settings slice**

Run:

```bash
JAVA_HOME=$HOME/.jdks/temurin-21 ./gradlew --no-daemon :app:testDebugUnitTest --tests com.mindflow.app.data.settings.OnDeviceExecutionModeCodecTest :app:compileDebugKotlin
```

Expected: PASS for the codec tests and successful Kotlin compilation.

- [ ] **Step 5: Commit the execution-mode settings slice**

```bash
git add app/src/main/java/com/mindflow/app/data/settings/OnDeviceExecutionModeCodec.kt   app/src/main/java/com/mindflow/app/data/model/OnDeviceModelSettings.kt   app/src/main/java/com/mindflow/app/data/settings/PreferencesOnDeviceModelSettingsRepository.kt   app/src/main/java/com/mindflow/app/ui/screens/settings/SettingsViewModel.kt   app/src/main/java/com/mindflow/app/ui/screens/settings/SettingsScreen.kt   app/src/test/java/com/mindflow/app/data/settings/OnDeviceExecutionModeCodecTest.kt

git commit -m "feat: add global AI execution mode settings"
```

### Task 3: Add Gemma Task Prompts and Provider Adapters

**Files:**
- Create: `app/src/main/java/com/mindflow/app/data/localmodel/GemmaTaskPromptFactory.kt`
- Create: `app/src/main/java/com/mindflow/app/data/ai/OnDeviceAiTaskProvider.kt`
- Create: `app/src/main/java/com/mindflow/app/data/ai/CloudAiTaskProvider.kt`
- Modify: `app/src/main/java/com/mindflow/app/data/localmodel/OnDeviceAiClient.kt`
- Modify: `app/src/main/java/com/mindflow/app/data/topic/AiServiceClient.kt`
- Test: `app/src/test/java/com/mindflow/app/data/localmodel/GemmaTaskPromptFactoryTest.kt`

- [ ] **Step 1: Write the failing prompt-factory tests**

```kotlin
package com.mindflow.app.data.localmodel

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GemmaTaskPromptFactoryTest {
    @Test
    fun `polish prompt forbids inventing new facts`() {
        val prompt = GemmaTaskPromptFactory.polish("原文")
        assertThat(prompt).contains("不新增原文没有的信息")
        assertThat(prompt).contains("polishedText")
        assertThat(prompt).contains("changeSummary")
    }

    @Test
    fun `topic prompt rejects generic labels`() {
        val prompt = GemmaTaskPromptFactory.extractTopic("原文")
        assertThat(prompt).contains("禁止使用“记录”“想法”“学习”“随想”")
        assertThat(prompt).contains("whyThisTopic")
    }

    @Test
    fun `graph relation prompt stays local and layer-scoped`() {
        val prompt = GemmaTaskPromptFactory.generateGraphRelations("sleep", listOf("recovery", "focus"))
        assertThat(prompt).contains("只判断中心知识点与候选邻居之间的关系")
        assertThat(prompt).doesNotContain("生成整张图")
    }
}
```

- [ ] **Step 2: Run the prompt-factory tests and verify they fail**

Run:

```bash
JAVA_HOME=$HOME/.jdks/temurin-21 ./gradlew --no-daemon :app:testDebugUnitTest --tests com.mindflow.app.data.localmodel.GemmaTaskPromptFactoryTest
```

Expected: FAIL with unresolved references for `GemmaTaskPromptFactory`.

- [ ] **Step 3: Implement Gemma prompts and typed providers**

```kotlin
package com.mindflow.app.data.localmodel

object GemmaTaskPromptFactory {
    fun polish(content: String): String = """
        你在做中文正文润色。
        目标：保留原意、保留原始语气、做最小充分修改。
        约束：不新增原文没有的信息；不扩写观点；优先修病句、重复、跳跃表达。
        只返回 JSON：{"polishedText":"...","changeSummary":"..."}
        原文：$content
    """.trimIndent()

    fun extractTopic(content: String): String = """
        你在提取笔记主题。
        只输出一个短而可检索的主题短语。
        禁止使用“记录”“想法”“学习”“随想”等空泛主题。
        只返回 JSON：{"topic":"...","confidence":0.0,"whyThisTopic":"..."}
        原文：$content
    """.trimIndent()

    fun extractTags(content: String): String = """
        你在提取主分类和少量高密度标签。
        只返回 JSON：{"primaryCategory":"...","tags":["..."],"discardedCandidates":["..."]}
        原文：$content
    """.trimIndent()

    fun classifyFolder(content: String): String = """
        你只允许输出 work、life、project、health 之一。
        只返回 JSON：{"folderKey":"...","confidence":0.0}
        原文：$content
    """.trimIndent()

    fun extractGraphConcepts(context: String): String = "概念候选抽取 JSON prompt：$context"
    fun canonicalizeGraphConcepts(context: String): String = "概念归并 JSON prompt：$context"
    fun generateGraphRelations(center: String, neighbors: List<String>): String =
        "只判断中心知识点与候选邻居之间的关系。中心：$center 邻居：${neighbors.joinToString()}"
}
```

```kotlin
interface OnDeviceAiClient {
    suspend fun testModel(settings: OnDeviceModelSettings): AiConnectionResult
    suspend fun generateFlowMainline(settings: OnDeviceModelSettings, contextSummary: String): AiChatResult
    suspend fun generateFlowSettledKnowledge(settings: OnDeviceModelSettings, contextSummary: String): AiChatResult
    suspend fun generateFlowBreakthroughGap(settings: OnDeviceModelSettings, contextSummary: String): AiChatResult
    suspend fun generateLocalKnowledgeShape(settings: OnDeviceModelSettings, contextSummary: String): AiChatResult
    suspend fun generateLocalOpenQuestion(settings: OnDeviceModelSettings, contextSummary: String): AiChatResult
    suspend fun generateEditorRecall(settings: OnDeviceModelSettings, contextSummary: String): AiChatResult
    suspend fun extractTopic(settings: OnDeviceModelSettings, content: String): AiChatResult
    suspend fun extractTags(settings: OnDeviceModelSettings, content: String): AiChatResult
    suspend fun classifyFolder(settings: OnDeviceModelSettings, content: String): AiChatResult
    suspend fun polishContent(settings: OnDeviceModelSettings, content: String): AiChatResult
    suspend fun extractConceptGraphConcepts(settings: OnDeviceModelSettings, contextSummary: String): AiChatResult
    suspend fun canonicalizeConceptGraphConcepts(settings: OnDeviceModelSettings, contextSummary: String): AiChatResult
    suspend fun generateConceptGraphRelations(settings: OnDeviceModelSettings, contextSummary: String): AiChatResult
}
```

```kotlin
class OnDeviceAiTaskProvider(
    private val settingsRepository: OnDeviceModelSettingsRepository,
    private val client: OnDeviceAiClient,
) : AiTaskProvider {
    override suspend fun <T : AiTaskPayload> run(request: AiTaskRequest<T>): T? {
        val settings = settingsRepository.getCurrent()
        if (!settings.isReady) return null
        val chatResult = when (request.type) {
            AiTaskType.EXTRACT_TOPIC -> client.extractTopic(settings, (request.input as AiTaskInput.NoteText).content)
            AiTaskType.EXTRACT_TAGS -> client.extractTags(settings, (request.input as AiTaskInput.NoteText).content)
            AiTaskType.CLASSIFY_CATEGORY -> client.classifyFolder(settings, (request.input as AiTaskInput.NoteText).content)
            AiTaskType.POLISH_CONTENT -> client.polishContent(settings, (request.input as AiTaskInput.NoteText).content)
            AiTaskType.GRAPH_EXTRACT_CONCEPTS -> client.extractConceptGraphConcepts(settings, (request.input as AiTaskInput.GraphContext).contextSummary)
            AiTaskType.GRAPH_CANONICALIZE_CONCEPTS -> client.canonicalizeConceptGraphConcepts(settings, (request.input as AiTaskInput.GraphContext).contextSummary)
            AiTaskType.GRAPH_GENERATE_RELATIONS -> client.generateConceptGraphRelations(settings, (request.input as AiTaskInput.GraphContext).contextSummary)
        }
        return chatResult.toPayloadOrNull(request.type) as T?
    }
}
```

```kotlin
class CloudAiTaskProvider(
    private val settingsRepository: AiSettingsRepository,
    private val client: AiServiceClient,
) : AiTaskProvider {
    override suspend fun <T : AiTaskPayload> run(request: AiTaskRequest<T>): T? {
        val settings = settingsRepository.getCurrent()
        if (!settings.aiEnabled || !settings.isConfigured) return null
        val chatResult = when (request.type) {
            AiTaskType.EXTRACT_TOPIC -> client.extractTopic(settings, (request.input as AiTaskInput.NoteText).content)
            AiTaskType.EXTRACT_TAGS -> client.extractTags(settings, (request.input as AiTaskInput.NoteText).content)
            AiTaskType.CLASSIFY_CATEGORY -> client.classifyFolder(settings, (request.input as AiTaskInput.NoteText).content)
            AiTaskType.POLISH_CONTENT -> client.polishContent(settings, (request.input as AiTaskInput.NoteText).content)
            AiTaskType.GRAPH_EXTRACT_CONCEPTS -> client.extractConceptGraphConcepts(settings, (request.input as AiTaskInput.GraphContext).contextSummary)
            AiTaskType.GRAPH_CANONICALIZE_CONCEPTS -> client.canonicalizeConceptGraphConcepts(settings, (request.input as AiTaskInput.GraphContext).contextSummary)
            AiTaskType.GRAPH_GENERATE_RELATIONS -> client.generateConceptGraphRelations(settings, (request.input as AiTaskInput.GraphContext).contextSummary)
        }
        return chatResult.toPayloadOrNull(request.type) as T?
    }
}
```

- [ ] **Step 4: Re-run the prompt tests and compile the provider slice**

Run:

```bash
JAVA_HOME=$HOME/.jdks/temurin-21 ./gradlew --no-daemon :app:testDebugUnitTest --tests com.mindflow.app.data.localmodel.GemmaTaskPromptFactoryTest :app:compileDebugKotlin
```

Expected: PASS for prompt tests and successful Kotlin compilation.

- [ ] **Step 5: Commit the provider and prompt layer**

```bash
git add app/src/main/java/com/mindflow/app/data/localmodel/GemmaTaskPromptFactory.kt   app/src/main/java/com/mindflow/app/data/ai/OnDeviceAiTaskProvider.kt   app/src/main/java/com/mindflow/app/data/ai/CloudAiTaskProvider.kt   app/src/main/java/com/mindflow/app/data/localmodel/OnDeviceAiClient.kt   app/src/main/java/com/mindflow/app/data/topic/AiServiceClient.kt   app/src/test/java/com/mindflow/app/data/localmodel/GemmaTaskPromptFactoryTest.kt

git commit -m "feat: add Gemma task prompts and AI providers"
```

### Task 4: Migrate Metadata Extraction and Polish to the Router

**Files:**
- Create: `app/src/main/java/com/mindflow/app/data/topic/ContentPolishPlanner.kt`
- Modify: `app/src/main/java/com/mindflow/app/data/topic/AiTopicExtractor.kt`
- Modify: `app/src/main/java/com/mindflow/app/data/topic/AiTagExtractor.kt`
- Modify: `app/src/main/java/com/mindflow/app/data/topic/AiFolderClassifier.kt`
- Modify: `app/src/main/java/com/mindflow/app/ui/screens/editor/NoteEditorViewModel.kt`
- Modify: `app/src/main/java/com/mindflow/app/di/AppContainer.kt`
- Test: `app/src/test/java/com/mindflow/app/data/topic/AiTaskBackedMetadataExtractorsTest.kt`
- Test: `app/src/test/java/com/mindflow/app/data/topic/ContentPolishPlannerTest.kt`

- [ ] **Step 1: Write the failing metadata and polish tests**

```kotlin
package com.mindflow.app.data.topic

import com.google.common.truth.Truth.assertThat
import com.mindflow.app.data.ai.AiExecutionMode
import com.mindflow.app.data.ai.AiProvider
import com.mindflow.app.data.ai.AiTaskInput
import com.mindflow.app.data.ai.AiTaskMeta
import com.mindflow.app.data.ai.AiTaskPayload
import com.mindflow.app.data.ai.AiTaskRequest
import com.mindflow.app.data.ai.AiTaskResult
import com.mindflow.app.data.ai.AiTaskRouter
import kotlinx.coroutines.test.runTest
import org.junit.Test

class AiTaskBackedMetadataExtractorsTest {
    @Test
    fun `topic extractor uses router payload and keeps cacheable normalized topic`() = runTest {
        val extractor = AiTopicExtractor(
            aiTaskRouter = FakeTopicRouter("睡眠恢复"),
        )

        val result = extractor.extract("睡眠影响恢复")

        assertThat(result.topic).isEqualTo("睡眠恢复")
        assertThat(result.notice).isNull()
    }
}

class ContentPolishPlannerTest {
    @Test
    fun `polish planner rejects unchanged payloads`() = runTest {
        val planner = ContentPolishPlanner(
            aiTaskRouter = FakePolishRouter(polished = "原文", summary = "无变化"),
        )

        val result = planner.polish("原文")

        assertThat(result).isEqualTo(ContentPolishResult.NoChange)
    }
}
```

- [ ] **Step 2: Run the metadata and polish tests and verify they fail**

Run:

```bash
JAVA_HOME=$HOME/.jdks/temurin-21 ./gradlew --no-daemon :app:testDebugUnitTest --tests com.mindflow.app.data.topic.AiTaskBackedMetadataExtractorsTest --tests com.mindflow.app.data.topic.ContentPolishPlannerTest
```

Expected: FAIL with constructor mismatches and missing `ContentPolishPlanner` / `ContentPolishResult`.

- [ ] **Step 3: Route extractors and polish through typed task calls**

```kotlin
class AiTopicExtractor(
    private val aiTaskRouter: AiTaskRouter,
) {
    suspend fun extract(content: String): AiTopicResult = withContext(Dispatchers.IO) {
        val result = runCatching {
            aiTaskRouter.run(
                AiTaskRequest(
                    type = AiTaskType.EXTRACT_TOPIC,
                    input = AiTaskInput.NoteText(content),
                    validate = { payload ->
                        val topic = payload as AiTaskPayload.Topic
                        topic.topic.isNotBlank() && topic.topic !in setOf("记录", "想法", "学习", "随想")
                    },
                )
            )
        }.getOrNull() ?: return@withContext AiTopicResult(notice = null)

        val normalized = normalize((result.payload as AiTaskPayload.Topic).topic)
        AiTopicResult(topic = normalized)
    }
}
```

```kotlin
sealed interface ContentPolishResult {
    data class Success(val polishedText: String, val summary: String) : ContentPolishResult
    data object NoChange : ContentPolishResult
    data class Failure(val message: String) : ContentPolishResult
}

class ContentPolishPlanner(
    private val aiTaskRouter: AiTaskRouter,
) {
    suspend fun polish(content: String): ContentPolishResult {
        val result = runCatching {
            aiTaskRouter.run(
                AiTaskRequest(
                    type = AiTaskType.POLISH_CONTENT,
                    input = AiTaskInput.NoteText(content),
                    validate = { payload ->
                        val polish = payload as AiTaskPayload.Polish
                        polish.polishedText.isNotBlank()
                    },
                )
            )
        }.getOrElse { error ->
            if (error is AiTaskRoutingException && error.mode == AiExecutionMode.ON_DEVICE_ONLY) {
                return ContentPolishResult.Failure("端侧模型这次没有给出可用结果")
            }
            return ContentPolishResult.Failure(error.message ?: "AI 润色失败")
        }

        val payload = result.payload as AiTaskPayload.Polish
        val polished = payload.polishedText.trim()
        return if (polished == content.trim()) {
            ContentPolishResult.NoChange
        } else {
            ContentPolishResult.Success(polishedText = polished, summary = payload.changeSummary)
        }
    }
}
```

```kotlin
// NoteEditorViewModel.kt
when (val result = contentPolishPlanner.polish(state.content)) {
    is ContentPolishResult.Success -> {
        _uiState.update {
            it.copy(
                isPolishingContent = false,
                polishedOriginalContent = state.content,
                polishedCandidateContent = result.polishedText,
            )
        }
        _events.emit(NoteEditorEvent.Message("AI 已生成润色结果，长按可对照原文"))
    }
    ContentPolishResult.NoChange -> {
        _events.emit(NoteEditorEvent.Message("AI 返回内容与当前正文接近，没有生成新的润色稿"))
        _uiState.update { it.copy(isPolishingContent = false) }
    }
    is ContentPolishResult.Failure -> {
        _events.emit(NoteEditorEvent.Message(result.message))
        _uiState.update { it.copy(isPolishingContent = false) }
    }
}
```

- [ ] **Step 4: Re-run the metadata and polish tests and verify they pass**

Run:

```bash
JAVA_HOME=$HOME/.jdks/temurin-21 ./gradlew --no-daemon :app:testDebugUnitTest --tests com.mindflow.app.data.topic.AiTaskBackedMetadataExtractorsTest --tests com.mindflow.app.data.topic.ContentPolishPlannerTest
```

Expected: PASS for both test classes.

- [ ] **Step 5: Commit the metadata and polish migration**

```bash
git add app/src/main/java/com/mindflow/app/data/topic/ContentPolishPlanner.kt   app/src/main/java/com/mindflow/app/data/topic/AiTopicExtractor.kt   app/src/main/java/com/mindflow/app/data/topic/AiTagExtractor.kt   app/src/main/java/com/mindflow/app/data/topic/AiFolderClassifier.kt   app/src/main/java/com/mindflow/app/ui/screens/editor/NoteEditorViewModel.kt   app/src/main/java/com/mindflow/app/di/AppContainer.kt   app/src/test/java/com/mindflow/app/data/topic/AiTaskBackedMetadataExtractorsTest.kt   app/src/test/java/com/mindflow/app/data/topic/ContentPolishPlannerTest.kt

git commit -m "feat: route metadata and polish tasks through AI router"
```

### Task 5: Convert Concept Graph Generation to a Staged Router Pipeline

**Files:**
- Modify: `app/src/main/java/com/mindflow/app/data/wiki/ConceptGraphPlanner.kt`
- Modify: `app/src/main/java/com/mindflow/app/di/AppContainer.kt`
- Test: `app/src/test/java/com/mindflow/app/data/wiki/ConceptGraphPlannerTest.kt`
- Test: `app/src/test/java/com/mindflow/app/data/wiki/DirectionWikiCoordinatorConceptGraphTest.kt`

- [ ] **Step 1: Write the failing staged-graph tests**

```kotlin
package com.mindflow.app.data.wiki

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ConceptGraphPlannerTest {
    @Test
    fun `summarize uses staged router requests before rule fallback`() = runTest {
        val planner = plannerWithRouter(
            conceptPayload = listOf("sleep", "recovery"),
            canonicalPayload = mapOf("sleep" to listOf("rest"), "recovery" to emptyList()),
            relationPayload = listOf(
                relation(
                    from = "sleep",
                    to = "recovery",
                    type = "supports",
                    reason = "睡眠支持恢复",
                    confidence = 0.9f,
                )
            ),
        )

        val snapshot = planner.summarize(
            listOf(candidate(conceptId = "sleep", title = "睡眠"), candidate(conceptId = "recovery", title = "恢复"))
        )

        assertThat(snapshot.nodes.map { it.conceptId }).containsExactly("sleep", "recovery")
        assertThat(snapshot.edges).hasSize(1)
        assertThat(snapshot.source).isEqualTo("router+rule")
    }

    @Test
    fun `summarize falls back only relation stage when relation payload fails`() = runTest {
        val planner = plannerWithRouter(
            conceptPayload = listOf("sleep", "recovery"),
            canonicalPayload = mapOf("sleep" to listOf("rest"), "recovery" to emptyList()),
            relationPayload = emptyList(),
        )

        val snapshot = planner.summarize(
            listOf(
                candidate(conceptId = "sleep", title = "睡眠", sourceIds = listOf("note-1")),
                candidate(conceptId = "recovery", title = "恢复", sourceIds = listOf("note-1")),
            )
        )

        assertThat(snapshot.edges).isNotEmpty()
        assertThat(snapshot.edges.first().reasonLine).contains("共同出现")
    }
}
```

- [ ] **Step 2: Run the graph tests and verify they fail**

Run:

```bash
JAVA_HOME=$HOME/.jdks/temurin-21 ./gradlew --no-daemon :app:testDebugUnitTest --tests com.mindflow.app.data.wiki.ConceptGraphPlannerTest --tests com.mindflow.app.data.wiki.DirectionWikiCoordinatorConceptGraphTest
```

Expected: FAIL because `ConceptGraphPlanner` still expects a single cloud snapshot call.

- [ ] **Step 3: Refactor `ConceptGraphPlanner` to three router stages plus deterministic fallback**

```kotlin
class ConceptGraphPlanner(
    private val aiTaskRouter: AiTaskRouter,
) {
    suspend fun summarize(candidates: List<ConceptGraphCandidate>): ConceptGraphSnapshot {
        if (candidates.isEmpty()) return ConceptGraphSnapshot()

        val generatedAt = System.currentTimeMillis()
        val ruleSnapshot = buildRuleSnapshot(candidates, generatedAt)
        val conceptPayload = runStage(
            type = AiTaskType.GRAPH_EXTRACT_CONCEPTS,
            context = buildConceptExtractionContext(candidates),
            validate = { payload ->
                val concepts = payload as AiTaskPayload.GraphConcepts
                concepts.concepts.isNotEmpty()
            },
        ) as? AiTaskPayload.GraphConcepts ?: return ruleSnapshot

        val canonicalPayload = runStage(
            type = AiTaskType.GRAPH_CANONICALIZE_CONCEPTS,
            context = buildCanonicalizationContext(candidates, conceptPayload),
            validate = { payload ->
                val canonical = payload as AiTaskPayload.GraphCanonicalization
                canonical.canonical.isNotEmpty()
            },
        ) as? AiTaskPayload.GraphCanonicalization ?: return mergeCanonicalNodesWithRuleFallback(candidates, generatedAt)

        val relationPayload = runStage(
            type = AiTaskType.GRAPH_GENERATE_RELATIONS,
            context = buildRelationContext(candidates, canonicalPayload),
            validate = { payload ->
                val relations = payload as AiTaskPayload.GraphRelations
                relations.relations.isNotEmpty()
            },
        ) as? AiTaskPayload.GraphRelations

        return buildSnapshotFromStages(
            candidates = candidates,
            concepts = conceptPayload,
            canonical = canonicalPayload,
            relations = relationPayload,
            generatedAt = generatedAt,
        )
    }

    private suspend fun runStage(
        type: AiTaskType,
        context: String,
        validate: (AiTaskPayload) -> Boolean,
    ): AiTaskPayload? = runCatching {
        aiTaskRouter.run(
            AiTaskRequest(
                type = type,
                input = AiTaskInput.GraphContext(context),
                validate = validate,
            )
        ).payload
    }.getOrNull()
}
```

- [ ] **Step 4: Re-run the graph tests and verify they pass**

Run:

```bash
JAVA_HOME=$HOME/.jdks/temurin-21 ./gradlew --no-daemon :app:testDebugUnitTest --tests com.mindflow.app.data.wiki.ConceptGraphPlannerTest --tests com.mindflow.app.data.wiki.DirectionWikiCoordinatorConceptGraphTest
```

Expected: PASS with staged graph coverage and coordinator compatibility still intact.

- [ ] **Step 5: Commit the concept-graph pipeline migration**

```bash
git add app/src/main/java/com/mindflow/app/data/wiki/ConceptGraphPlanner.kt   app/src/main/java/com/mindflow/app/di/AppContainer.kt   app/src/test/java/com/mindflow/app/data/wiki/ConceptGraphPlannerTest.kt   app/src/test/java/com/mindflow/app/data/wiki/DirectionWikiCoordinatorConceptGraphTest.kt

git commit -m "feat: move concept graph generation to staged AI routing"
```

### Task 6: Add Trace Logging, Finish Wiring, and Run the Focused Verification Suite

**Files:**
- Create: `app/src/main/java/com/mindflow/app/data/ai/AiTaskTraceRecorder.kt`
- Modify: `app/src/main/java/com/mindflow/app/data/ai/AiTaskRouter.kt`
- Modify: `app/src/main/java/com/mindflow/app/di/AppContainer.kt`
- Test: `app/src/test/java/com/mindflow/app/data/ai/AiTaskTraceRecorderTest.kt`

- [ ] **Step 1: Write the failing trace recorder test**

```kotlin
package com.mindflow.app.data.ai

import com.google.common.truth.Truth.assertThat
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import org.junit.Test

class AiTaskTraceRecorderTest {
    @Test
    fun `append writes one json line per task`() = runTest {
        val dir = Files.createTempDirectory("ai-traces")
        val recorder = AiTaskTraceRecorder(dir.toFile())

        recorder.append(
            taskType = AiTaskType.EXTRACT_TOPIC,
            meta = AiTaskMeta(
                providerUsed = AiProvider.ON_DEVICE,
                fallbackOccurred = false,
                latencyMs = 42,
                qualitySignals = mapOf("confidence" to "0.86"),
            ),
        )

        val lines = dir.resolve("ai-task-traces.jsonl").toFile().readLines()
        assertThat(lines).hasSize(1)
        assertThat(lines.first()).contains("EXTRACT_TOPIC")
        assertThat(lines.first()).contains("ON_DEVICE")
        assertThat(lines.first()).contains("confidence")

        val latest = dir.resolve("latest-successful-provider.json").toFile().readText()
        assertThat(latest).contains("EXTRACT_TOPIC")
        assertThat(latest).contains("ON_DEVICE")
    }
}
```

- [ ] **Step 2: Run the trace test and verify it fails**

Run:

```bash
JAVA_HOME=$HOME/.jdks/temurin-21 ./gradlew --no-daemon :app:testDebugUnitTest --tests com.mindflow.app.data.ai.AiTaskTraceRecorderTest
```

Expected: FAIL with unresolved reference for `AiTaskTraceRecorder`.

- [ ] **Step 3: Implement the trace recorder and hook it into the router**

```kotlin
package com.mindflow.app.data.ai

import java.io.File
import org.json.JSONObject

class AiTaskTraceRecorder(
    private val directory: File,
) {
    private val traceFile: File by lazy {
        directory.mkdirs()
        File(directory, "ai-task-traces.jsonl")
    }
    private val latestSuccessFile: File by lazy {
        directory.mkdirs()
        File(directory, "latest-successful-provider.json")
    }

    fun append(taskType: AiTaskType, meta: AiTaskMeta) {
        val line = JSONObject(
            linkedMapOf(
                "taskType" to taskType.name,
                "providerUsed" to meta.providerUsed.name,
                "fallbackOccurred" to meta.fallbackOccurred,
                "fallbackReason" to meta.fallbackReason,
                "latencyMs" to meta.latencyMs,
                "qualitySignals" to JSONObject(meta.qualitySignals),
            )
        ).toString()
        traceFile.appendText(line + "
")
    }
}
```

```kotlin
class AiTaskRouter(
    private val resolveMode: suspend () -> AiExecutionMode,
    private val onDeviceProvider: AiTaskProvider,
    private val cloudProvider: AiTaskProvider,
    private val traceRecorder: AiTaskTraceRecorder? = null,
) {
    suspend fun <T : AiTaskPayload> run(request: AiTaskRequest<T>): AiTaskResult<T> {
        // existing provider loop
        val result = AiTaskResult(payload = payload, meta = meta)
        traceRecorder?.append(request.type, result.meta)
        return result
    }
}
```

```kotlin
// AppContainer.kt
private val aiTaskTraceRecorder = AiTaskTraceRecorder(
    File(context.applicationContext.filesDir, "ai-traces")
)

val aiTaskRouter = AiTaskRouter(
    resolveMode = {
        onDeviceModelSettingsRepository.getCurrent().executionMode
    },
    onDeviceProvider = OnDeviceAiTaskProvider(
        settingsRepository = onDeviceModelSettingsRepository,
        client = onDeviceAiClient,
    ),
    cloudProvider = CloudAiTaskProvider(
        settingsRepository = aiSettingsRepository,
        client = aiServiceClient,
    ),
    traceRecorder = aiTaskTraceRecorder,
)
```

- [ ] **Step 4: Run the focused verification suite**

Run:

```bash
JAVA_HOME=$HOME/.jdks/temurin-21 ./gradlew --no-daemon   :app:testDebugUnitTest   --tests com.mindflow.app.data.ai.AiTaskRouterTest   --tests com.mindflow.app.data.settings.OnDeviceExecutionModeCodecTest   --tests com.mindflow.app.data.localmodel.GemmaTaskPromptFactoryTest   --tests com.mindflow.app.data.topic.AiTaskBackedMetadataExtractorsTest   --tests com.mindflow.app.data.topic.ContentPolishPlannerTest   --tests com.mindflow.app.data.ai.AiTaskTraceRecorderTest   --tests com.mindflow.app.data.wiki.ConceptGraphPlannerTest   --tests com.mindflow.app.data.wiki.DirectionWikiCoordinatorConceptGraphTest

JAVA_HOME=$HOME/.jdks/temurin-21 ./gradlew --no-daemon :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin
```

Expected: PASS for the focused unit suite and successful debug compilation.

- [ ] **Step 5: Commit the trace logging and final wiring**

```bash
git add app/src/main/java/com/mindflow/app/data/ai/AiTaskTraceRecorder.kt   app/src/main/java/com/mindflow/app/data/ai/AiTaskRouter.kt   app/src/main/java/com/mindflow/app/di/AppContainer.kt   app/src/test/java/com/mindflow/app/data/ai/AiTaskTraceRecorderTest.kt

git commit -m "feat: log routed AI task traces"
```
