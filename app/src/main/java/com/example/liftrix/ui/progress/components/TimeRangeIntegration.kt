package com.example.liftrix.ui.progress.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.liftrix.domain.model.analytics.DateRange
import com.example.liftrix.domain.model.analytics.TimeRange as DomainTimeRange
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import com.example.liftrix.core.extensions.toLocalDate
import com.example.liftrix.domain.model.analytics.TimeRangeType

/**
 * TimeRangeIntegration - Coordination layer for time range selection across chart ViewModels
 *
 * Provides centralized time range state management that broadcasts changes to all chart components:
 * - Seamless integration between UI TimeRange and domain TimeRange systems
 * - Reactive state flow for chart ViewModel synchronization
 * - Performance-optimized broadcast mechanism with debouncing
 * - Type-safe conversion between UI and domain time range representations
 * - Memory-efficient subscriber management with automatic cleanup
 */

/**
 * Adapter to convert between UI TimeRangeType and Domain TimeRange
 */
object TimeRangeAdapter {
    
    /**
     * Convert UI TimeRangeType to domain TimeRange
     */
    fun toDomainTimeRange(uiTimeRange: TimeRangeType): DomainTimeRange {
        return when (uiTimeRange) {
            TimeRangeType.MONTH -> DomainTimeRange.lastMonth()
            TimeRangeType.SIX_MONTHS -> DomainTimeRange.lastSixMonths()
            TimeRangeType.ALL_TIME -> DomainTimeRange.allTime()
        }
    }
    
    /**
     * Convert domain TimeRange to UI TimeRangeType
     */
    fun fromDomainTimeRange(domainTimeRange: DomainTimeRange): TimeRangeType {
        return domainTimeRange.type
    }
    
    /**
     * Convert UI DateRange to domain DateRange (simplified - using UI type)
     */
    fun toDomainDateRange(uiDateRange: DateRange): DateRange {
        return uiDateRange
    }
    
    /**
     * Convert domain DateRange to UI DateRange (simplified - using UI type)
     */
    fun fromDomainDateRange(domainDateRange: DateRange): DateRange {
        return domainDateRange
    }
}

/**
 * Time range event for cross-component communication
 */
sealed class TimeRangeEvent {
    data class TimeRangeChanged(
        val newTimeRange: TimeRangeType,
        val dateRange: DateRange,
        val source: String = "unknown"
    ) : TimeRangeEvent()
    
    data class DateRangeRequested(
        val timeRange: TimeRangeType,
        val customStart: LocalDate? = null,
        val customEnd: LocalDate? = null
    ) : TimeRangeEvent()
    
    object RefreshRequested : TimeRangeEvent()
}

/**
 * Time range coordinator for managing chart synchronization
 */
class TimeRangeCoordinator : ViewModel() {
    
    private val _currentTimeRange = MutableStateFlow(TimeRangeType.MONTH)
    val currentTimeRange: StateFlow<TimeRangeType> = _currentTimeRange.asStateFlow()
    
    private val _currentDateRange = MutableStateFlow(
        TimeRangeAdapter.toDomainTimeRange(TimeRangeType.MONTH).let { domainRange ->
            DateRange(start = domainRange.startDate.toLocalDate(), end = domainRange.endDate.toLocalDate())
        }
    )
    val currentDateRange: StateFlow<DateRange> = _currentDateRange.asStateFlow()
    
    private val _timeRangeEvents = MutableStateFlow<TimeRangeEvent?>(null)
    val timeRangeEvents: StateFlow<TimeRangeEvent?> = _timeRangeEvents.asStateFlow()
    
    private val subscribers = mutableSetOf<String>()
    
    /**
     * Change time range and notify all subscribers simultaneously
     * Ensures all charts update at the same time for consistent UI
     */
    fun changeTimeRange(newTimeRange: TimeRangeType, source: String = "user") {
        viewModelScope.launch {
            // Apply debouncing to prevent rapid changes
            if (!TimeRangeOptimizations.shouldProcessTimeRangeChange()) {
                return@launch
            }
            
            val domainRange = TimeRangeAdapter.toDomainTimeRange(newTimeRange)
            val dateRange = DateRange(
                start = domainRange.startDate.toLocalDate(),
                end = domainRange.endDate.toLocalDate()
            )
            
            // Update state atomically to ensure all subscribers get the same update
            _currentTimeRange.value = newTimeRange
            _currentDateRange.value = dateRange
            
            // Emit single event that triggers simultaneous updates
            _timeRangeEvents.value = TimeRangeEvent.TimeRangeChanged(
                newTimeRange = newTimeRange,
                dateRange = dateRange,
                source = source
            )
        }
    }
    
    /**
     * Request custom date range
     */
    fun requestCustomDateRange(
        timeRange: TimeRangeType,
        customStart: LocalDate? = null,
        customEnd: LocalDate? = null
    ) {
        viewModelScope.launch {
            _timeRangeEvents.value = TimeRangeEvent.DateRangeRequested(
                timeRange = timeRange,
                customStart = customStart,
                customEnd = customEnd
            )
        }
    }
    
    /**
     * Request refresh of all charts
     */
    fun refreshCharts() {
        viewModelScope.launch {
            _timeRangeEvents.value = TimeRangeEvent.RefreshRequested
        }
    }
    
    /**
     * Register a subscriber (chart ViewModel)
     */
    fun subscribe(subscriberId: String) {
        subscribers.add(subscriberId)
    }
    
    /**
     * Unregister a subscriber
     */
    fun unsubscribe(subscriberId: String) {
        subscribers.remove(subscriberId)
    }
    
    /**
     * Clear events after processing
     */
    fun clearEvents() {
        viewModelScope.launch {
            _timeRangeEvents.value = null
        }
    }
    
    /**
     * Get current domain time range
     */
    fun getCurrentDomainTimeRange(): DomainTimeRange {
        return TimeRangeAdapter.toDomainTimeRange(_currentTimeRange.value)
    }
    
    /**
     * Get current domain date range
     */
    fun getCurrentDomainDateRange(): DateRange {
        return TimeRangeAdapter.toDomainDateRange(_currentDateRange.value)
    }
}

/**
 * Base interface for chart ViewModels that respond to time range changes
 */
interface TimeRangeAware {
    /**
     * Handle time range change
     */
    fun onTimeRangeChanged(timeRange: TimeRangeType, dateRange: DateRange)
    
    /**
     * Handle refresh request
     */
    fun onRefreshRequested() {}
    
    /**
     * Get subscriber ID for tracking
     */
    fun getSubscriberId(): String
}

/**
 * Extension function for ViewModels to easily observe time range changes
 */
fun ViewModel.observeTimeRangeChanges(
    coordinator: TimeRangeCoordinator,
    onTimeRangeChanged: (TimeRangeType, DateRange) -> Unit
) {
    coordinator.subscribe(this::class.simpleName ?: "unknown")
    
    // Observe time range events in viewModelScope
    viewModelScope.launch {
        coordinator.timeRangeEvents.collect { event ->
            when (event) {
                is TimeRangeEvent.TimeRangeChanged -> {
                    onTimeRangeChanged(event.newTimeRange, event.dateRange)
                }
                is TimeRangeEvent.RefreshRequested -> {
                    // Handle refresh if needed
                }
                null -> { /* No event */ }
                else -> { /* Handle other events */ }
            }
        }
    }
}

/**
 * Mixin for chart ViewModels to handle time range coordination
 */
abstract class TimeRangeAwareViewModel(
    protected val timeRangeCoordinator: TimeRangeCoordinator
) : ViewModel(), TimeRangeAware {
    
    init {
        observeTimeRangeChanges()
    }
    
    private fun observeTimeRangeChanges() {
        timeRangeCoordinator.subscribe(getSubscriberId())
        
        viewModelScope.launch {
            timeRangeCoordinator.timeRangeEvents.collect { event ->
                when (event) {
                    is TimeRangeEvent.TimeRangeChanged -> {
                        onTimeRangeChanged(event.newTimeRange, event.dateRange)
                    }
                    is TimeRangeEvent.RefreshRequested -> {
                        onRefreshRequested()
                    }
                    null -> { /* No event */ }
                    else -> { /* Handle other events */ }
                }
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        timeRangeCoordinator.unsubscribe(getSubscriberId())
    }
    
    override fun getSubscriberId(): String {
        return this::class.simpleName ?: "unknown_chart_viewmodel"
    }
}

/**
 * Chart ViewModel state wrapper for time range integration
 */
data class ChartViewModelState<T>(
    val data: T? = null,
    val timeRange: TimeRangeType = TimeRangeType.MONTH,
    val dateRange: DateRange = TimeRangeAdapter.toDomainTimeRange(TimeRangeType.MONTH).let { domainRange ->
        DateRange(start = domainRange.startDate.toLocalDate(), end = domainRange.endDate.toLocalDate())
    },
    val isLoading: Boolean = false,
    val error: String? = null,
    val lastUpdated: Long = System.currentTimeMillis()
) {
    fun updateTimeRange(newTimeRange: TimeRangeType): ChartViewModelState<T> {
        val domainRange = TimeRangeAdapter.toDomainTimeRange(newTimeRange)
        val newDateRange = DateRange(
            start = domainRange.startDate.toLocalDate(),
            end = domainRange.endDate.toLocalDate()
        )
        return copy(
            timeRange = newTimeRange,
            dateRange = newDateRange,
            lastUpdated = System.currentTimeMillis()
        )
    }
    
    fun updateData(newData: T): ChartViewModelState<T> {
        return copy(
            data = newData,
            isLoading = false,
            error = null,
            lastUpdated = System.currentTimeMillis()
        )
    }
    
    fun updateLoading(loading: Boolean): ChartViewModelState<T> {
        return copy(isLoading = loading)
    }
    
    fun updateError(errorMessage: String?): ChartViewModelState<T> {
        return copy(
            error = errorMessage,
            isLoading = false
        )
    }
}

/**
 * Performance optimization for time range coordination
 */
object TimeRangeOptimizations {
    
    private const val DEBOUNCE_DELAY_MS = 150L
    private var lastChangeTime = 0L
    
    /**
     * Debounce rapid time range changes
     */
    fun shouldProcessTimeRangeChange(): Boolean {
        val currentTime = System.currentTimeMillis()
        val shouldProcess = currentTime - lastChangeTime >= DEBOUNCE_DELAY_MS
        if (shouldProcess) {
            lastChangeTime = currentTime
        }
        return shouldProcess
    }
    
    /**
     * Check if data needs refresh based on time range change
     */
    fun needsDataRefresh(
        oldTimeRange: TimeRangeType,
        newTimeRange: TimeRangeType,
        lastDataUpdate: Long
    ): Boolean {
        val timeSinceLastUpdate = System.currentTimeMillis() - lastDataUpdate
        
        // Always refresh if time range changed
        if (oldTimeRange != newTimeRange) return true
        
        // Refresh if data is stale (5 minutes)
        if (timeSinceLastUpdate > 5 * 60 * 1000) return true
        
        return false
    }
    
    /**
     * Calculate optimal batch size for data queries based on time range
     */
    fun getOptimalBatchSize(timeRange: TimeRangeType): Int {
        return when (timeRange) {
            TimeRangeType.MONTH -> 100
            TimeRangeType.SIX_MONTHS -> 350
            TimeRangeType.ALL_TIME -> 1000
        }
    }
}