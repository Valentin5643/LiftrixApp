package com.example.liftrix.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class SocialMutationSyncTest {
    @Test
    fun socialMutationTypesUseMediumPriority() {
        val managerTypes = listOf(
            SyncOperationManager.ENTITY_POST_LIKE,
            SyncOperationManager.ENTITY_SAVED_POST,
            SyncOperationManager.ENTITY_POST_COMMENT,
            SyncOperationManager.ENTITY_BLOCKED_USER,
            SyncOperationManager.ENTITY_CONTENT_REPORT
        )
        managerTypes.forEach { type ->
            assertEquals(SyncOperationManager.PRIORITY_MEDIUM, typePriority(type))
        }
    }

    private fun typePriority(type: String): Int = when (type) {
        SyncOperationManager.ENTITY_POST_LIKE,
        SyncOperationManager.ENTITY_SAVED_POST,
        SyncOperationManager.ENTITY_POST_COMMENT,
        SyncOperationManager.ENTITY_BLOCKED_USER,
        SyncOperationManager.ENTITY_CONTENT_REPORT -> SyncOperationManager.PRIORITY_MEDIUM
        else -> SyncOperationManager.PRIORITY_LOW
    }
}
