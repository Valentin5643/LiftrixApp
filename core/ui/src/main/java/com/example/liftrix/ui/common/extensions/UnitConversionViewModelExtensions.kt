package com.example.liftrix.ui.common.extensions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.liftrix.domain.model.WeightUnit
import com.example.liftrix.domain.service.UnitConversionService
import com.example.liftrix.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted

/**
 * ViewModel extensions for unit conversion integration.
 * Part of Firebase sync infrastructure from SPEC-20250118-firebase-sync-social.
 * 
 * Provides reactive unit conversion capabilities that automatically adapt
 * to user's synced preferences for seamless UI updates.
 */

/**
 * Extension function to create a StateFlow that emits formatted weight strings
 * based on user's weight unit preferences from synced settings
 */
fun ViewModel.observeFormattedWeight(
    weightKgFlow: Flow<Double>,
    userId: String,
    unitConversionService: UnitConversionService,
    settingsRepository: SettingsRepository,
    precision: Int = 1
): StateFlow<String> {
    return combine(
        weightKgFlow,
        settingsRepository.observeWeightUnit(userId)
    ) { weightKg, weightUnit ->
        unitConversionService.formatWeight(weightKg, weightUnit, precision)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = "0 kg"
    )
}

/**
 * Extension function to create a StateFlow that emits converted weight values
 * in user's preferred unit
 */
fun ViewModel.observeConvertedWeight(
    weightKgFlow: Flow<Double>,
    userId: String,
    settingsRepository: SettingsRepository
): StateFlow<Double> {
    return combine(
        weightKgFlow,
        settingsRepository.observeWeightUnit(userId)
    ) { weightKg, weightUnit ->
        when (weightUnit) {
            WeightUnit.KILOGRAMS -> weightKg
            WeightUnit.POUNDS -> weightKg * 2.20462
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0.0
    )
}

/**
 * Extension function to create a StateFlow that emits the current weight unit
 */
fun ViewModel.observeWeightUnit(
    userId: String,
    settingsRepository: SettingsRepository
): StateFlow<WeightUnit> {
    return settingsRepository.observeWeightUnit(userId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = WeightUnit.getSystemDefault()
        )
}

/**
 * Extension function to format a list of weights reactively
 */
fun ViewModel.observeFormattedWeightList(
    weightsKgFlow: Flow<List<Double>>,
    userId: String,
    unitConversionService: UnitConversionService,
    settingsRepository: SettingsRepository,
    precision: Int = 1
): StateFlow<List<String>> {
    return combine(
        weightsKgFlow,
        settingsRepository.observeWeightUnit(userId)
    ) { weightsKg, weightUnit ->
        weightsKg.map { weightKg ->
            unitConversionService.formatWeight(weightKg, weightUnit, precision)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )
}

/**
 * Creates a formatted weight string for immediate use (non-reactive)
 */
suspend fun formatWeightForDisplay(
    weightKg: Double,
    userId: String,
    unitConversionService: UnitConversionService,
    precision: Int = 1
): String {
    val weightUnit = unitConversionService.getCurrentWeightUnit(userId)
    return unitConversionService.formatWeight(weightKg, weightUnit, precision)
}

/**
 * Converts user input to kilograms for storage
 */
suspend fun convertUserInputToKg(
    userInput: Double,
    userId: String,
    unitConversionService: UnitConversionService
): Double {
    val weightUnit = unitConversionService.getCurrentWeightUnit(userId)
    return unitConversionService.userUnitToKg(userInput, weightUnit)
}

/**
 * Converts stored kg value to user's preferred unit for display
 */
suspend fun convertKgToUserUnit(
    weightKg: Double,
    userId: String,
    unitConversionService: UnitConversionService
): Double {
    val weightUnit = unitConversionService.getCurrentWeightUnit(userId)
    return unitConversionService.kgToUserUnit(weightKg, weightUnit)
}

/**
 * Extension to get current weight unit symbol reactively
 */
fun ViewModel.observeWeightUnitSymbol(
    userId: String,
    settingsRepository: SettingsRepository
): StateFlow<String> {
    return settingsRepository.observeWeightUnit(userId)
        .map { it.symbol }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = WeightUnit.getSystemDefault().symbol
        )
}

/**
 * Extension to get weight unit from coordinatorPreferences if available
 */
fun getWeightUnitFromPreferences(
    coordinatorPreferences: Map<String, Any>,
    fallback: WeightUnit = WeightUnit.getSystemDefault()
): WeightUnit {
    return coordinatorPreferences["weightUnit"] as? WeightUnit ?: fallback
}

/**
 * Extension to format weight using coordinator preferences
 */
fun formatWeightWithPreferences(
    weightKg: Double,
    coordinatorPreferences: Map<String, Any>,
    unitConversionService: UnitConversionService,
    precision: Int = 1
): String {
    val weightUnit = getWeightUnitFromPreferences(coordinatorPreferences)
    return unitConversionService.formatWeight(weightKg, weightUnit, precision)
}

/**
 * Extension to get weight unit symbol from coordinator preferences
 */
fun getWeightUnitSymbolFromPreferences(
    coordinatorPreferences: Map<String, Any>
): String {
    val weightUnit = getWeightUnitFromPreferences(coordinatorPreferences)
    return weightUnit.symbol
}