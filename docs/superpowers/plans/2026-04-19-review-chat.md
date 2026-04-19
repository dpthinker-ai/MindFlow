# Review Chat Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a review-page chat flow that lets users ask and discuss their historical notes and structured knowledge, enter a full-screen chat screen on send, and explicitly save useful conversations.

**Architecture:** Add a dedicated `reviewchat` domain with a cloud-first `ReviewChatPlanner`, a separate Room-backed saved-session store, and a `ReviewChatViewModel` that keeps unsaved conversations in memory. Keep the review-page entry lightweight by replacing the current search card with a chat entry card that launches a dedicated full-screen route and can reopen the latest saved conversation read-only.

**Tech Stack:** Kotlin, Jetpack Compose, Navigation Compose, coroutines/StateFlow, Room, existing `AiServiceClient`, existing `OnDeviceAiClient`

---

## File Map

### New files

- `app/src/main/java/com/mindflow/app/data/reviewchat/ReviewChatModels.kt`
  - Domain models for question intent, message role, provider, planner input/output, saved-session summaries.
- `app/src/main/java/com/mindflow/app/data/reviewchat/ReviewChatPromptFactory.kt`
  - Builds cloud and on-device prompts from the assembled context packet.
- `app/src/main/java/com/mindflow/app/data/reviewchat/ReviewChatPlanner.kt`
  - Classifies questions, assembles raw + structured context, routes cloud-first in automatic mode, and returns provider metadata.
- `app/src/main/java/com/mindflow/app/data/reviewchat/ReviewChatSavedConversationRepository.kt`
  - Repository interface for saving and loading chat sessions.
- `app/src/main/java/com/mindflow/app/data/reviewchat/RoomReviewChatSavedConversationRepository.kt`
  - Room-backed implementation of saved chat persistence.
- `app/src/main/java/com/mindflow/app/data/local/reviewchat/ReviewChatSessionEntity.kt`
- `app/src/main/java/com/mindflow/app/data/local/reviewchat/ReviewChatMessageEntity.kt`
- `app/src/main/java/com/mindflow/app/data/local/reviewchat/ReviewChatDao.kt`
- `app/src/main/java/com/mindflow/app/data/local/reviewchat/ReviewChatDatabase.kt`
  - Dedicated Room database for saved review-chat sessions; separate from markdown notes.
- `app/src/main/java/com/mindflow/app/ui/navigation/ReviewChatSeed.kt`
  - Ephemeral launch seed used to open a new unsaved chat or reopen a saved one.
- `app/src/main/java/com/mindflow/app/ui/screens/reviewchat/ReviewChatViewModel.kt`
  - In-memory live conversation state, send/retry/save logic, saved-session loading.
- `app/src/main/java/com/mindflow/app/ui/screens/reviewchat/ReviewChatScreen.kt`
  - Full-screen chat UI.
- `app/src/main/java/com/mindflow/app/ui/screens/flow/ReviewChatEntryCard.kt`
  - Lightweight card replacing the current review-page search card.
- `app/src/test/java/com/mindflow/app/data/reviewchat/ReviewChatPlannerTest.kt`
- `app/src/test/java/com/mindflow/app/ui/screens/reviewchat/ReviewChatViewModelTest.kt`
- `app/src/androidTest/java/com/mindflow/app/data/reviewchat/RoomReviewChatSavedConversationRepositoryTest.kt`
- `app/src/androidTest/java/com/mindflow/app/ui/screens/reviewchat/ReviewChatScreenInstrumentedTest.kt`
- `app/src/androidTest/java/com/mindflow/app/ui/screens/flow/ReviewChatEntryCardInstrumentedTest.kt`

### Modified files

- `app/src/main/java/com/mindflow/app/data/topic/AiServiceClient.kt`
  - Add a review-chat cloud generation method that reuses the existing text completion path.
- `app/src/main/java/com/mindflow/app/data/localmodel/OnDeviceAiClient.kt`
  - Add a review-chat on-device generation method to the interface and LiteRT implementation.
- `app/src/main/java/com/mindflow/app/di/AppContainer.kt`
  - Build the new Room database, repository, and planner, and expose them.
- `app/src/main/java/com/mindflow/app/MainActivity.kt`
  - Thread the new planner/repository into `MindFlowApp`.
- `app/src/main/java/com/mindflow/app/ui/navigation/MindFlowDestinations.kt`
  - Add the review-chat route and route helper.
- `app/src/main/java/com/mindflow/app/ui/MindFlowApp.kt`
  - Add a non-top-level review-chat route, ephemeral seed store, and callbacks from the review page.
- `app/src/main/java/com/mindflow/app/ui/screens/flow/FlowScreen.kt`
  - Replace `SearchRecordsCard` on the review page with the new chat entry card.

## Implementation Notes

- Do **not** reuse `SearchViewModel`. Search and review chat both read historical data, but search is filter/list state while review chat is retrieval + multi-turn conversation + explicit save.
- Do **not** persist unsaved sessions. The live conversation stays in `ReviewChatViewModel` only.
- Do **not** piggyback on `MarkdownNoteRepository`. Saved chat sessions are a distinct data type and should be isolated.
- Keep `automatic` routing in this feature explicit and local to `ReviewChatPlanner`: automatic means `cloud first -> on-device fallback`.
- The “recent saved conversation” requirement is intentionally minimal for v1: only reopen the latest saved session from the review entry card.

### Task 1: Build the review-chat AI planner

**Files:**
- Create: `app/src/main/java/com/mindflow/app/data/reviewchat/ReviewChatModels.kt`
- Create: `app/src/main/java/com/mindflow/app/data/reviewchat/ReviewChatPromptFactory.kt`
- Create: `app/src/main/java/com/mindflow/app/data/reviewchat/ReviewChatPlanner.kt`
- Modify: `app/src/main/java/com/mindflow/app/data/topic/AiServiceClient.kt`
- Modify: `app/src/main/java/com/mindflow/app/data/localmodel/OnDeviceAiClient.kt`
- Test: `app/src/test/java/com/mindflow/app/data/reviewchat/ReviewChatPlannerTest.kt`

- [ ] **Step 1: Write the failing planner tests**

```kotlin
package com.mindflow.app.data.reviewchat

import com.google.common.truth.Truth.assertThat
import com.mindflow.app.data.ai.AiExecutionMode
import com.mindflow.app.data.local.entity.NoteEntity
import com.mindflow.app.data.localmodel.LocalKnowledgeMaintenanceSnapshot
import com.mindflow.app.data.localmodel.LocalMaintainerCard
import com.mindflow.app.data.model.FolderSource
import com.mindflow.app.data.model.KnowledgeTrust
import com.mindflow.app.data.model.NoteHorizon
import com.mindflow.app.data.model.NoteStatus
import com.mindflow.app.data.model.TagSource
import com.mindflow.app.data.model.TopicSource
import com.mindflow.app.data.review.WeeklyReviewState
import com.mindflow.app.data.topic.AiChatResult
import com.mindflow.app.data.wiki.DirectionWikiDirectionSummary
import com.mindflow.app.data.wiki.DirectionWikiSnapshot
import com.mindflow.app.data.wiki.KnowledgeLayerSearchItem
import com.mindflow.app.data.wiki.KnowledgeLayerSearchType
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ReviewChatPlannerTest {
    @Test
    fun classifyReviewChatIntent_prioritizesSynthesisLanguage() {
        assertThat(classifyReviewChatIntent("把工作和副项目里的共同主题串一下"))
            .isEqualTo(ReviewChatIntent.SYNTHESIZE)
        assertThat(classifyReviewChatIntent("为什么这个方向现在更值得继续推"))
            .isEqualTo(ReviewChatIntent.DISCUSS)
        assertThat(classifyReviewChatIntent("我之前什么时候提过这个问题"))
            .isEqualTo(ReviewChatIntent.RECALL)
    }

    @Test
    fun buildContextPacket_includesRawAndStructuredInputsButCapsLength() {
        val packet = buildReviewChatContextPacket(
            question = "把最近两周的矛盾串一下",
            intent = ReviewChatIntent.SYNTHESIZE,
            notes = List(12) { index -> sampleNote(index.toLong() + 1L, "主题$index", "正文$index") },
            weeklyReview = WeeklyReviewState(lines = listOf("主线", "推进", "重启", "串联")),
            maintenanceSnapshot = LocalKnowledgeMaintenanceSnapshot(
                currentJudgement = LocalMaintainerCard(line = "当前判断", support = "判断依据"),
                recentAbsorption = LocalMaintainerCard(line = "最近吸收", support = "吸收依据"),
            ),
            wikiSnapshot = DirectionWikiSnapshot(
                directions = mapOf(
                    "product" to DirectionWikiDirectionSummary(
                        threadKey = "product",
                        slug = "product",
                        title = "产品方向",
                        conclusionLine = "一个关键结论",
                    ),
                ),
                knowledgeItems = listOf(
                    KnowledgeLayerSearchItem(
                        id = "k1",
                        type = KnowledgeLayerSearchType.CONCLUSION,
                        title = "关键结论",
                        summary = "已经被沉淀下来的判断",
                    ),
                ),
            ),
            sessionSummary = "上一轮在讨论方向冲突",
        )

        assertThat(packet.rawNoteSnippets).hasSizeAtMost(6)
        assertThat(packet.structuredSnippets.joinToString("\n")).contains("当前判断")
        assertThat(packet.structuredSnippets.joinToString("\n")).contains("关键结论")
    }

    @Test
    fun answer_automaticModePrefersCloudThenFallsBackToOnDevice() = runTest {
        val planner = ReviewChatPlanner(
            loadNotes = { listOf(sampleNote(1L, "主题", "一条正文")) },
            loadWeeklyReview = { WeeklyReviewState(lines = listOf("主线")) },
            loadMaintenanceSnapshot = { LocalKnowledgeMaintenanceSnapshot() },
            loadWikiSnapshot = { DirectionWikiSnapshot() },
            resolveExecutionMode = { AiExecutionMode.AUTOMATIC },
            isCloudConfigured = { true },
            isOnDeviceReady = { true },
            runCloud = { AiChatResult.Failure(com.mindflow.app.data.topic.AiFailureReason.NETWORK, "网络失败") },
            runOnDevice = { AiChatResult.Success("端侧补位回答") },
        )

        val result = planner.answer(
            ReviewChatTurnRequest(
                question = "把最近的问题串起来",
                priorMessages = emptyList(),
            ),
        )

        assertThat(result.provider).isEqualTo(ReviewChatProvider.ON_DEVICE)
        assertThat(result.fallbackOccurred).isTrue()
        assertThat(result.answer).contains("端侧补位回答")
    }

    private fun sampleNote(id: Long, topic: String, content: String): NoteEntity = NoteEntity(
        id = id,
        content = content,
        topic = topic,
        topicSource = TopicSource.MANUAL,
        folderKey = "work",
        folderSource = FolderSource.MANUAL,
        tags = listOf("产品"),
        tagSource = TagSource.MANUAL,
        status = NoteStatus.IDEA,
        horizon = NoteHorizon.MEDIUM,
        knowledgeTrust = KnowledgeTrust.NONE,
        isArchived = false,
        createdAt = 1_000L + id,
        updatedAt = 2_000L + id,
    )
}
```

- [ ] **Step 2: Run the planner tests to verify they fail**

Run:

```bash
JAVA_HOME=$HOME/.jdks/temurin-21 ./gradlew --no-daemon :app:testDebugUnitTest --tests com.mindflow.app.data.reviewchat.ReviewChatPlannerTest
```

Expected:

```text
FAILURE: Build failed
error: unresolved reference: ReviewChatPlanner
```

- [ ] **Step 3: Write the minimal planner and prompt plumbing**

```kotlin
// app/src/main/java/com/mindflow/app/data/reviewchat/ReviewChatModels.kt
package com.mindflow.app.data.reviewchat

enum class ReviewChatIntent { SYNTHESIZE, DISCUSS, RECALL }
enum class ReviewChatMessageRole { USER, ASSISTANT }
enum class ReviewChatProvider { CLOUD, ON_DEVICE }

data class ReviewChatMessage(
    val role: ReviewChatMessageRole,
    val content: String,
    val provider: ReviewChatProvider? = null,
    val createdAt: Long,
)

data class ReviewChatTurnRequest(
    val question: String,
    val priorMessages: List<ReviewChatMessage>,
)

data class ReviewChatTurnResult(
    val answer: String,
    val provider: ReviewChatProvider,
    val fallbackOccurred: Boolean,
    val providerLine: String,
    val sessionSummary: String,
    val titleSuggestion: String,
)

data class ReviewChatContextPacket(
    val intent: ReviewChatIntent,
    val question: String,
    val sessionSummary: String,
    val rawNoteSnippets: List<String>,
    val structuredSnippets: List<String>,
)
```

```kotlin
// app/src/main/java/com/mindflow/app/data/reviewchat/ReviewChatPlanner.kt
package com.mindflow.app.data.reviewchat

import com.mindflow.app.data.ai.AiExecutionMode
import com.mindflow.app.data.local.entity.NoteEntity
import com.mindflow.app.data.localmodel.LocalKnowledgeMaintenanceSnapshot
import com.mindflow.app.data.review.WeeklyReviewState
import com.mindflow.app.data.topic.AiChatResult
import com.mindflow.app.data.wiki.DirectionWikiSnapshot

internal fun classifyReviewChatIntent(question: String): ReviewChatIntent {
    val normalized = question.lowercase()
    return when {
        listOf("串", "归纳", "共同", "主线", "矛盾").any(normalized::contains) -> ReviewChatIntent.SYNTHESIZE
        listOf("为什么", "值不值得", "该不该", "怎么推进", "分歧").any(normalized::contains) -> ReviewChatIntent.DISCUSS
        else -> ReviewChatIntent.RECALL
    }
}

internal fun buildReviewChatContextPacket(
    question: String,
    intent: ReviewChatIntent,
    notes: List<NoteEntity>,
    weeklyReview: WeeklyReviewState,
    maintenanceSnapshot: LocalKnowledgeMaintenanceSnapshot,
    wikiSnapshot: DirectionWikiSnapshot,
    sessionSummary: String,
): ReviewChatContextPacket {
    val rawSnippets = notes
        .sortedByDescending { it.updatedAt }
        .take(6)
        .map { note ->
            "记录｜${note.topic.ifBlank { "未命名记录" }}｜${note.content.replace("\n", " ").take(120)}"
        }
    val structuredSnippets = buildList {
        weeklyReview.lines.forEach { add("周回看｜$it") }
        maintenanceSnapshot.currentJudgement.line.takeIf { it.isNotBlank() }?.let { add("当前判断｜$it") }
        maintenanceSnapshot.recentAbsorption.line.takeIf { it.isNotBlank() }?.let { add("最近吸收｜$it") }
        wikiSnapshot.directions.values.take(3).forEach { add("方向｜${it.title}｜${it.conclusionLine}") }
        wikiSnapshot.knowledgeItems.take(4).forEach { add("${it.type.label}｜${it.title}｜${it.summary}") }
    }
    return ReviewChatContextPacket(
        intent = intent,
        question = question,
        sessionSummary = sessionSummary,
        rawNoteSnippets = rawSnippets,
        structuredSnippets = structuredSnippets,
    )
}

class ReviewChatPlanner(
    private val loadNotes: suspend () -> List<NoteEntity>,
    private val loadWeeklyReview: suspend () -> WeeklyReviewState,
    private val loadMaintenanceSnapshot: suspend () -> LocalKnowledgeMaintenanceSnapshot,
    private val loadWikiSnapshot: suspend () -> DirectionWikiSnapshot,
    private val resolveExecutionMode: suspend () -> AiExecutionMode,
    private val isCloudConfigured: suspend () -> Boolean,
    private val isOnDeviceReady: suspend () -> Boolean,
    private val runCloud: suspend (String) -> AiChatResult,
    private val runOnDevice: suspend (String) -> AiChatResult,
) {
    suspend fun answer(request: ReviewChatTurnRequest): ReviewChatTurnResult {
        val intent = classifyReviewChatIntent(request.question)
        val packet = buildReviewChatContextPacket(
            question = request.question,
            intent = intent,
            notes = loadNotes(),
            weeklyReview = loadWeeklyReview(),
            maintenanceSnapshot = loadMaintenanceSnapshot(),
            wikiSnapshot = loadWikiSnapshot(),
            sessionSummary = request.priorMessages.takeLast(2).joinToString("\n") { it.content.take(120) },
        )
        val cloudPrompt = ReviewChatPromptFactory.cloud(packet)
        val onDevicePrompt = ReviewChatPromptFactory.onDevice(packet)
        val mode = resolveExecutionMode()
        val attempts = when (mode) {
            AiExecutionMode.AUTOMATIC -> listOf(ReviewChatProvider.CLOUD, ReviewChatProvider.ON_DEVICE)
            AiExecutionMode.CLOUD_ONLY -> listOf(ReviewChatProvider.CLOUD)
            AiExecutionMode.ON_DEVICE_ONLY -> listOf(ReviewChatProvider.ON_DEVICE)
        }
        var fallback = false
        attempts.forEachIndexed { index, provider ->
            val result = when (provider) {
                ReviewChatProvider.CLOUD ->
                    if (isCloudConfigured()) runCloud(cloudPrompt) else AiChatResult.Failure(com.mindflow.app.data.topic.AiFailureReason.CONFIG, "cloud")
                ReviewChatProvider.ON_DEVICE ->
                    if (isOnDeviceReady()) runOnDevice(onDevicePrompt) else AiChatResult.Failure(com.mindflow.app.data.topic.AiFailureReason.CONFIG, "device")
            }
            if (result is AiChatResult.Success) {
                fallback = index > 0
                return ReviewChatTurnResult(
                    answer = result.content.trim(),
                    provider = provider,
                    fallbackOccurred = fallback,
                    providerLine = when {
                        provider == ReviewChatProvider.CLOUD -> "本次由云侧完成"
                        fallback -> "云侧不可用，已回退端侧"
                        else -> "本次由端侧完成"
                    },
                    sessionSummary = "${request.question.take(40)}｜${result.content.take(80)}",
                    titleSuggestion = request.question.take(18),
                )
            }
        }
        error("No provider returned a usable review chat answer")
    }
}
```

```kotlin
// app/src/main/java/com/mindflow/app/data/reviewchat/ReviewChatPromptFactory.kt
package com.mindflow.app.data.reviewchat

object ReviewChatPromptFactory {
    fun cloud(packet: ReviewChatContextPacket): String = buildString {
        appendLine("问题类型：${packet.intent}")
        appendLine("当前问题：${packet.question}")
        if (packet.sessionSummary.isNotBlank()) appendLine("近期会话摘要：${packet.sessionSummary}")
        appendLine("原始记录：")
        packet.rawNoteSnippets.forEach(::appendLine)
        appendLine("已沉淀结构：")
        packet.structuredSnippets.forEach(::appendLine)
    }

    fun onDevice(packet: ReviewChatContextPacket): String = cloud(packet)
}
```

```kotlin
// app/src/main/java/com/mindflow/app/data/topic/AiServiceClient.kt
suspend fun generateReviewChatReply(
    settings: AiSettings,
    prompt: String,
): AiChatResult = withContext(Dispatchers.IO) {
    requestChatCompletion(
        settings = settings,
        userPrompt = prompt.take(6_000),
        systemPrompt = "You are answering questions about a person's historical notes and structured personal knowledge. Reply in Chinese. Prioritize synthesis, discussion, and recall based only on the supplied material. Do not pretend to know facts outside the provided context. Keep the answer clear, concrete, and non-generic.",
        maxTokens = 720,
        temperature = 0.55,
        thinkingEnabled = false,
    )
}
```

```kotlin
// app/src/main/java/com/mindflow/app/data/localmodel/OnDeviceAiClient.kt
interface OnDeviceAiClient {
    suspend fun generateReviewChatReply(settings: OnDeviceModelSettings, prompt: String): AiChatResult
}

override suspend fun generateReviewChatReply(
    settings: OnDeviceModelSettings,
    prompt: String,
): AiChatResult = runPrompt(settings, prompt)
```

- [ ] **Step 4: Re-run the planner tests to verify they pass**

Run:

```bash
JAVA_HOME=$HOME/.jdks/temurin-21 ./gradlew --no-daemon :app:testDebugUnitTest --tests com.mindflow.app.data.reviewchat.ReviewChatPlannerTest
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 5: Commit the planner**

```bash
git add \
  app/src/main/java/com/mindflow/app/data/reviewchat/ReviewChatModels.kt \
  app/src/main/java/com/mindflow/app/data/reviewchat/ReviewChatPromptFactory.kt \
  app/src/main/java/com/mindflow/app/data/reviewchat/ReviewChatPlanner.kt \
  app/src/main/java/com/mindflow/app/data/topic/AiServiceClient.kt \
  app/src/main/java/com/mindflow/app/data/localmodel/OnDeviceAiClient.kt \
  app/src/test/java/com/mindflow/app/data/reviewchat/ReviewChatPlannerTest.kt
git commit -m "feat: add review chat planner"
```

### Task 2: Add saved-session persistence

**Files:**
- Create: `app/src/main/java/com/mindflow/app/data/local/reviewchat/ReviewChatSessionEntity.kt`
- Create: `app/src/main/java/com/mindflow/app/data/local/reviewchat/ReviewChatMessageEntity.kt`
- Create: `app/src/main/java/com/mindflow/app/data/local/reviewchat/ReviewChatDao.kt`
- Create: `app/src/main/java/com/mindflow/app/data/local/reviewchat/ReviewChatDatabase.kt`
- Create: `app/src/main/java/com/mindflow/app/data/reviewchat/ReviewChatSavedConversationRepository.kt`
- Create: `app/src/main/java/com/mindflow/app/data/reviewchat/RoomReviewChatSavedConversationRepository.kt`
- Modify: `app/src/main/java/com/mindflow/app/di/AppContainer.kt`
- Test: `app/src/androidTest/java/com/mindflow/app/data/reviewchat/RoomReviewChatSavedConversationRepositoryTest.kt`

- [ ] **Step 1: Write the failing repository integration test**

```kotlin
package com.mindflow.app.data.reviewchat

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.mindflow.app.data.local.reviewchat.ReviewChatDatabase
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomReviewChatSavedConversationRepositoryTest {
    private lateinit var database: ReviewChatDatabase
    private lateinit var repository: RoomReviewChatSavedConversationRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ReviewChatDatabase::class.java).build()
        repository = RoomReviewChatSavedConversationRepository(database.reviewChatDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun saveSession_persistsMessagesAndLatestSummary() = kotlinx.coroutines.test.runTest {
        val sessionId = repository.saveSession(
            title = "产品方向的矛盾",
            messages = listOf(
                ReviewChatMessage(ReviewChatMessageRole.USER, "最近最大的矛盾是什么", null, 1_000L),
                ReviewChatMessage(ReviewChatMessageRole.ASSISTANT, "你在增长和定位之间反复摇摆", ReviewChatProvider.CLOUD, 1_100L),
            ),
        )

        val saved = repository.getSession(sessionId)
        val latest = repository.observeLatestSavedSessionSummary().first()

        assertThat(saved?.messages).hasSize(2)
        assertThat(saved?.messages?.last()?.provider).isEqualTo(ReviewChatProvider.CLOUD)
        assertThat(latest?.sessionId).isEqualTo(sessionId)
        assertThat(latest?.title).isEqualTo("产品方向的矛盾")
    }
}
```

- [ ] **Step 2: Run the repository test to verify it fails**

Run:

```bash
JAVA_HOME=$HOME/.jdks/temurin-21 ./gradlew --no-daemon :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.mindflow.app.data.reviewchat.RoomReviewChatSavedConversationRepositoryTest
```

Expected:

```text
FAILURE: Build failed
error: unresolved reference: ReviewChatDatabase
```

- [ ] **Step 3: Write the Room entities, DAO, repository, and AppContainer wiring**

```kotlin
// app/src/main/java/com/mindflow/app/data/local/reviewchat/ReviewChatSessionEntity.kt
package com.mindflow.app.data.local.reviewchat

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "review_chat_sessions")
data class ReviewChatSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val saved: Boolean = true,
)
```

```kotlin
// app/src/main/java/com/mindflow/app/data/local/reviewchat/ReviewChatMessageEntity.kt
package com.mindflow.app.data.local.reviewchat

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "review_chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = ReviewChatSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId")],
)
data class ReviewChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val role: String,
    val content: String,
    val provider: String?,
    val createdAt: Long,
)
```

```kotlin
// app/src/main/java/com/mindflow/app/data/local/reviewchat/ReviewChatDao.kt
package com.mindflow.app.data.local.reviewchat

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ReviewChatDao {
    @Insert
    suspend fun insertSession(entity: ReviewChatSessionEntity): Long

    @Insert
    suspend fun insertMessages(entities: List<ReviewChatMessageEntity>)

    @Query("SELECT * FROM review_chat_sessions ORDER BY updatedAt DESC LIMIT 1")
    fun observeLatestSession(): Flow<ReviewChatSessionEntity?>

    @Query("SELECT * FROM review_chat_sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getSession(sessionId: Long): ReviewChatSessionEntity?

    @Query("SELECT * FROM review_chat_messages WHERE sessionId = :sessionId ORDER BY createdAt ASC, id ASC")
    suspend fun getMessages(sessionId: Long): List<ReviewChatMessageEntity>
}
```

```kotlin
// app/src/main/java/com/mindflow/app/data/local/reviewchat/ReviewChatDatabase.kt
package com.mindflow.app.data.local.reviewchat

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [ReviewChatSessionEntity::class, ReviewChatMessageEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class ReviewChatDatabase : RoomDatabase() {
    abstract fun reviewChatDao(): ReviewChatDao
}
```

```kotlin
// app/src/main/java/com/mindflow/app/data/reviewchat/ReviewChatSavedConversationRepository.kt
package com.mindflow.app.data.reviewchat

import kotlinx.coroutines.flow.Flow

data class SavedReviewChatSession(
    val sessionId: Long,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val messages: List<ReviewChatMessage>,
)

data class SavedReviewChatSessionSummary(
    val sessionId: Long,
    val title: String,
    val updatedAt: Long,
)

interface ReviewChatSavedConversationRepository {
    suspend fun saveSession(title: String, messages: List<ReviewChatMessage>): Long
    suspend fun getSession(sessionId: Long): SavedReviewChatSession?
    fun observeLatestSavedSessionSummary(): Flow<SavedReviewChatSessionSummary?>
}
```

```kotlin
// app/src/main/java/com/mindflow/app/data/reviewchat/RoomReviewChatSavedConversationRepository.kt
package com.mindflow.app.data.reviewchat

import com.mindflow.app.data.local.reviewchat.ReviewChatDao
import com.mindflow.app.data.local.reviewchat.ReviewChatMessageEntity
import com.mindflow.app.data.local.reviewchat.ReviewChatSessionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomReviewChatSavedConversationRepository(
    private val dao: ReviewChatDao,
) : ReviewChatSavedConversationRepository {
    override suspend fun saveSession(title: String, messages: List<ReviewChatMessage>): Long {
        val now = messages.lastOrNull()?.createdAt ?: System.currentTimeMillis()
        val sessionId = dao.insertSession(
            ReviewChatSessionEntity(
                title = title,
                createdAt = messages.firstOrNull()?.createdAt ?: now,
                updatedAt = now,
            ),
        )
        dao.insertMessages(
            messages.map {
                ReviewChatMessageEntity(
                    sessionId = sessionId,
                    role = it.role.name,
                    content = it.content,
                    provider = it.provider?.name,
                    createdAt = it.createdAt,
                )
            },
        )
        return sessionId
    }

    override suspend fun getSession(sessionId: Long): SavedReviewChatSession? {
        val session = dao.getSession(sessionId) ?: return null
        val messages = dao.getMessages(sessionId).map {
            ReviewChatMessage(
                role = ReviewChatMessageRole.valueOf(it.role),
                content = it.content,
                provider = it.provider?.let(ReviewChatProvider::valueOf),
                createdAt = it.createdAt,
            )
        }
        return SavedReviewChatSession(session.id, session.title, session.createdAt, session.updatedAt, messages)
    }

    override fun observeLatestSavedSessionSummary(): Flow<SavedReviewChatSessionSummary?> =
        dao.observeLatestSession().map { entity ->
            entity?.let { SavedReviewChatSessionSummary(it.id, it.title, it.updatedAt) }
        }
}
```

```kotlin
// app/src/main/java/com/mindflow/app/di/AppContainer.kt
private val reviewChatDatabase = androidx.room.Room.databaseBuilder(
    context.applicationContext,
    com.mindflow.app.data.local.reviewchat.ReviewChatDatabase::class.java,
    "review-chat.db",
).build()

val reviewChatSavedConversationRepository: ReviewChatSavedConversationRepository =
    RoomReviewChatSavedConversationRepository(reviewChatDatabase.reviewChatDao())
```

- [ ] **Step 4: Re-run the repository integration test**

Run:

```bash
JAVA_HOME=$HOME/.jdks/temurin-21 ./gradlew --no-daemon :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.mindflow.app.data.reviewchat.RoomReviewChatSavedConversationRepositoryTest
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 5: Commit persistence**

```bash
git add \
  app/src/main/java/com/mindflow/app/data/local/reviewchat/ReviewChatSessionEntity.kt \
  app/src/main/java/com/mindflow/app/data/local/reviewchat/ReviewChatMessageEntity.kt \
  app/src/main/java/com/mindflow/app/data/local/reviewchat/ReviewChatDao.kt \
  app/src/main/java/com/mindflow/app/data/local/reviewchat/ReviewChatDatabase.kt \
  app/src/main/java/com/mindflow/app/data/reviewchat/ReviewChatSavedConversationRepository.kt \
  app/src/main/java/com/mindflow/app/data/reviewchat/RoomReviewChatSavedConversationRepository.kt \
  app/src/main/java/com/mindflow/app/di/AppContainer.kt \
  app/src/androidTest/java/com/mindflow/app/data/reviewchat/RoomReviewChatSavedConversationRepositoryTest.kt
git commit -m "feat: persist saved review chat sessions"
```

### Task 3: Add the review-chat ViewModel

**Files:**
- Create: `app/src/main/java/com/mindflow/app/ui/screens/reviewchat/ReviewChatViewModel.kt`
- Test: `app/src/test/java/com/mindflow/app/ui/screens/reviewchat/ReviewChatViewModelTest.kt`

- [ ] **Step 1: Write the failing ViewModel tests**

```kotlin
package com.mindflow.app.ui.screens.reviewchat

import com.google.common.truth.Truth.assertThat
import com.mindflow.app.data.reviewchat.ReviewChatMessage
import com.mindflow.app.data.reviewchat.ReviewChatMessageRole
import com.mindflow.app.data.reviewchat.ReviewChatProvider
import com.mindflow.app.data.reviewchat.ReviewChatSavedConversationRepository
import com.mindflow.app.data.reviewchat.ReviewChatTurnRequest
import com.mindflow.app.data.reviewchat.ReviewChatTurnResult
import com.mindflow.app.data.reviewchat.SavedReviewChatSession
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ReviewChatViewModelTest {
    @Test
    fun sendingInitialQuestion_pushesUserMessageAndAssistantReply() = runTest {
        val viewModel = ReviewChatViewModel(
            initialQuestion = "把最近两周的矛盾串一下",
            savedSessionId = null,
            planner = { request ->
                ReviewChatTurnResult(
                    answer = "你在增长和定位之间反复摇摆。",
                    provider = ReviewChatProvider.CLOUD,
                    fallbackOccurred = false,
                    providerLine = "本次由云侧完成",
                    sessionSummary = "增长和定位之间的摇摆",
                    titleSuggestion = "两周内的矛盾",
                )
            },
            repository = FakeReviewChatRepository(),
        )

        viewModel.awaitIdle()
        val state = viewModel.uiState.value

        assertThat(state.messages.map { it.role })
            .containsExactly(ReviewChatMessageRole.USER, ReviewChatMessageRole.ASSISTANT)
            .inOrder()
        assertThat(state.providerLine).isEqualTo("本次由云侧完成")
        assertThat(state.isSaved).isFalse()
    }

    @Test
    fun saveConversation_persistsOnlyWhenExplicitlyRequested() = runTest {
        val repository = FakeReviewChatRepository()
        val viewModel = ReviewChatViewModel(
            initialQuestion = "把工作和副项目串一下",
            savedSessionId = null,
            planner = {
                ReviewChatTurnResult(
                    answer = "两个方向都在追求可复用的方法。",
                    provider = ReviewChatProvider.ON_DEVICE,
                    fallbackOccurred = false,
                    providerLine = "本次由端侧完成",
                    sessionSummary = "可复用方法",
                    titleSuggestion = "工作和副项目",
                )
            },
            repository = repository,
        )

        viewModel.awaitIdle()
        assertThat(repository.savedSessions).isEmpty()

        viewModel.saveConversation()

        assertThat(repository.savedSessions).hasSize(1)
        assertThat(viewModel.uiState.value.isSaved).isTrue()
    }

    @Test
    fun loadingSavedConversation_opensReadOnlyHistory() = runTest {
        val repository = FakeReviewChatRepository(
            savedSession = SavedReviewChatSession(
                sessionId = 7L,
                title = "最近的方向矛盾",
                createdAt = 1_000L,
                updatedAt = 2_000L,
                messages = listOf(
                    ReviewChatMessage(ReviewChatMessageRole.USER, "最近卡在哪", null, 1_000L),
                    ReviewChatMessage(ReviewChatMessageRole.ASSISTANT, "你在定位和执行之间摇摆。", ReviewChatProvider.CLOUD, 1_100L),
                ),
            ),
        )
        val viewModel = ReviewChatViewModel(
            initialQuestion = "",
            savedSessionId = 7L,
            planner = { error("planner should not run for saved sessions") },
            repository = repository,
        )

        viewModel.awaitIdle()

        assertThat(viewModel.uiState.value.isReadOnly).isTrue()
        assertThat(viewModel.uiState.value.messages).hasSize(2)
        assertThat(viewModel.uiState.value.title).isEqualTo("最近的方向矛盾")
    }

    private class FakeReviewChatRepository(
        savedSession: SavedReviewChatSession? = null,
    ) : ReviewChatSavedConversationRepository {
        val savedSessions = mutableListOf<SavedReviewChatSession>()
        private var latestSavedSession = savedSession

        override suspend fun saveSession(title: String, messages: List<ReviewChatMessage>): Long {
            val next = SavedReviewChatSession(
                sessionId = (savedSessions.size + 1).toLong(),
                title = title,
                createdAt = messages.firstOrNull()?.createdAt ?: 0L,
                updatedAt = messages.lastOrNull()?.createdAt ?: 0L,
                messages = messages,
            )
            savedSessions += next
            latestSavedSession = next
            return next.sessionId
        }

        override suspend fun getSession(sessionId: Long): SavedReviewChatSession? =
            latestSavedSession?.takeIf { it.sessionId == sessionId }

        override fun observeLatestSavedSessionSummary() = flowOf(
            latestSavedSession?.let {
                com.mindflow.app.data.reviewchat.SavedReviewChatSessionSummary(
                    sessionId = it.sessionId,
                    title = it.title,
                    updatedAt = it.updatedAt,
                )
            },
        )
    }
}
```

- [ ] **Step 2: Run the ViewModel tests to verify they fail**

Run:

```bash
JAVA_HOME=$HOME/.jdks/temurin-21 ./gradlew --no-daemon :app:testDebugUnitTest --tests com.mindflow.app.ui.screens.reviewchat.ReviewChatViewModelTest
```

Expected:

```text
FAILURE: Build failed
error: unresolved reference: ReviewChatViewModel
```

- [ ] **Step 3: Implement the ViewModel**

```kotlin
package com.mindflow.app.ui.screens.reviewchat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mindflow.app.data.reviewchat.ReviewChatMessage
import com.mindflow.app.data.reviewchat.ReviewChatMessageRole
import com.mindflow.app.data.reviewchat.ReviewChatSavedConversationRepository
import com.mindflow.app.data.reviewchat.ReviewChatTurnRequest
import com.mindflow.app.data.reviewchat.ReviewChatTurnResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReviewChatUiState(
    val title: String = "和历史聊聊",
    val messages: List<ReviewChatMessage> = emptyList(),
    val draft: String = "",
    val isSending: Boolean = false,
    val isSaved: Boolean = false,
    val isReadOnly: Boolean = false,
    val providerLine: String = "",
    val errorMessage: String? = null,
)

class ReviewChatViewModel(
    initialQuestion: String,
    savedSessionId: Long?,
    private val planner: suspend (ReviewChatTurnRequest) -> ReviewChatTurnResult,
    private val repository: ReviewChatSavedConversationRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ReviewChatUiState())
    val uiState: StateFlow<ReviewChatUiState> = _uiState.asStateFlow()
    private var latestSummary: String = ""
    private val initialLoad = CompletableDeferred<Unit>()

    init {
        viewModelScope.launch {
            when {
                savedSessionId != null -> loadSavedSession(savedSessionId)
                initialQuestion.isNotBlank() -> sendQuestion(initialQuestion.trim())
            }
            initialLoad.complete(Unit)
        }
    }

    suspend fun awaitIdle() {
        initialLoad.await()
    }

    companion object {
        fun factory(
            initialQuestion: String,
            savedSessionId: Long?,
            planner: suspend (ReviewChatTurnRequest) -> ReviewChatTurnResult,
            repository: ReviewChatSavedConversationRepository,
        ): androidx.lifecycle.ViewModelProvider.Factory = androidx.lifecycle.viewmodel.viewModelFactory {
            initializer {
                ReviewChatViewModel(
                    initialQuestion = initialQuestion,
                    savedSessionId = savedSessionId,
                    planner = planner,
                    repository = repository,
                )
            }
        }
    }

    fun updateDraft(value: String) {
        _uiState.update { it.copy(draft = value) }
    }

    fun retryLastQuestion() {
        val lastUser = _uiState.value.messages.lastOrNull { it.role == ReviewChatMessageRole.USER } ?: return
        viewModelScope.launch { sendQuestion(lastUser.content, appendUserMessage = false) }
    }

    fun sendFromDraft() {
        val question = _uiState.value.draft.trim()
        if (question.isBlank() || _uiState.value.isReadOnly) return
        viewModelScope.launch { sendQuestion(question) }
    }

    fun saveConversation() {
        if (_uiState.value.isSaved || _uiState.value.isReadOnly || _uiState.value.messages.isEmpty()) return
        viewModelScope.launch {
            repository.saveSession(_uiState.value.title, _uiState.value.messages)
            _uiState.update { it.copy(isSaved = true) }
        }
    }

    private suspend fun loadSavedSession(sessionId: Long) {
        val session = repository.getSession(sessionId) ?: return
        _uiState.value = ReviewChatUiState(
            title = session.title,
            messages = session.messages,
            isSaved = true,
            isReadOnly = true,
            providerLine = session.messages.lastOrNull()?.provider?.let { provider ->
                if (provider.name == "CLOUD") "本次由云侧完成" else "本次由端侧完成"
            }.orEmpty(),
        )
    }

    private suspend fun sendQuestion(question: String, appendUserMessage: Boolean = true) {
        val userMessage = ReviewChatMessage(
            role = ReviewChatMessageRole.USER,
            content = question,
            createdAt = System.currentTimeMillis(),
        )
        if (appendUserMessage) {
            _uiState.update {
                it.copy(
                    draft = "",
                    messages = it.messages + userMessage,
                    isSending = true,
                    errorMessage = null,
                )
            }
        } else {
            _uiState.update { it.copy(isSending = true, errorMessage = null) }
        }

        runCatching {
            planner(
                ReviewChatTurnRequest(
                    question = question,
                    priorMessages = _uiState.value.messages,
                ),
            )
        }.onSuccess { result ->
            latestSummary = result.sessionSummary
            val assistantMessage = ReviewChatMessage(
                role = ReviewChatMessageRole.ASSISTANT,
                content = result.answer,
                provider = result.provider,
                createdAt = System.currentTimeMillis(),
            )
            _uiState.update {
                it.copy(
                    title = result.titleSuggestion.ifBlank { it.title },
                    messages = it.messages + assistantMessage,
                    isSending = false,
                    providerLine = result.providerLine,
                )
            }
        }.onFailure { error ->
            _uiState.update {
                it.copy(
                    isSending = false,
                    errorMessage = error.message ?: "生成失败，请重试",
                )
            }
        }
    }
}
```

Implementation notes for the real code:
- Replace the inline provider-line mapping in `loadSavedSession()` with a shared helper to avoid string drift.
- If you need the session summary on later turns, either:
  - keep `latestSummary` in state and pass it into `ReviewChatTurnRequest`, or
  - let `ReviewChatPlanner` derive it from the last few messages, as in Task 1. Do not do both.

- [ ] **Step 4: Re-run the ViewModel tests**

Run:

```bash
JAVA_HOME=$HOME/.jdks/temurin-21 ./gradlew --no-daemon :app:testDebugUnitTest --tests com.mindflow.app.ui.screens.reviewchat.ReviewChatViewModelTest
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 5: Commit the ViewModel**

```bash
git add \
  app/src/main/java/com/mindflow/app/ui/screens/reviewchat/ReviewChatViewModel.kt \
  app/src/test/java/com/mindflow/app/ui/screens/reviewchat/ReviewChatViewModelTest.kt
git commit -m "feat: add review chat viewmodel"
```

### Task 4: Add the chat route and screen

**Files:**
- Create: `app/src/main/java/com/mindflow/app/ui/navigation/ReviewChatSeed.kt`
- Create: `app/src/main/java/com/mindflow/app/ui/screens/reviewchat/ReviewChatScreen.kt`
- Modify: `app/src/main/java/com/mindflow/app/ui/navigation/MindFlowDestinations.kt`
- Modify: `app/src/main/java/com/mindflow/app/ui/MindFlowApp.kt`
- Modify: `app/src/main/java/com/mindflow/app/MainActivity.kt`
- Modify: `app/src/main/java/com/mindflow/app/di/AppContainer.kt`
- Test: `app/src/androidTest/java/com/mindflow/app/ui/screens/reviewchat/ReviewChatScreenInstrumentedTest.kt`

- [ ] **Step 1: Write the failing screen test**

```kotlin
package com.mindflow.app.ui.screens.reviewchat

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mindflow.app.data.reviewchat.ReviewChatMessage
import com.mindflow.app.data.reviewchat.ReviewChatMessageRole
import com.mindflow.app.ui.theme.MindFlowTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReviewChatScreenInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun screenShowsMessagesProviderLineAndSaveAction() {
        composeRule.setContent {
            MindFlowTheme {
                ReviewChatScreen(
                    uiState = ReviewChatUiState(
                        title = "最近的方向矛盾",
                        messages = listOf(
                            ReviewChatMessage(ReviewChatMessageRole.USER, "最近卡在哪", null, 1_000L),
                            ReviewChatMessage(ReviewChatMessageRole.ASSISTANT, "你在定位和执行之间反复摇摆。", com.mindflow.app.data.reviewchat.ReviewChatProvider.CLOUD, 1_100L),
                        ),
                        providerLine = "本次由云侧完成",
                    ),
                    onBack = {},
                    onDraftChange = {},
                    onSend = {},
                    onSave = {},
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithText("最近的方向矛盾").assertIsDisplayed()
        composeRule.onNodeWithText("本次由云侧完成").assertIsDisplayed()
        composeRule.onNodeWithText("保存").assertIsDisplayed()
        composeRule.onNodeWithText("你在定位和执行之间反复摇摆。").assertIsDisplayed()
    }
}
```

- [ ] **Step 2: Run the screen test to verify it fails**

Run:

```bash
JAVA_HOME=$HOME/.jdks/temurin-21 ./gradlew --no-daemon :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.mindflow.app.ui.screens.reviewchat.ReviewChatScreenInstrumentedTest
```

Expected:

```text
FAILURE: Build failed
error: unresolved reference: ReviewChatScreen
```

- [ ] **Step 3: Implement route plumbing and the screen**

```kotlin
// app/src/main/java/com/mindflow/app/ui/navigation/ReviewChatSeed.kt
package com.mindflow.app.ui.navigation

data class ReviewChatSeed(
    val requestId: Long = System.currentTimeMillis(),
    val initialQuestion: String = "",
    val savedSessionId: Long? = null,
)
```

```kotlin
// app/src/main/java/com/mindflow/app/ui/navigation/MindFlowDestinations.kt
const val REVIEW_CHAT = "review-chat/{reviewChatSeedId}"
const val REVIEW_CHAT_ARG = "reviewChatSeedId"

fun reviewChatRoute(seedId: Long): String = "review-chat/$seedId"
```

```kotlin
// app/src/main/java/com/mindflow/app/ui/screens/reviewchat/ReviewChatScreen.kt
package com.mindflow.app.ui.screens.reviewchat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mindflow.app.ui.components.ScreenBackground
import com.mindflow.app.ui.components.ScreenHorizontalPadding

@Composable
fun ReviewChatRoute(
    viewModel: ReviewChatViewModel,
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ReviewChatScreen(
        uiState = uiState,
        onBack = onBack,
        onDraftChange = viewModel::updateDraft,
        onSend = viewModel::sendFromDraft,
        onSave = viewModel::saveConversation,
        onRetry = viewModel::retryLastQuestion,
    )
}

@Composable
fun ReviewChatScreen(
    uiState: ReviewChatUiState,
    onBack: () -> Unit,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onSave: () -> Unit,
    onRetry: () -> Unit,
) {
    ScreenBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenHorizontalPadding, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = onBack) { Text("返回") }
                Text(uiState.title)
                TextButton(onClick = onSave, enabled = !uiState.isSaved && !uiState.isReadOnly) {
                    Text(if (uiState.isSaved) "已保存" else "保存")
                }
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = ScreenHorizontalPadding, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(uiState.messages) { message ->
                    Text(message.content)
                }
                if (uiState.providerLine.isNotBlank()) {
                    item { Text(uiState.providerLine) }
                }
                uiState.errorMessage?.let { error ->
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(error)
                            TextButton(onClick = onRetry) { Text("重试") }
                        }
                    }
                }
            }
            if (!uiState.isReadOnly) {
                OutlinedTextField(
                    value = uiState.draft,
                    onValueChange = onDraftChange,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    placeholder = { Text("继续追问你的历史记录") },
                )
                TextButton(
                    onClick = onSend,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                ) { Text("发送") }
            }
        }
    }
}
```

```kotlin
// app/src/main/java/com/mindflow/app/ui/MindFlowApp.kt
val reviewChatSeeds = remember { mutableStateMapOf<Long, ReviewChatSeed>() }

fun openReviewChat(seed: ReviewChatSeed) {
    reviewChatSeeds[seed.requestId] = seed
    navController.navigate(MindFlowDestinations.reviewChatRoute(seed.requestId))
}
```

```kotlin
// inside NavHost in MindFlowApp.kt
composable(
    route = MindFlowDestinations.REVIEW_CHAT,
    arguments = listOf(navArgument(MindFlowDestinations.REVIEW_CHAT_ARG) { type = NavType.LongType }),
) { backStackEntry ->
    val seedId = backStackEntry.arguments?.getLong(MindFlowDestinations.REVIEW_CHAT_ARG) ?: 0L
    val seed = reviewChatSeeds[seedId] ?: ReviewChatSeed(requestId = seedId)
    val viewModel: ReviewChatViewModel = viewModel(
        factory = ReviewChatViewModel.factory(
            initialQuestion = seed.initialQuestion,
            savedSessionId = seed.savedSessionId,
            planner = reviewChatPlanner,
            repository = reviewChatSavedConversationRepository,
        ),
    )
    ReviewChatRoute(
        viewModel = viewModel,
        onBack = {
            reviewChatSeeds.remove(seedId)
            navController.popBackStack()
        },
    )
}
```

```kotlin
// app/src/main/java/com/mindflow/app/di/AppContainer.kt
val reviewChatPlanner = ReviewChatPlanner(
    loadNotes = { noteRepository.observeAllNotes().first() },
    loadWeeklyReview = { weeklyReviewPlanner.state.first() },
    loadMaintenanceSnapshot = { localKnowledgeMaintenancePlanner.snapshot.value },
    loadWikiSnapshot = { directionWikiCoordinator.snapshot.value },
    resolveExecutionMode = { onDeviceModelSettingsRepository.getCurrent().executionMode },
    isCloudConfigured = { aiSettingsRepository.getCurrent().isConfigured },
    isOnDeviceReady = { onDeviceModelSettingsRepository.getCurrent().isReady },
    runCloud = { prompt -> aiServiceClient.generateReviewChatReply(aiSettingsRepository.getCurrent(), prompt) },
    runOnDevice = { prompt -> onDeviceAiClient.generateReviewChatReply(onDeviceModelSettingsRepository.getCurrent(), prompt) },
)
```

- [ ] **Step 4: Re-run the screen test**

Run:

```bash
JAVA_HOME=$HOME/.jdks/temurin-21 ./gradlew --no-daemon :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.mindflow.app.ui.screens.reviewchat.ReviewChatScreenInstrumentedTest
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 5: Commit route + screen**

```bash
git add \
  app/src/main/java/com/mindflow/app/ui/navigation/ReviewChatSeed.kt \
  app/src/main/java/com/mindflow/app/ui/navigation/MindFlowDestinations.kt \
  app/src/main/java/com/mindflow/app/ui/MindFlowApp.kt \
  app/src/main/java/com/mindflow/app/MainActivity.kt \
  app/src/main/java/com/mindflow/app/di/AppContainer.kt \
  app/src/main/java/com/mindflow/app/ui/screens/reviewchat/ReviewChatScreen.kt \
  app/src/androidTest/java/com/mindflow/app/ui/screens/reviewchat/ReviewChatScreenInstrumentedTest.kt
git commit -m "feat: add review chat route and screen"
```

### Task 5: Replace the review-page search card with a chat entry card

**Files:**
- Create: `app/src/main/java/com/mindflow/app/ui/screens/flow/ReviewChatEntryCard.kt`
- Modify: `app/src/main/java/com/mindflow/app/ui/screens/flow/FlowScreen.kt`
- Modify: `app/src/main/java/com/mindflow/app/ui/MindFlowApp.kt`
- Test: `app/src/androidTest/java/com/mindflow/app/ui/screens/flow/ReviewChatEntryCardInstrumentedTest.kt`

- [ ] **Step 1: Write the failing entry-card test**

```kotlin
package com.mindflow.app.ui.screens.flow

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.mindflow.app.data.reviewchat.SavedReviewChatSessionSummary
import com.mindflow.app.ui.theme.MindFlowTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReviewChatEntryCardInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun entryCardSendsTypedQuestionAndCanReopenLatestSavedConversation() {
        var sentQuestion = ""
        var reopenedSessionId: Long? = null

        composeRule.setContent {
            MindFlowTheme {
                ReviewChatEntryCard(
                    latestSavedSession = SavedReviewChatSessionSummary(
                        sessionId = 7L,
                        title = "最近保存的对话",
                        updatedAt = 1_000L,
                    ),
                    onSubmitQuestion = { sentQuestion = it },
                    onOpenLatestSaved = { reopenedSessionId = it },
                )
            }
        }

        composeRule.onNodeWithText("和历史聊聊").assertIsDisplayed()
        composeRule.onNodeWithText("查看最近保存的对话").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("基于你的历史记录和沉淀内容继续聊").assertIsDisplayed()
        composeRule.onNodeWithText("问一个基于历史积累的问题").performTextInput("把最近两周的矛盾串一下")
        composeRule.onNodeWithText("发送").performClick()

        assertThat(reopenedSessionId).isEqualTo(7L)
        assertThat(sentQuestion).isEqualTo("把最近两周的矛盾串一下")
    }
}
```

- [ ] **Step 2: Run the entry-card test to verify it fails**

Run:

```bash
JAVA_HOME=$HOME/.jdks/temurin-21 ./gradlew --no-daemon :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.mindflow.app.ui.screens.flow.ReviewChatEntryCardInstrumentedTest
```

Expected:

```text
FAILURE: Build failed
error: unresolved reference: ReviewChatEntryCard
```

- [ ] **Step 3: Implement the entry card and hook it into the review page**

```kotlin
// app/src/main/java/com/mindflow/app/ui/screens/flow/ReviewChatEntryCard.kt
package com.mindflow.app.ui.screens.flow

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mindflow.app.data.reviewchat.SavedReviewChatSessionSummary
import com.mindflow.app.ui.components.PanelShape
import com.mindflow.app.ui.theme.BorderSoft
import com.mindflow.app.ui.theme.TextMain
import com.mindflow.app.ui.theme.TextSoft
import com.mindflow.app.ui.theme.WhiteGlass

@Composable
fun ReviewChatEntryCard(
    latestSavedSession: SavedReviewChatSessionSummary?,
    onSubmitQuestion: (String) -> Unit,
    onOpenLatestSaved: (Long) -> Unit,
) {
    var question by remember { mutableStateOf("") }

    Surface(
        color = WhiteGlass.copy(alpha = 0.92f),
        shape = PanelShape,
        border = BorderStroke(1.dp, BorderSoft),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("和历史聊聊", color = TextMain)
            Text("基于你的历史记录和沉淀内容继续聊", color = TextSoft)
            OutlinedTextField(
                value = question,
                onValueChange = { question = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("问一个基于历史积累的问题") },
            )
            TextButton(
                onClick = {
                    val trimmed = question.trim()
                    if (trimmed.isNotBlank()) {
                        onSubmitQuestion(trimmed)
                        question = ""
                    }
                },
            ) { Text("发送") }
            latestSavedSession?.let {
                TextButton(onClick = { onOpenLatestSaved(it.sessionId) }) {
                    Text("查看最近保存的对话")
                }
            }
        }
    }
}
```

```kotlin
// app/src/main/java/com/mindflow/app/ui/screens/flow/FlowScreen.kt
item {
    ReviewChatEntryCard(
        latestSavedSession = latestSavedReviewChat,
        onSubmitQuestion = onOpenReviewChatQuestion,
        onOpenLatestSaved = onOpenLatestReviewChat,
    )
}
```

```kotlin
// app/src/main/java/com/mindflow/app/ui/MindFlowApp.kt
val latestSavedReviewChat by reviewChatSavedConversationRepository
    .observeLatestSavedSessionSummary()
    .collectAsStateWithLifecycle(initialValue = null)

FlowRoute(
    viewModel = sharedFlowViewModel,
    initialFocus = FlowFocus.REVIEW,
    onOpenThread = { threadKey -> navController.navigate(MindFlowDestinations.threadRoute(threadKey)) },
    onOpenNote = openNoteSafely,
    onCreateCapture = ::openCapture,
    onOpenSearch = { navController.navigate(MindFlowDestinations.SEARCH_BASE) },
    latestSavedReviewChat = latestSavedReviewChat,
    onOpenReviewChatQuestion = { question ->
        openReviewChat(ReviewChatSeed(initialQuestion = question))
    },
    onOpenLatestReviewChat = { sessionId ->
        openReviewChat(ReviewChatSeed(savedSessionId = sessionId))
    },
)
```

Implementation note:
- Delete the old `SearchRecordsCard()` call from the review page, but leave the actual search route intact. Search still exists in the product; it just no longer owns the review-page bottom card.

- [ ] **Step 4: Re-run the entry-card test**

Run:

```bash
JAVA_HOME=$HOME/.jdks/temurin-21 ./gradlew --no-daemon :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.mindflow.app.ui.screens.flow.ReviewChatEntryCardInstrumentedTest
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 5: Commit the review-page entry**

```bash
git add \
  app/src/main/java/com/mindflow/app/ui/screens/flow/ReviewChatEntryCard.kt \
  app/src/main/java/com/mindflow/app/ui/screens/flow/FlowScreen.kt \
  app/src/main/java/com/mindflow/app/ui/MindFlowApp.kt \
  app/src/androidTest/java/com/mindflow/app/ui/screens/flow/ReviewChatEntryCardInstrumentedTest.kt
git commit -m "feat: add review chat entry card"
```

### Task 6: End-to-end verification and cleanup

**Files:**
- Modify: `app/src/main/java/com/mindflow/app/data/reviewchat/ReviewChatModels.kt`
- Modify: `app/src/main/java/com/mindflow/app/ui/screens/reviewchat/ReviewChatViewModel.kt`
- Modify: `app/src/main/java/com/mindflow/app/ui/screens/reviewchat/ReviewChatScreen.kt`
- Test: `app/src/test/java/com/mindflow/app/data/reviewchat/ReviewChatPlannerTest.kt`
- Test: `app/src/test/java/com/mindflow/app/ui/screens/reviewchat/ReviewChatViewModelTest.kt`
- Test: `app/src/androidTest/java/com/mindflow/app/data/reviewchat/RoomReviewChatSavedConversationRepositoryTest.kt`
- Test: `app/src/androidTest/java/com/mindflow/app/ui/screens/reviewchat/ReviewChatScreenInstrumentedTest.kt`
- Test: `app/src/androidTest/java/com/mindflow/app/ui/screens/flow/ReviewChatEntryCardInstrumentedTest.kt`

- [ ] **Step 1: Run the full targeted verification suite**

Run:

```bash
JAVA_HOME=$HOME/.jdks/temurin-21 ./gradlew --no-daemon \
  :app:testDebugUnitTest \
  --tests com.mindflow.app.data.reviewchat.ReviewChatPlannerTest \
  --tests com.mindflow.app.ui.screens.reviewchat.ReviewChatViewModelTest
JAVA_HOME=$HOME/.jdks/temurin-21 ./gradlew --no-daemon \
  :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.mindflow.app.data.reviewchat.RoomReviewChatSavedConversationRepositoryTest,com.mindflow.app.ui.screens.reviewchat.ReviewChatScreenInstrumentedTest,com.mindflow.app.ui.screens.flow.ReviewChatEntryCardInstrumentedTest
JAVA_HOME=$HOME/.jdks/temurin-21 ./gradlew --no-daemon :app:assembleRelease
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 2: Manually verify the product flow on device or emulator**

Checklist:

```text
1. 打开“回看”页，看到“和历史聊聊”入口卡，而不是“搜索记录”
2. 输入一句问题并发送，进入全屏聊天页
3. 首轮回答显示 provider 文案
4. 继续追问一轮，消息流继续增长
5. 返回回看页，未保存会话不出现历史入口
6. 再次发问并保存，返回回看页出现“查看最近保存的对话”
7. 点开最近保存对话，进入只读历史会话
```

- [ ] **Step 3: If manual verification finds string drift, unify helper text**

Code to centralize before final ship:

```kotlin
// app/src/main/java/com/mindflow/app/data/reviewchat/ReviewChatModels.kt
fun buildReviewChatProviderLine(
    provider: ReviewChatProvider,
    fallbackOccurred: Boolean,
): String = when {
    provider == ReviewChatProvider.CLOUD -> "本次由云侧完成"
    fallbackOccurred -> "云侧不可用，已回退端侧"
    else -> "本次由端侧完成"
}
```

- [ ] **Step 4: Commit the verified feature**

```bash
git add \
  app/src/main/java/com/mindflow/app/data/reviewchat \
  app/src/main/java/com/mindflow/app/data/local/reviewchat \
  app/src/main/java/com/mindflow/app/ui/navigation/ReviewChatSeed.kt \
  app/src/main/java/com/mindflow/app/ui/screens/reviewchat \
  app/src/main/java/com/mindflow/app/ui/screens/flow/ReviewChatEntryCard.kt \
  app/src/main/java/com/mindflow/app/ui/screens/flow/FlowScreen.kt \
  app/src/main/java/com/mindflow/app/ui/MindFlowApp.kt \
  app/src/main/java/com/mindflow/app/MainActivity.kt \
  app/src/main/java/com/mindflow/app/di/AppContainer.kt \
  app/src/main/java/com/mindflow/app/data/topic/AiServiceClient.kt \
  app/src/main/java/com/mindflow/app/data/localmodel/OnDeviceAiClient.kt \
  app/src/test/java/com/mindflow/app/data/reviewchat/ReviewChatPlannerTest.kt \
  app/src/test/java/com/mindflow/app/ui/screens/reviewchat/ReviewChatViewModelTest.kt \
  app/src/androidTest/java/com/mindflow/app/data/reviewchat/RoomReviewChatSavedConversationRepositoryTest.kt \
  app/src/androidTest/java/com/mindflow/app/ui/screens/reviewchat/ReviewChatScreenInstrumentedTest.kt \
  app/src/androidTest/java/com/mindflow/app/ui/screens/flow/ReviewChatEntryCardInstrumentedTest.kt
git commit -m "feat: add review chat flow"
```

## Self-Review

### Spec coverage

- 回看页入口卡：Task 5
- 发问即进入全屏聊天页：Task 4 + Task 5
- 检索范围 `原始记录 + 已沉淀结构`：Task 1
- 自动模式 `云侧优先 -> 端侧回退`：Task 1
- 多轮追问：Task 3 + Task 4
- 显式保存：Task 2 + Task 3
- 最近一次已保存会话可再次打开：Task 2 + Task 5

### Placeholder scan

- No placeholder markers remain in executable steps.

### Type consistency

- Shared domain types live in `ReviewChatModels.kt`.
- Provider display copy is called out as a shared helper in Task 6 to avoid string drift.
- Repository, planner, and ViewModel all consume `ReviewChatMessage`, so role/provider fields stay aligned.
