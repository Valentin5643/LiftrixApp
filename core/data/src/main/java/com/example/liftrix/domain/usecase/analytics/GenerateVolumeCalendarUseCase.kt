package com.example.liftrix.domain.usecase.analytics

import com.example.liftrix.domain.model.analytics.VolumeCalendarData
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixFailure
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.usecase.common.ErrorHandler
import com.example.liftrix.service.AnalyticsEngine
import kotlinx.datetime.Clock
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import timber.log.Timber
import javax.inject.Inject

/**
 * Use case for generating monthly volume calendar data for analytics dashboard
 * 
 * Provides volume calendar generation for:
 * - Monthly workout volume visualization with color-coded intensity
 * - Historical volume pattern analysis and trend identification
 * - Calendar widget data for dashboard display
 * - Volume distribution analysis across days of the month
 * 
 * Business Logic:
 * - Validates user access and date range parameters
 * - Aggregates daily workout volumes for specified month
 * - Calculates volume intensity factors for color coding
 * - Generates calendar grid data for consistent UI display
 * - Handles timezone considerations for global users
 * 
 * Performance Targets:
 * - Calendar generation: <200ms for monthly data
 * - Data accuracy: 100% volume aggregation accuracy
 * - Memory efficiency: Optimized for large historical datasets
 */
class GenerateVolumeCalendarUseCase @Inject constructor(
    private val analyticsEngine: AnalyticsEngine,
    private val errorHandler: ErrorHandler
) {
    
    /**
     * Generates volume calendar data for the specified month and year
     * 
     * @param request The volume calendar generation request
     * @return LiftrixResult containing VolumeCalendarData or error information
     */
    suspend operator fun invoke(request: VolumeCalendarRequest): LiftrixResult<VolumeCalendarData> {
        return try {
            Timber.d("Generating volume calendar for user: ${request.userId}, year: ${request.year}, month: ${request.month}")
            
            // Validate request
            val validationResult = validateRequest(request)
            if (validationResult.isFailure) {
                return LiftrixResult.failure(
                    validationResult.exceptionOrNull()
                        ?: LiftrixError.UnknownError("Unexpected volume calendar validation failure")
                )
            }
            
            // Delegate to analytics engine for calendar generation
            val calendarResult = analyticsEngine.generateVolumeCalendar(
                userId = request.userId,
                year = request.year,
                month = request.month
            )
            
            // Handle any generation errors through centralized error handler
            if (calendarResult.isFailure) {
                val error = calendarResult.exceptionOrNull()
                    ?: LiftrixError.UnknownError("Unexpected error during volume calendar generation")
                
                if (error is LiftrixError) {
                    errorHandler.handleError(error, mapOf("context" to "GenerateVolumeCalendarUseCase"))
                }
                return LiftrixResult.failure(error)
            }
            
            val calendarData = calendarResult.getOrThrow()
            Timber.d("Successfully generated volume calendar with ${calendarData.dailyVolumes.size} days of data")
            
            calendarResult
            
        } catch (e: Exception) {
            Timber.e(e, "Unexpected error generating volume calendar for user: ${request.userId}")
            val error = LiftrixError.UnknownError("Failed to generate volume calendar: ${e.message}")
            errorHandler.handleError(error, mapOf("context" to "GenerateVolumeCalendarUseCase"))
            liftrixFailure(error)
        }
    }
    
    /**
     * Convenience method for generating current month calendar
     */
    suspend fun generateCurrentMonth(userId: String): LiftrixResult<VolumeCalendarData> {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        return invoke(
            VolumeCalendarRequest(
                userId = userId,
                year = today.year,
                month = today.month
            )
        )
    }
    
    /**
     * Convenience method for generating previous month calendar
     */
    suspend fun generatePreviousMonth(userId: String): LiftrixResult<VolumeCalendarData> {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val previousMonth = if (today.month.ordinal == 0) {
            Month.DECEMBER
        } else {
            Month(today.month.ordinal)
        }
        val previousYear = if (today.month.ordinal == 0) {
            today.year - 1
        } else {
            today.year
        }
        
        return invoke(
            VolumeCalendarRequest(
                userId = userId,
                year = previousYear,
                month = previousMonth
            )
        )
    }
    
    /**
     * Generates volume calendar data for multiple consecutive months
     * 
     * @param userId The user ID to generate calendars for
     * @param startYear The starting year
     * @param startMonth The starting month
     * @param monthCount The number of consecutive months to generate (max 12)
     * @return LiftrixResult containing list of VolumeCalendarData
     */
    suspend fun generateMultipleMonths(
        userId: String,
        startYear: Int,
        startMonth: Month,
        monthCount: Int
    ): LiftrixResult<List<VolumeCalendarData>> {
        return try {
            Timber.d("Generating $monthCount months of volume calendar data for user: $userId")
            
            if (monthCount <= 0 || monthCount > MAX_MONTH_COUNT) {
                return liftrixFailure(
                    LiftrixError.ValidationError(
                        field = "monthCount",
                        violations = listOf("Month count must be between 1 and $MAX_MONTH_COUNT")
                    )
                )
            }
            
            val calendars = mutableListOf<VolumeCalendarData>()
            var currentYear = startYear
            var currentMonth = startMonth
            
            repeat(monthCount) {
                val request = VolumeCalendarRequest(
                    userId = userId,
                    year = currentYear,
                    month = currentMonth
                )
                
                val result = invoke(request)
                if (result.isSuccess) {
                    calendars.add(result.getOrThrow())
                } else {
                    // Log error but continue with remaining months
                    Timber.w("Failed to generate calendar for $currentYear-$currentMonth")
                }
                
                // Advance to next month
                if (currentMonth.ordinal == 11) {
                    currentMonth = Month.JANUARY
                    currentYear++
                } else {
                    currentMonth = Month(currentMonth.ordinal + 2)
                }
            }
            
            Timber.d("Successfully generated ${calendars.size}/$monthCount volume calendars")
            LiftrixResult.success(calendars)
            
        } catch (e: Exception) {
            Timber.e(e, "Unexpected error generating multiple month calendars")
            val error = LiftrixError.UnknownError("Failed to generate multiple month calendars: ${e.message}")
            errorHandler.handleError(error, mapOf("context" to "GenerateVolumeCalendarUseCase.generateMultipleMonths"))
            liftrixFailure(error)
        }
    }
    
    /**
     * Validates the volume calendar generation request
     */
    private fun validateRequest(request: VolumeCalendarRequest): LiftrixResult<Unit> {
        val violations = mutableListOf<String>()
        
        // Validate user ID
        if (request.userId.isBlank()) {
            violations.add("User ID cannot be blank")
        }
        
        // Validate year range
        if (request.year < MIN_YEAR || request.year > MAX_YEAR) {
            violations.add("Year must be between $MIN_YEAR and $MAX_YEAR")
        }
        
        // Validate future date constraint
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        if (request.year > today.year || 
            (request.year == today.year && request.month.ordinal > today.month.ordinal)) {
            violations.add("Cannot generate calendar for future months")
        }
        
        return if (violations.isEmpty()) {
            LiftrixResult.success(Unit)
        } else {
            liftrixFailure(
                LiftrixError.ValidationError(
                    field = "VolumeCalendarRequest",
                    violations = violations
                )
            )
        }
    }
    
    companion object {
        private const val MIN_YEAR = 2020 // Minimum supported year
        private const val MAX_YEAR = 2040 // Maximum supported year  
        private const val MAX_MONTH_COUNT = 12 // Maximum months in batch generation
    }
}

/**
 * Request data class for volume calendar generation
 * 
 * @property userId The ID of the user to generate calendar for
 * @property year The year for calendar generation
 * @property month The month for calendar generation
 * @property includeEmptyDays Whether to include days with zero volume (default: true)
 * @property timeZone The timezone for date calculations (default: system timezone)
 */
data class VolumeCalendarRequest(
    val userId: String,
    val year: Int,
    val month: Month,
    val includeEmptyDays: Boolean = true,
    val timeZone: TimeZone = TimeZone.currentSystemDefault()
) {
    init {
        require(userId.isNotBlank()) { "User ID cannot be blank" }
        require(year >= 2020 && year <= 2040) { "Year must be between 2020 and 2040" }
    }
}
