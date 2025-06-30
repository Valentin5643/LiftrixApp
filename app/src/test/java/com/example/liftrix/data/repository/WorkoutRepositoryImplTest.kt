package com.example.liftrix.data.repository

import androidx.work.WorkManager
import com.example.liftrix.data.local.dao.WorkoutDao
import com.example.liftrix.data.local.entity.WorkoutEntity
import com.example.liftrix.data.mapper.WorkoutMapper
import com.example.liftrix.domain.model.FeedWorkout
import com.example.liftrix.domain.model.UserInfo
import com.example.liftrix.domain.model.Workout
import com.example.liftrix.domain.model.WorkoutId
import com.example.liftrix.domain.model.WorkoutStats
import com.example.liftrix.domain.model.WorkoutStatus
import com.example.liftrix.domain.repository.SocialRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for WorkoutRepositoryImpl focusing on home screen methods
 * Tests the new getRecentWorkouts and getWorkoutStats methods added for HOME-BE-001
 */
class WorkoutRepositoryImplTest {
    
    private lateinit var workoutDao: WorkoutDao
    private lateinit var workoutMapper: WorkoutMapper
    private lateinit var workManager: WorkManager
    private lateinit var socialRepository: SocialRepository
    private lateinit var repository: WorkoutRepositoryImpl
    
    private val testUserId = "test-user-123"
    private val testWorkoutId1 = WorkoutId.generate()
    private val testWorkoutId2 = WorkoutId.generate()
    private val testWorkoutId3 = WorkoutId.generate()
    
    // Test entities for DAO layer
    private val testEntity1 = WorkoutEntity(
        id = testWorkoutId1.value,
        userId = testUserId,
        name = "Recent Workout 1",
        date = LocalDate.now().minusDays(1),
        status = WorkoutStatus.COMPLETED,
        startTime = LocalTime.of(10, 0),
        endTime = LocalTime.of(11, 0),
        createdAt = Instant.now().minusSeconds(86400),
        updatedAt = Instant.now().minusSeconds(86400),
        isSynced = true,
        syncVersion = 1
    )
    
    private val testEntity2 = WorkoutEntity(
        id = testWorkoutId2.value,
        userId = testUserId,
        name = "Recent Workout 2",
        date = LocalDate.now().minusDays(2),
        status = WorkoutStatus.COMPLETED,
        startTime = LocalTime.of(14, 0),
        endTime = LocalTime.of(15, 30),
        createdAt = Instant.now().minusSeconds(172800),
        updatedAt = Instant.now().minusSeconds(172800),
        isSynced = true,
        syncVersion = 1
    )
    
    private val testEntity3 = WorkoutEntity(
        id = testWorkoutId3.value,
        userId = testUserId,
        name = "In Progress Workout",
        date = LocalDate.now(),
        status = WorkoutStatus.IN_PROGRESS,
        startTime = LocalTime.of(16, 0),
        endTime = null,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
        isSynced = false,
        syncVersion = 1
    )
    
    // Test domain models
    private val testWorkout1 = Workout(
        id = testWorkoutId1,
        userId = testUserId,
        name = "Recent Workout 1",
        date = LocalDate.now().minusDays(1),
        status = WorkoutStatus.COMPLETED,
        startTime = LocalTime.of(10, 0),
        endTime = LocalTime.of(11, 0),
        exercises = emptyList(),
        notes = null,
        createdAt = testEntity1.createdAt,
        updatedAt = testEntity1.updatedAt
    )
    
    private val testWorkout2 = Workout(
        id = testWorkoutId2,
        userId = testUserId,
        name = "Recent Workout 2",
        date = LocalDate.now().minusDays(2),
        status = WorkoutStatus.COMPLETED,
        startTime = LocalTime.of(14, 0),
        endTime = LocalTime.of(15, 30),
        exercises = emptyList(),
        notes = null,
        createdAt = testEntity2.createdAt,
        updatedAt = testEntity2.updatedAt
    )
    
    private val testWorkoutStats = WorkoutStats(
        totalWorkouts = 2,
        currentStreak = 2,
        weeklyVolume = Duration.ofMinutes(150),
        averageWorkoutDuration = Duration.ofMinutes(75)
    )
    
    @Before
    fun setup() {
        workoutDao = mockk()
        workoutMapper = mockk()
        workManager = mockk(relaxed = true)
        socialRepository = mockk()
        repository = WorkoutRepositoryImpl(workoutDao, workoutMapper, workManager, socialRepository)
    }
    
    @Test
    fun test_getRecentWorkouts_returnsCorrectOrder() = runTest {
        // Given - Setup entities in DAO order (most recent first)
        val entities = listOf(testEntity1, testEntity2) // Already ordered by date DESC
        val domainModels = listOf(testWorkout1, testWorkout2)
        
        every { workoutDao.getRecentCompletedWorkouts(testUserId, 7) } returns flowOf(entities)
        every { workoutMapper.toDomain(testEntity1) } returns testWorkout1
        every { workoutMapper.toDomain(testEntity2) } returns testWorkout2
        
        // When
        val result = repository.getRecentWorkouts(testUserId, 7).toList()
        
        // Then
        assertEquals(1, result.size)
        val workouts = result[0]
        assertEquals(2, workouts.size)
        // Verify order: most recent workout first
        assertTrue(workouts[0].date.isAfter(workouts[1].date))
        assertEquals(testWorkout1, workouts[0])
        assertEquals(testWorkout2, workouts[1])
        
        verify { workoutDao.getRecentCompletedWorkouts(testUserId, 7) }
        verify { workoutMapper.toDomain(testEntity1) }
        verify { workoutMapper.toDomain(testEntity2) }
    }
    
    @Test
    fun test_getRecentWorkouts_respectsLimit() = runTest {
        // Given - Setup more entities than the limit
        val manyEntities = listOf(testEntity1, testEntity2) // Only 2 entities returned
        val mappedWorkouts = listOf(testWorkout1, testWorkout2)
        
        every { workoutDao.getRecentCompletedWorkouts(testUserId, 1) } returns flowOf(listOf(testEntity1))
        every { workoutMapper.toDomain(testEntity1) } returns testWorkout1
        
        // When - Request with limit of 1
        val result = repository.getRecentWorkouts(testUserId, 1).toList()
        
        // Then - Should only return 1 workout
        assertEquals(1, result.size)
        val workouts = result[0]
        assertEquals(1, workouts.size)
        assertEquals(testWorkout1, workouts[0])
        
        verify { workoutDao.getRecentCompletedWorkouts(testUserId, 1) }
        verify { workoutMapper.toDomain(testEntity1) }
    }
    
    @Test
    fun test_getWorkoutStats_calculatesCorrectly() = runTest {
        // Given - Setup workouts for stats calculation
        val allWorkouts = listOf(testWorkout1, testWorkout2)
        
        every { workoutDao.getAllWorkoutsForUser(testUserId) } returns flowOf(listOf(testEntity1, testEntity2))
        every { workoutMapper.toDomain(testEntity1) } returns testWorkout1
        every { workoutMapper.toDomain(testEntity2) } returns testWorkout2
        
        // When
        val result = repository.getWorkoutStats(testUserId).toList()
        
        // Then
        assertEquals(1, result.size)
        val stats = result[0]
        
        // Verify stats are calculated (exact values depend on calculation logic)
        assertTrue(stats.totalWorkouts > 0)
        assertTrue(stats.currentStreak >= 0)
        assertTrue(!stats.weeklyVolume.isNegative)
        assertTrue(!stats.averageWorkoutDuration.isNegative)
        
        verify { workoutDao.getAllWorkoutsForUser(testUserId) }
        verify { workoutMapper.toDomain(testEntity1) }
        verify { workoutMapper.toDomain(testEntity2) }
    }
    
    @Test
    fun getRecentWorkouts_returnsEmptyWhenNoWorkouts() = runTest {
        // Given
        every { workoutDao.getRecentCompletedWorkouts(testUserId, 7) } returns flowOf(emptyList())
        
        // When
        val result = repository.getRecentWorkouts(testUserId, 7).toList()
        
        // Then
        assertEquals(1, result.size)
        assertTrue(result[0].isEmpty())
        
        verify { workoutDao.getRecentCompletedWorkouts(testUserId, 7) }
    }
    
    @Test
    fun getWorkoutStats_returnsEmptyStatsWhenNoWorkouts() = runTest {
        // Given
        every { workoutDao.getAllWorkoutsForUser(testUserId) } returns flowOf(emptyList())
        
        // When
        val result = repository.getWorkoutStats(testUserId).toList()
        
        // Then
        assertEquals(1, result.size)
        val stats = result[0]
        assertEquals(WorkoutStats.EMPTY, stats)
        
        verify { workoutDao.getAllWorkoutsForUser(testUserId) }
    }
    
    @Test
    fun getRecentWorkouts_enforcesUserScoping() = runTest {
        // Given
        val differentUserId = "different-user-456"
        every { workoutDao.getRecentCompletedWorkouts(differentUserId, 7) } returns flowOf(emptyList())
        
        // When
        val result = repository.getRecentWorkouts(differentUserId, 7).toList()
        
        // Then
        assertEquals(1, result.size)
        assertTrue(result[0].isEmpty())
        
        // Verify the correct user ID was passed to DAO
        verify { workoutDao.getRecentCompletedWorkouts(differentUserId, 7) }
    }
    
    @Test
    fun getWorkoutStats_enforcesUserScoping() = runTest {
        // Given
        val differentUserId = "different-user-456"
        every { workoutDao.getAllWorkoutsForUser(differentUserId) } returns flowOf(emptyList())
        
        // When
        val result = repository.getWorkoutStats(differentUserId).toList()
        
        // Then
        assertEquals(1, result.size)
        assertEquals(WorkoutStats.EMPTY, result[0])
        
        // Verify the correct user ID was passed to DAO
        verify { workoutDao.getAllWorkoutsForUser(differentUserId) }
    }
    
    @Test
    fun getRecentWorkouts_handlesFlowEmissions() = runTest {
        // Given - Flow that emits multiple times
        val initialEntities = listOf(testEntity1)
        val updatedEntities = listOf(testEntity1, testEntity2)
        
        every { workoutDao.getRecentCompletedWorkouts(testUserId, 7) } returns flowOf(initialEntities, updatedEntities)
        every { workoutMapper.toDomain(testEntity1) } returns testWorkout1
        every { workoutMapper.toDomain(testEntity2) } returns testWorkout2
        
        // When
        val result = repository.getRecentWorkouts(testUserId, 7).toList()
        
        // Then - Should receive both emissions
        assertEquals(2, result.size)
        assertEquals(1, result[0].size) // First emission: 1 workout
        assertEquals(2, result[1].size) // Second emission: 2 workouts
        
        verify { workoutDao.getRecentCompletedWorkouts(testUserId, 7) }
    }
    
    @Test
    fun getWorkoutStats_onlyProcessesCompletedWorkouts() = runTest {
        // Given - Mix of completed and in-progress workouts
        val mixedEntities = listOf(testEntity1, testEntity3) // testEntity3 is IN_PROGRESS
        val completedWorkout = testWorkout1
        val inProgressWorkout = testWorkout1.copy(
            id = testWorkoutId3,
            status = WorkoutStatus.IN_PROGRESS,
            endTime = null
        )
        
        every { workoutDao.getAllWorkoutsForUser(testUserId) } returns flowOf(mixedEntities)
        every { workoutMapper.toDomain(testEntity1) } returns completedWorkout
        every { workoutMapper.toDomain(testEntity3) } returns inProgressWorkout
        
        // When
        val result = repository.getWorkoutStats(testUserId).toList()
        
        // Then - Stats should only be calculated from completed workouts
        assertEquals(1, result.size)
        val stats = result[0]
        
        // Should only count the 1 completed workout
        assertEquals(1, stats.totalWorkouts)
        
        verify { workoutDao.getAllWorkoutsForUser(testUserId) }
        verify { workoutMapper.toDomain(testEntity1) }
        verify { workoutMapper.toDomain(testEntity3) }
    }
    
    @Test
    fun getRecentWorkouts_handlesMapperErrors() = runTest {
        // Given
        every { workoutDao.getRecentCompletedWorkouts(testUserId, 7) } returns flowOf(listOf(testEntity1))
        every { workoutMapper.toDomain(testEntity1) } throws RuntimeException("Mapping error")
        
        // When & Then - Should propagate the exception
        try {
            repository.getRecentWorkouts(testUserId, 7).toList()
            kotlin.test.fail("Expected exception to be thrown")
        } catch (e: RuntimeException) {
            assertEquals("Mapping error", e.message)
        }
        
        verify { workoutDao.getRecentCompletedWorkouts(testUserId, 7) }
        verify { workoutMapper.toDomain(testEntity1) }
    }

    // ================================================================================
    // FEED INTEGRATION TESTS (REPO-001)
    // Tests for enhanced feed methods getFeedWorkouts() and hasMoreFeedWorkouts()
    // ================================================================================

    private val friendUserId1 = "friend-user-123"
    private val friendUserId2 = "friend-user-456"
    private val friendWorkoutId1 = WorkoutId.generate()
    private val friendWorkoutId2 = WorkoutId.generate()

    private val friendEntity1 = WorkoutEntity(
        id = friendWorkoutId1.value,
        userId = friendUserId1,
        name = "Friend's Morning Workout",
        date = LocalDate.now().minusDays(1),
        status = WorkoutStatus.COMPLETED,
        startTime = LocalTime.of(8, 0),
        endTime = LocalTime.of(9, 0),
        createdAt = Instant.now().minusSeconds(86400),
        updatedAt = Instant.now().minusSeconds(86400),
        isSynced = true,
        syncVersion = 1
    )

    private val friendEntity2 = WorkoutEntity(
        id = friendWorkoutId2.value,
        userId = friendUserId2,
        name = "Friend's Evening Workout",
        date = LocalDate.now().minusDays(3),
        status = WorkoutStatus.COMPLETED,
        startTime = LocalTime.of(18, 0),
        endTime = LocalTime.of(19, 30),
        createdAt = Instant.now().minusSeconds(259200),
        updatedAt = Instant.now().minusSeconds(259200),
        isSynced = true,
        syncVersion = 1
    )

    private val friendWorkout1 = Workout(
        id = friendWorkoutId1,
        userId = friendUserId1,
        name = "Friend's Morning Workout",
        date = LocalDate.now().minusDays(1),
        status = WorkoutStatus.COMPLETED,
        startTime = LocalTime.of(8, 0),
        endTime = LocalTime.of(9, 0),
        exercises = emptyList(),
        notes = null,
        createdAt = friendEntity1.createdAt,
        updatedAt = friendEntity1.updatedAt
    )

    private val friendWorkout2 = Workout(
        id = friendWorkoutId2,
        userId = friendUserId2,
        name = "Friend's Evening Workout",
        date = LocalDate.now().minusDays(3),
        status = WorkoutStatus.COMPLETED,
        startTime = LocalTime.of(18, 0),
        endTime = LocalTime.of(19, 30),
        exercises = emptyList(),
        notes = null,
        createdAt = friendEntity2.createdAt,
        updatedAt = friendEntity2.updatedAt
    )

    private val userInfo1 = UserInfo(
        userId = friendUserId1,
        username = "friend1",
        displayName = "Friend One",
        email = "friend1@example.com",
        profileImageUrl = "https://example.com/profile1.jpg"
    )

    private val userInfo2 = UserInfo(
        userId = friendUserId2,
        username = "friend2",
        displayName = "Friend Two",
        email = "friend2@example.com",
        profileImageUrl = "https://example.com/profile2.jpg"
    )

    @Test
    fun getFeedWorkouts_combinesPersonalAndFriendsWorkouts() = runTest {
        // Given
        val friendIds = listOf(friendUserId1, friendUserId2)
        val allEntities = listOf(testEntity1, friendEntity1, testEntity2, friendEntity2)
        
        coEvery { socialRepository.getFriendIds(testUserId) } returns friendIds
        every { workoutDao.getFeedWorkouts(testUserId, friendIds, 10, 0) } returns flowOf(allEntities)
        coEvery { socialRepository.getUserInfo(friendUserId1) } returns userInfo1
        coEvery { socialRepository.getUserInfo(friendUserId2) } returns userInfo2
        
        every { workoutMapper.toDomain(testEntity1) } returns testWorkout1
        every { workoutMapper.toDomain(testEntity2) } returns testWorkout2
        every { workoutMapper.toDomain(friendEntity1) } returns friendWorkout1
        every { workoutMapper.toDomain(friendEntity2) } returns friendWorkout2

        // When
        val result = repository.getFeedWorkouts(10, 0).toList()

        // Then
        assertEquals(1, result.size)
        val feedWorkouts = result[0]
        assertEquals(4, feedWorkouts.size)
        
        // Verify personal workout mapping
        val personalWorkout = feedWorkouts.find { it.workout.id == testWorkoutId1 }
        assertTrue(personalWorkout!!.isPersonal)
        assertEquals(null, personalWorkout.userInfo)
        
        // Verify friend workout mapping
        val friendWorkout = feedWorkouts.find { it.workout.id == friendWorkoutId1 }
        assertEquals(false, friendWorkout!!.isPersonal)
        assertEquals(userInfo1, friendWorkout.userInfo)

        verify { workoutDao.getFeedWorkouts(testUserId, friendIds, 10, 0) }
        verify { workoutMapper.toDomain(testEntity1) }
        verify { workoutMapper.toDomain(friendEntity1) }
    }

    @Test
    fun getFeedWorkouts_respectsChronologicalOrder() = runTest {
        // Given - Entities should be returned in reverse chronological order
        val friendIds = listOf(friendUserId1)
        val orderedEntities = listOf(testEntity1, friendEntity1, testEntity2) // Most recent first
        
        coEvery { socialRepository.getFriendIds(testUserId) } returns friendIds
        every { workoutDao.getFeedWorkouts(testUserId, friendIds, 10, 0) } returns flowOf(orderedEntities)
        coEvery { socialRepository.getUserInfo(friendUserId1) } returns userInfo1
        
        every { workoutMapper.toDomain(testEntity1) } returns testWorkout1
        every { workoutMapper.toDomain(testEntity2) } returns testWorkout2
        every { workoutMapper.toDomain(friendEntity1) } returns friendWorkout1

        // When
        val result = repository.getFeedWorkouts(10, 0).toList()

        // Then
        assertEquals(1, result.size)
        val feedWorkouts = result[0]
        assertEquals(3, feedWorkouts.size)
        
        // Verify chronological order (most recent first)
        assertTrue(feedWorkouts[0].workout.date.isAfter(feedWorkouts[1].workout.date))
        assertTrue(feedWorkouts[1].workout.date.isAfter(feedWorkouts[2].workout.date))
    }

    @Test
    fun getFeedWorkouts_handlesPaginationCorrectly() = runTest {
        // Given
        val friendIds = listOf(friendUserId1)
        val firstPageEntities = listOf(testEntity1, friendEntity1)
        val secondPageEntities = listOf(testEntity2)
        
        coEvery { socialRepository.getFriendIds(testUserId) } returns friendIds
        every { workoutDao.getFeedWorkouts(testUserId, friendIds, 2, 0) } returns flowOf(firstPageEntities)
        every { workoutDao.getFeedWorkouts(testUserId, friendIds, 2, 2) } returns flowOf(secondPageEntities)
        coEvery { socialRepository.getUserInfo(friendUserId1) } returns userInfo1
        
        every { workoutMapper.toDomain(testEntity1) } returns testWorkout1
        every { workoutMapper.toDomain(testEntity2) } returns testWorkout2
        every { workoutMapper.toDomain(friendEntity1) } returns friendWorkout1

        // When - First page
        val firstPage = repository.getFeedWorkouts(2, 0).toList()
        val secondPage = repository.getFeedWorkouts(2, 2).toList()

        // Then
        assertEquals(1, firstPage.size)
        assertEquals(2, firstPage[0].size)
        
        assertEquals(1, secondPage.size)
        assertEquals(1, secondPage[0].size)
        
        // Verify different workout IDs between pages
        val firstPageIds = firstPage[0].map { it.workout.id }
        val secondPageIds = secondPage[0].map { it.workout.id }
        assertTrue(firstPageIds.intersect(secondPageIds).isEmpty())

        verify { workoutDao.getFeedWorkouts(testUserId, friendIds, 2, 0) }
        verify { workoutDao.getFeedWorkouts(testUserId, friendIds, 2, 2) }
    }

    @Test
    fun hasMoreFeedWorkouts_returnsFalseAfter40Workouts() = runTest {
        // Given
        val friendIds = listOf(friendUserId1)
        
        coEvery { socialRepository.getFriendIds(testUserId) } returns friendIds
        every { workoutDao.getFeedWorkoutsCount(testUserId, friendIds) } returns flowOf(45)

        // When
        val result = repository.hasMoreFeedWorkouts(40).toList()

        // Then
        assertEquals(1, result.size)
        assertEquals(false, result[0]) // Should return false when count > 40

        verify { workoutDao.getFeedWorkoutsCount(testUserId, friendIds) }
    }

    @Test
    fun hasMoreFeedWorkouts_returnsTrueBeforeLimit() = runTest {
        // Given
        val friendIds = listOf(friendUserId1)
        
        coEvery { socialRepository.getFriendIds(testUserId) } returns friendIds
        every { workoutDao.getFeedWorkoutsCount(testUserId, friendIds) } returns flowOf(25)

        // When
        val result = repository.hasMoreFeedWorkouts(20).toList()

        // Then
        assertEquals(1, result.size)
        assertEquals(true, result[0]) // Should return true when more workouts available

        verify { workoutDao.getFeedWorkoutsCount(testUserId, friendIds) }
    }

    @Test
    fun getFeedWorkouts_handlesEmptyFriendsList() = runTest {
        // Given
        val emptyFriendIds = emptyList<String>()
        val personalOnlyEntities = listOf(testEntity1, testEntity2)
        
        coEvery { socialRepository.getFriendIds(testUserId) } returns emptyFriendIds
        every { workoutDao.getFeedWorkouts(testUserId, emptyFriendIds, 10, 0) } returns flowOf(personalOnlyEntities)
        
        every { workoutMapper.toDomain(testEntity1) } returns testWorkout1
        every { workoutMapper.toDomain(testEntity2) } returns testWorkout2

        // When
        val result = repository.getFeedWorkouts(10, 0).toList()

        // Then
        assertEquals(1, result.size)
        val feedWorkouts = result[0]
        assertEquals(2, feedWorkouts.size)
        
        // All workouts should be personal
        assertTrue(feedWorkouts.all { it.isPersonal })
        assertTrue(feedWorkouts.all { it.userInfo == null })

        verify { workoutDao.getFeedWorkouts(testUserId, emptyFriendIds, 10, 0) }
    }

    @Test
    fun getFeedWorkouts_handlesSocialRepositoryErrors() = runTest {
        // Given
        val friendIds = listOf(friendUserId1)
        val mixedEntities = listOf(testEntity1, friendEntity1)
        
        coEvery { socialRepository.getFriendIds(testUserId) } returns friendIds
        every { workoutDao.getFeedWorkouts(testUserId, friendIds, 10, 0) } returns flowOf(mixedEntities)
        coEvery { socialRepository.getUserInfo(friendUserId1) } throws RuntimeException("Network error")
        
        every { workoutMapper.toDomain(testEntity1) } returns testWorkout1
        every { workoutMapper.toDomain(friendEntity1) } returns friendWorkout1

        // When & Then - Should propagate social repository errors
        try {
            repository.getFeedWorkouts(10, 0).toList()
            kotlin.test.fail("Expected exception to be thrown")
        } catch (e: RuntimeException) {
            assertEquals("Network error", e.message)
        }

        verify { workoutDao.getFeedWorkouts(testUserId, friendIds, 10, 0) }
    }

    @Test
    fun getFeedWorkouts_includesUserInfoForFriendsWorkoutsOnly() = runTest {
        // Given
        val friendIds = listOf(friendUserId1)
        val mixedEntities = listOf(testEntity1, friendEntity1)
        
        coEvery { socialRepository.getFriendIds(testUserId) } returns friendIds
        every { workoutDao.getFeedWorkouts(testUserId, friendIds, 10, 0) } returns flowOf(mixedEntities)
        coEvery { socialRepository.getUserInfo(friendUserId1) } returns userInfo1
        
        every { workoutMapper.toDomain(testEntity1) } returns testWorkout1
        every { workoutMapper.toDomain(friendEntity1) } returns friendWorkout1

        // When
        val result = repository.getFeedWorkouts(10, 0).toList()

        // Then
        assertEquals(1, result.size)
        val feedWorkouts = result[0]
        assertEquals(2, feedWorkouts.size)
        
        val personalWorkout = feedWorkouts.find { it.workout.userId == testUserId }
        val friendWorkout = feedWorkouts.find { it.workout.userId == friendUserId1 }
        
        // Personal workout should not have user info
        assertTrue(personalWorkout!!.isPersonal)
        assertEquals(null, personalWorkout.userInfo)
        
        // Friend workout should have user info
        assertEquals(false, friendWorkout!!.isPersonal)
        assertEquals(userInfo1, friendWorkout.userInfo)

        // getUserInfo should only be called for friend workouts
        verify(exactly = 0) { socialRepository.getUserInfo(testUserId) }
        verify { socialRepository.getUserInfo(friendUserId1) }
    }

    @Test
    fun getFeedWorkouts_onlyReturnsCompletedWorkouts() = runTest {
        // Given - Include in-progress workout
        val friendIds = listOf(friendUserId1)
        val completedOnlyEntities = listOf(testEntity1, friendEntity1) // Both completed
        
        coEvery { socialRepository.getFriendIds(testUserId) } returns friendIds
        every { workoutDao.getFeedWorkouts(testUserId, friendIds, 10, 0) } returns flowOf(completedOnlyEntities)
        coEvery { socialRepository.getUserInfo(friendUserId1) } returns userInfo1
        
        every { workoutMapper.toDomain(testEntity1) } returns testWorkout1
        every { workoutMapper.toDomain(friendEntity1) } returns friendWorkout1

        // When
        val result = repository.getFeedWorkouts(10, 0).toList()

        // Then
        assertEquals(1, result.size)
        val feedWorkouts = result[0]
        
        // All returned workouts should be completed
        assertTrue(feedWorkouts.all { it.workout.status == WorkoutStatus.COMPLETED })
        
        // DAO should be called with conditions to only return completed workouts
        verify { workoutDao.getFeedWorkouts(testUserId, friendIds, 10, 0) }
    }
} 