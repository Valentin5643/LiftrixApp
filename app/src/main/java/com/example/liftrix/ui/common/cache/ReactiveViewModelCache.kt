package com.example.liftrix.ui.common.cache

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.liftrix.core.cache.CacheKey
import com.example.liftrix.core.cache.CacheKeyGenerator
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.service.CachedData
import com.example.liftrix.service.ReactiveCacheService
import com.example.liftrix.ui.common.state.AsyncData
import com.example.liftrix.ui.common.state.UiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.hours

/**
 * Reactive cache integration layer for ViewModels with automatic UI state management.
 * 
 * This class provides a bridge between the reactive caching system and ViewModel
 * UI state patterns, offering:
 * - Automatic conversion between CachedData and UiState
 * - Reactive data streams with loading/cached/fresh states
 * - Lifecycle-aware caching with ViewModel scope integration
 * - Error handling with proper UI state mapping
 * - Performance monitoring and cache hit tracking
 * 
 * Key Features:
 * - Seamless integration with existing UiState patterns
 * - Automatic loading state management
 * - Cached vs fresh data visual indicators
 * - Error recovery with retry mechanisms
 * - Performance analytics for cache effectiveness
 * 
 * Usage in ViewModels:
 * ```kotlin
 * class MyViewModel @Inject constructor(
 *     private val viewModelCache: ReactiveViewModelCache,
 *     private val dataService: MyDataService
 * ) : ViewModel() {
 * 
 *     val dataState = viewModelCache.createReactiveState(
 *         key = CacheKeyGenerator.volumeKey(userId, timeRange).first,
 *         source = { dataService.getData(userId, timeRange) },
 *         scope = viewModelScope
 *     )
 * }
 * ```
 */
@Singleton
class ReactiveViewModelCache @Inject constructor(
    private val reactiveCacheService: ReactiveCacheService
) {
    
    companion object {
        private const val TAG = "ReactiveViewModelCache"
    }
    
    /**
     * Creates a reactive StateFlow that automatically manages UI states with caching.
     * 
     * This method provides the primary integration point between reactive caching
     * and ViewModel UI patterns:
     * - Loading: Initial state while data is being fetched
     * - Success: Data loaded successfully (with cached/fresh indicators)
     * - Error: Error occurred during data loading
     * 
     * @param key Cache key for the data
     * @param source Suspend function to load fresh data
     * @param scope CoroutineScope for the reactive stream (typically viewModelScope)
     * @param ttl Optional TTL override (uses intelligent TTL by default)
     * @param refreshOnInvalidate Whether to refresh data when cache is invalidated
     * @return StateFlow of UiState with reactive cache integration
     */
    suspend fun <T> createReactiveState(
        key: CacheKey,
        source: suspend () -> LiftrixResult<T>,
        scope: CoroutineScope,
        typeClass: Class<T>,
        ttl: Duration? = null,
        refreshOnInvalidate: Boolean = true
    ): StateFlow<UiState<ReactiveData<T>>> {
        
        val actualTtl = ttl ?: 15.minutes
        
        return reactiveCacheService.observeCachedData(
            key = key,
            ttl = actualTtl,
            typeClass = typeClass,
            source = source,
            refreshOnInvalidate = refreshOnInvalidate
        )
        .map { cachedData -> cachedData.toUiState() }
        .catch { throwable ->
            Timber.e(throwable, "$TAG: Error in reactive state for key: ${key.keyString}")
            emit(UiState.Error(throwable.toLiftrixError()))
        }
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UiState.Loading
        )
    }
    
    /**
     * Creates a reactive StateFlow for batch data operations.
     * 
     * Useful for dashboard screens that need to load multiple data sets:
     * - Widget dashboard with multiple analytics
     * - Chart screens with multiple time series
     * - Summary screens with various metrics
     * 
     * @param operations Map of cache keys to data source functions
     * @param scope CoroutineScope for the reactive stream
     * @param ttl TTL for all cache entries
     * @return StateFlow of batch operation results
     */
    suspend fun <T> createBatchReactiveState(
        operations: Map<CacheKey, suspend () -> LiftrixResult<T>>,
        scope: CoroutineScope,
        typeClass: Class<T>,
        ttl: Duration = 15.minutes
    ): StateFlow<UiState<BatchReactiveData<T>>> {
        
        return reactiveCacheService.observeBatchCachedData(operations, ttl, typeClass)
            .map { batchData -> batchData.toUiState() }
            .catch { throwable ->
                Timber.e(throwable, "$TAG: Error in batch reactive state")
                emit(UiState.Error(throwable.toLiftrixError()))
            }
            .stateIn(
                scope = scope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = UiState.Loading
            )
    }
    
    /**
     * Creates a hot reactive stream for real-time data.
     * 
     * Used for data that needs frequent updates:
     * - Current workout session progress
     * - Live analytics during workouts
     * - Real-time performance metrics
     * 
     * @param key Cache key for the data
     * @param source Data source function
     * @param scope CoroutineScope for the stream
     * @param refreshInterval How often to refresh the data
     * @return StateFlow of hot data updates
     */
    fun <T> createHotReactiveState(
        key: CacheKey,
        source: suspend () -> LiftrixResult<T>,
        scope: CoroutineScope,
        typeClass: Class<T>,
        refreshInterval: Duration = 30.minutes
    ): StateFlow<UiState<ReactiveData<T>>> {
        
        return reactiveCacheService.createHotDataStream(key, source, refreshInterval, typeClass)
            .map { cachedData -> cachedData.toUiState() }
            .catch { throwable ->
                Timber.e(throwable, "$TAG: Error in hot reactive state for key: ${key.keyString}")
                emit(UiState.Error(throwable.toLiftrixError()))
            }
            .stateIn(
                scope = scope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = UiState.Loading
            )
    }
    
    /**
     * Creates a cold reactive stream for one-time data loading.
     * 
     * Used for relatively static data:
     * - User preferences
     * - Configuration settings
     * - Historical analytics data
     * 
     * @param key Cache key for the data
     * @param source Data source function
     * @param scope CoroutineScope for the stream
     * @param ttl Cache TTL
     * @return StateFlow of cold data
     */
    fun <T> createColdReactiveState(
        key: CacheKey,
        source: suspend () -> LiftrixResult<T>,
        scope: CoroutineScope,
        typeClass: Class<T>,
        ttl: Duration = 1.hours
    ): StateFlow<UiState<ReactiveData<T>>> {
        
        return reactiveCacheService.createColdDataStream(key, source, ttl, typeClass)
            .map { cachedData -> cachedData.toUiState() }
            .catch { throwable ->
                Timber.e(throwable, "$TAG: Error in cold reactive state for key: ${key.keyString}")
                emit(UiState.Error(throwable.toLiftrixError()))
            }
            .stateIn(
                scope = scope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = UiState.Loading
            )
    }
    
    /**
     * Manually refreshes data for a specific cache key.
     * 
     * Can be used for pull-to-refresh or explicit refresh actions.
     * 
     * @param key Cache key to refresh
     * @param source Data source function
     * @param scope CoroutineScope for the operation
     * @return Flow of refresh operation result
     */
    fun <T> refreshData(
        key: CacheKey,
        source: suspend () -> LiftrixResult<T>,
        scope: CoroutineScope,
        typeClass: Class<T>
    ): Flow<UiState<T>> = flow {
        emit(UiState.Loading)
        
        val result = reactiveCacheService.refreshData(key, source, typeClass)
        result.fold(
            onSuccess = { data -> emit(UiState.Success(data)) },
            onFailure = { throwable -> emit(UiState.Error(throwable.toLiftrixError())) }
        )
    }.flowOn(scope.coroutineContext)
    
    /**
     * Extension function to combine multiple reactive states.
     * 
     * Useful for screens that depend on multiple data sources:
     * ```kotlin
     * val combinedState = viewModelCache.combineReactiveStates(
     *     volumeState,
     *     frequencyState
     * ) { volume, frequency ->
     *     DashboardData(volume, frequency)
     * }
     * ```
     */
    fun <T1, T2, R> combineReactiveStates(
        state1: StateFlow<UiState<ReactiveData<T1>>>,
        state2: StateFlow<UiState<ReactiveData<T2>>>,
        scope: CoroutineScope,
        transform: (T1, T2) -> R
    ): StateFlow<UiState<ReactiveData<R>>> {
        return combine(state1, state2) { s1, s2 ->
            when {
                s1 is UiState.Error -> UiState.Error(s1.error)
                s2 is UiState.Error -> UiState.Error(s2.error)
                s1 is UiState.Loading || s2 is UiState.Loading -> UiState.Loading
                s1 is UiState.Success && s2 is UiState.Success -> {
                    val combinedData = ReactiveData(
                        data = transform(s1.data.data, s2.data.data),
                        isCached = s1.data.isCached && s2.data.isCached,
                        timestamp = maxOf(s1.data.timestamp, s2.data.timestamp),
                        isLoading = s1.data.isLoading || s2.data.isLoading
                    )
                    UiState.Success(combinedData)
                }
                else -> UiState.Loading
            }
        }.stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UiState.Loading
        )
    }
}

/**
 * Data wrapper for reactive cache integration with UI state management.
 */
data class ReactiveData<T>(
    val data: T,
    val isCached: Boolean,
    val timestamp: Long,
    val isLoading: Boolean = false
) {
    /**
     * Indicates if this data came from cache vs fresh computation.
     */
    val isFresh: Boolean = !isCached
    
    /**
     * Age of the data in milliseconds.
     */
    val ageMillis: Long = System.currentTimeMillis() - timestamp
}

/**
 * Batch data wrapper for multiple reactive cache operations.
 */
data class BatchReactiveData<T>(
    val results: Map<CacheKey, ReactiveData<T>>,
    val completedCount: Int,
    val totalCount: Int,
    val isComplete: Boolean
) {
    val progress: Float = if (totalCount > 0) completedCount.toFloat() / totalCount else 0f
}

/**
 * Extension functions to convert between cache data types and UI states.
 */

/**
 * Converts CachedData to UiState with ReactiveData wrapper.
 */
private fun <T> CachedData<T>.toUiState(): UiState<ReactiveData<T>> {
    return when (this) {
        is CachedData.Loading -> UiState.Loading
        is CachedData.Cached -> UiState.Success(
            ReactiveData(
                data = data,
                isCached = true,
                timestamp = timestamp
            )
        )
        is CachedData.Fresh -> UiState.Success(
            ReactiveData(
                data = data,
                isCached = false,
                timestamp = timestamp
            )
        )
        is CachedData.Error -> UiState.Error(error.toLiftrixError())
        is CachedData.Invalidated -> UiState.Loading
    }
}

/**
 * Converts BatchCachedData to UiState with BatchReactiveData wrapper.
 */
private fun <T> com.example.liftrix.service.BatchCachedData<T>.toUiState(): UiState<BatchReactiveData<T>> {
    return when (this) {
        is com.example.liftrix.service.BatchCachedData.Loading -> UiState.Loading
        is com.example.liftrix.service.BatchCachedData.Partial -> UiState.Success(
            BatchReactiveData(
                results = results.mapValues { (_, cachedData) ->
                    when (cachedData) {
                        is CachedData.Cached -> ReactiveData(
                            data = cachedData.data,
                            isCached = true,
                            timestamp = cachedData.timestamp
                        )
                        is CachedData.Fresh -> ReactiveData(
                            data = cachedData.data,
                            isCached = false,
                            timestamp = cachedData.timestamp
                        )
                        is CachedData.Loading -> ReactiveData(
                            data = null as T, // Loading state doesn't have data yet
                            isCached = false,
                            timestamp = cachedData.timestamp,
                            isLoading = true
                        )
                        is CachedData.Error -> ReactiveData(
                            data = null as T, // Error state doesn't have valid data
                            isCached = false,
                            timestamp = cachedData.timestamp,
                            isLoading = false
                        )
                        is CachedData.Invalidated -> ReactiveData(
                            data = null as T, // Invalidated state doesn't have valid data
                            isCached = false,
                            timestamp = cachedData.timestamp,
                            isLoading = true // Invalidated means we're about to reload
                        )
                    }
                },
                completedCount = completedCount,
                totalCount = totalCount,
                isComplete = false
            )
        )
        is com.example.liftrix.service.BatchCachedData.Completed -> UiState.Success(
            BatchReactiveData(
                results = results.mapValues { (_, cachedData) ->
                    when (cachedData) {
                        is CachedData.Cached -> ReactiveData(
                            data = cachedData.data,
                            isCached = true,
                            timestamp = cachedData.timestamp
                        )
                        is CachedData.Fresh -> ReactiveData(
                            data = cachedData.data,
                            isCached = false,
                            timestamp = cachedData.timestamp
                        )
                        is CachedData.Loading -> ReactiveData(
                            data = null as T, // Loading state doesn't have data yet
                            isCached = false,
                            timestamp = cachedData.timestamp,
                            isLoading = true
                        )
                        is CachedData.Error -> ReactiveData(
                            data = null as T, // Error state doesn't have valid data
                            isCached = false,
                            timestamp = cachedData.timestamp,
                            isLoading = false
                        )
                        is CachedData.Invalidated -> ReactiveData(
                            data = null as T, // Invalidated state doesn't have valid data
                            isCached = false,
                            timestamp = cachedData.timestamp,
                            isLoading = true // Invalidated means we're about to reload
                        )
                    }
                },
                completedCount = results.size,
                totalCount = results.size,
                isComplete = true
            )
        )
    }
}

/**
 * Inline extension functions to provide reified type support.
 */

/**
 * Creates a reactive state with automatic type inference.
 */
suspend inline fun <reified T> ReactiveViewModelCache.createReactiveStateTyped(
    key: CacheKey,
    noinline source: suspend () -> LiftrixResult<T>,
    scope: CoroutineScope,
    ttl: Duration? = null,
    refreshOnInvalidate: Boolean = true
): StateFlow<UiState<ReactiveData<T>>> = createReactiveState(
    key, source, scope, T::class.java, ttl, refreshOnInvalidate
)

/**
 * Creates a batch reactive state with automatic type inference.
 */
suspend inline fun <reified T> ReactiveViewModelCache.createBatchReactiveStateTyped(
    operations: Map<CacheKey, suspend () -> LiftrixResult<T>>,
    scope: CoroutineScope,
    ttl: Duration = 15.minutes
): StateFlow<UiState<BatchReactiveData<T>>> = createBatchReactiveState(
    operations, scope, T::class.java, ttl
)

/**
 * Creates a hot reactive state with automatic type inference.
 */
inline fun <reified T> ReactiveViewModelCache.createHotReactiveStateTyped(
    key: CacheKey,
    noinline source: suspend () -> LiftrixResult<T>,
    scope: CoroutineScope,
    refreshInterval: Duration = 30.minutes
): StateFlow<UiState<ReactiveData<T>>> = createHotReactiveState(
    key, source, scope, T::class.java, refreshInterval
)

/**
 * Creates a cold reactive state with automatic type inference.
 */
inline fun <reified T> ReactiveViewModelCache.createColdReactiveStateTyped(
    key: CacheKey,
    noinline source: suspend () -> LiftrixResult<T>,
    scope: CoroutineScope,
    ttl: Duration = 1.hours
): StateFlow<UiState<ReactiveData<T>>> = createColdReactiveState(
    key, source, scope, T::class.java, ttl
)

/**
 * Refreshes data with automatic type inference.
 */
inline fun <reified T> ReactiveViewModelCache.refreshDataTyped(
    key: CacheKey,
    noinline source: suspend () -> LiftrixResult<T>,
    scope: CoroutineScope
): Flow<UiState<T>> = refreshData(key, source, scope, T::class.java)

/**
 * Reactive cache extensions for ViewModels.
 */
object ViewModelCacheExtensions {
    
    /**
     * Creates a simple reactive data flow for a single data source.
     * 
     * @param cache ReactiveViewModelCache instance
     * @param userId Current user ID
     * @param operation Operation name for cache key generation
     * @param source Data source function
     * @param scope ViewModel scope
     * @return StateFlow of UI state with reactive data
     */
    suspend inline fun <reified T> createSimpleReactiveFlow(
        cache: ReactiveViewModelCache,
        userId: String,
        operation: String,
        noinline source: suspend () -> LiftrixResult<T>,
        scope: CoroutineScope,
        ttl: Duration = 15.minutes
    ): StateFlow<UiState<ReactiveData<T>>> {
        val key = com.example.liftrix.core.cache.CacheKey.Operation(
            operation = operation,
            userId = userId,
            parameters = mapOf("version" to "v1")
        )
        
        return cache.createReactiveState(
            key = key,
            source = source,
            scope = scope,
            typeClass = T::class.java,
            ttl = ttl
        )
    }
}

/**
 * Helper functions for ViewModel integration.
 */

/**
 * Extension function for ViewModels to create reactive cached flows easily with type inference.
 */
suspend inline fun <reified T> ViewModel.reactiveFlowTyped(
    cache: ReactiveViewModelCache,
    key: CacheKey,
    ttl: Duration = 15.minutes,
    noinline source: suspend () -> LiftrixResult<T>
): StateFlow<UiState<ReactiveData<T>>> {
    return try {
        cache.createReactiveState(
            key = key,
            source = source,
            scope = viewModelScope,
            typeClass = T::class.java,
            ttl = ttl
        )
    } catch (e: Exception) {
        MutableStateFlow<UiState<ReactiveData<T>>>(UiState.Error(e.toLiftrixError())).asStateFlow()
    }
}

/**
 * Extension function for ViewModels to create reactive cached flows easily.
 */
fun <T> ViewModel.reactiveFlow(
    cache: ReactiveViewModelCache,
    key: CacheKey,
    typeClass: Class<T>,
    ttl: Duration = 15.minutes,
    source: suspend () -> LiftrixResult<T>
): StateFlow<UiState<ReactiveData<T>>> {
    return runCatching {
        kotlinx.coroutines.runBlocking {
            cache.createReactiveState(
                key = key,
                source = { source() },
                scope = viewModelScope,
                typeClass = typeClass,
                ttl = ttl
            )
        }
    }.getOrElse { 
        MutableStateFlow<UiState<ReactiveData<T>>>(UiState.Error(it.toLiftrixError())).asStateFlow()
    }
}

/**
 * Extension function to convert Throwable to LiftrixError for consistent error handling.
 */
fun Throwable.toLiftrixError(): LiftrixError {
    return when (this) {
        is LiftrixError -> this
        else -> LiftrixError.UnknownError(
            errorMessage = this.message ?: "Unknown error occurred",
            analyticsContext = mapOf(
                "error_type" to this::class.simpleName.orEmpty(),
                "error_location" to "ReactiveViewModelCache"
            )
        )
    }
}