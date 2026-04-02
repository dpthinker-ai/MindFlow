package com.mindflow.app.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mindflow.app.data.model.TimeBankSettings
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.timeBankSettingsDataStore by preferencesDataStore(name = "mindflow_time_bank_settings")

class PreferencesTimeBankSettingsRepository(
    private val context: Context,
) : TimeBankSettingsRepository {
    override val settings: Flow<TimeBankSettings> = context.timeBankSettingsDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            TimeBankSettings(
                currentAge = preferences[CURRENT_AGE] ?: 32,
                expectedLifespan = preferences[EXPECTED_LIFESPAN] ?: 88,
                activeDaysPerWeek = preferences[ACTIVE_DAYS_PER_WEEK] ?: 5,
            )
        }

    override suspend fun getCurrent(): TimeBankSettings = settings.first()

    override suspend fun save(settings: TimeBankSettings) {
        context.timeBankSettingsDataStore.edit { preferences ->
            preferences[CURRENT_AGE] = settings.currentAge
            preferences[EXPECTED_LIFESPAN] = settings.expectedLifespan
            preferences[ACTIVE_DAYS_PER_WEEK] = settings.activeDaysPerWeek
        }
    }

    override suspend fun clear() {
        context.timeBankSettingsDataStore.edit { preferences ->
            preferences.clear()
        }
    }

    private companion object {
        val CURRENT_AGE = intPreferencesKey("current_age")
        val EXPECTED_LIFESPAN = intPreferencesKey("expected_lifespan")
        val ACTIVE_DAYS_PER_WEEK = intPreferencesKey("active_days_per_week")
    }
}
