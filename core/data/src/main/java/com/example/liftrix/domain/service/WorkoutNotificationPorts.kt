package com.example.liftrix.domain.service

import com.example.liftrix.domain.model.common.LiftrixResult

data class WorkoutNotificationToken(
    val id: String,
    val token: String
)

interface WorkoutNotificationTokenSource {
    suspend fun getActiveTokensForUser(userId: String): LiftrixResult<List<WorkoutNotificationToken>>
}

interface WorkoutLocalNotificationPresenter {
    suspend fun showSystemNotification(
        title: String,
        body: String,
        type: String,
        data: Map<String, String> = emptyMap()
    )
}
