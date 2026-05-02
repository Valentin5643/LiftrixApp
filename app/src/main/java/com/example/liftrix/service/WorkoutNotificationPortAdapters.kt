package com.example.liftrix.service

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.repository.FCMTokenRepository
import com.example.liftrix.domain.service.NotificationHandler
import com.example.liftrix.domain.service.WorkoutLocalNotificationPresenter
import com.example.liftrix.domain.service.WorkoutNotificationToken
import com.example.liftrix.domain.service.WorkoutNotificationTokenSource
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkoutNotificationTokenSourceAdapter @Inject constructor(
    private val fcmTokenRepository: FCMTokenRepository
) : WorkoutNotificationTokenSource {
    override suspend fun getActiveTokensForUser(
        userId: String
    ): LiftrixResult<List<WorkoutNotificationToken>> {
        return fcmTokenRepository.getActiveTokensForUser(userId).map { tokens ->
            tokens.map { token ->
                WorkoutNotificationToken(
                    id = token.id,
                    token = token.token
                )
            }
        }
    }
}

@Singleton
class WorkoutLocalNotificationPresenterAdapter @Inject constructor(
    private val notificationHandler: NotificationHandler
) : WorkoutLocalNotificationPresenter {
    override suspend fun showSystemNotification(
        title: String,
        body: String,
        type: String,
        data: Map<String, String>
    ) {
        notificationHandler.showSystemNotification(
            title = title,
            body = body,
            type = type,
            data = data
        )
    }
}
