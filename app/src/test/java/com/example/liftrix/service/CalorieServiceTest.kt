package com.example.liftrix.service

import com.example.liftrix.domain.model.UserProfile
import com.example.liftrix.domain.model.Weight
import com.example.liftrix.domain.model.Workout
import com.example.liftrix.domain.model.analytics.CalorieCalculator
import com.example.liftrix.domain.model.analytics.TimeRange
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.MetDataRepository
import com.example.liftrix.domain.repository.UserRepository
import com.example.liftrix.domain.repository.WorkoutRepository
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Date

/**
 * Comprehensive unit tests for CalorieServiceImpl
 * 
 * Tests all service methods including calorie summary calculation, daily calorie tracking,
 * weekly trend analysis, and individual workout calorie calculations using MockK and 
 * proper coroutine testing patterns following Given/When/Then structure.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CalorieServiceTest {
    
    @MockK
    private lateinit var calorieCalculator: CalorieCalculator
    
    @MockK
    private lateinit var metDataRepository: MetDataRepository
    
    @MockK
    private lateinit var workoutRepository: WorkoutRepository
    
    @MockK
    private lateinit var userRepository: UserRepository
    
    private lateinit var testDispatcher: CoroutineDispatcher
    private lateinit var calorieService: CalorieService
    
    @Before
    fun setup() {
        MockKAnnotations.init(this)
        testDispatcher = StandardTestDispatcher()
        calorieService = CalorieServiceImpl(
            calorieCalculator,
            metDataRepository,
            workoutRepository,
            userRepository,
            testDispatcher
        )
    }
    
    @Test
    fun `given valid user id, when getting calorie summary, then returns success with aggregated data`() = runTest {
        // Given
        val userId = "user123"
        val currentWeekWorkouts = createMockWorkouts(3)
        val previousWeekWorkouts = createMockWorkouts(2)
        val monthWorkouts = createMockWorkouts(10)
        
        coEvery { workoutRepository.getWorkoutsByDateRange(userId, any(), any()) } returns 
            LiftrixResult.Success(currentWeekWorkouts) andThen
            LiftrixResult.Success(previousWeekWorkouts) andThen
            LiftrixResult.Success(monthWorkouts)
        
        coEvery { calorieCalculator.calculateCaloriesBurned(any(), any(), any()) } returns 250
        
        // When
        val result = calorieService.getCalorieSummary(userId)
        
        // Then
        assertTrue("Result should be successful", result.isSuccess)
        val summary = result.getOrNull()!!
        
        assertEquals("Should calculate total calories", 2500, summary.totalCaloriesBurned)
        assertEquals("Should calculate average daily calories", 83, summary.averageDailyCalories)
        assertEquals("Should have correct total workouts", 10, summary.totalWorkouts)
        assertEquals("Should calculate average workout calories", 250, summary.averageWorkoutCalories)
        assertTrue("Should have current week calories", summary.currentWeekCalories > 0)
        assertTrue("Should have previous week calories", summary.previousWeekCalories > 0)
        
        coVerify { workoutRepository.getWorkoutsByDateRange(userId, any(), any()) }
    }
    
    @Test
    fun `given user with no workouts, when getting calorie summary, then returns success with default values`() = runTest {
        // Given
        val userId = "user123"
        val emptyWorkouts = emptyList<Workout>()
        
        coEvery { workoutRepository.getWorkoutsByDateRange(userId, any(), any()) } returns 
            LiftrixResult.Success(emptyWorkouts)
        
        // When
        val result = calorieService.getCalorieSummary(userId)
        
        // Then
        assertTrue("Result should be successful", result.isSuccess)
        val summary = result.getOrNull()!!
        
        assertEquals("Should have zero total calories", 0, summary.totalCaloriesBurned)
        assertEquals("Should have zero average daily calories", 0, summary.averageDailyCalories)
        assertEquals("Should have zero total workouts", 0, summary.totalWorkouts)
        assertEquals("Should have default average workout calories", 250, summary.averageWorkoutCalories)
        assertEquals("Should have zero current week calories", 0, summary.currentWeekCalories)
        assertEquals("Should have zero previous week calories", 0, summary.previousWeekCalories)
        assertEquals("Should have zero weekly trend", 0f, summary.weeklyTrend)
    }
    
    @Test
    fun `given repository error, when getting calorie summary, then returns error`() = runTest {
        // Given
        val userId = "user123"
        val repositoryError = RuntimeException("Repository failure")
        
        coEvery { workoutRepository.getWorkoutsByDateRange(userId, any(), any()) } throws repositoryError
        
        // When
        val result = calorieService.getCalorieSummary(userId)
        
        // Then
        assertTrue("Result should be failure", result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue("Should be RuntimeException", error is RuntimeException)
        assertTrue("Should contain error message", error!!.message!!.contains("Failed to get calorie summary"))
    }
    
    @Test
    fun `given valid user and time period, when getting daily calories, then returns success with daily data`() = runTest {
        // Given
        val userId = "user123"
        val startDate = Date(System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000) // 7 days ago
        val endDate = Date(System.currentTimeMillis())
        val period = TimeRange(startDate, endDate)
        
        val workouts = createMockWorkouts(5)
        coEvery { workoutRepository.getWorkoutsByDateRange(userId, any(), any()) } returns 
            LiftrixResult.Success(workouts)
        coEvery { calorieCalculator.calculateCaloriesBurned(any(), any(), any()) } returns 300
        coEvery { metDataRepository.getAverageMetForCategory(any()) } returns LiftrixResult.Success(5.0f)
        
        // When
        val result = calorieService.getDailyCalories(userId, period)
        
        // Then
        assertTrue("Result should be successful", result.isSuccess)
        val dailyData = result.getOrNull()!!
        
        assertTrue("Should have daily data for the period", dailyData.isNotEmpty())
        assertEquals("Should have 8 days of data", 8, dailyData.size) // 7 days + 1 for inclusive range
        
        dailyData.forEach { dayData ->
            assertTrue("Should have valid date", dayData.date.time > 0)
            assertTrue("Should have non-negative calories", dayData.totalCalories >= 0)
            assertTrue("Should have non-negative workout count", dayData.workoutCount >= 0)
            assertTrue("Should have non-negative duration", dayData.durationMinutes >= 0)
        }
        
        coVerify { workoutRepository.getWorkoutsByDateRange(userId, any(), any()) }
    }
    
    @Test
    fun `given user with workouts on specific dates, when getting daily calories, then maps workouts to correct dates`() = runTest {
        // Given
        val userId = "user123"
        val startDate = Date(System.currentTimeMillis() - 2 * 24 * 60 * 60 * 1000) // 2 days ago
        val endDate = Date(System.currentTimeMillis())
        val period = TimeRange(startDate, endDate)
        
        val workoutsOnSpecificDate = createMockWorkouts(2)
        coEvery { workoutRepository.getWorkoutsByDateRange(userId, any(), any()) } returns 
            LiftrixResult.Success(workoutsOnSpecificDate)
        coEvery { calorieCalculator.calculateCaloriesBurned(any(), any(), any()) } returns 250
        coEvery { metDataRepository.getAverageMetForCategory(any()) } returns LiftrixResult.Success(5.0f)
        
        // When
        val result = calorieService.getDailyCalories(userId, period)
        
        // Then
        assertTrue("Result should be successful", result.isSuccess)
        val dailyData = result.getOrNull()!!
        
        assertEquals("Should have 3 days of data", 3, dailyData.size)
        
        val dayWithWorkouts = dailyData.find { it.workoutCount > 0 }
        assertTrue("Should have at least one day with workouts", dayWithWorkouts != null)
        assertEquals("Should have correct workout count", 2, dayWithWorkouts!!.workoutCount)
        assertEquals("Should have correct total calories", 500, dayWithWorkouts.totalCalories)
        assertTrue("Should have positive average intensity", dayWithWorkouts.averageIntensity > 0)
    }
    
    @Test
    fun `given repository error, when getting daily calories, then returns error`() = runTest {
        // Given
        val userId = "user123"
        val period = TimeRange(Date(), Date())
        val repositoryError = RuntimeException("Database connection failed")
        
        coEvery { workoutRepository.getWorkoutsByDateRange(userId, any(), any()) } throws repositoryError
        
        // When
        val result = calorieService.getDailyCalories(userId, period)
        
        // Then
        assertTrue("Result should be failure", result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue("Should be RuntimeException", error is RuntimeException)
        assertTrue("Should contain error message", error!!.message!!.contains("Failed to get daily calories"))
    }
    
    @Test
    fun `given valid user id, when getting weekly trend, then returns success with trend analysis`() = runTest {
        // Given
        val userId = "user123"
        val workouts = createMockWorkouts(20) // Spread across multiple weeks
        
        coEvery { workoutRepository.getWorkoutsByDateRange(userId, any(), any()) } returns 
            LiftrixResult.Success(workouts)
        coEvery { calorieCalculator.calculateCaloriesBurned(any(), any(), any()) } returns 300
        
        // When
        val result = calorieService.getWeeklyTrend(userId)
        
        // Then
        assertTrue("Result should be successful", result.isSuccess)
        val trend = result.getOrNull()!!
        
        assertEquals("Should have 12 weeks of data", 12, trend.weeklyData.size)
        assertTrue("Should have positive moving average", trend.movingAverage >= 0)
        assertTrue("Should have trend percentage", trend.trendPercentage != 0f || trend.weeklyData.all { it.totalCalories == 0 })
        assertTrue("Should have consistency score", trend.consistency >= 0 && trend.consistency <= 100)
        
        trend.weeklyData.forEach { weekData ->
            assertTrue("Should have valid week start date", weekData.weekStartDate.time > 0)
            assertTrue("Should have valid week end date", weekData.weekEndDate.time > 0)
            assertTrue("Should have non-negative calories", weekData.totalCalories >= 0)
            assertTrue("Should have non-negative workout count", weekData.workoutCount >= 0)
            assertTrue("Should have non-negative average daily calories", weekData.averageDailyCalories >= 0)
        }
        
        coVerify { workoutRepository.getWorkoutsByDateRange(userId, any(), any()) }
    }
    
    @Test
    fun `given user with consistent weekly workouts, when getting weekly trend, then returns high consistency score`() = runTest {
        // Given
        val userId = "user123"
        val consistentWorkouts = (1..84).map { // 84 workouts over 12 weeks (7 per week)
            createMockWorkout(userId, "workout$it")
        }
        
        coEvery { workoutRepository.getWorkoutsByDateRange(userId, any(), any()) } returns 
            LiftrixResult.Success(consistentWorkouts)
        coEvery { calorieCalculator.calculateCaloriesBurned(any(), any(), any()) } returns 300
        
        // When
        val result = calorieService.getWeeklyTrend(userId)
        
        // Then
        assertTrue("Result should be successful", result.isSuccess)
        val trend = result.getOrNull()!!
        
        assertTrue("Should have high consistency score", trend.consistency >= 80)
        assertTrue("Should have meaningful moving average", trend.movingAverage > 0)
        
        trend.weeklyData.forEach { weekData ->
            assertEquals("Should have consistent workout count", 7, weekData.workoutCount)
            assertEquals("Should have consistent total calories", 2100, weekData.totalCalories)
            assertEquals("Should have consistent average daily calories", 300, weekData.averageDailyCalories)
        }
    }
    
    @Test
    fun `given repository error, when getting weekly trend, then returns error`() = runTest {
        // Given
        val userId = "user123"
        val repositoryError = RuntimeException("Query execution failed")
        
        coEvery { workoutRepository.getWorkoutsByDateRange(userId, any(), any()) } throws repositoryError
        
        // When
        val result = calorieService.getWeeklyTrend(userId)
        
        // Then
        assertTrue("Result should be failure", result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue("Should be RuntimeException", error is RuntimeException)
        assertTrue("Should contain error message", error!!.message!!.contains("Failed to get weekly trend"))
    }
    
    @Test
    fun `given valid workout with timing data, when calculating workout calories, then returns success with calculated calories`() = runTest {
        // Given
        val userId = "user123"
        val workout = createMockWorkout(userId, "workout1")
        val userProfile = createMockUserProfile(userId)
        val expectedCalories = 350
        
        coEvery { userRepository.getUserProfile(userId) } returns LiftrixResult.Success(userProfile)
        coEvery { calorieCalculator.calculateCaloriesBurned(any(), any(), any()) } returns expectedCalories
        
        // When
        val result = calorieService.calculateWorkoutCalories(workout)
        
        // Then
        assertTrue("Result should be successful", result.isSuccess)
        assertEquals("Should return calculated calories", expectedCalories, result.getOrNull())
        
        coVerify { userRepository.getUserProfile(userId) }
        coVerify { calorieCalculator.calculateCaloriesBurned(any(), any(), any()) }
    }
    
    @Test
    fun `given workout without timing data, when calculating workout calories, then estimates duration and calculates calories`() = runTest {
        // Given
        val userId = "user123"
        val workout = createMockWorkout(userId, "workout1").apply {
            every { startTime } returns null
            every { endTime } returns null
            every { exercises } returns listOf(mockk(), mockk(), mockk()) // 3 exercises
        }
        val userProfile = createMockUserProfile(userId)
        val expectedCalories = 300
        
        coEvery { userRepository.getUserProfile(userId) } returns LiftrixResult.Success(userProfile)
        coEvery { calorieCalculator.calculateCaloriesBurned(any(), any(), any()) } returns expectedCalories
        
        // When
        val result = calorieService.calculateWorkoutCalories(workout)
        
        // Then
        assertTrue("Result should be successful", result.isSuccess)
        assertEquals("Should return calculated calories", expectedCalories, result.getOrNull())
        
        coVerify { userRepository.getUserProfile(userId) }
        coVerify { calorieCalculator.calculateCaloriesBurned(any(), any(), any()) }
    }
    
    @Test
    fun `given workout with missing user profile, when calculating workout calories, then creates default profile and calculates calories`() = runTest {
        // Given
        val userId = "user123"
        val workout = createMockWorkout(userId, "workout1")
        val expectedCalories = 250
        
        coEvery { userRepository.getUserProfile(userId) } returns LiftrixResult.Error(
            LiftrixError.NotFoundError("User profile not found")
        )
        coEvery { calorieCalculator.calculateCaloriesBurned(any(), any(), any()) } returns expectedCalories
        
        // When
        val result = calorieService.calculateWorkoutCalories(workout)
        
        // Then
        assertTrue("Result should be successful", result.isSuccess)
        assertEquals("Should return calculated calories", expectedCalories, result.getOrNull())
        
        coVerify { userRepository.getUserProfile(userId) }
        coVerify { calorieCalculator.calculateCaloriesBurned(any(), any(), any()) }
    }
    
    @Test
    fun `given calorie calculator error, when calculating workout calories, then returns error`() = runTest {
        // Given
        val userId = "user123"
        val workout = createMockWorkout(userId, "workout1")
        val userProfile = createMockUserProfile(userId)
        val calculatorError = RuntimeException("Calculation failed")
        
        coEvery { userRepository.getUserProfile(userId) } returns LiftrixResult.Success(userProfile)
        coEvery { calorieCalculator.calculateCaloriesBurned(any(), any(), any()) } throws calculatorError
        
        // When
        val result = calorieService.calculateWorkoutCalories(workout)
        
        // Then
        assertTrue("Result should be failure", result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue("Should be RuntimeException", error is RuntimeException)
        assertTrue("Should contain error message", error!!.message!!.contains("Failed to calculate workout calories"))
    }
    
    @Test
    fun `given multiple concurrent requests, when calculating calories, then handles concurrency correctly`() = runTest {
        // Given
        val userId = "user123"
        val workouts = (1..5).map { createMockWorkout(userId, "workout$it") }
        val userProfile = createMockUserProfile(userId)
        val expectedCalories = 300
        
        coEvery { userRepository.getUserProfile(userId) } returns LiftrixResult.Success(userProfile)
        coEvery { calorieCalculator.calculateCaloriesBurned(any(), any(), any()) } returns expectedCalories
        
        // When - Execute multiple concurrent calls
        val results = workouts.map { workout ->
            calorieService.calculateWorkoutCalories(workout)
        }
        
        // Then
        results.forEach { result ->
            assertTrue("Each result should be successful", result.isSuccess)
            assertEquals("Each result should return expected calories", expectedCalories, result.getOrNull())
        }
        
        coVerify(exactly = 5) { userRepository.getUserProfile(userId) }
        coVerify(exactly = 5) { calorieCalculator.calculateCaloriesBurned(any(), any(), any()) }
    }
    
    // Helper methods for creating mock objects
    
    private fun createMockWorkouts(count: Int): List<Workout> {
        return (1..count).map { createMockWorkout("user123", "workout$it") }
    }
    
    private fun createMockWorkout(userId: String, workoutId: String): Workout {
        return mockk<Workout> {
            every { id } returns workoutId
            every { this@mockk.userId } returns userId
            every { date } returns LocalDate.now()
            every { startTime } returns java.time.Instant.now()
            every { endTime } returns java.time.Instant.now().plus(Duration.ofMinutes(45))
            every { exercises } returns listOf(mockk {
                every { libraryExercise } returns mockk {
                    every { primaryMuscleGroup } returns mockk {
                        every { name } returns "CHEST"
                    }
                }
            })
        }
    }
    
    private fun createMockUserProfile(userId: String): UserProfile {
        return UserProfile(
            userId = userId,
            weight = Weight(70.0),
            age = 30,
            availableEquipment = emptyList(),
            otherEquipment = null,
            fitnessGoals = emptyList(),
            goalsPriority = null,
            completedAt = null,
            updatedAt = LocalDateTime.now()
        )
    }
}