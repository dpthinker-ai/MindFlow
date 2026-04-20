# Local Knowledge Brain Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a local knowledge brain around Gemma 4 E4B that incrementally maintains `MemoryFragment`, `MemoryThread`, and `MemoryDigest`, then uses that layer to improve review chat and concept graph behavior.

**Architecture:** Add a new `data/knowledgebrain` module that sits between raw notes and the existing structure layer. It owns memory-layer schemas, incremental maintenance, and retrieval helpers. Review chat consumes it first, while graph consumes a thinner thread-derived enhancement path.

**Tech Stack:** Kotlin, Room, coroutines/flows, existing `MarkdownNoteRepository`, existing `ReviewChatPlanner`, existing `DirectionWikiCoordinator`, existing Gemma 4 E4B on-device client.

---

## File Structure

### New files

- `app/src/main/java/com/mindflow/app/data/local/entity/MemoryFragmentEntity.kt`
  - Room entity for per-note incremental memory fragments.
- `app/src/main/java/com/mindflow/app/data/local/entity/MemoryThreadEntity.kt`
  - Room entity for merged topic/question threads.
- `app/src/main/java/com/mindflow/app/data/local/entity/MemoryDigestEntity.kt`
  - Room entity for day/week/topic/question digests.
- `app/src/main/java/com/mindflow/app/data/local/dao/MemoryLayerDao.kt`
  - DAO for upsert/query/delete operations across memory layer tables.
- `app/src/main/java/com/mindflow/app/data/knowledgebrain/MemoryLayerModels.kt`
  - Domain models and mapping helpers for fragment/thread/digest.
- `app/src/main/java/com/mindflow/app/data/knowledgebrain/MemoryLayerRepository.kt`
  - Repository interface plus Room-backed implementation.
- `app/src/main/java/com/mindflow/app/data/knowledgebrain/LocalKnowledgeBrainPromptFactory.kt`
  - Prompt builders for fragment extraction, thread merge, digest refresh.
- `app/src/main/java/com/mindflow/app/data/knowledgebrain/LocalKnowledgeBrainPlanner.kt`
  - Incremental maintenance coordinator for note ingestion, merge, digest refresh, and rebuild hooks.
- `app/src/main/java/com/mindflow/app/data/knowledgebrain/MemoryLayerChatAssembler.kt`
  - Retrieval helper that converts memory layer results into chat-ready context packets.
- `app/src/test/java/com/mindflow/app/data/knowledgebrain/MemoryLayerRepositoryTest.kt`
  - Repository tests.
- `app/src/test/java/com/mindflow/app/data/knowledgebrain/LocalKnowledgeBrainPlannerTest.kt`
  - Incremental maintenance tests.
- `app/src/test/java/com/mindflow/app/data/knowledgebrain/MemoryLayerChatAssemblerTest.kt`
  - Chat retrieval/order tests.

### Modified files

- `app/src/main/java/com/mindflow/app/data/local/MindFlowDatabase.kt`
  - Register new entities, DAO, and migrations.
- `app/src/main/java/com/mindflow/app/di/AppContainer.kt`
  - Wire repository, planner, and graph/chat consumers.
- `app/src/main/java/com/mindflow/app/data/repository/MarkdownNoteRepository.kt`
  - Enqueue local knowledge maintenance after note create/update.
- `app/src/main/java/com/mindflow/app/data/reviewchat/ReviewChatModels.kt`
  - Extend context packet to carry memory-layer results and raw-note expansion metadata.
- `app/src/main/java/com/mindflow/app/data/reviewchat/ReviewChatPlanner.kt`
  - Consume `MemoryLayerChatAssembler` first and support explicit full-note expansion.
- `app/src/main/java/com/mindflow/app/data/reviewchat/ReviewChatPromptFactory.kt`
  - Reflect memory-layer-first prompt structure and raw-note expansion path.
- `app/src/main/java/com/mindflow/app/data/wiki/DirectionWikiCoordinator.kt`
  - Accept thread-derived enhancement input for graph defaults/weak edges.
- `app/src/main/java/com/mindflow/app/ui/screens/flow/FlowScreen.kt`
  - Add manual “refresh local knowledge layer” entry if no better existing control surface exists.
- `app/src/main/java/com/mindflow/app/ui/screens/reviewchat/ReviewChatViewModel.kt`
  - Surface “full record available” state and handle explicit expansion/open-record affordance.
- `app/src/test/java/com/mindflow/app/data/reviewchat/ReviewChatPlannerTest.kt`
  - Add memory-layer and full-record coverage.

### Existing files to read before implementation

- `app/src/main/java/com/mindflow/app/data/local/MindFlowDatabase.kt`
- `app/src/main/java/com/mindflow/app/data/local/entity/NoteEntity.kt`
- `app/src/main/java/com/mindflow/app/data/localmodel/LocalKnowledgeMaintenancePlanner.kt`
- `app/src/main/java/com/mindflow/app/data/localmodel/LocalKnowledgeMaintenanceSnapshot.kt`
- `app/src/main/java/com/mindflow/app/data/reviewchat/ReviewChatPlanner.kt`
- `app/src/main/java/com/mindflow/app/data/reviewchat/ReviewChatPromptFactory.kt`
- `app/src/main/java/com/mindflow/app/data/wiki/DirectionWikiCoordinator.kt`
- `app/src/main/java/com/mindflow/app/data/wiki/DirectionWikiModels.kt`
- `app/src/main/java/com/mindflow/app/data/repository/MarkdownNoteRepository.kt`

---

### Task 1: Add the Memory Layer storage foundation

**Files:**
- Create: `app/src/main/java/com/mindflow/app/data/local/entity/MemoryFragmentEntity.kt`
- Create: `app/src/main/java/com/mindflow/app/data/local/entity/MemoryThreadEntity.kt`
- Create: `app/src/main/java/com/mindflow/app/data/local/entity/MemoryDigestEntity.kt`
- Create: `app/src/main/java/com/mindflow/app/data/local/dao/MemoryLayerDao.kt`
- Create: `app/src/main/java/com/mindflow/app/data/knowledgebrain/MemoryLayerModels.kt`
- Modify: `app/src/main/java/com/mindflow/app/data/local/MindFlowDatabase.kt`
- Test: `app/src/test/java/com/mindflow/app/data/knowledgebrain/MemoryLayerRepositoryTest.kt`

- [ ] **Step 1: Write the failing repository test skeleton**

```kotlin
@Test
fun upsertFragmentAndThread_roundTripsFromRoom() = runTest {
    val db = buildTestDb()
    val dao = db.memoryLayerDao()

    dao.upsertFragment(
        MemoryFragmentEntity(
            id = "fragment-1",
            sourceNoteIds = listOf(11L),
            topicKey = "topic/leakspace",
            questionKey = "question/optimization",
            summary = "记录提出 leakspace 对抖音推荐链路是否有指导意义。",
            salience = 0.82,
            timeSpanStart = 1710000000000,
            timeSpanEnd = 1710000000000,
            createdAt = 1710000000000,
            updatedAt = 1710000000000,
        ),
    )

    dao.upsertThread(
        MemoryThreadEntity(
            id = "thread-1",
            title = "leakspace 与推荐优化",
            type = "QUESTION",
            fragmentIds = listOf("fragment-1"),
            summary = "一条持续问题线，关注 leakspace 对推荐优化是否有实际指导。",
            currentState = "刚形成问题线",
            openQuestions = listOf("是否已在真实分发链里验证"),
            updatedAt = 1710000000100,
        ),
    )

    assertThat(dao.loadThread("thread-1")?.fragmentIds).containsExactly("fragment-1")
}
```

- [ ] **Step 2: Run the test to confirm missing DAO/entity support**

Run: `JAVA_HOME=$HOME/.jdks/temurin-21 ./gradlew --no-daemon :app:testDebugUnitTest --tests com.mindflow.app.data.knowledgebrain.MemoryLayerRepositoryTest`
Expected: FAIL with missing entity/DAO/database registration errors.

- [ ] **Step 3: Add Room entities and DAO minimally**

```kotlin
@Entity(tableName = "memory_fragments")
data class MemoryFragmentEntity(
    @PrimaryKey val id: String,
    val sourceNoteIds: List<Long>,
    val topicKey: String,
    val questionKey: String,
    val summary: String,
    val salience: Double,
    val timeSpanStart: Long,
    val timeSpanEnd: Long,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "memory_threads")
data class MemoryThreadEntity(
    @PrimaryKey val id: String,
    val title: String,
    val type: String,
    val fragmentIds: List<String>,
    val summary: String,
    val currentState: String,
    val openQuestions: List<String>,
    val updatedAt: Long,
)

@Entity(tableName = "memory_digests")
data class MemoryDigestEntity(
    @PrimaryKey val id: String,
    val scopeType: String,
    val scopeKey: String,
    val summary: String,
    val highlights: List<String>,
    val sourceFragmentIds: List<String>,
    val updatedAt: Long,
)

@Dao
interface MemoryLayerDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFragment(entity: MemoryFragmentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertThread(entity: MemoryThreadEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDigest(entity: MemoryDigestEntity)

    @Query("SELECT * FROM memory_threads WHERE id = :id LIMIT 1")
    suspend fun loadThread(id: String): MemoryThreadEntity?
}
```

- [ ] **Step 4: Register the new entities, DAO, and migration**

```kotlin
@Database(
    entities = [
        NoteEntity::class,
        NoteStatusHistoryEntity::class,
        MemoryFragmentEntity::class,
        MemoryThreadEntity::class,
        MemoryDigestEntity::class,
    ],
    version = 7,
    exportSchema = false,
)
abstract class MindFlowDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun noteStatusHistoryDao(): NoteStatusHistoryDao
    abstract fun memoryLayerDao(): MemoryLayerDao

    companion object {
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS memory_fragments (id TEXT NOT NULL PRIMARY KEY, sourceNoteIds TEXT NOT NULL, topicKey TEXT NOT NULL, questionKey TEXT NOT NULL, summary TEXT NOT NULL, salience REAL NOT NULL, timeSpanStart INTEGER NOT NULL, timeSpanEnd INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS memory_threads (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, type TEXT NOT NULL, fragmentIds TEXT NOT NULL, summary TEXT NOT NULL, currentState TEXT NOT NULL, openQuestions TEXT NOT NULL, updatedAt INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS memory_digests (id TEXT NOT NULL PRIMARY KEY, scopeType TEXT NOT NULL, scopeKey TEXT NOT NULL, summary TEXT NOT NULL, highlights TEXT NOT NULL, sourceFragmentIds TEXT NOT NULL, updatedAt INTEGER NOT NULL)")
            }
        }
    }
}
```

- [ ] **Step 5: Add domain models and mapping helpers**

```kotlin
enum class MemoryThreadType { TOPIC, QUESTION, DIRECTION }
enum class MemoryDigestScopeType { DAY, WEEK, TOPIC, QUESTION }

data class MemoryFragment(
    val id: String,
    val sourceNoteIds: List<Long>,
    val topicKey: String,
    val questionKey: String,
    val summary: String,
    val salience: Double,
    val timeSpanStart: Long,
    val timeSpanEnd: Long,
    val createdAt: Long,
    val updatedAt: Long,
)
```

- [ ] **Step 6: Re-run the repository test**

Run: `JAVA_HOME=$HOME/.jdks/temurin-21 ./gradlew --no-daemon :app:testDebugUnitTest --tests com.mindflow.app.data.knowledgebrain.MemoryLayerRepositoryTest`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/mindflow/app/data/local/MindFlowDatabase.kt \
  app/src/main/java/com/mindflow/app/data/local/dao/MemoryLayerDao.kt \
  app/src/main/java/com/mindflow/app/data/local/entity/MemoryFragmentEntity.kt \
  app/src/main/java/com/mindflow/app/data/local/entity/MemoryThreadEntity.kt \
  app/src/main/java/com/mindflow/app/data/local/entity/MemoryDigestEntity.kt \
  app/src/main/java/com/mindflow/app/data/knowledgebrain/MemoryLayerModels.kt \
  app/src/test/java/com/mindflow/app/data/knowledgebrain/MemoryLayerRepositoryTest.kt

git commit -m "feat: add memory layer storage foundation"
```

### Task 2: Add the memory-layer repository and query API

**Files:**
- Create: `app/src/main/java/com/mindflow/app/data/knowledgebrain/MemoryLayerRepository.kt`
- Modify: `app/src/main/java/com/mindflow/app/di/AppContainer.kt`
- Test: `app/src/test/java/com/mindflow/app/data/knowledgebrain/MemoryLayerRepositoryTest.kt`

- [ ] **Step 1: Extend the failing test with repository behavior**

```kotlin
@Test
fun repository_loadsDayDigestAndActiveThreads() = runTest {
    val repository = RoomMemoryLayerRepository(db.memoryLayerDao())

    repository.upsertDigest(
        MemoryDigest(
            id = "day-2026-04-19",
            scopeType = MemoryDigestScopeType.DAY,
            scopeKey = "2026-04-19",
            summary = "这一天主要在聊 leakspace 与推荐链路。",
            highlights = listOf("讨论是否有优化指导意义"),
            sourceFragmentIds = listOf("fragment-1"),
            updatedAt = 1710000001000,
        ),
    )

    assertThat(repository.loadDigest(MemoryDigestScopeType.DAY, "2026-04-19")?.summary)
        .contains("leakspace")
}
```

- [ ] **Step 2: Run the repository test to confirm missing repository API**

Run: `JAVA_HOME=$HOME/.jdks/temurin-21 ./gradlew --no-daemon :app:testDebugUnitTest --tests com.mindflow.app.data.knowledgebrain.MemoryLayerRepositoryTest`
Expected: FAIL with unresolved repository symbols.

- [ ] **Step 3: Create the repository interface and Room implementation**

```kotlin
interface MemoryLayerRepository {
    suspend fun upsertFragment(fragment: MemoryFragment)
    suspend fun upsertThread(thread: MemoryThread)
    suspend fun upsertDigest(digest: MemoryDigest)
    suspend fun loadDigest(scopeType: MemoryDigestScopeType, scopeKey: String): MemoryDigest?
    suspend fun loadThreadsForQuery(keywords: List<String>, limit: Int): List<MemoryThread>
    suspend fun loadFragmentsForNotes(noteIds: List<Long>): List<MemoryFragment>
    suspend fun clearAll()
}

class RoomMemoryLayerRepository(
    private val dao: MemoryLayerDao,
) : MemoryLayerRepository {
    override suspend fun upsertFragment(fragment: MemoryFragment) = dao.upsertFragment(fragment.toEntity())
    override suspend fun upsertThread(thread: MemoryThread) = dao.upsertThread(thread.toEntity())
    override suspend fun upsertDigest(digest: MemoryDigest) = dao.upsertDigest(digest.toEntity())
    override suspend fun loadDigest(scopeType: MemoryDigestScopeType, scopeKey: String): MemoryDigest? =
        dao.loadDigest(scopeType.name, scopeKey)?.toModel()
}
```

- [ ] **Step 4: Expand DAO queries just enough for the repository**

```kotlin
@Query("SELECT * FROM memory_digests WHERE scopeType = :scopeType AND scopeKey = :scopeKey LIMIT 1")
suspend fun loadDigest(scopeType: String, scopeKey: String): MemoryDigestEntity?

@Query("SELECT * FROM memory_threads ORDER BY updatedAt DESC LIMIT :limit")
suspend fun loadLatestThreads(limit: Int): List<MemoryThreadEntity>

@Query("DELETE FROM memory_fragments")
suspend fun clearFragments()
```

- [ ] **Step 5: Wire the repository in `AppContainer`**

```kotlin
val memoryLayerRepository: MemoryLayerRepository = RoomMemoryLayerRepository(
    dao = database.memoryLayerDao(),
)
```

- [ ] **Step 6: Re-run the repository test**

Run: `JAVA_HOME=$HOME/.jdks/temurin-21 ./gradlew --no-daemon :app:testDebugUnitTest --tests com.mindflow.app.data.knowledgebrain.MemoryLayerRepositoryTest`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/mindflow/app/data/knowledgebrain/MemoryLayerRepository.kt \
  app/src/main/java/com/mindflow/app/di/AppContainer.kt \
  app/src/test/java/com/mindflow/app/data/knowledgebrain/MemoryLayerRepositoryTest.kt

git commit -m "feat: add memory layer repository"
```

### Task 3: Build the incremental local knowledge brain planner

**Files:**
- Create: `app/src/main/java/com/mindflow/app/data/knowledgebrain/LocalKnowledgeBrainPromptFactory.kt`
- Create: `app/src/main/java/com/mindflow/app/data/knowledgebrain/LocalKnowledgeBrainPlanner.kt`
- Modify: `app/src/main/java/com/mindflow/app/data/repository/MarkdownNoteRepository.kt`
- Modify: `app/src/main/java/com/mindflow/app/di/AppContainer.kt`
- Test: `app/src/test/java/com/mindflow/app/data/knowledgebrain/LocalKnowledgeBrainPlannerTest.kt`

- [ ] **Step 1: Write a failing planner test for note ingestion**

```kotlin
@Test
fun ingestNote_generatesFragmentThreadAndDayDigest() = runTest {
    val repository = FakeMemoryLayerRepository()
    val planner = LocalKnowledgeBrainPlanner(
        memoryLayerRepository = repository,
        loadNoteById = { sampleNote(id = 7L, topic = "leakspace", content = "讨论 leakspace 对推荐链路是否有帮助") },
        runOnDevice = { prompt -> AiChatResult.Success("fragmentSummary=这条记录在追问 leakspace 是否能优化推荐；topicKey=topic/leakspace;questionKey=question/recommendation") },
        now = { 1710000000000L },
    )

    planner.ingestNote(noteId = 7L)

    assertThat(repository.fragments).hasSize(1)
    assertThat(repository.threads.single().title).contains("leakspace")
    assertThat(repository.digests.single().scopeType).isEqualTo(MemoryDigestScopeType.DAY)
}
```

- [ ] **Step 2: Run the test to confirm the planner does not exist**

Run: `JAVA_HOME=$HOME/.jdks/temurin-21 ./gradlew --no-daemon :app:testDebugUnitTest --tests com.mindflow.app.data.knowledgebrain.LocalKnowledgeBrainPlannerTest`
Expected: FAIL with missing planner/prompt factory symbols.

- [ ] **Step 3: Create the prompt factory with explicit output contracts**

```kotlin
object LocalKnowledgeBrainPromptFactory {
    fun fragment(note: NoteEntity): String = buildString {
        appendLine("你在维护一个端侧本地知识层。")
        appendLine("任务：把下面这条记录压成一个 MemoryFragment。")
        appendLine("输出格式：")
        appendLine("fragmentSummary=<一句摘要>")
        appendLine("topicKey=<topic/...>")
        appendLine("questionKey=<question/...或空>")
        appendLine("salience=<0.0-1.0>")
        appendLine("记录标题：${note.topic}")
        appendLine("记录正文：${note.content}")
    }
}
```

- [ ] **Step 4: Implement the planner minimally**

```kotlin
class LocalKnowledgeBrainPlanner(
    private val memoryLayerRepository: MemoryLayerRepository,
    private val loadNoteById: suspend (Long) -> NoteEntity?,
    private val runOnDevice: suspend (String) -> AiChatResult,
    private val now: () -> Long,
) {
    suspend fun ingestNote(noteId: Long) {
        val note = loadNoteById(noteId) ?: return
        val result = runOnDevice(LocalKnowledgeBrainPromptFactory.fragment(note)) as? AiChatResult.Success ?: return
        val parsed = parseFragmentResult(note, result.content)
        memoryLayerRepository.upsertFragment(parsed.fragment)
        memoryLayerRepository.upsertThread(parsed.thread)
        memoryLayerRepository.upsertDigest(parsed.dayDigest)
    }
}
```

- [ ] **Step 5: Enqueue incremental maintenance from note writes**

```kotlin
override suspend fun createNote(...): Long {
    val noteId = storageMutex.withLock { /* existing note write */ }
    localKnowledgeBrainPlanner.enqueueNoteIngestion(noteId)
    return noteId
}

override suspend fun updateNote(...): Boolean {
    val changed = storageMutex.withLock { /* existing note update */ }
    if (changed) localKnowledgeBrainPlanner.enqueueNoteIngestion(noteId)
    return changed
}
```

- [ ] **Step 6: Re-run the planner test**

Run: `JAVA_HOME=$HOME/.jdks/temurin-21 ./gradlew --no-daemon :app:testDebugUnitTest --tests com.mindflow.app.data.knowledgebrain.LocalKnowledgeBrainPlannerTest`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/mindflow/app/data/knowledgebrain/LocalKnowledgeBrainPromptFactory.kt \
  app/src/main/java/com/mindflow/app/data/knowledgebrain/LocalKnowledgeBrainPlanner.kt \
  app/src/main/java/com/mindflow/app/data/repository/MarkdownNoteRepository.kt \
  app/src/main/java/com/mindflow/app/di/AppContainer.kt \
  app/src/test/java/com/mindflow/app/data/knowledgebrain/LocalKnowledgeBrainPlannerTest.kt

git commit -m "feat: add incremental local knowledge brain planner"
```

### Task 4: Make review chat consume the memory layer and support raw expansion

**Files:**
- Create: `app/src/main/java/com/mindflow/app/data/knowledgebrain/MemoryLayerChatAssembler.kt`
- Modify: `app/src/main/java/com/mindflow/app/data/reviewchat/ReviewChatModels.kt`
- Modify: `app/src/main/java/com/mindflow/app/data/reviewchat/ReviewChatPlanner.kt`
- Modify: `app/src/main/java/com/mindflow/app/data/reviewchat/ReviewChatPromptFactory.kt`
- Modify: `app/src/main/java/com/mindflow/app/ui/screens/reviewchat/ReviewChatViewModel.kt`
- Test: `app/src/test/java/com/mindflow/app/data/knowledgebrain/MemoryLayerChatAssemblerTest.kt`
- Test: `app/src/test/java/com/mindflow/app/data/reviewchat/ReviewChatPlannerTest.kt`

- [ ] **Step 1: Write a failing assembler test for retrieval priority**

```kotlin
@Test
fun assemble_questionAboutDay_prefersDayDigestThenRawNotes() = runTest {
    val assembler = MemoryLayerChatAssembler(
        memoryLayerRepository = FakeMemoryLayerRepository(
            digests = listOf(sampleDayDigest()),
            threads = listOf(sampleThread()),
        ),
        loadNotes = { listOf(sampleNote(id = 42L, topic = "2026-04-10", content = "完整原文")) },
    )

    val packet = assembler.assemble(
        question = "把 4 月 10 号那天的完整内容发给我",
        priorMessages = emptyList(),
    )

    assertThat(packet.memoryDigestSnippets.first()).contains("4 月 10")
    assertThat(packet.rawNoteDetails.single().fullContent).contains("完整原文")
}
```

- [ ] **Step 2: Run the tests to confirm the new retrieval layer is missing**

Run: `JAVA_HOME=$HOME/.jdks/temurin-21 ./gradlew --no-daemon :app:testDebugUnitTest --tests com.mindflow.app.data.knowledgebrain.MemoryLayerChatAssemblerTest --tests com.mindflow.app.data.reviewchat.ReviewChatPlannerTest`
Expected: FAIL with missing assembler/raw expansion fields.

- [ ] **Step 3: Extend the review chat models for memory-layer results**

```kotlin
data class ReviewChatRawNoteDetail(
    val noteId: Long,
    val title: String,
    val dateLabel: String,
    val fullContent: String,
)

data class ReviewChatContextPacket(
    val intent: ReviewChatIntent,
    val question: String,
    val sessionSummary: String,
    val conversationSnippets: List<String>,
    val memoryDigestSnippets: List<String>,
    val memoryThreadSnippets: List<String>,
    val knowledgeBaseSnippets: List<String>,
    val wikiSnippets: List<String>,
    val rawNoteSnippets: List<String>,
    val rawNoteDetails: List<ReviewChatRawNoteDetail>,
    val structuredSnippets: List<String>,
)
```

- [ ] **Step 4: Implement the chat assembler and use it inside `ReviewChatPlanner`**

```kotlin
class MemoryLayerChatAssembler(
    private val memoryLayerRepository: MemoryLayerRepository,
    private val loadNotes: suspend () -> List<NoteEntity>,
) {
    suspend fun assemble(question: String, priorMessages: List<ReviewChatMessage>): ReviewChatContextPacket {
        val intent = classifyReviewChatIntent(question)
        val dayDigest = findDayDigestIfRequested(question)
        val matchingThreads = memoryLayerRepository.loadThreadsForQuery(extractReviewChatKeywords(question), limit = 4)
        val rawDetails = if (question.contains("完整") || question.contains("原文")) {
            loadFullNotesFor(question)
        } else {
            emptyList()
        }
        return ReviewChatContextPacket(
            intent = intent,
            question = question,
            sessionSummary = priorMessages.takeLast(2).joinToString("\n") { it.content.take(120) },
            conversationSnippets = buildConversationSnippets(priorMessages),
            memoryDigestSnippets = listOfNotNull(dayDigest?.summary),
            memoryThreadSnippets = matchingThreads.map { it.summary },
            knowledgeBaseSnippets = emptyList(),
            wikiSnippets = emptyList(),
            rawNoteSnippets = rawDetails.map { "记录｜${it.dateLabel}｜${it.title}" },
            rawNoteDetails = rawDetails,
            structuredSnippets = emptyList(),
        )
    }
}
```

- [ ] **Step 5: Update the prompt to include explicit full-record mode**

```kotlin
appendLine("回答要求：")
appendLine("1. 先回答当前问题。")
appendLine("2. 如果用户明确要求完整内容，优先返回完整记录内容，而不是只做摘要。")
appendLine("3. 如果提供了原始记录入口，明确告诉用户可以打开哪条记录。")
if (packet.rawNoteDetails.isNotEmpty()) {
    appendLine("完整记录：")
    packet.rawNoteDetails.forEach {
        appendLine("- noteId=${it.noteId}｜${it.dateLabel}｜${it.title}｜${it.fullContent}")
    }
}
```

- [ ] **Step 6: Add planner assertions for explicit full-content questions**

```kotlin
@Test
fun answer_fullRecordQuestion_includesRawDetailInsteadOfSummaryOnly() = runTest {
    val planner = buildPlannerForFullRecordQuestion()
    val result = planner.answer(
        ReviewChatTurnRequest(
            question = "把 4 月 10 号那条完整记录发给我",
            priorMessages = emptyList(),
        ),
    )

    assertThat(result.answer).contains("完整原文")
}
```

- [ ] **Step 7: Re-run the tests**

Run: `JAVA_HOME=$HOME/.jdks/temurin-21 ./gradlew --no-daemon :app:testDebugUnitTest --tests com.mindflow.app.data.knowledgebrain.MemoryLayerChatAssemblerTest --tests com.mindflow.app.data.reviewchat.ReviewChatPlannerTest`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/mindflow/app/data/knowledgebrain/MemoryLayerChatAssembler.kt \
  app/src/main/java/com/mindflow/app/data/reviewchat/ReviewChatModels.kt \
  app/src/main/java/com/mindflow/app/data/reviewchat/ReviewChatPlanner.kt \
  app/src/main/java/com/mindflow/app/data/reviewchat/ReviewChatPromptFactory.kt \
  app/src/main/java/com/mindflow/app/ui/screens/reviewchat/ReviewChatViewModel.kt \
  app/src/test/java/com/mindflow/app/data/knowledgebrain/MemoryLayerChatAssemblerTest.kt \
  app/src/test/java/com/mindflow/app/data/reviewchat/ReviewChatPlannerTest.kt

git commit -m "feat: route review chat through memory layer"
```

### Task 5: Add explicit open-record affordance and graph thread enhancement

**Files:**
- Modify: `app/src/main/java/com/mindflow/app/ui/MindFlowApp.kt`
- Modify: `app/src/main/java/com/mindflow/app/ui/screens/reviewchat/ReviewChatScreen.kt`
- Modify: `app/src/main/java/com/mindflow/app/data/wiki/DirectionWikiCoordinator.kt`
- Modify: `app/src/main/java/com/mindflow/app/ui/screens/flow/FlowScreen.kt`
- Test: `app/src/test/java/com/mindflow/app/ui/screens/reviewchat/ReviewChatViewModelTest.kt`
- Test: `app/src/test/java/com/mindflow/app/ui/screens/flow/KnowledgeGraphScreenTest.kt`

- [ ] **Step 1: Write a failing review-chat UI-state test for open-record affordance**

```kotlin
@Test
fun assistantTurn_withRawNoteReference_exposesOpenRecordAction() {
    val state = ReviewChatUiState(
        messages = listOf(sampleAssistantMessage()),
        openRecordNoteId = 42L,
    )

    assertThat(state.openRecordNoteId).isEqualTo(42L)
}
```

- [ ] **Step 2: Run the targeted tests**

Run: `JAVA_HOME=$HOME/.jdks/temurin-21 ./gradlew --no-daemon :app:testDebugUnitTest --tests com.mindflow.app.ui.screens.reviewchat.ReviewChatViewModelTest --tests com.mindflow.app.ui.screens.flow.KnowledgeGraphScreenTest`
Expected: FAIL with missing open-record and thread-enhancement behavior.

- [ ] **Step 3: Add an explicit “open record” action to the chat UI contract**

```kotlin
ReviewChatScreen(
    uiState = uiState,
    onBack = onBack,
    onDraftChange = viewModel::onDraftChange,
    onSend = viewModel::sendDraft,
    onRetry = viewModel::retry,
    onSave = viewModel::saveConversation,
    onOpenRecord = viewModel::openReferencedRecord,
)
```

```kotlin
if (message.referencedNoteId != null) {
    GhostActionButton(
        text = "打开原记录",
        onClick = { onOpenRecord(message.referencedNoteId) },
    )
}
```

- [ ] **Step 4: Route the open-record callback back through existing note navigation**

```kotlin
ReviewChatRoute(
    seed = seed,
    planner = reviewChatPlanner,
    savedConversationRepository = reviewChatSavedConversationRepository,
    onBack = { navController.popBackStack() },
    onOpenRecord = openNoteSafely,
)
```

- [ ] **Step 5: Add a minimal thread-derived graph enhancement in `DirectionWikiCoordinator`**

```kotlin
val threadHints = memoryLayerRepository.loadThreadsForQuery(emptyList(), limit = 6)
val boostedDefaultCenter = threadHints.firstOrNull()?.title?.let { title ->
    snapshot.conceptGraph.nodes.firstOrNull { node -> node.label.contains(title.take(8)) }?.conceptId
}
val conceptGraph = snapshot.conceptGraph.copy(
    defaultCenterNodeId = boostedDefaultCenter ?: snapshot.conceptGraph.defaultCenterNodeId,
)
```

- [ ] **Step 6: Add a manual refresh entry in the flow/review surface**

```kotlin
GhostActionButton(
    text = "刷新本地知识层",
    onClick = onRefreshLocalKnowledgeBrain,
)
```

- [ ] **Step 7: Re-run the UI tests**

Run: `JAVA_HOME=$HOME/.jdks/temurin-21 ./gradlew --no-daemon :app:testDebugUnitTest --tests com.mindflow.app.ui.screens.reviewchat.ReviewChatViewModelTest --tests com.mindflow.app.ui.screens.flow.KnowledgeGraphScreenTest`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/mindflow/app/ui/MindFlowApp.kt \
  app/src/main/java/com/mindflow/app/ui/screens/reviewchat/ReviewChatScreen.kt \
  app/src/main/java/com/mindflow/app/data/wiki/DirectionWikiCoordinator.kt \
  app/src/main/java/com/mindflow/app/ui/screens/flow/FlowScreen.kt \
  app/src/test/java/com/mindflow/app/ui/screens/reviewchat/ReviewChatViewModelTest.kt \
  app/src/test/java/com/mindflow/app/ui/screens/flow/KnowledgeGraphScreenTest.kt

git commit -m "feat: expose local knowledge brain through chat and graph"
```

### Task 6: Add rebuild hooks, verification, and documentation updates

**Files:**
- Modify: `app/src/main/java/com/mindflow/app/data/knowledgebrain/LocalKnowledgeBrainPlanner.kt`
- Modify: `app/src/main/java/com/mindflow/app/ui/screens/flow/FlowScreen.kt`
- Modify: `app/src/test/java/com/mindflow/app/data/knowledgebrain/LocalKnowledgeBrainPlannerTest.kt`
- Modify: `docs/superpowers/specs/2026-04-19-local-knowledge-brain-design.md` (only if spec/implementation mismatch is discovered)

- [ ] **Step 1: Write a failing planner test for rebuild/reset**

```kotlin
@Test
fun rebuildAll_clearsExistingMemoryLayerThenReingestsNotes() = runTest {
    val repository = FakeMemoryLayerRepository()
    val planner = buildPlanner(repository = repository)

    planner.rebuildAll()

    assertThat(repository.clearAllCalled).isTrue()
    assertThat(repository.fragments).isNotEmpty()
}
```

- [ ] **Step 2: Run the planner test**

Run: `JAVA_HOME=$HOME/.jdks/temurin-21 ./gradlew --no-daemon :app:testDebugUnitTest --tests com.mindflow.app.data.knowledgebrain.LocalKnowledgeBrainPlannerTest`
Expected: FAIL with missing rebuild logic.

- [ ] **Step 3: Add explicit rebuild and manual refresh entrypoints**

```kotlin
suspend fun refreshNow(noteId: Long? = null) {
    if (noteId != null) ingestNote(noteId) else rebuildAll()
}

suspend fun rebuildAll() {
    memoryLayerRepository.clearAll()
    loadAllNotes()
        .sortedBy(NoteEntity::updatedAt)
        .forEach { ingestNote(it.id) }
}
```

- [ ] **Step 4: Re-run the planner tests**

Run: `JAVA_HOME=$HOME/.jdks/temurin-21 ./gradlew --no-daemon :app:testDebugUnitTest --tests com.mindflow.app.data.knowledgebrain.LocalKnowledgeBrainPlannerTest`
Expected: PASS.

- [ ] **Step 5: Run the final targeted verification suite**

Run:
`JAVA_HOME=$HOME/.jdks/temurin-21 ./gradlew --no-daemon :app:testDebugUnitTest --tests com.mindflow.app.data.knowledgebrain.MemoryLayerRepositoryTest --tests com.mindflow.app.data.knowledgebrain.LocalKnowledgeBrainPlannerTest --tests com.mindflow.app.data.knowledgebrain.MemoryLayerChatAssemblerTest --tests com.mindflow.app.data.reviewchat.ReviewChatPlannerTest --tests com.mindflow.app.ui.screens.reviewchat.ReviewChatViewModelTest --tests com.mindflow.app.ui.screens.flow.KnowledgeGraphScreenTest`
Expected: PASS.

- [ ] **Step 6: Run compile verification**

Run:
`JAVA_HOME=$HOME/.jdks/temurin-21 ./gradlew --no-daemon :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/mindflow/app/data/knowledgebrain/LocalKnowledgeBrainPlanner.kt \
  app/src/main/java/com/mindflow/app/ui/screens/flow/FlowScreen.kt \
  app/src/test/java/com/mindflow/app/data/knowledgebrain/LocalKnowledgeBrainPlannerTest.kt

git commit -m "feat: add local knowledge brain rebuild flow"
```

## Self-Review

### Spec coverage

- `Memory Layer schema + repository`: Task 1, Task 2
- `note -> fragment/thread/digest incremental chain`: Task 3
- `review chat consumes Memory Layer first`: Task 4
- `full raw-note expansion + record entry`: Task 4, Task 5
- `graph partially consumes MemoryThread`: Task 5
- `manual refresh / low-frequency rebuild hooks`: Task 5, Task 6

No uncovered spec section remains for the v1 scope.

### Placeholder scan

- No `TODO`, `TBD`, or “implement later” placeholders remain.
- Each code-changing step includes concrete code, file targets, and verification commands.

### Type consistency

- `MemoryFragment`, `MemoryThread`, `MemoryDigest` are introduced first in Task 1/2 and reused consistently later.
- `MemoryLayerRepository` is the only read/write interface consumed by planner/chat/graph tasks.
- `LocalKnowledgeBrainPlanner` owns ingestion/rebuild entrypoints throughout the plan.

