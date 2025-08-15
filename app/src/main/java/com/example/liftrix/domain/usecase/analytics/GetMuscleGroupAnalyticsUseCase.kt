package com.example.liftrix.domain.usecase.analytics

import com.example.liftrix.data.local.dao.ExerciseDao
import com.example.liftrix.data.local.dao.ExerciseSetDao
import com.example.liftrix.data.local.dao.MuscleGroupDistributionResult
import com.example.liftrix.data.local.dao.MuscleGroupVolumeResult
import com.example.liftrix.domain.model.analytics.TimeRangeType
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.workout.WorkoutRepository
import com.example.liftrix.service.AnalyticsEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Use case for comprehensive muscle group analytics and balance analysis.
 * 
 * This use case provides real-time muscle group analytics from actual workout data,
 * replacing mock data with database-driven analysis. Includes distribution analysis,
 * balance recommendations, and volume tracking per muscle group.
 * 
 * Features:
 * - Real-time muscle group distribution from database
 * - Balance analysis with recommendations
 * - Volume tracking per muscle group over time
 * - Imbalance detection and scoring
 * - Performance optimized for <500ms response times
 * - Proper error handling with LiftrixError context
 * - Support for filtering by specific muscle groups
 * 
 * Usage:
 * ```
 * val muscleGroupAnalytics = getMuscleGroupAnalyticsUseCase.execute(
 *     userId = "user123",
 *     muscleGroup = MuscleGroup.CHEST,
 *     timeRange = TimeRangeType.SIX_MONTHS
 * )
 * ```
 */
@Singleton
class GetMuscleGroupAnalyticsUseCase @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val exerciseDao: ExerciseDao,
    private val exerciseSetDao: ExerciseSetDao,
    private val analyticsEngine: AnalyticsEngine
) {
    
    /**
     * Executes muscle group analytics for specified muscle group and time range.
     * 
     * @param userId User ID for scoping data access
     * @param muscleGroup Specific muscle group to analyze (null for all muscle groups)
     * @param timeRange Time range for analysis (THREE_MONTHS, SIX_MONTHS, ONE_YEAR, ALL_TIME)
     * @return LiftrixResult containing muscle group analytics data or error
     */
    suspend fun execute(
        userId: String,
        muscleGroup: MuscleGroup? = null,
        timeRange: TimeRangeType
    ): LiftrixResult<MuscleGroupAnalyticsData> = withContext(Dispatchers.IO) {
        return@withContext liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.BusinessLogicError(
                    code = "MUSCLE_GROUP_ANALYTICS_FAILED",
                    errorMessage = "Failed to retrieve muscle group analytics for user $userId, muscleGroup $muscleGroup, timeRange $timeRange: ${throwable.message}",
                    analyticsContext = mapOf(
                        "operation" to "getMuscleGroupAnalytics",
                        "userId" to userId,
                        "muscleGroup" to (muscleGroup?.displayName ?: "ALL"),
                        "timeRange" to timeRange.name
                    )
                )
            }
        ) {
            // Validate user ID to prevent data leakage
            if (userId.isBlank()) {
                throw IllegalArgumentException("User ID cannot be blank")
            }
            
            Timber.d("Retrieving muscle group analytics for user: $userId, muscleGroup: $muscleGroup, timeRange: $timeRange")
            
            val (startDate, endDate) = calculateDateRange(timeRange)
            
            val distributionData = exerciseDao.getMuscleGroupDistribution(userId, startDate, endDate)
            val volumeData = exerciseSetDao.getVolumeDataByMuscleGroup(userId, startDate, endDate)
            
            val filteredDistribution = muscleGroup?.let { targetGroup ->
                distributionData.filter { it.primary_muscle_group.equals(targetGroup.displayName, ignoreCase = true) }
            } ?: distributionData
            
            val filteredVolume = muscleGroup?.let { targetGroup ->
                volumeData.filter { it.primary_muscle_group.equals(targetGroup.displayName, ignoreCase = true) }
            } ?: volumeData
            
            val muscleGroupDistribution = buildMuscleGroupDistribution(filteredDistribution, filteredVolume)
            val balanceAnalysis = buildBalanceAnalysis(distributionData, volumeData)
            val recommendations = generateRecommendations(balanceAnalysis)
            
            MuscleGroupAnalyticsData(
                muscleGroupDistribution = muscleGroupDistribution,
                balanceAnalysis = balanceAnalysis,
                recommendations = recommendations,
                totalVolume = filteredVolume.sumOf { it.total_volume },
                totalExercises = filteredDistribution.sumOf { it.unique_exercises },
                timeRange = timeRange,
                targetMuscleGroup = muscleGroup,
                isEmpty = muscleGroupDistribution.isEmpty()
            )
        }
    }
    
    /**
     * Calculates date range based on time range type.
     */
    private fun calculateDateRange(timeRange: TimeRangeType): Pair<String, String> {
        val endDate = LocalDate.now()
        val startDate = when (timeRange) {
            TimeRangeType.MONTH -> endDate.minusMonths(1)
            TimeRangeType.SIX_MONTHS -> endDate.minusMonths(6)
            TimeRangeType.ALL_TIME -> LocalDate.of(2020, 1, 1) // App launch date
        }
        
        val formatter = DateTimeFormatter.ISO_LOCAL_DATE
        return Pair(startDate.format(formatter), endDate.format(formatter))
    }
    
    /**
     * Builds muscle group distribution data combining frequency and volume metrics.
     */
    private fun buildMuscleGroupDistribution(
        distributionData: List<MuscleGroupDistributionResult>,
        volumeData: List<MuscleGroupVolumeResult>
    ): List<MuscleGroupData> {
        val volumeMap = volumeData.associateBy { it.primary_muscle_group }
        
        return distributionData.map { distribution ->
            val volume = volumeMap[distribution.primary_muscle_group]
            
            MuscleGroupData(
                muscleGroup = MuscleGroup.fromDisplayName(distribution.primary_muscle_group),
                exerciseCount = distribution.exercise_count,
                uniqueExercises = distribution.unique_exercises,
                workoutDays = distribution.workout_days,
                totalVolume = volume?.total_volume ?: 0.0,
                totalSets = volume?.total_sets ?: 0,
                percentage = 0.0 // Will be calculated after all data is collected
            )
        }.let { muscleGroupList ->
            // Calculate percentages based on total volume
            val totalVolume = muscleGroupList.sumOf { it.totalVolume }
            if (totalVolume > 0) {
                muscleGroupList.map { data ->
                    data.copy(percentage = (data.totalVolume / totalVolume) * 100)
                }
            } else {
                muscleGroupList
            }
        }
    }
    
    /**
     * Builds comprehensive balance analysis across all muscle groups.
     */
    private fun buildBalanceAnalysis(
        distributionData: List<MuscleGroupDistributionResult>,
        volumeData: List<MuscleGroupVolumeResult>
    ): BalanceAnalysis {
        if (volumeData.isEmpty()) {
            return BalanceAnalysis(
                imbalances = emptyList(),
                balanceScore = 100.0,
                mostTrained = null,
                leastTrained = null
            )
        }
        
        val totalVolume = volumeData.sumOf { it.total_volume }
        val expectedPercentage = 100.0 / volumeData.size
        
        val imbalances = volumeData.mapNotNull { data ->
            val actualPercentage = (data.total_volume / totalVolume) * 100
            val deviation = abs(actualPercentage - expectedPercentage)
            
            if (deviation > IMBALANCE_THRESHOLD) {
                MuscleGroupImbalance(
                    muscleGroup = MuscleGroup.fromDisplayName(data.primary_muscle_group),
                    currentPercentage = actualPercentage,
                    expectedPercentage = expectedPercentage,
                    deviation = deviation,
                    severity = when {
                        deviation > 20 -> ImbalanceSeverity.HIGH
                        deviation > 10 -> ImbalanceSeverity.MEDIUM
                        else -> ImbalanceSeverity.LOW
                    }
                )
            } else null
        }
        
        val balanceScore = calculateBalanceScore(imbalances)
        val mostTrained = volumeData.maxByOrNull { it.total_volume }?.let { 
            MuscleGroup.fromDisplayName(it.primary_muscle_group) 
        }
        val leastTrained = volumeData.minByOrNull { it.total_volume }?.let { 
            MuscleGroup.fromDisplayName(it.primary_muscle_group) 
        }
        
        return BalanceAnalysis(
            imbalances = imbalances,
            balanceScore = balanceScore,
            mostTrained = mostTrained,
            leastTrained = leastTrained
        )
    }
    
    /**
     * Generates actionable recommendations based on balance analysis.
     */
    private fun generateRecommendations(balanceAnalysis: BalanceAnalysis): List<String> {
        val recommendations = mutableListOf<String>()
        
        if (balanceAnalysis.balanceScore < 70) {
            recommendations.add("Your muscle group balance needs improvement. Focus on balancing your training.")
        }
        
        balanceAnalysis.imbalances.forEach { imbalance ->
            when (imbalance.severity) {
                ImbalanceSeverity.HIGH -> {
                    if (imbalance.currentPercentage > imbalance.expectedPercentage) {
                        recommendations.add("Reduce ${imbalance.muscleGroup.displayName} volume and focus on undertrained groups.")
                    } else {
                        recommendations.add("Significantly increase ${imbalance.muscleGroup.displayName} training volume.")
                    }
                }
                ImbalanceSeverity.MEDIUM -> {
                    if (imbalance.currentPercentage < imbalance.expectedPercentage) {
                        recommendations.add("Add more ${imbalance.muscleGroup.displayName} exercises to your routine.")
                    }
                }
                ImbalanceSeverity.LOW -> {
                    // No specific recommendation for low severity imbalances
                }
            }
        }
        
        if (recommendations.isEmpty()) {
            recommendations.add("Great job! Your muscle group training is well balanced.")
        }
        
        return recommendations
    }
    
    /**
     * Calculates overall balance score based on imbalances.
     */
    private fun calculateBalanceScore(imbalances: List<MuscleGroupImbalance>): Double {
        if (imbalances.isEmpty()) return 100.0
        
        val totalDeviation = imbalances.sumOf { imbalance ->
            when (imbalance.severity) {
                ImbalanceSeverity.HIGH -> imbalance.deviation * 2
                ImbalanceSeverity.MEDIUM -> imbalance.deviation * 1.5
                ImbalanceSeverity.LOW -> imbalance.deviation
            }
        }
        
        // Score decreases based on total weighted deviation
        return maxOf(0.0, 100.0 - (totalDeviation / imbalances.size))
    }
    
    companion object {
        private const val IMBALANCE_THRESHOLD = 5.0 // 5% deviation threshold
    }
}

/**
 * Enum representing different muscle groups.
 */
enum class MuscleGroup(val displayName: String) {
    CHEST("Chest"),
    BACK("Back"),
    SHOULDERS("Shoulders"),
    ARMS("Arms"),
    LEGS("Legs"),
    CORE("Core"),
    GLUTES("Glutes"),
    CARDIO("Cardio"),
    OTHER("Other");
    
    companion object {
        fun fromDisplayName(displayName: String): MuscleGroup {
            return values().find { 
                it.displayName.equals(displayName, ignoreCase = true) 
            } ?: OTHER
        }
    }
}

/**
 * Enum representing imbalance severity levels.
 */
enum class ImbalanceSeverity {
    LOW, MEDIUM, HIGH
}

/**
 * Data class representing complete muscle group analytics.
 */
data class MuscleGroupAnalyticsData(
    val muscleGroupDistribution: List<MuscleGroupData>,
    val balanceAnalysis: BalanceAnalysis,
    val recommendations: List<String>,
    val totalVolume: Double,
    val totalExercises: Int,
    val timeRange: TimeRangeType,
    val targetMuscleGroup: MuscleGroup?,
    val isEmpty: Boolean
)

/**
 * Data class representing individual muscle group data.
 */
data class MuscleGroupData(
    val muscleGroup: MuscleGroup,
    val exerciseCount: Int,
    val uniqueExercises: Int,
    val workoutDays: Int,
    val totalVolume: Double,
    val totalSets: Int,
    val percentage: Double
)

/**
 * Data class representing balance analysis results.
 */
data class BalanceAnalysis(
    val imbalances: List<MuscleGroupImbalance>,
    val balanceScore: Double, // 0-100 scale
    val mostTrained: MuscleGroup?,
    val leastTrained: MuscleGroup?
)

/**
 * Data class representing muscle group imbalance.
 */
data class MuscleGroupImbalance(
    val muscleGroup: MuscleGroup,
    val currentPercentage: Double,
    val expectedPercentage: Double,
    val deviation: Double,
    val severity: ImbalanceSeverity
)