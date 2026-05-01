package com.example.liftrix.ui.common.state

/**
 * Immutable data class for tracking multiple concurrent loading operations.
 * 
 * LoadingState provides a set-based storage mechanism for tracking multiple concurrent
 * operations that can be in progress simultaneously. This is particularly useful for
 * ViewModels that handle multiple data sources or operations that can overlap.
 * 
 * Key Features:
 * - Immutable operations tracking with functional state updates
 * - Thread-safe Set-based operation storage
 * - Efficient query methods for checking loading states
 * - Integration with existing UiState and AsyncData patterns
 * - Memory-efficient design for large operation sets
 * 
 * Usage Examples:
 * ```kotlin
 * // Initial state
 * var loadingState = LoadingState()
 * 
 * // Start loading operations
 * loadingState = loadingState.withOperation("charts")
 * loadingState = loadingState.withOperation("widgets")
 * 
 * // Check loading status
 * if (loadingState.isLoading()) {
 *     // Show loading indicator
 * }
 * 
 * if (loadingState.isLoading("charts")) {
 *     // Show charts loading
 * }
 * 
 * // Complete operations
 * loadingState = loadingState.withoutOperation("charts")
 * 
 * // Get active operations for debugging
 * val activeOps = loadingState.getActiveOperations()
 * ```
 * 
 * Integration with ViewModels:
 * ```kotlin
 * class MyViewModel : ViewModel() {
 *     private val _loadingState = MutableStateFlow(LoadingState())
 *     val loadingState = _loadingState.asStateFlow()
 *     
 *     fun loadData() {
 *         _loadingState.value = _loadingState.value.withOperation("data")
 *         // ... load data
 *         _loadingState.value = _loadingState.value.withoutOperation("data")
 *     }
 * }
 * ```
 * 
 * @param operations Set of currently active operation identifiers
 */
data class LoadingState(
    val operations: Set<String> = emptySet()
) {
    
    /**
     * Checks if any loading operation is currently active.
     * 
     * @return true if there are any active operations, false otherwise
     */
    fun isLoading(): Boolean = operations.isNotEmpty()
    
    /**
     * Checks if a specific loading operation is currently active.
     * 
     * @param operation The operation identifier to check
     * @return true if the operation is active, false otherwise
     */
    fun isLoading(operation: String): Boolean = operations.contains(operation)
    
    /**
     * Creates a new LoadingState with an additional operation.
     * 
     * This is an immutable operation that returns a new LoadingState instance
     * with the specified operation added to the active operations set.
     * 
     * @param operation The operation identifier to add
     * @return New LoadingState with the operation added
     */
    fun withOperation(operation: String): LoadingState {
        return copy(operations = operations + operation)
    }
    
    /**
     * Creates a new LoadingState with an operation removed.
     * 
     * This is an immutable operation that returns a new LoadingState instance
     * with the specified operation removed from the active operations set.
     * If the operation was not active, returns the same LoadingState.
     * 
     * @param operation The operation identifier to remove
     * @return New LoadingState with the operation removed
     */
    fun withoutOperation(operation: String): LoadingState {
        return copy(operations = operations - operation)
    }
    
    /**
     * Creates a new LoadingState with multiple operations added.
     * 
     * @param operations The operation identifiers to add
     * @return New LoadingState with all operations added
     */
    fun withOperations(vararg operations: String): LoadingState {
        return copy(operations = this.operations + operations.toSet())
    }
    
    /**
     * Creates a new LoadingState with multiple operations removed.
     * 
     * @param operations The operation identifiers to remove
     * @return New LoadingState with all operations removed
     */
    fun withoutOperations(vararg operations: String): LoadingState {
        return copy(operations = this.operations - operations.toSet())
    }
    
    /**
     * Gets a copy of all currently active operations.
     * 
     * Returns an immutable Set of operation identifiers that are currently active.
     * This is useful for debugging, logging, or UI state management.
     * 
     * @return Immutable Set of active operation identifiers
     */
    fun getActiveOperations(): Set<String> = operations.toSet()
    
    /**
     * Gets the count of currently active operations.
     * 
     * @return Number of active operations
     */
    fun getOperationCount(): Int = operations.size
    
    /**
     * Checks if the LoadingState has no active operations.
     * 
     * @return true if no operations are active, false otherwise
     */
    fun isEmpty(): Boolean = operations.isEmpty()
    
    /**
     * Checks if the LoadingState has any active operations.
     * 
     * @return true if any operations are active, false otherwise
     */
    fun isNotEmpty(): Boolean = operations.isNotEmpty()
    
    /**
     * Creates a new LoadingState with all operations cleared.
     * 
     * @return New LoadingState with no active operations
     */
    fun clear(): LoadingState = LoadingState()
    
    /**
     * Applies a transformation to the operations set.
     * 
     * This is an advanced method that allows custom transformations of the
     * operations set while maintaining immutability.
     * 
     * @param transform Function to transform the operations set
     * @return New LoadingState with transformed operations
     */
    inline fun transform(transform: (Set<String>) -> Set<String>): LoadingState {
        return copy(operations = transform(operations))
    }
    
    /**
     * Combines this LoadingState with another LoadingState.
     * 
     * The resulting LoadingState will have all operations from both states.
     * This is useful for merging loading states from different sources.
     * 
     * @param other The other LoadingState to combine with
     * @return New LoadingState with combined operations
     */
    fun combine(other: LoadingState): LoadingState {
        return copy(operations = operations + other.operations)
    }
    
    /**
     * Filters operations based on a predicate.
     * 
     * @param predicate Function to test each operation
     * @return New LoadingState with filtered operations
     */
    inline fun filter(predicate: (String) -> Boolean): LoadingState {
        return copy(operations = operations.filter(predicate).toSet())
    }
    
    /**
     * Checks if any operation matches the given predicate.
     * 
     * @param predicate Function to test each operation
     * @return true if any operation matches, false otherwise
     */
    inline fun any(predicate: (String) -> Boolean): Boolean {
        return operations.any(predicate)
    }
    
    /**
     * Checks if all operations match the given predicate.
     * 
     * @param predicate Function to test each operation
     * @return true if all operations match, false otherwise
     */
    inline fun all(predicate: (String) -> Boolean): Boolean {
        return operations.all(predicate)
    }
    
    /**
     * Checks if no operations match the given predicate.
     * 
     * @param predicate Function to test each operation
     * @return true if no operations match, false otherwise
     */
    inline fun none(predicate: (String) -> Boolean): Boolean {
        return operations.none(predicate)
    }
    
    /**
     * Provides a string representation of the LoadingState for debugging.
     * 
     * @return String representation showing active operations
     */
    override fun toString(): String {
        return if (operations.isEmpty()) {
            "LoadingState(no active operations)"
        } else {
            "LoadingState(active operations: ${operations.sorted()})"
        }
    }
}

/**
 * Extension functions for creating LoadingState instances.
 */

/**
 * Creates a LoadingState with a single operation.
 * 
 * @param operation The operation identifier
 * @return LoadingState with the operation active
 */
fun loadingStateOf(operation: String): LoadingState = LoadingState(setOf(operation))

/**
 * Creates a LoadingState with multiple operations.
 * 
 * @param operations The operation identifiers
 * @return LoadingState with all operations active
 */
fun loadingStateOf(vararg operations: String): LoadingState = LoadingState(operations.toSet())

/**
 * Creates a LoadingState with operations from a collection.
 * 
 * @param operations The operation identifiers
 * @return LoadingState with all operations active
 */
fun loadingStateOf(operations: Collection<String>): LoadingState = LoadingState(operations.toSet())

/**
 * Creates an empty LoadingState.
 * 
 * @return LoadingState with no active operations
 */
fun emptyLoadingState(): LoadingState = LoadingState()

/**
 * Extension functions for common LoadingState operations.
 */

/**
 * Converts a String to a LoadingState with that operation active.
 * 
 * @return LoadingState with this string as an active operation
 */
fun String.asLoadingState(): LoadingState = loadingStateOf(this)

/**
 * Converts a Collection of Strings to a LoadingState with those operations active.
 * 
 * @return LoadingState with all strings as active operations
 */
fun Collection<String>.asLoadingState(): LoadingState = loadingStateOf(this)

/**
 * Utility functions for common loading patterns.
 */

/**
 * Common operation identifiers for consistent usage across the application.
 */
object LoadingOperations {
    const val CHARTS = "charts"
    const val WIDGETS = "widgets"
    const val SUMMARY = "summary"
    const val CALORIES = "calories"
    const val PREFERENCES = "preferences"
    const val EXPORT = "export"
    const val SYNC = "sync"
    const val REFRESH = "refresh"
    const val INITIAL_LOAD = "initial_load"
    const val BACKGROUND_REFRESH = "background_refresh"
}

/**
 * Creates a LoadingState with common chart loading operations.
 * 
 * @return LoadingState with chart operations active
 */
fun chartLoadingState(): LoadingState = loadingStateOf(
    LoadingOperations.CHARTS,
    LoadingOperations.WIDGETS,
    LoadingOperations.SUMMARY
)

/**
 * Creates a LoadingState with common refresh operations.
 * 
 * @return LoadingState with refresh operations active
 */
fun refreshLoadingState(): LoadingState = loadingStateOf(
    LoadingOperations.REFRESH,
    LoadingOperations.SYNC
)

/**
 * Creates a LoadingState with initial load operations.
 * 
 * @return LoadingState with initial load operations active
 */
fun initialLoadingState(): LoadingState = loadingStateOf(
    LoadingOperations.INITIAL_LOAD,
    LoadingOperations.PREFERENCES
)