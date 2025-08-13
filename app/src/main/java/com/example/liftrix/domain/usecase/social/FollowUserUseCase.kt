package com.example.liftrix.domain.usecase.social

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.social.FollowStatus
import com.example.liftrix.domain.repository.social.FollowRepository
import com.example.liftrix.domain.usecase.auth.GetCurrentUserIdUseCase
import com.example.liftrix.domain.service.AnalyticsTracker
import com.example.liftrix.domain.service.NotificationService
import javax.inject.Inject

/**
 * Use case for follow/unfollow operations with comprehensive relationship management.
 * Part of user profiles and follow system from SPEC-20250113-user-profiles-follow.
 * 
 * Responsibilities:
 * - Manages all follow actions (follow, unfollow, accept, decline)
 * - Validates user permissions and privacy settings
 * - Sends appropriate notifications for follow events
 * - Tracks analytics for social engagement
 * - Handles error recovery with user-friendly messaging
 * 
 * Business Rules:
 * - Public profiles: Creates immediate ACCEPTED relationship
 * - Private profiles: Creates PENDING relationship and sends notification
 * - Users cannot follow themselves
 * - Blocked users cannot follow each other
 * - All operations respect privacy settings
 */
class FollowUserUseCase @Inject constructor(
    private val followRepository: FollowRepository,
    private val getCurrentUserIdUseCase: GetCurrentUserIdUseCase,
    private val notificationService: NotificationService,
    private val analyticsTracker: AnalyticsTracker
) {

    /**
     * Execute follow action with comprehensive validation and notifications
     * 
     * @param targetUserId The ID of the user to follow/unfollow
     * @param action The follow action to perform
     * @param context Additional context for analytics (e.g., "PROFILE_VIEW", "SEARCH_RESULT")
     * @return LiftrixResult containing the new follow status
     */
    suspend operator fun invoke(
        targetUserId: String,
        action: FollowAction,
        context: String = "PROFILE_VIEW"
    ): LiftrixResult<FollowStatus> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "FOLLOW_USER_FAILED",
                errorMessage = "Failed to update follow status",
                analyticsContext = mapOf(
                    "action" to action.name,
                    "target_user_id" to targetUserId,
                    "context" to context,
                    "error" to (throwable.message ?: "Unknown error")
                )
            )
        }
    ) {
        // Get current user ID
        val currentUserId = getCurrentUserIdUseCase()
            ?: throw IllegalStateException("User not authenticated")

        // Validate action parameters
        validateFollowAction(currentUserId, targetUserId, action)

        // Execute the follow action
        val result = when (action) {
            FollowAction.FOLLOW -> followRepository.sendFollowRequest(
                followerId = currentUserId,
                targetUserId = targetUserId,
                requestSource = context
            )
            
            FollowAction.UNFOLLOW -> {
                followRepository.unfollowUser(
                    followerId = currentUserId,
                    targetUserId = targetUserId
                )
                FollowStatus.NONE // Return NONE status after unfollowing
            }
            
            FollowAction.ACCEPT -> followRepository.acceptFollowRequest(
                targetUserId = currentUserId, // Current user is accepting request
                requesterId = targetUserId    // Target user sent the request
            )
            
            FollowAction.DECLINE -> {
                followRepository.declineFollowRequest(
                    targetUserId = currentUserId,
                    requesterId = targetUserId
                )
                FollowStatus.NONE
            }
            
            FollowAction.CANCEL -> {
                followRepository.cancelFollowRequest(
                    followerId = currentUserId,
                    targetUserId = targetUserId
                )
                FollowStatus.NONE
            }
            
            FollowAction.BLOCK -> {
                followRepository.blockUser(
                    blockerId = currentUserId,
                    targetUserId = targetUserId
                )
                FollowStatus.BLOCKED
            }
            
            FollowAction.UNBLOCK -> {
                followRepository.unblockUser(
                    blockerId = currentUserId,
                    targetUserId = targetUserId
                )
                FollowStatus.NONE
            }
        }.let { status ->
            when (status) {
                is FollowStatus -> status
                is LiftrixResult<*> -> (status as LiftrixResult<FollowStatus>).getOrThrow()
                else -> throw IllegalStateException("Unexpected result type: ${status::class.simpleName}")
            }
        }

        // Send notification if appropriate
        sendNotificationIfNeeded(action, targetUserId, currentUserId, result)

        // Track analytics
        trackSocialAction(action, targetUserId, currentUserId, context, result)

        result
    }

    /**
     * Validates follow action parameters
     */
    private fun validateFollowAction(
        currentUserId: String,
        targetUserId: String,
        action: FollowAction
    ) {
        // Users cannot follow themselves (except for some admin actions)
        if (currentUserId == targetUserId && action in listOf(FollowAction.FOLLOW, FollowAction.UNFOLLOW)) {
            throw IllegalArgumentException("Users cannot follow themselves")
        }

        // Validate user IDs are not empty
        if (currentUserId.isBlank() || targetUserId.isBlank()) {
            throw IllegalArgumentException("User IDs cannot be blank")
        }
    }

    /**
     * Sends notifications for follow actions when appropriate
     */
    private suspend fun sendNotificationIfNeeded(
        action: FollowAction,
        targetUserId: String,
        currentUserId: String,
        result: FollowStatus
    ) {
        try {
            when (action) {
                FollowAction.FOLLOW -> {
                    when (result) {
                        FollowStatus.PENDING_SENT -> {
                            // Send follow request notification for private profiles
                            notificationService.sendFollowRequestNotification(
                                targetUserId = targetUserId,
                                requesterUserId = currentUserId,
                                requesterName = "" // TODO: Get actual requester name
                            )
                        }
                        FollowStatus.FOLLOWING -> {
                            // Send follow notification for public profiles
                            // TODO: Implement sendFollowNotification method
                            // notificationService.sendFollowNotification(
                            //     targetUserId = targetUserId,
                            //     followerUserId = currentUserId
                            // )
                        }
                        else -> { /* No notification needed */ }
                    }
                }
                
                FollowAction.ACCEPT -> {
                    // Send follow acceptance notification
                    notificationService.sendFollowAcceptedNotification(
                        targetUserId = targetUserId, // Person who sent original request
                        accepterUserId = currentUserId, // Person who accepted
                        accepterName = "" // TODO: Get actual accepter name
                    )
                }
                
                else -> { /* No notifications for other actions */ }
            }
        } catch (e: Exception) {
            // Don't fail the main operation if notification fails
            timber.log.Timber.w(e, "Failed to send notification for follow action: $action")
        }
    }

    /**
     * Tracks analytics for social actions
     */
    private suspend fun trackSocialAction(
        action: FollowAction,
        targetUserId: String,
        currentUserId: String,
        context: String,
        result: FollowStatus
    ) {
        try {
            analyticsTracker.trackSocialAction(
                action = action.name,
                targetUser = targetUserId,
                source = context,
                additionalProperties = mapOf(
                    "result_status" to result.name,
                    "user_id" to currentUserId
                )
            )
        } catch (e: Exception) {
            // Don't fail the main operation if analytics fails
            timber.log.Timber.w(e, "Failed to track social action analytics: $action")
        }
    }
}

/**
 * Enum representing different follow actions
 */
enum class FollowAction {
    FOLLOW,
    UNFOLLOW,
    ACCEPT,
    DECLINE,
    CANCEL,
    BLOCK,
    UNBLOCK
}