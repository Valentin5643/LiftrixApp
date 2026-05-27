package com.example.liftrix.demo

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Singleton
class DemoModeController @Inject constructor(
    private val store: DemoModeStore,
    @com.example.liftrix.di.ApplicationScope applicationScope: CoroutineScope
) {
    private val mutableState = MutableStateFlow(DemoModeState.Inactive)
    val state: StateFlow<DemoModeState> = mutableState.asStateFlow()

    init {
        applicationScope.launch {
            store.state.collect { persistedState ->
                if (!(mutableState.value.isActive && !persistedState.isActive)) {
                    mutableState.value = persistedState
                }
            }
        }
    }

    suspend fun activate(): Result<DemoModeState> = runCatching {
        val now = System.currentTimeMillis()
        val seed = Random(now).nextLong(100_000L, Long.MAX_VALUE)
        val activeState = DemoModeState(enabled = true, sessionSeed = seed, activatedAtMillis = now)
        mutableState.value = activeState
        store.activate(sessionSeed = seed, activatedAtMillis = now)
        activeState
    }

    suspend fun disable(): Result<DemoModeState> = runCatching {
        val now = System.currentTimeMillis()
        val inactiveState = DemoModeState(lastDisabledAtMillis = now)
        mutableState.value = inactiveState
        store.disable(disabledAtMillis = now)
        inactiveState
    }
}
