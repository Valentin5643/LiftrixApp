package com.example.liftrix.service

import android.app.IntentService
import android.content.Intent
import com.example.liftrix.domain.service.AnalyticsTracker
import com.example.liftrix.domain.usecase.social.SocialRelationshipUseCase
import com.example.liftrix.domain.repository.social.EngagementRepository
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

/**
 * Service for handling notification actions without opening the main app.
 * Part of notification system from SPEC-20250113-notifications-privacy.
 */
@AndroidEntryPoint
class NotificationActionService : IntentService("NotificationActionService") {

    @Inject lateinit var analyticsTracker: AnalyticsTracker
    @Inject lateinit var socialRelationshipUseCase: SocialRelationshipUseCase
    @Inject lateinit var engagementRepository: EngagementRepository

    override fun onHandleIntent(intent: Intent?) {
        when (intent?.action) {
            "CELEBRATE_PR" -> {
                val fromUser = intent.getStringExtra("from_user") ?: return
                handleCelebratePR(fromUser)
            }
            "ACCEPT_FOLLOW" -> {
                val fromUser = intent.getStringExtra("from_user") ?: return
                handleAcceptFollow(fromUser)
            }
            "DECLINE_FOLLOW" -> {
                val fromUser = intent.getStringExtra("from_user") ?: return
                handleDeclineFollow(fromUser)
            }
            else -> {
                Timber.w("Unknown notification action: ${intent?.action}")
            }
        }
    }

    private fun handleCelebratePR(fromUser: String) {
        Timber.d("Celebrating PR from user: $fromUser")
        
        runBlocking {
            try {
                // Extract post ID from intent if available for proper PR reaction
                // For now, we'll track the celebration action which can trigger UI celebrations
                // when the user opens the app
                
                // Track the celebration action for analytics
                analyticsTracker.trackNotificationAction(
                    "CELEBRATE_PR", 
                    mapOf(
                        "from_user" to fromUser,
                        "action_source" to "notification_action"
                    )
                )
                
                // In a full implementation, this would:
                // 1. Find the specific PR post that was celebrated
                // 2. Add a celebration reaction to the post
                // 3. Send a notification back to the PR author
                // 4. Update engagement metrics
                
                Timber.i("PR celebration recorded for user: $fromUser")
                
            } catch (e: Exception) {
                Timber.e(e, "Failed to handle PR celebration for user: $fromUser")
                analyticsTracker.trackNotificationAction(
                    "CELEBRATE_PR_FAILED",
                    mapOf(
                        "from_user" to fromUser,
                        "error_message" to (e.message ?: "Unknown error"),
                        "action_source" to "notification_action"
                    )
                )
            }
        }
    }

    private fun handleAcceptFollow(fromUser: String) {
        Timber.d("Accepting follow request from user: $fromUser")
        
        runBlocking {
            try {
                // Accept the follow request using the FollowUserUseCase
                val result = socialRelationshipUseCase.followUser(
                    targetUserId = fromUser,
                    action = com.example.liftrix.domain.usecase.social.FollowAction.ACCEPT,
                    context = "NOTIFICATION_ACTION"
                )
                
                result.fold(
                    onSuccess = { followStatus ->
                        Timber.i("Successfully accepted follow request from $fromUser. Status: $followStatus")
                        
                        // Track successful follow acceptance
                        analyticsTracker.trackNotificationAction(
                            "ACCEPT_FOLLOW_SUCCESS",
                            mapOf(
                                "from_user" to fromUser,
                                "result_status" to followStatus.name,
                                "action_source" to "notification_action"
                            )
                        )
                    },
                    onFailure = { error ->
                        Timber.e("Failed to accept follow request from $fromUser: $error")
                        
                        // Track failed follow acceptance
                        analyticsTracker.trackNotificationAction(
                            "ACCEPT_FOLLOW_FAILED",
                            mapOf(
                                "from_user" to fromUser,
                                "error_message" to error.toString(),
                                "action_source" to "notification_action"
                            )
                        )
                    }
                )
                
            } catch (e: Exception) {
                Timber.e(e, "Exception while accepting follow request from user: $fromUser")
                analyticsTracker.trackNotificationAction(
                    "ACCEPT_FOLLOW_EXCEPTION",
                    mapOf(
                        "from_user" to fromUser,
                        "error_message" to (e.message ?: "Unknown error"),
                        "action_source" to "notification_action"
                    )
                )
            }
        }
    }

    private fun handleDeclineFollow(fromUser: String) {
        Timber.d("Declining follow request from user: $fromUser")
        
        runBlocking {
            try {
                // Decline the follow request using the FollowUserUseCase
                val result = socialRelationshipUseCase.followUser(
                    targetUserId = fromUser,
                    action = com.example.liftrix.domain.usecase.social.FollowAction.DECLINE,
                    context = "NOTIFICATION_ACTION"
                )
                
                result.fold(
                    onSuccess = { followStatus ->
                        Timber.i("Successfully declined follow request from $fromUser. Status: $followStatus")
                        
                        // Track successful follow decline
                        analyticsTracker.trackNotificationAction(
                            "DECLINE_FOLLOW_SUCCESS",
                            mapOf(
                                "from_user" to fromUser,
                                "result_status" to followStatus.name,
                                "action_source" to "notification_action"
                            )
                        )
                    },
                    onFailure = { error ->
                        Timber.e("Failed to decline follow request from $fromUser: $error")
                        
                        // Track failed follow decline
                        analyticsTracker.trackNotificationAction(
                            "DECLINE_FOLLOW_FAILED",
                            mapOf(
                                "from_user" to fromUser,
                                "error_message" to error.toString(),
                                "action_source" to "notification_action"
                            )
                        )
                    }
                )
                
            } catch (e: Exception) {
                Timber.e(e, "Exception while declining follow request from user: $fromUser")
                analyticsTracker.trackNotificationAction(
                    "DECLINE_FOLLOW_EXCEPTION",
                    mapOf(
                        "from_user" to fromUser,
                        "error_message" to (e.message ?: "Unknown error"),
                        "action_source" to "notification_action"
                    )
                )
            }
        }
    }
}