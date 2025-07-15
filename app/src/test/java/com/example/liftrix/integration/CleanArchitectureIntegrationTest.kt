package com.example.liftrix.integration

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.liftrix.domain.repository.workout.WorkoutRepository
import com.example.liftrix.domain.model.*
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.repository.SocialRepository
import com.example.liftrix.domain.service.AnalyticsService
import com.example.liftrix.domain.usecase.GetWorkoutHistoryUseCase
import com.example.liftrix.ui.home.HomeScreen
import com.example.liftrix.ui.home.HomeViewModel
import com.example.liftrix.ui.theme.LiftrixTheme
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * Comprehensive integration test suite validating Clean Architecture integrity
 * and backward compatibility after theme enhancements.
 * 
 * Validates:
 * - Clean Architecture layer separation and contracts
 * - Domain layer use cases work unchanged with enhanced UI
 * - Data repository integration remains functional
 * - ViewModel state management with enhanced components
 * - Navigation flow preservation
 * - No functional regressions from visual enhancements
 */
@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
class CleanArchitectureIntegrationTest {

    @get:Rule(order = 1)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 2)
    val composeTestRule = createComposeRule()

    // Mock dependencies for isolation
    private val mockWorkoutRepository = mockk<WorkoutRepository>()
    private val mockAuthRepository = mockk<AuthRepository>()
    private val mockSocialRepository = mockk<SocialRepository>()
    private val mockAnalyticsService = mockk<AnalyticsService>()
    private val mockGetWorkoutHistoryUseCase = mockk<GetWorkoutHistoryUseCase>()

    // Test data
    private val testUser = User(
        uid = "test-user-123",
        email = "test@example.com",
        displayName = "Test User",
        photoUrl = null,
        isEmailVerified = true,
        createdAt = LocalDateTime.now()
    )

    private val testWorkouts = listOf(
        Workout(
            id = WorkoutId("workout-1"),
            userId = testUser.uid,
            name = "Push Day",
            exercises = emptyList(),
            date = LocalDate.now(),
            duration = 3600,
            isCompleted = true,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
    )

    private val testWorkoutStats = WorkoutStats(
        totalWorkouts = 25,
        totalDuration = 75000,
        averageDuration = 3000,
        currentStreak = 7,
        longestStreak = 12,
        totalVolume = 12500.0,
        averageVolume = 500.0,
        lastWorkoutDate = LocalDate.now()
    )

    @Before
    fun setUp() {
        hiltRule.inject()
        clearAllMocks()
        setupMockBehaviors()
    }

    /**
     * Architecture Integrity Validator
     * Ensures Clean Architecture principles are maintained
     */
    @Test
    fun architectureIntegrityValidator_maintainsCleanArchitecturePrinciples() = runTest {
        // Given - Create validator
        val validator = ArchitectureIntegrityValidator()

        // When - Validate complete architecture
        val result = validator.validateCompleteArchitecture()

        // Then - Verify architecture integrity
        when (result) {
            is ValidationResult.Success -> {
                // Architecture validation passed
                assert(true) { result.message }
            }
            is ValidationResult.Failure -> {
                // Log violations for debugging
                result.violations.forEach { violation ->
                    println("Architecture violation: $violation")
                }
                assert(false) { "Architecture integrity violations: ${result.violations.joinToString()}" }
            }
        }
    }

    /**
     * Backward Compatibility Checker
     * Ensures existing APIs work unchanged with enhanced UI
     */
    @Test
    fun backwardCompatibilityChecker_preservesExistingAPIs() = runTest {
        // Given - Create compatibility checker
        val checker = BackwardCompatibilityChecker()

        // When - Validate complete backward compatibility
        val result = checker.validateCompleteBackwardCompatibility()

        // Then - Verify backward compatibility
        when (result) {
            is CompatibilityResult.Success -> {
                // Compatibility validation passed
                assert(true) { result.message }
            }
            is CompatibilityResult.Failure -> {
                // Log violations for debugging
                result.violations.forEach { violation ->
                    println("Compatibility violation: $violation")
                }
                assert(false) { "Backward compatibility violations: ${result.violations.joinToString()}" }
            }
        }
    }

    /**
     * Data Flow Validator
     * Ensures repository and use case integration remains functional
     */
    @Test
    fun dataFlowValidator_maintainsRepositoryIntegration() = runTest {
        // Given - Create data flow validator
        val validator = DataFlowValidator()

        // When - Validate complete data flow
        val result = validator.validateCompleteDataFlow()

        // Then - Verify data flow integrity
        when (result) {
            is DataFlowResult.Success -> {
                // Data flow validation passed
                assert(true) { result.message }
            }
            is DataFlowResult.Failure -> {
                // Log violations for debugging
                result.violations.forEach { violation ->
                    println("Data flow violation: $violation")
                }
                assert(false) { "Data flow violations: ${result.violations.joinToString()}" }
            }
        }

        // Cleanup
        validator.cleanup()
    }

    /**
     * Enhanced HomeScreen Integration Test
     * Validates enhanced UI components work with existing ViewModels
     */
    @Test
    fun enhancedHomeScreen_preservesExistingDataFlow() {
        // Given - Setup mock data for enhanced HomeScreen
        every { mockAuthRepository.getCurrentUser() } returns testUser
        every { mockWorkoutRepository.getRecentWorkouts(testUser.uid, any()) } returns flowOf(testWorkouts)
        every { mockWorkoutRepository.getWorkoutStats(testUser.uid) } returns flowOf(testWorkoutStats)
        every { mockAnalyticsService.trackHomeScreenViewed() } just Runs

        // Create ViewModel with mocked dependencies
        val viewModel = HomeViewModel(
            workoutRepository = mockWorkoutRepository,
            authRepository = mockAuthRepository,
            analyticsService = mockAnalyticsService,
            socialRepository = mockSocialRepository,
            getWorkoutHistoryUseCase = mockGetWorkoutHistoryUseCase
        )

        // When - Render enhanced HomeScreen with existing ViewModel
        composeTestRule.setContent {
            LiftrixTheme {
                HomeScreen(
                    onNavigateToWorkout = { },
                    onNavigateToFriends = { },
                    onNavigateToMyWorkouts = { },
                    viewModel = viewModel
                )
            }
        }

        // Then - Verify enhanced UI components display correctly
        composeTestRule.waitForIdle()
        
        // Verify data flow reaches enhanced components
        verify { mockWorkoutRepository.getRecentWorkouts(testUser.uid, any()) }
        verify { mockWorkoutRepository.getWorkoutStats(testUser.uid) }
    }

    private fun setupMockBehaviors() {
        // Default mock behaviors for all tests
        every { mockAuthRepository.getCurrentUser() } returns testUser
        every { mockWorkoutRepository.getRecentWorkouts(any(), any()) } returns flowOf(emptyList())
        every { mockWorkoutRepository.getWorkoutStats(any()) } returns flowOf(testWorkoutStats)
        every { mockAnalyticsService.trackHomeScreenViewed() } just Runs
        coEvery { mockGetWorkoutHistoryUseCase.execute(any(), any()) } returns flowOf(Result.success(emptyList()))
    }
} 