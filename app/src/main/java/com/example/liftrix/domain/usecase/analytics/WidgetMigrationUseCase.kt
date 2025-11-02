package com.example.liftrix.domain.usecase.analytics

import com.example.liftrix.domain.model.analytics.AnalyticsWidget
import com.example.liftrix.domain.model.analytics.UserLevel
import com.example.liftrix.domain.model.analytics.WidgetPreferences
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.WidgetPreferencesRepository
import com.example.liftrix.domain.service.WidgetOperationsService
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Consolidated use case for all widget preference migration operations.
 *
 * This use case consolidates functionality from:
 * - MigrateWidgetPreferencesUseCase (initial user migration)
 * - FixWidgetPreferenceMigrationUseCase (legacy name fixes)
 * - AnalyticsWidgetMigrationUseCase (deprecated widget migration)
 *
 * **Migration Types**:
 * 1. **Initial Migration**: New/existing users get appropriate defaults based on experience
 * 2. **Legacy Name Fix**: Converts old display names to proper enum names
 * 3. **Deprecated Widget Migration**: Removes calorie/duration widgets, ensures minimum set
 *
 * **Usage Examples**:
 * ```kotlin
 * // Initial migration for new user
 * widgetMigrationUseCase.migrateInitial(
 *     userId = "user123",
 *     totalWorkouts = 50,
 *     daysSinceFirstWorkout = 90
 * )
 *
 * // Fix legacy widget names
 * widgetMigrationUseCase.fixLegacyNames(userId = "user123")
 *
 * // Migrate from deprecated widgets
 * widgetMigrationUseCase.migrateDeprecatedWidgets(userId = "user123")
 *
 * // Check if migration is needed
 * if (widgetMigrationUseCase.needsMigration(userId)) {
 *     widgetMigrationUseCase.migrateInitial(userId)
 * }
 * ```
 *
 * @property repository Repository for widget preferences persistence
 * @property widgetOperationsService Service for widget business logic
 */
@Singleton
class WidgetMigrationUseCase @Inject constructor(
    private val repository: WidgetPreferencesRepository,
    private val widgetOperationsService: WidgetOperationsService
) {

    /**
     * Migrates existing user to widget preferences system with intelligent defaults.
     *
     * Analyzes user characteristics and creates appropriate widget preferences.
     * Designed to be called once for each existing user when they first encounter
     * the new preferences system.
     *
     * **Initial Migration Operation**: Creates preferences based on user experience level.
     *
     * @param userId The unique identifier for the user
     * @param totalWorkouts Total number of workouts completed by user
     * @param daysSinceFirstWorkout Days since user's first workout
     * @param legacyPreferences Any existing preference data to consider
     * @return LiftrixResult indicating success or failure
     */
    suspend fun migrateInitial(
        userId: String,
        totalWorkouts: Int = 0,
        daysSinceFirstWorkout: Int = 0,
        legacyPreferences: Map<String, Any>? = null
    ): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "INITIAL_MIGRATION_FAILED",
                errorMessage = "Failed to migrate widget preferences: ${throwable.message}",
                analyticsContext = mapOf(
                    "operation" to "migrateInitialWidgetPreferences",
                    "userId" to userId,
                    "totalWorkouts" to totalWorkouts.toString()
                )
            )
        }
    ) {
        require(userId.isNotBlank()) { "User ID cannot be blank" }
        require(totalWorkouts >= 0) { "Total workouts cannot be negative" }
        require(daysSinceFirstWorkout >= 0) { "Days since first workout cannot be negative" }

        Timber.d("Migrating widget preferences for user $userId (workouts: $totalWorkouts, days: $daysSinceFirstWorkout)")

        // Determine appropriate user level based on experience
        val userLevel = determineUserLevel(totalWorkouts, daysSinceFirstWorkout, legacyPreferences)

        // Create migration-specific preferences
        val migratedPreferences = createMigratedPreferences(
            userId = userId,
            userLevel = userLevel,
            legacyPreferences = legacyPreferences
        )

        // Save migrated preferences
        repository.saveWidgetPreferences(migratedPreferences).getOrThrow()

        Timber.i("Widget preferences migrated successfully for user $userId with level: $userLevel")
    }

    /**
     * Fixes widget preferences with legacy display names.
     *
     * Converts old display names (e.g., "Progress summary") to proper enum names
     * (e.g., "ProgressChart") and saves the corrected preferences permanently.
     *
     * **Legacy Name Fix Operation**: Repairs naming inconsistencies.
     *
     * @param userId The unique identifier for the user whose preferences need fixing
     * @return LiftrixResult indicating success or failure
     */
    suspend fun fixLegacyNames(userId: String): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "LEGACY_NAME_FIX_FAILED",
                errorMessage = "Failed to fix legacy widget names: ${throwable.message}",
                analyticsContext = mapOf(
                    "operation" to "fixLegacyWidgetNames",
                    "userId" to userId
                )
            )
        }
    ) {
        require(userId.isNotBlank()) { "User ID cannot be blank" }

        Timber.i("Applying widget preference migration fix for user: $userId")

        // Call the repository method to apply the migration fix
        repository.fixWidgetPreferenceMigration(userId).getOrThrow()

        Timber.i("Widget preference migration fix applied successfully for user: $userId")
    }

    /**
     * Migrates user from deprecated widgets to modern focused analytics.
     *
     * Removes deprecated calorie and duration widgets while ensuring users have
     * a minimum viable widget set for their dashboard.
     *
     * **Deprecated Widget Migration Operation**: Removes old widgets, adds defaults.
     *
     * @param userId The unique identifier for the user
     * @return LiftrixResult indicating success or failure
     */
    suspend fun migrateDeprecatedWidgets(userId: String): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "DEPRECATED_WIDGET_MIGRATION_FAILED",
                errorMessage = "Failed to migrate deprecated widgets: ${throwable.message}",
                analyticsContext = mapOf(
                    "operation" to "migrateDeprecatedWidgets",
                    "userId" to userId
                )
            )
        }
    ) {
        require(userId.isNotBlank()) { "User ID cannot be blank" }

        Timber.d("Starting analytics widget migration for user: $userId")

        // Get current widget preferences
        val currentPreferences = repository.getWidgetPreferences(userId).first()

        currentPreferences.fold(
            onSuccess = { preferences ->
                // Migrate using widget operations service
                val migrated = widgetOperationsService.migrateDeprecatedWidgets(preferences)
                val ensuredMinimum = widgetOperationsService.ensureMinimumWidgets(migrated)

                // Save updated preferences
                repository.saveWidgetPreferences(ensuredMinimum).getOrThrow()

                Timber.i("Analytics widget migration completed for user: $userId")
                Timber.i("Final widget count: ${ensuredMinimum.visibleWidgets.size}")
            },
            onFailure = { error ->
                Timber.w("User $userId has no existing preferences, creating default active widget set")

                // Create fresh preferences with active widgets only
                val activeWidgets = AnalyticsWidget.getActiveWidgets()
                val defaultActiveWidgets = getDefaultActiveWidgets(activeWidgets).map { it.id }.toSet()

                // Create new preferences through repository migration
                repository.migrateUserPreferences(
                    userId = userId,
                    legacyConfiguration = mapOf(
                        "visibleWidgets" to defaultActiveWidgets.toList(),
                        "migration_reason" to "analytics_widget_deprecation"
                    )
                ).getOrThrow()
            }
        )
    }

    /**
     * Checks if user needs any type of migration.
     *
     * @param userId The user identifier to check
     * @return Boolean indicating if migration is needed
     */
    suspend fun needsMigration(userId: String): Boolean {
        return try {
            // Check if preferences exist
            val preferencesResult = repository.getWidgetPreferences(userId).first()

            preferencesResult.fold(
                onSuccess = { preferences ->
                    // Check if needs deprecated widget migration
                    widgetOperationsService.needsDeprecatedWidgetMigration(preferences)
                },
                onFailure = {
                    // No preferences exist, needs initial migration
                    true
                }
            )
        } catch (e: Exception) {
            Timber.e(e, "Error checking migration status for user: $userId")
            true // Err on the side of attempting migration
        }
    }

    /**
     * Checks if user needs legacy name fix specifically.
     *
     * @param userId The user identifier to check
     * @return Boolean indicating if legacy name fix is needed
     */
    suspend fun needsLegacyNameFix(userId: String): Boolean {
        return try {
            val preferencesResult = repository.getWidgetPreferences(userId).first()

            preferencesResult.fold(
                onSuccess = { preferences ->
                    // Check if any visible widgets contain legacy display names
                    val legacyNames = setOf(
                        "Progress summary", "Total volume", "Active time", "Best streak",
                        "Consistency", "Today's calories", "Calories burned", "Weekly calories",
                        "Volume trend", "Workout duration", "Frequency calendar"
                    )

                    preferences.visibleWidgets.any { it in legacyNames }
                },
                onFailure = { false }
            )
        } catch (e: Exception) {
            Timber.e(e, "Error checking legacy name fix status for user: $userId")
            false
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
            val preferences = repository.getWidgetPreferences(userId).first()

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

    // Private helper methods

    /**
     * Determines appropriate user level based on usage history.
     */
    private fun determineUserLevel(
        totalWorkouts: Int,
        daysSinceFirstWorkout: Int,
        legacyPreferences: Map<String, Any>?
    ): UserLevel {
        // Check for explicit level in legacy preferences
        legacyPreferences?.get("userLevel")?.let { level ->
            when (level.toString().lowercase()) {
                "intermediate" -> return UserLevel.INTERMEDIATE
                "advanced" -> return UserLevel.ADVANCED
                "beginner" -> return UserLevel.BEGINNER
            }
        }

        // Determine level based on usage patterns
        return when {
            // Advanced users: Long-term consistent users
            totalWorkouts >= 100 && daysSinceFirstWorkout >= 180 -> UserLevel.ADVANCED

            // Intermediate users: Regular users with good consistency
            totalWorkouts >= 30 && daysSinceFirstWorkout >= 60 -> UserLevel.INTERMEDIATE

            // Intermediate users: High frequency users regardless of total time
            totalWorkouts >= 50 -> UserLevel.INTERMEDIATE

            // Everyone else starts as beginner
            else -> UserLevel.BEGINNER
        }
    }

    /**
     * Creates migrated preferences with intelligent defaults.
     */
    private fun createMigratedPreferences(
        userId: String,
        userLevel: UserLevel,
        legacyPreferences: Map<String, Any>?
    ): WidgetPreferences {
        // Start with standard defaults for the user level
        val basePreferences = WidgetPreferences.createDefault(userId, userLevel)

        // Apply any legacy preference overrides
        return legacyPreferences?.let { legacy ->
            applyLegacyPreferences(basePreferences, legacy)
        } ?: basePreferences
    }

    /**
     * Applies legacy preferences to new preference structure.
     */
    private fun applyLegacyPreferences(
        basePreferences: WidgetPreferences,
        legacyPreferences: Map<String, Any>
    ): WidgetPreferences {
        var preferences = basePreferences

        // Apply legacy widget visibility if available
        legacyPreferences["visibleWidgets"]?.let { widgets ->
            if (widgets is List<*>) {
                val widgetNames = widgets.mapNotNull { it as? String }.toSet()
                if (widgetNames.isNotEmpty()) {
                    preferences = preferences.updateVisibleWidgets(widgetNames)
                }
            }
        }

        // Apply legacy layout mode if available
        legacyPreferences["dashboardLayout"]?.let { layout ->
            val layoutMode = when (layout.toString().lowercase()) {
                "grid" -> com.example.liftrix.domain.model.analytics.DashboardLayoutMode.GRID
                "list" -> com.example.liftrix.domain.model.analytics.DashboardLayoutMode.SECTIONS
                "custom" -> com.example.liftrix.domain.model.analytics.DashboardLayoutMode.CUSTOM
                else -> com.example.liftrix.domain.model.analytics.DashboardLayoutMode.AUTO
            }
            preferences = preferences.updateLayout(layoutMode)
        }

        // Apply legacy refresh settings if available
        legacyPreferences["autoRefresh"]?.let { enabled ->
            if (enabled is Boolean) {
                val interval = (legacyPreferences["refreshInterval"] as? Number)?.toInt() ?: 5
                preferences = preferences.updateAutoRefresh(enabled, interval)
            }
        }

        return preferences
    }

    /**
     * Gets sensible default widget selection for users after deprecation migration.
     */
    private fun getDefaultActiveWidgets(availableWidgets: List<AnalyticsWidget>): List<AnalyticsWidget> {
        // Priority order for default widgets (strength-focused)
        val priorityWidgetIds = listOf(
            "one_rm_progression",
            "total_volume",
            "volume_chart",
            "workout_frequency",
            "personal_records",
            "muscle_group_distribution",
            "strength_progress",
            "volume_trends"
        )

        return priorityWidgetIds.mapNotNull { widgetId ->
            availableWidgets.find { it.id == widgetId }
        }
    }
}
