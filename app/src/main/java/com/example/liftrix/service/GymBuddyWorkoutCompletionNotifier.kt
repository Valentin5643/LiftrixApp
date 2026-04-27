package com.example.liftrix.service

import com.example.liftrix.domain.model.Workout
import com.example.liftrix.domain.model.WorkoutStatus
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.repository.FCMTokenRepository
import com.example.liftrix.domain.repository.social.GymBuddyRepository
import com.example.liftrix.domain.service.NotificationHandler
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.RemoteMessage
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sends real Gym Buddy workout-completion notifications from persisted workout data.
 */
@Singleton
class GymBuddyWorkoutCompletionNotifier @Inject constructor(
    private val gymBuddyRepository: GymBuddyRepository,
    private val authRepository: AuthRepository,
    private val fcmTokenRepository: FCMTokenRepository,
    private val notificationHandler: NotificationHandler,
    private val firebaseMessaging: FirebaseMessaging
) {

    suspend fun notifyWorkoutCompleted(workout: Workout) {
        if (workout.status != WorkoutStatus.COMPLETED) {
            Timber.d("Skipping Gym Buddy notification for non-completed workout ${workout.id.value}")
            return
        }

        val buddies = gymBuddyRepository.getGymBuddies(workout.userId).fold(
            onSuccess = { it },
            onFailure = { error ->
                Timber.e("Failed to load Gym Buddies for workout notification: ${error.message}")
                emptyList()
            }
        )

        val recipientIds = linkedSetOf<String>().apply {
            buddies.mapTo(this) { it.buddyId }

            // DEBUG: Self-notification makes local workout-completion testing possible.
            // Remove this line before production if self-notifications are no longer desired.
            add(workout.userId)
        }

        if (recipientIds.isEmpty()) {
            Timber.d("No Gym Buddy workout-completion recipients for ${workout.id.value}")
            return
        }

        val completerName = resolveCompleterDisplayName(workout.userId)
        val title = "Your Gym Buddy Finished a Workout"
        val body = "$completerName just finished ${workout.name}"

        recipientIds.forEach { recipientId ->
            if (recipientId == workout.userId) {
                sendLocalDebugNotification(
                    recipientId = recipientId,
                    workout = workout,
                    title = title,
                    body = body
                )
            } else {
                sendRemoteBuddyNotification(
                    recipientId = recipientId,
                    workout = workout,
                    title = title,
                    body = body,
                    completerName = completerName
                )
            }
        }

        Timber.i(
            "Sent Gym Buddy workout-completion notifications for workout ${workout.id.value} " +
                "to ${recipientIds.size} recipient(s)"
        )
    }

    private suspend fun sendLocalDebugNotification(
        recipientId: String,
        workout: Workout,
        title: String,
        body: String
    ) {
        notificationHandler.showSystemNotification(
            title = title,
            body = body,
            type = LOCAL_NOTIFICATION_TYPE,
            data = notificationData(
                recipientId = recipientId,
                workout = workout,
                title = title,
                body = body
            )
        )
    }

    private suspend fun sendRemoteBuddyNotification(
        recipientId: String,
        workout: Workout,
        title: String,
        body: String,
        completerName: String
    ) {
        val tokens = fcmTokenRepository.getActiveTokensForUser(recipientId).fold(
            onSuccess = { tokenList -> tokenList.distinctBy { it.token } },
            onFailure = { error ->
                Timber.e("Failed to load FCM tokens for Gym Buddy notification: ${error.message}")
                emptyList()
            }
        )

        if (tokens.isEmpty()) {
            Timber.d("No active FCM tokens for Gym Buddy notification recipient $recipientId")
            return
        }

        tokens.forEach { token ->
            try {
                val message = RemoteMessage.Builder(token.token)
                    .setData(
                        notificationData(
                            recipientId = recipientId,
                            workout = workout,
                            title = title,
                            body = body
                        ) + mapOf(
                            "fromUserName" to completerName,
                            "click_action" to "OPEN_WORKOUT_DETAIL"
                        )
                    )
                    .setMessageId("gym_buddy_workout_${workout.id.value}_${token.id}_${System.currentTimeMillis()}")
                    .setTtl(24 * 60 * 60 * 1000)
                    .build()

                firebaseMessaging.send(message)
            } catch (e: Exception) {
                Timber.e(e, "Failed to send Gym Buddy workout notification to token ${token.getMaskedToken()}")
            }
        }
    }

    private fun notificationData(
        recipientId: String,
        workout: Workout,
        title: String,
        body: String
    ): Map<String, String> = mapOf(
        "type" to REMOTE_NOTIFICATION_TYPE,
        "title" to title,
        "body" to body,
        "recipient_user_id" to recipientId,
        "completed_by_user_id" to workout.userId,
        "workout_id" to workout.id.value,
        "workout_name" to workout.name
    )

    private suspend fun resolveCompleterDisplayName(userId: String): String {
        val currentUser = authRepository.getCurrentUser()
        if (currentUser?.uid == userId) {
            return currentUser.displayName
                ?.takeIf { it.isNotBlank() }
                ?: currentUser.email.takeIf { it.isNotBlank() }
                ?: "Your gym buddy"
        }

        return "Your gym buddy"
    }

    private companion object {
        const val LOCAL_NOTIFICATION_TYPE = "gym_buddy_workout_completed"
        const val REMOTE_NOTIFICATION_TYPE = "GYM_BUDDY_WORKOUT_COMPLETED"
    }
}
