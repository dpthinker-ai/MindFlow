package com.mindflow.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mindflow.app.data.local.entity.MemoryDigestEntity
import com.mindflow.app.data.local.entity.MemoryFragmentEntity
import com.mindflow.app.data.local.entity.MemoryThreadEntity

@Dao
interface MemoryLayerDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFragment(entity: MemoryFragmentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFragments(entities: List<MemoryFragmentEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertThread(entity: MemoryThreadEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDigest(entity: MemoryDigestEntity)

    @Query("SELECT * FROM memory_fragments WHERE id = :id LIMIT 1")
    suspend fun loadFragment(id: String): MemoryFragmentEntity?

    @Query("SELECT * FROM memory_threads WHERE id = :id LIMIT 1")
    suspend fun loadThread(id: String): MemoryThreadEntity?

    @Query("SELECT * FROM memory_digests WHERE scopeType = :scopeType AND scopeKey = :scopeKey LIMIT 1")
    suspend fun loadDigest(scopeType: String, scopeKey: String): MemoryDigestEntity?

    @Query("SELECT * FROM memory_threads ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun loadLatestThreads(limit: Int): List<MemoryThreadEntity>

    @Query("SELECT * FROM memory_fragments")
    suspend fun loadAllFragments(): List<MemoryFragmentEntity>

    suspend fun loadFragmentsByNoteIds(noteIds: List<Long>): List<MemoryFragmentEntity> =
        if (noteIds.isEmpty()) {
            emptyList()
        } else {
            loadAllFragments().filter { entity -> entity.sourceNoteIds.any(noteIds::contains) }
        }

    @Query("DELETE FROM memory_fragments")
    suspend fun clearFragments()

    @Query("DELETE FROM memory_threads")
    suspend fun clearThreads()

    @Query("DELETE FROM memory_digests")
    suspend fun clearDigests()
}
