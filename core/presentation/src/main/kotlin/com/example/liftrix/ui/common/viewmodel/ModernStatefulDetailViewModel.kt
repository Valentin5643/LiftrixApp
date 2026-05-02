package com.example.liftrix.ui.common.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable
import com.example.liftrix.ui.common.state.StateCleanupManager
import timber.log.Timber

/**
 * Modernized StatefulDetailViewModel with single generic parameter and state persistence.
 *
 * Extends ModernBaseViewModel to provide seamless state persistence across configuration changes,
 * process death, and app backgrounding using Android's SavedStateHandle mechanism.
 *
 * Key Features:
 * - Automatic state persistence with SavedStateHandle integration
 * - Single generic parameter (simplified from two-generic pattern)
 * - Direct function calls instead of event indirection
 * - Type-safe state restoration with validation
 * - Support for complex objects via JSON serialization
 * - State versioning for graceful app updates
 * - Performance-optimized state flows with proper scoping
 *
 * Migration from StatefulDetailViewModel:
 * - Remove Event generic parameter
 * - Convert event handlers to direct public functions
 * - Keep all state persistence functionality intact
 *
 * Usage:
 * ```kotlin
 * @HiltViewModel
 * class MyDetailViewModel @Inject constructor(
 *     savedStateHandle: SavedStateHandle,
 *     private val myUseCase: MyUseCase
 * ) : ModernStatefulDetailViewModel<MyUiState>(
 *     initialState = MyUiState.Loading,
 *     savedStateHandle = savedStateHandle
 * ) {
 *     // Persisted state properties
 *     private val _timeRange = savedStateFlow(
 *         key = "time_range",
 *         initialValue = TimeRange.MONTH
 *     )
 *     val timeRange: StateFlow<TimeRange> = _timeRange
 *
 *     // Direct public function instead of event handling
 *     fun updateTimeRange(newRange: TimeRange) {
 *         updateSavedState("time_range", newRange)
 *     }
 * }
 * ```
 *
 * @param S The UI state type
 * @param initialState The initial UI state value
 * @param savedStateHandle Android's SavedStateHandle for state persistence
 */
abstract class ModernStatefulDetailViewModel<S : Any>(
    initialState: S,
    protected val savedStateHandle: SavedStateHandle
) : ModernBaseViewModel<S>(initialState), StateCleanupManager.StateCleanupAware {

    companion object {
        private const val STATE_VERSION_KEY = "state_version"
        private const val CURRENT_STATE_VERSION = 1
    }

    init {
        // Initialize state versioning
        initializeStateVersioning()
        Timber.d("ModernStatefulDetailViewModel initialized for ${this::class.simpleName}")
    }

    /**
     * Creates a StateFlow that persists its value using SavedStateHandle.
     *
     * Provides automatic state persistence with validation and restoration capabilities.
     * The state is automatically saved whenever the value changes and restored during
     * ViewModel creation.
     *
     * @param T The type of the state value (must be supported by SavedStateHandle)
     * @param key Unique key for storing the state value
     * @param initialValue Default value used on first launch or if restoration fails
     * @param validator Optional validation function to ensure restored state is valid
     * @return StateFlow that automatically persists and restores its value
     */
    protected fun <T> savedStateFlow(
        key: String,
        initialValue: T,
        validator: (T) -> Boolean = { true }
    ): StateFlow<T> {
        // Initialize with saved value or initial value
        val savedValue = savedStateHandle.get<T>(key) ?: initialValue
        val validatedValue = if (validator(savedValue)) {
            savedValue
        } else {
            Timber.w("Invalid saved state for key '$key', using initial value: $initialValue")
            updateSavedState(key, initialValue)
            initialValue
        }

        // Return as StateFlow from SavedStateHandle
        return savedStateHandle.getStateFlow(key, validatedValue)
    }

    /**
     * Creates a StateFlow for complex objects using JSON serialization.
     *
     * Used for objects that cannot be directly stored in SavedStateHandle (e.g., custom
     * data classes, lists, sets). Automatically serializes to JSON string for persistence.
     *
     * @param T The type of the state value (must be Serializable)
     * @param key Unique key for storing the state value
     * @param initialValue Default value used on first launch or if restoration fails
     * @param validator Optional validation function to ensure restored state is valid
     * @return StateFlow that automatically persists and restores its value via JSON
     */
    protected inline fun <reified T> savedComplexStateFlow(
        key: String,
        initialValue: T,
        noinline validator: (T) -> Boolean = { true }
    ): StateFlow<T> {
        val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }

        // Try to restore from saved state
        val savedJsonString = savedStateHandle.get<String>(key)
        val restoredValue = try {
            savedJsonString?.let { json.decodeFromString<T>(it) }
        } catch (e: Exception) {
            Timber.e(e, "Failed to deserialize saved state for key '$key'")
            null
        }

        val validatedValue = restoredValue?.takeIf { validator(it) } ?: initialValue

        // Save initial or validated value
        if (restoredValue == null || !validator(restoredValue)) {
            updateComplexSavedState(key, validatedValue)
        }

        // Return as flow that tracks changes from SavedStateHandle
        return savedStateHandle.getStateFlow(key, json.encodeToString(validatedValue))
            .map { jsonString ->
                try {
                    json.decodeFromString<T>(jsonString)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to deserialize state for key '$key'")
                    initialValue
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = validatedValue
            )
    }

    /**
     * Updates a simple persisted state value.
     *
     * Saves the new value to SavedStateHandle for automatic persistence across
     * configuration changes and process death.
     *
     * @param T The type of the state value
     * @param key Unique key for the state value
     * @param value New value to persist
     */
    protected fun <T> updateSavedState(key: String, value: T) {
        savedStateHandle[key] = value
        Timber.d("Updated saved state '$key' = $value")
    }

    /**
     * Updates a complex persisted state value using JSON serialization.
     *
     * Serializes the value to JSON string before persisting to SavedStateHandle.
     * Used for custom data classes, collections, and other complex types.
     *
     * @param T The type of the state value (must be Serializable)
     * @param key Unique key for the state value
     * @param value New value to persist
     */
    protected inline fun <reified T> updateComplexSavedState(key: String, value: T) {
        val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }

        try {
            val jsonString = json.encodeToString(value)
            savedStateHandle[key] = jsonString
            Timber.d("Updated complex saved state '$key'")
        } catch (e: Exception) {
            Timber.e(e, "Failed to serialize state for key '$key'")
        }
    }

    /**
     * Initializes state versioning for migration support.
     *
     * Allows for graceful handling of state schema changes across app updates.
     * Future versions can check the version number and migrate old state formats.
     */
    private fun initializeStateVersioning() {
        val currentVersion = savedStateHandle.get<Int>(STATE_VERSION_KEY) ?: 0

        if (currentVersion < CURRENT_STATE_VERSION) {
            Timber.i("State version upgraded from $currentVersion to $CURRENT_STATE_VERSION")
            // Future: Implement state migration logic here
            savedStateHandle[STATE_VERSION_KEY] = CURRENT_STATE_VERSION
        }
    }

    /**
     * Clears all persisted state for this ViewModel.
     *
     * Called when user signs out to ensure no user-specific state persists.
     */
    override fun onUserSignOut() {
        savedStateHandle.keys().forEach { key ->
            savedStateHandle.remove<Any>(key)
        }
        Timber.i("Cleared all saved state for ${this::class.simpleName}")
    }

    /**
     * Clears all persisted state for this ViewModel.
     *
     * Convenience method that calls onUserSignOut for explicit state cleanup when needed
     * (e.g., user logout, data reset).
     */
    fun clearState() {
        onUserSignOut()
    }

    /**
     * Exports current saved state for debugging purposes.
     *
     * Returns a map of all persisted state values for inspection and logging.
     * Useful for debugging state persistence issues.
     *
     * @return Map of state keys to their current values
     */
    fun exportStateSnapshot(): Map<String, Any?> {
        return savedStateHandle.keys().associateWith { key ->
            savedStateHandle.get<Any>(key)
        }
    }

    override fun onCleared() {
        super.onCleared()
        Timber.d("ModernStatefulDetailViewModel cleared: ${this::class.simpleName}")
        // State is automatically persisted by SavedStateHandle, no manual cleanup needed
    }
}
