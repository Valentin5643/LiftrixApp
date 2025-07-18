package com.example.liftrix.ui.common.state

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import com.example.liftrix.domain.model.error.LiftrixError

/**
 * Sealed class for asynchronous data loading states with timestamp tracking.
 * 
 * AsyncData provides a robust pattern for managing asynchronous data operations
 * with clear state distinctions and functional programming utilities. It complements
 * the existing UiState pattern by focusing specifically on data loading lifecycles
 * rather than UI presentation states.
 * 
 * Key Features:
 * - Type-safe state management with sealed class hierarchy
 * - Timestamp tracking for cache invalidation and freshness indicators
 * - Functional programming utilities (map, flatMap, fold)
 * - Thread-safe immutable implementation
 * - Integration with existing LiftrixError system
 * - Comprehensive state checking utilities
 * 
 * State Flow:
 * NotAsked → Loading → Success/Failure
 * 
 * Usage:
 * ```kotlin
 * // Initial state
 * var data: AsyncData<List<Workout>> = AsyncData.NotAsked
 * 
 * // Loading state
 * data = AsyncData.Loading
 * 
 * // Success state
 * data = AsyncData.Success(workouts)
 * 
 * // Error state
 * data = AsyncData.Failure(LiftrixError.NetworkError())
 * 
 * // Functional transformations
 * val workoutNames = data.map { workouts -> workouts.map { it.name } }
 * ```
 * 
 * @param T The type of data contained in success state
 */
sealed class AsyncData<out T> {
    
    /**
     * Initial state indicating no data has been requested yet.
     * 
     * Use this state when:
     * - Component is first initialized
     * - Data has been cleared/reset
     * - No operation has been triggered yet
     * 
     * This state helps distinguish between "not loaded" and "loading" states
     * for better UX in scenarios where data fetching is triggered by user actions.
     */
    object NotAsked : AsyncData<Nothing>()
    
    /**
     * Loading state indicating data is currently being fetched.
     * 
     * @param timestamp When the loading operation started
     * 
     * Use this state when:
     * - Data fetch operation is in progress
     * - Background refresh is happening
     * - Any async operation is running
     * 
     * The timestamp allows tracking loading duration and implementing timeouts.
     */
    data class Loading(
        val timestamp: Instant = Clock.System.now()
    ) : AsyncData<Nothing>()
    
    /**
     * Success state containing the loaded data.
     * 
     * @param data The successfully loaded data of type T
     * @param timestamp When the data was successfully loaded
     * 
     * Use this state when:
     * - Data has been successfully retrieved
     * - Operation completed without errors
     * - Data is ready for consumption
     * 
     * The timestamp enables cache invalidation strategies and freshness indicators.
     */
    data class Success<T>(
        val data: T,
        val timestamp: Instant = Clock.System.now()
    ) : AsyncData<T>()
    
    /**
     * Failure state containing error information.
     * 
     * @param error The LiftrixError describing what went wrong
     * @param timestamp When the error occurred
     * 
     * Use this state when:
     * - Data fetch operation failed
     * - Validation errors occurred
     * - Any recoverable or non-recoverable error happened
     * 
     * The timestamp helps with error analytics and retry strategies.
     */
    data class Failure(
        val error: LiftrixError,
        val timestamp: Instant = Clock.System.now()
    ) : AsyncData<Nothing>()
}

/**
 * State checking utility methods for AsyncData.
 * These methods provide convenient ways to check the current state type.
 */

/**
 * Checks if the async data is in NotAsked state.
 * 
 * @return true if state is NotAsked, false otherwise
 */
fun <T> AsyncData<T>.isNotAsked(): Boolean = this is AsyncData.NotAsked

/**
 * Checks if the async data is in Loading state.
 * 
 * @return true if state is Loading, false otherwise
 */
fun <T> AsyncData<T>.isLoading(): Boolean = this is AsyncData.Loading

/**
 * Checks if the async data is in Success state.
 * 
 * @return true if state is Success, false otherwise
 */
fun <T> AsyncData<T>.isSuccess(): Boolean = this is AsyncData.Success

/**
 * Checks if the async data is in Failure state.
 * 
 * @return true if state is Failure, false otherwise
 */
fun <T> AsyncData<T>.isFailure(): Boolean = this is AsyncData.Failure

/**
 * Checks if the async data has completed (either Success or Failure).
 * 
 * @return true if state is Success or Failure, false otherwise
 */
fun <T> AsyncData<T>.isComplete(): Boolean = this is AsyncData.Success || this is AsyncData.Failure

/**
 * Checks if the async data is in a pending state (NotAsked or Loading).
 * 
 * @return true if state is NotAsked or Loading, false otherwise
 */
fun <T> AsyncData<T>.isPending(): Boolean = this is AsyncData.NotAsked || this is AsyncData.Loading

/**
 * Data access utility methods for AsyncData.
 * These methods provide safe ways to access the underlying data.
 */

/**
 * Returns the data if in Success state, null otherwise.
 * 
 * @return The data if successful, null otherwise
 */
fun <T> AsyncData<T>.getOrNull(): T? = when (this) {
    is AsyncData.Success -> data
    else -> null
}

/**
 * Legacy alias for getOrNull for backward compatibility.
 * 
 * @return The data if successful, null otherwise
 */
fun <T> AsyncData<T>.dataOrNull(): T? = getOrNull()

/**
 * Checks if the async data has data available.
 * 
 * @return true if data is available, false otherwise
 */
fun <T> AsyncData<T>.hasData(): Boolean = this is AsyncData.Success

/**
 * Returns the data if in Success state, throws exception otherwise.
 * 
 * @return The data if successful
 * @throws IllegalStateException if not in Success state
 */
fun <T> AsyncData<T>.getOrThrow(): T = when (this) {
    is AsyncData.Success -> data
    is AsyncData.Failure -> throw error
    is AsyncData.Loading -> throw IllegalStateException("Data is still loading")
    is AsyncData.NotAsked -> throw IllegalStateException("Data has not been requested")
}

/**
 * Returns the data if in Success state, or the provided default value otherwise.
 * 
 * @param defaultValue The value to return if not in Success state
 * @return The data if successful, or defaultValue otherwise
 */
fun <T> AsyncData<T>.getOrDefault(defaultValue: T): T = when (this) {
    is AsyncData.Success -> data
    else -> defaultValue
}

/**
 * Returns the data if in Success state, or the result of the provided function otherwise.
 * 
 * @param defaultValue Function that provides the default value
 * @return The data if successful, or result of defaultValue function otherwise
 */
inline fun <T> AsyncData<T>.getOrElse(defaultValue: () -> T): T = when (this) {
    is AsyncData.Success -> data
    else -> defaultValue()
}

/**
 * Returns the error if in Failure state, null otherwise.
 * 
 * @return The error if failed, null otherwise
 */
fun <T> AsyncData<T>.errorOrNull(): LiftrixError? = when (this) {
    is AsyncData.Failure -> error
    else -> null
}

/**
 * Returns the timestamp for states that have timestamps.
 * 
 * @return The timestamp if available, null for NotAsked state
 */
fun <T> AsyncData<T>.timestampOrNull(): Instant? = when (this) {
    is AsyncData.NotAsked -> null
    is AsyncData.Loading -> timestamp
    is AsyncData.Success -> timestamp
    is AsyncData.Failure -> timestamp
}

/**
 * Functional programming utilities for AsyncData transformations.
 * These methods enable functional composition and data transformation.
 */

/**
 * Maps the data in a Success state to a new type while preserving other states.
 * 
 * This is a functor operation that allows transforming the data while maintaining
 * the async context and state information.
 * 
 * @param transform Function to transform the data from type T to type R
 * @return AsyncData<R> with transformed data or unchanged non-Success state
 */
inline fun <T, R> AsyncData<T>.map(transform: (T) -> R): AsyncData<R> = when (this) {
    is AsyncData.NotAsked -> AsyncData.NotAsked
    is AsyncData.Loading -> AsyncData.Loading(timestamp)
    is AsyncData.Success -> AsyncData.Success(transform(data), timestamp)
    is AsyncData.Failure -> AsyncData.Failure(error, timestamp)
}

/**
 * Flat maps the data in a Success state to a new AsyncData while preserving other states.
 * 
 * This is a monadic operation that allows chaining async operations while handling
 * all possible states correctly.
 * 
 * @param transform Function to transform the data from type T to AsyncData<R>
 * @return AsyncData<R> with transformed data or unchanged non-Success state
 */
inline fun <T, R> AsyncData<T>.flatMap(transform: (T) -> AsyncData<R>): AsyncData<R> = when (this) {
    is AsyncData.NotAsked -> AsyncData.NotAsked
    is AsyncData.Loading -> AsyncData.Loading(timestamp)
    is AsyncData.Success -> transform(data)
    is AsyncData.Failure -> AsyncData.Failure(error, timestamp)
}

/**
 * Applies a function to the data if in Success state, returns the original AsyncData.
 * 
 * Useful for side effects like logging, analytics, or triggering other operations
 * without changing the async data state.
 * 
 * @param action Function to apply to the data
 * @return The original AsyncData unchanged
 */
inline fun <T> AsyncData<T>.onSuccess(action: (T) -> Unit): AsyncData<T> {
    if (this is AsyncData.Success) {
        action(data)
    }
    return this
}

/**
 * Applies a function to the error if in Failure state, returns the original AsyncData.
 * 
 * Useful for error handling side effects like logging, analytics, or error reporting
 * without changing the async data state.
 * 
 * @param action Function to apply to the error
 * @return The original AsyncData unchanged
 */
inline fun <T> AsyncData<T>.onFailure(action: (LiftrixError) -> Unit): AsyncData<T> {
    if (this is AsyncData.Failure) {
        action(error)
    }
    return this
}

/**
 * Applies a function if in Loading state, returns the original AsyncData.
 * 
 * Useful for loading state side effects like starting timers, showing progress,
 * or analytics without changing the async data state.
 * 
 * @param action Function to apply when loading
 * @return The original AsyncData unchanged
 */
inline fun <T> AsyncData<T>.onLoading(action: (Instant) -> Unit): AsyncData<T> {
    if (this is AsyncData.Loading) {
        action(timestamp)
    }
    return this
}

/**
 * Folds the AsyncData into a single value by applying functions to each state.
 * 
 * This is a catamorphism that allows handling all possible states and reducing
 * them to a single value of type R.
 * 
 * @param notAsked Function to handle NotAsked state
 * @param loading Function to handle Loading state  
 * @param success Function to handle Success state
 * @param failure Function to handle Failure state
 * @return Result of applying the appropriate function based on current state
 */
inline fun <T, R> AsyncData<T>.fold(
    notAsked: () -> R,
    loading: (Instant) -> R,
    success: (T, Instant) -> R,
    failure: (LiftrixError, Instant) -> R
): R = when (this) {
    is AsyncData.NotAsked -> notAsked()
    is AsyncData.Loading -> loading(timestamp)
    is AsyncData.Success -> success(data, timestamp)
    is AsyncData.Failure -> failure(error, timestamp)
}

/**
 * Simplified fold for common use cases where timestamps are not needed.
 * 
 * @param notAsked Function to handle NotAsked state
 * @param loading Function to handle Loading state
 * @param success Function to handle Success state
 * @param failure Function to handle Failure state
 * @return Result of applying the appropriate function based on current state
 */
inline fun <T, R> AsyncData<T>.foldSimple(
    notAsked: () -> R,
    loading: () -> R,
    success: (T) -> R,
    failure: (LiftrixError) -> R
): R = when (this) {
    is AsyncData.NotAsked -> notAsked()
    is AsyncData.Loading -> loading()
    is AsyncData.Success -> success(data)
    is AsyncData.Failure -> failure(error)
}

/**
 * Convenience factory methods for creating AsyncData instances.
 */

/**
 * Creates a Success AsyncData with the given data.
 * 
 * @param data The data to wrap in Success state
 * @return AsyncData.Success containing the data
 */
fun <T> T.asAsyncSuccess(): AsyncData<T> = AsyncData.Success(this)

/**
 * Creates a Failure AsyncData with the given error.
 * 
 * @param error The error to wrap in Failure state
 * @return AsyncData.Failure containing the error
 */
fun LiftrixError.asAsyncFailure(): AsyncData<Nothing> = AsyncData.Failure(this)

/**
 * Creates a Loading AsyncData with current timestamp.
 * 
 * @return AsyncData.Loading with current timestamp
 */
fun asyncLoading(): AsyncData<Nothing> = AsyncData.Loading()

/**
 * Creates a NotAsked AsyncData.
 * 
 * @return AsyncData.NotAsked
 */
fun asyncNotAsked(): AsyncData<Nothing> = AsyncData.NotAsked

/**
 * Time-based utility methods for AsyncData.
 * These methods help with cache invalidation and freshness tracking.
 */

/**
 * Checks if the async data is fresh based on the given duration.
 * 
 * @param maxAge Maximum age in milliseconds
 * @return true if data is fresh (within maxAge), false otherwise
 */
fun <T> AsyncData<T>.isFresh(maxAge: Long): Boolean {
    val timestamp = timestampOrNull() ?: return false
    val now = Clock.System.now()
    return (now.toEpochMilliseconds() - timestamp.toEpochMilliseconds()) <= maxAge
}

/**
 * Checks if the async data is stale based on the given duration.
 * 
 * @param maxAge Maximum age in milliseconds
 * @return true if data is stale (older than maxAge), false otherwise
 */
fun <T> AsyncData<T>.isStale(maxAge: Long): Boolean = !isFresh(maxAge)

/**
 * Gets the age of the async data in milliseconds.
 * 
 * @return Age in milliseconds, or null if no timestamp available
 */
fun <T> AsyncData<T>.ageInMillis(): Long? {
    val timestamp = timestampOrNull() ?: return null
    val now = Clock.System.now()
    return now.toEpochMilliseconds() - timestamp.toEpochMilliseconds()
}

/**
 * Combines two AsyncData instances following specific combination rules.
 * 
 * Combination rules:
 * - If either is NotAsked, result is NotAsked
 * - If either is Loading, result is Loading  
 * - If either is Failure, result is Failure (first error)
 * - If both are Success, result is Success with Pair of data
 * 
 * @param other The other AsyncData to combine with
 * @return Combined AsyncData containing Pair of both values if both successful
 */
fun <T, R> AsyncData<T>.combineWith(other: AsyncData<R>): AsyncData<Pair<T, R>> = when {
    this.isNotAsked() || other.isNotAsked() -> AsyncData.NotAsked
    this.isLoading() || other.isLoading() -> AsyncData.Loading()
    this.isFailure() -> AsyncData.Failure(this.errorOrNull()!!)
    other.isFailure() -> AsyncData.Failure(other.errorOrNull()!!)
    this.isSuccess() && other.isSuccess() -> {
        val thisData = this.getOrNull()!!
        val otherData = other.getOrNull()!!
        AsyncData.Success(Pair(thisData, otherData))
    }
    else -> AsyncData.NotAsked
}

/**
 * Recovers from failure by providing a default value.
 * 
 * @param defaultValue The value to use if current state is Failure
 * @return Success with defaultValue if currently Failure, otherwise unchanged
 */
fun <T> AsyncData<T>.recover(defaultValue: T): AsyncData<T> = when (this) {
    is AsyncData.Failure -> AsyncData.Success(defaultValue)
    else -> this
}

/**
 * Recovers from failure by applying a function to the error.
 * 
 * @param recovery Function that takes the error and returns a new value
 * @return Success with recovered value if currently Failure, otherwise unchanged
 */
inline fun <T> AsyncData<T>.recoverWith(recovery: (LiftrixError) -> T): AsyncData<T> = when (this) {
    is AsyncData.Failure -> AsyncData.Success(recovery(error))
    else -> this
}

/**
 * Filters the data in Success state based on a predicate.
 * 
 * @param predicate Function to test the data
 * @param errorOnFalse Error to use if predicate returns false
 * @return Original AsyncData if predicate passes, Failure if predicate fails
 */
inline fun <T> AsyncData<T>.filter(
    predicate: (T) -> Boolean,
    errorOnFalse: () -> LiftrixError
): AsyncData<T> = when (this) {
    is AsyncData.Success -> {
        if (predicate(data)) {
            this
        } else {
            AsyncData.Failure(errorOnFalse())
        }
    }
    else -> this
}