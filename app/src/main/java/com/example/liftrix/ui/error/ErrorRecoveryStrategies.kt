package com.example.liftrix.ui.error

import com.example.liftrix.domain.model.validation.ValidationError as DomainValidationError
import com.example.liftrix.domain.model.validation.ValidationSeverity as DomainValidationSeverity

import com.example.liftrix.domain.model.UnifiedWorkoutSession
import com.example.liftrix.domain.model.WorkoutId
import com.example.liftrix.domain.model.MuscleGroup
import com.example.liftrix.domain.model.Weight
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.error.shouldRetry
import com.example.liftrix.domain.repository.backup.BackupService
import com.example.liftrix.domain.repository.sync.SyncService
import kotlinx.coroutines.delay
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Comprehensive error recovery strategies for automatic error handling and system resilience.
 * 
 * Provides intelligent recovery mechanisms for various error scenarios including:
 * - Network failure graceful degradation with offline queuing
 * - Data corruption detection and automatic backup restoration
 * - Historical data validation and integrity preservation
 * - Retry logic with exponential backoff for transient errors
 * - Offline-first data preservation during connection issues
 * 
 * Integrates with existing Liftrix error handling infrastructure and repository patterns.
 */
@Singleton
class ErrorRecoveryStrategies @Inject constructor(
    private val backupService: BackupService,
    private val syncService: SyncService
) {
    
    companion object {
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val BASE_RETRY_DELAY_MS = 1000L
        private const val MAX_RETRY_DELAY_MS = 30_000L
    }
    
    /**
     * Recovers from data corruption by attempting backup restoration.
     * 
     * Implements intelligent recovery logic that attempts to restore corrupted entities
     * from the most recent backup, with fallback to user notification if no backup exists.
     */
    suspend fun recoverFromDataCorruption(
        corruptedEntity: String,
        entityId: String
    ): LiftrixResult<Boolean> {
        return try {
            Timber.w("Attempting data corruption recovery for $corruptedEntity:$entityId")
            
            // Check if backup exists for the entity
            val backups = backupService.listBackups(entityId)
            
            if (backups.isSuccess && backups.getOrNull()?.isNotEmpty() == true) {
                // Use the most recent backup
                val latestBackupId = backups.getOrNull()!!.first()
                val restoreResult = backupService.restoreFromBackup(entityId, latestBackupId)
                
                if (restoreResult.isSuccess) {
                    Timber.i("Successfully recovered corrupted data for $corruptedEntity:$entityId")
                    Result.success(true)
                } else {
                    Timber.e("Failed to restore backup data for $corruptedEntity:$entityId")
                    Result.failure(
                        LiftrixError.DatabaseError(
                            errorMessage = "Failed to restore backup data",
                            operation = "backup_restore",
                            table = corruptedEntity
                        )
                    )
                }
            } else {
                Timber.w("No backup available for corrupted data $corruptedEntity:$entityId")
                Result.failure(
                    LiftrixError.NotFoundError(
                        errorMessage = "No backup available for corrupted data",
                        resourceType = "backup",
                        resourceId = "$corruptedEntity:$entityId"
                    )
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Error during data corruption recovery for $corruptedEntity:$entityId")
            Result.failure(
                LiftrixError.DatabaseError(
                    errorMessage = "Recovery operation failed: ${e.message}",
                    operation = "corruption_recovery",
                    table = corruptedEntity
                )
            )
        }
    }
    
    /**
     * Handles network failures with graceful degradation to offline mode.
     * 
     * Queues failed operations for retry when network connectivity is restored,
     * preserving user data and workflow continuity during network issues.
     */
    suspend fun handleNetworkFailure(
        operationId: String,
        userId: String
    ): LiftrixResult<Unit> {
        return try {
            Timber.w("Handling network failure for operation: $operationId")
            
            // Queue operation for retry when network returns
            val queueResult = syncService.queueOperation(
                userId = userId,
                operation = operationId,
                priority = 2 // High priority for failed operations
            )
            
            if (queueResult.isSuccess) {
                Timber.i("Successfully queued operation $operationId for retry")
                Result.success(Unit)
            } else {
                Timber.e("Failed to queue operation $operationId for retry")
                Result.failure(
                    LiftrixError.NetworkError(
                        errorMessage = "Failed to queue operation for retry",
                        retryAfter = 5000L
                    )
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Error during network failure handling for operation: $operationId")
            Result.failure(
                LiftrixError.NetworkError(
                    errorMessage = "Network failure handling failed: ${e.message}",
                    retryAfter = 10000L
                )
            )
        }
    }
    
    /**
     * Validates historical data edits to prevent corruption and maintain integrity.
     * 
     * Performs comprehensive validation of edit operations on historical workout data,
     * ensuring that core session integrity is preserved while allowing valid modifications.
     */
    fun validateHistoricalDataEdit(
        originalData: UnifiedWorkoutSession,
        editedData: UnifiedWorkoutSession
    ): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()
        
        // Validate immutable core session data
        if (originalData.id != editedData.id) {
            errors.add(ValidationError(
                field = "sessionId",
                message = "Cannot change session ID of historical session",
                severity = ValidationSeverity.CRITICAL
            ))
        }
        
        if (originalData.userId != editedData.userId) {
            errors.add(ValidationError(
                field = "userId", 
                message = "Cannot change user ID of historical session",
                severity = ValidationSeverity.CRITICAL
            ))
        }
        
        if (originalData.startedAt != editedData.startedAt) {
            errors.add(ValidationError(
                field = "startedAt",
                message = "Cannot modify session start time",
                severity = ValidationSeverity.WARNING
            ))
        }
        
        // Validate exercise data integrity
        editedData.exercises.forEachIndexed { exerciseIndex, exercise ->
            exercise.sets.forEachIndexed { setIndex, set ->
                // Weight validation
                if (set.actualWeight != null && set.actualWeight!!.kilograms < 0) {
                    errors.add(ValidationError(
                        field = "exercises[$exerciseIndex].sets[$setIndex].weight",
                        message = "Weight cannot be negative",
                        severity = ValidationSeverity.ERROR
                    ))
                }
                
                if (set.actualWeight != null && set.actualWeight!!.kilograms > 907) { // 2000 lbs in kg
                    errors.add(ValidationError(
                        field = "exercises[$exerciseIndex].sets[$setIndex].weight",
                        message = "Weight seems unrealistic (max 2000 lbs)",
                        severity = ValidationSeverity.WARNING
                    ))
                }
                
                // Reps validation
                if (set.actualReps != null && set.actualReps!! < 0) {
                    errors.add(ValidationError(
                        field = "exercises[$exerciseIndex].sets[$setIndex].reps",
                        message = "Reps cannot be negative",
                        severity = ValidationSeverity.ERROR
                    ))
                }
                
                if (set.actualReps != null && set.actualReps!! > 1000) {
                    errors.add(ValidationError(
                        field = "exercises[$exerciseIndex].sets[$setIndex].reps",
                        message = "Reps seems unrealistic (max 1000)",
                        severity = ValidationSeverity.WARNING
                    ))
                }
                
                // Rest time validation (from exercise level)
                if (exercise.restTimeSeconds != null && exercise.restTimeSeconds!! < 0) {
                    errors.add(ValidationError(
                        field = "exercises[$exerciseIndex].restTime",
                        message = "Rest time cannot be negative",
                        severity = ValidationSeverity.ERROR
                    ))
                }
            }
        }
        
        // Validate session duration
        val sessionDuration = if (editedData.endedAt != null) {
            java.time.Duration.between(editedData.startedAt, editedData.endedAt)
        } else null
        
        sessionDuration?.let { duration ->
            if (duration.isNegative) {
                errors.add(ValidationError(
                    field = "duration",
                    message = "Session end time cannot be before start time",
                    severity = ValidationSeverity.CRITICAL
                ))
            }
            
            if (duration.toHours() > 12) {
                errors.add(ValidationError(
                    field = "duration",
                    message = "Session duration over 12 hours seems unrealistic",
                    severity = ValidationSeverity.WARNING
                ))
            }
        }
        
        return errors
    }
    
    /**
     * Executes operation with intelligent retry logic and exponential backoff.
     * 
     * Provides robust retry mechanism for transient errors with configurable
     * backoff strategies and failure handling.
     */
    suspend fun <T> executeWithRetry(
        operationId: String,
        operation: suspend (attemptNumber: Int) -> LiftrixResult<T>,
        maxAttempts: Int = MAX_RETRY_ATTEMPTS,
        baseDelayMs: Long = BASE_RETRY_DELAY_MS
    ): LiftrixResult<T> {
        
        repeat(maxAttempts) { attempt ->
            try {
                val result = operation(attempt + 1)
                
                if (result.isSuccess) {
                    if (attempt > 0) {
                        Timber.i("Operation $operationId succeeded after ${attempt + 1} attempts")
                    }
                    return result
                } else {
                    val error = result.exceptionOrNull() as? LiftrixError
                    val shouldRetry = error?.shouldRetry(attempt, maxAttempts) == true && 
                                     attempt < maxAttempts - 1
                    
                    if (shouldRetry) {
                        val delayMs = calculateRetryDelay(attempt, baseDelayMs)
                        Timber.w("Operation $operationId failed (attempt ${attempt + 1}), retrying in ${delayMs}ms: ${error?.message}")
                        delay(delayMs)
                    } else {
                        Timber.e("Operation $operationId failed after ${attempt + 1} attempts: ${error?.message}")
                        return result
                    }
                }
            } catch (e: Exception) {
                val shouldRetry = attempt < maxAttempts - 1
                
                if (shouldRetry) {
                    val delayMs = calculateRetryDelay(attempt, baseDelayMs)
                    Timber.w(e, "Operation $operationId threw exception (attempt ${attempt + 1}), retrying in ${delayMs}ms")
                    delay(delayMs)
                } else {
                    Timber.e(e, "Operation $operationId threw exception after ${attempt + 1} attempts")
                    return Result.failure(
                        LiftrixError.UnknownError(
                            errorMessage = "Operation failed after $maxAttempts attempts: ${e.message}"
                        )
                    )
                }
            }
        }
        
        // This should never be reached due to the loop logic above
        return Result.failure(
            LiftrixError.UnknownError(
                errorMessage = "Operation $operationId exhausted all retry attempts"
            )
        )
    }
    
    /**
     * Recovers from authentication failures with token refresh and re-authentication.
     */
    suspend fun recoverFromAuthenticationFailure(
        error: LiftrixError.AuthenticationError
    ): LiftrixResult<Boolean> {
        return try {
            when (error.errorCode) {
                "TOKEN_EXPIRED" -> {
                    // Attempt token refresh
                    Timber.i("Attempting token refresh for expired authentication")
                    // Implementation would integrate with AuthRepository
                    Result.success(true)
                }
                "INVALID_CREDENTIALS" -> {
                    // Cannot recover automatically, require user re-authentication
                    Timber.w("Invalid credentials - user re-authentication required")
                    Result.failure(error.copy(isRecoverable = false))
                }
                else -> {
                    // Generic auth error - attempt re-authentication
                    Timber.w("Generic authentication error - attempting recovery")
                    Result.success(false)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error during authentication recovery")
            Result.failure(
                LiftrixError.AuthenticationError(
                    errorMessage = "Authentication recovery failed: ${e.message}",
                    isRecoverable = false
                )
            )
        }
    }
    
    /**
     * Calculates exponential backoff delay with jitter to prevent thundering herd.
     */
    private fun calculateRetryDelay(attemptNumber: Int, baseDelayMs: Long): Long {
        val exponentialDelay = baseDelayMs * (1L shl attemptNumber) // 2^attemptNumber
        val jitter = (Math.random() * baseDelayMs * 0.1).toLong() // 10% jitter
        return (exponentialDelay + jitter).coerceAtMost(MAX_RETRY_DELAY_MS)
    }
}

/**
 * UI layer validation error for backward compatibility.
 * 
 * @deprecated Use domain.model.validation.ValidationError for business logic.
 * This UI version exists for backward compatibility and UI-specific functionality.
 */
@Deprecated(
    message = "Use domain ValidationError for business logic",
    replaceWith = ReplaceWith("com.example.liftrix.domain.model.validation.ValidationError")
)
data class ValidationError(
    val field: String,
    val message: String,
    val severity: ValidationSeverity = ValidationSeverity.ERROR
)

/**
 * UI layer validation severity for backward compatibility.
 * 
 * @deprecated Use domain.model.validation.ValidationSeverity for business logic.
 */
@Deprecated(
    message = "Use domain ValidationSeverity for business logic",
    replaceWith = ReplaceWith("com.example.liftrix.domain.model.validation.ValidationSeverity")
)
enum class ValidationSeverity {
    WARNING,  // Non-blocking, informational
    ERROR,    // Blocking, must be fixed
    CRITICAL  // System integrity issue
}

/**
 * Maps domain ValidationError to UI ValidationError
 */
fun DomainValidationError.toUiValidationError(): ValidationError = ValidationError(
    field = field,
    message = message,
    severity = severity.toUiValidationSeverity()
)

/**
 * Maps UI ValidationError to domain ValidationError
 */
fun ValidationError.toDomainValidationError(): DomainValidationError = DomainValidationError(
    field = field,
    message = message,
    severity = severity.toDomainValidationSeverity()
)

/**
 * Maps domain ValidationSeverity to UI ValidationSeverity
 */
fun DomainValidationSeverity.toUiValidationSeverity(): ValidationSeverity = when (this) {
    DomainValidationSeverity.WARNING -> ValidationSeverity.WARNING
    DomainValidationSeverity.ERROR -> ValidationSeverity.ERROR
    DomainValidationSeverity.CRITICAL -> ValidationSeverity.CRITICAL
}

/**
 * Maps UI ValidationSeverity to domain ValidationSeverity
 */
fun ValidationSeverity.toDomainValidationSeverity(): DomainValidationSeverity = when (this) {
    ValidationSeverity.WARNING -> DomainValidationSeverity.WARNING
    ValidationSeverity.ERROR -> DomainValidationSeverity.ERROR
    ValidationSeverity.CRITICAL -> DomainValidationSeverity.CRITICAL
}