package com.example.liftrix.domain.model.common

import com.example.liftrix.domain.model.error.LiftrixError
import kotlin.coroutines.cancellation.CancellationException

/**
 * Type alias for standardized result handling across all domain operations.
 * 
 * Built on Kotlin's Result<T> with additional LiftrixError-specific extensions
 * for consistent error handling throughout the Liftrix application.
 * 
 * Provides:
 * - Full compatibility with existing Kotlin Result<T> functionality
 * - LiftrixError-specific error transformation and handling
 * - Seamless integration with existing use cases and repositories
 * - Type-safe error handling with recovery mechanisms
 */
typealias LiftrixResult<T> = Result<T>

/**
 * Extension functions for enhanced LiftrixResult error handling and transformation
 */

/**
 * Transforms any Throwable in this Result to a LiftrixError using the provided transform function.
 * 
 * If this Result is successful, it remains unchanged.
 * If this Result is a failure, the Throwable is transformed to a LiftrixError.
 * 
 * @param transform Function to convert Throwable to LiftrixError
 * @return LiftrixResult with transformed error or original success value
 * 
 * Example:
 * ```
 * val result = runCatching { workoutRepository.create(workout) }
 *     .mapError { LiftrixError.DatabaseError("Database operation failed") }
 * ```
 */
fun <T> LiftrixResult<T>.mapError(transform: (Throwable) -> LiftrixError): LiftrixResult<T> {
    return fold(
        onSuccess = { Result.success(it) },
        onFailure = { throwable ->
            val liftrixError = transform(throwable)
            Result.failure(liftrixError)
        }
    )
}

/**
 * Performs the given action on the LiftrixError if this Result is a failure and the cause is a LiftrixError.
 * 
 * If this Result is successful or the failure cause is not a LiftrixError, no action is performed.
 * This function is useful for LiftrixError-specific error handling like analytics or logging.
 * 
 * @param action Function to execute with the LiftrixError
 * @return This LiftrixResult unchanged for chaining
 * 
 * Example:
 * ```
 * result.onLiftrixError { error ->
 *     analyticsService.recordException(error)
 *     if (error.isRecoverable) {
 *         scheduleRetry(error.retryAfter)
 *     }
 * }
 * ```
 */
fun <T> LiftrixResult<T>.onLiftrixError(action: (LiftrixError) -> Unit): LiftrixResult<T> {
    exceptionOrNull()?.let { throwable ->
        if (throwable is LiftrixError) {
            action(throwable)
        }
    }
    return this
}

/**
 * Returns the success value or recovers from a LiftrixError using the provided transform function.
 * 
 * If this Result is successful, returns the success value.
 * If this Result is a failure with a LiftrixError, applies the transform function.
 * If this Result is a failure with a non-LiftrixError, rethrows the original exception.
 * 
 * @param transform Function to recover from LiftrixError
 * @return Success value or recovered value from LiftrixError
 * @throws Throwable if the failure is not a LiftrixError
 * 
 * Example:
 * ```
 * val workout = result.recoverWithLiftrix { error ->
 *     when (error) {
 *         is LiftrixError.NetworkError -> cachedWorkout
 *         is LiftrixError.ValidationError -> Workout.createDefault()
 *         else -> throw error
 *     }
 * }
 * ```
 */
fun <T> LiftrixResult<T>.recoverWithLiftrix(transform: (LiftrixError) -> T): T {
    return fold(
        onSuccess = { it },
        onFailure = { throwable ->
            when (throwable) {
                is LiftrixError -> transform(throwable)
                else -> throw throwable
            }
        }
    )
}

/**
 * Returns the success value or recovers from a LiftrixError with another LiftrixResult.
 * 
 * Similar to recoverWithLiftrix but allows the recovery function to return another LiftrixResult,
 * enabling chained recovery operations that may also fail.
 * 
 * @param transform Function to recover from LiftrixError returning another LiftrixResult
 * @return Success value or result from recovery operation
 * 
 * Example:
 * ```
 * val result = primaryRepository.getWorkout(id)
 *     .recoverWithLiftrixResult { error ->
 *         when (error) {
 *             is LiftrixError.NetworkError -> fallbackRepository.getWorkout(id)
 *             else -> Result.failure(error)
 *         }
 *     }
 * ```
 */
fun <T> LiftrixResult<T>.recoverWithLiftrixResult(transform: (LiftrixError) -> LiftrixResult<T>): LiftrixResult<T> {
    return fold(
        onSuccess = { Result.success(it) },
        onFailure = { throwable ->
            when (throwable) {
                is LiftrixError -> transform(throwable)
                else -> Result.failure(throwable)
            }
        }
    )
}

/**
 * Maps the success value while preserving LiftrixError information in case of failure.
 * 
 * This is equivalent to the standard Result.map() but provides explicit LiftrixError typing
 * for better type safety and error handling clarity.
 * 
 * @param transform Function to transform the success value
 * @return LiftrixResult with transformed success value or original LiftrixError
 * 
 * Example:
 * ```
 * val workoutSummary = workoutResult.mapLiftrix { workout ->
 *     WorkoutSummary(
 *         id = workout.id,
 *         name = workout.name,
 *         duration = workout.calculateDuration()
 *     )
 * }
 * ```
 */
fun <T, R> LiftrixResult<T>.mapLiftrix(transform: (T) -> R): LiftrixResult<R> {
    return map(transform)
}

/**
 * FlatMaps the success value to another LiftrixResult while preserving error types.
 * 
 * This is equivalent to standard Result chaining but with explicit LiftrixError typing
 * for clearer error handling semantics.
 * 
 * @param transform Function to transform success value to another LiftrixResult
 * @return Flattened LiftrixResult
 * 
 * Example:
 * ```
 * val result = validateWorkout(workoutData)
 *     .flatMapLiftrix { validWorkout ->
 *         workoutRepository.save(validWorkout)
 *     }
 * ```
 */
fun <T, R> LiftrixResult<T>.flatMapLiftrix(transform: (T) -> LiftrixResult<R>): LiftrixResult<R> {
    return fold(
        onSuccess = transform,
        onFailure = { Result.failure(it) }
    )
}

/**
 * Creates a successful LiftrixResult with the given value.
 * Convenience function for creating successful results with explicit LiftrixResult typing.
 */
fun <T> liftrixSuccess(value: T): LiftrixResult<T> = Result.success(value)

/**
 * Creates a failed LiftrixResult with the given LiftrixError.
 * Convenience function for creating failed results with LiftrixError.
 */
fun <T> liftrixFailure(error: LiftrixError): LiftrixResult<T> = Result.failure(error)

/**
 * Executes the given block and wraps the result in a LiftrixResult, automatically converting
 * any thrown exceptions to LiftrixError using the provided error mapper.
 * 
 * @param errorMapper Function to convert exceptions to LiftrixError
 * @param block Code block to execute
 * @return LiftrixResult with success value or mapped LiftrixError
 * 
 * Example:
 * ```
 * val result = liftrixCatching(
 *     errorMapper = { LiftrixError.DatabaseError("Database operation failed") }
 * ) {
 *     workoutDao.insertWorkout(workout)
 * }
 * ```
 */
inline fun <T> liftrixCatching(
    noinline errorMapper: (Throwable) -> LiftrixError,
    block: () -> T
): LiftrixResult<T> {
    return try {
        Result.success(block())
    } catch (cancellationException: CancellationException) {
        throw cancellationException
    } catch (throwable: Throwable) {
        Result.failure(errorMapper(throwable))
    }
}
