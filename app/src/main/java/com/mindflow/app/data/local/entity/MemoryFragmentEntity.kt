package com.mindflow.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

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
