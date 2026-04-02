package com.mindflow.app.data.model

data class TimeBankSettings(
    val currentAge: Int = 32,
    val expectedLifespan: Int = 88,
    val activeDaysPerWeek: Int = 5,
) {
    val remainingLifeDays: Int
        get() = ((expectedLifespan - currentAge).coerceAtLeast(0) * 365.25).toInt()

    val remainingActiveDays: Int
        get() = (remainingLifeDays * (activeDaysPerWeek.coerceIn(1, 7) / 7f)).toInt()

    val isMeaningful: Boolean
        get() = expectedLifespan > currentAge && currentAge >= 0
}
