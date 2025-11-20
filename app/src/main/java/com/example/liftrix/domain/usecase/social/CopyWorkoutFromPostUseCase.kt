package com.example.liftrix.domain.usecase.social

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.social.EngagementRepository
import com.example.liftrix.domain.usecase.auth.AuthQueryUseCase
import timber.log.Timber
import javax.inject.Inject

/**
 * Copy a workout from a social post to user's workout templates.
 * 
 * Implements social engagement pattern from CLAUDE.md:
 * - Workout copying functionality from posts
 * - Template generation from shared workouts
 * - LiftrixResult<T> error handling
 * - User privacy enforcement
 */
class CopyWorkoutFromPostUseCase @Inject constructor(
    private val engagementRepository: EngagementRepository,
    private val authQueryUseCase: AuthQueryUseCase
) {
    
    /**
     * Copy a workout from a post to user's templates.
     * 
     * @param postId Post containing the workout to copy
     * @return Template ID of the copied workout
     */
    suspend operator fun invoke(postId: String): LiftrixResult<String> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "COPY_WORKOUT_FAILED",
                errorMessage = "Failed to copy workout from post",
                analyticsContext = mapOf(
                    "operation" to "COPY_WORKOUT",
                    "post_id" to postId,
                    "error" to throwable.message.orEmpty()
                )
            )
        }
    ) {
        // Get current user ID
        val userId = authQueryUseCase(waitForAuth = false).getOrThrow()
        
        Timber.d("Copying workout from post: $postId for user: $userId")
        
        // Copy workout from post
        val result = engagementRepository.copyWorkoutFromPost(postId, userId)
        
        result.fold(
            onSuccess = { templateId ->
                Timber.i("Workout copied successfully from post $postId to template $templateId")
                templateId
            },
            onFailure = { error ->
                Timber.e(error, "Failed to copy workout from post: $postId")
                throw error
            }
        )
    }
}