package com.mindflow.app.data.model

enum class AppThemeMode {
    LIGHT,
    DARK,
    SYSTEM,
}

data class AppearanceSettings(
    val themeMode: AppThemeMode = AppThemeMode.LIGHT,
)
