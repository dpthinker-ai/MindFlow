package com.mindflow.app.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mindflow.app.data.model.AppThemeMode
import com.mindflow.app.data.model.AppearanceSettings
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.appearanceSettingsDataStore by preferencesDataStore(name = "mindflow_appearance_settings")

class PreferencesAppearanceSettingsRepository(
    private val context: Context,
) : AppearanceSettingsRepository {
    override val settings: Flow<AppearanceSettings> = context.appearanceSettingsDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            AppearanceSettings(
                themeMode = preferences[THEME_MODE]
                    ?.let { raw -> runCatching { AppThemeMode.valueOf(raw) }.getOrNull() }
                    ?: AppThemeMode.LIGHT,
            )
        }

    override suspend fun getCurrent(): AppearanceSettings = settings.first()

    override suspend fun save(settings: AppearanceSettings) {
        context.appearanceSettingsDataStore.edit { preferences ->
            preferences[THEME_MODE] = settings.themeMode.name
        }
    }

    override suspend fun clear() {
        context.appearanceSettingsDataStore.edit { preferences ->
            preferences.clear()
        }
    }

    private companion object {
        val THEME_MODE = stringPreferencesKey("theme_mode")
    }
}
