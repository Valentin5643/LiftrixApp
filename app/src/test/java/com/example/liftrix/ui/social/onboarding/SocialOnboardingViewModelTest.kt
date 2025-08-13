package com.example.liftrix.ui.social.onboarding

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.social.SocialProfile
import com.example.liftrix.domain.model.social.SocialPrivacySettings
import com.example.liftrix.domain.usecase.common.ErrorHandler
import com.example.liftrix.domain.usecase.social.CheckUsernameAvailabilityUseCase
import com.example.liftrix.domain.usecase.social.CreateSocialProfileUseCase
import com.example.liftrix.domain.usecase.social.UpdateSocialPrivacySettingsUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for SocialOnboardingViewModel.
 * Tests onboarding flow state management, validation, and user interactions.
 * Part of social infrastructure testing from SPEC-20250113-social-infrastructure.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SocialOnboardingViewModelTest {

    private lateinit var viewModel: SocialOnboardingViewModel
    private val createSocialProfileUseCase: CreateSocialProfileUseCase = mockk()
    private val checkUsernameAvailabilityUseCase: CheckUsernameAvailabilityUseCase = mockk()
    private val updateSocialPrivacySettingsUseCase: UpdateSocialPrivacySettingsUseCase = mockk()
    private val errorHandler: ErrorHandler = mockk(relaxed = true)

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        viewModel = SocialOnboardingViewModel(
            createSocialProfileUseCase = createSocialProfileUseCase,
            checkUsernameAvailabilityUseCase = checkUsernameAvailabilityUseCase,
            updateSocialPrivacySettingsUseCase = updateSocialPrivacySettingsUseCase,
            errorHandler = errorHandler
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is correctly set`() {
        val state = viewModel.uiState.value
        
        assertEquals(SocialOnboardingStep.PRIVACY_INTRO, state.currentStep)
        assertEquals("", state.username)
        assertEquals("", state.displayName)
        assertEquals("", state.bio)
        assertFalse(state.allowFollowRequests)
        assertFalse(state.workoutSharingEnabled)
        assertFalse(state.gymBuddiesEnabled)
        assertTrue(state.showAchievements)
        assertNull(state.usernameError)
        assertNull(state.displayNameError)
        assertFalse(state.isLoading)
        assertFalse(state.isCompleted)
        assertFalse(state.canCreateProfile)
    }

    @Test
    fun `navigateNext advances through steps correctly`() {
        // Start at PRIVACY_INTRO
        assertEquals(SocialOnboardingStep.PRIVACY_INTRO, viewModel.uiState.value.currentStep)
        
        // Navigate to BENEFITS
        viewModel.handleEvent(SocialOnboardingEvent.NavigateNext)
        assertEquals(SocialOnboardingStep.BENEFITS, viewModel.uiState.value.currentStep)
        
        // Navigate to PROFILE_CREATION
        viewModel.handleEvent(SocialOnboardingEvent.NavigateNext)
        assertEquals(SocialOnboardingStep.PROFILE_CREATION, viewModel.uiState.value.currentStep)
        
        // Navigate to PRIVACY_SETTINGS
        viewModel.handleEvent(SocialOnboardingEvent.NavigateNext)
        assertEquals(SocialOnboardingStep.PRIVACY_SETTINGS, viewModel.uiState.value.currentStep)
        
        // Navigate to COMPLETION
        viewModel.handleEvent(SocialOnboardingEvent.NavigateNext)
        assertEquals(SocialOnboardingStep.COMPLETION, viewModel.uiState.value.currentStep)
        
        // Cannot navigate past COMPLETION
        viewModel.handleEvent(SocialOnboardingEvent.NavigateNext)
        assertEquals(SocialOnboardingStep.COMPLETION, viewModel.uiState.value.currentStep)
    }

    @Test
    fun `navigateBack moves through steps correctly`() {
        // Move to COMPLETION first
        repeat(4) { viewModel.handleEvent(SocialOnboardingEvent.NavigateNext) }
        assertEquals(SocialOnboardingStep.COMPLETION, viewModel.uiState.value.currentStep)
        
        // Navigate back to PRIVACY_SETTINGS
        viewModel.handleEvent(SocialOnboardingEvent.NavigateBack)
        assertEquals(SocialOnboardingStep.PRIVACY_SETTINGS, viewModel.uiState.value.currentStep)
        
        // Navigate back to PROFILE_CREATION
        viewModel.handleEvent(SocialOnboardingEvent.NavigateBack)
        assertEquals(SocialOnboardingStep.PROFILE_CREATION, viewModel.uiState.value.currentStep)
        
        // Navigate back to BENEFITS
        viewModel.handleEvent(SocialOnboardingEvent.NavigateBack)
        assertEquals(SocialOnboardingStep.BENEFITS, viewModel.uiState.value.currentStep)
        
        // Navigate back to PRIVACY_INTRO
        viewModel.handleEvent(SocialOnboardingEvent.NavigateBack)
        assertEquals(SocialOnboardingStep.PRIVACY_INTRO, viewModel.uiState.value.currentStep)
        
        // Cannot navigate before PRIVACY_INTRO
        viewModel.handleEvent(SocialOnboardingEvent.NavigateBack)
        assertEquals(SocialOnboardingStep.PRIVACY_INTRO, viewModel.uiState.value.currentStep)
    }

    @Test
    fun `updateUsername validates and enables profile creation`() {
        val validUsername = "testuser"
        val validDisplayName = "Test User"
        
        // Update username
        viewModel.handleEvent(SocialOnboardingEvent.UpdateUsername(validUsername))
        viewModel.handleEvent(SocialOnboardingEvent.UpdateDisplayName(validDisplayName))
        
        val state = viewModel.uiState.value
        assertEquals(validUsername, state.username)
        assertEquals(validDisplayName, state.displayName)
        assertNull(state.usernameError)
        assertNull(state.displayNameError)
        assertTrue(state.canCreateProfile) // Both username and display name are valid
    }

    @Test
    fun `updateDisplayName validates input`() {
        val validDisplayName = "Test User"
        
        viewModel.handleEvent(SocialOnboardingEvent.UpdateDisplayName(validDisplayName))
        
        val state = viewModel.uiState.value
        assertEquals(validDisplayName, state.displayName)
        assertNull(state.displayNameError)
    }

    @Test
    fun `updateDisplayName fails with empty name`() {
        viewModel.handleEvent(SocialOnboardingEvent.UpdateDisplayName(""))
        
        val state = viewModel.uiState.value
        assertEquals("Display name cannot be empty", state.displayNameError)
        assertFalse(state.canCreateProfile)
    }

    @Test
    fun `updateBio limits to 500 characters`() {
        val longBio = "a".repeat(600)
        val expectedBio = "a".repeat(500)
        
        viewModel.handleEvent(SocialOnboardingEvent.UpdateBio(longBio))
        
        val state = viewModel.uiState.value
        assertEquals(expectedBio, state.bio)
    }

    @Test
    fun `username availability check debounces correctly`() = runTest {
        coEvery { checkUsernameAvailabilityUseCase("testuser") } returns LiftrixResult.success(true)
        
        // Update username multiple times quickly
        viewModel.handleEvent(SocialOnboardingEvent.UpdateUsername("t"))
        viewModel.handleEvent(SocialOnboardingEvent.UpdateUsername("te"))
        viewModel.handleEvent(SocialOnboardingEvent.UpdateUsername("tes"))
        viewModel.handleEvent(SocialOnboardingEvent.UpdateUsername("test"))
        viewModel.handleEvent(SocialOnboardingEvent.UpdateUsername("testuser"))
        
        // Advance time past debounce period
        advanceTimeBy(600)
        
        // Should only check availability once for final username
        coVerify(exactly = 1) { checkUsernameAvailabilityUseCase("testuser") }
    }

    @Test
    fun `username availability check sets error when taken`() = runTest {
        coEvery { checkUsernameAvailabilityUseCase("takenuser") } returns LiftrixResult.success(false)
        
        viewModel.handleEvent(SocialOnboardingEvent.UpdateUsername("takenuser"))
        advanceTimeBy(600)
        
        val state = viewModel.uiState.value
        assertEquals("Username 'takenuser' is already taken", state.usernameError)
        assertFalse(state.canCreateProfile)
    }

    @Test
    fun `createProfile succeeds with valid data`() = runTest {
        val mockProfile = mockk<SocialProfile>()
        coEvery { 
            createSocialProfileUseCase("testuser", "Test User", "Test bio")
        } returns LiftrixResult.success(mockProfile)
        
        // Set up valid state
        viewModel.handleEvent(SocialOnboardingEvent.UpdateUsername("testuser"))
        viewModel.handleEvent(SocialOnboardingEvent.UpdateDisplayName("Test User"))
        viewModel.handleEvent(SocialOnboardingEvent.UpdateBio("Test bio"))
        
        // Create profile
        viewModel.handleEvent(SocialOnboardingEvent.CreateProfile)
        testDispatcher.scheduler.runCurrent()
        
        val state = viewModel.uiState.value
        assertEquals(SocialOnboardingStep.PRIVACY_SETTINGS, state.currentStep)
        assertFalse(state.isLoading)
        
        coVerify { createSocialProfileUseCase("testuser", "Test User", "Test bio") }
    }

    @Test
    fun `createProfile handles validation errors`() = runTest {
        coEvery { 
            createSocialProfileUseCase(any(), any(), any())
        } returns LiftrixResult.failure(
            LiftrixError.ValidationError(
                field = "username",
                violations = listOf("Invalid username format"),
                analyticsContext = mapOf("operation" to "VALIDATE_USERNAME")
            )
        )
        
        viewModel.handleEvent(SocialOnboardingEvent.UpdateUsername("testuser"))
        viewModel.handleEvent(SocialOnboardingEvent.UpdateDisplayName("Test User"))
        viewModel.handleEvent(SocialOnboardingEvent.CreateProfile)
        testDispatcher.scheduler.runCurrent()
        
        val state = viewModel.uiState.value
        assertEquals("Invalid username", state.usernameError)
        assertEquals(SocialOnboardingStep.PROFILE_CREATION, state.currentStep) // Still on creation step
    }

    @Test
    fun `savePrivacySettings creates correct privacy settings`() = runTest {
        val mockSettings = mockk<SocialPrivacySettings>()
        coEvery { updateSocialPrivacySettingsUseCase(any()) } returns LiftrixResult.success(mockSettings)
        
        // Set up privacy preferences
        viewModel.handleEvent(SocialOnboardingEvent.UpdateAllowFollowRequests(true))
        viewModel.handleEvent(SocialOnboardingEvent.UpdateWorkoutSharing(true))
        viewModel.handleEvent(SocialOnboardingEvent.UpdateGymBuddies(false))
        viewModel.handleEvent(SocialOnboardingEvent.UpdateShowAchievements(true))
        
        // Save settings
        viewModel.handleEvent(SocialOnboardingEvent.SavePrivacySettings)
        testDispatcher.scheduler.runCurrent()
        
        val state = viewModel.uiState.value
        assertEquals(SocialOnboardingStep.COMPLETION, state.currentStep)
        
        coVerify { 
            updateSocialPrivacySettingsUseCase(match { settings ->
                settings.socialEnabled && 
                settings.allowFollowRequests && 
                settings.workoutSharingEnabled && 
                !settings.gymBuddiesEnabled && 
                settings.showAchievements
            })
        }
    }

    @Test
    fun `privacy toggles update state correctly`() {
        viewModel.handleEvent(SocialOnboardingEvent.UpdateAllowFollowRequests(true))
        assertTrue(viewModel.uiState.value.allowFollowRequests)
        
        viewModel.handleEvent(SocialOnboardingEvent.UpdateWorkoutSharing(true))
        assertTrue(viewModel.uiState.value.workoutSharingEnabled)
        
        viewModel.handleEvent(SocialOnboardingEvent.UpdateGymBuddies(true))
        assertTrue(viewModel.uiState.value.gymBuddiesEnabled)
        
        viewModel.handleEvent(SocialOnboardingEvent.UpdateShowAchievements(false))
        assertFalse(viewModel.uiState.value.showAchievements)
    }

    @Test
    fun `completeOnboarding sets completion flag`() {
        viewModel.handleEvent(SocialOnboardingEvent.CompleteOnboarding)
        
        val state = viewModel.uiState.value
        assertTrue(state.isCompleted)
    }

    @Test
    fun `profile validation requires minimum username and display name`() {
        // Initially cannot create profile
        assertFalse(viewModel.uiState.value.canCreateProfile)
        
        // Username too short
        viewModel.handleEvent(SocialOnboardingEvent.UpdateUsername("ab"))
        viewModel.handleEvent(SocialOnboardingEvent.UpdateDisplayName("Test User"))
        assertFalse(viewModel.uiState.value.canCreateProfile)
        
        // Valid username, empty display name
        viewModel.handleEvent(SocialOnboardingEvent.UpdateUsername("testuser"))
        viewModel.handleEvent(SocialOnboardingEvent.UpdateDisplayName(""))
        assertFalse(viewModel.uiState.value.canCreateProfile)
        
        // Both valid
        viewModel.handleEvent(SocialOnboardingEvent.UpdateDisplayName("Test User"))
        assertTrue(viewModel.uiState.value.canCreateProfile)
    }

    @Test
    fun `loading states are managed correctly during operations`() = runTest {
        coEvery { createSocialProfileUseCase(any(), any(), any()) } coAnswers {
            // Simulate delay to test loading state
            kotlinx.coroutines.delay(100)
            LiftrixResult.success(mockk<SocialProfile>())
        }
        
        viewModel.handleEvent(SocialOnboardingEvent.UpdateUsername("testuser"))
        viewModel.handleEvent(SocialOnboardingEvent.UpdateDisplayName("Test User"))
        viewModel.handleEvent(SocialOnboardingEvent.CreateProfile)
        
        // Should be loading immediately
        assertTrue(viewModel.uiState.value.isLoading)
        
        // Complete the operation
        testDispatcher.scheduler.runCurrent()
        
        // Should no longer be loading
        assertFalse(viewModel.uiState.value.isLoading)
    }
}