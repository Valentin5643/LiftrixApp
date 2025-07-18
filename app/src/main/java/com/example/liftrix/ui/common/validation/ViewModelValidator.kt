package com.example.liftrix.ui.common.validation

import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber

/**
 * Utility for validating ViewModel state and preventing common initialization issues.
 * 
 * This validator helps catch ViewModel configuration problems early and provides
 * detailed error reporting for debugging dependency injection issues.
 */
object ViewModelValidator {
    
    /**
     * Validates that a StateFlow is properly initialized and accessible.
     * 
     * @param stateFlow The StateFlow to validate
     * @param viewModelName Name of the ViewModel for error reporting
     * @return true if valid, false if null or inaccessible
     */
    fun <T> validateStateFlow(
        stateFlow: StateFlow<T>?,
        viewModelName: String
    ): Boolean {
        return try {
            when {
                stateFlow == null -> {
                    Timber.e("$viewModelName: StateFlow is null - check ViewModel initialization")
                    false
                }
                else -> {
                    // Try to access the current value
                    stateFlow.value
                    Timber.d("$viewModelName: StateFlow validation passed")
                    true
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "$viewModelName: StateFlow validation failed - ${e.message}")
            false
        }
    }
    
    /**
     * Validates multiple ViewModels and their StateFlows for a screen.
     * 
     * @param validationPairs List of ViewModel name to StateFlow pairs
     * @return ValidationResult with overall status and details
     */
    fun validateViewModels(
        vararg validationPairs: Pair<String, StateFlow<*>?>
    ): ValidationResult {
        val failedViewModels = mutableListOf<String>()
        var totalValidated = 0
        
        validationPairs.forEach { (name, stateFlow) ->
            totalValidated++
            if (!validateStateFlow(stateFlow, name)) {
                failedViewModels.add(name)
            }
        }
        
        val isValid = failedViewModels.isEmpty()
        val message = if (isValid) {
            "All $totalValidated ViewModels validated successfully"
        } else {
            "Failed ViewModels: ${failedViewModels.joinToString(", ")}"
        }
        
        return ValidationResult(
            isValid = isValid,
            message = message,
            failedViewModels = failedViewModels,
            totalValidated = totalValidated
        )
    }
    
    /**
     * Result of ViewModel validation containing detailed information.
     */
    data class ValidationResult(
        val isValid: Boolean,
        val message: String,
        val failedViewModels: List<String>,
        val totalValidated: Int
    ) {
        fun logResult() {
            if (isValid) {
                Timber.i("ViewModel Validation: $message")
            } else {
                Timber.e("ViewModel Validation Failed: $message")
            }
        }
    }
}