package com.example.liftrix.domain.service

import com.example.liftrix.domain.model.analytics.WidgetPreferences
import com.example.liftrix.domain.model.common.LiftrixResult

/**
 * Handles widget business logic previously duplicated in use cases:
 * - SaveWidgetPreferencesUseCase (repair and validation logic)
 * - MigrateWidgetPreferencesUseCase (migration logic)
 * - FixWidgetPreferenceMigrationUseCase
 *
 * This service provides reusable widget operations that can be used across
 * multiple use cases while maintaining consistent business rules.
 *
 * **Design Philosophy**:
 * - Pure domain logic without repository dependencies
 * - Stateless operations for thread safety
 * - Comprehensive validation with detailed error messages
 * - Domain-level service following Clean Architecture principles
 *
 * **Key Responsibilities**:
 * - Widget preference consistency repair
 * - Widget configuration validation
 * - Deprecated widget migration
 * - Widget availability filtering
 */
interface WidgetOperationsService {

    /**
     * Repairs widget preference consistency issues.
     *
     * This method ensures that:
     * - Widget order includes all visible widgets
     * - Only valid widget IDs are included
     * - At least one widget remains visible
     * - Invalid or deprecated widgets are removed
     *
     * @param preferences The widget preferences to repair
     * @return Repaired WidgetPreferences with consistent state
     */
    fun repairPreferenceConsistency(preferences: WidgetPreferences): WidgetPreferences

    /**
     * Validates widget configuration according to business rules.
     *
     * Validation rules:
     * - User ID must not be blank
     * - At least one widget must be visible
     * - Widget order must contain all visible widgets
     * - Refresh interval must be between 1 and 60 minutes
     * - All widget IDs must reference valid widgets
     *
     * @param preferences The widget preferences to validate
     * @return LiftrixResult<Unit> indicating validation success or specific errors
     */
    fun validateWidgetConfiguration(preferences: WidgetPreferences): LiftrixResult<Unit>

    /**
     * Migrates deprecated widgets to their replacements.
     *
     * This handles the transition from deprecated widgets to consolidated widgets:
     * - calories_burned → (removed, calorie tracking deprecated)
     * - daily_calories → (removed, calorie tracking deprecated)
     * - weekly_calorie_trend → (removed, calorie tracking deprecated)
     * - duration_chart → (removed, duration tracking deprecated)
     * - set_completion_rate → (removed, set completion deprecated)
     *
     * @param preferences The widget preferences potentially containing deprecated widgets
     * @return WidgetPreferences with deprecated widgets migrated
     */
    fun migrateDeprecatedWidgets(preferences: WidgetPreferences): WidgetPreferences

    /**
     * Filters widget preferences to only include available widgets.
     *
     * Removes any widget IDs that don't correspond to currently available widgets,
     * which can occur after app updates or feature flag changes.
     *
     * @param preferences The widget preferences to filter
     * @return WidgetPreferences containing only available widgets
     */
    fun filterToAvailableWidgets(preferences: WidgetPreferences): WidgetPreferences

    /**
     * Ensures at least one widget remains visible.
     *
     * If all widgets would be hidden, this method ensures that a minimum set
     * of essential widgets remains visible for the user.
     *
     * @param preferences The widget preferences to check
     * @return WidgetPreferences with at least one widget visible
     */
    fun ensureMinimumWidgets(preferences: WidgetPreferences): WidgetPreferences

    /**
     * Determines if preferences need migration from deprecated widgets.
     *
     * @param preferences The widget preferences to check
     * @return Boolean indicating if migration is needed
     */
    fun needsDeprecatedWidgetMigration(preferences: WidgetPreferences): Boolean

    /**
     * Repairs and validates preferences in a single operation.
     *
     * Convenience method that combines repair, migration, and validation
     * for streamlined widget preference processing.
     *
     * @param preferences The widget preferences to process
     * @return LiftrixResult<WidgetPreferences> containing repaired and validated preferences
     */
    fun repairAndValidate(preferences: WidgetPreferences): LiftrixResult<WidgetPreferences>
}
