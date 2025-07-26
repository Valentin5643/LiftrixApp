package com.example.liftrix.service

import com.example.liftrix.domain.model.Workout
import com.example.liftrix.domain.model.UserProfile
import com.example.liftrix.domain.model.analytics.CalorieCalculator
import com.example.liftrix.domain.model.analytics.TimeRange
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.repository.MetDataRepository
import com.example.liftrix.domain.repository.workout.WorkoutRepository
import com.example.liftrix.domain.repository.UserRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Calendar
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * Implementation of CalorieService providing MET-based calorie calculations and data aggregation.
 * 
 * Integrates with CalorieCalculator for accurate MET-based calculations and provides temporal
 * aggregation of calorie data across different time periods. Uses repository pattern for data
 * access and follows Clean Architecture principles.
 * 
 * Performance Characteristics:
 * - Background processing using IoDispatcher for database operations and CPU-intensive calculations
 * - Efficient data aggregation with minimal database queries
 * - Caching-friendly design for repeated calculations
 * - Proper dispatcher usage: IO for database operations, computation within same context for efficiency
 * 
 * @property calorieCalculator MET-based calorie calculation engine
 * @property metDataRepository Repository for MET data access
 * @property workoutRepository Repository for workout data access
 * @property userRepository Repository for user profile access
 * @property dispatcher Coroutine dispatcher for background processing
 */
@Singleton
class CalorieServiceImpl @Inject constructor(
    private val calorieCalculator: CalorieCalculator,
    private val metDataRepository: MetDataRepository,
    private val workoutRepository: WorkoutRepository,
    private val userRepository: UserRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : CalorieService {
    
    companion object {
        private const val WEEKS_FOR_TREND_ANALYSIS = 12
        private const val MOVING_AVERAGE_WEEKS = 4
        private const val MIN_WORKOUTS_FOR_AVERAGE = 1
        private const val MAX_CONSISTENCY_SCORE = 100
        private const val DEFAULT_CALORIES_PER_WORKOUT = 250 // Conservative estimate
    }
    
    override suspend fun getCalorieSummary(userId: String): LiftrixResult<CalorieSummary> =
        withContext(dispatcher) {
            runCatching {
                Timber.d("CalorieServiceImpl: Getting calorie summary for user: $userId")
                
                // Get current week and previous week data
                val currentWeekRange = getCurrentWeekRange()
                val previousWeekRange = getPreviousWeekRange()
                val monthRange = getCurrentMonthRange()
                
                Timber.d("CalorieServiceImpl: Time ranges - current: $currentWeekRange, previous: $previousWeekRange, month: $monthRange")
                
                // Get workouts for analysis
                val currentWeekWorkouts = getWorkoutsForPeriod(userId, currentWeekRange)
                val previousWeekWorkouts = getWorkoutsForPeriod(userId, previousWeekRange)
                val monthWorkouts = getWorkoutsForPeriod(userId, monthRange)
                
                Timber.d("CalorieServiceImpl: Workouts found - current week: ${currentWeekWorkouts.size}, previous week: ${previousWeekWorkouts.size}, month: ${monthWorkouts.size}")
                
                // Calculate calorie metrics
                val currentWeekCalories = calculateTotalCalories(currentWeekWorkouts, userId)
                val previousWeekCalories = calculateTotalCalories(previousWeekWorkouts, userId)
                val totalCaloriesBurned = calculateTotalCalories(monthWorkouts, userId)
                
                // Calculate averages and trends
                val totalWorkouts = monthWorkouts.size
                val averageWorkoutCalories = if (totalWorkouts > 0) {
                    totalCaloriesBurned / totalWorkouts
                } else {
                    DEFAULT_CALORIES_PER_WORKOUT
                }
                
                val daysInMonth = monthRange.getDurationInDays()
                val averageDailyCalories = if (daysInMonth > 0) {
                    totalCaloriesBurned / daysInMonth
                } else {
                    0
                }
                
                // Calculate highest daily calories
                val dailyCaloriesMap = monthWorkouts.groupBy { 
                    it.date 
                }.mapValues { (_, workouts) -> 
                    calculateTotalCalories(workouts, userId) 
                }
                val highestDailyCalories = dailyCaloriesMap.values.maxOrNull() ?: 0
                
                // Calculate weekly trend
                val weeklyTrend = if (previousWeekCalories > 0) {
                    ((currentWeekCalories - previousWeekCalories).toFloat() / previousWeekCalories) * 100
                } else {
                    0f
                }
                
                CalorieSummary(
                    totalCaloriesBurned = totalCaloriesBurned,
                    averageDailyCalories = averageDailyCalories,
                    totalWorkouts = totalWorkouts,
                    averageWorkoutCalories = averageWorkoutCalories,
                    highestDailyCalories = highestDailyCalories,
                    currentWeekCalories = currentWeekCalories,
                    previousWeekCalories = previousWeekCalories,
                    weeklyTrend = weeklyTrend
                )
            }.fold(
                onSuccess = { LiftrixResult.success(it) },
                onFailure = { error ->
                    LiftrixResult.failure(
                        RuntimeException("Failed to get calorie summary for user $userId: ${error.message}", error)
                    )
                }
            )
        }
    
    override suspend fun getDailyCalories(userId: String, period: TimeRange): LiftrixResult<List<DailyCalorieData>> =
        withContext(dispatcher) {
            runCatching {
                // Get workouts for the specified period
                val workouts = getWorkoutsForPeriod(userId, period)
                
                // Group workouts by date
                val workoutsByDate = workouts.groupBy { it.date }
                
                // Generate date range for the period
                val dateRange = generateDateRange(period)
                
                // Calculate daily calorie data for each date
                dateRange.map { date ->
                    val dayWorkouts = workoutsByDate[date] ?: emptyList()
                    val totalCalories = calculateTotalCalories(dayWorkouts, userId)
                    val workoutCount = dayWorkouts.size
                    
                    // Calculate average intensity (MET value)
                    val averageIntensity = if (dayWorkouts.isNotEmpty()) {
                        calculateAverageIntensity(dayWorkouts, userId)
                    } else {
                        0f
                    }
                    
                    // Find top exercise category
                    val topExerciseCategory = findTopExerciseCategory(dayWorkouts)
                    
                    // Calculate total duration
                    val durationMinutes = dayWorkouts.sumOf { workout ->
                        if (workout.startTime != null && workout.endTime != null) {
                            Duration.between(workout.startTime, workout.endTime).toMinutes().toInt()
                        } else {
                            0
                        }
                    }
                    
                    DailyCalorieData(
                        date = Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant()),
                        totalCalories = totalCalories,
                        workoutCount = workoutCount,
                        averageIntensity = averageIntensity,
                        topExerciseCategory = topExerciseCategory,
                        durationMinutes = durationMinutes
                    )
                }
            }.fold(
                onSuccess = { LiftrixResult.success(it) },
                onFailure = { error ->
                    LiftrixResult.failure(
                        RuntimeException("Failed to get daily calories for user $userId: ${error.message}", error)
                    )
                }
            )
        }
    
    override suspend fun getWeeklyTrend(userId: String): LiftrixResult<WeeklyCalorieTrend> =
        withContext(dispatcher) {
            runCatching {
                // Generate weekly ranges for the last 12 weeks
                val weeklyRanges = generateWeeklyRanges(WEEKS_FOR_TREND_ANALYSIS)
                
                // Calculate calorie data for each week
                val weeklyData = weeklyRanges.map { weekRange ->
                    val weekWorkouts = getWorkoutsForPeriod(userId, weekRange)
                    val totalCalories = calculateTotalCalories(weekWorkouts, userId)
                    val workoutCount = weekWorkouts.size
                    val averageDailyCalories = totalCalories / 7 // 7 days per week
                    
                    // Find most active day
                    val dailyCaloriesMap = weekWorkouts.groupBy { it.date }
                        .mapValues { (_, workouts) -> calculateTotalCalories(workouts, userId) }
                    val mostActiveDay = dailyCaloriesMap.maxByOrNull { it.value }?.key
                    
                    WeeklyCalorieData(
                        weekStartDate = weekRange.startDate,
                        weekEndDate = weekRange.endDate,
                        totalCalories = totalCalories,
                        workoutCount = workoutCount,
                        averageDailyCalories = averageDailyCalories,
                        mostActiveDay = mostActiveDay?.let { 
                            Date.from(it.atStartOfDay(ZoneId.systemDefault()).toInstant()) 
                        }
                    )
                }
                
                // Calculate moving average
                val movingAverage = calculateMovingAverage(weeklyData, MOVING_AVERAGE_WEEKS)
                
                // Calculate trend percentage
                val trendPercentage = calculateTrendPercentage(weeklyData)
                
                // Find peak and low weeks
                val peakWeek = weeklyData.maxByOrNull { it.totalCalories }
                val lowWeek = weeklyData.minByOrNull { it.totalCalories }
                
                // Calculate consistency score
                val consistency = calculateConsistencyScore(weeklyData)
                
                WeeklyCalorieTrend(
                    weeklyData = weeklyData,
                    movingAverage = movingAverage,
                    trendPercentage = trendPercentage,
                    peakWeek = peakWeek,
                    lowWeek = lowWeek,
                    consistency = consistency
                )
            }.fold(
                onSuccess = { LiftrixResult.success(it) },
                onFailure = { error ->
                    LiftrixResult.failure(
                        RuntimeException("Failed to get weekly trend for user $userId: ${error.message}", error)
                    )
                }
            )
        }
    
    override suspend fun calculateWorkoutCalories(workout: Workout): LiftrixResult<Int> =
        withContext(dispatcher) {
            runCatching {
                // Get user profile for personalized calculations
                val userProfile = getUserProfile(workout.userId)
                
                // Calculate workout duration
                val duration = if (workout.startTime != null && workout.endTime != null) {
                    Duration.between(workout.startTime, workout.endTime)
                } else {
                    // Estimate duration based on exercise count (conservative estimate)
                    Duration.ofMinutes(workout.exercises.size * 5L) // 5 minutes per exercise
                }
                
                // Use CalorieCalculator for accurate MET-based calculation
                calorieCalculator.calculateCaloriesBurned(
                    exercises = workout.exercises,
                    duration = duration,
                    userProfile = userProfile
                )
            }.fold(
                onSuccess = { LiftrixResult.success(it) },
                onFailure = { error ->
                    LiftrixResult.failure(
                        RuntimeException("Failed to calculate workout calories for workout ${workout.id}: ${error.message}", error)
                    )
                }
            )
        }
    
    // Private helper methods
    
    private suspend fun getWorkoutsForPeriod(userId: String, period: TimeRange): List<Workout> {
        return try {
            val startDate = period.startDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
            val endDate = period.endDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
            
            Timber.d("CalorieServiceImpl: getWorkoutsForPeriod - userId: $userId, startDate: $startDate, endDate: $endDate")
            
            // Use getWorkoutsByUser and filter by date range since getWorkoutsByDateRange doesn't exist
            val workouts = workoutRepository.getWorkoutsByUser(userId)
                .map { result: LiftrixResult<List<Workout>> ->
                    result.fold(
                        onSuccess = { workoutList ->
                            val filteredWorkouts = workoutList.filter { workout: Workout ->
                                val workoutDate = workout.date
                                workoutDate != null && !workoutDate.isBefore(startDate) && !workoutDate.isAfter(endDate)
                            }
                            Timber.d("CalorieServiceImpl: Found ${workoutList.size} total workouts, ${filteredWorkouts.size} in period")
                            filteredWorkouts
                        },
                        onFailure = { exception ->
                            Timber.e("CalorieServiceImpl: Error getting workouts: ${exception.message}")
                            emptyList<Workout>()
                        }
                    )
                }
                .first()
            
            Timber.d("CalorieServiceImpl: Returning ${workouts.size} workouts for period")
            workouts
        } catch (e: Exception) {
            Timber.e(e, "CalorieServiceImpl: Exception in getWorkoutsForPeriod")
            emptyList()
        }
    }
    
    private suspend fun calculateTotalCalories(workouts: List<Workout>, userId: String): Int {
        return workouts.sumOf { workout ->
            calculateWorkoutCalories(workout).getOrNull() ?: DEFAULT_CALORIES_PER_WORKOUT
        }
    }
    
    private suspend fun calculateAverageIntensity(workouts: List<Workout>, userId: String): Float {
        if (workouts.isEmpty()) return 0f
        
        val userProfile = getUserProfile(userId)
        var totalMetValue = 0f
        var exerciseCount = 0
        
        workouts.forEach { workout ->
            workout.exercises.forEach { exercise ->
                // Estimate MET value for the exercise category
                val category = exercise.libraryExercise.primaryMuscleGroup.name
                val metValue = metDataRepository.getAverageMetForCategory(category)
                    .getOrNull() ?: 5.0f // Default moderate intensity
                totalMetValue += metValue
                exerciseCount++
            }
        }
        
        return if (exerciseCount > 0) totalMetValue / exerciseCount else 0f
    }
    
    private fun findTopExerciseCategory(workouts: List<Workout>): String? {
        if (workouts.isEmpty()) return null
        
        val categoryCount = mutableMapOf<String, Int>()
        workouts.forEach { workout ->
            workout.exercises.forEach { exercise ->
                val category = exercise.libraryExercise.primaryMuscleGroup.name
                categoryCount[category] = categoryCount.getOrDefault(category, 0) + 1
            }
        }
        
        return categoryCount.maxByOrNull { it.value }?.key
    }
    
    private suspend fun getUserProfile(userId: String): UserProfile {
        return userRepository.getUserProfile(userId).getOrNull()
            ?: createDefaultUserProfile(userId)
    }
    
    private fun createDefaultUserProfile(userId: String): UserProfile {
        // Create a default profile for calorie calculations when user profile is not available
        return UserProfile(
            userId = userId,
            displayName = "User", // Default display name
            bio = null, // No bio by default
            weight = com.example.liftrix.domain.model.Weight(70.0), // 70kg default
            age = 30, // 30 years default
            availableEquipment = emptyList(),
            otherEquipment = null,
            fitnessGoals = emptyList(),
            goalsPriority = null,
            lastActiveAt = java.time.LocalDateTime.now(), // Set as current time
            memberSince = java.time.LocalDateTime.now(), // Set as current time
            completedAt = null,
            updatedAt = java.time.LocalDateTime.now()
        )
    }
    
    private fun getCurrentWeekRange(): TimeRange {
        return TimeRange.lastWeek()
    }
    
    private fun getPreviousWeekRange(): TimeRange {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.WEEK_OF_YEAR, -1)
        val endDate = calendar.time
        calendar.add(Calendar.WEEK_OF_YEAR, -1)
        val startDate = calendar.time
        return TimeRange(startDate, endDate)
    }
    
    private fun getCurrentMonthRange(): TimeRange {
        return TimeRange.lastMonth()
    }
    
    private fun generateDateRange(period: TimeRange): List<LocalDate> {
        val startDate = period.startDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
        val endDate = period.endDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
        
        val dates = mutableListOf<LocalDate>()
        var current = startDate
        while (!current.isAfter(endDate)) {
            dates.add(current)
            current = current.plusDays(1)
        }
        return dates
    }
    
    private fun generateWeeklyRanges(weeks: Int): List<TimeRange> {
        val ranges = mutableListOf<TimeRange>()
        val calendar = Calendar.getInstance()
        
        repeat(weeks) { weekIndex ->
            // Set to start of current week minus weekIndex weeks
            calendar.time = Date()
            calendar.add(Calendar.WEEK_OF_YEAR, -weekIndex)
            calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            val weekStart = calendar.time
            
            calendar.add(Calendar.DAY_OF_WEEK, 6)
            val weekEnd = calendar.time
            
            ranges.add(TimeRange(weekStart, weekEnd))
        }
        
        return ranges.reversed() // Return in chronological order
    }
    
    private fun calculateMovingAverage(weeklyData: List<WeeklyCalorieData>, windowSize: Int): Float {
        if (weeklyData.size < windowSize) return 0f
        
        val recentWeeks = weeklyData.takeLast(windowSize)
        val totalCalories = recentWeeks.sumOf { it.totalCalories }
        return totalCalories.toFloat() / windowSize
    }
    
    private fun calculateTrendPercentage(weeklyData: List<WeeklyCalorieData>): Float {
        if (weeklyData.size < 2) return 0f
        
        val firstHalf = weeklyData.take(weeklyData.size / 2)
        val secondHalf = weeklyData.drop(weeklyData.size / 2)
        
        val firstHalfAverage = firstHalf.map { it.totalCalories }.average()
        val secondHalfAverage = secondHalf.map { it.totalCalories }.average()
        
        return if (firstHalfAverage > 0) {
            ((secondHalfAverage - firstHalfAverage) / firstHalfAverage * 100).toFloat()
        } else {
            0f
        }
    }
    
    private fun calculateConsistencyScore(weeklyData: List<WeeklyCalorieData>): Int {
        if (weeklyData.isEmpty()) return 0
        
        val calories = weeklyData.map { it.totalCalories.toDouble() }
        val mean = calories.average()
        
        if (mean == 0.0) return 0
        
        val variance = calories.map { (it - mean) * (it - mean) }.average()
        val standardDeviation = kotlin.math.sqrt(variance)
        val coefficientOfVariation = standardDeviation / mean
        
        // Convert to consistency score (lower CV = higher consistency)
        val consistencyRatio = 1.0 - coefficientOfVariation.coerceIn(0.0, 1.0)
        return (consistencyRatio * MAX_CONSISTENCY_SCORE).toInt()
    }
}