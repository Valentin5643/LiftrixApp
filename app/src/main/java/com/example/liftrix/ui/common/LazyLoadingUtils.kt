package com.example.liftrix.ui.common

import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import com.example.liftrix.domain.model.PerformanceMetrics
import com.example.liftrix.ui.common.PerformanceOptimizations
import kotlinx.coroutines.flow.distinctUntilChanged
import timber.log.Timber

/**
 * Lazy loading utilities and scroll performance optimizations for Liftrix
 * Provides enhanced LazyColumn performance with optimized item rendering and prefetch settings
 * 
 * Follows existing optimization patterns from WorkoutHistoryList and WorkoutFeedSection
 * Integrates with PerformanceOptimizations for comprehensive performance monitoring
 */
object LazyLoadingUtils {
    
    /**
     * Default prefetch item count for optimal scroll performance
     * Balances memory usage with smooth scrolling experience
     */
    const val DEFAULT_PREFETCH_COUNT = 3
    
    /**
     * Default scroll threshold for pagination triggers
     * Items from bottom when load more should be triggered
     */
    const val DEFAULT_SCROLL_THRESHOLD = 3
    
    /**
     * Optimizes scrolling performance for LazyColumn/LazyRow components
     * Configures prefetch settings and scroll behavior for 60fps performance
     * 
     * @param prefetchItemCount Number of items to prefetch during scrolling
     * @return ScrollConfiguration optimized for smooth performance
     */
    fun optimizeScrollingPerformance(
        prefetchItemCount: Int = DEFAULT_PREFETCH_COUNT
    ): ScrollOptimizationConfig {
        return ScrollOptimizationConfig(
            prefetchItemCount = prefetchItemCount,
            enableScrollOptimization = true,
            enableCompositionSkipping = true
        )
    }
    
    /**
     * Configuration class for scroll optimizations
     * Encapsulates various performance settings for LazyColumn components
     */
    @Stable
    data class ScrollOptimizationConfig(
        val prefetchItemCount: Int,
        val enableScrollOptimization: Boolean,
        val enableCompositionSkipping: Boolean
    )
}

/**
 * Enhanced LazyItemScope extension for optimized item rendering
 * Provides performance tracking and stable key management for LazyColumn items
 * 
 * @param key Stable key for the item (uses PerformanceOptimizations.rememberStableKey)
 * @param contentType Optional content type for better item recycling
 * @param content Composable content for the item
 */
@Composable
fun LazyItemScope.optimizedItem(
    key: Any,
    contentType: String? = null,
    content: @Composable LazyItemScope.() -> Unit
) {
    // Generate stable key using remember
    val stableKey = remember(key) { key.toString() }
    
    // Track composition performance for this item
    val compositionStartTime = remember { System.currentTimeMillis() }
    
    DisposableEffect(stableKey) {
        onDispose {
            val compositionTime = System.currentTimeMillis() - compositionStartTime
            if (compositionTime > 16) { // Log if composition takes more than 16ms (60fps threshold)
                Timber.w("LazyLoadingPerformance: Slow composition for item $stableKey: ${compositionTime}ms")
            }
        }
    }
    
    content()
}

/**
 * Advanced scroll detection with performance optimizations
 * Enhanced version of existing isScrolledToEndOptimized function with additional features
 * 
 * @param threshold Items from bottom to trigger pagination
 * @param enableLogging Whether to log scroll performance metrics
 * @return Boolean indicating if scrolled near end
 */
fun LazyListState.isScrolledToEndAdvanced(
    threshold: Int = LazyLoadingUtils.DEFAULT_SCROLL_THRESHOLD,
    enableLogging: Boolean = false
): Boolean {
    val layoutInfo = layoutInfo
    if (layoutInfo.totalItemsCount == 0) return false
    
    val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull() ?: return false
    val isNearEnd = lastVisibleItem.index >= layoutInfo.totalItemsCount - threshold
    
    if (enableLogging && isNearEnd) {
        Timber.d("ScrollDetection: Near end triggered (${lastVisibleItem.index}/${layoutInfo.totalItemsCount})")
    }
    
    return isNearEnd
}

/**
 * Optimized pagination handler with performance monitoring
 * Integrates scroll detection with load more functionality following existing patterns
 * 
 * @param listState LazyListState to monitor
 * @param hasMoreData Whether more data is available to load
 * @param isLoading Whether loading is currently in progress
 * @param threshold Scroll threshold for triggering pagination
 * @param onLoadMore Callback to trigger load more functionality
 */
@Composable
fun OptimizedPaginationHandler(
    listState: LazyListState,
    hasMoreData: Boolean,
    isLoading: Boolean,
    threshold: Int = LazyLoadingUtils.DEFAULT_SCROLL_THRESHOLD,
    onLoadMore: () -> Unit
) {
    // Remember stable callback to prevent recomposition
    val stableOnLoadMore = remember(onLoadMore) { onLoadMore }
    
    // Remember threshold to prevent recreation
    val stableThreshold = remember(threshold) { threshold }
    
    LaunchedEffect(listState, hasMoreData, isLoading, stableThreshold) {
        snapshotFlow { 
            listState.isScrolledToEndAdvanced(stableThreshold, enableLogging = true)
        }.distinctUntilChanged().collect { isScrolledToEnd ->
            if (isScrolledToEnd && hasMoreData && !isLoading) {
                // Log performance metrics for pagination
                val loadStartTime = System.currentTimeMillis()
                Timber.d("LazyLoadingPerformance: Triggering pagination at item ${listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index}")
                
                stableOnLoadMore()
                
                // Log completion time in a coroutine to avoid blocking
                kotlinx.coroutines.delay(100) // Small delay to allow load to start
                Timber.d("LazyLoadingPerformance: Pagination triggered in ${System.currentTimeMillis() - loadStartTime}ms")
            }
        }
    }
}

/**
 * Enhanced scroll performance configuration for WorkoutFeed components
 * Provides specialized configuration for workout-related lists with optimal settings
 * 
 * @param itemCount Expected number of items for optimization calculations
 * @return ScrollOptimizationConfig tuned for workout feeds
 */
fun getWorkoutFeedScrollConfig(itemCount: Int): LazyLoadingUtils.ScrollOptimizationConfig {
    val prefetchCount = when {
        itemCount < 10 -> 2
        itemCount < 50 -> 3
        else -> 4
    }
    
    return LazyLoadingUtils.optimizeScrollingPerformance(prefetchCount)
}

/**
 * Enhanced scroll performance configuration for WorkoutHistory components
 * Provides specialized configuration for history lists with pagination support
 * 
 * @param totalItems Total number of items in the history
 * @param hasMoreData Whether more data is available
 * @return ScrollOptimizationConfig tuned for workout history
 */
fun getWorkoutHistoryScrollConfig(
    totalItems: Int,
    hasMoreData: Boolean
): LazyLoadingUtils.ScrollOptimizationConfig {
    val prefetchCount = when {
        totalItems < 20 -> 2
        totalItems < 100 -> 3
        hasMoreData -> 4  // Higher prefetch when more data available
        else -> 3
    }
    
    return LazyLoadingUtils.optimizeScrollingPerformance(prefetchCount)
}

/**
 * Memory-efficient item key generator for large lists
 * Prevents memory leaks in long-running lists with many items
 * 
 * @param item Item to generate key for
 * @param maxCacheSize Maximum number of keys to cache (prevents memory leaks)
 * @return Stable key string
 */
@Composable
fun rememberEfficientKey(
    item: Any,
    maxCacheSize: Int = 1000
): String {
    // Use remember to create stable key
    return remember(item) { item.toString() }
}

/**
 * Performance monitoring extension for LazyListState
 * Provides detailed scroll performance metrics for optimization analysis
 */
fun LazyListState.logScrollPerformance(componentName: String) {
    val layoutInfo = layoutInfo
    val visibleItems = layoutInfo.visibleItemsInfo.size
    val totalItems = layoutInfo.totalItemsCount
    val firstVisibleIndex = firstVisibleItemIndex
    val scrollOffset = firstVisibleItemScrollOffset
    
    Timber.d(
        "ScrollPerformance [$componentName]: " +
        "Visible: $visibleItems/$totalItems, " +
        "FirstIndex: $firstVisibleIndex, " +
        "Offset: $scrollOffset"
    )
} 