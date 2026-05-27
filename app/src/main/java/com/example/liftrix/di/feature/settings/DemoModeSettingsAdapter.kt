package com.example.liftrix.di.feature.settings

import com.example.liftrix.demo.DemoModeController
import com.example.liftrix.feature.settings.ports.DemoModeSettingsPort
import com.example.liftrix.feature.settings.ports.DemoModeSettingsState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DemoModeSettingsAdapter @Inject constructor(
    private val controller: DemoModeController
) : DemoModeSettingsPort {
    override val state: Flow<DemoModeSettingsState> = controller.state.map { demoState ->
        DemoModeSettingsState(
            enabled = demoState.isActive,
            activatedAtMillis = demoState.activatedAtMillis,
            lastDisabledAtMillis = demoState.lastDisabledAtMillis
        )
    }

    override suspend fun activate(): Result<Unit> =
        controller.activate().map { }

    override suspend fun disable(): Result<Unit> =
        controller.disable().map { }
}
