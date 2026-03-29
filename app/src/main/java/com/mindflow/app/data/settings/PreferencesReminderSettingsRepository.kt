package com.mindflow.app.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mindflow.app.data.model.ReminderSettings
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.reminderSettingsDataStore by preferencesDataStore(name = "mindflow_reminder_settings")

class PreferencesReminderSettingsRepository(
    private val context: Context,
) : ReminderSettingsRepository {
    override val settings: Flow<ReminderSettings> = context.reminderSettingsDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            ReminderSettings(
                morningBriefEnabled = preferences[MORNING_BRIEF_ENABLED] ?: false,
                eveningReviewEnabled = preferences[EVENING_REVIEW_ENABLED] ?: false,
                morningHour = preferences[MORNING_HOUR] ?: ReminderSettings.DEFAULT_MORNING_HOUR,
                morningMinute = preferences[MORNING_MINUTE] ?: ReminderSettings.DEFAULT_MORNING_MINUTE,
                eveningHour = preferences[EVENING_HOUR] ?: ReminderSettings.DEFAULT_EVENING_HOUR,
                eveningMinute = preferences[EVENING_MINUTE] ?: ReminderSettings.DEFAULT_EVENING_MINUTE,
            )
        }

    override suspend fun getCurrent(): ReminderSettings = settings.first()

    override suspend fun save(settings: ReminderSettings) {
        context.reminderSettingsDataStore.edit { preferences ->
            preferences[MORNING_BRIEF_ENABLED] = settings.morningBriefEnabled
            preferences[EVENING_REVIEW_ENABLED] = settings.eveningReviewEnabled
            preferences[MORNING_HOUR] = settings.morningHour
            preferences[MORNING_MINUTE] = settings.morningMinute
            preferences[EVENING_HOUR] = settings.eveningHour
            preferences[EVENING_MINUTE] = settings.eveningMinute
        }
    }

    override suspend fun clear() {
        context.reminderSettingsDataStore.edit { preferences ->
            preferences.clear()
        }
    }

    private companion object {
        val MORNING_BRIEF_ENABLED = booleanPreferencesKey("morning_brief_enabled")
        val EVENING_REVIEW_ENABLED = booleanPreferencesKey("evening_review_enabled")
        val MORNING_HOUR = intPreferencesKey("morning_hour")
        val MORNING_MINUTE = intPreferencesKey("morning_minute")
        val EVENING_HOUR = intPreferencesKey("evening_hour")
        val EVENING_MINUTE = intPreferencesKey("evening_minute")
    }
}
