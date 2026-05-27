package com.example.liftrix.ui.settings.about

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.liftrix.feature.settings.ports.DemoModeSettingsPort
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AboutDemoModeViewModel @Inject constructor(
    private val demoModeSettingsPort: DemoModeSettingsPort
) : ViewModel() {
    private val trigger = MutableStateFlow(DemoModeTriggerState())

    val state: StateFlow<AboutDemoModeUiState> = combine(
        demoModeSettingsPort.state,
        trigger
    ) { demoState, triggerState ->
        AboutDemoModeUiState(
            isActive = demoState.enabled,
            pendingTapCount = triggerState.tapCount,
            showConfirmation = triggerState.showConfirmation,
            activationError = triggerState.activationError
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AboutDemoModeUiState()
    )

    fun onVersionLongPress() {
        if (state.value.isActive) {
            disable()
            return
        }
        trigger.value = DemoModeTriggerState(windowStartedAtMillis = now())
    }

    fun onBuildRowTap() {
        val current = trigger.value
        val startedAt = current.windowStartedAtMillis ?: return
        if (now() - startedAt > ACTIVATION_WINDOW_MILLIS) {
            trigger.value = DemoModeTriggerState()
            return
        }

        val nextCount = current.tapCount + 1
        trigger.update {
            it.copy(
                tapCount = nextCount,
                showConfirmation = nextCount >= REQUIRED_BUILD_TAPS,
                activationError = null
            )
        }
    }

    fun confirmActivation() {
        viewModelScope.launch {
            demoModeSettingsPort.activate()
                .onSuccess { trigger.value = DemoModeTriggerState() }
                .onFailure { throwable ->
                    trigger.update {
                        it.copy(
                            showConfirmation = false,
                            activationError = throwable.message ?: "Demo Mode activation failed"
                        )
                    }
                }
        }
    }

    fun dismissConfirmation() {
        trigger.value = DemoModeTriggerState()
    }

    fun disable() {
        viewModelScope.launch {
            demoModeSettingsPort.disable()
            trigger.value = DemoModeTriggerState()
        }
    }

    private fun now(): Long = System.currentTimeMillis()

    private companion object {
        const val REQUIRED_BUILD_TAPS = 7
        const val ACTIVATION_WINDOW_MILLIS = 10_000L
    }
}

data class AboutDemoModeUiState(
    val isActive: Boolean = false,
    val pendingTapCount: Int = 0,
    val showConfirmation: Boolean = false,
    val activationError: String? = null
)

private data class DemoModeTriggerState(
    val windowStartedAtMillis: Long? = null,
    val tapCount: Int = 0,
    val showConfirmation: Boolean = false,
    val activationError: String? = null
)
