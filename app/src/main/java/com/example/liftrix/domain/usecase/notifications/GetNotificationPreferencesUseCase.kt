package com.example.liftrix.domain.usecase.notifications

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.common.liftrixFailure
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.notifications.NotificationPreferences
import com.example.liftrix.domain.repository.NotificationRepository
import com.example.liftrix.domain.usecase.auth.GetCurrentUserIdUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

/**
 * Use case for retrieving notification preferences with comprehensive error handling.
 * 
 * This use case provides reactive access to user notification preferences through
 * Flow streams, handling authentication, validation, and error recovery. It ensures
 * that the UI always has access to valid notification preferences, creating defaults
 * when none exist.
 * 
 * Key responsibilities:
 * - Validates user authentication before preference retrieval
 * - Provides reactive streams for real-time preference updates
 * - Creates default preferences for new users automatically
 * - Handles errors gracefully with detailed error context
 * - Supports both authenticated and guest user scenarios
 */
class GetNotificationPreferencesUseCase @Inject constructor(
    private val notificationRepository: NotificationRepository,
    private val getCurrentUserIdUseCase: GetCurrentUserIdUseCase
) {

    /**
     * Retrieves notification preferences for the current authenticated user.
     * 
     * This method provides continuous updates to preference changes with proper
     * error handling and automatic fallback to default preferences when none exist.
     * 
     * @return Flow<LiftrixResult<NotificationPreferences>> that emits preferences
     *         updates or detailed error information
     */
    suspend operator fun invoke(): Flow<LiftrixResult<NotificationPreferences>> {
        return try {
            // Get current user ID
            val userId = getCurrentUserIdUseCase()
                ?: return flowOf(
                    liftrixFailure(
                        LiftrixError.BusinessLogicError(
                            code = "USER_NOT_AUTHENTICATED",
                            errorMessage = "User not authenticated"
                        )
                    )
                )

            timber.log.Timber.d("Retrieving notification preferences for user: $userId")

            // Get preferences from repository as reactive stream
            notificationRepository.getNotificationPreferences(userId)
                .map { preferences ->
                    if (preferences != null) {
                        timber.log.Timber.d("Notification preferences found for user: $userId")
                        LiftrixResult.success(preferences)
                    } else {
                        timber.log.Timber.d("No preferences found for user: $userId, creating defaults")
                        
                        // Create default preferences automatically
                        val defaultResult = notificationRepository.createDefaultPreferences(userId)
                        defaultResult.fold(
                            onSuccess = { defaultPrefs ->
                                timber.log.Timber.d("Default notification preferences created for user: $userId")
                                LiftrixResult.success(defaultPrefs)
                            },
                            onFailure = { error ->
                                timber.log.Timber.e("Failed to create default preferences for user: $userId, error: $error")
                                // Return in-memory defaults as fallback
                                LiftrixResult.success(NotificationPreferences.createDefault(userId))
                            }
                        )
                    }
                }
                .catch { throwable ->
                    val error = LiftrixError.BusinessLogicError(
                        code = "GET_NOTIFICATION_PREFERENCES_FAILED",
                        errorMessage = "Failed to retrieve notification preferences",
                        analyticsContext = mapOf(
                            "error" to (throwable.message ?: "Unknown error"),
                            "error_type" to (throwable::class.simpleName ?: "Unknown")
                        )
                    )
                    emit(liftrixFailure(error))
                }
        } catch (throwable: Throwable) {
            val error = LiftrixError.BusinessLogicError(
                code = "GET_NOTIFICATION_PREFERENCES_FAILED",
                errorMessage = "Failed to retrieve notification preferences",
                analyticsContext = mapOf(
                    "error" to (throwable.message ?: "Unknown error"),
                    "error_type" to (throwable::class.simpleName ?: "Unknown")
                )
            )
            flowOf(liftrixFailure(error))
        }
    }

    /**
     * Retrieves notification preferences for a specific user ID.
     * 
     * This method is useful for admin operations or when accessing preferences
     * for users other than the current authenticated user.
     * 
     * @param userId The ID of the user whose preferences to retrieve
     * @return Flow<LiftrixResult<NotificationPreferences>> that emits preferences
     */
    suspend fun getPreferencesForUser(userId: String): Flow<LiftrixResult<NotificationPreferences>> {
        return try {
            if (userId.isBlank()) {
                return flowOf(
                    liftrixFailure(
                        LiftrixError.BusinessLogicError(
                            code = "INVALID_USER_ID",
                            errorMessage = "User ID cannot be blank"
                        )
                    )
                )
            }

            timber.log.Timber.d("Retrieving notification preferences for specific user: $userId")

            notificationRepository.getNotificationPreferences(userId)
                .map { preferences ->
                    if (preferences != null) {
                        timber.log.Timber.d("Notification preferences found for user: $userId")
                        LiftrixResult.success(preferences)
                    } else {
                        timber.log.Timber.d("No preferences found for user: $userId, returning defaults")
                        LiftrixResult.success(NotificationPreferences.createDefault(userId))
                    }
                }
                .catch { throwable ->
                    val error = LiftrixError.BusinessLogicError(
                        code = "GET_USER_NOTIFICATION_PREFERENCES_FAILED",
                        errorMessage = "Failed to retrieve notification preferences for user",
                        analyticsContext = mapOf(
                            "target_user_id" to userId,
                            "error" to (throwable.message ?: "Unknown error"),
                            "error_type" to (throwable::class.simpleName ?: "Unknown")
                        )
                    )
                    emit(liftrixFailure(error))
                }
        } catch (throwable: Throwable) {
            val error = LiftrixError.BusinessLogicError(
                code = "GET_USER_NOTIFICATION_PREFERENCES_FAILED",
                errorMessage = "Failed to retrieve notification preferences for user",
                analyticsContext = mapOf(
                    "target_user_id" to userId,
                    "error" to (throwable.message ?: "Unknown error"),
                    "error_type" to (throwable::class.simpleName ?: "Unknown")
                )
            )
            flowOf(liftrixFailure(error))
        }
    }

    /**
     * Checks if notification preferences exist for the current user.
     * 
     * This method provides a quick way to determine if preferences have been
     * configured without retrieving the full preferences object.
     * 
     * @return LiftrixResult<Boolean> indicating if preferences exist
     */
    suspend fun hasNotificationPreferences(): LiftrixResult<Boolean> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "CHECK_NOTIFICATION_PREFERENCES_FAILED",
                errorMessage = "Failed to check if notification preferences exist",
                analyticsContext = mapOf(
                    "error" to (throwable.message ?: "Unknown error"),
                    "error_type" to (throwable::class.simpleName ?: "Unknown")
                )
            )
        }
    ) {
        val userId = getCurrentUserIdUseCase()
            ?: throw IllegalStateException("User not authenticated")

        timber.log.Timber.d("Checking if notification preferences exist for user: $userId")

        // Use repository to check if preferences exist
        // Since we don't have a direct method, we'll use the flow to check
        val hasPrefs = try {
            notificationRepository.getNotificationPreferences(userId).first() != null
        } catch (e: Exception) {
            timber.log.Timber.w(e, "Error checking preferences existence for user: $userId")
            false
        }

        timber.log.Timber.d("Notification preferences exist for user $userId: $hasPrefs")
        hasPrefs
    }
}