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
 * Enhanced base ViewModel with state persistence capabilities using SavedStateHandle.
 * 
 * Extends BaseViewModel to provide seamless state persistence across configuration changes,
 * process death, and app backgrounding. Uses Android's SavedStateHandle mechanism for
 * reliable state restoration.
 * 
 * Key Features:
 * - Automatic state persistence with SavedStateHandle integration
 * - Type-safe state restoration with validation
 * - Support for complex objects via JSON serialization
 * - State versioning for graceful app updates
 * - Performance-optimized state flows with proper scoping
 * 
 * Usage:
 * ```kotlin
 * @HiltViewModel
 * class MyDetailViewModel @Inject constructor(
 *     savedStateHandle: SavedStateHandle,
 *     private val myUseCase: MyUseCase
 * ) : StatefulDetailViewModel<MyUiState>(
 *     initialState = MyUiState.Loading,
 *     savedStateHandle = savedStateHandle
 * ) {
 * 
 *     // Persisted state properties
 *     private val _timeRange = savedStateFlow(
 *         key = "time_range",
 *         initialValue = TimeRange.MONTH
 *     )
 *     val timeRange: StateFlow<TimeRange> = _timeRange
 * 
 *     fun updateTimeRange(newRange: TimeRange) {
 *         updateSavedState("time_range", newRange)
 *     }
 * }
 * ```
 * 
 * @param S The UI state type
 * @param initialState The initial state value
 * @param savedStateHandle Android's SavedStateHandle for state persistence
 */
abstract class StatefulDetailViewModel<S : Any, E : Any>(
    initialState: S,
    protected val savedStateHandle: SavedStateHandle
) : ModernBaseViewModel<S>(initialState), StateCleanupManager.StateCleanupAware {

    constructor(
        savedStateHandle: SavedStateHandle,
        @Suppress("UNUSED_PARAMETER") errorHandler: com.example.liftrix.domain.usecase.common.ErrorHandler
    ) : this("" as S, savedStateHandle)

    open fun handleEvent(event: E) = Unit

    companion object {
        private const val STATE_VERSION_KEY = "state_version"
        private const val CURRENT_STATE_VERSION = 1
    }

    init {
        // Initialize state versioning
        initializeStateVersioning()
        Timber.d("StatefulDetailViewModel initialized for ${this::class.simpleName}")
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
        
        // Return the SavedStateHandle's StateFlow directly to ensure immediate updates
        return savedStateHandle.getStateFlow(key, validatedValue)
    }

    /**
     * Updates a saved state value and triggers StateFlow updates.
     * 
     * Provides thread-safe updates to persisted state values with automatic
     * StateFlow notification for UI reactivity.
     * 
     * @param T The type of the state value
     * @param key The state key to update
     * @param value The new value to persist
     */
    protected fun <T> updateSavedState(key: String, value: T) {
        try {
            savedStateHandle[key] = value
            Timber.d("State updated for key '$key': $value")
        } catch (e: Exception) {
            Timber.e(e, "Failed to update saved state for key '$key'")
        }
    }

    /**
     * Creates a StateFlow for complex objects using JSON serialization.
     * 
     * Enables persistence of complex data structures that aren't directly supported
     * by SavedStateHandle by serializing them to JSON strings. Requires objects
     * to be marked with @Serializable annotation.
     * 
     * @param T The type of the complex object (must be @Serializable)
     * @param key Unique key for storing the serialized state
     * @param initialValue Default value used on first launch or if restoration fails
     * @param validator Optional validation function for the deserialized object
     * @return StateFlow that automatically serializes and deserializes the object
     */
    protected inline fun <reified T> savedComplexStateFlow(
        key: String,
        initialValue: T,
        noinline validator: (T) -> Boolean = { true }
    ): StateFlow<T> where T : @Serializable Any {
        val jsonKey = "${key}_json"
        
        // Get initial JSON value or serialize initial value
        val initialJson = try {
            savedStateHandle.get<String>(jsonKey) ?: Json.encodeToString(initialValue)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get initial JSON for key '$jsonKey', using initial value")
            Json.encodeToString(initialValue)
        }

        return savedStateHandle.getStateFlow(jsonKey, initialJson)
            .map { json ->
                try {
                    val deserializedValue = Json.decodeFromString<T>(json)
                    if (validator(deserializedValue)) {
                        Timber.d("Complex state restored for key '$key': $deserializedValue")
                        deserializedValue
                    } else {
                        Timber.w("Invalid complex state for key '$key', using initial value")
                        updateComplexSavedState(key, initialValue)
                        initialValue
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to deserialize state for key '$key', using initial value")
                    updateComplexSavedState(key, initialValue)
                    initialValue
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = try {
                    Json.decodeFromString<T>(initialJson)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to deserialize initial value for key '$key'")
                    initialValue
                }
            )
    }

    /**
     * Updates a complex saved state object using JSON serialization.
     * 
     * @param T The type of the complex object
     * @param key The state key to update
     * @param value The new complex object to persist
     */
    protected inline fun <reified T> updateComplexSavedState(key: String, value: T) where T : @Serializable Any {
        try {
            val jsonKey = "${key}_json"
            val serializedValue = Json.encodeToString(value)
            savedStateHandle[jsonKey] = serializedValue
            Timber.d("Complex state updated for key '$key': $value")
        } catch (e: Exception) {
            Timber.e(e, "Failed to serialize and update complex state for key '$key'")
        }
    }

    /**
     * Gets a saved state value directly without StateFlow wrapper.
     * 
     * Useful for one-time state retrieval or initialization logic.
     * 
     * @param T The type of the state value
     * @param key The state key to retrieve
     * @param defaultValue Default value if key doesn't exist
     * @return The saved state value or default value
     */
    protected fun <T> getSavedState(key: String, defaultValue: T): T {
        return savedStateHandle.get<T>(key) ?: defaultValue
    }

    /**
     * Clears a specific saved state entry.
     * 
     * @param key The state key to clear
     */
    protected fun clearSavedState(key: String) {
        try {
            savedStateHandle.remove<Any>(key)
            Timber.d("Cleared saved state for key '$key'")
        } catch (e: Exception) {
            Timber.e(e, "Failed to clear saved state for key '$key'")
        }
    }

    /**
     * Clears all saved state entries.
     * 
     * Useful when user signs out or when state needs to be completely reset.
     * Note: This will clear ALL state for this ViewModel, including version info.
     */
    protected fun clearAllSavedState() {
        try {
            savedStateHandle.keys().forEach { key ->
                savedStateHandle.remove<Any>(key)
            }
            Timber.d("Cleared all saved state for ${this::class.simpleName}")
        } catch (e: Exception) {
            Timber.e(e, "Failed to clear all saved state")
        }
    }

    /**
     * Initializes state versioning system for handling app updates.
     */
    private fun initializeStateVersioning() {
        val savedVersion = getSavedState(STATE_VERSION_KEY, 0)
        
        if (savedVersion < CURRENT_STATE_VERSION) {
            Timber.i("State version upgrade from $savedVersion to $CURRENT_STATE_VERSION")
            handleStateVersionUpgrade(savedVersion, CURRENT_STATE_VERSION)
            updateSavedState(STATE_VERSION_KEY, CURRENT_STATE_VERSION)
        }
    }

    /**
     * Handles state version upgrades when app is updated.
     * 
     * Override this method in concrete ViewModels to handle state migration
     * when the state structure changes between app versions.
     * 
     * @param fromVersion The previous state version
     * @param toVersion The current state version
     */
    protected open fun handleStateVersionUpgrade(fromVersion: Int, toVersion: Int) {
        // Default implementation - override in subclasses for custom migration logic
        Timber.d("State version upgrade handled: $fromVersion -> $toVersion")
    }

    /**
     * Implementation of StateCleanupAware interface for sign out handling
     * 
     * Called when user signs out - clears all persisted state.
     * Override this method to perform custom cleanup operations before
     * state is cleared.
     */
    override fun onUserSignOut() {
        Timber.i("User sign out - clearing all saved state for ${getViewModelId()}")
        clearAllSavedState()
    }
    
    /**
     * Provides a unique identifier for this ViewModel instance
     */
    override fun getViewModelId(): String {
        return this::class.simpleName ?: "StatefulDetailViewModel"
    }

    /**
     * Gets the saved scroll position for the screen.
     * 
     * @param key Optional custom key, defaults to generic scroll position key
     * @return Saved scroll position or 0 if not saved
     */
    fun getSavedScrollPosition(key: String = "scroll_position"): Int {
        return getSavedState(key, 0)
    }

    /**
     * Saves the current scroll position for the screen.
     * 
     * @param position Current scroll position to save
     * @param key Optional custom key, defaults to generic scroll position key
     */
    fun saveScrollPosition(position: Int, key: String = "scroll_position") {
        updateSavedState(key, position)
    }

    override fun onCleared() {
        super.onCleared()
        Timber.d("StatefulDetailViewModel cleared: ${this::class.simpleName}")
    }
}

/**
 * Object containing standardized state keys for detail screens.
 * 
 * Centralizes state key definitions to prevent conflicts and ensure consistency
 * across different detail ViewModels. Only includes keys for non-deprecated widgets.
 */
object DetailScreenStateKeys {
    // VolumeAnalysisDetailScreen state keys
    const val VOLUME_TIME_RANGE = "volume_time_range"
    const val VOLUME_GROUP_BY = "volume_group_by"
    const val VOLUME_SHOW_PROJECTIONS = "volume_show_projections"
    const val VOLUME_SCROLL_POSITION = "volume_scroll_position"
    
    // OneRmProgressionDetailScreen state keys
    const val ONE_RM_TIME_RANGE = "one_rm_time_range"
    const val ONE_RM_SELECTED_EXERCISES = "one_rm_selected_exercises"
    const val ONE_RM_SHOW_ESTIMATED = "one_rm_show_estimated"
    const val ONE_RM_FILTER_EXPANDED = "one_rm_filter_expanded"
    
    // MuscleGroupDetailScreen state keys
    const val MUSCLE_GROUP_SELECTED = "muscle_group_selected"
    const val MUSCLE_GROUP_TIME_RANGE = "muscle_group_time_range"
    const val MUSCLE_GROUP_SHOW_BALANCE = "muscle_group_show_balance"
    
    // WorkoutFrequencyDetailScreen state keys (non-deprecated components only)
    const val FREQUENCY_SELECTED_MONTH = "frequency_selected_month"
    const val FREQUENCY_VIEW_MODE = "frequency_view_mode"
    
    // ExerciseRankingDetailScreen state keys
    const val EXERCISE_RANKING_SORT_ORDER = "exercise_ranking_sort_order"
    const val EXERCISE_RANKING_FILTER_CRITERIA = "exercise_ranking_filter_criteria"
    const val EXERCISE_RANKING_SELECTED_METRICS = "exercise_ranking_selected_metrics"
    const val EXERCISE_RANKING_TIME_RANGE = "exercise_ranking_time_range"
    
    // Common scroll position keys
    const val SCROLL_POSITION_SUFFIX = "_scroll_position"
    
    // Common filter expansion keys
    const val FILTER_EXPANDED_SUFFIX = "_filter_expanded"
    
    /**
     * Generates a scroll position key for a specific screen.
     * 
     * @param screenPrefix The screen identifier prefix
     * @return The complete scroll position state key
     */
    fun scrollPositionKey(screenPrefix: String): String = "${screenPrefix}${SCROLL_POSITION_SUFFIX}"
    
    /**
     * Generates a filter expanded state key for a specific screen.
     * 
     * @param screenPrefix The screen identifier prefix
     * @return The complete filter expanded state key
     */
    fun filterExpandedKey(screenPrefix: String): String = "${screenPrefix}${FILTER_EXPANDED_SUFFIX}"
}
