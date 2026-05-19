package com.example.liftrix.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.liftrix.domain.service.AnalyticsTracker
import com.example.liftrix.domain.usecase.social.FollowAction
import com.example.liftrix.domain.usecase.social.SocialRelationshipUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber
import java.util.concurrent.TimeUnit

@HiltWorker
class NotificationActionWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val analyticsTracker: AnalyticsTracker,
    private val socialRelationshipUseCase: SocialRelationshipUseCase
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val action = inputData.getString(KEY_ACTION)
        val fromUser = inputData.getString(KEY_FROM_USER)

        if (action.isNullOrBlank() || fromUser.isNullOrBlank()) {
            Timber.w("Notification action worker missing action or from_user")
            return Result.failure()
        }

        return when (action) {
            ACTION_CELEBRATE_PR -> handleCelebratePr(fromUser)
            ACTION_ACCEPT_FOLLOW -> handleFollowAction(
                fromUser = fromUser,
                followAction = FollowAction.ACCEPT,
                successEvent = "ACCEPT_FOLLOW_SUCCESS",
                failureEvent = "ACCEPT_FOLLOW_FAILED"
            )
            ACTION_DECLINE_FOLLOW -> handleFollowAction(
                fromUser = fromUser,
                followAction = FollowAction.DECLINE,
                successEvent = "DECLINE_FOLLOW_SUCCESS",
                failureEvent = "DECLINE_FOLLOW_FAILED"
            )
            else -> {
                Timber.w("Unknown notification action work: $action")
                Result.failure()
            }
        }
    }

    private fun handleCelebratePr(fromUser: String): Result {
        return try {
            analyticsTracker.trackNotificationAction(
                "CELEBRATE_PR",
                mapOf(
                    "from_user" to fromUser,
                    "action_source" to "notification_action"
                )
            )
            Timber.i("PR celebration recorded for user: $fromUser")
            Result.success()
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to handle PR celebration for user: $fromUser")
            analyticsTracker.trackNotificationAction(
                "CELEBRATE_PR_FAILED",
                mapOf(
                    "from_user" to fromUser,
                    "error_message" to (exception.message ?: "Unknown error"),
                    "action_source" to "notification_action"
                )
            )
            retryOrFail()
        }
    }

    private suspend fun handleFollowAction(
        fromUser: String,
        followAction: FollowAction,
        successEvent: String,
        failureEvent: String
    ): Result {
        return try {
            val result = socialRelationshipUseCase.followAction(
                targetUserId = fromUser,
                action = followAction,
                context = "NOTIFICATION_ACTION"
            )

            result.fold(
                onSuccess = { followStatus ->
                    Timber.i("Notification follow action $followAction succeeded for $fromUser. Status: $followStatus")
                    analyticsTracker.trackNotificationAction(
                        successEvent,
                        mapOf(
                            "from_user" to fromUser,
                            "result_status" to followStatus.name,
                            "action_source" to "notification_action"
                        )
                    )
                    Result.success()
                },
                onFailure = { error ->
                    Timber.e("Notification follow action $followAction failed for $fromUser: $error")
                    analyticsTracker.trackNotificationAction(
                        failureEvent,
                        mapOf(
                            "from_user" to fromUser,
                            "error_message" to error.toString(),
                            "action_source" to "notification_action"
                        )
                    )
                    retryOrFail()
                }
            )
        } catch (exception: Exception) {
            Timber.e(exception, "Exception during notification follow action $followAction for user: $fromUser")
            analyticsTracker.trackNotificationAction(
                "${followAction.name}_FOLLOW_EXCEPTION",
                mapOf(
                    "from_user" to fromUser,
                    "error_message" to (exception.message ?: "Unknown error"),
                    "action_source" to "notification_action"
                )
            )
            retryOrFail()
        }
    }

    private fun retryOrFail(): Result =
        if (runAttemptCount < MAX_RETRY_COUNT) Result.retry() else Result.failure()

    companion object {
        const val ACTION_CELEBRATE_PR = "CELEBRATE_PR"
        const val ACTION_ACCEPT_FOLLOW = "ACCEPT_FOLLOW"
        const val ACTION_DECLINE_FOLLOW = "DECLINE_FOLLOW"

        private const val KEY_ACTION = "action"
        private const val KEY_FROM_USER = "from_user"
        private const val MAX_RETRY_COUNT = 3

        fun createWorkRequest(action: String, fromUser: String): OneTimeWorkRequest {
            return OneTimeWorkRequestBuilder<NotificationActionWorker>()
                .setInputData(
                    workDataOf(
                        KEY_ACTION to action,
                        KEY_FROM_USER to fromUser
                    )
                )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    15,
                    TimeUnit.SECONDS
                )
                .addTag("notification_action")
                .build()
        }
    }
}
