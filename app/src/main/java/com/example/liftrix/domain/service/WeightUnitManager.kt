package com.example.liftrix.domain.service

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import com.example.liftrix.domain.model.WeightUnit
import com.example.liftrix.domain.repository.SettingsRepository
import com.example.liftrix.domain.usecase.auth.AuthQueryUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Centralized manager for weight unit conversion and formatting.
 * 
 * Provides reactive weight unit management with caching and automatic updates
 * when user preferences change. Essential for ensuring weight displays remain
 * consistent across all screens without hardcoding units.
 * 
 * Key features:
 * - Reactive weight unit observation
 * - Cached conversion calculations
 * - Compose-friendly display helpers
 * - Automatic preference synchronization
 */
@Singleton
class WeightUnitManager @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val authQueryUseCase: AuthQueryUseCase
) {
    
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    private val _currentUnit = MutableStateFlow(WeightUnit.getSystemDefault())
    val currentUnit: StateFlow<WeightUnit> = _currentUnit.asStateFlow()
    
    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()
    
    // Cache for recent conversions to improve performance
    private val conversionCache = mutableMapOf<String, Double>()
    private var lastCacheClear = System.currentTimeMillis()
    
    init {
        startUnitObservation()
    }
    
    /**
     * Initializes the weight unit manager for the current user.
     * Should be called when user authentication state is available.
     */
    suspend fun initialize() {
        try {
            val userId = authQueryUseCase(waitForAuth = false).getOrNull() ?: return
            val userSettings = settingsRepository.getUserSettings(userId.value)
                .filterNotNull()
                .map { it.weightUnit }
                .distinctUntilChanged()
            
            serviceScope.launch {
                userSettings.collect { unit ->
                    _currentUnit.value = unit
                    clearConversionCache()
                    Timber.d("Weight unit updated to: ${unit.name}")
                }
            }
            
            _isInitialized.value = true
            Timber.d("WeightUnitManager initialized for user: $userId")
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize WeightUnitManager")
            // Fall back to system default
            _currentUnit.value = WeightUnit.getSystemDefault()
            _isInitialized.value = true
        }
    }
    
    /**
     * Converts a weight value from its stored unit to the current display unit.
     * 
     * @param value The weight value in the stored unit
     * @param storedUnit The unit the value is stored in
     * @return The converted weight value for display
     */
    fun convertForDisplay(value: Double, storedUnit: WeightUnit): Double {
        val currentDisplayUnit = _currentUnit.value
        
        // Fast path for no conversion needed
        if (storedUnit == currentDisplayUnit) return value
        
        // Check cache first
        val cacheKey = "$value:${storedUnit.name}:${currentDisplayUnit.name}"
        conversionCache[cacheKey]?.let { cachedResult ->
            return cachedResult
        }
        
        // Perform conversion
        val converted = WeightUnitConverter.convert(value, storedUnit, currentDisplayUnit)
        
        // Cache the result (with periodic cleanup)
        if (conversionCache.size < 1000) { // Prevent unbounded cache growth
            conversionCache[cacheKey] = converted
        } else if (System.currentTimeMillis() - lastCacheClear > 60_000) { // Clear cache every minute
            clearConversionCache()
            conversionCache[cacheKey] = converted
        }
        
        return converted
    }
    
    /**
     * Formats a weight value with appropriate precision and unit symbol.
     * 
     * @param value The weight value in the stored unit
     * @param storedUnit The unit the value is stored in
     * @param precision Number of decimal places (default: 1)
     * @return Formatted weight string with unit symbol
     */
    fun formatWeight(value: Double, storedUnit: WeightUnit, precision: Int = 1): String {
        val displayValue = convertForDisplay(value, storedUnit)
        val displayUnit = _currentUnit.value
        
        return if (precision == 0 || displayValue == displayValue.toInt().toDouble()) {
            "${displayValue.toInt()} ${displayUnit.symbol}"
        } else {
            "${"%.${precision}f".format(displayValue)} ${displayUnit.symbol}"
        }
    }
    
    /**
     * Formats weight for display without decimal places when appropriate.
     * 
     * @param value The weight value in the stored unit
     * @param storedUnit The unit the value is stored in
     * @return Formatted weight string optimized for readability
     */
    fun formatWeightCompact(value: Double, storedUnit: WeightUnit): String {
        val displayValue = convertForDisplay(value, storedUnit)
        val displayUnit = _currentUnit.value
        
        return when {
            displayValue == displayValue.toInt().toDouble() -> {
                "${displayValue.toInt()} ${displayUnit.symbol}"
            }
            displayValue < 10 -> {
                "${"%.1f".format(displayValue)} ${displayUnit.symbol}"
            }
            else -> {
                "${"%.0f".format(displayValue)} ${displayUnit.symbol}"
            }
        }
    }
    
    /**
     * Converts a weight value from the current display unit back to the storage unit.
     * Useful for processing user input.
     * 
     * @param displayValue The weight value in display units
     * @param targetStorageUnit The unit to store the value in
     * @return The converted weight value for storage
     */
    fun convertFromDisplay(displayValue: Double, targetStorageUnit: WeightUnit): Double {
        val currentDisplayUnit = _currentUnit.value
        return WeightUnitConverter.convert(displayValue, currentDisplayUnit, targetStorageUnit)
    }
    
    /**
     * Gets the current weight unit synchronously.
     * Safe to use in Compose contexts.
     */
    fun getCurrentUnit(): WeightUnit = _currentUnit.value
    
    /**
     * Gets the display symbol for the current unit (e.g., "kg", "lbs").
     */
    fun getCurrentUnitSymbol(): String = _currentUnit.value.symbol
    
    /**
     * Gets the display name for the current unit (e.g., "Kilograms", "Pounds").
     */
    fun getCurrentUnitDisplayName(): String = _currentUnit.value.displayName
    
    /**
     * Compose utility to remember a formatted weight display that updates reactively.
     * 
     * @param value The weight value in the stored unit
     * @param storedUnit The unit the value is stored in
     * @param precision Number of decimal places (default: 1)
     * @return Formatted weight string that updates when preferences change
     */
    @Composable
    fun rememberWeightDisplay(
        value: Double, 
        storedUnit: WeightUnit,
        precision: Int = 1
    ): String {
        val unit = currentUnit.collectAsState()
        return remember(value, storedUnit, unit.value, precision) {
            formatWeight(value, storedUnit, precision)
        }
    }
    
    /**
     * Compose utility to remember a compact weight display that updates reactively.
     * 
     * @param value The weight value in the stored unit
     * @param storedUnit The unit the value is stored in
     * @return Compact formatted weight string that updates when preferences change
     */
    @Composable
    fun rememberWeightDisplayCompact(value: Double, storedUnit: WeightUnit): String {
        val unit = currentUnit.collectAsState()
        return remember(value, storedUnit, unit.value) {
            formatWeightCompact(value, storedUnit)
        }
    }
    
    /**
     * Compose utility to get the current unit symbol reactively.
     */
    @Composable
    fun rememberCurrentUnitSymbol(): String {
        val unit = currentUnit.collectAsState()
        return unit.value.symbol
    }
    
    /**
     * Compose utility to get the current unit display name reactively.
     */
    @Composable
    fun rememberCurrentUnitDisplayName(): String {
        val unit = currentUnit.collectAsState()
        return unit.value.displayName
    }
    
    // Private helper methods
    
    private fun startUnitObservation() {
        serviceScope.launch {
            // Observe both user changes and initialization status
            combine(_isInitialized, currentUnit) { initialized, unit ->
                initialized to unit
            }.collect { (initialized, unit) ->
                if (initialized) {
                    Timber.v("Current weight unit: ${unit.name} (${unit.symbol})")
                }
            }
        }
    }
    
    private fun clearConversionCache() {
        conversionCache.clear()
        lastCacheClear = System.currentTimeMillis()
        Timber.v("Weight conversion cache cleared")
    }
}

/**
 * Utility object for weight unit conversions.
 * Extracted for testing and reusability.
 */
object WeightUnitConverter {
    
    /**
     * Converts weight between different units.
     * 
     * @param value The weight value to convert
     * @param from The source unit
     * @param to The target unit
     * @return The converted weight value
     */
    fun convert(value: Double, from: WeightUnit, to: WeightUnit): Double {
        if (from == to) return value
        
        return when {
            from == WeightUnit.KILOGRAMS && to == WeightUnit.POUNDS -> value * 2.20462
            from == WeightUnit.POUNDS && to == WeightUnit.KILOGRAMS -> value / 2.20462
            else -> {
                // Future-proof: convert through kg as base unit if more units are added
                val kgValue = if (from == WeightUnit.KILOGRAMS) value else value / 2.20462
                if (to == WeightUnit.KILOGRAMS) kgValue else kgValue * 2.20462
            }
        }
    }
    
    /**
     * Formats weight with proper precision and unit symbol.
     * 
     * @param value The weight value
     * @param unit The weight unit
     * @param precision Number of decimal places
     * @return Formatted weight string
     */
    fun formatWeight(value: Double, unit: WeightUnit, precision: Int = 1): String {
        return if (precision == 0 || value == value.toInt().toDouble()) {
            "${value.toInt()} ${unit.symbol}"
        } else {
            "${"%.${precision}f".format(value)} ${unit.symbol}"
        }
    }
    
    /**
     * Validates that a conversion makes sense (no negative weights, reasonable bounds).
     * 
     * @param value The weight value to validate
     * @return True if the weight is valid
     */
    fun isValidWeight(value: Double): Boolean {
        return value >= 0.0 && value <= 10000.0 // Reasonable upper bound
    }
    
    /**
     * Rounds weight to appropriate precision for the given unit.
     * 
     * @param value The weight value to round
     * @param unit The weight unit (some units may prefer different precision)
     * @return Rounded weight value
     */
    fun roundForUnit(value: Double, unit: WeightUnit): Double {
        return when (unit) {
            WeightUnit.KILOGRAMS -> {
                // Round to nearest 0.1 kg
                (value * 10).let { kotlin.math.round(it) } / 10.0
            }
            WeightUnit.POUNDS -> {
                // Round to nearest 0.5 lbs for heavy weights, 0.1 lbs for light weights
                if (value > 50) {
                    (value * 2).let { kotlin.math.round(it) } / 2.0
                } else {
                    (value * 10).let { kotlin.math.round(it) } / 10.0
                }
            }
        }
    }
}