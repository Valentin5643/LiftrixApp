package com.example.liftrix.sync

import com.example.liftrix.domain.sync.TemplateRestoreCompleted
import com.example.liftrix.domain.sync.TemplateRestoreEventSource
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TemplateRestoreNotifier @Inject constructor() : TemplateRestoreEventSource {
    private val _events = MutableSharedFlow<TemplateRestoreCompleted>(
        replay = 0,
        extraBufferCapacity = 8
    )
    override val events: SharedFlow<TemplateRestoreCompleted> = _events.asSharedFlow()

    fun notifyRestoreCompleted(userId: String, templateCount: Int, finishedAtMs: Long) {
        val event = TemplateRestoreCompleted(
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
