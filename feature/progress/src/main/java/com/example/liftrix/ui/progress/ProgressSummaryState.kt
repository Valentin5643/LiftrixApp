package com.example.liftrix.ui.progress

import com.example.liftrix.domain.model.analytics.TimeRange
import com.example.liftrix.domain.repository.ProgressSummary
import com.example.liftrix.ui.common.state.AsyncData
import com.example.liftrix.ui.common.state.isLoading
import com.example.liftrix.ui.common.state.isSuccess
import com.example.liftrix.ui.common.state.isFailure
import com.example.liftrix.ui.common.state.isNotAsked
import com.example.liftrix.ui.common.state.getOrNull
import com.example.liftrix.ui.common.state.errorOrNull
import kotlinx.datetime.Instant

/**
 * State class for ProgressSummaryViewModel following the established AsyncData pattern.
 * 
 * This state manages the progress summary data with comprehensive metadata tracking
 * including time range selection, refresh timestamps, and user context. It provides
 * all necessary information for the UI to display summary statistics and handle
 * user interactions effectively.
 * 
 * Key Features:
 * - AsyncData wrapper for comprehensive state management
 * - Time range selection with reactive updates
 * - Refresh timestamp tracking for cache invalidation
 * - User authentication state integration
 * - Metadata for UI state management
 * 
 * Integration Points:
 * - Used by ProgressSummaryViewModel for state management
 * - Consumed by UI components for reactive updates
 * - Integrates with ProgressDataPort for data operations
 * - Follows established patterns from other ViewModels
 * 
 * Usage:
 * ```kotlin
 * val state = ProgressSummaryState(
 *     summaryData = AsyncData.Success(progressSummary),
 *     currentTimeRange = TimeRange.lastMonth(),
 *     userId = "user123"
 * )
 * ```
 * 
 * @param summaryData The async data containing progress summary information
 * @param currentTimeRange The currently selected time range for data filtering
 * @param userId The authenticated user's ID for data scoping
 * @param lastRefreshTimestamp When the data was last refreshed
 * @param isRefreshing Whether a refresh operation is currently in progress
 */
data class ProgressSummaryState(
    val summaryData: AsyncData<ProgressSummary> = AsyncData.NotAsked,
    val currentTimeRange: TimeRange = TimeRange.lastMonth(),
    val userId: String? = null,
    val lastRefreshTimestamp: Long = 0L,
    val isRefreshing: Boolean = false
) {
    
    /**
     * Checks if the user is authenticated and has a valid user ID.
     * 
     * @return true if user is authenticated, false otherwise
     */
    fun hasValidUser(): Boolean = userId != null
    
    /**
     * Checks if the summary data is currently loading.
     * 
     * @return true if loading, false otherwise
     */
    fun isLoading(): Boolean = summaryData.isLoading()
    
    /**
     * Checks if the summary data has been successfully loaded.
     * 
     * @return true if data is available, false otherwise
     */
    fun hasData(): Boolean = summaryData.isSuccess()
    
    /**
     * Checks if there's an error in the summary data.
     * 
     * @return true if error occurred, false otherwise
     */
    fun hasError(): Boolean = summaryData.isFailure()
    
    /**
     * Checks if the summary data has not been requested yet.
     * 
     * @return true if not requested, false otherwise
     */
    fun isNotAsked(): Boolean = summaryData.isNotAsked()
    
    /**
     * Gets the progress summary data if available.
     * 
     * @return ProgressSummary if successful, null otherwise
     */
    fun getProgressSummary(): ProgressSummary? = summaryData.getOrNull()
    
    /**
     * Gets the error if data loading failed.
     * 
     * @return LiftrixError if failed, null otherwise
     */
    fun getError(): com.example.liftrix.domain.model.error.LiftrixError? = summaryData.errorOrNull()
    
    /**
     * Checks if the data is stale and needs refreshing.
     * 
     * @param maxAgeMillis Maximum age in milliseconds
     * @return true if data is stale, false otherwise
     */
    fun isDataStale(maxAgeMillis: Long = 300_000L): Boolean { // 5 minutes default
        if (lastRefreshTimestamp == 0L) return true
        val now = System.currentTimeMillis()
        return (now - lastRefreshTimestamp) > maxAgeMillis
    }
    
    /**
     * Checks if any refresh operation is in progress.
     * 
     * @return true if refreshing, false otherwise
     */
    fun isAnyRefreshInProgress(): Boolean = isRefreshing || summaryData.isLoading()
    
    /**
     * Creates a new state with updated summary data.
     * 
     * @param newSummaryData The updated async data
     * @return Updated state with new data
     */
    fun withSummaryData(newSummaryData: AsyncData<ProgressSummary>): ProgressSummaryState =
        copy(
            summaryData = newSummaryData,
            lastRefreshTimestamp = if (newSummaryData.isSuccess()) System.currentTimeMillis() else lastRefreshTimestamp
        )
    
    /**
     * Creates a new state with updated time range.
     * 
     * @param newTimeRange The updated time range
     * @return Updated state with new time range
     */
    fun withTimeRange(newTimeRange: TimeRange): ProgressSummaryState =
        copy(
            currentTimeRange = newTimeRange,
            summaryData = AsyncData.Loading() // Reset to loading when time range changes
        )
    
    /**
     * Creates a new state with updated user ID.
     * 
     * @param newUserId The updated user ID
     * @return Updated state with new user ID
     */
    fun withUserId(newUserId: String?): ProgressSummaryState =
        copy(
            userId = newUserId,
            summaryData = if (newUserId != null) AsyncData.Loading() else AsyncData.NotAsked
        )
    
    /**
     * Creates a new state with updated refresh status.
     * 
     * @param refreshing Whether refresh is in progress
     * @return Updated state with new refresh status
     */
    fun withRefreshStatus(refreshing: Boolean): ProgressSummaryState =
        copy(isRefreshing = refreshing)
    
    /**
     * Creates a new state representing the loading state.
     * 
     * @return State with loading summary data
     */
    fun toLoadingState(): ProgressSummaryState =
        copy(summaryData = AsyncData.Loading())
    
    /**
     * Creates a new state representing an error state.
     * 
     * @param error The error that occurred
     * @return State with error summary data
     */
    fun toErrorState(error: com.example.liftrix.domain.model.error.LiftrixError): ProgressSummaryState =
        copy(summaryData = AsyncData.Failure(error))
    
    /**
     * Creates a new state representing a success state.
     * 
     * @param summary The successfully loaded summary data
     * @return State with success summary data
     */
    fun toSuccessState(summary: ProgressSummary): ProgressSummaryState =
        copy(
            summaryData = AsyncData.Success(summary),
            lastRefreshTimestamp = System.currentTimeMillis(),
            isRefreshing = false
        )
    
    companion object {
        /**
         * Creates an initial state for an unauthenticated user.
         * 
         * @return State with no user and not asked data
         */
        fun createUnauthenticatedState(): ProgressSummaryState =
            ProgressSummaryState(
                summaryData = AsyncData.NotAsked,
                currentTimeRange = TimeRange.lastMonth(),
                userId = null,
                lastRefreshTimestamp = 0L,
                isRefreshing = false
            )
        
        /**
         * Creates an initial state for an authenticated user.
         * 
         * @param userId The authenticated user's ID
         * @param timeRange The initial time range
         * @return State with user and loading data
         */
        fun createAuthenticatedState(
            userId: String,
            timeRange: TimeRange = TimeRange.lastMonth()
        ): ProgressSummaryState =
            ProgressSummaryState(
                summaryData = AsyncData.Loading(),
                currentTimeRange = timeRange,
                userId = userId,
                lastRefreshTimestamp = 0L,
                isRefreshing = false
            )
    }
}