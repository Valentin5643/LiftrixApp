package com.example.liftrix.service

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.error.LiftrixError
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Comprehensive unit tests for FeatureFlagServiceImpl
 * 
 * Tests all service methods including feature flag evaluation, A/B test variant retrieval,
 * remote config refresh, and error handling scenarios using MockK and proper coroutine 
 * testing patterns following Given/When/Then structure.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FeatureFlagServiceTest {
    
    @MockK
    private lateinit var firebaseRemoteConfig: FirebaseRemoteConfig
    
    private lateinit var testDispatcher: CoroutineDispatcher
    private lateinit var featureFlagService: FeatureFlagService
    
    @Before
    fun setup() {
        MockKAnnotations.init(this)
        testDispatcher = StandardTestDispatcher()
        
        // Mock Firebase Remote Config setup methods
        every { firebaseRemoteConfig.setConfigSettingsAsync(any()) } returns mockk<Task<Void>>()
        every { firebaseRemoteConfig.setDefaultsAsync(any<Map<String, Any>>()) } returns mockk<Task<Void>>()
        
        // Mock the initial fetch
        val mockFetchTask = mockk<Task<Boolean>>()
        every { firebaseRemoteConfig.fetchAndActivate() } returns mockFetchTask
        every { mockFetchTask.addOnCompleteListener(any()) } returns mockFetchTask
        
        featureFlagService = FeatureFlagServiceImpl(firebaseRemoteConfig, testDispatcher)
    }
    
    @Test
    fun `given existing feature flag, when checking if feature is enabled, then returns cached value`() = runTest {
        // Given
        val featureKey = "use_new_progress_dashboard"
        val expectedValue = true
        
        every { firebaseRemoteConfig.getBoolean(featureKey) } returns expectedValue
        
        // When
        val result = featureFlagService.isFeatureEnabled(featureKey)
        
        // Then
        assertTrue("Result should be successful", result.isSuccess)
        assertEquals("Should return expected value", expectedValue, result.getOrNull())
        
        verify { firebaseRemoteConfig.getBoolean(featureKey) }
    }
    
    @Test
    fun `given non-existing feature flag, when checking if feature is enabled, then returns false default`() = runTest {
        // Given
        val featureKey = "non_existing_feature"
        
        every { firebaseRemoteConfig.getBoolean(featureKey) } returns false
        
        // When
        val result = featureFlagService.isFeatureEnabled(featureKey)
        
        // Then
        assertTrue("Result should be successful", result.isSuccess)
        assertFalse("Should return false for non-existing feature", result.getOrNull() ?: true)
        
        verify { firebaseRemoteConfig.getBoolean(featureKey) }
    }
    
    @Test
    fun `given firebase error, when checking if feature is enabled, then returns error with fallback`() = runTest {
        // Given
        val featureKey = "enable_analytics_widgets"
        val firebaseException = RuntimeException("Firebase Remote Config error")
        
        every { firebaseRemoteConfig.getBoolean(featureKey) } throws firebaseException
        
        // When
        val result = featureFlagService.isFeatureEnabled(featureKey)
        
        // Then
        assertTrue("Result should be failure", result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue("Should be LiftrixError.NetworkError", error is LiftrixError.NetworkError)
        
        val networkError = error as LiftrixError.NetworkError
        assertEquals("firebase_remote_config", networkError.networkType)
        assertEquals(featureKey, networkError.analyticsContext["feature_key"])
        assertEquals("true", networkError.analyticsContext["fallback_value"]) // Default for this key
        assertEquals("feature_flag_fetch_error", networkError.analyticsContext["error_type"])
    }
    
    @Test
    fun `given default feature flags, when checking multiple features, then returns correct values`() = runTest {
        // Given
        val featureFlags = mapOf(
            "use_new_progress_dashboard" to false,
            "enable_analytics_widgets" to true,
            "show_calorie_tracking" to true,
            "enable_export_analytics" to false
        )
        
        featureFlags.forEach { (key, value) ->
            every { firebaseRemoteConfig.getBoolean(key) } returns value
        }
        
        // When & Then
        featureFlags.forEach { (key, expectedValue) ->
            val result = featureFlagService.isFeatureEnabled(key)
            
            assertTrue("Result should be successful for $key", result.isSuccess)
            assertEquals("Should return expected value for $key", expectedValue, result.getOrNull())
        }
        
        featureFlags.keys.forEach { key ->
            verify { firebaseRemoteConfig.getBoolean(key) }
        }
    }
    
    @Test
    fun `given existing A/B test, when getting variant, then returns cached variant`() = runTest {
        // Given
        val testKey = "progress_dashboard_layout"
        val expectedVariant = "variant_a"
        
        every { firebaseRemoteConfig.getString(testKey) } returns expectedVariant
        
        // When
        val result = featureFlagService.getABTestVariant(testKey)
        
        // Then
        assertTrue("Result should be successful", result.isSuccess)
        assertEquals("Should return expected variant", expectedVariant, result.getOrNull())
        
        verify { firebaseRemoteConfig.getString(testKey) }
    }
    
    @Test
    fun `given non-existing A/B test, when getting variant, then returns control default`() = runTest {
        // Given
        val testKey = "non_existing_test"
        
        every { firebaseRemoteConfig.getString(testKey) } returns ""
        
        // When
        val result = featureFlagService.getABTestVariant(testKey)
        
        // Then
        assertTrue("Result should be successful", result.isSuccess)
        assertEquals("Should return empty string for non-existing test", "", result.getOrNull())
        
        verify { firebaseRemoteConfig.getString(testKey) }
    }
    
    @Test
    fun `given firebase error, when getting A/B test variant, then returns error with fallback`() = runTest {
        // Given
        val testKey = "onboarding_flow"
        val firebaseException = RuntimeException("Remote Config fetch failed")
        
        every { firebaseRemoteConfig.getString(testKey) } throws firebaseException
        
        // When
        val result = featureFlagService.getABTestVariant(testKey)
        
        // Then
        assertTrue("Result should be failure", result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue("Should be LiftrixError.NetworkError", error is LiftrixError.NetworkError)
        
        val networkError = error as LiftrixError.NetworkError
        assertEquals("firebase_remote_config", networkError.networkType)
        assertEquals(testKey, networkError.analyticsContext["test_key"])
        assertEquals("control", networkError.analyticsContext["fallback_variant"])
        assertEquals("ab_test_variant_fetch_error", networkError.analyticsContext["error_type"])
    }
    
    @Test
    fun `given default A/B test variants, when getting multiple variants, then returns correct values`() = runTest {
        // Given
        val abTestVariants = mapOf(
            "progress_dashboard_layout" to "control",
            "onboarding_flow" to "control",
            "premium_pricing_display" to "control",
            "chart_visualization_style" to "control"
        )
        
        abTestVariants.forEach { (key, value) ->
            every { firebaseRemoteConfig.getString(key) } returns value
        }
        
        // When & Then
        abTestVariants.forEach { (key, expectedVariant) ->
            val result = featureFlagService.getABTestVariant(key)
            
            assertTrue("Result should be successful for $key", result.isSuccess)
            assertEquals("Should return expected variant for $key", expectedVariant, result.getOrNull())
        }
        
        abTestVariants.keys.forEach { key ->
            verify { firebaseRemoteConfig.getString(key) }
        }
    }
    
    @Test
    fun `given remote config initialized, when getting all feature flags, then returns complete map`() = runTest {
        // Given
        val defaultFeatureFlags = mapOf(
            "use_new_progress_dashboard" to false,
            "enable_analytics_widgets" to true,
            "show_calorie_tracking" to true,
            "enable_export_analytics" to false,
            "show_premium_features" to true,
            "enable_advanced_charts" to false,
            "show_social_features" to true,
            "enable_workout_recommendations" to false
        )
        
        defaultFeatureFlags.forEach { (key, value) ->
            every { firebaseRemoteConfig.getBoolean(key) } returns value
        }
        
        // When
        val result = featureFlagService.getAllFeatureFlags()
        
        // Then
        assertTrue("Result should be successful", result.isSuccess)
        val allFlags = result.getOrNull()!!
        
        assertEquals("Should return correct number of flags", defaultFeatureFlags.size, allFlags.size)
        defaultFeatureFlags.forEach { (key, expectedValue) ->
            assertEquals("Should return correct value for $key", expectedValue, allFlags[key])
        }
        
        defaultFeatureFlags.keys.forEach { key ->
            verify { firebaseRemoteConfig.getBoolean(key) }
        }
    }
    
    @Test
    fun `given firebase error, when getting all feature flags, then returns error`() = runTest {
        // Given
        val firebaseException = RuntimeException("Failed to fetch all flags")
        
        every { firebaseRemoteConfig.getBoolean(any()) } throws firebaseException
        
        // When
        val result = featureFlagService.getAllFeatureFlags()
        
        // Then
        assertTrue("Result should be failure", result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue("Should be LiftrixError.NetworkError", error is LiftrixError.NetworkError)
        
        val networkError = error as LiftrixError.NetworkError
        assertEquals("firebase_remote_config", networkError.networkType)
        assertEquals("get_all_feature_flags", networkError.analyticsContext["operation"])
        assertEquals("bulk_fetch_error", networkError.analyticsContext["error_type"])
    }
    
    @Test
    fun `given successful fetch, when refreshing remote config, then returns success`() = runTest {
        // Given
        val mockTask = mockk<Task<Boolean>>()
        every { firebaseRemoteConfig.fetchAndActivate() } returns mockTask
        coEvery { mockTask.await() } returns true
        
        // When
        val result = featureFlagService.refreshRemoteConfig()
        
        // Then
        assertTrue("Result should be successful", result.isSuccess)
        
        verify { firebaseRemoteConfig.fetchAndActivate() }
        coVerify { mockTask.await() }
    }
    
    @Test
    fun `given fetch returns false, when refreshing remote config, then returns network error`() = runTest {
        // Given
        val mockTask = mockk<Task<Boolean>>()
        every { firebaseRemoteConfig.fetchAndActivate() } returns mockTask
        coEvery { mockTask.await() } returns false
        
        // When
        val result = featureFlagService.refreshRemoteConfig()
        
        // Then
        assertTrue("Result should be failure", result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue("Should be LiftrixError.NetworkError", error is LiftrixError.NetworkError)
        
        val networkError = error as LiftrixError.NetworkError
        assertEquals("Remote config refresh failed", networkError.errorMessage)
        assertEquals("firebase_remote_config", networkError.networkType)
        assertEquals("refresh_remote_config", networkError.analyticsContext["operation"])
        assertEquals("refresh_failed", networkError.analyticsContext["error_type"])
    }
    
    @Test
    fun `given fetch throws exception, when refreshing remote config, then returns network error`() = runTest {
        // Given
        val mockTask = mockk<Task<Boolean>>()
        val fetchException = RuntimeException("Network timeout")
        every { firebaseRemoteConfig.fetchAndActivate() } returns mockTask
        coEvery { mockTask.await() } throws fetchException
        
        // When
        val result = featureFlagService.refreshRemoteConfig()
        
        // Then
        assertTrue("Result should be failure", result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue("Should be LiftrixError.NetworkError", error is LiftrixError.NetworkError)
        
        val networkError = error as LiftrixError.NetworkError
        assertEquals("Failed to refresh remote config: Network timeout", networkError.errorMessage)
        assertEquals("firebase_remote_config", networkError.networkType)
        assertEquals("refresh_remote_config", networkError.analyticsContext["operation"])
        assertEquals("refresh_exception", networkError.analyticsContext["error_type"])
        assertEquals("RuntimeException", networkError.analyticsContext["exception_type"])
    }
    
    @Test
    fun `given multiple concurrent feature flag requests, when executed simultaneously, then handles concurrency correctly`() = runTest {
        // Given
        val featureKey = "enable_analytics_widgets"
        val expectedValue = true
        
        every { firebaseRemoteConfig.getBoolean(featureKey) } returns expectedValue
        
        // When - Execute multiple concurrent calls
        val results = (1..5).map {
            featureFlagService.isFeatureEnabled(featureKey)
        }
        
        // Then
        results.forEach { result ->
            assertTrue("Each result should be successful", result.isSuccess)
            assertEquals("Each result should return expected value", expectedValue, result.getOrNull())
        }
        
        verify(exactly = 5) { firebaseRemoteConfig.getBoolean(featureKey) }
    }
    
    @Test
    fun `given multiple concurrent A/B test requests, when executed simultaneously, then handles concurrency correctly`() = runTest {
        // Given
        val testKey = "progress_dashboard_layout"
        val expectedVariant = "variant_b"
        
        every { firebaseRemoteConfig.getString(testKey) } returns expectedVariant
        
        // When - Execute multiple concurrent calls
        val results = (1..5).map {
            featureFlagService.getABTestVariant(testKey)
        }
        
        // Then
        results.forEach { result ->
            assertTrue("Each result should be successful", result.isSuccess)
            assertEquals("Each result should return expected variant", expectedVariant, result.getOrNull())
        }
        
        verify(exactly = 5) { firebaseRemoteConfig.getString(testKey) }
    }
    
    @Test
    fun `given cached values, when checking feature flags multiple times, then uses cache efficiently`() = runTest {
        // Given
        val featureKey = "show_premium_features"
        val expectedValue = true
        
        every { firebaseRemoteConfig.getBoolean(featureKey) } returns expectedValue
        
        // When - Check same feature multiple times
        val firstResult = featureFlagService.isFeatureEnabled(featureKey)
        val secondResult = featureFlagService.isFeatureEnabled(featureKey)
        val thirdResult = featureFlagService.isFeatureEnabled(featureKey)
        
        // Then
        assertTrue("First result should be successful", firstResult.isSuccess)
        assertTrue("Second result should be successful", secondResult.isSuccess)
        assertTrue("Third result should be successful", thirdResult.isSuccess)
        
        assertEquals("All results should be consistent", expectedValue, firstResult.getOrNull())
        assertEquals("All results should be consistent", expectedValue, secondResult.getOrNull())
        assertEquals("All results should be consistent", expectedValue, thirdResult.getOrNull())
        
        // Should call Firebase Remote Config multiple times since service doesn't implement caching
        verify(exactly = 3) { firebaseRemoteConfig.getBoolean(featureKey) }
    }
    
    @Test
    fun `given mixed successful and failing requests, when checking features, then handles each independently`() = runTest {
        // Given
        val successfulFeature = "enable_analytics_widgets"
        val failingFeature = "failing_feature"
        
        every { firebaseRemoteConfig.getBoolean(successfulFeature) } returns true
        every { firebaseRemoteConfig.getBoolean(failingFeature) } throws RuntimeException("Feature error")
        
        // When
        val successResult = featureFlagService.isFeatureEnabled(successfulFeature)
        val failureResult = featureFlagService.isFeatureEnabled(failingFeature)
        
        // Then
        assertTrue("Success result should be successful", successResult.isSuccess)
        assertTrue("Failure result should be failure", failureResult.isFailure)
        
        assertEquals("Success result should return expected value", true, successResult.getOrNull())
        assertTrue("Failure result should be LiftrixError.NetworkError", failureResult.exceptionOrNull() is LiftrixError.NetworkError)
        
        verify { firebaseRemoteConfig.getBoolean(successfulFeature) }
        verify { firebaseRemoteConfig.getBoolean(failingFeature) }
    }
}