package com.example.liftrix.ui.home

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.liftrix.MainCoroutineRule
import com.example.liftrix.data.repository.WorkoutRepository
import com.example.liftrix.domain.model.CardData
import com.example.liftrix.domain.model.Workout
import com.example.liftrix.domain.model.WorkoutStats
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.repository.SocialRepository
import com.example.liftrix.domain.service.AnalyticsService
import com.example.liftrix.domain.usecase.GetWorkoutHistoryUseCase
import com.example.liftrix.ui.components.cards.Trend
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.Duration

/**
 * Integration tests for enhanced HomeViewModel card data functionality
 * Tests the new cardData StateFlow integration with existing ViewModels
 */
@ExperimentalCoroutinesApi
class HomeViewModelIntegrationTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private lateinit var homeViewModel: HomeViewModel
    private lateinit var mockWorkoutRepository: WorkoutRepository
    private lateinit var mockAuthRepository: AuthRepository
    private lateinit var mockAnalyticsService: AnalyticsService
    private lateinit var mockSocialRepository: SocialRepository
    private lateinit var mockGetWorkoutHistoryUseCase: GetWorkoutHistoryUseCase

    private val testUserId = "test-user-123"

    @Before
    fun setup() {
        mockWorkoutRepository = mockk(relaxed = true)
        mockAuthRepository = mockk(relaxed = true)
        mockAnalyticsService = mockk(relaxed = true)
        mockSocialRepository = mockk(relaxed = true)
        mockGetWorkoutHistoryUseCase = mockk(relaxed = true)

        every { mockAuthRepository.getCurrentUser()?.uid } returns testUserId
    }

    @Test
    fun `cardData emits correct stats cards with workout data`() = runTest {
        // Given
        val mockWorkoutStats = WorkoutStats(
            totalWorkouts = 12,
            currentStreak = 5,
            weeklyVolume = Duration.ofHours(4),
            averageWorkoutDuration = Duration.ofMinutes(45)
        )
        val mockRecentWorkouts = listOf(
            mockk<Workout> {
                every { name } returns "Push Day"
                every { getDuration() } returns Duration.ofMinutes(50)
            }
        )

        every { mockWorkoutRepository.getWorkoutStats(testUserId) } returns flowOf(mockWorkoutStats)
        every { mockWorkoutRepository.getRecentWorkouts(testUserId, any()) } returns flowOf(mockRecentWorkouts)

        // When
        homeViewModel = HomeViewModel(
            mockWorkoutRepository,
            mockAuthRepository,
            mockAnalyticsService,
            mockSocialRepository,
            mockGetWorkoutHistoryUseCase
        )

        // Then
        val cardData = homeViewModel.cardData.value
        
        // Should have 4 cards: total workouts, recent workout, streak, avg duration
        assertEquals(4, cardData.size)
        
        // Check total workouts card
        val totalWorkoutsCard = cardData.first { it is CardData.Stats && it.title == "Total Workouts" } as CardData.Stats
        assertEquals("12", totalWorkoutsCard.value)
        assertEquals("Completed", totalWorkoutsCard.subtitle)
        assertTrue(totalWorkoutsCard.trend is Trend.Positive)
        
        // Check recent workout card
        val recentWorkoutCard = cardData.first { it is CardData.Activity && it.title == "Recent Workout" } as CardData.Activity
        assertEquals("Push Day", recentWorkoutCard.subtitle)
        assertEquals("Today", recentWorkoutCard.trailing)
        
        // Check streak card
        val streakCard = cardData.first { it is CardData.Stats && it.title == "Current Streak" } as CardData.Stats
        assertEquals("5", streakCard.value)
        assertEquals("5 day streak", streakCard.subtitle)
        
        // Check average duration card
        val avgDurationCard = cardData.first { it is CardData.Stats && it.title == "Avg Duration" } as CardData.Stats
        assertEquals("45m", avgDurationCard.value)
        assertEquals("Per workout", avgDurationCard.subtitle)
    }

    @Test
    fun `cardData handles empty workout data gracefully`() = runTest {
        // Given
        val emptyWorkoutStats = WorkoutStats.EMPTY
        val emptyWorkouts = emptyList<Workout>()

        every { mockWorkoutRepository.getWorkoutStats(testUserId) } returns flowOf(emptyWorkoutStats)
        every { mockWorkoutRepository.getRecentWorkouts(testUserId, any()) } returns flowOf(emptyWorkouts)

        // When
        homeViewModel = HomeViewModel(
            mockWorkoutRepository,
            mockAuthRepository,
            mockAnalyticsService,
            mockSocialRepository,
            mockGetWorkoutHistoryUseCase
        )

        // Then
        val cardData = homeViewModel.cardData.value
        
        // Should still have 4 cards with empty state data
        assertEquals(4, cardData.size)
        
        // Check total workouts shows 0
        val totalWorkoutsCard = cardData.first { it is CardData.Stats && it.title == "Total Workouts" } as CardData.Stats
        assertEquals("0", totalWorkoutsCard.value)
        assertTrue(totalWorkoutsCard.trend is Trend.Neutral)
        
        // Check recent workout shows no data
        val recentWorkoutCard = cardData.first { it is CardData.Activity && it.title == "Recent Workout" } as CardData.Activity
        assertEquals("No recent workouts", recentWorkoutCard.subtitle)
        assertFalse(recentWorkoutCard.showChevron)
        
        // Check streak shows 0
        val streakCard = cardData.first { it is CardData.Stats && it.title == "Current Streak" } as CardData.Stats
        assertEquals("0", streakCard.value)
        assertEquals("No current streak", streakCard.subtitle)
    }

    @Test
    fun `cardData maintains backward compatibility with existing HomeViewModel functions`() = runTest {
        // Given
        every { mockWorkoutRepository.getWorkoutStats(testUserId) } returns flowOf(WorkoutStats.EMPTY)
        every { mockWorkoutRepository.getRecentWorkouts(testUserId, any()) } returns flowOf(emptyList())

        // When
        homeViewModel = HomeViewModel(
            mockWorkoutRepository,
            mockAuthRepository,
            mockAnalyticsService,
            mockSocialRepository,
            mockGetWorkoutHistoryUseCase
        )

        // Then - verify existing functions still work
        homeViewModel.loadHomeData()
        homeViewModel.refreshData()
        
        // Verify repository calls happened as expected
        verify { mockWorkoutRepository.getRecentWorkouts(testUserId, any()) }
        verify { mockWorkoutRepository.getWorkoutStats(testUserId) }
        
        // Verify UI state is accessible
        assertNotNull(homeViewModel.uiState)
        assertNotNull(homeViewModel.cardData)
    }

    @Test
    fun `cardData shows significant streak trend for 7+ day streaks`() = runTest {
        // Given
        val mockWorkoutStats = WorkoutStats(
            totalWorkouts = 15,
            currentStreak = 10, // Significant streak
            weeklyVolume = Duration.ofHours(5),
            averageWorkoutDuration = Duration.ofMinutes(60)
        )

        every { mockWorkoutRepository.getWorkoutStats(testUserId) } returns flowOf(mockWorkoutStats)
        every { mockWorkoutRepository.getRecentWorkouts(testUserId, any()) } returns flowOf(emptyList())

        // When
        homeViewModel = HomeViewModel(
            mockWorkoutRepository,
            mockAuthRepository,
            mockAnalyticsService,
            mockSocialRepository,
            mockGetWorkoutHistoryUseCase
        )

        // Then
        val streakCard = homeViewModel.cardData.value
            .first { it is CardData.Stats && it.title == "Current Streak" } as CardData.Stats
        
        assertEquals("10", streakCard.value)
        assertEquals("10 day streak", streakCard.subtitle)
        assertTrue(streakCard.trend is Trend.Positive)
        assertEquals("strong", (streakCard.trend as Trend.Positive).label)
    }
} 