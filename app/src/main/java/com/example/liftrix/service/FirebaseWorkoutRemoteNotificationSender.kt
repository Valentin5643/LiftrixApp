package com.example.liftrix.service

import com.example.liftrix.domain.service.WorkoutRemoteNotificationSender
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.RemoteMessage
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseWorkoutRemoteNotificationSender @Inject constructor(
    private val firebaseMessaging: FirebaseMessaging
) : WorkoutRemoteNotificationSender {
    override suspend fun sendDataMessage(
        token: String,
        data: Map<String, String>,
        messageId: String,
        ttlMs: Int
    ) {
        val message = RemoteMessage.Builder(token)
            .setData(data)
            .setMessageId(messageId)
            .setTtl(ttlMs)
            .build()

        firebaseMessaging.send(message)
    }
}
