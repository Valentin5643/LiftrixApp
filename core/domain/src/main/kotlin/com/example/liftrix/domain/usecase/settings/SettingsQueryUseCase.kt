package com.example.liftrix.domain.usecase.settings

import com.example.liftrix.domain.model.SubscriptionStatus
import com.example.liftrix.domain.model.UserSettings
import com.example.liftrix.domain.model.WeightUnit
import com.example.liftrix.domain.repository.SettingsRepository
import com.example.liftrix.domain.repository.SubscriptionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import com.example.liftrix.domain.util.DomainLogger as Timber
import javax.inject.Inject

/**
 * Consolidated query use case for settings information retrieval.
 *
 * Replaces:
 * - GetUserSettingsUseCase.kt
 * - GetWeightUnitPreferenceUseCase.kt
 * - GetSubscriptionStatusUseCase.kt
 *
 * Provides methods for retrieving user settings, weight unit preference, and subscription status.
 * All operations include proper error handling and reactive Flow-based patterns.
 *
 * @property settingsRepository Repository for settings data access
 * @property subscriptionRepository Repository for subscription data access
 */
class SettingsQueryUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val subscriptionRepository: SubscriptionRepository
) {

    /**
     * Retrieves user settings as a reactive stream wrapped in Result.
     * Replaces GetUserSettingsUseCase.invoke()
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
     * Gets the user's weight unit preference as a reactive stream.
     * Replaces GetWeightUnitPreferenceUseCase.invoke()
     *
     * @param userId The ID of the user
     * @return Flow that emits the user's preferred WeightUnit
     */
    fun getWeightUnitPreference(userId: String): Flow<WeightUnit> {
        return settingsRepository.getUserSettings(userId).map { settings ->
            settings?.weightUnit ?: WeightUnit.getSystemDefault()
        }
    }

    /**
     * Retrieves subscription status as a reactive stream wrapped in Result.
     * Replaces GetSubscriptionStatusUseCase.invoke()
     *
     * @param userId The ID of the user whose subscription status to retrieve
     * @return Flow<Result<SubscriptionStatus>> that emits subscription updates or errors
     */
    suspend fun getSubscriptionStatus(userId: String): Flow<Result<SubscriptionStatus>> {
        return try {
            if (userId.isBlank()) {
                Timber.e("Attempted to get subscription status with blank user ID")
                throw IllegalArgumentException("User ID cannot be blank")
            }

            Timber.d("Getting subscription status for user: $userId")

            subscriptionRepository.getSubscriptionStatus(userId)
                .map { subscription ->
                    if (subscription != null) {
                        Timber.d("Subscription retrieved successfully for user: $userId")
                        Result.success(subscription.status)
                    } else {
                        Timber.d("No subscription found for user: $userId, returning default tier")
                        Result.success(SubscriptionStatus.default())
                    }
                }
                .catch { exception ->
                    Timber.e(exception, "Error retrieving subscription status for user: $userId")
                    emit(Result.failure(exception))
                }
        } catch (e: Exception) {
            Timber.e(e, "Exception while setting up subscription status retrieval for user: $userId")
            throw e
        }
    }

    /**
     * Checks if settings exist for a user.
     * Replaces GetUserSettingsUseCase.hasUserSettings()
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
     * Checks if user has active premium subscription.
     * Replaces GetSubscriptionStatusUseCase.hasActivePremiumSubscription()
     *
     * @param userId The ID of the user to check
     * @return Flow<Result<Boolean>> indicating premium status
     */
    suspend fun hasActivePremiumSubscription(userId: String): Flow<Result<Boolean>> {
        return try {
            if (userId.isBlank()) {
                Timber.e("Attempted to check premium status with blank user ID")
                throw IllegalArgumentException("User ID cannot be blank")
            }

            Timber.d("Checking premium status for user: $userId")

            subscriptionRepository.hasActivePremiumSubscription(userId)
                .map { hasPremium ->
                    Timber.d("Premium status for user $userId: $hasPremium")
                    Result.success(hasPremium)
                }
                .catch { exception ->
                    Timber.e(exception, "Error checking premium status for user: $userId")
                    emit(Result.failure(exception))
                }
        } catch (e: Exception) {
            Timber.e(e, "Exception while checking premium status for user: $userId")
            throw e
        }
    }

    /**
     * Forces synchronization of settings from DataStore to Room.
     * Replaces GetUserSettingsUseCase.syncUserSettings()
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

    /**
     * Forces synchronization of subscription status with Google Play Billing.
     * Replaces GetSubscriptionStatusUseCase.syncSubscriptionStatus()
     *
     * @param userId The ID of the user whose subscription to sync
     * @return Result<Unit> indicating success or failure of the sync operation
     */
    suspend fun syncSubscriptionStatus(userId: String): Result<Unit> {
        return try {
            if (userId.isBlank()) {
                val error = IllegalArgumentException("User ID cannot be blank")
                Timber.e("Attempted to sync subscription with blank user ID")
                return Result.failure(error)
            }

            Timber.d("Syncing subscription status for user: $userId")
            val result = subscriptionRepository.syncWithGooglePlay(userId)

            if (result.isSuccess) {
                Timber.d("Subscription synced successfully for user: $userId")
            } else {
                Timber.e("Failed to sync subscription for user: $userId")
            }

            result
        } catch (e: Exception) {
            Timber.e(e, "Exception while syncing subscription for user: $userId")
            Result.failure(e)
        }
    }
}
