package com.mindflow.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

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
