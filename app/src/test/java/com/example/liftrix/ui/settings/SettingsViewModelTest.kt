package com.example.liftrix.ui.settings

import app.cash.turbine.test
import com.example.liftrix.domain.model.SubscriptionStatus
import com.example.liftrix.domain.model.User
import com.example.liftrix.domain.model.UserSettings
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.service.AnalyticsService
import com.example.liftrix.domain.usecase.settings.EnhancedSignOutUseCase
import com.example.liftrix.domain.usecase.settings.GetSubscriptionStatusUseCase
import com.example.liftrix.domain.usecase.settings.GetUserSettingsUseCase
import com.example.liftrix.domain.usecase.settings.UpdateSettingsUseCase
import com.example.liftrix.data.local.entity.SubscriptionTier
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    // Test dependencies
    private lateinit var mockGetUserSettingsUseCase: GetUserSettingsUseCase
    private lateinit var mockUpdateSettingsUseCase: UpdateSettingsUseCase
    private lateinit var mockGetSubscriptionStatusUseCase: GetSubscriptionStatusUseCase
    private lateinit var mockEnhancedSignOutUseCase: EnhancedSignOutUseCase
    private lateinit var mockAuthRepository: AuthRepository
    private lateinit var mockAnalyticsService: AnalyticsService
    private lateinit var viewModel: SettingsViewModel

    // Test dispatchers
    private val testDispatcher = StandardTestDispatcher()

    // Test data
    private val testUserId = "test-user-id"
    private val testUser = User(
        uid = testUserId,
        email = "test@example.com",
        displayName = "Test User",
        isAnonymous = false,
        createdAt = Instant.now()
    )

    private val testUserSettings = UserSettings(
        userId = testUserId,
        darkMode = false,
        notificationsEnabled = true,
        updatedAt = Instant.now()
    )

    private val testSubscriptionStatus = SubscriptionStatus(
        userId = testUserId,
        tier = SubscriptionTier.FREE,
        isActive = false,
        status = "free",
        provider = "none",
        expiresAt = null,
        trialEndsAt = null,
        autoRenew = false,
        startedAt = Instant.now()
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        
        // Initialize mocks
        mockGetUserSettingsUseCase = mockk()
        mockUpdateSettingsUseCase = mockk()
        mockGetSubscriptionStatusUseCase = mockk()
        mockEnhancedSignOutUseCase = mockk()
        mockAuthRepository = mockk()
        mockAnalyticsService = mockk()

        // Default mock behaviors
        every { mockAuthRepository.currentUser } returns flowOf(testUser)
        coEvery { mockGetUserSettingsUseCase(any()) } returns flowOf(Result.success(testUserSettings))
        coEvery { mockGetSubscriptionStatusUseCase(any()) } returns flowOf(Result.success(testSubscriptionStatus))
        coEvery { mockUpdateSettingsUseCase.updateDarkMode(any(), any()) } returns Result.success(Unit)
        coEvery { mockUpdateSettingsUseCase.updateNotifications(any(), any()) } returns Result.success(Unit)
        coEvery { mockEnhancedSignOutUseCase() } returns Result.success(Unit)
        coEvery { mockAnalyticsService.logEvent(any(), any()) } just Runs
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        clearAllMocks()
    }

    @Test
    fun `initial state should be default values`() = runTest {
        // When
        viewModel = SettingsViewModel(
            mockGetUserSettingsUseCase,
            mockUpdateSettingsUseCase,
            mockGetSubscriptionStatusUseCase,
            mockEnhancedSignOutUseCase,
            mockAuthRepository,
            mockAnalyticsService
        )

        // Then
        val initialState = viewModel.uiState.value
        assertFalse(initialState.isLoading)
        assertNull(initialState.userSettings)
        assertNull(initialState.subscriptionStatus)
        assertNull(initialState.error)
        assertFalse(initialState.isUpdatingSettings)
        assertFalse(initialState.isSigningOut)
        assertFalse(initialState.showLogoutDialog)
        assertNull(initialState.expandedCard)
    }

    @Test
    fun `init should observe auth state and track screen viewed`() = runTest {
        // When
        viewModel = SettingsViewModel(
            mockGetUserSettingsUseCase,
            mockUpdateSettingsUseCase,
            mockGetSubscriptionStatusUseCase,
            mockEnhancedSignOutUseCase,
            mockAuthRepository,
            mockAnalyticsService
        )
        advanceUntilIdle()

        // Then
        verify { mockAuthRepository.currentUser }
        coVerify { mockAnalyticsService.logEvent("settings_screen_viewed", any()) }
    }

    @Test
    fun `authenticated user should load settings successfully`() = runTest {
        // Given
        viewModel = SettingsViewModel(
            mockGetUserSettingsUseCase,
            mockUpdateSettingsUseCase,
            mockGetSubscriptionStatusUseCase,
            mockEnhancedSignOutUseCase,
            mockAuthRepository,
            mockAnalyticsService
        )

        // When
        advanceUntilIdle()

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(testUserSettings, state.userSettings)
            assertEquals(testSubscriptionStatus, state.subscriptionStatus)
            assertFalse(state.isLoading)
            assertNull(state.error)
        }

        coVerify { mockGetUserSettingsUseCase(testUserId) }
        coVerify { mockGetSubscriptionStatusUseCase(testUserId) }
    }

    @Test
    fun `unauthenticated user should show error state`() = runTest {
        // Given
        every { mockAuthRepository.currentUser } returns flowOf(null)
        
        // When
        viewModel = SettingsViewModel(
            mockGetUserSettingsUseCase,
            mockUpdateSettingsUseCase,
            mockGetSubscriptionStatusUseCase,
            mockEnhancedSignOutUseCase,
            mockAuthRepository,
            mockAnalyticsService
        )
        advanceUntilIdle()

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals("User not authenticated", state.error)
            assertNull(state.userSettings)
            assertNull(state.subscriptionStatus)
        }

        coVerify(exactly = 0) { mockGetUserSettingsUseCase(any()) }
        coVerify(exactly = 0) { mockGetSubscriptionStatusUseCase(any()) }
    }

    @Test
    fun `settings loading error should update error state`() = runTest {
        // Given
        val errorMessage = "Settings loading failed"
        coEvery { mockGetUserSettingsUseCase(any()) } returns flowOf(Result.failure(RuntimeException(errorMessage)))
        
        // When
        viewModel = SettingsViewModel(
            mockGetUserSettingsUseCase,
            mockUpdateSettingsUseCase,
            mockGetSubscriptionStatusUseCase,
            mockEnhancedSignOutUseCase,
            mockAuthRepository,
            mockAnalyticsService
        )
        advanceUntilIdle()

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals("Failed to load settings data", state.error)
            assertFalse(state.isLoading)
            assertNull(state.userSettings)
            assertEquals(testSubscriptionStatus, state.subscriptionStatus)
        }
    }

    @Test
    fun `subscription loading error should update error state`() = runTest {
        // Given
        val errorMessage = "Subscription loading failed"
        coEvery { mockGetSubscriptionStatusUseCase(any()) } returns flowOf(Result.failure(RuntimeException(errorMessage)))
        
        // When
        viewModel = SettingsViewModel(
            mockGetUserSettingsUseCase,
            mockUpdateSettingsUseCase,
            mockGetSubscriptionStatusUseCase,
            mockEnhancedSignOutUseCase,
            mockAuthRepository,
            mockAnalyticsService
        )
        advanceUntilIdle()

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals("Failed to load subscription data", state.error)
            assertFalse(state.isLoading)
            assertEquals(testUserSettings, state.userSettings)
            assertNull(state.subscriptionStatus)
        }
    }

    @Test
    fun `both settings and subscription loading error should show combined error`() = runTest {
        // Given
        coEvery { mockGetUserSettingsUseCase(any()) } returns flowOf(Result.failure(RuntimeException("Settings error")))
        coEvery { mockGetSubscriptionStatusUseCase(any()) } returns flowOf(Result.failure(RuntimeException("Subscription error")))
        
        // When
        viewModel = SettingsViewModel(
            mockGetUserSettingsUseCase,
            mockUpdateSettingsUseCase,
            mockGetSubscriptionStatusUseCase,
            mockEnhancedSignOutUseCase,
            mockAuthRepository,
            mockAnalyticsService
        )
        advanceUntilIdle()

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals("Failed to load settings and subscription data", state.error)
            assertFalse(state.isLoading)
            assertNull(state.userSettings)
            assertNull(state.subscriptionStatus)
        }
    }

    @Test
    fun `LoadSettings event should reload settings data`() = runTest {
        // Given
        viewModel = SettingsViewModel(
            mockGetUserSettingsUseCase,
            mockUpdateSettingsUseCase,
            mockGetSubscriptionStatusUseCase,
            mockEnhancedSignOutUseCase,
            mockAuthRepository,
            mockAnalyticsService
        )
        advanceUntilIdle()

        // When
        viewModel.onEvent(SettingsEvent.LoadSettings)
        advanceUntilIdle()

        // Then
        coVerify(atLeast = 2) { mockGetUserSettingsUseCase(testUserId) }
        coVerify(atLeast = 2) { mockGetSubscriptionStatusUseCase(testUserId) }
    }

    @Test
    fun `RefreshSettings event should reload settings and track refresh`() = runTest {
        // Given
        viewModel = SettingsViewModel(
            mockGetUserSettingsUseCase,
            mockUpdateSettingsUseCase,
            mockGetSubscriptionStatusUseCase,
            mockEnhancedSignOutUseCase,
            mockAuthRepository,
            mockAnalyticsService
        )
        advanceUntilIdle()

        // When
        viewModel.onEvent(SettingsEvent.RefreshSettings)
        advanceUntilIdle()

        // Then
        coVerify(atLeast = 2) { mockGetUserSettingsUseCase(testUserId) }
        coVerify(atLeast = 2) { mockGetSubscriptionStatusUseCase(testUserId) }
        coVerify { mockAnalyticsService.logEvent("settings_refreshed", any()) }
    }

    @Test
    fun `UpdateDarkMode event should update dark mode setting successfully`() = runTest {
        // Given
        viewModel = SettingsViewModel(
            mockGetUserSettingsUseCase,
            mockUpdateSettingsUseCase,
            mockGetSubscriptionStatusUseCase,
            mockEnhancedSignOutUseCase,
            mockAuthRepository,
            mockAnalyticsService
        )
        advanceUntilIdle()

        // When
        viewModel.onEvent(SettingsEvent.UpdateDarkMode(true))
        advanceUntilIdle()

        // Then
        coVerify { mockUpdateSettingsUseCase.updateDarkMode(testUserId, true) }
        coVerify { mockAnalyticsService.logEvent("setting_changed", match { 
            it["setting_name"] == "dark_mode" && it["new_value"] == true 
        }) }
    }

    @Test
    fun `UpdateDarkMode event should handle failure and set error state`() = runTest {
        // Given
        val errorMessage = "Dark mode update failed"
        coEvery { mockUpdateSettingsUseCase.updateDarkMode(any(), any()) } returns Result.failure(RuntimeException(errorMessage))
        
        viewModel = SettingsViewModel(
            mockGetUserSettingsUseCase,
            mockUpdateSettingsUseCase,
            mockGetSubscriptionStatusUseCase,
            mockEnhancedSignOutUseCase,
            mockAuthRepository,
            mockAnalyticsService
        )
        advanceUntilIdle()

        // When
        viewModel.onEvent(SettingsEvent.UpdateDarkMode(true))
        advanceUntilIdle()

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state.error?.contains("Failed to update dark mode") == true)
            assertFalse(state.isUpdatingSettings)
        }
    }

    @Test
    fun `UpdateNotifications event should update notifications setting successfully`() = runTest {
        // Given
        viewModel = SettingsViewModel(
            mockGetUserSettingsUseCase,
            mockUpdateSettingsUseCase,
            mockGetSubscriptionStatusUseCase,
            mockEnhancedSignOutUseCase,
            mockAuthRepository,
            mockAnalyticsService
        )
        advanceUntilIdle()

        // When
        viewModel.onEvent(SettingsEvent.UpdateNotifications(false))
        advanceUntilIdle()

        // Then
        coVerify { mockUpdateSettingsUseCase.updateNotifications(testUserId, false) }
        coVerify { mockAnalyticsService.logEvent("setting_changed", match { 
            it["setting_name"] == "notifications" && it["new_value"] == false 
        }) }
    }

    @Test
    fun `UpdateNotifications event should handle failure and set error state`() = runTest {
        // Given
        val errorMessage = "Notifications update failed"
        coEvery { mockUpdateSettingsUseCase.updateNotifications(any(), any()) } returns Result.failure(RuntimeException(errorMessage))
        
        viewModel = SettingsViewModel(
            mockGetUserSettingsUseCase,
            mockUpdateSettingsUseCase,
            mockGetSubscriptionStatusUseCase,
            mockEnhancedSignOutUseCase,
            mockAuthRepository,
            mockAnalyticsService
        )
        advanceUntilIdle()

        // When
        viewModel.onEvent(SettingsEvent.UpdateNotifications(false))
        advanceUntilIdle()

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state.error?.contains("Failed to update notifications") == true)
            assertFalse(state.isUpdatingSettings)
        }
    }

    @Test
    fun `SignOutRequested event should show logout dialog and track analytics`() = runTest {
        // Given
        viewModel = SettingsViewModel(
            mockGetUserSettingsUseCase,
            mockUpdateSettingsUseCase,
            mockGetSubscriptionStatusUseCase,
            mockEnhancedSignOutUseCase,
            mockAuthRepository,
            mockAnalyticsService
        )
        advanceUntilIdle()

        // When
        viewModel.onEvent(SettingsEvent.SignOutRequested)

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state.showLogoutDialog)
        }
        
        coVerify { mockAnalyticsService.logEvent("settings_sign_out_requested", any()) }
    }

    @Test
    fun `SignOutConfirmed event should perform sign out successfully`() = runTest {
        // Given
        viewModel = SettingsViewModel(
            mockGetUserSettingsUseCase,
            mockUpdateSettingsUseCase,
            mockGetSubscriptionStatusUseCase,
            mockEnhancedSignOutUseCase,
            mockAuthRepository,
            mockAnalyticsService
        )
        advanceUntilIdle()

        // When
        viewModel.onEvent(SettingsEvent.SignOutConfirmed)
        advanceUntilIdle()

        // Then
        coVerify { mockEnhancedSignOutUseCase() }
        coVerify { mockAnalyticsService.logEvent("settings_sign_out_completed", any()) }
        
        viewModel.uiState.test {
            val state = awaitItem()
            assertFalse(state.showLogoutDialog)
        }
    }

    @Test
    fun `SignOutConfirmed event should handle failure and set error state`() = runTest {
        // Given
        val errorMessage = "Sign out failed"
        coEvery { mockEnhancedSignOutUseCase() } returns Result.failure(RuntimeException(errorMessage))
        
        viewModel = SettingsViewModel(
            mockGetUserSettingsUseCase,
            mockUpdateSettingsUseCase,
            mockGetSubscriptionStatusUseCase,
            mockEnhancedSignOutUseCase,
            mockAuthRepository,
            mockAnalyticsService
        )
        advanceUntilIdle()

        // When
        viewModel.onEvent(SettingsEvent.SignOutConfirmed)
        advanceUntilIdle()

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state.error?.contains("Failed to sign out") == true)
            assertFalse(state.isSigningOut)
        }
    }

    @Test
    fun `SignOutCancelled event should hide logout dialog and track analytics`() = runTest {
        // Given
        viewModel = SettingsViewModel(
            mockGetUserSettingsUseCase,
            mockUpdateSettingsUseCase,
            mockGetSubscriptionStatusUseCase,
            mockEnhancedSignOutUseCase,
            mockAuthRepository,
            mockAnalyticsService
        )
        advanceUntilIdle()

        // Show dialog first
        viewModel.onEvent(SettingsEvent.SignOutRequested)

        // When
        viewModel.onEvent(SettingsEvent.SignOutCancelled)

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertFalse(state.showLogoutDialog)
        }
        
        coVerify { mockAnalyticsService.logEvent("settings_sign_out_cancelled", any()) }
    }

    @Test
    fun `ToggleCardExpansion event should toggle card expansion state`() = runTest {
        // Given
        val cardId = "test-card"
        viewModel = SettingsViewModel(
            mockGetUserSettingsUseCase,
            mockUpdateSettingsUseCase,
            mockGetSubscriptionStatusUseCase,
            mockEnhancedSignOutUseCase,
            mockAuthRepository,
            mockAnalyticsService
        )
        advanceUntilIdle()

        // When - expand card
        viewModel.onEvent(SettingsEvent.ToggleCardExpansion(cardId))

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(cardId, state.expandedCard)
        }
        
        coVerify { mockAnalyticsService.logEvent("settings_card_toggled", match { 
            it["card_id"] == cardId && it["expanded"] == true 
        }) }

        // When - collapse card
        viewModel.onEvent(SettingsEvent.ToggleCardExpansion(cardId))

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertNull(state.expandedCard)
        }
        
        coVerify { mockAnalyticsService.logEvent("settings_card_toggled", match { 
            it["card_id"] == cardId && it["expanded"] == false 
        }) }
    }

    @Test
    fun `ProfileAvatarTapped event should track analytics`() = runTest {
        // Given
        viewModel = SettingsViewModel(
            mockGetUserSettingsUseCase,
            mockUpdateSettingsUseCase,
            mockGetSubscriptionStatusUseCase,
            mockEnhancedSignOutUseCase,
            mockAuthRepository,
            mockAnalyticsService
        )

        // When
        viewModel.onEvent(SettingsEvent.ProfileAvatarTapped)

        // Then
        coVerify { mockAnalyticsService.logEvent("settings_profile_avatar_tapped", any()) }
    }

    @Test
    fun `ErrorDismissed event should clear error state`() = runTest {
        // Given
        coEvery { mockGetUserSettingsUseCase(any()) } returns flowOf(Result.failure(RuntimeException("Test error")))
        
        viewModel = SettingsViewModel(
            mockGetUserSettingsUseCase,
            mockUpdateSettingsUseCase,
            mockGetSubscriptionStatusUseCase,
            mockEnhancedSignOutUseCase,
            mockAuthRepository,
            mockAnalyticsService
        )
        advanceUntilIdle()

        // Verify error exists
        viewModel.uiState.test {
            val errorState = awaitItem()
            assertTrue(errorState.error != null)
        }

        // When
        viewModel.onEvent(SettingsEvent.ErrorDismissed)

        // Then
        viewModel.uiState.test {
            val clearedState = awaitItem()
            assertNull(clearedState.error)
        }
    }

    @Test
    fun `RetryRequested event should retry last failed operation`() = runTest {
        // Given
        coEvery { mockUpdateSettingsUseCase.updateDarkMode(any(), any()) } returns Result.failure(RuntimeException("Test error"))
        
        viewModel = SettingsViewModel(
            mockGetUserSettingsUseCase,
            mockUpdateSettingsUseCase,
            mockGetSubscriptionStatusUseCase,
            mockEnhancedSignOutUseCase,
            mockAuthRepository,
            mockAnalyticsService
        )
        advanceUntilIdle()

        // Perform operation that fails
        viewModel.onEvent(SettingsEvent.UpdateDarkMode(true))
        advanceUntilIdle()

        // Reset mock to succeed
        coEvery { mockUpdateSettingsUseCase.updateDarkMode(any(), any()) } returns Result.success(Unit)

        // When
        viewModel.onEvent(SettingsEvent.RetryRequested)
        advanceUntilIdle()

        // Then
        coVerify(exactly = 2) { mockUpdateSettingsUseCase.updateDarkMode(testUserId, true) }
    }

    @Test
    fun `navigation events should track analytics`() = runTest {
        // Given
        viewModel = SettingsViewModel(
            mockGetUserSettingsUseCase,
            mockUpdateSettingsUseCase,
            mockGetSubscriptionStatusUseCase,
            mockEnhancedSignOutUseCase,
            mockAuthRepository,
            mockAnalyticsService
        )

        // When
        viewModel.onEvent(SettingsEvent.NavigateToProfile)
        viewModel.onEvent(SettingsEvent.NavigateToSubscription)
        viewModel.onEvent(SettingsEvent.NavigateToPrivacy)
        viewModel.onEvent(SettingsEvent.NavigateToHelp)
        viewModel.onEvent(SettingsEvent.NavigateToAbout)

        // Then
        coVerify { mockAnalyticsService.logEvent("settings_navigation", match { it["destination"] == "profile" }) }
        coVerify { mockAnalyticsService.logEvent("settings_navigation", match { it["destination"] == "subscription" }) }
        coVerify { mockAnalyticsService.logEvent("settings_navigation", match { it["destination"] == "privacy" }) }
        coVerify { mockAnalyticsService.logEvent("settings_navigation", match { it["destination"] == "help" }) }
        coVerify { mockAnalyticsService.logEvent("settings_navigation", match { it["destination"] == "about" }) }
    }

    @Test
    fun `subscription events should track analytics`() = runTest {
        // Given
        viewModel = SettingsViewModel(
            mockGetUserSettingsUseCase,
            mockUpdateSettingsUseCase,
            mockGetSubscriptionStatusUseCase,
            mockEnhancedSignOutUseCase,
            mockAuthRepository,
            mockAnalyticsService
        )

        // When
        viewModel.onEvent(SettingsEvent.UpgradeSubscription)
        viewModel.onEvent(SettingsEvent.ManageSubscription)
        viewModel.onEvent(SettingsEvent.SubscriptionPurchaseCompleted)

        // Then
        coVerify { mockAnalyticsService.logEvent("settings_subscription_action", match { it["action"] == "upgrade_requested" }) }
        coVerify { mockAnalyticsService.logEvent("settings_subscription_action", match { it["action"] == "manage_requested" }) }
        coVerify { mockAnalyticsService.logEvent("settings_subscription_action", match { it["action"] == "purchase_completed" }) }
    }

    @Test
    fun `SubscriptionPurchaseCompleted event should refresh settings`() = runTest {
        // Given
        viewModel = SettingsViewModel(
            mockGetUserSettingsUseCase,
            mockUpdateSettingsUseCase,
            mockGetSubscriptionStatusUseCase,
            mockEnhancedSignOutUseCase,
            mockAuthRepository,
            mockAnalyticsService
        )
        advanceUntilIdle()

        // When
        viewModel.onEvent(SettingsEvent.SubscriptionPurchaseCompleted)
        advanceUntilIdle()

        // Then
        coVerify(atLeast = 2) { mockGetUserSettingsUseCase(testUserId) }
        coVerify(atLeast = 2) { mockGetSubscriptionStatusUseCase(testUserId) }
    }

    @Test
    fun `data events should track analytics`() = runTest {
        // Given
        viewModel = SettingsViewModel(
            mockGetUserSettingsUseCase,
            mockUpdateSettingsUseCase,
            mockGetSubscriptionStatusUseCase,
            mockEnhancedSignOutUseCase,
            mockAuthRepository,
            mockAnalyticsService
        )

        // When
        viewModel.onEvent(SettingsEvent.ExportDataRequested)
        viewModel.onEvent(SettingsEvent.DeleteAccountRequested)

        // Then
        coVerify { mockAnalyticsService.logEvent("settings_data_action", match { it["action"] == "export_requested" }) }
        coVerify { mockAnalyticsService.logEvent("settings_data_action", match { it["action"] == "delete_requested" }) }
    }

    @Test
    fun `system events should track analytics and refresh settings`() = runTest {
        // Given
        viewModel = SettingsViewModel(
            mockGetUserSettingsUseCase,
            mockUpdateSettingsUseCase,
            mockGetSubscriptionStatusUseCase,
            mockEnhancedSignOutUseCase,
            mockAuthRepository,
            mockAnalyticsService
        )
        advanceUntilIdle()

        // When
        viewModel.onEvent(SettingsEvent.SystemThemeChanged)
        viewModel.onEvent(SettingsEvent.SubscriptionStatusChanged)
        advanceUntilIdle()

        // Then
        coVerify { mockAnalyticsService.logEvent("settings_system_event", match { it["event"] == "theme_changed" }) }
        coVerify { mockAnalyticsService.logEvent("settings_system_event", match { it["event"] == "subscription_status_changed" }) }
        coVerify(atLeast = 2) { mockGetUserSettingsUseCase(testUserId) }
        coVerify(atLeast = 2) { mockGetSubscriptionStatusUseCase(testUserId) }
    }

    @Test
    fun `loading states should be managed correctly during operations`() = runTest {
        // Given
        viewModel = SettingsViewModel(
            mockGetUserSettingsUseCase,
            mockUpdateSettingsUseCase,
            mockGetSubscriptionStatusUseCase,
            mockEnhancedSignOutUseCase,
            mockAuthRepository,
            mockAnalyticsService
        )
        advanceUntilIdle()

        // When
        viewModel.onEvent(SettingsEvent.UpdateDarkMode(true))
        testScheduler.advanceTimeBy(100) // Advance a bit but not to completion

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state.isUpdatingSettings)
        }
    }

    @Test
    fun `sign out loading state should be managed correctly`() = runTest {
        // Given
        viewModel = SettingsViewModel(
            mockGetUserSettingsUseCase,
            mockUpdateSettingsUseCase,
            mockGetSubscriptionStatusUseCase,
            mockEnhancedSignOutUseCase,
            mockAuthRepository,
            mockAnalyticsService
        )
        advanceUntilIdle()

        // When
        viewModel.onEvent(SettingsEvent.SignOutConfirmed)
        testScheduler.advanceTimeBy(100) // Advance a bit but not to completion

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state.isSigningOut)
            assertFalse(state.showLogoutDialog)
        }
    }

    @Test
    fun `analytics tracking should handle exceptions gracefully`() = runTest {
        // Given
        coEvery { mockAnalyticsService.logEvent(any(), any()) } throws RuntimeException("Analytics error")
        
        // When & Then - no exception should be thrown
        viewModel = SettingsViewModel(
            mockGetUserSettingsUseCase,
            mockUpdateSettingsUseCase,
            mockGetSubscriptionStatusUseCase,
            mockEnhancedSignOutUseCase,
            mockAuthRepository,
            mockAnalyticsService
        )
        advanceUntilIdle()

        // Verify the app didn't crash and state is still correct
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(testUserSettings, state.userSettings)
            assertEquals(testSubscriptionStatus, state.subscriptionStatus)
        }
    }

    @Test
    fun `state computed properties should work correctly`() = runTest {
        // Given
        viewModel = SettingsViewModel(
            mockGetUserSettingsUseCase,
            mockUpdateSettingsUseCase,
            mockGetSubscriptionStatusUseCase,
            mockEnhancedSignOutUseCase,
            mockAuthRepository,
            mockAnalyticsService
        )
        advanceUntilIdle()

        // When
        viewModel.uiState.test {
            val state = awaitItem()
            
            // Then
            assertTrue(state.shouldShowContent)
            assertFalse(state.shouldShowInitialLoading)
            assertFalse(state.shouldShowError)
            assertFalse(state.isAnyUpdateInProgress)
            assertFalse(state.currentThemeMode)
            assertTrue(state.currentNotificationSetting)
            assertEquals("Free", state.subscriptionDisplayName)
            assertFalse(state.hasPremiumAccess)
            assertFalse(state.isInTrialPeriod)
        }
    }

    @Test
    fun `authentication state changes should be handled correctly`() = runTest {
        // Given
        val authStateFlow = MutableStateFlow(testUser)
        every { mockAuthRepository.currentUser } returns authStateFlow
        
        viewModel = SettingsViewModel(
            mockGetUserSettingsUseCase,
            mockUpdateSettingsUseCase,
            mockGetSubscriptionStatusUseCase,
            mockEnhancedSignOutUseCase,
            mockAuthRepository,
            mockAnalyticsService
        )
        advanceUntilIdle()

        // When - user becomes unauthenticated
        authStateFlow.value = null
        advanceUntilIdle()

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals("User not authenticated", state.error)
        }
    }

    @Test
    fun `exception in loadSettings should be handled gracefully`() = runTest {
        // Given
        coEvery { mockGetUserSettingsUseCase(any()) } throws RuntimeException("Unexpected error")
        
        // When
        viewModel = SettingsViewModel(
            mockGetUserSettingsUseCase,
            mockUpdateSettingsUseCase,
            mockGetSubscriptionStatusUseCase,
            mockEnhancedSignOutUseCase,
            mockAuthRepository,
            mockAnalyticsService
        )
        advanceUntilIdle()

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals("Failed to load settings", state.error)
            assertFalse(state.isLoading)
        }
    }

    @Test
    fun `exception in updateDarkMode should be handled gracefully`() = runTest {
        // Given
        coEvery { mockUpdateSettingsUseCase.updateDarkMode(any(), any()) } throws RuntimeException("Unexpected error")
        
        viewModel = SettingsViewModel(
            mockGetUserSettingsUseCase,
            mockUpdateSettingsUseCase,
            mockGetSubscriptionStatusUseCase,
            mockEnhancedSignOutUseCase,
            mockAuthRepository,
            mockAnalyticsService
        )
        advanceUntilIdle()

        // When
        viewModel.onEvent(SettingsEvent.UpdateDarkMode(true))
        advanceUntilIdle()

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals("Failed to update dark mode", state.error)
            assertFalse(state.isUpdatingSettings)
        }
    }

    @Test
    fun `exception in enhancedSignOut should be handled gracefully`() = runTest {
        // Given
        coEvery { mockEnhancedSignOutUseCase() } throws RuntimeException("Unexpected error")
        
        viewModel = SettingsViewModel(
            mockGetUserSettingsUseCase,
            mockUpdateSettingsUseCase,
            mockGetSubscriptionStatusUseCase,
            mockEnhancedSignOutUseCase,
            mockAuthRepository,
            mockAnalyticsService
        )
        advanceUntilIdle()

        // When
        viewModel.onEvent(SettingsEvent.SignOutConfirmed)
        advanceUntilIdle()

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals("Failed to sign out", state.error)
            assertFalse(state.isSigningOut)
        }
    }
}