package com.example.liftrix.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

enum class StartupRestoreState {
    AUTH_PENDING,
    RESTORE_NOT_STARTED,
    RESTORING_FROM_FIREBASE,
    RESTORE_COMPLETE,
    RESTORE_FAILED
}

@Singleton
class StartupRestoreGate @Inject constructor() {
    private val _states = MutableStateFlow<Map<String, StartupRestoreState>>(emptyMap())
    val states: StateFlow<Map<String, StartupRestoreState>> = _states.asStateFlow()

    fun currentState(userId: String?): StartupRestoreState {
        if (userId.isNullOrBlank()) return StartupRestoreState.AUTH_PENDING
        return _states.value[userId] ?: StartupRestoreState.RESTORE_NOT_STARTED
    }

    fun isRestoreComplete(userId: String): Boolean {
        return currentState(userId) == StartupRestoreState.RESTORE_COMPLETE
    }

    fun transition(userId: String, state: StartupRestoreState, reason: String) {
        val previous = currentState(userId)
        _states.update { it + (userId to state) }
        Timber.tag("StartupRestoreFix").i(
            "operation=RESTORE_GATE_TRANSITION userId=$userId from=$previous to=$state reason=$reason timestamp=${System.currentTimeMillis()}"
        )
    }

    fun resetForAuthPending(reason: String) {
        _states.value.keys.forEach { userId ->
            transition(userId, StartupRestoreState.AUTH_PENDING, reason)
        }
    }
}
