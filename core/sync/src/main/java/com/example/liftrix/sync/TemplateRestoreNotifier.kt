package com.example.liftrix.sync

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

data class TemplateRestoreCompletedEvent(
    val userId: String,
    val templateCount: Int,
    val finishedAtMs: Long
)

@Singleton
class TemplateRestoreNotifier @Inject constructor() {
    private val _events = MutableSharedFlow<TemplateRestoreCompletedEvent>(
        replay = 0,
        extraBufferCapacity = 8
    )
    val events: SharedFlow<TemplateRestoreCompletedEvent> = _events.asSharedFlow()

    fun notifyRestoreCompleted(userId: String, templateCount: Int, finishedAtMs: Long) {
        val event = TemplateRestoreCompletedEvent(
            userId = userId,
            templateCount = templateCount,
            finishedAtMs = finishedAtMs
        )
        val emitted = _events.tryEmit(event)
        Timber.tag("StartupRestoreFix").i(
            "[TEMPLATE-LOAD] operation=TEMPLATE_DAO_FLOW_INVALIDATED userId=$userId count=$templateCount emitted=$emitted restoreFinishedAt=$finishedAtMs timestamp=${System.currentTimeMillis()}"
        )
    }
}
