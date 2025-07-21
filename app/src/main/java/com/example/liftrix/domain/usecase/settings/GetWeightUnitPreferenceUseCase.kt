package com.example.liftrix.domain.usecase.settings

import com.example.liftrix.domain.model.WeightUnit
import com.example.liftrix.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Use case for retrieving the user's weight unit preference.
 * Returns the user's preferred weight unit or system default if not set.
 */
class GetWeightUnitPreferenceUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    
    /**
     * Gets the user's weight unit preference as a reactive stream.
     * 
     * @param userId The ID of the user
     * @return Flow that emits the user's preferred WeightUnit
     */
    operator fun invoke(userId: String): Flow<WeightUnit> {
        return settingsRepository.getUserSettings(userId).map { settings ->
            settings?.weightUnit ?: WeightUnit.getSystemDefault()
        }
    }
}