package com.example.liftrix.core.extensions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import timber.log.Timber
import com.example.liftrix.BuildConfig

/**
 * Performance optimization extensions for Liftrix application.
 * 
 * Provides utilities for optimizing Compose recomposition, memory usage,
 * and rendering performance. Follows Clean Architecture patterns with
 * proper lifecycle management and debugging capabilities.
 * 
 * Key optimizations:
 * - Smart state derivation with automatic memoization
 * - Flow collection optimizations with lifecycle awareness
 * - Recomposition reduction utilities
 * - Memory-efficient state management
 * - Performance debugging helpers for development builds
 */

/**
 * Creates a derived state that only recomposes when the calculation result changes.
 * More efficient than regular derivedStateOf for expensive calculations.
 * 
 * @param calculation Function to compute the derived value
 * @return State that updates only when the result changes
 */
@Composable
fun <T> rememberDerivedStateOf(
    vararg keys: Any?,
    calculation: () -> T
): State<T> {
    return remember(*keys) {
        derivedStateOf(calculation)
    }
}

/**
 * Memoized state that persists across recompositions with optimization logging.
 * Includes debug logging for tracking state updates in development builds.
 * 
 * @param key Unique key for this memoized state
 * @param initialValue Initial value factory
 * @return Mutable state that persists across recompositions
 */
@Composable
fun <T> rememberMemoizedState(
    key: String,
    initialValue: () -> T
): MutableState<T> {
    val state = remember(key) { mutableStateOf(initialValue()) }
    
    if (BuildConfig.DEBUG) {
        LaunchedEffect(state.value) {
            Timber.v("PerformanceExtensions: State updated for key '$key': ${state.value}")
        }
    }
    
    return state
}

/**
 * Collect flow with lifecycle awareness and recomposition optimization.
 * Only collects when lifecycle is at least STARTED and stops collection
 * when lifecycle drops below STARTED.
 * 
 * @param flow Flow to collect
 * @param minActiveState Minimum lifecycle state for collection
 * @return Current value from the flow
 */
@Composable
fun <T> Flow<T>.collectAsLifecycleAwareState(
    minActiveState: Lifecycle.State = Lifecycle.State.STARTED
): State<T?> {
    val lifecycleOwner = LocalLifecycleOwner.current
    val state = remember { mutableStateOf<T?>(null) }
    
    LaunchedEffect(this, lifecycleOwner.lifecycle) {
        lifecycleOwner.repeatOnLifecycle(minActiveState) {
            this@collectAsLifecycleAwareState
                .distinctUntilChanged()
                .collect { value ->
                    state.value = value
                }
        }
    }
    
    return state
}

/**
 * Optimized state collection for performance-critical components.
 * Uses distinctUntilChanged to prevent unnecessary recompositions.
 * 
 * @param flow Flow to collect
 * @param equalityComparator Custom equality comparator (optional)
 * @return State with optimized recomposition behavior
 */
@Composable
fun <T> Flow<T>.collectAsOptimizedState(
    equalityComparator: ((T, T) -> Boolean)? = null
): State<T?> {
    val state = remember { mutableStateOf<T?>(null) }
    
    LaunchedEffect(this) {
        val distinctFlow = if (equalityComparator != null) {
            this@collectAsOptimizedState.distinctUntilChanged(equalityComparator)
        } else {
            this@collectAsOptimizedState.distinctUntilChanged()
        }
        
        distinctFlow.collect { value ->
            state.value = value
        }
    }
    
    return state
}

/**
 * Remember expensive calculation with automatic memoization.
 * Includes performance tracking in debug builds.
 * 
 * @param calculation Expensive calculation to memoize
 * @return Memoized result
 */
@Composable
fun <T> rememberExpensiveCalculation(
    vararg keys: Any?,
    calculation: () -> T
): T {
    return remember(*keys) {
        val startTime = if (BuildConfig.DEBUG) System.currentTimeMillis() else 0
        
        val result = calculation()
        
        if (BuildConfig.DEBUG) {
            val duration = System.currentTimeMillis() - startTime
            if (duration > 16) { // Longer than one frame at 60fps
                Timber.w("PerformanceExtensions: Expensive calculation took ${duration}ms - " +
                        "consider optimization or moving to background thread")
            }
        }
        
        result
    }
}

/**
 * Stable wrapper for lambda functions to prevent unnecessary recompositions.
 * Use when passing callbacks to child composables.
 * 
 * @param callback Lambda function to stabilize
 * @return Stable callback that won't trigger recompositions
 */
@Composable
fun <T> rememberStableCallback(
    vararg keys: Any?,
    callback: (T) -> Unit
): (T) -> Unit {
    return remember(*keys) { callback }
}

/**
 * Optimized list state that only recomposes when list content changes.
 * Useful for large lists where reference equality isn't sufficient.
 * 
 * @param list List to track
 * @param keySelector Function to extract unique key from each item
 * @return State that updates only when list content changes
 */
@Composable
fun <T, K> List<T>.rememberOptimizedListState(
    keySelector: (T) -> K
): State<List<T>> {
    val listKeys by rememberDerivedStateOf { 
        this.map(keySelector) 
    }
    
    return remember(listKeys) { 
        mutableStateOf(this) 
    }
}

/**
 * Performance-optimized conditional rendering.
 * Only recomposes content when condition actually changes.
 * 
 * @param condition Condition to evaluate
 * @param content Content to render when condition is true
 */
@Composable
fun OptimizedConditional(
    condition: Boolean,
    content: @Composable () -> Unit
) {
    val stableCondition by rememberDerivedStateOf { condition }
    
    if (stableCondition) {
        content()
    }
}

/**
 * Debug performance tracker for development builds.
 * Tracks component render time and recomposition count.
 * 
 * @param componentName Name of the component for logging
 */
@Composable
fun DebugPerformanceTracker(componentName: String) {
    if (BuildConfig.DEBUG) {
        val renderCount = remember { mutableStateOf(0) }
        val startTime = remember { System.currentTimeMillis() }
        
        LaunchedEffect(Unit) {
            renderCount.value++
            
            val currentTime = System.currentTimeMillis()
            val totalTime = currentTime - startTime
            
            if (renderCount.value > 5) {
                Timber.d("PerformanceExtensions: $componentName rendered ${renderCount.value} times " +
                        "in ${totalTime}ms (avg: ${totalTime / renderCount.value}ms per render)")
            }
        }
    }
}

/**
 * Memory-efficient widget data provider with caching.
 * Caches widget data calculations to prevent repeated expensive operations.
 * 
 * @param widgets List of widgets
 * @param dataProvider Function to provide data for each widget
 * @return Cached data map
 */
@Composable
fun <W, D> rememberWidgetDataCache(
    widgets: List<W>,
    dataProvider: (W) -> D
): Map<W, D> {
    val widgetKeys by rememberDerivedStateOf { 
        widgets.hashCode() 
    }
    
    return remember(widgetKeys) {
        widgets.associateWith { widget ->
            dataProvider(widget)
        }
    }
}

/**
 * Optimized state management for large collections.
 * Uses lazy initialization and memory-efficient updates.
 * 
 * @param items Collection items
 * @param keySelector Function to extract unique key
 * @param stateFactory Factory for creating state per item
 * @return Map of item keys to their states
 */
@Composable
fun <T, K, S> rememberCollectionState(
    items: List<T>,
    keySelector: (T) -> K,
    stateFactory: (T) -> S
): Map<K, S> {
    val itemKeys by rememberDerivedStateOf { 
        items.map(keySelector).toSet() 
    }
    
    return remember(itemKeys) {
        items.associate { item ->
            keySelector(item) to stateFactory(item)
        }
    }
}

/**
 * Performance monitoring utilities for debug builds
 */
object PerformanceUtils {
    
    /**
     * Measure execution time of a block in debug builds
     * @param operationName Name of the operation for logging
     * @param block Block to measure
     * @return Result of the block execution
     */
    inline fun <T> measureTime(
        operationName: String,
        block: () -> T
    ): T {
        return if (BuildConfig.DEBUG) {
            val startTime = System.currentTimeMillis()
            val result = block()
            val duration = System.currentTimeMillis() - startTime
            
            if (duration > 16) { // Longer than one frame
                Timber.w("PerformanceUtils: $operationName took ${duration}ms")
            }
            
            result
        } else {
            block()
        }
    }
    
    /**
     * Log recomposition for debugging
     * @param componentName Component name
     * @param reason Reason for recomposition
     */
    fun logRecomposition(componentName: String, reason: String = "unknown") {
        if (BuildConfig.DEBUG) {
            Timber.v("PerformanceUtils: Recomposition - $componentName ($reason)")
        }
    }
}

/**
 * Widget-specific performance extensions
 */
object WidgetPerformanceExt {
    
    /**
     * Optimized widget rendering with performance tracking
     * @param widgetId Widget identifier
     * @param renderBlock Widget rendering block
     */
    @Composable
    fun OptimizedWidgetRender(
        widgetId: String,
        renderBlock: @Composable () -> Unit
    ) {
        DebugPerformanceTracker("Widget_$widgetId")
        
        val startTime = if (BuildConfig.DEBUG) System.currentTimeMillis() else 0
        
        renderBlock()
        
        if (BuildConfig.DEBUG) {
            LaunchedEffect(Unit) {
                val renderTime = System.currentTimeMillis() - startTime
                if (renderTime > 16) { // Longer than 60fps frame
                    Timber.w("WidgetPerformanceExt: Widget $widgetId render took ${renderTime}ms")
                }
            }
        }
    }
    
    /**
     * Memory-efficient widget data loading
     * @param widgetId Widget identifier
     * @param loadData Data loading function
     * @return Loaded data with caching
     */
    @Composable
    fun <T> rememberWidgetData(
        widgetId: String,
        loadData: () -> T
    ): T {
        return rememberExpensiveCalculation(widgetId) {
            PerformanceUtils.measureTime("LoadWidgetData_$widgetId") {
                loadData()
            }
        }
    }
}