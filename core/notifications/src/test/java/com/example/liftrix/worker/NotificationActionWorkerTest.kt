package com.example.liftrix.worker

import androidx.work.NetworkType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationActionWorkerTest {

    @Test
    fun createWorkRequestCarriesActionPayloadAndNetworkConstraint() {
        val request = NotificationActionWorker.createWorkRequest(
            action = NotificationActionWorker.ACTION_ACCEPT_FOLLOW,
            fromUser = "user-123"
        )

        assertEquals(NotificationActionWorker.ACTION_ACCEPT_FOLLOW, request.workSpec.input.getString("action"))
        assertEquals("user-123", request.workSpec.input.getString("from_user"))
        assertEquals(NetworkType.CONNECTED, request.workSpec.constraints.requiredNetworkType)
        assertTrue(request.tags.contains("notification_action"))
    }
}
