package com.example.liftrix.core.security

import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * JSON Input Validator for preventing injection attacks and ensuring safe JSON processing.
 *
 * Provides comprehensive validation of JSON input before parsing to prevent:
 * - JSON injection attacks
 * - Memory exhaustion from oversized JSON
 * - Stack overflow from deeply nested JSON
 * - Malformed JSON that could cause parsing errors
 */
@Singleton
class JsonInputValidator @Inject constructor() {

    companion object {
        // Security limits for JSON input
        const val MAX_JSON_SIZE_BYTES = 1024 * 1024 * 2 // 2MB limit for workout JSON
        const val MAX_JSON_DEPTH = 20 // Maximum nesting depth
        const val MAX_ARRAY_SIZE = 1000 // Maximum array elements (exercises/sets)
        const val MAX_STRING_LENGTH = 10000 // Maximum string length for any field

        // Dangerous patterns that could indicate injection attempts
        private val DANGEROUS_PATTERNS = listOf(
            "javascript:",
            "<script",
            "eval(",
            "function(",
            "window.",
            "document.",
            "__proto__",
            "prototype",
            "constructor",
            "import(",
            "require(",
            "process."
        )
    }

    /**
     * Validates JSON input for security and size constraints
     */
    fun validateJson(input: String?): ValidationResult {
        if (input.isNullOrBlank()) {
            return ValidationResult.Valid(input ?: "")
        }

        // 1. Size validation
        if (input.length > MAX_JSON_SIZE_BYTES) {
            return ValidationResult.Invalid("JSON size exceeds maximum limit of ${MAX_JSON_SIZE_BYTES} bytes")
        }

        // 2. Dangerous pattern detection
        val lowercaseInput = input.lowercase()
        DANGEROUS_PATTERNS.forEach { pattern ->
            if (lowercaseInput.contains(pattern)) {
                Timber.w("JsonInputValidator: Detected dangerous pattern '$pattern' in JSON input")
                return ValidationResult.Invalid("JSON contains potentially dangerous content")
            }
        }

        // 3. Basic JSON syntax validation
        try {
            val jsonElement = JsonParser.parseString(input)

            // 4. Depth validation
            val depth = calculateJsonDepth(jsonElement)
            if (depth > MAX_JSON_DEPTH) {
                return ValidationResult.Invalid("JSON nesting depth exceeds maximum of $MAX_JSON_DEPTH")
            }

            // 5. Array size validation
            val arrayValidation = validateArraySizes(jsonElement)
            if (arrayValidation is ValidationResult.Invalid) {
                return arrayValidation
            }

            // 6. String length validation
            val stringValidation = validateStringLengths(jsonElement)
            if (stringValidation is ValidationResult.Invalid) {
                return stringValidation
            }

            return ValidationResult.Valid(input)

        } catch (e: JsonSyntaxException) {
            Timber.w(e, "JsonInputValidator: Invalid JSON syntax")
            return ValidationResult.Invalid("Invalid JSON syntax: ${e.message}")
        } catch (e: Exception) {
            Timber.e(e, "JsonInputValidator: Unexpected error during validation")
            return ValidationResult.Invalid("JSON validation failed: ${e.message}")
        }
    }

    /**
     * Sanitizes JSON input by removing dangerous patterns and enforcing limits
     */
    fun sanitizeJson(input: String?): String? {
        if (input.isNullOrBlank()) return input

        var sanitized = input

        // Remove dangerous patterns
        DANGEROUS_PATTERNS.forEach { pattern ->
            sanitized = sanitized?.replace(pattern, "", ignoreCase = true)
        }

        // Truncate if too large
        if (sanitized?.length ?: 0 > MAX_JSON_SIZE_BYTES) {
            sanitized = sanitized?.substring(0, MAX_JSON_SIZE_BYTES)
            Timber.w("JsonInputValidator: Truncated JSON input to maximum size limit")
        }

        return sanitized
    }

    private fun calculateJsonDepth(element: com.google.gson.JsonElement, currentDepth: Int = 0): Int {
        return when {
            element.isJsonObject -> {
                val obj = element.asJsonObject
                var maxDepth = currentDepth
                obj.entrySet().forEach { (_, value) ->
                    val childDepth = calculateJsonDepth(value, currentDepth + 1)
                    maxDepth = maxOf(maxDepth, childDepth)
                }
                maxDepth
            }
            element.isJsonArray -> {
                val array = element.asJsonArray
                var maxDepth = currentDepth
                array.forEach { value ->
                    val childDepth = calculateJsonDepth(value, currentDepth + 1)
                    maxDepth = maxOf(maxDepth, childDepth)
                }
                maxDepth
            }
            else -> currentDepth
        }
    }

    private fun validateArraySizes(element: com.google.gson.JsonElement): ValidationResult {
        return when {
            element.isJsonArray -> {
                val array = element.asJsonArray
                if (array.size() > MAX_ARRAY_SIZE) {
                    ValidationResult.Invalid("Array size ${array.size()} exceeds maximum of $MAX_ARRAY_SIZE")
                } else {
                    // Recursively validate nested arrays
                    array.forEach { child ->
                        val childValidation = validateArraySizes(child)
                        if (childValidation is ValidationResult.Invalid) {
                            return childValidation
                        }
                    }
                    ValidationResult.Valid("")
                }
            }
            element.isJsonObject -> {
                val obj = element.asJsonObject
                obj.entrySet().forEach { (_, value) ->
                    val childValidation = validateArraySizes(value)
                    if (childValidation is ValidationResult.Invalid) {
                        return childValidation
                    }
                }
                ValidationResult.Valid("")
            }
            else -> ValidationResult.Valid("")
        }
    }

    private fun validateStringLengths(element: com.google.gson.JsonElement): ValidationResult {
        return when {
            element.isJsonPrimitive && element.asJsonPrimitive.isString -> {
                val str = element.asString
                if (str.length > MAX_STRING_LENGTH) {
                    ValidationResult.Invalid("String length ${str.length} exceeds maximum of $MAX_STRING_LENGTH")
                } else {
                    ValidationResult.Valid("")
                }
            }
            element.isJsonObject -> {
                val obj = element.asJsonObject
                obj.entrySet().forEach { (key, value) ->
                    // Validate both key and value
                    if (key.length > MAX_STRING_LENGTH) {
                        return ValidationResult.Invalid("Object key length exceeds maximum of $MAX_STRING_LENGTH")
                    }
                    val childValidation = validateStringLengths(value)
                    if (childValidation is ValidationResult.Invalid) {
                        return childValidation
                    }
                }
                ValidationResult.Valid("")
            }
            element.isJsonArray -> {
                val array = element.asJsonArray
                array.forEach { child ->
                    val childValidation = validateStringLengths(child)
                    if (childValidation is ValidationResult.Invalid) {
                        return childValidation
                    }
                }
                ValidationResult.Valid("")
            }
            else -> ValidationResult.Valid("")
        }
    }

    /**
     * Result of JSON validation
     */
    sealed class ValidationResult {
        data class Valid(val json: String) : ValidationResult()
        data class Invalid(val reason: String) : ValidationResult()
    }
}