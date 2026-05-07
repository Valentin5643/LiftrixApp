package com.example.liftrix.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.example.liftrix.domain.model.analytics.DashboardLayoutMode
import com.example.liftrix.domain.model.analytics.UserLevel
import com.example.liftrix.domain.model.analytics.WidgetDisplaySize
import com.example.liftrix.domain.model.analytics.WidgetPreferences
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.WidgetPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Implementation of WidgetPreferencesRepository using DataStore for persistence.
 * 
 * This implementation provides offline-first storage for widget preferences using
 * Android DataStore with Preferences API. All operations are scoped by userId to
 * support multi-user scenarios and maintain data isolation.
 * 
 * @property widgetPreferencesDataStore DataStore instance for widget preferences
 */
@Singleton
class WidgetPreferencesRepositoryImpl @Inject constructor(
    @Named("widgetPreferences") private val widgetPreferencesDataStore: DataStore<Preferences>
) : WidgetPreferencesRepository {
    
    private val json = Json { 
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    override fun getWidgetPreferences(userId: String): Flow<LiftrixResult<WidgetPreferences>> {
        return widgetPreferencesDataStore.data
            .map { preferences ->
                try {
                    val widgetPreferences = extractPreferencesForUser(userId, preferences)
                    Result.success(widgetPreferences)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to load widget preferences for user: $userId")
                    Result.failure(LiftrixError.DatabaseError("Failed to load widget preferences: ${e.message}"))
                }
            }
            .catch { exception ->
                Timber.e(exception, "DataStore error loading widget preferences for user: $userId")
                emit(Result.failure(LiftrixError.DatabaseError("DataStore error: ${exception.message}")))
            }
    }
    
    override suspend fun saveWidgetPreferences(preferences: WidgetPreferences): LiftrixResult<Unit> {
        return try {
            preferences.validate()
            
            widgetPreferencesDataStore.edit { prefs ->
                savePreferencesForUser(preferences, prefs)
            }
            
            Timber.d("Widget preferences saved successfully for user: ${preferences.userId}")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to save widget preferences for user: ${preferences.userId}")
            Result.failure(LiftrixError.DatabaseError("Failed to save widget preferences: ${e.message}"))
        }
    }
    
    override suspend fun updateWidgetVisibility(
        userId: String,
        widgetName: String,
        visible: Boolean
    ): LiftrixResult<Unit> {
        return try {
            widgetPreferencesDataStore.edit { prefs ->
                val currentPreferences = extractPreferencesForUser(userId, prefs)
                val updatedPreferences = currentPreferences.toggleWidget(widgetName)
                savePreferencesForUser(updatedPreferences, prefs)
            }
            
            Timber.d("Widget visibility updated for user $userId: $widgetName = $visible")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to update widget visibility for user: $userId")
            Result.failure(LiftrixError.DatabaseError("Failed to update widget visibility: ${e.message}"))
        }
    }
    
    override suspend fun updateWidgetOrder(
        userId: String,
        widgetOrder: List<String>
    ): LiftrixResult<Unit> {
        return try {
            widgetPreferencesDataStore.edit { prefs ->
                val currentPreferences = extractPreferencesForUser(userId, prefs)
                val updatedPreferences = currentPreferences.updateWidgetOrder(widgetOrder)
                savePreferencesForUser(updatedPreferences, prefs)
            }
            
            Timber.d("Widget order updated for user $userId: $widgetOrder")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to update widget order for user: $userId")
            Result.failure(LiftrixError.DatabaseError("Failed to update widget order: ${e.message}"))
        }
    }
    
    override suspend fun updateDashboardLayout(
        userId: String,
        layoutMode: DashboardLayoutMode
    ): LiftrixResult<Unit> {
        return try {
            widgetPreferencesDataStore.edit { prefs ->
                val currentPreferences = extractPreferencesForUser(userId, prefs)
                val updatedPreferences = currentPreferences.updateLayout(layoutMode)
                savePreferencesForUser(updatedPreferences, prefs)
            }
            
            Timber.d("Dashboard layout updated for user $userId: $layoutMode")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to update dashboard layout for user: $userId")
            Result.failure(LiftrixError.DatabaseError("Failed to update dashboard layout: ${e.message}"))
        }
    }
    
    override suspend fun updateUserLevel(
        userId: String,
        userLevel: UserLevel
    ): LiftrixResult<Unit> {
        return try {
            widgetPreferencesDataStore.edit { prefs ->
                val currentPreferences = extractPreferencesForUser(userId, prefs)
                val updatedPreferences = currentPreferences.updateUserLevel(userLevel)
                savePreferencesForUser(updatedPreferences, prefs)
            }
            
            Timber.d("User level updated for user $userId: $userLevel")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to update user level for user: $userId")
            Result.failure(LiftrixError.DatabaseError("Failed to update user level: ${e.message}"))
        }
    }
    
    override suspend fun toggleSection(
        userId: String,
        sectionName: String
    ): LiftrixResult<Unit> {
        return try {
            widgetPreferencesDataStore.edit { prefs ->
                val currentPreferences = extractPreferencesForUser(userId, prefs)
                val updatedPreferences = currentPreferences.toggleSection(sectionName)
                savePreferencesForUser(updatedPreferences, prefs)
            }
            
            Timber.d("Section toggled for user $userId: $sectionName")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to toggle section for user: $userId")
            Result.failure(LiftrixError.DatabaseError("Failed to toggle section: ${e.message}"))
        }
    }
    
    override suspend fun updateWidgetSize(
        userId: String,
        widgetName: String,
        size: WidgetDisplaySize
    ): LiftrixResult<Unit> {
        return try {
            widgetPreferencesDataStore.edit { prefs ->
                val currentPreferences = extractPreferencesForUser(userId, prefs)
                val updatedPreferences = currentPreferences.updateWidgetSize(widgetName, size)
                savePreferencesForUser(updatedPreferences, prefs)
            }
            
            Timber.d("Widget size updated for user $userId: $widgetName = $size")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to update widget size for user: $userId")
            Result.failure(LiftrixError.DatabaseError("Failed to update widget size: ${e.message}"))
        }
    }
    
    override suspend fun updateAutoRefreshSettings(
        userId: String,
        enabled: Boolean,
        intervalMinutes: Int
    ): LiftrixResult<Unit> {
        return try {
            widgetPreferencesDataStore.edit { prefs ->
                val currentPreferences = extractPreferencesForUser(userId, prefs)
                val updatedPreferences = currentPreferences.updateAutoRefresh(enabled, intervalMinutes)
                savePreferencesForUser(updatedPreferences, prefs)
            }
            
            Timber.d("Auto-refresh settings updated for user $userId: enabled=$enabled, interval=$intervalMinutes")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to update auto-refresh settings for user: $userId")
            Result.failure(LiftrixError.DatabaseError("Failed to update auto-refresh settings: ${e.message}"))
        }
    }
    
    override suspend fun resetToDefaults(
        userId: String,
        userLevel: UserLevel?
    ): LiftrixResult<Unit> {
        return try {
            val defaultPreferences = WidgetPreferences.createDefault(
                userId = userId,
                userLevel = userLevel ?: UserLevel.BEGINNER
            )
            
            saveWidgetPreferences(defaultPreferences)
        } catch (e: Exception) {
            Timber.e(e, "Failed to reset preferences to defaults for user: $userId")
            Result.failure(LiftrixError.DatabaseError("Failed to reset preferences: ${e.message}"))
        }
    }
    
    override suspend fun migrateUserPreferences(
        userId: String,
        legacyConfiguration: Map<String, Any>?
    ): LiftrixResult<Unit> {
        return try {
            // Check if user already has preferences
            val currentPrefs = widgetPreferencesDataStore.data.map { prefs ->
                extractPreferencesForUser(userId, prefs)
            }
            
            // If user already has preferences, don't migrate
            currentPrefs.collect { existing ->
                if (existing.lastModified == kotlinx.datetime.Clock.System.now()) {
                    return@collect
                }
            }
            
            // Create default preferences for existing users
            val defaultPreferences = if (legacyConfiguration != null) {
                // Attempt to extract user level from legacy configuration
                val legacyLevel = legacyConfiguration["userLevel"] as? String
                val userLevel = when (legacyLevel?.lowercase()) {
                    "intermediate" -> UserLevel.INTERMEDIATE
                    "advanced" -> UserLevel.ADVANCED
                    else -> UserLevel.BEGINNER
                }
                
                WidgetPreferences.createDefault(userId, userLevel)
            } else {
                WidgetPreferences.createDefault(userId, UserLevel.BEGINNER)
            }
            
            saveWidgetPreferences(defaultPreferences)
            
            Timber.d("User preferences migrated successfully for user: $userId")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to migrate user preferences for user: $userId")
            Result.failure(LiftrixError.DatabaseError("Failed to migrate preferences: ${e.message}"))
        }
    }
    
    override suspend fun exportPreferences(userId: String): LiftrixResult<String> {
        return try {
            val serializedPrefs = widgetPreferencesDataStore.data.map { prefs ->
                val preferences = extractPreferencesForUser(userId, prefs)
                json.encodeToString(preferences)
            }.first()
            
            Result.success(serializedPrefs)
        } catch (e: Exception) {
            Timber.e(e, "Failed to export preferences for user: $userId")
            Result.failure(LiftrixError.FileSystemError("Failed to export preferences: ${e.message}"))
        }
    }
    
    override suspend fun importPreferences(
        userId: String,
        preferencesData: String
    ): LiftrixResult<Unit> {
        return try {
            val preferences = json.decodeFromString<WidgetPreferences>(preferencesData)
            val updatedPreferences = preferences.copy(userId = userId)
            
            saveWidgetPreferences(updatedPreferences)
        } catch (e: Exception) {
            Timber.e(e, "Failed to import preferences for user: $userId")
            Result.failure(LiftrixError.FileSystemError("Failed to import preferences: ${e.message}"))
        }
    }
    
    override suspend fun clearUserPreferences(userId: String): LiftrixResult<Unit> {
        return try {
            widgetPreferencesDataStore.edit { prefs ->
                // Remove all keys for this user
                val keysToRemove = prefs.asMap().keys.filter { key ->
                    key.name.startsWith("${userId}_")
                }
                
                keysToRemove.forEach { key ->
                    prefs.remove(key)
                }
            }
            
            Timber.d("User preferences cleared for user: $userId")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to clear preferences for user: $userId")
            Result.failure(LiftrixError.DatabaseError("Failed to clear preferences: ${e.message}"))
        }
    }
    
    /**
     * Extracts preferences for a specific user from DataStore preferences.
     */
    private fun extractPreferencesForUser(userId: String, prefs: Preferences): WidgetPreferences {
        val visibleWidgetsKey = stringSetPreferencesKey("${userId}_visible_widgets")
        val widgetOrderKey = stringPreferencesKey("${userId}_widget_order")
        val dashboardLayoutKey = stringPreferencesKey("${userId}_dashboard_layout")
        val userLevelKey = stringPreferencesKey("${userId}_user_level")
        val collapsedSectionsKey = stringSetPreferencesKey("${userId}_collapsed_sections")
        val widgetSizesKey = stringPreferencesKey("${userId}_widget_sizes")
        val autoRefreshEnabledKey = booleanPreferencesKey("${userId}_auto_refresh_enabled")
        val refreshIntervalKey = intPreferencesKey("${userId}_refresh_interval")
        val lastModifiedKey = longPreferencesKey("${userId}_last_modified")
        
        val rawVisibleWidgets = prefs[visibleWidgetsKey] ?: WidgetPreferences.createDefault(userId).visibleWidgets
        val migratedVisibleWidgets = migrateWidgetNames(rawVisibleWidgets)
        
        val widgetOrderString = prefs[widgetOrderKey] ?: ""
        val rawWidgetOrder = if (widgetOrderString.isNotBlank()) {
            widgetOrderString.split(",").map { it.trim() }
        } else {
            WidgetPreferences.createDefault(userId).widgetOrder
        }
        val migratedWidgetOrder = migrateWidgetNames(rawWidgetOrder.toSet()).toList()
        
        val dashboardLayoutString = prefs[dashboardLayoutKey] ?: DashboardLayoutMode.AUTO.name
        val dashboardLayout = DashboardLayoutMode.values().find { it.name == dashboardLayoutString }
            ?: DashboardLayoutMode.AUTO
        
        val userLevelString = prefs[userLevelKey] ?: UserLevel.BEGINNER.name
        val userLevel = UserLevel.values().find { it.name == userLevelString } ?: UserLevel.BEGINNER
        
        val collapsedSections = prefs[collapsedSectionsKey] ?: emptySet()
        
        val widgetSizesString = prefs[widgetSizesKey] ?: ""
        val widgetSizes = if (widgetSizesString.isNotBlank()) {
            try {
                json.decodeFromString<Map<String, WidgetDisplaySize>>(widgetSizesString)
            } catch (e: Exception) {
                emptyMap()
            }
        } else {
            emptyMap()
        }
        
        val autoRefreshEnabled = prefs[autoRefreshEnabledKey] ?: true
        val refreshInterval = prefs[refreshIntervalKey] ?: 5
        val lastModifiedEpoch = prefs[lastModifiedKey] ?: kotlinx.datetime.Clock.System.now().epochSeconds
        val lastModified = Instant.fromEpochSeconds(lastModifiedEpoch)
        
        return WidgetPreferences(
            userId = userId,
            visibleWidgets = migratedVisibleWidgets,
            widgetOrder = migratedWidgetOrder,
            dashboardLayout = dashboardLayout,
            userLevel = userLevel,
            collapsedSections = collapsedSections,
            widgetSizes = widgetSizes,
            enableAutoRefresh = autoRefreshEnabled,
            refreshIntervalMinutes = refreshInterval,
            lastModified = lastModified
        )
    }
    
    /**
     * Saves preferences for a specific user to DataStore preferences.
     */
    private suspend fun savePreferencesForUser(preferences: WidgetPreferences, prefs: androidx.datastore.preferences.core.MutablePreferences) {
        val userId = preferences.userId
        
        val visibleWidgetsKey = stringSetPreferencesKey("${userId}_visible_widgets")
        val widgetOrderKey = stringPreferencesKey("${userId}_widget_order")
        val dashboardLayoutKey = stringPreferencesKey("${userId}_dashboard_layout")
        val userLevelKey = stringPreferencesKey("${userId}_user_level")
        val collapsedSectionsKey = stringSetPreferencesKey("${userId}_collapsed_sections")
        val widgetSizesKey = stringPreferencesKey("${userId}_widget_sizes")
        val autoRefreshEnabledKey = booleanPreferencesKey("${userId}_auto_refresh_enabled")
        val refreshIntervalKey = intPreferencesKey("${userId}_refresh_interval")
        val lastModifiedKey = longPreferencesKey("${userId}_last_modified")
        
        prefs[visibleWidgetsKey] = preferences.visibleWidgets
        prefs[widgetOrderKey] = preferences.widgetOrder.joinToString(",")
        prefs[dashboardLayoutKey] = preferences.dashboardLayout.name
        prefs[userLevelKey] = preferences.userLevel.name
        prefs[collapsedSectionsKey] = preferences.collapsedSections
        prefs[widgetSizesKey] = json.encodeToString(preferences.widgetSizes)
        prefs[autoRefreshEnabledKey] = preferences.enableAutoRefresh
        prefs[refreshIntervalKey] = preferences.refreshIntervalMinutes
        prefs[lastModifiedKey] = preferences.lastModified.epochSeconds
    }
    
    /**
     * Migrates old widget names to current AnalyticsWidget enum values.
     * 
     * This function handles migration of widget preferences from previous versions
     * where widget names may have changed or been removed. It maps legacy widget names
     * (PascalCase, display names, legacy snake_case) to the canonical snake_case IDs
     * used by the AnalyticsWidget enum.
     * 
     * @param widgetNames Collection of widget names to migrate
     * @return Set of valid widget names that exist in the current AnalyticsWidget enum
     */
    private fun migrateWidgetNames(widgetNames: Collection<String>): Set<String> {
        // Get all current valid widget names (canonical snake_case format)
        val validWidgetNames = try {
            com.example.liftrix.domain.model.analytics.AnalyticsWidget.getAllWidgets().map { it.id }.toSet()
        } catch (e: Exception) {
            Timber.e(e, "Failed to get valid widget names")
            return emptySet()
        }
        
        // Migration mapping FROM legacy formats TO canonical snake_case format
        val migrationMap = mapOf(
            // PRIORITY: PascalCase legacy IDs to snake_case canonical IDs
            "WorkoutFrequency" to "workout_frequency",
            "TotalVolume" to "total_volume",
            "CaloriesBurned" to "calories_burned",
            "ConsistencyStreak" to "consistency_streak",
            "ProgressChart" to "progress_chart",
            "VolumeCalendar" to "volume_calendar",
            "StrengthProgress" to "strength_progress",
            "PersonalRecords" to "personal_records",
            "VolumeTrends" to "volume_trends",
            "RecoveryMetrics" to "recovery_metrics",
            "PerformanceAnalysis" to "performance_analysis",
            "DailyCalories" to "daily_calories",
            "WeeklyCalorieTrend" to "weekly_calorie_trend",
            "AverageDuration" to "average_duration",
            "VolumeLoadProgression" to "volume_load_progression",
            "OneRMProgression" to "one_rm_progression",
            "WeeklyTrends" to "weekly_trends",
            "MuscleGroupDistribution" to "muscle_group_distribution",
            "RecoveryPatterns" to "recovery_patterns",
            "TrainingIntensity" to "training_intensity",
            "ExerciseVariety" to "exercise_variety",
            "TimeOfDayAnalysis" to "time_of_day_analysis",
            "SetCompletionRate" to "set_completion_rate",
            "MonthlySummary" to "monthly_summary",
            "GoalAchievement" to "goal_achievement",
            "VolumeChart" to "volume_chart",
            "DurationChart" to "duration_chart",
            "FrequencyChart" to "frequency_chart",
            "WorkoutStreak" to "workout_streak",
            
            // Legacy display names to canonical snake_case
            "Workout Frequency" to "workout_frequency",
            "Total Volume" to "total_volume",
            "Calories Burned" to "calories_burned",
            "Consistency Streak" to "consistency_streak",
            "Progress Chart" to "progress_chart",
            "Volume Calendar" to "volume_calendar",
            "Strength Progress" to "strength_progress",
            "Personal Records" to "personal_records",
            "Volume Trends" to "volume_trends",
            "Recovery Metrics" to "recovery_metrics",
            "Performance Analysis" to "performance_analysis",
            "Today's Calories" to "daily_calories",
            "Daily Calories" to "daily_calories",
            "Weekly Calorie Trend" to "weekly_calorie_trend",
            "Average Duration" to "average_duration",
            "Volume Progression" to "volume_load_progression",
            "1RM Progression" to "one_rm_progression",
            "Weekly Trends" to "weekly_trends",
            "Muscle Group Distribution" to "muscle_group_distribution",
            "Recovery Patterns" to "recovery_patterns",
            "Training Intensity" to "training_intensity",
            "Exercise Variety" to "exercise_variety",
            "Optimal Timing" to "time_of_day_analysis",
            "Set Completion Rate" to "set_completion_rate",
            "Monthly Summary" to "monthly_summary",
            "Goal Achievement" to "goal_achievement",
            "Volume Chart" to "volume_chart",
            "Duration Chart" to "duration_chart",
            "Frequency Chart" to "frequency_chart",
            "Workout Streak" to "workout_streak",
            
            // Legacy variations
            "Current streak" to "consistency_streak",
            "Best streak" to "consistency_streak",
            "Consistency" to "consistency_streak",
            "Active time" to "average_duration",
            "Workout duration" to "average_duration",
            "Personal record" to "personal_records",
            "Weekly calories" to "weekly_calorie_trend",
            "Weekly average" to "weekly_calorie_trend",
            "Volume trend" to "volume_trends",
            "Progress summary" to "progress_chart",
            "Frequency calendar" to "volume_calendar"
        )
        
        val migratedWidgets = mutableSetOf<String>()
        
        for (widgetName in widgetNames) {
            when {
                // Widget name is already valid
                widgetName in validWidgetNames -> {
                    migratedWidgets.add(widgetName)
                }
                // Widget name needs migration
                migrationMap.containsKey(widgetName) -> {
                    val migratedName = migrationMap[widgetName]!!
                    if (migratedName in validWidgetNames) {
                        migratedWidgets.add(migratedName)
                        Timber.d("Migrated widget: '$widgetName' -> '$migratedName'")
                    } else {
                        Timber.w("Migration target '$migratedName' for '$widgetName' is not valid")
                    }
                }
                // Widget name is invalid and can't be migrated
                else -> {
                    Timber.w("Removing invalid widget name: '$widgetName'")
                }
            }
        }
        
        // If no valid widgets remain after migration, provide safe defaults (canonical snake_case)
        if (migratedWidgets.isEmpty()) {
            Timber.i("No valid widgets after migration, using defaults")
            val defaultWidgets = setOf(
                "total_volume",
                "workout_frequency", 
                "consistency_streak",
                "calories_burned"
            ).filter { it in validWidgetNames }
            
            migratedWidgets.addAll(defaultWidgets)
        }
        
        Timber.d("Widget migration completed: ${widgetNames.size} -> ${migratedWidgets.size} widgets")
        return migratedWidgets
    }
    
    /**
     * Manually applies widget preference migration and saves the corrected preferences.
     * This fixes the issue where old display names are stored instead of proper enum names.
     * 
     * @param userId The user whose preferences should be migrated
     * @return LiftrixResult indicating success or failure
     */
    override suspend fun fixWidgetPreferenceMigration(userId: String): LiftrixResult<Unit> {
        return try {
            widgetPreferencesDataStore.edit { prefs ->
                // Extract current preferences (this applies migration automatically)
                val currentPreferences = extractPreferencesForUser(userId, prefs)
                
                // Force a repair to ensure consistency
                val repairedPreferences = currentPreferences.repairConsistency()
                
                // Save the migrated preferences back to storage
                savePreferencesForUser(repairedPreferences, prefs)
                
                Timber.i("Widget preference migration applied successfully for user: $userId")
                Timber.d("Migrated visible widgets: ${repairedPreferences.visibleWidgets}")
                Timber.d("Migrated widget order: ${repairedPreferences.widgetOrder}")
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to apply widget preference migration for user: $userId")
            Result.failure(LiftrixError.DatabaseError("Failed to apply migration: ${e.message}"))
        }
    }
}