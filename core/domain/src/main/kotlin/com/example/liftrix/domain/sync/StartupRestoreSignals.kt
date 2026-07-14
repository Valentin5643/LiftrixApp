package com.example.liftrix.domain.sync

import kotlinx.coroutines.flow.Flow

/** Read-only startup restore state exposed outside the sync runtime. */
interface StartupRestoreStatusSource {
    fun isRestoreComplete(userId: String): Boolean

    fun currentStateLabel(userId: String): String
}

/** Read-only template restore completion events exposed outside the sync runtime. */
interface TemplateRestoreEventSource {
    val events: Flow<TemplateRestoreCompleted>
}

data class TemplateRestoreCompleted(
    val userId: String,
    val templateCount: Int,
    val finishedAtMs: Long
)
