package com.example.liftrix.domain.usecase.analytics

import com.example.liftrix.domain.model.analytics.DashboardConfiguration
import com.example.liftrix.domain.model.analytics.WidgetPreferences
import com.example.liftrix.domain.model.analytics.AnalyticsWidget
import com.example.liftrix.domain.model.analytics.WidgetComplexity
import com.example.liftrix.domain.model.analytics.UserLevel
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.WidgetPreferencesRepository
import com.example.liftrix.service.AnalyticsService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use case for retrieving comprehensive dashboard configuration and layout preferences.
 * 
 * This use case coordinates between widget preferences and dashboard configuration
 * to provide a complete view of how the user's dashboard should be structured and
 * populated. It handles layout preferences, widget visibility, ordering, and
 * performance optimization settings.
 * 
 * Key Features:
 * - Complete dashboard configuration retrieval
 * - Widget preferences integration with layout settings
 * - Performance-aware widget recommendations
 * - Reactive Flow updates for real-time configuration changes
 * - Comprehensive error handling with graceful degradation
 * 
 * Configuration Components:
 * - Widget visibility and ordering preferences
 * - Dashboard layout mode (GRID, SECTIONS, LIST, CUSTOM)
 * - Performance settings and memory optimization
 * - User-level appropriate widget recommendations
 * 
 * Usage:
 * ```
 * getDashboardConfigurationUseCase.execute(GetConfigurationParams(userId))
 *     .collect { result ->
 *         result.fold(
 *             onSuccess = { config -> updateDashboard(config) },
 *             onFailure = { error -> handleError(error) }
 *         )
 *     }
 * ```
 */
@Singleton
class GetDashboardConfigurationUseCase @Inject constructor(
    private val widgetPreferencesRepository: WidgetPreferencesRepository,
    private val analyticsService: AnalyticsService
) {
    
    /**
     * Parameters for dashboard configuration retrieval
     * 
     * @property userId User identifier for configuration
     * @property includeRecommendations Whether to include widget recommendations
     * @property validatePerformance Whether to validate performance constraints
     */
    data class GetConfigurationParams(
        val userId: String,
        val includeRecommendations: Boolean = true,
        val validatePerformance: Boolean = true
    )
    
    /**
     * Complete dashboard configuration result
     * 
     * @property preferences User widget preferences
     * @property configuration Dashboard layout configuration
     * @property availableWidgets All widgets available to the user
     * @property recommendations Performance-aware widget recommendations
     * @property performanceInfo Performance constraints and optimization info
     */
    data class DashboardConfigurationResult(
        val preferences: WidgetPreferences,
        val configuration: DashboardConfiguration,
        val availableWidgets: List<AnalyticsWidget>,
        val recommendations: WidgetRecommendations? = null,
        val performanceInfo: PerformanceInfo
    )
    
    /**
     * Widget recommendations based on user level and performance constraints
     */
    data class WidgetRecommendations(
        val recommendedWidgets: List<AnalyticsWidget>,
        val alternativeWidgets: List<AnalyticsWidget>,
        val performanceOptimized: List<AnalyticsWidget>
    )
    
    /**
     * Performance information and constraints
     */
    data class PerformanceInfo(
        val currentMemoryUsage: Float,
        val maxMemoryLimit: Float,
        val currentWidgetCount: Int,
        val maxWidgetCount: Int,
        val complexWidgetCount: Int,
        val maxComplexWidgets: Int,
        val isWithinLimits: Boolean
    )
    
    /**
     * Retrieves complete dashboard configuration with reactive Flow updates.
     * 
     * Combines widget preferences and dashboard configuration to provide a
     * comprehensive view of the user's dashboard setup with real-time updates.
     * 
     * @param params Configuration retrieval parameters
     * @return Flow<LiftrixResult<DashboardConfigurationResult>> for reactive updates
     */
    suspend fun execute(params: GetConfigurationParams): Flow<LiftrixResult<DashboardConfigurationResult>> {
        Timber.d("Retrieving dashboard configuration for user: ${params.userId}")
        
        // Get widget preferences and dashboard configuration concurrently
        val preferencesFlow = widgetPreferencesRepository.getWidgetPreferences(params.userId)
        
        return combine(
            preferencesFlow,
            flowOf(getDashboardConfiguration(params.userId))
        ) { preferencesResult, configurationResult ->
            
            liftrixCatching(
                errorMapper = { throwable ->
                    LiftrixError.DataRetrievalError(
                        errorMessage = "Failed to retrieve dashboard configuration for user ${params.userId}: ${throwable.message}",
                        operation = "getDashboardConfiguration",
                        retryable = true
                    )
                }
            ) {
                val preferences = preferencesResult.fold(
                    onSuccess = { it },
                    onFailure = { error ->
                        Timber.w("Failed to load preferences, using defaults: $error")
                        WidgetPreferences.createDefault(params.userId)
                    }
                )
                
                val configuration = configurationResult.fold(
                    onSuccess = { it },
                    onFailure = { error ->
                        Timber.w("Failed to load configuration, using defaults: $error")
                        DashboardConfiguration.Beginner
                    }
                )
                
                // Build complete configuration result
                buildConfigurationResult(
                    preferences = preferences,
                    configuration = configuration,
                    params = params
                )
            }
        }.catch { error ->
            emit(LiftrixResult.failure(
                LiftrixError.DataRetrievalError(
                    errorMessage = "Unexpected error retrieving dashboard configuration: ${error.message}",
                    operation = "getDashboardConfiguration",
                    retryable = true
                )
            ))
        }
    }
    
    /**
     * Gets dashboard configuration from analytics service
     */
    private suspend fun getDashboardConfiguration(userId: String): LiftrixResult<DashboardConfiguration> = 
        withContext(Dispatchers.IO) {
            liftrixCatching(
                errorMapper = { throwable ->
                    LiftrixError.DataRetrievalError(
                        errorMessage = "Failed to load dashboard configuration: ${throwable.message}",
                        operation = "getDashboardConfiguration",
                        retryable = true
                    )
                }
            ) {
                // Try to get configuration from analytics service first
                val configResult = analyticsService.getWidgetPreferences(userId)
                
                configResult.fold(
                    onSuccess = { preferences ->
                        // Convert preferences to appropriate dashboard configuration
                        when (preferences.userLevel) {
                            UserLevel.BEGINNER -> DashboardConfiguration.Beginner
                            UserLevel.INTERMEDIATE -> DashboardConfiguration.Intermediate  
                            UserLevel.ADVANCED -> DashboardConfiguration.Advanced
                        }
                    },
                    onFailure = { error ->
                        Timber.w("Failed to load dashboard config from analytics service: $error")
                        DashboardConfiguration.Beginner
                    }
                )
            }
        }
    
    /**
     * Builds complete configuration result with recommendations and performance info
     */
    private suspend fun buildConfigurationResult(
        preferences: WidgetPreferences,
        configuration: DashboardConfiguration,
        params: GetConfigurationParams
    ): DashboardConfigurationResult {
        
        // Get available widgets for user level
        val availableWidgets = AnalyticsWidget.getForUserLevel(preferences.userLevel)
        
        // Generate recommendations if requested
        val recommendations = if (params.includeRecommendations) {
            generateWidgetRecommendations(preferences, availableWidgets)
        } else null
        
        // Calculate performance information
        val performanceInfo = if (params.validatePerformance) {
            calculatePerformanceInfo(preferences)
        } else {
            PerformanceInfo(
                currentMemoryUsage = 0f,
                maxMemoryLimit = 50f,
                currentWidgetCount = preferences.visibleWidgets.size,
                maxWidgetCount = 10,
                complexWidgetCount = 0,
                maxComplexWidgets = 2,
                isWithinLimits = true
            )
        }
        
        return DashboardConfigurationResult(
            preferences = preferences,
            configuration = configuration,
            availableWidgets = availableWidgets,
            recommendations = recommendations,
            performanceInfo = performanceInfo
        )
    }
    
    /**
     * Generates widget recommendations based on user preferences and performance
     */
    private fun generateWidgetRecommendations(
        preferences: WidgetPreferences,
        availableWidgets: List<AnalyticsWidget>
    ): WidgetRecommendations {
        
        val currentWidgets = preferences.visibleWidgets.mapNotNull { widgetId ->
            AnalyticsWidget.getById(widgetId)
        }
        
        val unusedWidgets = availableWidgets.filter { widget ->
            !preferences.visibleWidgets.contains(widget.id)
        }
        
        // Recommend high-priority widgets not currently visible
        val recommendedWidgets = unusedWidgets
            .filter { it.priority.configurationLevel <= 2 }
            .sortedBy { it.getLayoutPriority() }
            .take(3)
        
        // Alternative widgets based on current selection patterns
        val alternativeWidgets = unusedWidgets
            .filter { widget ->
                currentWidgets.any { current -> 
                    current.category == widget.category && current.complexity == widget.complexity
                }
            }
            .sortedBy { it.getLayoutPriority() }
            .take(3)
        
        // Performance-optimized widgets (simple widgets with high value)
        val performanceOptimized = unusedWidgets
            .filter { it.complexity == WidgetComplexity.SIMPLE }
            .sortedBy { it.getLayoutPriority() }
            .take(5)
        
        return WidgetRecommendations(
            recommendedWidgets = recommendedWidgets,
            alternativeWidgets = alternativeWidgets,
            performanceOptimized = performanceOptimized
        )
    }
    
    /**
     * Calculates current performance information and constraints
     */
    private fun calculatePerformanceInfo(preferences: WidgetPreferences): PerformanceInfo {
        val visibleWidgets = preferences.visibleWidgets.mapNotNull { widgetId ->
            AnalyticsWidget.getById(widgetId)
        }
        
        val complexityCounts = visibleWidgets.groupingBy { it.complexity }.eachCount()
        val currentMemoryUsage = WidgetComplexity.calculateTotalMemoryUsage(complexityCounts)
        val complexWidgetCount = complexityCounts[WidgetComplexity.COMPLEX] ?: 0
        
        val maxMemoryLimit = 50.0f
        val maxWidgetCount = 10
        val maxComplexWidgets = 2
        
        val isWithinLimits = currentMemoryUsage <= maxMemoryLimit &&
                visibleWidgets.size <= maxWidgetCount &&
                complexWidgetCount <= maxComplexWidgets
        
        return PerformanceInfo(
            currentMemoryUsage = currentMemoryUsage,
            maxMemoryLimit = maxMemoryLimit,
            currentWidgetCount = visibleWidgets.size,
            maxWidgetCount = maxWidgetCount,
            complexWidgetCount = complexWidgetCount,
            maxComplexWidgets = maxComplexWidgets,
            isWithinLimits = isWithinLimits
        )
    }
    
    /**
     * Calculates optimal grid columns based on widget preferences
     */
    private fun calculateGridColumns(preferences: WidgetPreferences): Int {
        val visibleWidgets = preferences.visibleWidgets.mapNotNull { widgetId ->
            AnalyticsWidget.getById(widgetId)
        }
        
        val complexityCounts = visibleWidgets.groupingBy { it.complexity }.eachCount()
        
        return when {
            (complexityCounts[WidgetComplexity.COMPLEX] ?: 0) > 1 -> 1 // Single column for multiple complex widgets
            (complexityCounts[WidgetComplexity.MODERATE] ?: 0) > 3 -> 2 // Two columns for many moderate widgets
            visibleWidgets.size <= 4 -> 2 // Two columns for few widgets
            else -> 3 // Three columns for optimal layout
        }
    }
    
    /**
     * Calculates optimal refresh interval based on widget complexity mix
     */
    private fun calculateRefreshInterval(preferences: WidgetPreferences): Int {
        val visibleWidgets = preferences.visibleWidgets.mapNotNull { widgetId ->
            AnalyticsWidget.getById(widgetId)
        }
        
        if (visibleWidgets.isEmpty()) return 30 // Default 30 minutes
        
        val averageInterval = visibleWidgets.map { it.getRefreshIntervalMinutes() }.average()
        return averageInterval.toInt().coerceIn(15, 240) // Between 15 minutes and 4 hours
    }
    
    /**
     * Validates dashboard configuration against performance constraints
     * 
     * @param configuration Configuration to validate
     * @return LiftrixResult with validation result or error
     */
    suspend fun validateConfiguration(configuration: DashboardConfigurationResult): LiftrixResult<Boolean> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.ValidationError(
                    field = "dashboardConfiguration",
                    violations = listOf("Configuration validation failed: ${throwable.message}")
                )
            }
        ) {
            val performanceInfo = configuration.performanceInfo
            val issues = mutableListOf<String>()
            
            if (!performanceInfo.isWithinLimits) {
                if (performanceInfo.currentMemoryUsage > performanceInfo.maxMemoryLimit) {
                    issues.add("Memory usage exceeds limit: ${performanceInfo.currentMemoryUsage}MB > ${performanceInfo.maxMemoryLimit}MB")
                }
                
                if (performanceInfo.currentWidgetCount > performanceInfo.maxWidgetCount) {
                    issues.add("Widget count exceeds limit: ${performanceInfo.currentWidgetCount} > ${performanceInfo.maxWidgetCount}")
                }
                
                if (performanceInfo.complexWidgetCount > performanceInfo.maxComplexWidgets) {
                    issues.add("Complex widget count exceeds limit: ${performanceInfo.complexWidgetCount} > ${performanceInfo.maxComplexWidgets}")
                }
            }
            
            if (issues.isNotEmpty()) {
                throw IllegalArgumentException("Configuration validation failed: ${issues.joinToString(", ")}")
            }
            
            true
        }
    }
}

