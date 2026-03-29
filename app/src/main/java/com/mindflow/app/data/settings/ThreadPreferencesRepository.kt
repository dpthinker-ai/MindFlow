package com.mindflow.app.data.settings

import com.mindflow.app.data.model.ThreadPreferences
import kotlinx.coroutines.flow.Flow

interface ThreadPreferencesRepository {
    val settings: Flow<ThreadPreferences>

    suspend fun getCurrent(): ThreadPreferences
    suspend fun toggleFollow(threadKey: String): Boolean
    suspend fun clear()
}
