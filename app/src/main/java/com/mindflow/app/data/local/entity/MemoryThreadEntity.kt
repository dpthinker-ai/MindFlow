package com.mindflow.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

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
