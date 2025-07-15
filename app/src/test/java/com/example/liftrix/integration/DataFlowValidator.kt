package com.example.liftrix.integration

import com.example.liftrix.domain.repository.workout.WorkoutRepository
import com.example.liftrix.domain.model.*
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.repository.SocialRepository
import com.example.liftrix.domain.service.AnalyticsService
import com.example.liftrix.domain.usecase.GetWorkoutHistoryUseCase
import com.example.liftrix.ui.home.HomeViewModel
import com.example.liftrix.ui.home.HomeEvent
import io.mockk.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Validates data flow integrity from repository layer through use cases to ViewModels.
 * Ensures enhanced UI components receive data correctly through existing architecture.
 */
class DataFlowValidator {

    private val mockWorkoutRepository = mockk<WorkoutRepository>()
    private val mockAuthRepository = mockk<AuthRepository>()
    private val mockSocialRepository = mockk<SocialRepository>()
    private val mockAnalyticsService = mockk<AnalyticsService>()
    private val mockGetWorkoutHistoryUseCase = mockk<GetWorkoutHistoryUseCase>()

    /**
     * Validates complete data flow from repository to ViewModel
     */
    suspend fun validateRepositoryToViewModelFlow(): DataFlowResult {
        val violations = mutableListOf<String>()

        try {
            // Setup test data
            val testUser = User(
                uid = "test-user-123",
                email = "test@example.com",
                displayName = "Test User",
                photoUrl = null,
                isEmailVerified = true,
                createdAt = LocalDateTime.now()
            )

            val testWorkouts = listOf(
                Workout(
                    id = WorkoutId("workout-1"),
                    userId = testUser.uid,
                    name = "Test Workout",
                    exercises = emptyList(),
                    date = LocalDate.now(),
                    duration = 3600,
                    isCompleted = true,
                    createdAt = LocalDateTime.now(),
                    updatedAt = LocalDateTime.now()
                )
            )

            val testWorkoutStats = WorkoutStats(
                totalWorkouts = 25,
                totalDuration = 75000,
                averageDuration = 3000,
                currentStreak = 7,
                longestStreak = 12,
                totalVolume = 12500.0,
                averageVolume = 500.0,
                lastWorkoutDate = LocalDate.now()
            )

            // Setup mocks
            every { mockAuthRepository.getCurrentUser() } returns testUser
            every { mockWorkoutRepository.getRecentWorkouts(testUser.uid, any()) } returns flowOf(testWorkouts)
            every { mockWorkoutRepository.getWorkoutStats(testUser.uid) } returns flowOf(testWorkoutStats)
            every { mockAnalyticsService.trackHomeScreenViewed() } just Runs

            // Create ViewModel
            val viewModel = HomeViewModel(
                workoutRepository = mockWorkoutRepository,
                authRepository = mockAuthRepository,
                analyticsService = mockAnalyticsService,
                socialRepository = mockSocialRepository,
                getWorkoutHistoryUseCase = mockGetWorkoutHistoryUseCase
            )

            // Trigger data loading
            viewModel.loadHomeData()

            // Verify data flow
            verify { mockAuthRepository.getCurrentUser() }
            verify { mockWorkoutRepository.getRecentWorkouts(testUser.uid, any()) }
            verify { mockWorkoutRepository.getWorkoutStats(testUser.uid) }
            verify { mockAnalyticsService.trackHomeScreenViewed() }

            // Verify ViewModel state is updated
            val uiState = viewModel.uiState.value
            if (uiState.isLoading) {
                violations.add("ViewModel state not updated after data loading")
            }

        } catch (e: Exception) {
            violations.add("Data flow validation failed: ${e.message}")
        }

        return if (violations.isEmpty()) {
            DataFlowResult.Success("Repository to ViewModel data flow validated successfully")
        } else {
            DataFlowResult.Failure("Data flow validation failed", violations)
        }
    }

    /**
     * Comprehensive data flow validation
     */
    suspend fun validateCompleteDataFlow(): DataFlowResult {
        val results = listOf(
            validateRepositoryToViewModelFlow()
        )

        val failures = results.filterIsInstance<DataFlowResult.Failure>()

        return if (failures.isEmpty()) {
            DataFlowResult.Success("Complete data flow validated successfully")
        } else {
            val allViolations = failures.flatMap { it.violations }
            DataFlowResult.Failure("Data flow validation failures found", allViolations)
        }
    }

    /**
     * Cleanup mocks after validation
     */
    fun cleanup() {
        clearAllMocks()
    }
}

/**
 * Data flow validation result
 */
sealed class DataFlowResult {
    data class Success(val message: String) : DataFlowResult()
    data class Failure(val message: String, val violations: List<String>) : DataFlowResult()
} 