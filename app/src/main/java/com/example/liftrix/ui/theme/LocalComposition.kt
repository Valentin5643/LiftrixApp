package com.example.liftrix.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import com.example.liftrix.domain.service.WeightUnitManager
import com.example.liftrix.ui.common.LocalWeightUnitManager as SharedLocalWeightUnitManager


/**
 * CompositionLocal providers for the Liftrix app.
 * 
 * This file provides dependency injection for services throughout the Compose tree,
 * enabling reactive updates for settings like weight units that need to be consistent
 * across all screens.
 * 
 * Critical for resolving the weight unit persistence issue by ensuring all components
 * have access to the current weight unit preference and automatic conversion.
 */

/**
 * CompositionLocal for the WeightUnitManager service.
 * 
 * Provides access to weight unit conversion and formatting throughout the Compose tree.
 * Essential for the WeightDisplay components to work properly and maintain consistency
 * with user preferences.
 */
val LocalWeightUnitManager = compositionLocalOf<WeightUnitManager?> { null }

/**
 * Provider component that injects the WeightUnitManager into the composition tree.
 * 
 * This should be placed at a high level in the app's composition hierarchy,
 * typically in the main activity or navigation container, to ensure all screens
 * have access to weight unit functionality.
 * 
 * @param weightUnitManager The WeightUnitManager instance to provide
 * @param content The composable content that will have access to the manager
 */
@Composable
fun ProvideWeightUnitManager(
    weightUnitManager: WeightUnitManager,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalWeightUnitManager provides weightUnitManager,
        SharedLocalWeightUnitManager provides weightUnitManager,
        content = content
    )
}

/**
 * Convenience function to access the WeightUnitManager from any composable.
 * 
 * @return The WeightUnitManager if provided, or null if not available
 */
@Composable
fun rememberWeightUnitManager(): WeightUnitManager? {
    return LocalWeightUnitManager.current
}

/**
 * Enhanced weight display utility that automatically uses the provided WeightUnitManager.
 * 
 * This is a convenience function that combines accessing the WeightUnitManager from
 * CompositionLocal with formatted weight display.
 * 
 * @param weight The weight value in its stored unit
 * @param storedUnit The unit the weight is stored in
 * @param precision Number of decimal places (default: 1)
 * @return Formatted weight string that updates when preferences change
 */
@Composable
fun rememberFormattedWeight(
    weight: Double,
    storedUnit: com.example.liftrix.domain.model.WeightUnit,
    precision: Int = 1
): String {
    val weightUnitManager = rememberWeightUnitManager()
    return if (weightUnitManager != null) {
        weightUnitManager.rememberWeightDisplay(weight, storedUnit, precision)
    } else {
        "${if (precision == 0) weight.toInt().toString() else "%.${precision}f".format(weight)} ${storedUnit.symbol}"
    }
}

/**
 * Enhanced compact weight display utility.
 * 
 * @param weight The weight value in its stored unit
 * @param storedUnit The unit the weight is stored in
 * @return Compact formatted weight string
 */
@Composable
fun rememberFormattedWeightCompact(
    weight: Double,
    storedUnit: com.example.liftrix.domain.model.WeightUnit
): String {
    val weightUnitManager = rememberWeightUnitManager()
    return if (weightUnitManager != null) {
        weightUnitManager.rememberWeightDisplayCompact(weight, storedUnit)
    } else {
        "${weight.toInt()} ${storedUnit.symbol}"
    }
}

/**
 * Get the current weight unit symbol reactively.
 * 
 * @return The current unit symbol (e.g., "kg", "lbs")
 */
@Composable
fun rememberCurrentWeightUnitSymbol(): String {
    val weightUnitManager = rememberWeightUnitManager()
    return weightUnitManager?.rememberCurrentUnitSymbol() 
        ?: com.example.liftrix.domain.model.WeightUnit.getSystemDefault().symbol
}

/**
 * Get the current weight unit display name reactively.
 * 
 * @return The current unit display name (e.g., "Kilograms", "Pounds")
 */
@Composable
fun rememberCurrentWeightUnitDisplayName(): String {
    val weightUnitManager = rememberWeightUnitManager()
    return weightUnitManager?.rememberCurrentUnitDisplayName()
        ?: com.example.liftrix.domain.model.WeightUnit.getSystemDefault().displayName
}
