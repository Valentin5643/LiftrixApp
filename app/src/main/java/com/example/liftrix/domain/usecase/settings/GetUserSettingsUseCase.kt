package com.example.liftrix.domain.usecase.settings

import com.example.liftrix.domain.model.UserSettings
import com.example.liftrix.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject

/**
 * Use case for retrieving user settings with proper error handling and caching strategy.
 * 
 * This use case provides reactive access to user settings data through Flow streams,
 * handling errors gracefully and providing appropriate fallback behavior.
 * 
 * The use case follows the single responsibility principle by focusing solely on
 * settings retrieval while delegating persistence logic to the repository layer.
 */
class GetUserSettingsUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository
) {

    /**
     * Retrieves user settings as a reactive stream wrapped in Result.
     * 
     * This method provides continuous updates to settings changes, with proper error
     * handling that wraps exceptions in Result.failure() for graceful degradation.
     * 
     * @param userId The ID of the user whose settings to retrieve
     * @return Flow<Result<UserSettings>> that emits settings updates or errors
     */
    suspend operator fun invoke(userId: String): Flow<Result<UserSettings>> {
        return try {
            if (userId.isBlank()) {
                Timber.e("Attempted to get settings with blank user ID")
                throw IllegalArgumentException("User ID cannot be blank")
            }

            Timber.d("Getting settings for user: $userId")
            
            settingsRepository.getUserSettings(userId)
                .map { settings ->
                    if (settings != null) {
                        Timber.d("Settings retrieved successfully for user: $userId")
                        Result.success(settings)
                    } else {
                        Timber.d("No settings found for user: $userId, returning defaults")
                        Result.success(UserSettings.createDefault(userId))
                    }
                }
                .catch { exception ->
                    Timber.e(exception, "Error retrieving settings for user: $userId")
                    emit(Result.failure(exception))
                }
        } catch (e: Exception) {
            Timber.e(e, "Exception while setting up settings retrieval for user: $userId")
            throw e
        }
    }

    /**
     * Checks if settings exist for a user.
     * 
     * This method provides a quick way to determine if a user has configured settings
     * without retrieving the full settings object.
     * 
     * @param userId The ID of the user to check
     * @return True if settings exist, false otherwise
     */
    suspend fun hasUserSettings(userId: String): Boolean {
        return try {
            if (userId.isBlank()) {
                Timber.e("Attempted to check settings existence with blank user ID")
                return false
            }

            val hasSettings = settingsRepository.hasSettings(userId)
            Timber.d("Settings exist for user $userId: $hasSettings")
            hasSettings
        } catch (e: Exception) {
            Timber.e(e, "Exception while checking settings existence for user: $userId")
            false
        }
    }

    /**
     * Forces synchronization of settings from DataStore to Room.
     * 
     * This method ensures that the Room database is up-to-date with the latest
     * settings from DataStore, useful for offline scenarios or cache consistency.
     * 
     * @param userId The ID of the user whose settings to sync
     * @return Result<Unit> indicating success or failure of the sync operation
     */
    suspend fun syncUserSettings(userId: String): Result<Unit> {
        return try {
            if (userId.isBlank()) {
                val error = IllegalArgumentException("User ID cannot be blank")
                Timber.e("Attempted to sync settings with blank user ID")
                return Result.failure(error)
            }

            Timber.d("Syncing settings for user: $userId")
            val result = settingsRepository.syncSettings(userId)
            
            if (result.isSuccess) {
                Timber.d("Settings synced successfully for user: $userId")
            } else {
                Timber.e("Failed to sync settings for user: $userId")
            }
            
            result
        } catch (e: Exception) {
            Timber.e(e, "Exception while syncing settings for user: $userId")
            Result.failure(e)
        }
    }
}