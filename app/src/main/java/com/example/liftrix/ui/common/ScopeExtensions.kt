package com.example.liftrix.ui.common

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onEach

/**
 * Extension functions to enhance scope function usage and reduce boilerplate code.
 * 
 * These extensions provide cleaner patterns for:
 * - Conditional operations with let/takeIf
 * - State management with apply/run
 * - Error handling with runCatching
 * - Flow operations with scope functions
 */

/**
 * Apply a transformation only if the condition is true, otherwise return the original value
 */
inline fun <T> T.applyIf(condition: Boolean, block: T.() -> T): T {
    return if (condition) this.block() else this
}

/**
 * Apply a transformation only if the condition predicate returns true
 */
inline fun <T> T.applyIf(predicate: (T) -> Boolean, block: T.() -> T): T {
    return if (predicate(this)) this.block() else this
}

/**
 * Execute a block only if the receiver is not null, more readable than let for void operations
 */
inline fun <T> T?.ifNotNull(block: (T) -> Unit) {
    this?.let(block)
}

/**
 * Execute a block only if the receiver is null
 */
inline fun <T> T?.ifNull(block: () -> Unit) {
    if (this == null) block()
}

/**
 * Chainable validation with early return pattern
 */
inline fun <T, R> T.validate(validator: (T) -> R?): R? = validator(this)

/**
 * Execute different blocks based on whether the receiver is null or not
 */
inline fun <T, R> T?.fold(ifNull: () -> R, ifNotNull: (T) -> R): R {
    return if (this == null) ifNull() else ifNotNull(this)
}

/**
 * Enhanced Flow error handling with state update pattern
 */
inline fun <T, S> Flow<T>.catchAndUpdateState(
    state: MutableStateFlow<S>,
    crossinline onError: S.(Throwable) -> S
): Flow<T> = catch { error ->
    state.value = state.value.onError(error)
}

/**
 * Enhanced Flow success handling with state update pattern
 */
inline fun <T, S> Flow<T>.onEachUpdateState(
    state: MutableStateFlow<S>,
    crossinline onSuccess: S.(T) -> S
): Flow<T> = onEach { data ->
    state.value = state.value.onSuccess(data)
}

/**
 * Combine Flow error handling and success handling in one chain
 */
inline fun <T, S> Flow<T>.handleWithState(
    state: MutableStateFlow<S>,
    crossinline onSuccess: S.(T) -> S,
    crossinline onError: S.(Throwable) -> S
): Flow<T> = catch { error ->
    state.value = state.value.onError(error)
}.onEach { data ->
    state.value = state.value.onSuccess(data)
}

/**
 * Simplified state copying with conditional updates
 */
inline fun <T> T.copyWith(vararg conditions: Pair<Boolean, T.() -> T>): T {
    return conditions.fold(this) { acc, (condition, transform) ->
        if (condition) acc.transform() else acc
    }
}

/**
 * Safe casting with fallback
 */
inline fun <reified T> Any?.safeCast(fallback: () -> T): T {
    return this as? T ?: fallback()
}

/**
 * Execute block only if all conditions are true
 */
inline fun <T> T.runIf(vararg conditions: Boolean, block: T.() -> T): T {
    return if (conditions.all { it }) this.block() else this
}

/**
 * Execute block only if any condition is true
 */
inline fun <T> T.runIfAny(vararg conditions: Boolean, block: T.() -> T): T {
    return if (conditions.any { it }) this.block() else this
}

/**
 * Chain multiple operations with early exit on null
 */
inline fun <T, R> T?.chain(block: (T) -> R?): R? = this?.let(block)

/**
 * Execute side effect only if condition is true
 */
inline fun <T> T.alsoIf(condition: Boolean, block: (T) -> Unit): T {
    return also { if (condition) block(it) }
}

/**
 * Execute side effect only if condition predicate returns true
 */
inline fun <T> T.alsoIf(predicate: (T) -> Boolean, block: (T) -> Unit): T {
    return also { if (predicate(it)) block(it) }
}

/**
 * More readable alternative to let for transformations
 */
inline fun <T, R> T.transform(block: (T) -> R): R = block(this)

/**
 * Execute block and return original value (like also but with different semantics)
 */
inline fun <T> T.peek(block: (T) -> Unit): T = also(block)

/**
 * Conditional execution with different blocks
 */
inline fun <T, R> T.ifElse(
    condition: Boolean,
    ifTrue: (T) -> R,
    ifFalse: (T) -> R
): R = if (condition) ifTrue(this) else ifFalse(this)

/**
 * Execute different blocks based on condition predicate
 */
inline fun <T, R> T.ifElse(
    predicate: (T) -> Boolean,
    ifTrue: (T) -> R,
    ifFalse: (T) -> R
): R = if (predicate(this)) ifTrue(this) else ifFalse(this)