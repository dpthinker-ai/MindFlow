package com.mindflow.app.data.skills

import com.google.common.truth.Truth.assertThat
import com.mindflow.app.data.local.entity.NoteEntity
import com.mindflow.app.data.model.FolderSource
import com.mindflow.app.data.model.KnowledgeTrust
import com.mindflow.app.data.model.NoteHorizon
import com.mindflow.app.data.model.NoteStatus
import com.mindflow.app.data.model.TagSource
import com.mindflow.app.data.model.TopicSource
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DefaultNativeToolBridgeTest {
    @Test
    fun historyCount_returnsMatchedCoverageForEntityTerms() = runTest {
        val bridge = DefaultNativeToolBridge(
            loadAllNotes = {
                listOf(
                    note(
                        id = 1L,
                        topic = "人生建议",
                        content = "人生是多线程运行",
                        date = LocalDate.of(2026, 4, 5),
                    ),
                    note(
                        id = 2L,
                        topic = "技术实现",
                        content = "MindFlow 接入 OpenCL",
                        date = LocalDate.of(2026, 4, 8),
                    ),
                )
            },
            zoneId = ZoneId.systemDefault(),
        )

        val raw = bridge.invoke(
            apiName = "history.count",
            payloadJson = """
                {
                  "timeScope": {"type": "all_time"},
                  "entityTerms": ["人生建议"]
                }
            """.trimIndent(),
        )

        val result = SkillMiniJsonParser(raw).parseObject()
        val coverage = result.objectValue("coverage")
        assertThat(coverage.numberValue("totalCount")?.toInt()).isEqualTo(2)
        assertThat(coverage.numberValue("matchedCount")?.toInt()).isEqualTo(1)
        assertThat(coverage.booleanValue("complete")).isTrue()
    }

    @Test
    fun historyQuery_paginatesAscendingByCreatedAt() = runTest {
        val bridge = DefaultNativeToolBridge(
            loadAllNotes = {
                listOf(
                    note(
                        id = 1L,
                        topic = "A",
                        content = "alpha",
                        date = LocalDate.of(2026, 4, 1),
                        folderKey = "work",
                        tags = listOf("AI", "项目"),
                    ),
                    note(id = 2L, topic = "B", content = "beta", date = LocalDate.of(2026, 4, 2)),
                    note(id = 3L, topic = "C", content = "gamma", date = LocalDate.of(2026, 4, 3)),
                )
            },
            zoneId = ZoneId.systemDefault(),
        )

        val raw = bridge.invoke(
            apiName = "history.query",
            payloadJson = """
                {
                  "timeScope": {"type": "all_time"},
                  "entityTerms": [],
                  "pageSize": 2,
                  "cursor": null,
                  "includeContent": false,
                  "sort": "created_at_asc"
                }
            """.trimIndent(),
        )

        val result = SkillMiniJsonParser(raw).parseObject()
        val coverage = result.objectValue("coverage")
        val records = result.arrayValue("records")
        assertThat(coverage.numberValue("matchedCount")?.toInt()).isEqualTo(3)
        assertThat(coverage.numberValue("processedCount")?.toInt()).isEqualTo(2)
        assertThat(coverage.stringValue("nextCursor")).isEqualTo("2")
        assertThat(records).hasSize(2)
        assertThat(records[0].objectValues().stringValue("id")).isEqualTo("1")
        assertThat(records[0].objectValues().stringValue("folder")).isEqualTo("work")
        assertThat(records[0].objectValues().arrayValue("tags").mapNotNull { (it as? SkillJsonValue.JsonString)?.value })
            .containsExactly("AI", "项目")
        assertThat(records[1].objectValues().stringValue("id")).isEqualTo("2")
    }

    @Test
    fun historyQuery_canUsePlannerPinnedNoteIdsInsteadOfExactEntityTerms() = runTest {
        val bridge = DefaultNativeToolBridge(
            loadAllNotes = {
                listOf(
                    note(id = 1L, topic = "人生是多线程运行", content = "守住边界", date = LocalDate.of(2026, 4, 1)),
                    note(id = 2L, topic = "社交边界与自我保护原则", content = "不要损耗自己的精力", date = LocalDate.of(2026, 4, 2)),
                    note(id = 3L, topic = "前端输入框问题", content = "与人生经验无关", date = LocalDate.of(2026, 4, 3)),
                )
            },
            zoneId = ZoneId.systemDefault(),
        )

        val raw = bridge.invoke(
            apiName = "history.query",
            payloadJson = """
                {
                  "timeScope": {"type": "all_time"},
                  "entityTerms": ["人生经验"],
                  "noteIds": ["1", "2"],
                  "pageSize": 10,
                  "cursor": null,
                  "includeContent": false,
                  "sort": "created_at_asc"
                }
            """.trimIndent(),
        )

        val result = SkillMiniJsonParser(raw).parseObject()
        val coverage = result.objectValue("coverage")
        val records = result.arrayValue("records")
        assertThat(coverage.numberValue("matchedCount")?.toInt()).isEqualTo(2)
        assertThat(records.map { it.objectValues().stringValue("id") }).containsExactly("1", "2").inOrder()
    }

    @Test
    fun historyCount_totalCountReflectsScopedNotes() = runTest {
        val bridge = DefaultNativeToolBridge(
            loadAllNotes = {
                listOf(
                    note(id = 1L, topic = "三月主题A", content = "alpha", date = LocalDate.of(2026, 3, 1)),
                    note(id = 2L, topic = "三月主题B", content = "beta", date = LocalDate.of(2026, 3, 20)),
                    note(id = 3L, topic = "四月主题", content = "gamma", date = LocalDate.of(2026, 4, 3)),
                )
            },
            zoneId = ZoneId.systemDefault(),
        )

        val raw = bridge.invoke(
            apiName = "history.count",
            payloadJson = """
                {
                  "timeScope": {"type": "month", "month": "2026-03"},
                  "entityTerms": []
                }
            """.trimIndent(),
        )

        val result = SkillMiniJsonParser(raw).parseObject()
        val coverage = result.objectValue("coverage")
        assertThat(coverage.numberValue("totalCount")?.toInt()).isEqualTo(2)
        assertThat(coverage.numberValue("matchedCount")?.toInt()).isEqualTo(2)
    }

    private fun note(
        id: Long,
        topic: String,
        content: String,
        date: LocalDate,
        folderKey: String? = null,
        tags: List<String> = emptyList(),
    ): NoteEntity {
        val timestamp = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return NoteEntity(
            id = id,
            content = content,
            topic = topic,
            topicSource = TopicSource.RULE,
            folderKey = folderKey,
            folderSource = FolderSource.RULE,
            tags = tags,
            tagSource = TagSource.RULE,
            status = NoteStatus.IDEA,
            horizon = NoteHorizon.MEDIUM,
            knowledgeTrust = KnowledgeTrust.NONE,
            isArchived = false,
            createdAt = timestamp,
            updatedAt = timestamp,
        )
    }
}

private fun Map<String, SkillJsonValue>.arrayValue(key: String): List<SkillJsonValue> =
    (this[key] as? SkillJsonValue.JsonArray)?.items ?: emptyList()

private fun SkillJsonValue.objectValues(): Map<String, SkillJsonValue> =
    (this as? SkillJsonValue.JsonObject)?.values ?: emptyMap()

private fun Map<String, SkillJsonValue>.stringValue(key: String): String? =
    (this[key] as? SkillJsonValue.JsonString)?.value

private fun Map<String, SkillJsonValue>.booleanValue(key: String): Boolean? =
    (this[key] as? SkillJsonValue.JsonBoolean)?.value

private fun Map<String, SkillJsonValue>.numberValue(key: String): Double? =
    (this[key] as? SkillJsonValue.JsonNumber)?.value

private fun Map<String, SkillJsonValue>.objectValue(key: String): Map<String, SkillJsonValue> =
    (this[key] as? SkillJsonValue.JsonObject)?.values ?: emptyMap()
