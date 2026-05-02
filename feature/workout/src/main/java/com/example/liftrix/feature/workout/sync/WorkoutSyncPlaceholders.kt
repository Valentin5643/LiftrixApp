package com.example.liftrix.feature.workout.sync

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StartupRestoreGate @Inject constructor() {
    fun isRestoreComplete(userId: String): Boolean = true
    fun currentState(userId: String): String = "feature-owned-noop"
}

data class TemplateRestoreEvent(
    val userId: String,
    val templateCount: Int,
    val finishedAtMs: Long
)

@Singleton
class TemplateRestoreNotifier @Inject constructor() {
    val events: Flow<TemplateRestoreEvent> = emptyFlow()
}
