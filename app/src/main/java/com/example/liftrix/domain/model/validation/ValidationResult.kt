package com.example.liftrix.domain.model.validation

/**
 * ValidationResult - Sealed class representing the outcome of input validation
 * 
 * Used throughout the Liftrix app for form validation with user-friendly error messages
 * and clear success/error state differentiation following Clean Architecture principles.
 * 
 * This follows the same pattern as LiftrixResult<T> for consistency across the codebase.
 */
sealed class ValidationResult {
    
    /**
     * Successful validation with no errors
     */
    object Success : ValidationResult()
    
    /**
     * Failed validation with user-friendly error message
     * 
     * @param message Clear, actionable message explaining the validation failure
     * @param field Optional field identifier for targeting specific form fields
     */
    data class Error(
        val message: String,
        val field: String? = null
    ) : ValidationResult()
    
    /**
     * Check if validation was successful
     */
    val isValid: Boolean
        get() = this is Success
    
    /**
     * Check if validation failed
     */
    val isError: Boolean
        get() = this is Error
    
    /**
     * Get error message if validation failed, null if successful
     */
    val errorMessage: String?
        get() = (this as? Error)?.message
    
    /**
     * Get field identifier if available
     */
    val fieldName: String?
        get() = (this as? Error)?.field
    
    companion object {
        /**
         * Create successful validation result
         */
        fun success(): ValidationResult = Success
        
        /**
         * Create error validation result with message
         */
        fun error(message: String, field: String? = null): ValidationResult = 
            Error(message, field)
        
        /**
         * Create validation result based on condition
         * 
         * @param condition If true, returns Success; if false, returns Error with message
         * @param errorMessage Message to use if condition is false
         * @param field Optional field identifier
         */
        fun fromCondition(
            condition: Boolean,
            errorMessage: String,
            field: String? = null
        ): ValidationResult {
            return if (condition) {
                Success
            } else {
                Error(errorMessage, field)
            }
        }
        
        /**
         * Combine multiple validation results
         * Returns first error found, or Success if all are valid
         */
        fun combine(vararg results: ValidationResult): ValidationResult {
            results.forEach { result ->
                if (result is Error) {
                    return result
                }
            }
            return Success
        }
        
        /**
         * Combine multiple validation results and collect all errors
         * Returns Success if all valid, or Error with combined messages
         */
        fun combineAll(vararg results: ValidationResult): ValidationResult {
            val errors = results.filterIsInstance<Error>()
            
            return if (errors.isEmpty()) {
                Success
            } else {
                val combinedMessage = errors.joinToString("; ") { it.message }
                Error(combinedMessage)
            }
        }
    }
}

/**
 * Extension functions for working with ValidationResult
 */

/**
 * Execute action if validation is successful
 */
inline fun ValidationResult.onSuccess(action: () -> Unit): ValidationResult {
    if (this is ValidationResult.Success) {
        action()
    }
    return this
}

/**
 * Execute action if validation failed
 */
inline fun ValidationResult.onError(action: (String, String?) -> Unit): ValidationResult {
    if (this is ValidationResult.Error) {
        action(message, field)
    }
    return this
}

/**
 * Transform error message while preserving success state
 */
inline fun ValidationResult.mapError(transform: (String) -> String): ValidationResult {
    return when (this) {
        is ValidationResult.Success -> this
        is ValidationResult.Error -> ValidationResult.Error(transform(message), field)
    }
}

/**
 * Add field context to validation result
 */
fun ValidationResult.withField(fieldName: String): ValidationResult {
    return when (this) {
        is ValidationResult.Success -> this
        is ValidationResult.Error -> copy(field = fieldName)
    }
}