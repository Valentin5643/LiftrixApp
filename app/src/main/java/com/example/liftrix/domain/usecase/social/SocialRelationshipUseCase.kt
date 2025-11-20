package com.example.liftrix.domain.usecase.social

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.social.FollowStatus
import com.example.liftrix.domain.model.social.ReportReason
import com.example.liftrix.domain.repository.social.BlockRepository
import com.example.liftrix.domain.repository.social.FollowRepository
import com.example.liftrix.domain.repository.social.ReportRepository
import com.example.liftrix.domain.service.AnalyticsTracker
import com.example.liftrix.domain.service.NotificationService
import com.example.liftrix.domain.usecase.auth.AuthQueryUseCase
import timber.log.Timber
import javax.inject.Inject

/**
 * Consolidated use case for managing social relationships.
 *
 * Consolidates:
 * - FollowUserUseCase (follow/unfollow operations)
 * - BlockUserUseCase (block/unblock operations)
 * - ReportUserUseCase (report user operations)
 *
 * Part of Phase 3: Social & Workout Domains consolidation.
 * Ref: SPEC-20251031-usecase-consolidation.md
 *
 * Business Rules:
 * - Public profiles: Creates immediate ACCEPTED relationship
 * - Private profiles: Creates PENDING relationship and sends notification
 * - Users cannot follow/block/report themselves
 * - Blocked users cannot follow each other
 * - All operations respect privacy settings
 */
class SocialRelationshipUseCase @Inject constructor(
    private val followRepository: FollowRepository,
    private val blockRepository: BlockRepository,
    private val reportRepository: ReportRepository,
    private val authQueryUseCase: AuthQueryUseCase,
    private val notificationService: NotificationService,
    private val analyticsTracker: AnalyticsTracker,
    private val getSocialProfileQueryUseCase: SocialProfileQueryUseCase
) {

    /**
     * Execute follow action with comprehensive validation and notifications.
     * Replaces: FollowUserUseCase.invoke()
     *
     * @param targetUserId The ID of the user to follow/unfollow
     * @param action The follow action to perform
     * @param context Additional context for analytics (e.g., "PROFILE_VIEW", "SEARCH_RESULT")
     * @return LiftrixResult containing the new follow status
     */
    suspend fun followAction(
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
        val currentUserId = authQueryUseCase(waitForAuth = false).getOrNull()
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
     * Block or unblock a user with automatic unfollow.
     * Replaces: BlockUserUseCase.invoke()
     *
     * @param targetUserId The user to block/unblock
     * @param shouldBlock True to block, false to unblock
     * @return Result indicating success or failure
     */
    suspend fun blockUser(
        targetUserId: String,
        shouldBlock: Boolean
    ): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = if (shouldBlock) "BLOCK_USER_FAILED" else "UNBLOCK_USER_FAILED",
                errorMessage = "Failed to ${if (shouldBlock) "block" else "unblock"} user",
                analyticsContext = mapOf(
                    "target_user_id" to targetUserId,
                    "action" to if (shouldBlock) "BLOCK" else "UNBLOCK",
                    "error" to (throwable.message ?: "Unknown error")
                )
            )
        }
    ) {
        // Get current user ID
        val currentUserId = authQueryUseCase(waitForAuth = false).getOrNull()
            ?: throw IllegalStateException("User not authenticated")

        // Validate not blocking self
        if (currentUserId == targetUserId) {
            throw IllegalArgumentException("Cannot block yourself")
        }

        if (shouldBlock) {
            // When blocking, first unfollow in both directions
            try {
                followRepository.unfollowUser(currentUserId, targetUserId)
                followRepository.unfollowUser(targetUserId, currentUserId)
            } catch (e: Exception) {
                Timber.w(e, "Failed to unfollow during block, continuing with block")
            }

            // Block the user
            blockRepository.blockUser(currentUserId, targetUserId)

            Timber.d("User blocked: $targetUserId by $currentUserId")
        } else {
            // Unblock the user
            blockRepository.unblockUser(currentUserId, targetUserId)

            Timber.d("User unblocked: $targetUserId by $currentUserId")
        }

        Unit
    }

    /**
     * Report a user for inappropriate behavior.
     * Replaces: ReportUserUseCase.invoke()
     *
     * @param targetUserId The user being reported
     * @param reason The reason for the report
     * @param description Additional details about the report
     * @return Result indicating success or failure
     */
    suspend fun reportUser(
        targetUserId: String,
        reason: ReportReason,
        description: String? = null
    ): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "REPORT_USER_FAILED",
                errorMessage = "Failed to submit report",
                analyticsContext = mapOf(
                    "target_user_id" to targetUserId,
                    "reason" to reason.name,
                    "error" to (throwable.message ?: "Unknown error")
                )
            )
        }
    ) {
        // Get current user ID
        val currentUserId = authQueryUseCase(waitForAuth = false).getOrNull()
            ?: throw IllegalStateException("User not authenticated")

        // Validate not reporting self
        if (currentUserId == targetUserId) {
            throw IllegalArgumentException("Cannot report yourself")
        }

        // Check for existing report to prevent duplicates
        val hasExistingReport = reportRepository.hasExistingReport(
            reporterId = currentUserId,
            targetUserId = targetUserId
        )

        if (hasExistingReport) {
            Timber.w("User already reported: $targetUserId by $currentUserId")
            return@liftrixCatching Unit
        }

        // Submit the report
        reportRepository.submitReport(
            reporterId = currentUserId,
            targetUserId = targetUserId,
            reason = reason,
            description = description
        )

        Timber.d("User reported: $targetUserId by $currentUserId for reason: ${reason.name}")

        Unit
    }

    // Private helper methods

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
                            // Get requester's name
                            val requesterProfile = getSocialProfileQueryUseCase(currentUserId).getOrNull()
                            val requesterName = requesterProfile?.displayName ?: "Someone"

                            // Send follow request notification for private profiles
                            notificationService.sendFollowRequestNotification(
                                targetUserId = targetUserId,
                                requesterUserId = currentUserId,
                                requesterName = requesterName
                            )
                        }
                        FollowStatus.FOLLOWING -> {
                            // Get follower's name
                            val followerProfile = getSocialProfileQueryUseCase(currentUserId).getOrNull()
                            val followerName = followerProfile?.displayName ?: "Someone"

                            // Send follow notification for public profiles
                            notificationService.sendFollowNotification(
                                targetUserId = targetUserId,
                                followerUserId = currentUserId,
                                followerName = followerName
                            )
                        }
                        else -> { /* No notification needed */ }
                    }
                }

                FollowAction.ACCEPT -> {
                    // Get accepter's name
                    val accepterProfile = getSocialProfileQueryUseCase(currentUserId).getOrNull()
                    val accepterName = accepterProfile?.displayName ?: "Someone"

                    // Send follow acceptance notification
                    notificationService.sendFollowAcceptedNotification(
                        targetUserId = targetUserId, // Person who sent original request
                        accepterUserId = currentUserId, // Person who accepted
                        accepterName = accepterName
                    )
                }

                else -> { /* No notifications for other actions */ }
            }
        } catch (e: Exception) {
            // Don't fail the main operation if notification fails
            Timber.w(e, "Failed to send notification for follow action: $action")
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
            Timber.w(e, "Failed to track social action analytics: $action")
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
