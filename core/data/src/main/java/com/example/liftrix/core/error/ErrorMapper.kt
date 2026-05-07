package com.example.liftrix.core.error

import com.example.liftrix.domain.model.error.LiftrixError

/**
 * Enum representing the severity level of an error for UI display purposes.
 * 
 * Used to determine appropriate visual styling, user messaging tone,
 * and urgency of user response required.
 */
enum class ErrorSeverity {
    /** Informational errors that don't prevent user workflow continuation */
    INFO,
    
    /** Warning errors that require attention but allow continued use */
    WARNING,
    
    /** Critical errors that prevent normal operation and require immediate attention */
    ERROR
}

/**
 * Utility class for mapping LiftrixError instances to user-friendly messages.
 * 
 * Converts technical error details into appropriate user-facing messages that provide
 * helpful guidance without exposing internal implementation details. Messages are
 * designed to be actionable and encourage appropriate user responses.
 */
object ErrorMapper {
    
    /**
     * Maps a LiftrixError to a user-friendly message string.
     * 
     * Provides contextual, actionable error messages that help users understand
     * what went wrong and what they can do about it.
     * 
     * @param error The LiftrixError to convert to a user message
     * @return User-friendly error message string
     */
    fun mapToUserMessage(error: LiftrixError): String {
        return when (error) {
            is LiftrixError.NetworkError -> {
                when {
                    error.httpStatusCode != null -> {
                        when (error.httpStatusCode) {
                            400 -> "Invalid request. Please check your input and try again."
                            401 -> "Your session has expired. Please sign in again."
                            403 -> "You don't have permission to perform this action."
                            404 -> "The requested information could not be found."
                            408 -> "Request timed out. Please check your connection and try again."
                            429 -> "Too many requests. Please wait a moment and try again."
                            500, 502, 503 -> "Server is temporarily unavailable. Please try again later."
                            else -> "Network error occurred. Please check your connection and try again."
                        }
                    }
                    error.networkType != null -> {
                        "Connection failed on ${error.networkType}. Please check your network settings."
                    }
                    else -> "Unable to connect. Please check your internet connection and try again."
                }
            }
            
            is LiftrixError.ValidationError -> {
                val fieldName = error.field.replace("_", " ").replaceFirstChar { it.uppercase() }
                val primaryViolation = error.violations.firstOrNull() ?: "Invalid input"
                
                when {
                    error.violations.size == 1 -> "$fieldName: $primaryViolation"
                    error.violations.size > 1 -> {
                        "$fieldName has ${error.violations.size} issues. $primaryViolation"
                    }
                    else -> "$fieldName is invalid. Please check your input."
                }
            }
            
            is LiftrixError.AuthenticationError -> {
                when (error.errorCode) {
                    "SIGN_IN_FAILED" -> "Sign in failed. Please check your credentials and try again."
                    "TOKEN_EXPIRED" -> "Your session has expired. Please sign in again."
                    "ACCOUNT_DISABLED" -> "Your account has been disabled. Please contact support."
                    "TOO_MANY_REQUESTS" -> "Too many sign in attempts. Please wait before trying again."
                    "NETWORK_ERROR" -> "Sign in failed due to network issues. Please try again."
                    "INVALID_CREDENTIALS" -> "Invalid email or password. Please try again."
                    "EMAIL_NOT_VERIFIED" -> "Please verify your email address before signing in."
                    else -> when (error.authProvider) {
                        "Google" -> "Google sign in failed. Please try again or use a different method."
                        "Facebook" -> "Facebook sign in failed. Please try again or use a different method."
                        "Apple" -> "Apple sign in failed. Please try again or use a different method."
                        else -> "Authentication failed. Please try signing in again."
                    }
                }
            }
            
            is LiftrixError.DatabaseError -> {
                when {
                    error.sqlErrorCode != null -> {
                        when (error.sqlErrorCode) {
                            1062 -> "This item already exists. Please try a different name."
                            1451 -> "Cannot delete this item because it's being used elsewhere."
                            1452 -> "Cannot save because required information is missing."
                            else -> "Unable to save your changes. Please try again."
                        }
                    }
                    error.operation != null -> {
                        val operation = error.operation
                        when (operation?.uppercase()) {
                            "INSERT" -> "Unable to save new item. Please try again."
                            "UPDATE" -> "Unable to save changes. Please try again."
                            "DELETE" -> "Unable to delete item. Please try again."
                            "SELECT" -> "Unable to load data. Please refresh and try again."
                            else -> "Database operation failed. Please try again."
                        }
                    }
                    else -> "Unable to save your changes. Please try again."
                }
            }
            
            is LiftrixError.BusinessLogicError -> {
                when (error.code) {
                    "WORKOUT_ALREADY_STARTED" -> "A workout is already in progress. Please finish it before starting a new one."
                    "TEMPLATE_NOT_FOUND" -> "The workout template could not be found. It may have been deleted."
                    "EXERCISE_NOT_AVAILABLE" -> "This exercise is not available. Please choose a different one."
                    "INVALID_SET_DATA" -> "Invalid workout data. Please check your sets and reps."
                    "PREMIUM_FEATURE_REQUIRED" -> "This feature requires a premium subscription. Upgrade to continue."
                    "MAX_WORKOUTS_REACHED" -> "You've reached the maximum number of workouts for your plan."
                    "TEMPLATE_LIMIT_EXCEEDED" -> "You've reached the maximum number of templates allowed."
                    "EXPORT_SIZE_LIMIT" -> "Export file is too large. Please select fewer workouts."
                    "INVALID_DATE_RANGE" -> "Please select a valid date range for your data."
                    "CONCURRENT_MODIFICATION" -> "This item was modified by another session. Please refresh and try again."
                    else -> "Unable to complete this action. Please try again or contact support."
                }
            }
            
            is LiftrixError.CalculationError -> {
                when (error.operation) {
                    "volume_calculation" -> "Unable to calculate workout volume. Please check your data and try again."
                    "progress_calculation" -> "Unable to calculate progress metrics. Please try again."
                    "analytics_calculation" -> "Unable to generate analytics. Please try again later."
                    else -> "Calculation failed. Please check your data and try again."
                }
            }
            
            is LiftrixError.DataRetrievalError -> {
                when (error.operation) {
                    "getWidgetData" -> "Unable to load widget data. Please try again."
                    "getVolumeData" -> "Unable to load volume data. Please try again."
                    "getDurationData" -> "Unable to load duration data. Please try again."
                    "getFrequencyData" -> "Unable to load frequency data. Please try again."
                    "getProgressSummary" -> "Unable to load progress summary. Please try again."
                    else -> "Unable to load data. Please try again."
                }
            }
            
            is LiftrixError.ConfigurationError -> {
                when (error.configKey) {
                    "feature_flag" -> "Unable to load feature configuration. Please try again."
                    "user_preferences" -> "Unable to load user preferences. Please try again."
                    "app_settings" -> "Unable to load app settings. Please try again."
                    else -> "Configuration error. Please try again."
                }
            }
            
            is LiftrixError.ExportError -> {
                when (error.format) {
                    "CSV" -> "Unable to export data to CSV format. Please try again."
                    "JSON" -> "Unable to export data to JSON format. Please try again."
                    "PDF" -> "Unable to generate PDF report. Please try again."
                    else -> "Export failed. Please check your data and try again."
                }
            }
            
            is LiftrixError.FileSystemError -> {
                when (error.operation) {
                    "READ" -> "Unable to read file. Please check file permissions and try again."
                    "WRITE" -> "Unable to save file. Please check available storage and try again."
                    "DELETE" -> "Unable to delete file. Please check file permissions and try again."
                    else -> "File operation failed. Please check file permissions and try again."
                }
            }
            
            is LiftrixError.NotFoundError -> {
                val resourceName = error.resourceType?.let { type ->
                    type.replace("_", " ").replaceFirstChar { it.uppercase() }
                } ?: "Resource"
                
                when (error.resourceType) {
                    "workout" -> "Workout not found. It may have been deleted or moved."
                    "exercise" -> "Exercise not found. Please try selecting a different exercise."
                    "template" -> "Template not found. It may have been deleted or moved."
                    "user" -> "User account not found. Please check your credentials."
                    else -> "$resourceName not found. Please check and try again."
                }
            }
            
            is LiftrixError.PermissionError -> {
                when {
                    error.permission != null -> {
                        "Permission denied: ${error.permission}. Please check your permissions and try again."
                    }
                    else -> "You don't have permission to perform this action."
                }
            }
            
            is LiftrixError.CacheError -> {
                when {
                    error.operation != null -> {
                        "Cache ${error.operation} failed. Please try again."
                    }
                    else -> "Cache operation failed. Please try again."
                }
            }
            
            is LiftrixError.UnknownError -> {
                "An unexpected error occurred. Please try again or contact support if the problem persists."
            }
        }
    }
    
    /**
     * Maps a LiftrixError to a brief title for error display.
     * 
     * Provides short, descriptive titles for error dialogs and notifications.
     * 
     * @param error The LiftrixError to get a title for
     * @return Brief error title string
     */
    fun mapToErrorTitle(error: LiftrixError): String {
        return when (error) {
            is LiftrixError.NetworkError -> "Connection Error"
            is LiftrixError.ValidationError -> "Invalid Input"
            is LiftrixError.AuthenticationError -> "Sign In Error"
            is LiftrixError.DatabaseError -> "Save Error"
            is LiftrixError.BusinessLogicError -> "Action Unavailable"
            is LiftrixError.CalculationError -> "Calculation Error"
            is LiftrixError.DataRetrievalError -> "Data Loading Error"
            is LiftrixError.ConfigurationError -> "Configuration Error"
            is LiftrixError.ExportError -> "Export Error"
            is LiftrixError.FileSystemError -> "File Error"
            is LiftrixError.NotFoundError -> "Not Found"
            is LiftrixError.PermissionError -> "Permission Denied"
            is LiftrixError.CacheError -> "Cache Error"
            is LiftrixError.UnknownError -> "Unexpected Error"
        }
    }
    
    /**
     * Determines if the error should be displayed to the user.
     * 
     * Some errors are technical or internal and should not be shown to users,
     * while others require user attention and action.
     * 
     * @param error The LiftrixError to evaluate
     * @return true if the error should be displayed to the user, false otherwise
     */
    fun shouldShowToUser(error: LiftrixError): Boolean {
        return when (error) {
            is LiftrixError.NetworkError -> true
            is LiftrixError.ValidationError -> true
            is LiftrixError.AuthenticationError -> true
            is LiftrixError.DatabaseError -> {
                // Show user-facing database errors, hide technical ones
                error.sqlErrorCode in listOf(1062, 1451, 1452) || error.operation != null
            }
            is LiftrixError.BusinessLogicError -> true
            is LiftrixError.CalculationError -> true
            is LiftrixError.DataRetrievalError -> true
            is LiftrixError.ConfigurationError -> true
            is LiftrixError.ExportError -> true
            is LiftrixError.FileSystemError -> true
            is LiftrixError.NotFoundError -> true
            is LiftrixError.PermissionError -> true
            is LiftrixError.CacheError -> true
            is LiftrixError.UnknownError -> true
        }
    }
    
    /**
     * Maps error to suggested user actions.
     * 
     * Provides actionable suggestions for users to resolve or work around the error.
     * 
     * @param error The LiftrixError to get suggestions for
     * @return List of suggested user actions
     */
    fun mapToSuggestions(error: LiftrixError): List<String> {
        return when (error) {
            is LiftrixError.NetworkError -> listOf(
                "Check your internet connection",
                "Try again in a few moments",
                "Switch to a different network if available"
            )
            
            is LiftrixError.ValidationError -> listOf(
                "Review the highlighted fields",
                "Ensure all required information is provided",
                "Check for valid formats (email, phone, etc.)"
            )
            
            is LiftrixError.AuthenticationError -> when (error.errorCode) {
                "TOO_MANY_REQUESTS" -> listOf("Wait a few minutes before trying again")
                "EMAIL_NOT_VERIFIED" -> listOf("Check your email for verification link")
                else -> listOf(
                    "Check your email and password",
                    "Try signing in with a different method",
                    "Reset your password if needed"
                )
            }
            
            is LiftrixError.DatabaseError -> listOf(
                "Try saving again",
                "Check that all required fields are filled",
                "Contact support if the problem continues"
            )
            
            is LiftrixError.BusinessLogicError -> when (error.code) {
                "PREMIUM_FEATURE_REQUIRED" -> listOf("Upgrade to premium to access this feature")
                "WORKOUT_ALREADY_STARTED" -> listOf("Finish your current workout first")
                else -> listOf(
                    "Try the action again",
                    "Contact support if needed"
                )
            }
            
            is LiftrixError.CalculationError -> listOf(
                "Check your workout data for completeness",
                "Try refreshing the analytics view",
                "Contact support if calculations seem incorrect"
            )
            
            is LiftrixError.DataRetrievalError -> listOf(
                "Pull to refresh the data",
                "Check your internet connection",
                "Try again in a few moments"
            )
            
            is LiftrixError.ConfigurationError -> listOf(
                "Check your app settings",
                "Restart the app",
                "Try again in a few moments"
            )
            
            is LiftrixError.ExportError -> listOf(
                "Check available storage space",
                "Try selecting fewer items to export",
                "Try a different export format"
            )
            
            is LiftrixError.FileSystemError -> listOf(
                "Check file permissions",
                "Ensure sufficient storage space",
                "Try restarting the app"
            )
            
            is LiftrixError.NotFoundError -> listOf(
                "Refresh the page",
                "Try searching for the item",
                "Go back and try again"
            )
            
            is LiftrixError.PermissionError -> listOf(
                "Check your permissions",
                "Sign in again if needed",
                "Contact your administrator"
            )
            
            is LiftrixError.CacheError -> listOf(
                "Try again",
                "Clear app cache",
                "Restart the app"
            )
            
            is LiftrixError.UnknownError -> listOf(
                "Try the action again",
                "Restart the app if the problem continues",
                "Contact support with details about what you were doing"
            )
        }
    }
    
    /**
     * Determines the severity level of an error for UI display purposes.
     * 
     * Maps error types to appropriate severity levels to guide UI rendering
     * decisions such as color schemes, animation urgency, and user attention patterns.
     * 
     * @param error The LiftrixError to evaluate
     * @return ErrorSeverity level for the error
     */
    fun getErrorSeverity(error: LiftrixError): ErrorSeverity {
        return when (error) {
            is LiftrixError.NetworkError -> {
                when {
                    error.httpStatusCode in 400..499 -> ErrorSeverity.WARNING
                    error.httpStatusCode in 500..599 -> ErrorSeverity.ERROR
                    else -> ErrorSeverity.WARNING
                }
            }
            
            is LiftrixError.ValidationError -> ErrorSeverity.INFO
            
            is LiftrixError.AuthenticationError -> {
                when (error.errorCode) {
                    "TOKEN_EXPIRED", "INVALID_CREDENTIALS" -> ErrorSeverity.WARNING
                    "ACCOUNT_DISABLED", "ACCOUNT_SUSPENDED" -> ErrorSeverity.ERROR
                    else -> ErrorSeverity.WARNING
                }
            }
            
            is LiftrixError.DatabaseError -> {
                when {
                    error.sqlErrorCode in listOf(1062, 1451, 1452) -> ErrorSeverity.WARNING
                    error.operation?.uppercase() in listOf("INSERT", "UPDATE", "DELETE") -> ErrorSeverity.WARNING
                    else -> ErrorSeverity.ERROR
                }
            }
            
            is LiftrixError.BusinessLogicError -> {
                when (error.code) {
                    "PREMIUM_FEATURE_REQUIRED", "MAX_WORKOUTS_REACHED", "TEMPLATE_LIMIT_EXCEEDED" -> ErrorSeverity.INFO
                    "ACCOUNT_SUSPENDED", "INVALID_USER_STATE" -> ErrorSeverity.ERROR
                    else -> ErrorSeverity.WARNING
                }
            }
            
            is LiftrixError.CalculationError -> ErrorSeverity.WARNING
            
            is LiftrixError.DataRetrievalError -> ErrorSeverity.WARNING
            
            is LiftrixError.ConfigurationError -> ErrorSeverity.WARNING
            
            is LiftrixError.ExportError -> ErrorSeverity.WARNING
            
            is LiftrixError.FileSystemError -> ErrorSeverity.ERROR
            
            is LiftrixError.NotFoundError -> ErrorSeverity.WARNING
            
            is LiftrixError.PermissionError -> ErrorSeverity.ERROR
            
            is LiftrixError.CacheError -> ErrorSeverity.WARNING
            
            is LiftrixError.UnknownError -> ErrorSeverity.ERROR
        }
    }
    
    /**
     * Determines if a retry button should be shown for the given error.
     * 
     * Evaluates error characteristics to determine if automatic or manual retry
     * is appropriate and likely to succeed.
     * 
     * @param error The LiftrixError to evaluate
     * @return true if a retry button should be displayed, false otherwise
     */
    fun shouldShowRetryButton(error: LiftrixError): Boolean {
        return when (error) {
            is LiftrixError.NetworkError -> true
            
            is LiftrixError.ValidationError -> false // User must fix validation issues first
            
            is LiftrixError.AuthenticationError -> {
                when (error.errorCode) {
                    "NETWORK_ERROR", "TOO_MANY_REQUESTS" -> true
                    "INVALID_CREDENTIALS", "ACCOUNT_DISABLED", "EMAIL_NOT_VERIFIED" -> false
                    else -> false
                }
            }
            
            is LiftrixError.DatabaseError -> {
                when {
                    error.sqlErrorCode in listOf(1062, 1451, 1452) -> false // Constraint violations
                    error.operation?.uppercase() in listOf("SELECT") -> true // Read operations can be retried
                    else -> true // Other DB errors might be transient
                }
            }
            
            is LiftrixError.BusinessLogicError -> {
                when (error.code) {
                    "WORKOUT_ALREADY_STARTED", "PREMIUM_FEATURE_REQUIRED", 
                    "MAX_WORKOUTS_REACHED", "TEMPLATE_LIMIT_EXCEEDED" -> false
                    "CONCURRENT_MODIFICATION" -> true
                    else -> false
                }
            }
            
            is LiftrixError.CalculationError -> true // Calculations can be retried
            
            is LiftrixError.DataRetrievalError -> error.retryable // Data retrieval based on retryable flag
            
            is LiftrixError.ConfigurationError -> true // Configuration errors can be retried
            
            is LiftrixError.ExportError -> true // Export operations can be retried
            
            is LiftrixError.FileSystemError -> {
                when (error.operation) {
                    "READ", "write" -> true // File operations can be retried
                    else -> false
                }
            }
            
            is LiftrixError.NotFoundError -> false // Resource not found typically can't be retried
            
            is LiftrixError.PermissionError -> false // Permission errors typically can't be retried
            
            is LiftrixError.CacheError -> true // Cache errors can be retried
            
            is LiftrixError.UnknownError -> true // Allow retry for unknown errors
        }
    }
    
    /**
     * Provides a single, actionable recovery suggestion for the error.
     * 
     * Returns the most important suggestion for error recovery, prioritizing
     * immediate actionable steps the user can take.
     * 
     * @param error The LiftrixError to get recovery suggestion for
     * @return Single recovery suggestion string, or null if no specific suggestion
     */
    fun getRecoverySuggestion(error: LiftrixError): String? {
        return when (error) {
            is LiftrixError.NetworkError -> {
                when {
                    error.httpStatusCode == 408 -> "Check your internet connection and try again"
                    error.httpStatusCode == 429 -> "Wait a moment before trying again"
                    error.httpStatusCode in 500..599 -> "Try again in a few minutes"
                    else -> "Check your internet connection"
                }
            }
            
            is LiftrixError.ValidationError -> {
                "Review the highlighted field and correct any errors"
            }
            
            is LiftrixError.AuthenticationError -> {
                when (error.errorCode) {
                    "TOKEN_EXPIRED" -> "Please sign in again"
                    "INVALID_CREDENTIALS" -> "Check your email and password"
                    "EMAIL_NOT_VERIFIED" -> "Check your email for verification link"
                    "TOO_MANY_REQUESTS" -> "Wait a few minutes before trying again"
                    "ACCOUNT_DISABLED" -> "Contact support for assistance"
                    else -> "Try signing in again or use a different method"
                }
            }
            
            is LiftrixError.DatabaseError -> {
                when {
                    error.sqlErrorCode == 1062 -> "Try using a different name"
                    error.sqlErrorCode == 1451 -> "Remove dependencies before deleting"
                    error.sqlErrorCode == 1452 -> "Fill in all required information"
                    else -> "Try saving again"
                }
            }
            
            is LiftrixError.BusinessLogicError -> {
                when (error.code) {
                    "WORKOUT_ALREADY_STARTED" -> "Finish your current workout first"
                    "PREMIUM_FEATURE_REQUIRED" -> "Upgrade to premium to access this feature"
                    "MAX_WORKOUTS_REACHED" -> "Upgrade your plan for more workouts"
                    "TEMPLATE_LIMIT_EXCEEDED" -> "Delete unused templates or upgrade your plan"
                    "CONCURRENT_MODIFICATION" -> "Refresh the page and try again"
                    else -> "Try again or contact support if the issue persists"
                }
            }
            
            is LiftrixError.CalculationError -> {
                when (error.operation) {
                    "volume_calculation" -> "Check your workout data and try again"
                    "progress_calculation" -> "Refresh your progress data and try again"
                    else -> "Try refreshing the data and calculate again"
                }
            }
            
            is LiftrixError.DataRetrievalError -> {
                when (error.operation) {
                    "getWidgetData" -> "Pull to refresh the widget data"
                    "getVolumeData" -> "Pull to refresh the volume data"
                    "getDurationData" -> "Pull to refresh the duration data"
                    "getFrequencyData" -> "Pull to refresh the frequency data"
                    "getProgressSummary" -> "Pull to refresh the progress summary"
                    else -> "Pull to refresh the data"
                }
            }
            
            is LiftrixError.ConfigurationError -> {
                when (error.configKey) {
                    "feature_flag" -> "Check your feature settings and try again"
                    "user_preferences" -> "Reset your preferences and try again"
                    "app_settings" -> "Check your app settings and try again"
                    else -> "Check your configuration and try again"
                }
            }
            
            is LiftrixError.ExportError -> {
                when (error.format) {
                    "PDF" -> "Try reducing the amount of data or use a different format"
                    "CSV" -> "Check your data and try exporting again"
                    else -> "Try selecting fewer items and export again"
                }
            }
            
            is LiftrixError.FileSystemError -> {
                when (error.operation) {
                    "read" -> "Check file permissions and try again"
                    "write" -> "Check available storage and try again"
                    "delete" -> "Check file permissions and try again"
                    else -> "Check file permissions and available storage"
                }
            }
            
            is LiftrixError.NotFoundError -> {
                when (error.resourceType) {
                    "workout" -> "Refresh the page or try searching for the workout"
                    "exercise" -> "Try selecting a different exercise"
                    "template" -> "Go back and try selecting a different template"
                    else -> "Refresh the page and try again"
                }
            }
            
            is LiftrixError.PermissionError -> {
                "Check your permissions or sign in again"
            }
            
            is LiftrixError.CacheError -> {
                "Try again or clear the app cache"
            }
            
            is LiftrixError.UnknownError -> {
                "Try again or restart the app if the problem continues"
            }
        }
    }
    
    /**
     * Convenience method to handle Firebase errors.
     * Delegates to FirebaseErrorMapper for specialized Firebase error handling.
     */
    fun handleFirebaseError(exception: Exception): LiftrixError {
        return FirebaseErrorMapper.handleFirebaseError(exception)
    }
}
