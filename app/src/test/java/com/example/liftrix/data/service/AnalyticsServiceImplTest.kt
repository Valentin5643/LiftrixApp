package com.example.liftrix.data.service

import com.example.liftrix.TestDataFactory
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

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
        val user = TestDataFactory.testUser
        
        val result = analyticsService.setUserProperties(user)
        
        assertTrue("Should succeed", result.isSuccess)
        
        verify { firebaseAnalytics.setUserId(user.uid) }
        verify { firebaseAnalytics.setUserProperty("subscription_tier", user.subscriptionTier.name.lowercase()) }
        verify { firebaseAnalytics.setUserProperty("is_premium", user.isPremium.toString()) }
        verify { firebaseAnalytics.setUserProperty("is_anonymous", user.isAnonymous.toString()) }
        verify { firebaseAnalytics.setUserProperty("onboarding_completed", user.onboardingCompleted.toString()) }
        
        verify { firebaseCrashlytics.setUserId(user.uid) }
        verify { firebaseCrashlytics.setCustomKey("user_id", user.uid) }
        verify { firebaseCrashlytics.setCustomKey("subscription_tier", user.subscriptionTier.name) }
        verify { firebaseCrashlytics.setCustomKey("is_premium", user.isPremium.toString()) }
    }

    @Test
    fun `logWorkoutStart should log analytics event and set crashlytics context`() = runTest {
        val userId = "test-user"
        val workoutId = "workout-123"
        val workoutName = "Test Workout"
        
        val result = analyticsService.logWorkoutStart(userId, workoutId, workoutName)
        
        assertTrue("Should succeed", result.isSuccess)
        
        verify { firebaseAnalytics.logEvent(eq("workout_started"), any()) }
        verify { firebaseCrashlytics.setCustomKey("current_workout_id", workoutId) }
        verify { firebaseCrashlytics.setCustomKey("workout_phase", "started") }
    }

    @Test
    fun `logWorkoutComplete should log analytics event with metrics`() = runTest {
        val userId = "test-user"
        val workoutId = "workout-123"
        val workoutName = "Test Workout"
        val metrics = TestDataFactory.sampleWorkout.getMetrics()
        val durationMinutes = 45L
        
        val result = analyticsService.logWorkoutComplete(
            userId, workoutId, workoutName, metrics, durationMinutes
        )
        
        assertTrue("Should succeed", result.isSuccess)
        
        verify { firebaseAnalytics.logEvent(eq("workout_completed"), any()) }
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
        val user = TestDataFactory.testUser
        every { firebaseAnalytics.setUserId(any()) } throws RuntimeException("Analytics error")
        
        val result = analyticsService.setUserProperties(user)
        
        assertTrue("Should fail gracefully", result.isFailure)
        assertTrue("Should contain exception", result.exceptionOrNull() is RuntimeException)
    }
} 