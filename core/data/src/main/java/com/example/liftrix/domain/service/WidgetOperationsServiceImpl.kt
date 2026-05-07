package com.example.liftrix.domain.service

import com.example.liftrix.domain.model.analytics.AnalyticsWidget
import com.example.liftrix.domain.model.analytics.WidgetPreferences
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of WidgetOperationsService providing widget business logic.
 *
 * This service consolidates widget operations previously duplicated across
 * multiple use cases, ensuring consistency in widget preference handling.
 *
 * **Thread Safety**: All methods are stateless and thread-safe.
 * **Performance**: Optimized for minimal allocations and fast execution.
 */
@Singleton
class WidgetOperationsServiceImpl @Inject constructor() : WidgetOperationsService {

    override fun repairPreferenceConsistency(preferences: WidgetPreferences): WidgetPreferences {
        Timber.d("Repairing widget preference consistency for user: ${preferences.userId}")

        var repaired = preferences

        // Step 1: Filter to available widgets
        repaired = filterToAvailableWidgets(repaired)

        // Step 2: Migrate deprecated widgets
        repaired = migrateDeprecatedWidgets(repaired)

        // Step 3: Ensure minimum widgets
        repaired = ensureMinimumWidgets(repaired)

        // Step 4: Ensure widget order includes all visible widgets
        val existingValidOrder = repaired.widgetOrder.filter { it in repaired.visibleWidgets }
        val missingFromOrder = repaired.visibleWidgets - existingValidOrder.toSet()
        val repairedWidgetOrder = existingValidOrder + missingFromOrder.toList()

        repaired = repaired.copy(
            widgetOrder = repairedWidgetOrder,
            lastModified = kotlinx.datetime.Clock.System.now()
        )

        Timber.d("Repaired preferences - Visible: ${repaired.visibleWidgets.size}, Order: ${repaired.widgetOrder.size}")
        return repaired
    }

    override fun validateWidgetConfiguration(preferences: WidgetPreferences): LiftrixResult<Unit> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.ValidationError(
                    field = "widgetPreferences",
                    violations = listOf(throwable.message ?: "Widget validation failed")
                )
            }
        ) {
            val violations = mutableListOf<String>()

            // Validate user ID
            if (preferences.userId.isBlank()) {
                violations.add("User ID cannot be blank")
            }

            // Validate at least one widget visible
            if (preferences.visibleWidgets.isEmpty()) {
                violations.add("At least one widget must be visible")
            }

            // Validate widget order contains all visible widgets
            val missingWidgets = preferences.visibleWidgets - preferences.widgetOrder.toSet()
            if (missingWidgets.isNotEmpty()) {
                violations.add("Widget order must include all visible widgets. Missing: ${missingWidgets.joinToString(", ")}")
            }

            // Validate refresh interval
            if (preferences.refreshIntervalMinutes !in 1..60) {
                violations.add("Refresh interval must be between 1 and 60 minutes")
            }

            // Validate all widget IDs are valid
            val availableWidgetIds = getAvailableWidgetIds()
            val invalidWidgets = preferences.visibleWidgets.filter { it !in availableWidgetIds }
            if (invalidWidgets.isNotEmpty()) {
                violations.add("Invalid widget IDs: ${invalidWidgets.joinToString(", ")}")
            }

            if (violations.isNotEmpty()) {
                Timber.w("Widget validation failed: ${violations.joinToString("; ")}")
                throw IllegalArgumentException(violations.joinToString("; "))
            }

            Timber.d("Widget configuration validated successfully for user: ${preferences.userId}")
        }
    }

    override fun migrateDeprecatedWidgets(preferences: WidgetPreferences): WidgetPreferences {
        val deprecatedWidgets = setOf(
            "calories_burned",
            "daily_calories",
            "weekly_calorie_trend",
            "duration_chart",
            "set_completion_rate"
        )

        val visibleWithoutDeprecated = preferences.visibleWidgets - deprecatedWidgets
        val orderWithoutDeprecated = preferences.widgetOrder.filter { it !in deprecatedWidgets }

        if (visibleWithoutDeprecated.size < preferences.visibleWidgets.size) {
            Timber.d("Migrated ${preferences.visibleWidgets.size - visibleWithoutDeprecated.size} deprecated widgets")
        }

        return if (visibleWithoutDeprecated != preferences.visibleWidgets ||
                   orderWithoutDeprecated != preferences.widgetOrder) {
            preferences.copy(
                visibleWidgets = visibleWithoutDeprecated,
                widgetOrder = orderWithoutDeprecated,
                hasSeenWidgetMigrationNotice = false, // Show migration notice
                lastModified = kotlinx.datetime.Clock.System.now()
            )
        } else {
            preferences
        }
    }

    override fun filterToAvailableWidgets(preferences: WidgetPreferences): WidgetPreferences {
        val availableWidgetIds = getAvailableWidgetIds()

        val filteredVisible = preferences.visibleWidgets.filter { it in availableWidgetIds }.toSet()
        val filteredOrder = preferences.widgetOrder.filter { it in availableWidgetIds }

        return if (filteredVisible != preferences.visibleWidgets || filteredOrder != preferences.widgetOrder) {
            Timber.d("Filtered out ${preferences.visibleWidgets.size - filteredVisible.size} unavailable widgets")
            preferences.copy(
                visibleWidgets = filteredVisible,
                widgetOrder = filteredOrder,
                lastModified = kotlinx.datetime.Clock.System.now()
            )
        } else {
            preferences
        }
    }

    override fun ensureMinimumWidgets(preferences: WidgetPreferences): WidgetPreferences {
        if (preferences.visibleWidgets.isNotEmpty()) {
            return preferences
        }

        Timber.w("No widgets visible, adding default minimum widgets for user: ${preferences.userId}")

        // Get minimum essential widgets
        val availableWidgetIds = getAvailableWidgetIds()
        val essentialWidgets = listOf(
            AnalyticsWidget.StrengthAnalytics.id,
            AnalyticsWidget.VolumeAnalytics.id,
            AnalyticsWidget.FrequencyChart.id,
            AnalyticsWidget.MuscleGroupDistribution.id
        ).filter { it in availableWidgetIds }

        val minimumWidgets = essentialWidgets.take(2).toSet() // At least 2 essential widgets

        return preferences.copy(
            visibleWidgets = minimumWidgets,
            widgetOrder = minimumWidgets.toList(),
            lastModified = kotlinx.datetime.Clock.System.now()
        )
    }

    override fun needsDeprecatedWidgetMigration(preferences: WidgetPreferences): Boolean {
        val deprecatedWidgets = setOf(
            "calories_burned",
            "daily_calories",
            "weekly_calorie_trend",
            "duration_chart",
            "set_completion_rate"
        )

        return preferences.visibleWidgets.any { it in deprecatedWidgets }
    }

    override fun repairAndValidate(preferences: WidgetPreferences): LiftrixResult<WidgetPreferences> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.ValidationError(
                    field = "widgetPreferences",
                    violations = listOf(throwable.message ?: "Widget repair and validation failed")
                )
            }
        ) {
            // Step 1: Repair consistency
            val repaired = repairPreferenceConsistency(preferences)

            // Step 2: Validate
            validateWidgetConfiguration(repaired).getOrThrow()

            // Step 3: Return repaired preferences
            repaired
        }
    }

    // Private helper methods

    /**
     * Gets all available widget IDs from the AnalyticsWidget enum.
     */
    private fun getAvailableWidgetIds(): Set<String> {
        return try {
            AnalyticsWidget.getAllWidgets().map { it.id }.toSet()
        } catch (e: Exception) {
            Timber.e(e, "Failed to get available widget IDs")
            emptySet()
        }
    }
}
