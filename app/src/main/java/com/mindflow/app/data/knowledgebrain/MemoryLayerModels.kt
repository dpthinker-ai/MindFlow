package com.mindflow.app.data.knowledgebrain

import com.mindflow.app.data.local.entity.MemoryDigestEntity
import com.mindflow.app.data.local.entity.MemoryFragmentEntity
import com.mindflow.app.data.local.entity.MemoryThreadEntity

enum class MemoryThreadType {
    TOPIC,
    QUESTION,
    DIRECTION,
}

enum class MemoryDigestScopeType {
    DAY,
    WEEK,
    TOPIC,
    QUESTION,
}

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

data class MemoryThread(
    val id: String,
    val title: String,
    val type: MemoryThreadType,
    val fragmentIds: List<String>,
    val summary: String,
    val currentState: String,
    val openQuestions: List<String>,
    val updatedAt: Long,
)

data class MemoryDigest(
    val id: String,
    val scopeType: MemoryDigestScopeType,
    val scopeKey: String,
    val summary: String,
    val highlights: List<String>,
    val sourceFragmentIds: List<String>,
    val updatedAt: Long,
)

fun MemoryFragment.toEntity(): MemoryFragmentEntity = MemoryFragmentEntity(
    id = id,
    sourceNoteIds = sourceNoteIds,
    topicKey = topicKey,
    questionKey = questionKey,
    summary = summary,
    salience = salience,
    timeSpanStart = timeSpanStart,
    timeSpanEnd = timeSpanEnd,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun MemoryFragmentEntity.toModel(): MemoryFragment = MemoryFragment(
    id = id,
    sourceNoteIds = sourceNoteIds,
    topicKey = topicKey,
    questionKey = questionKey,
    summary = summary,
    salience = salience,
    timeSpanStart = timeSpanStart,
    timeSpanEnd = timeSpanEnd,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun MemoryThread.toEntity(): MemoryThreadEntity = MemoryThreadEntity(
    id = id,
    title = title,
    type = type.name,
    fragmentIds = fragmentIds,
    summary = summary,
    currentState = currentState,
    openQuestions = openQuestions,
    updatedAt = updatedAt,
)

fun MemoryThreadEntity.toModel(): MemoryThread = MemoryThread(
    id = id,
    title = title,
    type = MemoryThreadType.valueOf(type),
    fragmentIds = fragmentIds,
    summary = summary,
    currentState = currentState,
    openQuestions = openQuestions,
    updatedAt = updatedAt,
)

fun MemoryDigest.toEntity(): MemoryDigestEntity = MemoryDigestEntity(
    id = id,
    scopeType = scopeType.name,
    scopeKey = scopeKey,
    summary = summary,
    highlights = highlights,
    sourceFragmentIds = sourceFragmentIds,
    updatedAt = updatedAt,
)

fun MemoryDigestEntity.toModel(): MemoryDigest = MemoryDigest(
    id = id,
    scopeType = MemoryDigestScopeType.valueOf(scopeType),
    scopeKey = scopeKey,
    summary = summary,
    highlights = highlights,
    sourceFragmentIds = sourceFragmentIds,
    updatedAt = updatedAt,
)
