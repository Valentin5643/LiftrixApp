package com.example.liftrix.service

import android.app.IntentService
import android.content.Intent
import androidx.work.WorkManager
import com.example.liftrix.worker.NotificationActionWorker
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

/**
 * Service for handling notification actions without opening the main app.
 * Part of notification system from SPEC-20250113-notifications-privacy.
 */
@AndroidEntryPoint
class NotificationActionService : IntentService("NotificationActionService") {

    override fun onHandleIntent(intent: Intent?) {
        when (intent?.action) {
            "CELEBRATE_PR" -> {
                val fromUser = intent.getStringExtra("from_user") ?: return
                enqueueAction(NotificationActionWorker.ACTION_CELEBRATE_PR, fromUser)
            }
            "ACCEPT_FOLLOW" -> {
                val fromUser = intent.getStringExtra("from_user") ?: return
                enqueueAction(NotificationActionWorker.ACTION_ACCEPT_FOLLOW, fromUser)
            }
            "DECLINE_FOLLOW" -> {
                val fromUser = intent.getStringExtra("from_user") ?: return
                enqueueAction(NotificationActionWorker.ACTION_DECLINE_FOLLOW, fromUser)
            }
            else -> {
                Timber.w("Unknown notification action: ${intent?.action}")
            }
        }
    }

    private fun enqueueAction(action: String, fromUser: String) {
        Timber.d("Enqueueing notification action=$action fromUser=$fromUser")
        WorkManager.getInstance(applicationContext)
            .enqueue(NotificationActionWorker.createWorkRequest(action, fromUser))
    }
}
