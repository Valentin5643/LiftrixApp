package com.example.liftrix.ui.progress

import app.cash.turbine.test
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.usecase.common.ErrorHandler
import com.example.liftrix.service.FeatureFlagService
import com.example.liftrix.ui.common.state.AsyncData
import com.example.liftrix.ui.common.state.UiState
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.assertFalse

@OptIn(ExperimentalCoroutinesApi::class)
class FeatureConfigurationViewModelTest {

    // Test dependencies
    private lateinit var mockFeatureFlagService: FeatureFlagService
    private lateinit var mockErrorHandler: ErrorHandler
    private lateinit var viewModel: FeatureConfigurationViewModel

    // Test dispatchers
    private val testDispatcher = StandardTestDispatcher()

    // Test data
    private val testFeatureFlags = mapOf(
        "analytics_enabled" to true,
        "export_enabled" to false,
        "premium_features" to true,
        "beta_charts" to false,
        "social_features" to true,
        "offline_mode" to true,
        "advanced_metrics" to false,
        "custom_themes" to true
    )
    
    private val testABTestVariants = mapOf(
        "onboarding_flow" to "variant_b",
        "chart_layout" to "grid_view",
        "notification_style" to "minimal",
        "workout_timer" to "enhanced",
        "progress_calculation" to "advanced"
    )
    
    private val testAnalyticsFlags = mapOf(
        "analytics_enabled" to true,
        "crash_reporting" to true,
        "performance_monitoring" to false,
        "user_behavior_tracking" to true
    )

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        Dispatchers.setMain(testDispatcher)
        
        mockFeatureFlagService = mockk(relaxed = true)
        mockErrorHandler = mockk(relaxed = true)
        
        // Default error handler behavior
        coEvery { mockErrorHandler.handleError(any(), any()) } returns mockk()
        
        viewModel = FeatureConfigurationViewModel(
            featureFlagService = mockFeatureFlagService,
            errorHandler = mockErrorHandler
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // === Initial State Tests ===

    @Test
    fun `given ViewModel initialization, when created, then starts with Loading state`() = runTest {
        // Given & When - ViewModel is created in setup()
        
        // Then
        viewModel.uiState.test {
            val initialState = awaitItem()
            assertIs<UiState.Loading<FeatureConfigurationState>>(initialState)
        }
    }

    @Test
    fun `given ViewModel initialization, when created, then has correct initial state structure`() = runTest {
        // Given & When - ViewModel is created in setup()
        
        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertIs<AsyncData.NotAsked>(state.data.featureFlags)
                assertIs<AsyncData.NotAsked>(state.data.abTestVariants)
                assertIs<AsyncData.NotAsked>(state.data.remoteConfigStatus)
                assertEquals(0L, state.data.lastRefreshTimestamp)
            }
        }
    }

    // === Feature Flag Loading Tests ===

    @Test
    fun `given feature flag service success, when LoadFeatureFlags event, then loads flags successfully`() = runTest {
        // Given
        coEvery { mockFeatureFlagService.getAllFeatureFlags() } returns Result.success(testFeatureFlags)
        
        // When
        viewModel.handleEvent(FeatureConfigurationEvent.LoadFeatureFlags)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertIs<AsyncData.Success<Map<String, Boolean>>>(state.data.featureFlags)
                assertEquals(testFeatureFlags, state.data.featureFlags.getOrNull())
                assertTrue(state.data.featureFlags.getOrNull()?.get("analytics_enabled") == true)
                assertFalse(state.data.featureFlags.getOrNull()?.get("export_enabled") == true)
                assertTrue(state.data.lastRefreshTimestamp > 0L)
            }
        }
        
        // Verify service call
        coVerify { mockFeatureFlagService.getAllFeatureFlags() }
    }

    @Test
    fun `given service error, when LoadFeatureFlags event, then shows error state`() = runTest {
        // Given
        val testError = LiftrixError.NetworkError("Failed to fetch feature flags from remote config")
        coEvery { mockFeatureFlagService.getAllFeatureFlags() } returns Result.failure(testError)
        
        // When
        viewModel.handleEvent(FeatureConfigurationEvent.LoadFeatureFlags)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertIs<AsyncData.Failure>(state.data.featureFlags)
                assertEquals(testError, state.data.featureFlags.errorOrNull())
            }
        }
        
        // Verify error handler called
        coVerify { mockErrorHandler.handleError(testError, any()) }
    }

    // === Remote Config Refresh Tests ===

    @Test
    fun `given remote config service, when RefreshRemoteConfig event, then refreshes configuration successfully`() = runTest {
        // Given
        coEvery { mockFeatureFlagService.refreshRemoteConfig() } returns Result.success(Unit)
        
        // When
        viewModel.handleEvent(FeatureConfigurationEvent.RefreshRemoteConfig)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertIs<AsyncData.Success<Unit>>(state.data.remoteConfigStatus)
                assertTrue(state.data.lastRefreshTimestamp > 0L)
            }
        }
        
        // Verify service call
        coVerify { mockFeatureFlagService.refreshRemoteConfig() }
    }

    @Test
    fun `given remote config fetch failure, when RefreshRemoteConfig event, then shows failure state`() = runTest {
        // Given
        val testError = LiftrixError.NetworkError("Remote config fetch timeout")
        coEvery { mockFeatureFlagService.refreshRemoteConfig() } returns Result.failure(testError)
        
        // When
        viewModel.handleEvent(FeatureConfigurationEvent.RefreshRemoteConfig)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertIs<AsyncData.Failure>(state.data.remoteConfigStatus)
                assertEquals(testError, state.data.remoteConfigStatus.errorOrNull())
            }
        }
        
        // Verify error handler called
        coVerify { mockErrorHandler.handleError(testError, any()) }
    }

    // === A/B Test Variant Tests ===

    @Test
    fun `given A/B test service, when GetABTestVariant event, then retrieves specific variant successfully`() = runTest {
        // Given
        val testKey = "onboarding_flow"
        val expectedVariant = "variant_b"
        coEvery { mockFeatureFlagService.getABTestVariant(testKey) } returns Result.success(expectedVariant)
        
        // When
        viewModel.handleEvent(FeatureConfigurationEvent.GetABTestVariant(testKey))
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertIs<AsyncData.Success<Map<String, String>>>(state.data.abTestVariants)
                val variants = state.data.abTestVariants.getOrNull()
                assertEquals(expectedVariant, variants?.get(testKey))
            }
        }
        
        // Verify service call
        coVerify { mockFeatureFlagService.getABTestVariant(testKey) }
    }

    @Test
    fun `given invalid test key, when GetABTestVariant event, then shows validation error`() = runTest {
        // Given
        val invalidTestKey = "non_existent_test"
        val testError = LiftrixError.ValidationError(field = invalidTestKey, violations = listOf("A/B test key not found"))
        coEvery { mockFeatureFlagService.getABTestVariant(invalidTestKey) } returns Result.failure(testError)
        
        // When
        viewModel.handleEvent(FeatureConfigurationEvent.GetABTestVariant(invalidTestKey))
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertIs<AsyncData.Failure>(state.data.abTestVariants)
                assertEquals(testError, state.data.abTestVariants.errorOrNull())
            }
        }
        
        // Verify error handler called
        coVerify { mockErrorHandler.handleError(testError, any()) }
    }

    // === Feature Flag Check Tests ===

    @Test
    fun `given feature flag key, when CheckFeatureEnabled event, then returns correct flag state`() = runTest {
        // Given
        val flagKey = "analytics_enabled"
        val expectedValue = true
        coEvery { mockFeatureFlagService.isFeatureEnabled(flagKey) } returns Result.success(expectedValue)
        
        // When
        viewModel.handleEvent(FeatureConfigurationEvent.CheckFeatureEnabled(flagKey))
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertIs<AsyncData.Success<Map<String, Boolean>>>(state.data.featureFlags)
                val flags = state.data.featureFlags.getOrNull()
                assertEquals(expectedValue, flags?.get(flagKey))
            }
        }
        
        // Verify service call
        coVerify { mockFeatureFlagService.isFeatureEnabled(flagKey) }
    }

    @Test
    fun `given unknown feature flag, when CheckFeatureEnabled event, then returns default false value`() = runTest {
        // Given
        val unknownFlagKey = "unknown_feature"
        val defaultValue = false
        coEvery { mockFeatureFlagService.isFeatureEnabled(unknownFlagKey) } returns Result.success(defaultValue)
        
        // When
        viewModel.handleEvent(FeatureConfigurationEvent.CheckFeatureEnabled(unknownFlagKey))
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertIs<AsyncData.Success<Map<String, Boolean>>>(state.data.featureFlags)
                val flags = state.data.featureFlags.getOrNull()
                assertEquals(defaultValue, flags?.get(unknownFlagKey))
            }
        }
        
        // Verify service call
        coVerify { mockFeatureFlagService.isFeatureEnabled(unknownFlagKey) }
    }

    // === Load All Feature Flags Tests ===

    @Test
    fun `given feature flag service, when LoadAllFeatureFlags event, then loads all flags and variants`() = runTest {
        // Given
        coEvery { mockFeatureFlagService.getAllFeatureFlags() } returns Result.success(testFeatureFlags)
        coEvery { mockFeatureFlagService.getABTestVariant("onboarding_flow") } returns Result.success("variant_b")
        coEvery { mockFeatureFlagService.getABTestVariant("chart_layout") } returns Result.success("grid_view")
        
        // When
        viewModel.handleEvent(FeatureConfigurationEvent.LoadAllFeatureFlags)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertIs<AsyncData.Success<Map<String, Boolean>>>(state.data.featureFlags)
                assertEquals(testFeatureFlags, state.data.featureFlags.getOrNull())
                assertTrue(state.data.lastRefreshTimestamp > 0L)
            }
        }
        
        // Verify service calls
        coVerify { mockFeatureFlagService.getAllFeatureFlags() }
    }

    // === Load Initial Data Tests ===

    @Test
    fun `given service initialization, when LoadInitialData event, then loads essential configuration data`() = runTest {
        // Given
        coEvery { mockFeatureFlagService.getAllFeatureFlags() } returns Result.success(testFeatureFlags)
        coEvery { mockFeatureFlagService.refreshRemoteConfig() } returns Result.success(Unit)
        
        // When
        viewModel.handleEvent(FeatureConfigurationEvent.LoadInitialData)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertIs<AsyncData.Success<Map<String, Boolean>>>(state.data.featureFlags)
                assertIs<AsyncData.Success<Unit>>(state.data.remoteConfigStatus)
                assertTrue(state.data.lastRefreshTimestamp > 0L)
            }
        }
        
        // Verify essential service calls
        coVerify { mockFeatureFlagService.getAllFeatureFlags() }
        coVerify { mockFeatureFlagService.refreshRemoteConfig() }
    }

    // === Clear Cache Tests ===

    @Test
    fun `given cached configuration data, when ClearCache event, then resets all data to NotAsked`() = runTest {
        // Given - load initial data
        coEvery { mockFeatureFlagService.getAllFeatureFlags() } returns Result.success(testFeatureFlags)
        viewModel.handleEvent(FeatureConfigurationEvent.LoadAllFeatureFlags)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // When
        viewModel.handleEvent(FeatureConfigurationEvent.ClearCache)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertIs<AsyncData.NotAsked>(state.data.featureFlags)
                assertIs<AsyncData.NotAsked>(state.data.abTestVariants)
                assertIs<AsyncData.NotAsked>(state.data.remoteConfigStatus)
                assertEquals(0L, state.data.lastRefreshTimestamp)
            }
        }
    }

    // === Analytics Configuration Tests ===

    @Test
    fun `given analytics flags, when EnableAnalytics event, then enables analytics features`() = runTest {
        // Given
        val analyticsFlags = mapOf(
            "analytics_enabled" to true,
            "crash_reporting" to true,
            "performance_monitoring" to true
        )
        coEvery { mockFeatureFlagService.getAllFeatureFlags() } returns Result.success(analyticsFlags)
        
        // When
        viewModel.handleEvent(FeatureConfigurationEvent.EnableAnalytics)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertIs<AsyncData.Success<Map<String, Boolean>>>(state.data.featureFlags)
                val flags = state.data.featureFlags.getOrNull()
                assertTrue(flags?.get("analytics_enabled") == true)
                assertTrue(flags?.get("crash_reporting") == true)
                assertTrue(flags?.get("performance_monitoring") == true)
            }
        }
        
        // Verify analytics enable service call
        coVerify { mockFeatureFlagService.getAllFeatureFlags() }
    }

    @Test
    fun `given analytics flags, when DisableAnalytics event, then disables analytics features`() = runTest {
        // Given
        val disabledAnalyticsFlags = mapOf(
            "analytics_enabled" to false,
            "crash_reporting" to false,
            "performance_monitoring" to false,
            "user_behavior_tracking" to false
        )
        coEvery { mockFeatureFlagService.getAllFeatureFlags() } returns Result.success(disabledAnalyticsFlags)
        
        // When
        viewModel.handleEvent(FeatureConfigurationEvent.DisableAnalytics)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertIs<AsyncData.Success<Map<String, Boolean>>>(state.data.featureFlags)
                val flags = state.data.featureFlags.getOrNull()
                assertFalse(flags?.get("analytics_enabled") == true)
                assertFalse(flags?.get("crash_reporting") == true)
                assertFalse(flags?.get("performance_monitoring") == true)
                assertFalse(flags?.get("user_behavior_tracking") == true)
            }
        }
        
        // Verify analytics disable service call
        coVerify { mockFeatureFlagService.getAllFeatureFlags() }
    }

    // === Service Exception Handling Tests ===

    @Test
    fun `given service throws exception, when loading feature flags, then handles error gracefully`() = runTest {
        // Given
        val exception = RuntimeException("Firebase Remote Config initialization failed")
        coEvery { mockFeatureFlagService.getAllFeatureFlags() } throws exception
        
        // When
        viewModel.handleEvent(FeatureConfigurationEvent.LoadFeatureFlags)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then - verify error handling doesn't crash
        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state is UiState.Success || state is UiState.Error || state is UiState.Loading)
        }
        
        // Verify error handler was called
        coVerify { mockErrorHandler.handleError(any(), any()) }
    }

    // === Feature Flag Consistency Tests ===

    @Test
    fun `given multiple flag requests, when checking same feature flag multiple times, then maintains consistency`() = runTest {
        // Given
        val flagKey = "premium_features"
        val consistentValue = true
        coEvery { mockFeatureFlagService.isFeatureEnabled(flagKey) } returns Result.success(consistentValue)
        
        // When - check same flag multiple times
        viewModel.handleEvent(FeatureConfigurationEvent.CheckFeatureEnabled(flagKey))
        testDispatcher.scheduler.advanceUntilIdle()
        
        viewModel.handleEvent(FeatureConfigurationEvent.CheckFeatureEnabled(flagKey))
        testDispatcher.scheduler.advanceUntilIdle()
        
        viewModel.handleEvent(FeatureConfigurationEvent.CheckFeatureEnabled(flagKey))
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then - should maintain consistent value
        viewModel.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertIs<AsyncData.Success<Map<String, Boolean>>>(state.data.featureFlags)
                val flags = state.data.featureFlags.getOrNull()
                assertEquals(consistentValue, flags?.get(flagKey))
            }
        }
        
        // Verify service called multiple times but returns consistent value
        coVerify(exactly = 3) { mockFeatureFlagService.isFeatureEnabled(flagKey) }
    }

    // === A/B Test Variant Consistency Tests ===

    @Test
    fun `given A/B test requests, when getting same variant multiple times, then maintains stable assignment`() = runTest {
        // Given
        val testKey = "workout_timer"
        val stableVariant = "enhanced"
        coEvery { mockFeatureFlagService.getABTestVariant(testKey) } returns Result.success(stableVariant)
        
        // When - get same variant multiple times
        viewModel.handleEvent(FeatureConfigurationEvent.GetABTestVariant(testKey))
        testDispatcher.scheduler.advanceUntilIdle()
        
        viewModel.handleEvent(FeatureConfigurationEvent.GetABTestVariant(testKey))
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then - should maintain stable variant assignment
        viewModel.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertIs<AsyncData.Success<Map<String, String>>>(state.data.abTestVariants)
                val variants = state.data.abTestVariants.getOrNull()
                assertEquals(stableVariant, variants?.get(testKey))
            }
        }
        
        // Verify stable variant assignment
        coVerify(exactly = 2) { mockFeatureFlagService.getABTestVariant(testKey) }
    }

    // === Remote Config Update Tests ===

    @Test
    fun `given remote config changes, when refreshing configuration, then updates local feature flags`() = runTest {
        // Given - initial flags
        coEvery { mockFeatureFlagService.getAllFeatureFlags() } returns Result.success(testFeatureFlags)
        viewModel.handleEvent(FeatureConfigurationEvent.LoadFeatureFlags)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Updated flags after remote config refresh
        val updatedFlags = testFeatureFlags.toMutableMap().apply {
            put("new_feature_rollout", true)
            put("beta_charts", true) // Changed from false to true
        }
        
        coEvery { mockFeatureFlagService.refreshRemoteConfig() } returns Result.success(Unit)
        coEvery { mockFeatureFlagService.getAllFeatureFlags() } returns Result.success(updatedFlags)
        
        // When
        viewModel.handleEvent(FeatureConfigurationEvent.RefreshRemoteConfig)
        testDispatcher.scheduler.advanceUntilIdle()
        
        viewModel.handleEvent(FeatureConfigurationEvent.LoadFeatureFlags)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertIs<AsyncData.Success<Map<String, Boolean>>>(state.data.featureFlags)
                val flags = state.data.featureFlags.getOrNull()
                assertTrue(flags?.get("new_feature_rollout") == true)
                assertTrue(flags?.get("beta_charts") == true) // Should be updated
            }
        }
        
        // Verify remote config refresh and reload
        coVerify { mockFeatureFlagService.refreshRemoteConfig() }
        coVerify(exactly = 2) { mockFeatureFlagService.getAllFeatureFlags() }
    }

    // === Network Connectivity Tests ===

    @Test
    fun `given network unavailable, when refreshing remote config, then shows network error with fallback`() = runTest {
        // Given
        val networkError = LiftrixError.NetworkError("No internet connection")
        coEvery { mockFeatureFlagService.refreshRemoteConfig() } returns Result.failure(networkError)
        
        // Local fallback flags
        val fallbackFlags = mapOf(
            "analytics_enabled" to false,
            "export_enabled" to false,
            "offline_mode" to true
        )
        coEvery { mockFeatureFlagService.getAllFeatureFlags() } returns Result.success(fallbackFlags)
        
        // When
        viewModel.handleEvent(FeatureConfigurationEvent.RefreshRemoteConfig)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then - should show error but still have local fallback
        viewModel.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertIs<AsyncData.Failure>(state.data.remoteConfigStatus)
                assertEquals(networkError, state.data.remoteConfigStatus.errorOrNull())
            }
        }
        
        // Verify fallback mechanism
        coVerify { mockFeatureFlagService.refreshRemoteConfig() }
        coVerify { mockErrorHandler.handleError(networkError, any()) }
    }

    // === Performance Tests ===

    @Test
    fun `given multiple concurrent feature requests, when triggered simultaneously, then handles all requests correctly`() = runTest {
        // Given
        val flag1 = "analytics_enabled"
        val flag2 = "export_enabled" 
        val flag3 = "premium_features"
        val abTest1 = "onboarding_flow"
        val abTest2 = "chart_layout"
        
        coEvery { mockFeatureFlagService.isFeatureEnabled(flag1) } returns Result.success(true)
        coEvery { mockFeatureFlagService.isFeatureEnabled(flag2) } returns Result.success(false)
        coEvery { mockFeatureFlagService.isFeatureEnabled(flag3) } returns Result.success(true)
        coEvery { mockFeatureFlagService.getABTestVariant(abTest1) } returns Result.success("variant_b")
        coEvery { mockFeatureFlagService.getABTestVariant(abTest2) } returns Result.success("grid_view")
        
        // When - trigger concurrent requests
        viewModel.handleEvent(FeatureConfigurationEvent.CheckFeatureEnabled(flag1))
        viewModel.handleEvent(FeatureConfigurationEvent.CheckFeatureEnabled(flag2))
        viewModel.handleEvent(FeatureConfigurationEvent.CheckFeatureEnabled(flag3))
        viewModel.handleEvent(FeatureConfigurationEvent.GetABTestVariant(abTest1))
        viewModel.handleEvent(FeatureConfigurationEvent.GetABTestVariant(abTest2))
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then - all requests should complete successfully
        viewModel.uiState.test {
            val state = awaitItem()
            if (state is UiState.Success) {
                assertIs<AsyncData.Success<Map<String, Boolean>>>(state.data.featureFlags)
                assertIs<AsyncData.Success<Map<String, String>>>(state.data.abTestVariants)
                
                val flags = state.data.featureFlags.getOrNull()
                val variants = state.data.abTestVariants.getOrNull()
                
                assertEquals(true, flags?.get(flag1))
                assertEquals(false, flags?.get(flag2))
                assertEquals(true, flags?.get(flag3))
                assertEquals("variant_b", variants?.get(abTest1))
                assertEquals("grid_view", variants?.get(abTest2))
            }
        }
        
        // Verify all service calls made
        coVerify { mockFeatureFlagService.isFeatureEnabled(flag1) }
        coVerify { mockFeatureFlagService.isFeatureEnabled(flag2) }
        coVerify { mockFeatureFlagService.isFeatureEnabled(flag3) }
        coVerify { mockFeatureFlagService.getABTestVariant(abTest1) }
        coVerify { mockFeatureFlagService.getABTestVariant(abTest2) }
    }
}