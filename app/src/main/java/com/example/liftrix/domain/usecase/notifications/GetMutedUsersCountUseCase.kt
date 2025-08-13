package com.example.liftrix.domain.usecase.notifications

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.common.liftrixFailure
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.NotificationRepository
import com.example.liftrix.domain.usecase.auth.GetCurrentUserIdUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

/**
 * Use case for retrieving the count of muted users for notification settings display.
 * 
 * This use case provides reactive access to the number of users that have been muted
 * by the current user. It's primarily used in the notification settings screen to show
 * users how many people they've muted and provide access to mute management.
 * 
 * Key responsibilities:
 * - Provides real-time count of muted users for UI display
 * - Handles authentication validation before data retrieval
 * - Manages error states gracefully with fallback to zero count
 * - Supports both reactive streams and one-time queries
 * - Tracks analytics for mute usage patterns
 */
class GetMutedUsersCountUseCase @Inject constructor(
    private val notificationRepository: NotificationRepository,
    private val getCurrentUserIdUseCase: GetCurrentUserIdUseCase
) {

    /**
     * Retrieves the count of muted users for the current authenticated user.
     * 
     * This method provides continuous updates to the muted users count with proper
     * error handling and graceful fallback to zero when errors occur. The count
     * updates in real-time as users are muted or unmuted.
     * 
     * @return Flow<LiftrixResult<Int>> that emits the muted users count or error details
     */
    suspend operator fun invoke(): Flow<LiftrixResult<Int>> {
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

            timber.log.Timber.d("Retrieving muted users count for user: $userId")

            // Get muted users count from repository as reactive stream
            notificationRepository.getMutedUsersCount(userId)
                .map { count ->
                    timber.log.Timber.d("Muted users count for user $userId: $count")
                    LiftrixResult.success(count)
                }
                .catch { throwable ->
                    val error = LiftrixError.BusinessLogicError(
                        code = "GET_MUTED_USERS_COUNT_FAILED",
                        errorMessage = "Failed to retrieve muted users count",
                        analyticsContext = mapOf(
                            "error" to (throwable.message ?: "Unknown error"),
                            "error_type" to (throwable::class.simpleName ?: "Unknown")
                        )
                    )
                    emit(liftrixFailure(error))
                }
        } catch (throwable: Throwable) {
            val error = LiftrixError.BusinessLogicError(
                code = "GET_MUTED_USERS_COUNT_FAILED",
                errorMessage = "Failed to retrieve muted users count",
                analyticsContext = mapOf(
                    "error" to (throwable.message ?: "Unknown error"),
                    "error_type" to (throwable::class.simpleName ?: "Unknown")
                )
            )
            flowOf(liftrixFailure(error))
        }
    }

    /**
     * Retrieves the count of muted users for a specific user.
     * 
     * This method is useful for admin operations or when checking muted users
     * count for users other than the current authenticated user.
     * 
     * @param userId The ID of the user whose muted users count to retrieve
     * @return Flow<LiftrixResult<Int>> that emits the muted users count
     */
    suspend fun getMutedUsersCountForUser(userId: String): Flow<LiftrixResult<Int>> {
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

            timber.log.Timber.d("Retrieving muted users count for specific user: $userId")

            notificationRepository.getMutedUsersCount(userId)
                .map { count ->
                    timber.log.Timber.d("Muted users count for user $userId: $count")
                    LiftrixResult.success(count)
                }
                .catch { throwable ->
                    val error = LiftrixError.BusinessLogicError(
                        code = "GET_USER_MUTED_USERS_COUNT_FAILED",
                        errorMessage = "Failed to retrieve muted users count for user",
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
                code = "GET_USER_MUTED_USERS_COUNT_FAILED",
                errorMessage = "Failed to retrieve muted users count for user",
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
     * Gets a one-time snapshot of the muted users count for the current user.
     * 
     * This method provides a single count value instead of a reactive stream,
     * useful for one-time checks or when continuous updates aren't needed.
     * 
     * @return LiftrixResult<Int> containing the current muted users count
     */
    suspend fun getCurrentCount(): LiftrixResult<Int> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "GET_CURRENT_MUTED_COUNT_FAILED",
                errorMessage = "Failed to get current muted users count",
                analyticsContext = mapOf(
                    "error" to (throwable.message ?: "Unknown error"),
                    "error_type" to (throwable::class.simpleName ?: "Unknown")
                )
            )
        }
    ) {
        val userId = getCurrentUserIdUseCase()
            ?: throw IllegalStateException("User not authenticated")

        timber.log.Timber.d("Getting current muted users count for user: $userId")

        // Get the first emission from the reactive stream
        val count = try {
            notificationRepository.getMutedUsersCount(userId).first()
        } catch (e: Exception) {
            timber.log.Timber.w(e, "Error getting current muted count for user: $userId, returning 0")
            0 // Graceful fallback to zero
        }

        timber.log.Timber.d("Current muted users count for user $userId: $count")
        count
    }

    /**
     * Checks if the current user has any muted users.
     * 
     * This is a convenience method that returns a boolean indicating whether
     * the user has muted anyone, useful for conditional UI display.
     * 
     * @return LiftrixResult<Boolean> indicating if any users are muted
     */
    suspend fun hasAnyMutedUsers(): LiftrixResult<Boolean> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "CHECK_HAS_MUTED_USERS_FAILED",
                errorMessage = "Failed to check if user has muted users",
                analyticsContext = mapOf(
                    "error" to (throwable.message ?: "Unknown error"),
                    "error_type" to (throwable::class.simpleName ?: "Unknown")
                )
            )
        }
    ) {
        val countResult = getCurrentCount()
        countResult.fold(
            onSuccess = { count -> count > 0 },
            onFailure = { error ->
                timber.log.Timber.w("Failed to get muted count, assuming no muted users: $error")
                false // Graceful fallback
            }
        )
    }

    /**
     * Retrieves detailed muted users information for management UI.
     * 
     * This method provides the full list of muted user IDs, which can be used
     * to build a management interface where users can view and unmute specific users.
     * 
     * @return Flow<LiftrixResult<List<String>>> that emits the list of muted user IDs
     */
    suspend fun getMutedUsersList(): Flow<LiftrixResult<List<String>>> {
        return try {
            val userId = getCurrentUserIdUseCase()
                ?: return flowOf(
                    liftrixFailure(
                        LiftrixError.BusinessLogicError(
                            code = "USER_NOT_AUTHENTICATED",
                            errorMessage = "User not authenticated"
                        )
                    )
                )

            timber.log.Timber.d("Retrieving muted users list for user: $userId")

            notificationRepository.getMutedUsers(userId)
                .map { mutedUsers ->
                    timber.log.Timber.d("Retrieved ${mutedUsers.size} muted users for user: $userId")
                    LiftrixResult.success(mutedUsers)
                }
                .catch { throwable ->
                    val error = LiftrixError.BusinessLogicError(
                        code = "GET_MUTED_USERS_LIST_FAILED",
                        errorMessage = "Failed to retrieve muted users list",
                        analyticsContext = mapOf(
                            "error" to (throwable.message ?: "Unknown error"),
                            "error_type" to (throwable::class.simpleName ?: "Unknown")
                        )
                    )
                    emit(liftrixFailure(error))
                }
        } catch (throwable: Throwable) {
            val error = LiftrixError.BusinessLogicError(
                code = "GET_MUTED_USERS_LIST_FAILED",
                errorMessage = "Failed to retrieve muted users list",
                analyticsContext = mapOf(
                    "error" to (throwable.message ?: "Unknown error"),
                    "error_type" to (throwable::class.simpleName ?: "Unknown")
                )
            )
            flowOf(liftrixFailure(error))
        }
    }
}