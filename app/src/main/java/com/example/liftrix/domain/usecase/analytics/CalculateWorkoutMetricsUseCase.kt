package com.example.liftrix.domain.usecase.analytics

import com.example.liftrix.domain.model.WorkoutId
import com.example.liftrix.domain.model.analytics.WorkoutMetrics
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixFailure
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.usecase.common.ErrorHandler
import com.example.liftrix.service.AnalyticsEngine
import timber.log.Timber
import javax.inject.Inject

/**
 * Use case for calculating comprehensive workout metrics for individual workouts
 * 
 * Provides detailed analytics calculations for:
 * - Individual workout performance analysis
 * - Volume efficiency and intensity calculations
 * - MET-based calorie burn estimates
 * - Training load and quality assessments
 * 
 * Business Logic:
 * - Validates workout exists and user has access
 * - Calculates metrics using standardized fitness industry formulas
 * - Returns comprehensive analytics data for dashboard display
 * - Handles real-time calculation triggers from workout completion
 * 
 * Performance Targets:
 * - Calculation time: <100ms for individual workouts
 * - Accuracy: Within 10% of gold-standard measurements
 * - Reliability: 99.9% success rate for valid workout IDs
 */
class CalculateWorkoutMetricsUseCase @Inject constructor(
    private val analyticsEngine: AnalyticsEngine,
    private val errorHandler: ErrorHandler
) {
    
    /**
     * Calculates comprehensive metrics for a specific workout
     * 
     * @param request The workout metrics calculation request
     * @return LiftrixResult containing calculated WorkoutMetrics or error information
     */
    suspend operator fun invoke(request: WorkoutMetricsRequest): LiftrixResult<WorkoutMetrics> {
        return try {
            Timber.d("Calculating workout metrics for workoutId: ${request.workoutId.value}")
            
            // Validate request
            val validationResult = validateRequest(request)
            if (validationResult.isFailure) {
                return validationResult as LiftrixResult<WorkoutMetrics>
            }
            
            // Delegate to analytics engine for calculation
            val metricsResult = analyticsEngine.calculateWorkoutMetrics(request.workoutId)
            
            // Handle any calculation errors through centralized error handler
            if (metricsResult.isFailure) {
                val error = metricsResult.exceptionOrNull() as? LiftrixError
                    ?: LiftrixError.UnknownError("Unexpected error during workout metrics calculation")
                
                errorHandler.handleError(error, mapOf("context" to "CalculateWorkoutMetricsUseCase"))
                return liftrixFailure(error)
            }
            
            val metrics = metricsResult.getOrThrow()
            Timber.d("Successfully calculated metrics for workout: ${metrics.workoutId}")
            
            metricsResult
            
        } catch (e: Exception) {
            Timber.e(e, "Unexpected error calculating workout metrics for workoutId: ${request.workoutId.value}")
            val error = LiftrixError.UnknownError("Failed to calculate workout metrics: ${e.message}")
            errorHandler.handleError(error, mapOf("context" to "CalculateWorkoutMetricsUseCase"))
            liftrixFailure(error)
        }
    }
    
    /**
     * Convenience method for calculating metrics with just workout ID
     */
    suspend operator fun invoke(workoutId: WorkoutId): LiftrixResult<WorkoutMetrics> {
        return invoke(WorkoutMetricsRequest(workoutId))
    }
    
    /**
     * Batch calculates metrics for multiple workouts efficiently
     * 
     * @param workoutIds List of workout IDs to calculate metrics for
     * @return LiftrixResult containing map of workoutId to WorkoutMetrics
     */
    suspend fun calculateBatch(workoutIds: List<WorkoutId>): LiftrixResult<Map<String, WorkoutMetrics>> {
        return try {
            Timber.d("Calculating batch workout metrics for ${workoutIds.size} workouts")
            
            if (workoutIds.isEmpty()) {
                return LiftrixResult.success(emptyMap())
            }
            
            if (workoutIds.size > MAX_BATCH_SIZE) {
                return liftrixFailure(
                    LiftrixError.ValidationError(
                        field = "workoutIds",
                        violations = listOf("Batch size cannot exceed $MAX_BATCH_SIZE workouts")
                    )
                )
            }
            
            val metricsMap = mutableMapOf<String, WorkoutMetrics>()
            val errors = mutableListOf<String>()
            
            // Calculate metrics for each workout
            workoutIds.forEach { workoutId ->
                val result = analyticsEngine.calculateWorkoutMetrics(workoutId)
                if (result.isSuccess) {
                    val metrics = result.getOrThrow()
                    metricsMap[workoutId.value] = metrics
                } else {
                    errors.add("Failed to calculate metrics for workout: ${workoutId.value}")
                }
            }
            
            // Return partial results even if some calculations failed
            if (errors.isNotEmpty()) {
                Timber.w("Batch calculation completed with ${errors.size} errors: $errors")
            }
            
            Timber.d("Successfully calculated batch metrics for ${metricsMap.size}/${workoutIds.size} workouts")
            LiftrixResult.success(metricsMap)
            
        } catch (e: Exception) {
            Timber.e(e, "Unexpected error in batch workout metrics calculation")
            val error = LiftrixError.UnknownError("Failed to calculate batch workout metrics: ${e.message}")
            errorHandler.handleError(error, mapOf("context" to "CalculateWorkoutMetricsUseCase.calculateBatch"))
            liftrixFailure(error)
        }
    }
    
    /**
     * Validates the workout metrics calculation request
     */
    private fun validateRequest(request: WorkoutMetricsRequest): LiftrixResult<Unit> {
        val violations = mutableListOf<String>()
        
        // Validate workout ID
        if (request.workoutId.value.isBlank()) {
            violations.add("Workout ID cannot be blank")
        }
        
        return if (violations.isEmpty()) {
            LiftrixResult.success(Unit)
        } else {
            liftrixFailure(
                LiftrixError.ValidationError(
                    field = "WorkoutMetricsRequest",
                    violations = violations
                )
            )
        }
    }
    
    companion object {
        private const val MAX_BATCH_SIZE = 50 // Maximum workouts per batch calculation
    }
}

/**
 * Request data class for workout metrics calculation
 * 
 * @property workoutId The ID of the workout to calculate metrics for
 * @property includeAdvancedMetrics Whether to include advanced metrics (default: true)
 * @property recalculateCache Whether to force recalculation ignoring cache (default: false)
 */
data class WorkoutMetricsRequest(
    val workoutId: WorkoutId,
    val includeAdvancedMetrics: Boolean = true,
    val recalculateCache: Boolean = false
) {
    init {
        require(workoutId.value.isNotBlank()) { "Workout ID cannot be blank" }
    }
}