package com.example.liftrix.domain.usecase.analytics

import com.example.liftrix.domain.model.analytics.AnalyticsWidget
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.WidgetPreferencesRepository
import timber.log.Timber
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Use case for migrating deprecated widgets to modern analytics system.
 * 
 * This use case handles the transition from the full 25+ widget system to the
 * new focused 15-widget strength-focused analytics experience. It removes
 * deprecated calorie and duration widgets while ensuring users have minimum
 * viable widget sets for their dashboard.
 * 
 * Key migration tasks:
 * - Remove deprecated widgets from user preferences
 * - Ensure minimum viable widget set (5-8 widgets)
 * - Replace deprecated widgets with modern alternatives
 * - Preserve user customizations for non-deprecated widgets
 * 
 * @property widgetPreferencesRepository Repository for widget preferences operations
 */
class AnalyticsWidgetMigrationUseCase @Inject constructor(
    private val widgetPreferencesRepository: WidgetPreferencesRepository
) {
    
    /**
     * Migrates user from deprecated widgets to modern focused analytics.
     * 
     * @param userId The unique identifier for the user
     * @return LiftrixResult indicating success or failure
     */
    suspend operator fun invoke(userId: String): LiftrixResult<Unit> {
        return try {
            require(userId.isNotBlank()) { "User ID cannot be blank" }
            
            Timber.d("Starting analytics widget migration for user: $userId")
            
            // Get current widget preferences
            val currentPreferences = widgetPreferencesRepository
                .getWidgetPreferences(userId)
                .first()
            
            currentPreferences.fold(
                onSuccess = { preferences ->
                    
                    // Remove deprecated widgets from visible widgets
                    val deprecatedWidgetIds = AnalyticsWidget.DEPRECATED_WIDGET_IDS
                    val filteredVisibleWidgets = preferences.visibleWidgets.filterNot { widgetId ->
                        widgetId in deprecatedWidgetIds
                    }.toSet()
                    
                    Timber.d("Filtered deprecated widgets. Before: ${preferences.visibleWidgets.size}, After: ${filteredVisibleWidgets.size}")
                    Timber.d("Removed deprecated widgets: ${preferences.visibleWidgets.filter { it in deprecatedWidgetIds }}")
                    
                    // Ensure user has minimum viable widget set
                    val activeWidgets = AnalyticsWidget.getActiveWidgets()
                    val minimalWidgetSet = ensureMinimumViableWidgetSet(
                        currentWidgets = filteredVisibleWidgets,
                        availableWidgets = activeWidgets,
                        userLevel = preferences.userLevel
                    )
                    
                    // Update preferences with filtered widgets
                    val updatedPreferences = preferences.copy(
                        visibleWidgets = minimalWidgetSet
                    )
                    
                    // Save updated preferences
                    val saveResult = widgetPreferencesRepository.saveWidgetPreferences(updatedPreferences)
                    
                    saveResult.fold(
                        onSuccess = {
                            Timber.i("Analytics widget migration completed successfully for user: $userId")
                            Timber.i("Final widget count: ${minimalWidgetSet.size}")
                            Timber.d("Final widgets: ${minimalWidgetSet.toList()}")
                        },
                        onFailure = { error ->
                            Timber.e("Failed to save migrated widget preferences for user $userId: ${error.message}")
                        }
                    )
                    
                    saveResult
                },
                onFailure = { error ->
                    Timber.w("User $userId has no existing preferences, creating default active widget set")
                    
                    // Create fresh preferences with active widgets only
                    val activeWidgets = AnalyticsWidget.getActiveWidgets()
                    val defaultActiveWidgets = getDefaultActiveWidgets(activeWidgets).map { it.id }.toSet()
                    
                    // Create new preferences through repository migration
                    widgetPreferencesRepository.migrateUserPreferences(
                        userId = userId,
                        legacyConfiguration = mapOf(
                            "visibleWidgets" to defaultActiveWidgets.toList(),
                            "migration_reason" to "analytics_widget_deprecation"
                        )
                    )
                }
            )
        } catch (e: IllegalArgumentException) {
            Timber.e(e, "Invalid parameters for analytics widget migration")
            Result.failure(
                LiftrixError.ValidationError(
                    field = "userId", 
                    violations = listOf(e.message ?: "Invalid parameters")
                )
            )
        } catch (e: Exception) {
            Timber.e(e, "Unexpected error during analytics widget migration for user: $userId")
            Result.failure(
                LiftrixError.UnknownError("Failed to migrate analytics widgets: ${e.message}")
            )
        }
    }
    
    /**
     * Ensures user has minimum viable widget set for good dashboard experience.
     * 
     * @param currentWidgets User's current visible widgets (after deprecation filtering)
     * @param availableWidgets List of available non-deprecated widgets
     * @param userLevel User's experience level
     * @return Set of widget IDs ensuring minimum viable dashboard
     */
    private fun ensureMinimumViableWidgetSet(
        currentWidgets: Set<String>,
        availableWidgets: List<AnalyticsWidget>,
        userLevel: com.example.liftrix.domain.model.analytics.UserLevel
    ): Set<String> {
        // If user already has sufficient widgets, use their selection
        if (currentWidgets.size >= MINIMUM_WIDGET_COUNT) {
            return currentWidgets
        }
        
        // Otherwise, add default widgets to reach minimum count
        val defaultWidgets = getDefaultActiveWidgets(availableWidgets)
        val defaultWidgetIds = defaultWidgets.map { it.id }.toSet()
        
        // Combine user's existing widgets with defaults, prioritizing user choices
        val combinedWidgets = currentWidgets + defaultWidgetIds
        
        // Take up to target count, prioritizing user's existing choices
        val targetCount = when (userLevel) {
            com.example.liftrix.domain.model.analytics.UserLevel.BEGINNER -> 6
            com.example.liftrix.domain.model.analytics.UserLevel.INTERMEDIATE -> 8
            com.example.liftrix.domain.model.analytics.UserLevel.ADVANCED -> 10
        }
        
        return combinedWidgets.take(targetCount).toSet()
    }
    
    /**
     * Gets sensible default widget selection for users after deprecation migration.
     * 
     * Focuses on strength and performance metrics while maintaining simplicity.
     */
    private fun getDefaultActiveWidgets(availableWidgets: List<AnalyticsWidget>): List<AnalyticsWidget> {
        // Priority order for default widgets (strength-focused)
        val priorityWidgetIds = listOf(
            "one_rm_progression",       // 1RM tracking - core strength metric
            "total_volume",             // Volume tracking - fundamental metric  
            "volume_chart",             // Volume visualization
            "workout_frequency",        // Consistency tracking
            "personal_records",         // Achievement tracking
            "muscle_group_distribution", // Balance assessment
            "strength_progress",        // Overall progress
            "volume_trends"             // Advanced analysis
        )
        
        return priorityWidgetIds.mapNotNull { widgetId ->
            availableWidgets.find { it.id == widgetId }
        }
    }
    
    /**
     * Gets count of deprecated widgets that would be removed for a user.
     * 
     * @param userId The user identifier
     * @return Number of deprecated widgets in user's current preferences
     */
    suspend fun getDeprecatedWidgetCount(userId: String): Int {
        return try {
            val preferences = widgetPreferencesRepository
                .getWidgetPreferences(userId)
                .first()
            
            preferences.fold(
                onSuccess = { preferencesData ->
                    val deprecatedCount = preferencesData.visibleWidgets.count { widgetId ->
                        widgetId in AnalyticsWidget.DEPRECATED_WIDGET_IDS
                    }
                    Timber.d("User $userId has $deprecatedCount deprecated widgets")
                    deprecatedCount
                },
                onFailure = {
                    Timber.d("User $userId has no preferences, no deprecated widgets to remove")
                    0
                }
            )
        } catch (e: Exception) {
            Timber.e(e, "Error getting deprecated widget count for user: $userId")
            0
        }
    }
    
    /**
     * Checks if user needs analytics widget migration.
     * 
     * @param userId The user identifier
     * @return True if user has deprecated widgets in their preferences
     */
    suspend fun needsAnalyticsWidgetMigration(userId: String): Boolean {
        return getDeprecatedWidgetCount(userId) > 0
    }
    
    companion object {
        private const val MINIMUM_WIDGET_COUNT = 5
    }
}