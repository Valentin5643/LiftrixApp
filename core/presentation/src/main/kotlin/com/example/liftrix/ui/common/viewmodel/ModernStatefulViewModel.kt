package com.example.liftrix.ui.common.viewmodel

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.flow.StateFlow

/**
 * Modern ViewModel with SavedStateHandle support for process death recovery.
 *
 * This ViewModel extends ModernBaseViewModel to add SavedStateHandle integration,
 * enabling state persistence across configuration changes and process death.
 *
 * Key Features:
 * - All features of ModernBaseViewModel (updateState, setState, logError)
 * - SavedStateHandle integration for state persistence
 * - Helper methods for saved state operations
 * - Type-safe saved state flow creation
 *
 * Usage:
 * ```kotlin
 * @HiltViewModel
 * class MyViewModel @Inject constructor(
 *     savedStateHandle: SavedStateHandle,
 *     private val myUseCase: MyUseCase
 * ) : ModernStatefulViewModel<MyUiState>(
 *     initialState = MyUiState(),
 *     savedStateHandle = savedStateHandle
 * ) {
 *     // Access saved state
 *     private val savedQuery = savedStateFlow("query", "")
 *
 *     fun updateQuery(query: String) {
 *         updateSavedState("query", query)
 *         // ... load data with query
 *     }
 * }
 * ```
 *
 * @param S The UI state type
 * @param initialState The initial state value
 * @param savedStateHandle Handle for saving/restoring state
 */
abstract class ModernStatefulViewModel<S : Any>(
    initialState: S,
    protected val savedStateHandle: SavedStateHandle
) : ModernBaseViewModel<S>(initialState) {

    /**
     * Creates a StateFlow backed by SavedStateHandle for automatic persistence.
     *
     * The returned flow automatically persists its value to SavedStateHandle
     * and survives configuration changes and process death.
     *
     * @param key The key to use for persistence
     * @param initialValue The initial value if no saved value exists
     * @return StateFlow that persists its value
     */
    protected fun <T> savedStateFlow(
        key: String,
        initialValue: T
    ): StateFlow<T> = savedStateHandle.getStateFlow(key, initialValue)

    /**
     * Updates a value in SavedStateHandle for persistence.
     *
     * Use this to persist values that should survive process death.
     *
     * @param key The key to use for persistence
     * @param value The value to persist
     */
    protected fun <T> updateSavedState(key: String, value: T) {
        savedStateHandle[key] = value
    }

    /**
     * Retrieves a value from SavedStateHandle.
     *
     * @param key The key to look up
     * @return The saved value, or null if not found
     */
    protected fun <T> getSavedState(key: String): T? = savedStateHandle[key]

    /**
     * Retrieves a value from SavedStateHandle with a default fallback.
     *
     * @param key The key to look up
     * @param defaultValue The default value if key not found
     * @return The saved value or the default
     */
    protected fun <T> getSavedStateOrDefault(key: String, defaultValue: T): T =
        savedStateHandle[key] ?: defaultValue
}
