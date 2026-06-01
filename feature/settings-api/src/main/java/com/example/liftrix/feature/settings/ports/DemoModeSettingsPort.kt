package com.example.liftrix.feature.settings.ports

import kotlinx.coroutines.flow.Flow

data class DemoModeSettingsState(
    val enabled: Boolean = false,
    val activatedAtMillis: Long? = null,
    val lastDisabledAtMillis: Long? = null
)

interface DemoModeSettingsPort {
    val state: Flow<DemoModeSettingsState>

    suspend fun activate(): Result<Unit>

    suspend fun disable(): Result<Unit>
}
