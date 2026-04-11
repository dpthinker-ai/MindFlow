package com.mindflow.app.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mindflow.app.data.connect.NoteConnectionAnalyzer
import com.mindflow.app.data.connect.ThemeThread
import com.mindflow.app.data.local.entity.NoteEntity
import com.mindflow.app.data.model.FolderSource
import com.mindflow.app.data.model.KnowledgeTrust
import com.mindflow.app.data.model.MindFolder
import com.mindflow.app.data.model.MindFolderCatalog
import com.mindflow.app.data.model.NoteStatus
import com.mindflow.app.data.organize.BackgroundFolderOrganizer
import com.mindflow.app.data.model.SearchFilters
import com.mindflow.app.data.model.TimeRange
import com.mindflow.app.data.repository.NoteRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class QueryAction(
    val label: String,
    val title: String,
    val description: String,
    val startLabel: String,
    val emptyState: String,
) {
    NURTURE(
        label = "继续养",
        title = "把一个半成形的点继续养大",
        description = "先挑一个最近最值得继续喂养的对象，补一条推进记录、判断或实验。",
        startLabel = "继续养它",
        emptyState = "还没有足够材料可继续养，先记下一颗新火花。",
    ),
    COLLIDE(
        label = "撞一下",
        title = "让两个旧点发生一次碰撞",
        description = "把两条原本分开的线索放到同一页里，看它们是否能长出新问题或新方案。",
        startLabel = "撞一下",
        emptyState = "至少要有两条相关记录，碰撞才有意义。",
    ),
    CHALLENGE(
        label = "反驳它",
        title = "挑战一个看起来快成立的判断",
        description = "先找出最像结论的对象，再逼自己写出反例、风险和能推翻它的证据。",
        startLabel = "反驳一下",
        emptyState = "先让一个判断站住脚，再来挑战它。",
    ),
    ABSTRACT(
        label = "帮我抽象",
        title = "把重复出现的线索抽成结构",
        description = "把多条相似记录压成一个主题、模式、方法或可复用框架。",
        startLabel = "开始抽象",
        emptyState = "重复线索还不够明显，再积累几条会更有价值。",
    ),
    PLAN(
        label = "拉成方案",
        title = "把一个点拉成真正能执行的方案",
        description = "不是继续发散，而是收成目标、步骤、风险和验证口径。",
        startLabel = "拉成方案",
        emptyState = "先有一个明确对象，再把它拉成方案。",
    ),
    EVIDENCE(
        label = "找证据",
        title = "补最缺的一条证据链",
        description = "优先找还缺支撑的判断，明确需要什么材料、去哪补、补完会改变什么。",
        startLabel = "补证据",
        emptyState = "还没出现需要补证据的对象，先把判断写清楚。",
    ),
}

data class QuerySuggestion(
    val id: String,
    val title: String,
    val support: String,
    val detail: String,
    val actionLabel: String,
    val noteId: Long? = null,
    val threadKey: String? = null,
    val captureTopic: String,
    val captureContent: String,
    val captureFolderKey: String? = null,
    val captureTags: List<String> = emptyList(),
    val captureKnowledgeTrust: KnowledgeTrust = KnowledgeTrust.NONE,
)

data class SearchUiState(
    val filters: SearchFilters = SearchFilters(),
    val results: List<NoteEntity> = emptyList(),
    val availableFolders: List<MindFolder> = MindFolderCatalog.all,
    val availableTags: List<String> = emptyList(),
    val folderCounts: Map<String, Int> = emptyMap(),
    val uncategorizedCount: Int = 0,
    val pendingFolderClassificationCount: Int = 0,
    val lastAutoOrganizedAt: Long = 0L,
    val lastAutoOrganizedCount: Int = 0,
    val selectedAction: QueryAction = QueryAction.NURTURE,
    val suggestions: List<QuerySuggestion> = emptyList(),
    val focusLine: String = "",
)

sealed interface SearchEvent {
    data class Message(val text: String) : SearchEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModel(
    private val noteRepository: NoteRepository,
    private val backgroundFolderOrganizer: BackgroundFolderOrganizer,
    initialStatus: NoteStatus? = null,
    initialArchivedOnly: Boolean = false,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val selectedTag = MutableStateFlow<String?>(null)
    private val selectedFolder = MutableStateFlow<String?>(null)
    private val selectedStatus = MutableStateFlow(initialStatus)
    private val selectedTimeRange = MutableStateFlow(TimeRange.ALL)
    private val includeArchived = MutableStateFlow(false)
    private val archivedOnly = MutableStateFlow(initialArchivedOnly)
    private val selectedAction = MutableStateFlow(QueryAction.NURTURE)
    private val _events = MutableSharedFlow<SearchEvent>()
    val events = _events.asSharedFlow()

    private val filtersFlow = combine(
        combine(
            query,
            selectedTag,
            selectedFolder,
            selectedStatus,
            selectedTimeRange,
        ) { queryText, tag, folder, status, timeRange ->
            SearchFilters(
                query = queryText,
                tag = tag,
                folderKey = folder,
                status = status,
                timeRange = timeRange,
            )
        },
        archivedOnly,
    ) { baseFilters, archivedOnlyValue ->
        baseFilters.copy(archivedOnly = archivedOnlyValue)
    }.combine(includeArchived) { filters, archived ->
        filters.copy(includeArchived = if (filters.archivedOnly) false else archived)
    }

    val uiState: StateFlow<SearchUiState> = combine(
        filtersFlow.flatMapLatest { filters ->
            noteRepository.observeSearchResults(filters).map { results ->
                SearchUiState(filters = filters, results = results)
            }
        },
        noteRepository.observeAllNotes(),
        selectedAction,
    ) { state, allNotes, action ->
        Triple(state, allNotes, action)
    }.combine(backgroundFolderOrganizer.status) { (state, allNotes, action), organizerStatus ->
        val activeNotes = allNotes.filter { !it.isArchived }
        val tags = allNotes
            .sortedByDescending { it.updatedAt }
            .flatMap { it.tags }
            .distinct()
        val candidateThreads = NoteConnectionAnalyzer.buildThemeThreads(activeNotes, limit = 4)
        val focusNotes = focusedNotes(
            filters = state.filters,
            activeNotes = activeNotes,
            searchResults = state.results,
        )
        state.copy(
            availableTags = tags,
            folderCounts = MindFolderCatalog.all.associate { folder ->
                folder.key to activeNotes.count { note ->
                    MindFolderCatalog.normalizedKey(note.folderKey) == folder.key
                }
            },
            uncategorizedCount = activeNotes.count {
                MindFolderCatalog.normalizedKey(it.folderKey) == null
            },
            pendingFolderClassificationCount = activeNotes.count {
                MindFolderCatalog.normalizedKey(it.folderKey) == null && it.folderSource != FolderSource.MANUAL
            },
            lastAutoOrganizedAt = organizerStatus.lastOrganizedAt,
            lastAutoOrganizedCount = organizerStatus.lastOrganizedCount,
            selectedAction = action,
            suggestions = buildQuerySuggestions(
                action = action,
                focusNotes = focusNotes,
                activeNotes = activeNotes,
                threads = candidateThreads,
            ),
            focusLine = buildFocusLine(
                filters = state.filters,
                focusNotes = focusNotes,
                activeNotes = activeNotes,
                threads = candidateThreads,
            ),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    fun updateQuery(value: String) {
        query.value = value
    }

    fun updateTag(tag: String?) {
        selectedTag.value = tag
    }

    fun updateFolder(folderKey: String?) {
        selectedFolder.value = folderKey
    }

    fun updateStatus(status: NoteStatus?) {
        selectedStatus.value = status
    }

    fun updateTimeRange(timeRange: TimeRange) {
        selectedTimeRange.value = timeRange
    }

    fun selectAction(action: QueryAction) {
        selectedAction.value = action
    }

    fun toggleArchived() {
        if (archivedOnly.value) {
            archivedOnly.value = false
            includeArchived.value = false
        } else {
            includeArchived.update { !it }
        }
    }

    fun deleteNote(noteId: Long) {
        viewModelScope.launch {
            noteRepository.deleteNote(noteId)
            _events.emit(SearchEvent.Message("已删除记录"))
        }
    }

    fun classifyPendingFolders() {
        viewModelScope.launch {
            val classifiedCount = backgroundFolderOrganizer.organizeNow()
            val message = if (classifiedCount > 0) {
                "已自动归类 $classifiedCount 条记录"
            } else {
                "没有可整理的未分类记录"
            }
            _events.emit(SearchEvent.Message(message))
        }
    }

    private fun focusedNotes(
        filters: SearchFilters,
        activeNotes: List<NoteEntity>,
        searchResults: List<NoteEntity>,
    ): List<NoteEntity> {
        val hasExplicitFilters = filters.query.isNotBlank() ||
            filters.tag != null ||
            filters.folderKey != null ||
            filters.status != null ||
            filters.timeRange != TimeRange.ALL ||
            filters.includeArchived ||
            filters.archivedOnly
        return if (hasExplicitFilters) {
            searchResults.sortedByDescending { it.updatedAt }.take(12)
        } else {
            activeNotes.sortedByDescending { it.updatedAt }.take(12)
        }
    }

    private fun buildFocusLine(
        filters: SearchFilters,
        focusNotes: List<NoteEntity>,
        activeNotes: List<NoteEntity>,
        threads: List<ThemeThread>,
    ): String {
        val hasExplicitFilters = filters.query.isNotBlank() ||
            filters.tag != null ||
            filters.folderKey != null ||
            filters.status != null ||
            filters.timeRange != TimeRange.ALL ||
            filters.includeArchived ||
            filters.archivedOnly
        return if (hasExplicitFilters) {
            "当前筛到 ${focusNotes.size} 条记录，可直接拿来继续养、反驳或补证据。"
        } else {
            "当前从 ${activeNotes.size} 条活跃记录里挑对象，优先关注 ${threads.size} 条已经成线的主题。"
        }
    }

    private fun buildQuerySuggestions(
        action: QueryAction,
        focusNotes: List<NoteEntity>,
        activeNotes: List<NoteEntity>,
        threads: List<ThemeThread>,
    ): List<QuerySuggestion> =
        when (action) {
            QueryAction.NURTURE -> nurtureSuggestions(focusNotes, threads)
            QueryAction.COLLIDE -> collisionSuggestions(focusNotes, threads)
            QueryAction.CHALLENGE -> challengeSuggestions(focusNotes, threads)
            QueryAction.ABSTRACT -> abstractSuggestions(focusNotes, threads)
            QueryAction.PLAN -> planSuggestions(focusNotes, threads)
            QueryAction.EVIDENCE -> evidenceSuggestions(focusNotes, activeNotes, threads)
        }
            .distinctBy { it.id }
            .take(3)

    private fun nurtureSuggestions(
        focusNotes: List<NoteEntity>,
        threads: List<ThemeThread>,
    ): List<QuerySuggestion> {
        val leadingNote = focusNotes.firstOrNull { it.status == NoteStatus.IN_PROGRESS }
            ?: focusNotes.firstOrNull()
        val backupNote = focusNotes.firstOrNull { it.id != leadingNote?.id && it.status == NoteStatus.IDEA }
        val thread = threads.firstOrNull()
        return buildList {
            leadingNote?.let { note ->
                add(
                    noteSuggestion(
                        action = QueryAction.NURTURE,
                        note = note,
                        support = "这条记录已经有推进势能，最适合继续喂养。",
                        detail = note.content.compactPreview(88),
                        topicSuffix = "继续养",
                        contentBuilder = {
                            appendLine("继续养大这条想法：${note.topic.ifBlank { "未命名想法" }}")
                            appendLine("- 当前记录：${note.content.compactPreview(160)}")
                            appendLine("- 现在最值得往前推的一步：")
                            appendLine("- 这一步为什么重要：")
                            appendLine("- 做完之后要看什么反馈：")
                        },
                    ),
                )
            }
            thread?.let {
                add(
                    threadSuggestion(
                        action = QueryAction.NURTURE,
                        thread = it,
                        support = "这条线已经反复出现，值得当成长期主题继续养。",
                        detail = it.summary,
                        topicSuffix = "继续养",
                        contentBuilder = {
                            appendLine("继续养这条主题线：${it.title}")
                            appendLine("- 现有线索：${it.summary}")
                            it.focusLine.takeIf(String::isNotBlank)?.let { focusLine ->
                                appendLine("- 当前重心：$focusLine")
                            }
                            appendLine("- 接下来最值得补的一条材料：")
                            appendLine("- 这条线下一步该往哪个判断或实验推进：")
                        },
                    ),
                )
            }
            backupNote?.let { note ->
                add(
                    noteSuggestion(
                        action = QueryAction.NURTURE,
                        note = note,
                        support = "这颗火花还没沉下去，补一条推进记录很划算。",
                        detail = note.content.compactPreview(88),
                        topicSuffix = "补推进",
                        contentBuilder = {
                            appendLine("围绕这颗火花再补一条推进记录：${note.topic.ifBlank { "未命名想法" }}")
                            appendLine("- 当前内容：${note.content.compactPreview(150)}")
                            appendLine("- 我想把它往哪个方向推：")
                            appendLine("- 缺的判断或材料：")
                        },
                    ),
                )
            }
        }
    }

    private fun collisionSuggestions(
        focusNotes: List<NoteEntity>,
        threads: List<ThemeThread>,
    ): List<QuerySuggestion> {
        val pair = pickCollisionPair(focusNotes)
        val threadPair = threads.take(2).takeIf { it.size == 2 }
        return buildList {
            pair?.let { (left, right) ->
                add(
                    QuerySuggestion(
                        id = "collision:note:${left.id}:${right.id}",
                        title = "「${left.topic.ifBlank { "未命名记录" }}」 × 「${right.topic.ifBlank { "未命名记录" }}」",
                        support = "这两个点还没被放进同一个问题里，值得撞一下。",
                        detail = "${left.content.compactPreview(60)} · ${right.content.compactPreview(60)}",
                        actionLabel = QueryAction.COLLIDE.startLabel,
                        noteId = left.id,
                        captureTopic = "${left.topic.ifBlank { "想法 A" }} × ${right.topic.ifBlank { "想法 B" }}",
                        captureContent = buildString {
                            appendLine("把这两个点放到同一页里试着撞一下：")
                            appendLine("- 点 A：${left.topic.ifBlank { "未命名记录" }}")
                            appendLine("- 内容 A：${left.content.compactPreview(120)}")
                            appendLine("- 点 B：${right.topic.ifBlank { "未命名记录" }}")
                            appendLine("- 内容 B：${right.content.compactPreview(120)}")
                            appendLine("- 这两个点可能共享的底层问题：")
                            appendLine("- 如果把它们连起来，会长出什么新方案或新实验：")
                            appendLine("- 最小碰撞动作：")
                        },
                    ),
                )
            }
            if (threadPair != null) {
                val left = threadPair[0]
                val right = threadPair[1]
                add(
                    QuerySuggestion(
                        id = "collision:thread:${left.key}:${right.key}",
                        title = "${left.title} × ${right.title}",
                        support = "这两条主题都已经成线，跨线碰撞更容易长出新东西。",
                        detail = "${left.summary} · ${right.summary}",
                        actionLabel = QueryAction.COLLIDE.startLabel,
                        threadKey = left.key,
                        captureTopic = "${left.title} × ${right.title}",
                        captureContent = buildString {
                            appendLine("让两条主题发生一次碰撞：")
                            appendLine("- 主题 A：${left.title}")
                            appendLine("- 线索 A：${left.summary}")
                            appendLine("- 主题 B：${right.title}")
                            appendLine("- 线索 B：${right.summary}")
                            appendLine("- 它们之间最值得试的连接：")
                            appendLine("- 这次碰撞如果成立，会带来什么新判断：")
                        },
                    ),
                )
            }
        }
    }

    private fun challengeSuggestions(
        focusNotes: List<NoteEntity>,
        threads: List<ThemeThread>,
    ): List<QuerySuggestion> {
        val target = focusNotes
            .sortedWith(
                compareByDescending<NoteEntity> { it.knowledgeTrust == KnowledgeTrust.VALIDATED || it.knowledgeTrust == KnowledgeTrust.VERIFIED }
                    .thenByDescending { it.status == NoteStatus.DONE }
                    .thenByDescending { it.updatedAt },
            )
            .firstOrNull()
        val thread = threads.firstOrNull()
        return buildList {
            target?.let { note ->
                add(
                    noteSuggestion(
                        action = QueryAction.CHALLENGE,
                        note = note,
                        support = "这条记录最像一个判断，适合主动反驳。",
                        detail = note.content.compactPreview(88),
                        topicSuffix = "反驳它",
                        contentBuilder = {
                            appendLine("挑战这个判断：${note.topic.ifBlank { "未命名判断" }}")
                            appendLine("- 当前说法：${note.content.compactPreview(160)}")
                            appendLine("- 最强反例是什么：")
                            appendLine("- 哪个前提最脆弱：")
                            appendLine("- 什么证据会直接推翻它：")
                        },
                        knowledgeTrust = KnowledgeTrust.HYPOTHESIS,
                    ),
                )
            }
            thread?.let {
                add(
                    threadSuggestion(
                        action = QueryAction.CHALLENGE,
                        thread = it,
                        support = "这条主题已经有重复判断，值得逆向拆一次。",
                        detail = it.summary,
                        topicSuffix = "反驳它",
                        contentBuilder = {
                            appendLine("反过来挑战这条主题：${it.title}")
                            appendLine("- 现有线索：${it.summary}")
                            appendLine("- 这条线最可能错在哪里：")
                            appendLine("- 如果要否定它，需要补哪条证据：")
                        },
                        knowledgeTrust = KnowledgeTrust.HYPOTHESIS,
                    ),
                )
            }
        }
    }

    private fun abstractSuggestions(
        focusNotes: List<NoteEntity>,
        threads: List<ThemeThread>,
    ): List<QuerySuggestion> =
        buildList {
            threads.firstOrNull()?.let {
                add(
                    threadSuggestion(
                        action = QueryAction.ABSTRACT,
                        thread = it,
                        support = "这条主题已经积累了重复样本，适合抽成结构。",
                        detail = it.summary,
                        topicSuffix = "抽成结构",
                        contentBuilder = {
                            appendLine("把这条主题抽成结构：${it.title}")
                            appendLine("- 现有线索：${it.summary}")
                            appendLine("- 反复出现的共同模式：")
                            appendLine("- 能不能抽成一个概念、方法或框架：")
                            appendLine("- 之后怎样复用：")
                        },
                    ),
                )
            }
            focusNotes.firstOrNull()?.let { note ->
                add(
                    noteSuggestion(
                        action = QueryAction.ABSTRACT,
                        note = note,
                        support = "先从这条记录里抽出抽象层，再回看相似记录。",
                        detail = note.content.compactPreview(88),
                        topicSuffix = "抽象",
                        contentBuilder = {
                            appendLine("把这条记录往上抽一层：${note.topic.ifBlank { "未命名记录" }}")
                            appendLine("- 当前内容：${note.content.compactPreview(160)}")
                            appendLine("- 这背后的更高层模式是什么：")
                            appendLine("- 还有哪些记录可能属于同一模式：")
                        },
                    ),
                )
            }
        }

    private fun planSuggestions(
        focusNotes: List<NoteEntity>,
        threads: List<ThemeThread>,
    ): List<QuerySuggestion> {
        val target = focusNotes.firstOrNull { it.status == NoteStatus.IN_PROGRESS }
            ?: focusNotes.firstOrNull()
        val thread = threads.firstOrNull()
        return buildList {
            target?.let { note ->
                add(
                    noteSuggestion(
                        action = QueryAction.PLAN,
                        note = note,
                        support = "这条记录已经足够具体，适合拉成可执行方案。",
                        detail = note.content.compactPreview(88),
                        topicSuffix = "方案草稿",
                        contentBuilder = {
                            appendLine("把这个点拉成方案：${note.topic.ifBlank { "未命名想法" }}")
                            appendLine("- 当前内容：${note.content.compactPreview(160)}")
                            appendLine("- 目标：")
                            appendLine("- 3 个关键步骤：")
                            appendLine("- 最大风险：")
                            appendLine("- 如何验证方案有效：")
                        },
                    ),
                )
            }
            thread?.let {
                add(
                    threadSuggestion(
                        action = QueryAction.PLAN,
                        thread = it,
                        support = "这条线已经不是单点了，可以拉成阶段性方案。",
                        detail = it.summary,
                        topicSuffix = "阶段方案",
                        contentBuilder = {
                            appendLine("围绕这条主题拉一个阶段方案：${it.title}")
                            appendLine("- 当前线索：${it.summary}")
                            appendLine("- 这个阶段想解决什么：")
                            appendLine("- 接下来 1 周内的关键动作：")
                            appendLine("- 方案成功的标记：")
                        },
                    ),
                )
            }
        }
    }

    private fun evidenceSuggestions(
        focusNotes: List<NoteEntity>,
        activeNotes: List<NoteEntity>,
        threads: List<ThemeThread>,
    ): List<QuerySuggestion> {
        val target = focusNotes.firstOrNull {
            it.knowledgeTrust == KnowledgeTrust.NONE || it.knowledgeTrust == KnowledgeTrust.HYPOTHESIS
        } ?: activeNotes.firstOrNull { it.status != NoteStatus.DONE }
        val thread = threads.firstOrNull()
        return buildList {
            target?.let { note ->
                add(
                    noteSuggestion(
                        action = QueryAction.EVIDENCE,
                        note = note,
                        support = "这条记录还缺证据支撑，补证据最有价值。",
                        detail = note.content.compactPreview(88),
                        topicSuffix = "补证据",
                        contentBuilder = {
                            appendLine("给这个判断补证据：${note.topic.ifBlank { "未命名判断" }}")
                            appendLine("- 当前内容：${note.content.compactPreview(160)}")
                            appendLine("- 现在缺的是哪类证据：")
                            appendLine("- 去哪里找：")
                            appendLine("- 补完之后，判断会怎么变化：")
                        },
                        knowledgeTrust = KnowledgeTrust.SIGNAL,
                    ),
                )
            }
            thread?.let {
                add(
                    threadSuggestion(
                        action = QueryAction.EVIDENCE,
                        thread = it,
                        support = "这条主题已经成线，补一条关键证据会明显改变判断质量。",
                        detail = it.summary,
                        topicSuffix = "补证据",
                        contentBuilder = {
                            appendLine("给这条主题补一条证据链：${it.title}")
                            appendLine("- 现有线索：${it.summary}")
                            appendLine("- 最缺的一条证据：")
                            appendLine("- 证据来源：")
                            appendLine("- 补完之后最可能改变哪条判断：")
                        },
                        knowledgeTrust = KnowledgeTrust.SIGNAL,
                    ),
                )
            }
        }
    }

    private fun noteSuggestion(
        action: QueryAction,
        note: NoteEntity,
        support: String,
        detail: String,
        topicSuffix: String,
        knowledgeTrust: KnowledgeTrust = KnowledgeTrust.NONE,
        contentBuilder: StringBuilder.() -> Unit,
    ): QuerySuggestion =
        QuerySuggestion(
            id = "note:${action.name}:${note.id}",
            title = note.topic.ifBlank { "未命名记录" },
            support = support,
            detail = detail,
            actionLabel = action.startLabel,
            noteId = note.id,
            captureTopic = "${note.topic.ifBlank { "未命名记录" }} · $topicSuffix",
            captureContent = buildString(contentBuilder),
            captureFolderKey = note.folderKey,
            captureTags = note.tags.take(3),
            captureKnowledgeTrust = knowledgeTrust,
        )

    private fun threadSuggestion(
        action: QueryAction,
        thread: ThemeThread,
        support: String,
        detail: String,
        topicSuffix: String,
        knowledgeTrust: KnowledgeTrust = KnowledgeTrust.NONE,
        contentBuilder: StringBuilder.() -> Unit,
    ): QuerySuggestion {
        val initialFolderKey = thread.key
            .takeIf { it.startsWith("folder:") }
            ?.removePrefix("folder:")
            ?.trim()
            ?.ifBlank { null }
        val initialTags = thread.key
            .takeIf { it.startsWith("tag:") }
            ?.removePrefix("tag:")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let(::listOf)
            .orEmpty()
        return QuerySuggestion(
            id = "thread:${action.name}:${thread.key}",
            title = thread.title,
            support = support,
            detail = detail,
            actionLabel = action.startLabel,
            threadKey = thread.key,
            captureTopic = "${thread.title} · $topicSuffix",
            captureContent = buildString(contentBuilder),
            captureFolderKey = initialFolderKey,
            captureTags = initialTags,
            captureKnowledgeTrust = knowledgeTrust,
        )
    }

    private fun pickCollisionPair(notes: List<NoteEntity>): Pair<NoteEntity, NoteEntity>? {
        val ordered = notes.sortedByDescending { it.updatedAt }
        ordered.forEachIndexed { index, left ->
            val right = ordered.drop(index + 1).firstOrNull { candidate ->
                MindFolderCatalog.normalizedKey(candidate.folderKey) != MindFolderCatalog.normalizedKey(left.folderKey) ||
                    candidate.tags.intersect(left.tags.toSet()).isNotEmpty()
            }
            if (right != null) {
                return left to right
            }
        }
        return ordered.take(2).takeIf { it.size == 2 }?.let { it[0] to it[1] }
    }

    companion object {
        fun factory(
            noteRepository: NoteRepository,
            backgroundFolderOrganizer: BackgroundFolderOrganizer,
            initialStatus: NoteStatus? = null,
            initialArchivedOnly: Boolean = false,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SearchViewModel(
                    noteRepository = noteRepository,
                    backgroundFolderOrganizer = backgroundFolderOrganizer,
                    initialStatus = initialStatus,
                    initialArchivedOnly = initialArchivedOnly,
                )
            }
        }
    }
}

private fun String.compactPreview(limit: Int): String {
    val compact = replace("\n", " ").trim()
    return if (compact.length <= limit) compact else compact.take(limit).trimEnd() + "…"
}
