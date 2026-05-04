package com.mindflow.app.ui.screens.feed

import com.google.common.truth.Truth.assertThat
import com.mindflow.app.data.local.entity.NoteEntity
import com.mindflow.app.data.model.FolderSource
import com.mindflow.app.data.model.KnowledgeTrust
import com.mindflow.app.data.model.NoteHorizon
import com.mindflow.app.data.model.NoteStatus
import com.mindflow.app.data.model.TagSource
import com.mindflow.app.data.model.TopicSource
import com.mindflow.app.ui.navigation.CaptureMode
import org.junit.Test

class FeedRecordFiltersTest {
    @Test
    fun matchesFeedSearch_checksTopicContentAndTags() {
        val note = sampleNote(
            topic = "MindFlow MVP",
            content = "把记录页改成快速输入入口",
            tags = listOf("设计", "Android"),
        )

        assertThat(note.matchesFeedSearch("mindflow")).isTrue()
        assertThat(note.matchesFeedSearch("快速输入")).isTrue()
        assertThat(note.matchesFeedSearch("android")).isTrue()
        assertThat(note.matchesFeedSearch("不存在")).isFalse()
    }

    @Test
    fun matchesFeedSearch_emptyQueryAlwaysMatches() {
        assertThat(sampleNote().matchesFeedSearch("")).isTrue()
        assertThat(sampleNote().matchesFeedSearch("   ")).isTrue()
    }

    @Test
    fun matchesFeedQuickFilter_classifiesIdeasTasksArticlesAndVoice() {
        val idea = sampleNote(status = NoteStatus.IDEA, content = "一个产品想法")
        val task = sampleNote(status = NoteStatus.IN_PROGRESS, content = "今天推进这个任务")
        val doneTask = sampleNote(status = NoteStatus.DONE, content = "已经完成")
        val link = sampleNote(content = "收藏 https://example.com/article")
        val voice = sampleNote(content = "语音转写：先记录这段想法")

        assertThat(idea.matchesFeedQuickFilter(FeedQuickFilter.IDEA)).isTrue()
        assertThat(task.matchesFeedQuickFilter(FeedQuickFilter.TASK)).isTrue()
        assertThat(doneTask.matchesFeedQuickFilter(FeedQuickFilter.TASK)).isTrue()
        assertThat(link.matchesFeedQuickFilter(FeedQuickFilter.LINK)).isTrue()
        assertThat(voice.matchesFeedQuickFilter(FeedQuickFilter.VOICE)).isTrue()
        assertThat(link.matchesFeedQuickFilter(FeedQuickFilter.ALL)).isTrue()
    }

    @Test
    fun feedStartsInLoadingStateUntilRepositoryEmits() {
        assertThat(FeedUiState().isLoading).isTrue()
        assertThat(FeedUiState(isLoading = false).isLoading).isFalse()
    }

    @Test
    fun quickCaptureActionsCreateModeSpecificSeeds() {
        val text = FeedCaptureAction.TEXT.toCaptureSeed()
        assertThat(text.mode).isEqualTo(CaptureMode.TEXT)
        assertThat(text.initialTopic).isEmpty()

        val voice = FeedCaptureAction.VOICE.toCaptureSeed()
        assertThat(voice.mode).isEqualTo(CaptureMode.VOICE)
        assertThat(voice.autoStartVoiceInput).isFalse()
        assertThat(voice.initialTags).contains("语音")

        val image = FeedCaptureAction.IMAGE.toCaptureSeed()
        assertThat(image.mode).isEqualTo(CaptureMode.IMAGE)
        assertThat(image.initialTopic).isEqualTo("图片记录")
        assertThat(image.initialTags).contains("图片")

        val link = FeedCaptureAction.LINK.toCaptureSeed()
        assertThat(link.mode).isEqualTo(CaptureMode.ARTICLE)
        assertThat(link.initialTopic).isEqualTo("文章收藏")
        assertThat(link.initialContent).contains("链接：")
        assertThat(link.initialTags).contains("文章")
    }

    @Test
    fun filterFeedNotes_appliesSearchAndQuickFilterTogether() {
        val notes = listOf(
            sampleNote(id = 1L, topic = "链接 A", content = "https://example.com/a", tags = listOf("阅读")),
            sampleNote(id = 2L, topic = "任务 B", status = NoteStatus.IN_PROGRESS, content = "推进 UI"),
            sampleNote(id = 3L, topic = "想法 C", status = NoteStatus.IDEA, content = "推进设计"),
        )

        val result = filterFeedNotes(
            notes = notes,
            query = "推进",
            filter = FeedQuickFilter.TASK,
        )

        assertThat(result.map { it.id }).containsExactly(2L)
    }

    @Test
    fun recordHomeLayout_usesCompactTimeBankSearchAndCaptureSizing() {
        assertThat(RecordTimeBankBadgeVerticalPadding.value).isAtMost(6f)
        assertThat(RecordSearchMinHeight.value).isEqualTo(48f)
        assertThat(RecordQuickCaptureVerticalPadding.value).isAtMost(10f)
        assertThat(RecordFilterChipVerticalPadding.value).isAtMost(6f)
    }

    @Test
    fun recordHomeLayout_keepsSearchReadableWithoutUnusedTrailingButton() {
        assertThat(RecordSearchUsesStandaloneFilterButton).isFalse()
        assertThat(RecordSearchPlaceholder).isEqualTo("搜索记录、标签、链接、任务、语音")
        assertThat(RecordSearchUsesOutlinedTextField).isFalse()
    }

    private fun sampleNote(
        id: Long = 1L,
        topic: String = "未命名想法",
        content: String = "正文",
        tags: List<String> = emptyList(),
        status: NoteStatus = NoteStatus.IDEA,
    ): NoteEntity = NoteEntity(
        id = id,
        content = content,
        topic = topic,
        topicSource = TopicSource.MANUAL,
        folderKey = null,
        folderSource = FolderSource.MANUAL,
        tags = tags,
        tagSource = TagSource.MANUAL,
        status = status,
        horizon = NoteHorizon.SHORT,
        knowledgeTrust = KnowledgeTrust.NONE,
        isArchived = false,
        createdAt = 1_000L,
        updatedAt = 2_000L,
    )
}
