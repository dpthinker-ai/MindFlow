package com.mindflow.app.data.backup

interface CloudBackupIndexRepository {
    suspend fun getCurrent(): CloudBackupIndex
    suspend fun save(index: CloudBackupIndex)
    suspend fun clear()
}
