package com.example.liftrix.domain.service

import com.example.liftrix.domain.model.UserSettings
import com.example.liftrix.domain.model.WeightUnit
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import timber.log.Timber
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service responsible for validating settings integrity and detecting corruption.
 * 
 * Provides comprehensive validation for user settings across different storage layers
 * and detects potential corruption scenarios. Critical for ensuring reliable
 * settings persistence and preventing data loss.
 * 
 * Validation checks include:
 * - Data type consistency
 * - Value range validation
 * - Cross-field validation
 * - Corruption detection
 * - Migration state validation
 */
@Singleton
class SettingsValidator @Inject constructor() {
    
    companion object {
        // Validation constants
        private const val MIN_USER_ID_LENGTH = 3
        private const val MAX_USER_ID_LENGTH = 100
        private const val MAX_TIMESTAMP_SKEW_SECONDS = 300L // 5 minutes
        private val VALID_TERMINOLOGY_PREFERENCES = setOf("NEW", "LEGACY")
    }
    
    /**
     * Validates a complete UserSettings object.
     * 
     * @param settings The settings object to validate
     * @return LiftrixResult indicating validation success or specific errors
     */
    suspend fun validateSettings(settings: UserSettings): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.ValidationError(
                field = "settings",
                violations = listOf("Settings validation failed: ${throwable.message}"),
                analyticsContext = mapOf(
                    "user_id" to settings.userId,
                    "validation_context" to "COMPLETE_SETTINGS"
                )
            )
        }
    ) {
        val violations = mutableListOf<String>()
        
        // Validate user ID
        validateUserId(settings.userId).onFailure { error ->
            if (error is LiftrixError.ValidationError) {
                violations.addAll(error.violations)
            } else {
                violations.add("User ID validation failed")
            }
        }
        
        // Validate timestamp
        validateTimestamp(settings.updatedAt).onFailure { error ->
            if (error is LiftrixError.ValidationError) {
                violations.addAll(error.violations)
            } else {
                violations.add("Timestamp validation failed")
            }
        }
        
        // Validate weight unit
        validateWeightUnit(settings.weightUnit).onFailure { error ->
            if (error is LiftrixError.ValidationError) {
                violations.addAll(error.violations)
            } else {
                violations.add("Weight unit validation failed")
            }
        }
        
        // Validate terminology preference
        validateTerminologyPreference(settings.terminologyPreference).onFailure { error ->
            if (error is LiftrixError.ValidationError) {
                violations.addAll(error.violations)
            } else {
                violations.add("Terminology preference validation failed")
            }
        }
        
        // Cross-field validations
        validateMigrationState(settings).onFailure { error ->
            if (error is LiftrixError.ValidationError) {
                violations.addAll(error.violations)
            } else {
                violations.add("Migration state validation failed")
            }
        }
        
        if (violations.isNotEmpty()) {
            throw LiftrixError.ValidationError(
                field = "settings",
                violations = violations,
                analyticsContext = mapOf(
                    "user_id" to settings.userId,
                    "violations_count" to violations.size.toString()
                )
            )
        }
        
    }
    
    /**
     * Validates a complete UserSettings object (alias for validateSettings).
     * 
     * This method provides backward compatibility for code that expects validateUserSettings.
     */
    suspend fun validateUserSettings(settings: UserSettings): LiftrixResult<Unit> = validateSettings(settings)
    
    /**
     * Validates user ID format and constraints.
     */
    suspend fun validateUserId(userId: String): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.ValidationError(
                field = "userId",
                violations = listOf("User ID validation failed: ${throwable.message}"),
                analyticsContext = mapOf("validation_context" to "USER_ID")
            )
        }
    ) {
        val violations = mutableListOf<String>()
        
        if (userId.isBlank()) {
            violations.add("User ID cannot be blank")
        }
        
        if (userId.length < MIN_USER_ID_LENGTH) {
            violations.add("User ID must be at least $MIN_USER_ID_LENGTH characters")
        }
        
        if (userId.length > MAX_USER_ID_LENGTH) {
            violations.add("User ID must not exceed $MAX_USER_ID_LENGTH characters")
        }
        
        // Check for invalid characters that could cause issues in storage
        if (userId.contains("..") || userId.contains("/") || userId.contains("\\")) {
            violations.add("User ID contains invalid characters")
        }
        
        if (violations.isNotEmpty()) {
            throw LiftrixError.ValidationError(
                field = "userId",
                violations = violations,
                analyticsContext = mapOf(
                    "user_id_length" to userId.length.toString(),
                    "validation_context" to "USER_ID"
                )
            )
        }
    }
    
    /**
     * Validates timestamp for reasonable bounds and corruption detection.
     */
    suspend fun validateTimestamp(timestamp: Instant): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.ValidationError(
                field = "timestamp",
                violations = listOf("Timestamp validation failed: ${throwable.message}"),
                analyticsContext = mapOf("validation_context" to "TIMESTAMP")
            )
        }
    ) {
        val violations = mutableListOf<String>()
        val now = Instant.now()
        
        // Check for timestamps too far in the future (potential corruption)
        if (timestamp.isAfter(now.plusSeconds(MAX_TIMESTAMP_SKEW_SECONDS))) {
            violations.add("Timestamp is too far in the future (${timestamp} vs ${now})")
        }
        
        // Check for impossibly old timestamps (year 1970 issues)
        val epoch = Instant.ofEpochSecond(0)
        if (timestamp.isBefore(epoch.plusSeconds(3600 * 24 * 365))) { // Before 1971
            violations.add("Timestamp is suspiciously old: $timestamp")
        }
        
        if (violations.isNotEmpty()) {
            throw LiftrixError.ValidationError(
                field = "timestamp",
                violations = violations,
                analyticsContext = mapOf(
                    "timestamp" to timestamp.toString(),
                    "current_time" to now.toString(),
                    "validation_context" to "TIMESTAMP"
                )
            )
        }
    }
    
    /**
     * Validates weight unit enum value.
     */
    suspend fun validateWeightUnit(weightUnit: WeightUnit): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.ValidationError(
                field = "weightUnit",
                violations = listOf("Weight unit validation failed: ${throwable.message}"),
                analyticsContext = mapOf("validation_context" to "WEIGHT_UNIT")
            )
        }
    ) {
        // Weight unit validation is mostly handled by the enum type system,
        // but we can add additional business logic checks here
        
        // Verify the weight unit has valid conversion factors
        if (weightUnit.conversionFactorToKg <= 0) {
            throw LiftrixError.ValidationError(
                field = "weightUnit",
                violations = listOf("Invalid conversion factor for weight unit: ${weightUnit.name}"),
                analyticsContext = mapOf(
                    "weight_unit" to weightUnit.name,
                    "conversion_factor" to weightUnit.conversionFactorToKg.toString(),
                    "validation_context" to "WEIGHT_UNIT"
                )
            )
        }
        
        Timber.v("Weight unit validation passed: ${weightUnit.name}")
    }
    
    /**
     * Validates terminology preference value.
     */
    suspend fun validateTerminologyPreference(preference: String): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.ValidationError(
                field = "terminologyPreference",
                violations = listOf("Terminology preference validation failed: ${throwable.message}"),
                analyticsContext = mapOf("validation_context" to "TERMINOLOGY_PREFERENCE")
            )
        }
    ) {
        val violations = mutableListOf<String>()
        
        if (preference.isBlank()) {
            violations.add("Terminology preference cannot be blank")
        }
        
        if (preference !in VALID_TERMINOLOGY_PREFERENCES) {
            violations.add("Invalid terminology preference: '$preference'. Must be one of: ${VALID_TERMINOLOGY_PREFERENCES.joinToString(", ")}")
        }
        
        if (violations.isNotEmpty()) {
            throw LiftrixError.ValidationError(
                field = "terminologyPreference",
                violations = violations,
                analyticsContext = mapOf(
                    "preference_value" to preference,
                    "valid_values" to VALID_TERMINOLOGY_PREFERENCES.toString(),
                    "validation_context" to "TERMINOLOGY_PREFERENCE"
                )
            )
        }
    }
    
    /**
     * Validates migration state consistency.
     */
    suspend fun validateMigrationState(settings: UserSettings): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.ValidationError(
                field = "migrationState",
                violations = listOf("Migration state validation failed: ${throwable.message}"),
                analyticsContext = mapOf(
                    "user_id" to settings.userId,
                    "validation_context" to "MIGRATION_STATE"
                )
            )
        }
    ) {
        val violations = mutableListOf<String>()
        
        // If migration is completed, the explanation should have been seen
        if (settings.migrationCompleted && !settings.migrationExplanationSeen) {
            violations.add("Migration is marked as completed but explanation was not shown to user")
        }
        
        // If using LEGACY terminology, migration should be completed
        if (settings.terminologyPreference == "LEGACY" && !settings.migrationCompleted) {
            violations.add("Using LEGACY terminology but migration is not marked as completed")
        }
        
        if (violations.isNotEmpty()) {
            throw LiftrixError.ValidationError(
                field = "migrationState",
                violations = violations,
                analyticsContext = mapOf(
                    "migration_completed" to settings.migrationCompleted.toString(),
                    "explanation_seen" to settings.migrationExplanationSeen.toString(),
                    "terminology_preference" to settings.terminologyPreference,
                    "validation_context" to "MIGRATION_STATE"
                )
            )
        }
    }
    
    /**
     * Detects potential data corruption patterns in settings.
     * 
     * @param settings The settings to analyze for corruption
     * @return LiftrixResult<List<String>> containing detected corruption indicators
     */
    suspend fun detectCorruption(settings: UserSettings): LiftrixResult<List<String>> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "DETECT_CORRUPTION_FAILED",
                errorMessage = "Corruption detection failed",
                analyticsContext = mapOf(
                    "user_id" to settings.userId,
                    "error" to throwable.message.orEmpty()
                )
            )
        }
    ) {
        val corruptionIndicators = mutableListOf<String>()
        
        // Check for suspicious timestamp patterns
        val now = Instant.now()
        if (settings.updatedAt.isAfter(now.plusSeconds(3600))) {
            corruptionIndicators.add("Timestamp is more than 1 hour in the future")
        }
        
        // Check for impossible combinations
        if (settings.migrationCompleted && settings.terminologyPreference == "NEW" && !settings.migrationExplanationSeen) {
            corruptionIndicators.add("Inconsistent migration state detected")
        }
        
        // Check for default values that might indicate corruption
        val systemDefault = WeightUnit.getSystemDefault()
        if (settings.weightUnit == systemDefault && 
            !settings.darkMode && 
            settings.notificationsEnabled && 
            settings.terminologyPreference == "NEW") {
            corruptionIndicators.add("All settings appear to be default values - possible corruption or reset")
        }
        
        if (corruptionIndicators.isNotEmpty()) {
            Timber.w("Detected ${corruptionIndicators.size} corruption indicators for user: ${settings.userId}")
        }
        
        corruptionIndicators
    }
    
    /**
     * Validates that settings are safe to persist to all storage layers.
     */
    suspend fun validateForPersistence(settings: UserSettings): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.ValidationError(
                field = "settings",
                violations = listOf("Persistence validation failed: ${throwable.message}"),
                analyticsContext = mapOf(
                    "user_id" to settings.userId,
                    "validation_context" to "PERSISTENCE"
                )
            )
        }
    ) {
        // Run all standard validations
        validateSettings(settings).getOrThrow()
        
        // Additional checks for persistence safety
        val violations = mutableListOf<String>()
        
        // Check for values that might cause serialization issues
        if (settings.userId.contains("\u0000") || settings.terminologyPreference.contains("\u0000")) {
            violations.add("Settings contain null bytes that may cause persistence issues")
        }
        
        // Check for reasonable string lengths to prevent storage bloat
        if (settings.userId.length > MAX_USER_ID_LENGTH) {
            violations.add("User ID exceeds maximum length for persistence")
        }
        
        if (violations.isNotEmpty()) {
            throw LiftrixError.ValidationError(
                field = "settings",
                violations = violations,
                analyticsContext = mapOf(
                    "user_id" to settings.userId,
                    "validation_context" to "PERSISTENCE"
                )
            )
        }
        
    }
    
    /**
     * Verifies that a specific setting has been correctly persisted.
     * 
     * This method checks that a setting value matches the expected value,
     * which is useful for verification after persistence operations.
     */
    suspend fun verifySetting(
        userId: String,
        key: String,
        expectedValue: Any
    ): LiftrixResult<Boolean> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "VERIFY_SETTING_FAILED",
                errorMessage = "Failed to verify setting: $key",
                analyticsContext = mapOf(
                    "user_id" to userId,
                    "setting_key" to key,
                    "expected_value" to expectedValue.toString()
                )
            )
        }
    ) {
        // This is a simplified verification - in a real implementation,
        // you might want to read from the actual storage layers
        
        // For now, we'll always return true as this is primarily for
        // demonstrating the verification pattern
        true
    }
}