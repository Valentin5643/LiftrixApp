package com.example.liftrix.domain.progress

import com.example.liftrix.domain.model.UserId
import com.example.liftrix.domain.model.WeightUnit
import com.example.liftrix.domain.model.analytics.AnalyticsWidget
import com.example.liftrix.domain.model.analytics.DashboardConfiguration
import com.example.liftrix.domain.model.analytics.DashboardLayoutMode
import com.example.liftrix.domain.model.analytics.TimeRange
import com.example.liftrix.domain.model.analytics.UserLevel
import com.example.liftrix.domain.model.analytics.VolumeCalendarData
import com.example.liftrix.domain.model.analytics.WidgetData
import com.example.liftrix.domain.model.analytics.WidgetLayoutMode
import com.example.liftrix.domain.model.analytics.WidgetPreferences
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.repository.DurationDataPoint
import com.example.liftrix.domain.repository.FrequencyDataPoint
import com.example.liftrix.domain.repository.ProgressSummary
import com.example.liftrix.domain.repository.VolumeDataPoint
import kotlinx.coroutines.flow.Flow
import java.util.Date

interface ProgressCaloriePort {
    suspend fun getCalorieSummary(userId: String): LiftrixResult<ProgressCalorieSummary>
    suspend fun getDailyCalories(userId: String, period: TimeRange): LiftrixResult<List<ProgressDailyCalorieData>>
    suspend fun getWeeklyTrend(userId: String): LiftrixResult<ProgressWeeklyCalorieTrend>
}

data class ProgressCalorieSummary(
    val totalCaloriesBurned: Int,
    val averageDailyCalories: Int,
    val totalWorkouts: Int,
    val averageWorkoutCalories: Int,
    val highestDailyCalories: Int,
    val currentWeekCalories: Int,
    val previousWeekCalories: Int,
    val weeklyTrend: Float
)

data class ProgressDailyCalorieData(
    val date: Date,
    val totalCalories: Int,
    val workoutCount: Int,
    val averageIntensity: Float,
    val topExerciseCategory: String?,
    val durationMinutes: Int
)

data class ProgressWeeklyCalorieTrend(
    val weeklyData: List<ProgressWeeklyCalorieData>,
    val movingAverage: Float,
    val trendPercentage: Float,
    val peakWeek: ProgressWeeklyCalorieData?,
    val lowWeek: ProgressWeeklyCalorieData?,
    val consistency: Int
)

data class ProgressWeeklyCalorieData(
    val weekStartDate: Date,
    val weekEndDate: Date,
    val totalCalories: Int,
    val workoutCount: Int,
    val averageDailyCalories: Int,
    val mostActiveDay: Date?
)

interface ProgressDataPort {
    suspend fun getVolumeData(userId: String, timeRange: TimeRange): LiftrixResult<List<VolumeDataPoint>>
    suspend fun getDurationData(userId: String, timeRange: TimeRange): LiftrixResult<List<DurationDataPoint>>
    suspend fun getFrequencyData(userId: String, timeRange: TimeRange): LiftrixResult<List<FrequencyDataPoint>>
    suspend fun getVolumeCalendarData(userId: String): LiftrixResult<VolumeCalendarData>
    suspend fun getProgressSummary(userId: String, timeRange: TimeRange): LiftrixResult<ProgressSummary>
    suspend fun refreshAllData(userId: String): LiftrixResult<Unit>
}

interface ProgressAnalyticsServicePort {
    suspend fun getWidgetData(userId: String, widget: AnalyticsWidget): LiftrixResult<WidgetData>
    suspend fun getWidgetPreferences(userId: String): LiftrixResult<WidgetPreferences>
    suspend fun updateWidgetPreferences(preferences: WidgetPreferences): LiftrixResult<Unit>
    suspend fun toggleWidgetVisibility(userId: String, widgetId: String): LiftrixResult<Unit>
    suspend fun resetPreferences(userId: String): LiftrixResult<Unit>
}

interface ProgressPreferencesPort {
    suspend fun getUserPreferences(userId: String): LiftrixResult<WidgetPreferences>
    suspend fun updateLayoutMode(userId: String, mode: WidgetLayoutMode): LiftrixResult<Unit>
    suspend fun updateUserLevel(userId: String, level: UserLevel): LiftrixResult<Unit>
    suspend fun resetToDefaults(userId: String): LiftrixResult<Unit>
    suspend fun updateWidgetVisibility(userId: String, widgetName: String, visible: Boolean): LiftrixResult<Unit>
    suspend fun updateWidgetOrder(userId: String, widgetOrder: List<String>): LiftrixResult<Unit>
    suspend fun updateAutoRefreshSettings(userId: String, enabled: Boolean, intervalMinutes: Int): LiftrixResult<Unit>
    suspend fun toggleSection(userId: String, sectionName: String): LiftrixResult<Unit>
    suspend fun saveWidgetPreferences(preferences: WidgetPreferences): LiftrixResult<Unit>
}

interface ProgressFeatureFlagPort {
    suspend fun isFeatureEnabled(featureKey: String): LiftrixResult<Boolean>
    suspend fun getABTestVariant(testKey: String): LiftrixResult<String>
    suspend fun getAllFeatureFlags(): LiftrixResult<Map<String, Boolean>>
    suspend fun refreshRemoteConfig(): LiftrixResult<Unit>
}

interface ProgressWidgetResolverPort {
    fun resolveWidgets(
        userLevel: UserLevel,
        layoutMode: DashboardLayoutMode = DashboardLayoutMode.AUTO,
        preferences: WidgetPreferences? = null
    ): List<AnalyticsWidget>

    fun resolveStandardWidgets(userLevel: UserLevel): List<AnalyticsWidget>
    fun resolveWidgetsFromPreferences(preferences: WidgetPreferences?, userLevel: UserLevel = UserLevel.BEGINNER): List<AnalyticsWidget>
    fun createDefaultPreferences(userId: String, userLevel: UserLevel, layoutMode: DashboardLayoutMode = DashboardLayoutMode.AUTO): WidgetPreferences
    fun forceCleanupDeprecatedWidgets(preferences: WidgetPreferences): WidgetPreferences
}

data class ProgressSessionSummary(
    val sessionActive: Boolean,
    val sessionId: String?
)

interface ProgressSessionSummaryPort {
    val currentSessionSummary: Flow<ProgressSessionSummary?>
}

interface ProgressAuthPort {
    suspend operator fun invoke(waitForAuth: Boolean = true): LiftrixResult<UserId>
}

interface ProgressExerciseCatalogPort {
    suspend operator fun invoke(): LiftrixResult<List<ProgressExerciseOption>>
}

data class ProgressExerciseOption(
    val id: String,
    val name: String,
    val primaryMuscleGroup: com.example.liftrix.domain.model.ExerciseCategory,
    val instructions: String?
)

interface ProgressSettingsPort {
    fun getWeightUnitPreference(userId: String): Flow<WeightUnit>
}

interface ProgressUnitConversionPort {
    suspend fun getCurrentWeightUnit(userId: String): WeightUnit
}
