package com.example.liftrix.domain.usecase.settings

import com.example.liftrix.domain.model.WeightUnit
import com.example.liftrix.domain.repository.SettingsRepository
import javax.inject.Inject

/**
 * Use case for updating the user's weight unit preference.
 * Validates the input and updates the user's settings.
 */
class UpdateWeightUnitPreferenceUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    
    /**
     * Updates the user's weight unit preference.
     * 
     * @param userId The ID of the user
     * @param weightUnit The new weight unit preference
     * @return Result indicating success or failure
     */
    suspend operator fun invoke(userId: String, weightUnit: WeightUnit): Result<Unit> {
        return try {
            // Validate input
            require(userId.isNotBlank()) { "User ID cannot be blank" }
            
            // Update the preference
            settingsRepository.updateWeightUnit(userId, weightUnit)
            
        } catch (e: IllegalArgumentException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(RuntimeException("Failed to update weight unit preference", e))
        }
    }
}