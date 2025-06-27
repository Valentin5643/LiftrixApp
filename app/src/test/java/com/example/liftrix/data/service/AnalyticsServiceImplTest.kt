package com.example.liftrix.data.service

import com.example.liftrix.domain.model.User
import com.example.liftrix.domain.model.WorkoutMetrics
import com.example.liftrix.domain.model.Weight
import com.example.liftrix.domain.model.Reps
import com.example.liftrix.domain.model.ExerciseCategory
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Duration

@OptIn(ExperimentalCoroutinesApi::class)
class AnalyticsServiceImplTest {

    private lateinit var firebaseAnalytics: FirebaseAnalytics
    private lateinit var firebaseCrashlytics: FirebaseCrashlytics
    private lateinit var analyticsService: AnalyticsServiceImpl

    @Before
    fun setup() {
        firebaseAnalytics = mockk(relaxed = true)
        firebaseCrashlytics = mockk(relaxed = true)
        
        analyticsService = AnalyticsServiceImpl(
            firebaseAnalytics = firebaseAnalytics,
            firebaseCrashlytics = firebaseCrashlytics
        )
    }

    @Test
    fun `setUserProperties should set Firebase Analytics and Crashlytics properties`() = runTest {
        val user = mockk<User> {
            every { uid } returns "test-user-id"
            every { subscriptionTier } returns mockk {
                every { name } returns "PREMIUM"
            }
            every { isPremium } returns true
            every { isAnonymous } returns false
            every { onboardingCompleted } returns true
        }
        
        val result = analyticsService.setUserProperties(user)
        
        assertTrue("Should succeed", result.isSuccess)
        
        verify { firebaseAnalytics.setUserId("test-user-id") }
        verify { firebaseAnalytics.setUserProperty("subscription_tier", "premium") }
        verify { firebaseAnalytics.setUserProperty("is_premium", "true") }
        verify { firebaseAnalytics.setUserProperty("is_anonymous", "false") }
        verify { firebaseAnalytics.setUserProperty("onboarding_completed", "true") }
        
        verify { firebaseCrashlytics.setUserId("test-user-id") }
        verify { firebaseCrashlytics.setCustomKey("user_id", "test-user-id") }
        verify { firebaseCrashlytics.setCustomKey("subscription_tier", "PREMIUM") }
        verify { firebaseCrashlytics.setCustomKey("is_premium", "true") }
    }

    @Test
    fun `logWorkoutStart should log analytics event and set crashlytics context`() = runTest {
        val userId = "test-user-id"
        val workoutId = "test-workout-id"
        val workoutName = "Test Workout"
        
        val result = analyticsService.logWorkoutStart(userId, workoutId, workoutName)
        
        assertTrue("Should succeed", result.isSuccess)
        
        verify { firebaseAnalytics.logEvent("workout_started", any()) }
        verify { firebaseCrashlytics.setCustomKey("current_workout_id", workoutId) }
        verify { firebaseCrashlytics.setCustomKey("workout_phase", "started") }
    }

    @Test
    fun `logWorkoutComplete should log analytics event with metrics`() = runTest {
        val userId = "test-user-id"
        val workoutId = "test-workout-id"
        val workoutName = "Test Workout"
        val metrics = WorkoutMetrics(
            totalVolume = Weight.kilograms(500.0),
            totalSets = 12,
            completedSets = 10,
            totalReps = Reps(120),
            duration = Duration.ofMinutes(45),
            completionPercentage = 83.3,
            exerciseCount = 5,
            categories = setOf(ExerciseCategory.CHEST, ExerciseCategory.SHOULDERS)
        )
        val durationMinutes = 45L
        
        val result = analyticsService.logWorkoutComplete(
            userId, workoutId, workoutName, metrics, durationMinutes
        )
        
        assertTrue("Should succeed", result.isSuccess)
        
        verify { firebaseAnalytics.logEvent("workout_completed", any()) }
        verify { firebaseCrashlytics.setCustomKey("current_workout_id", "") }
        verify { firebaseCrashlytics.setCustomKey("workout_phase", "completed") }
    }

    @Test
    fun `recordException should set additional data and record exception`() = runTest {
        val exception = RuntimeException("Test exception")
        val additionalData = mapOf("context" to "workout_save", "user_action" to "complete_set")
        
        val result = analyticsService.recordException(exception, additionalData)
        
        assertTrue("Should succeed", result.isSuccess)
        
        verify { firebaseCrashlytics.setCustomKey("context", "workout_save") }
        verify { firebaseCrashlytics.setCustomKey("user_action", "complete_set") }
        verify { firebaseCrashlytics.recordException(exception) }
    }

    @Test
    fun `clearUserProperties should clear all user context`() = runTest {
        val result = analyticsService.clearUserProperties()
        
        assertTrue("Should succeed", result.isSuccess)
        
        verify { firebaseAnalytics.setUserId(null) }
        verify { firebaseAnalytics.setUserProperty("subscription_tier", null) }
        verify { firebaseAnalytics.setUserProperty("is_premium", null) }
        verify { firebaseAnalytics.setUserProperty("is_anonymous", null) }
        verify { firebaseAnalytics.setUserProperty("onboarding_completed", null) }
        
        verify { firebaseCrashlytics.setUserId("") }
        verify { firebaseCrashlytics.setCustomKey("user_id", "") }
        verify { firebaseCrashlytics.setCustomKey("subscription_tier", "") }
        verify { firebaseCrashlytics.setCustomKey("current_workout_id", "") }
        verify { firebaseCrashlytics.setCustomKey("workout_phase", "") }
    }

    @Test
    fun `setUserProperties should handle exceptions gracefully`() = runTest {
        val user = mockk<User> {
            every { uid } returns "test-user-id"
            every { subscriptionTier } returns mockk {
                every { name } returns "PREMIUM"
            }
            every { isPremium } returns true
            every { isAnonymous } returns false
            every { onboardingCompleted } returns true
        }
        every { firebaseAnalytics.setUserId(any()) } throws RuntimeException("Analytics error")
        
        val result = analyticsService.setUserProperties(user)
        
        assertTrue("Should fail gracefully", result.isFailure)
        assertTrue("Should contain exception", result.exceptionOrNull() is RuntimeException)
    }

    @Test
    fun `logWorkoutCreationEvent logs event with correct parameters`() = runTest {
        // Arrange
        val userId = "test-user-id"
        val workoutId = "test-workout-id"
        val workoutName = "Test Workout"
        val workoutType = "unified"
        val exerciseCount = 5

        // Act
        val result = analyticsService.logWorkoutCreationEvent(
            userId = userId,
            workoutId = workoutId,
            workoutName = workoutName,
            workoutType = workoutType,
            exerciseCount = exerciseCount
        )

        // Assert
        assertTrue(result.isSuccess)
        verify { firebaseAnalytics.logEvent("workout_created", any()) }
    }

    @Test
    fun `logExerciseSelectionEvent logs event with correct parameters`() = runTest {
        // Arrange
        val userId = "test-user-id"
        val exerciseId = "bench-press-123"
        val exerciseName = "Bench Press"
        val selectionMethod = "search"

        // Act
        val result = analyticsService.logExerciseSelectionEvent(
            userId = userId,
            exerciseId = exerciseId,
            exerciseName = exerciseName,
            selectionMethod = selectionMethod
        )

        // Assert
        assertTrue(result.isSuccess)
        verify { firebaseAnalytics.logEvent("exercise_selected", any()) }
    }

    @Test
    fun `logPersonalRecord logs event successfully`() = runTest {
        // Arrange
        val userId = "test-user-id"
        val exerciseName = "Bench Press"
        val recordType = "1RM"
        val newValue = 120.0
        val previousValue = 115.0

        // Act
        val result = analyticsService.logPersonalRecord(
            userId, exerciseName, recordType, newValue, previousValue
        )

        // Assert
        assertTrue(result.isSuccess)
        verify { firebaseAnalytics.logEvent("pr_achieved", any()) }
    }

    @Test
    fun `logAiSummaryViewed logs event successfully`() = runTest {
        // Arrange
        val userId = "test-user-id"
        val workoutId = "test-workout-id"
        val summaryType = "performance_insights"

        // Act
        val result = analyticsService.logAiSummaryViewed(userId, workoutId, summaryType)

        // Assert
        assertTrue(result.isSuccess)
        verify { firebaseAnalytics.logEvent("ai_summary_viewed", any()) }
    }

    @Test
    fun `logSpotterAdded logs event successfully`() = runTest {
        // Arrange
        val userId = "test-user-id"
        val spotterUserId = "spotter-user-id"
        val connectionType = "qr_code"

        // Act
        val result = analyticsService.logSpotterAdded(userId, spotterUserId, connectionType)

        // Assert
        assertTrue(result.isSuccess)
        verify { firebaseAnalytics.logEvent("spotter_added", any()) }
    }

    @Test
    fun `analytics methods handle exceptions gracefully`() = runTest {
        // Arrange
        every { firebaseAnalytics.logEvent(any<String>(), any()) } throws RuntimeException("Analytics error")

        // Act
        val result = analyticsService.logWorkoutCreationEvent(
            "user", "workout", "name", "unified", 1
        )

        // Assert
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is RuntimeException)
    }

    @Test
    fun `crashlytics methods handle exceptions gracefully`() = runTest {
        // Arrange
        every { firebaseCrashlytics.setCustomKey(any<String>(), any<String>()) } throws RuntimeException("Crashlytics error")

        // Act
        val result = analyticsService.logWorkoutStart("user", "workout", "name")

        // Assert
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is RuntimeException)
    }
} 