package com.example.liftrix.data.repository

import androidx.work.WorkManager
import com.example.liftrix.data.local.dao.WorkoutDao
import com.example.liftrix.data.mapper.WorkoutMapper
import com.example.liftrix.domain.model.Exercise
import com.example.liftrix.domain.model.ExerciseCategory
import com.example.liftrix.domain.model.ExerciseSet
import com.example.liftrix.domain.model.FeedWorkout
import com.example.liftrix.domain.model.RepCount
import com.example.liftrix.domain.model.User
import com.example.liftrix.domain.model.Weight
import com.example.liftrix.domain.model.Workout
import com.example.liftrix.domain.model.WorkoutId
import com.example.liftrix.domain.model.WorkoutStatus
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.repository.SocialRepository
import com.google.firebase.firestore.FirebaseFirestore
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Integration tests for WorkoutRepository feed functionality
 * Tests REPO-001 implementation: feed methods for unified workout timeline
 */
@RunWith(RobolectricTestRunner::class)
class WorkoutRepositoryFeedIntegrationTest {

    @MockK
    private lateinit var workoutDao: WorkoutDao
    
    @MockK
    private lateinit var workoutMapper: WorkoutMapper
    
    @MockK
    private lateinit var workManager: WorkManager
    
    @MockK
    private lateinit var socialRepository: SocialRepository
    
    @MockK
    private lateinit var authRepository: AuthRepository
    
    @MockK
    private lateinit var firestore: FirebaseFirestore

    private lateinit var workoutRepository: WorkoutRepositoryImpl

    private val testUserId = "test-user-123"
    private val friendUserId = "friend-user-456"
    
    private val testWorkout = Workout(
        id = WorkoutId("workout-123"),
        userId = testUserId,
        name = "Push Day",
        date = LocalDate.now(),
        status = WorkoutStatus.COMPLETED,
        exercises = listOf(
            Exercise(
                name = "Bench Press",
                category = ExerciseCategory.CHEST,
                sets = listOf(
                    ExerciseSet(
                        reps = RepCount(10),
                        weight = Weight(135f)
                    )
                )
            )
        ),
        startTime = LocalDateTime.now().minusHours(1),
        endTime = LocalDateTime.now()
    )
    
    private val friendWorkout = Workout(
        id = WorkoutId("friend-workout-789"),
        userId = friendUserId,
        name = "Leg Day",
        date = LocalDate.now(),
        status = WorkoutStatus.COMPLETED,
        exercises = listOf(
            Exercise(
                name = "Squats",
                category = ExerciseCategory.LEGS,
                sets = listOf(
                    ExerciseSet(
                        reps = RepCount(12),
                        weight = Weight(185f)
                    )
                )
            )
        ),
        startTime = LocalDateTime.now().minusHours(2),
        endTime = LocalDateTime.now().minusMinutes(90)
    )
    
    private val friendUser = User(
        uid = friendUserId,
        email = "friend@example.com",
        displayName = "Friend User",
        photoUrl = null,
        isAnonymous = false,
        subscriptionTier = com.example.liftrix.domain.model.SubscriptionTier.FREE,
        subscriptionStatus = com.example.liftrix.domain.model.SubscriptionStatus.ACTIVE,
        subscriptionExpiresAt = null,
        premiumFeaturesEnabled = false,
        onboardingCompleted = true,
        profileVersion = 1L,
        createdAt = LocalDateTime.now().minusDays(30),
        lastSignInAt = LocalDateTime.now().minusHours(2),
        updatedAt = LocalDateTime.now().minusHours(2)
    )

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        workoutRepository = WorkoutRepositoryImpl(
            workoutDao = workoutDao,
            workoutMapper = workoutMapper,
            workManager = workManager,
            socialRepository = socialRepository,
            authRepository = authRepository,
            firestore = firestore
        )
    }

    @Test
    fun `getFeedWorkouts returns combined personal and friends workouts`() = runTest {
        // Given
        val limit = 10
        val offset = 0
        val friendIds = listOf(friendUserId)
        
        val personalWorkoutEntity = mockk<com.example.liftrix.data.local.entity.WorkoutEntity> {
            every { userId } returns testUserId
            every { id } returns testWorkout.id.value
        }
        
        val friendWorkoutEntity = mockk<com.example.liftrix.data.local.entity.WorkoutEntity> {
            every { userId } returns friendUserId
            every { id } returns friendWorkout.id.value
        }
        
        val combinedEntities = listOf(personalWorkoutEntity, friendWorkoutEntity)
        
        every { workoutDao.getAcceptedFriendIds(testUserId) } returns friendIds
        every { workoutDao.getFeedWorkouts(testUserId, friendIds, limit, offset) } returns flowOf(combinedEntities)
        every { workoutMapper.toDomain(personalWorkoutEntity) } returns testWorkout
        every { workoutMapper.toDomain(friendWorkoutEntity) } returns friendWorkout
        coEvery { socialRepository.getUserById(friendUserId) } returns friendUser

        // When
        val result = workoutRepository.getFeedWorkouts(testUserId, limit, offset).toList()

        // Then
        assertEquals(1, result.size)
        val feedWorkouts = result.first()
        assertEquals(2, feedWorkouts.size)
        
        // Verify personal workout
        val personalFeedWorkout = feedWorkouts.find { it.isPersonal }
        assertTrue(personalFeedWorkout != null)
        assertEquals(testWorkout.id, personalFeedWorkout.workout.id)
        assertTrue(personalFeedWorkout.isPersonal)
        assertEquals(null, personalFeedWorkout.user)
        
        // Verify friend workout
        val friendFeedWorkout = feedWorkouts.find { !it.isPersonal }
        assertTrue(friendFeedWorkout != null)
        assertEquals(friendWorkout.id, friendFeedWorkout.workout.id)
        assertFalse(friendFeedWorkout.isPersonal)
        assertEquals(friendUser.uid, friendFeedWorkout.user?.uid)
        
        verify { workoutDao.getAcceptedFriendIds(testUserId) }
        verify { workoutDao.getFeedWorkouts(testUserId, friendIds, limit, offset) }
        coVerify { socialRepository.getUserById(friendUserId) }
    }

    @Test
    fun `getFeedWorkouts handles friend without user info gracefully`() = runTest {
        // Given
        val limit = 10
        val offset = 0
        val friendIds = listOf(friendUserId)
        
        val friendWorkoutEntity = mockk<com.example.liftrix.data.local.entity.WorkoutEntity> {
            every { userId } returns friendUserId
            every { id } returns friendWorkout.id.value
        }
        
        every { workoutDao.getAcceptedFriendIds(testUserId) } returns friendIds
        every { workoutDao.getFeedWorkouts(testUserId, friendIds, limit, offset) } returns flowOf(listOf(friendWorkoutEntity))
        every { workoutMapper.toDomain(friendWorkoutEntity) } returns friendWorkout
        coEvery { socialRepository.getUserById(friendUserId) } returns null // User not found

        // When
        val result = workoutRepository.getFeedWorkouts(testUserId, limit, offset).toList()

        // Then
        assertEquals(1, result.size)
        val feedWorkouts = result.first()
        assertEquals(0, feedWorkouts.size) // Friend workout should be filtered out
        
        coVerify { socialRepository.getUserById(friendUserId) }
    }

    @Test
    fun `getFeedWorkouts returns empty list when no friends`() = runTest {
        // Given
        val limit = 10
        val offset = 0
        val emptyFriendIds = emptyList<String>()
        
        every { workoutDao.getAcceptedFriendIds(testUserId) } returns emptyFriendIds
        every { workoutDao.getFeedWorkouts(testUserId, emptyFriendIds, limit, offset) } returns flowOf(emptyList())

        // When
        val result = workoutRepository.getFeedWorkouts(testUserId, limit, offset).toList()

        // Then
        assertEquals(1, result.size)
        val feedWorkouts = result.first()
        assertEquals(0, feedWorkouts.size)
        
        verify { workoutDao.getAcceptedFriendIds(testUserId) }
        verify { workoutDao.getFeedWorkouts(testUserId, emptyFriendIds, limit, offset) }
    }

    @Test
    fun `getFeedWorkouts handles dao exception gracefully`() = runTest {
        // Given
        val limit = 10
        val offset = 0
        
        every { workoutDao.getAcceptedFriendIds(testUserId) } throws RuntimeException("Database error")

        // When
        val result = workoutRepository.getFeedWorkouts(testUserId, limit, offset).toList()

        // Then
        assertEquals(1, result.size)
        val feedWorkouts = result.first()
        assertEquals(0, feedWorkouts.size) // Should return empty list on error
        
        verify { workoutDao.getAcceptedFriendIds(testUserId) }
    }

    @Test
    fun `hasMoreFeedWorkouts returns false when offset exceeds maximum`() = runTest {
        // Given
        val offset = 45 // Greater than MAX_FEED_WORKOUTS (40)
        
        // When
        val hasMore = workoutRepository.hasMoreFeedWorkouts(testUserId, offset)
        
        // Then
        assertFalse(hasMore)
    }

    @Test
    fun `hasMoreFeedWorkouts returns true when more workouts available`() = runTest {
        // Given
        val offset = 5
        val friendIds = listOf(friendUserId)
        val totalCount = 20
        
        every { workoutDao.getAcceptedFriendIds(testUserId) } returns friendIds
        every { workoutDao.getFeedWorkoutCount(testUserId, friendIds) } returns totalCount

        // When
        val hasMore = workoutRepository.hasMoreFeedWorkouts(testUserId, offset)
        
        // Then
        assertTrue(hasMore) // offset (5) < totalCount (20) && offset < MAX_FEED_WORKOUTS (40)
        
        verify { workoutDao.getAcceptedFriendIds(testUserId) }
        verify { workoutDao.getFeedWorkoutCount(testUserId, friendIds) }
    }

    @Test
    fun `hasMoreFeedWorkouts returns false when no more workouts available`() = runTest {
        // Given
        val offset = 15
        val friendIds = listOf(friendUserId)
        val totalCount = 10 // Less than offset
        
        every { workoutDao.getAcceptedFriendIds(testUserId) } returns friendIds
        every { workoutDao.getFeedWorkoutCount(testUserId, friendIds) } returns totalCount

        // When
        val hasMore = workoutRepository.hasMoreFeedWorkouts(testUserId, offset)
        
        // Then
        assertFalse(hasMore) // offset (15) >= totalCount (10)
        
        verify { workoutDao.getAcceptedFriendIds(testUserId) }
        verify { workoutDao.getFeedWorkoutCount(testUserId, friendIds) }
    }

    @Test
    fun `hasMoreFeedWorkouts handles dao exception gracefully`() = runTest {
        // Given
        val offset = 5
        
        every { workoutDao.getAcceptedFriendIds(testUserId) } throws RuntimeException("Database error")

        // When
        val hasMore = workoutRepository.hasMoreFeedWorkouts(testUserId, offset)
        
        // Then
        assertFalse(hasMore) // Should return false on error
        
        verify { workoutDao.getAcceptedFriendIds(testUserId) }
    }

    @Test
    fun `setupRealtimeFeedListener sets up Firebase listener for user workouts`() = runTest {
        // Given
        val listenerRegistration = mockk<com.google.firebase.firestore.ListenerRegistration>(relaxed = true)
        val query = mockk<com.google.firebase.firestore.Query>(relaxed = true)
        val collectionReference = mockk<com.google.firebase.firestore.CollectionReference>(relaxed = true)
        
        every { authRepository.getCurrentUserId() } returns testUserId
        every { firestore.collection("workouts") } returns collectionReference
        every { 
            collectionReference.whereEqualTo("userId", testUserId) 
        } returns query
        every { 
            query.whereNotEqualTo("completedAt", null) 
        } returns query
        every { 
            query.orderBy("completedAt", com.google.firebase.firestore.Query.Direction.DESCENDING) 
        } returns query
        every { 
            query.limit(20) 
        } returns query
        every { 
            query.addSnapshotListener(any<com.google.firebase.firestore.EventListener<com.google.firebase.firestore.QuerySnapshot>>()) 
        } returns listenerRegistration

        // When
        val flow = workoutRepository.setupRealtimeFeedListener()
        val result = flow.toList()

        // Then
        assertEquals(1, result.size) // Should emit at least once
        
        verify { authRepository.getCurrentUserId() }
        verify { firestore.collection("workouts") }
        verify { collectionReference.whereEqualTo("userId", testUserId) }
    }

    @Test
    fun `setupRealtimeFeedListener handles unauthenticated user gracefully`() = runTest {
        // Given
        every { authRepository.getCurrentUserId() } returns null

        // When
        val flow = workoutRepository.setupRealtimeFeedListener()
        val result = flow.toList()

        // Then
        assertEquals(1, result.size) // Should emit once and close
        
        verify { authRepository.getCurrentUserId() }
    }

    @Test
    fun `feed integration end-to-end scenario`() = runTest {
        // Given - Setup complete feed scenario
        val limit = 5
        val offset = 0
        val friendIds = listOf(friendUserId)
        
        // Mock entities for personal and friend workouts
        val personalEntity = mockk<com.example.liftrix.data.local.entity.WorkoutEntity> {
            every { userId } returns testUserId
            every { id } returns testWorkout.id.value
        }
        
        val friendEntity = mockk<com.example.liftrix.data.local.entity.WorkoutEntity> {
            every { userId } returns friendUserId
            every { id } returns friendWorkout.id.value
        }
        
        // Setup feed loading
        every { workoutDao.getAcceptedFriendIds(testUserId) } returns friendIds
        every { workoutDao.getFeedWorkouts(testUserId, friendIds, limit, offset) } returns flowOf(
            listOf(personalEntity, friendEntity)
        )
        every { workoutMapper.toDomain(personalEntity) } returns testWorkout
        every { workoutMapper.toDomain(friendEntity) } returns friendWorkout
        coEvery { socialRepository.getUserById(friendUserId) } returns friendUser
        
        // Setup pagination check
        every { workoutDao.getFeedWorkoutCount(testUserId, friendIds) } returns 10

        // When - Execute feed operations
        val feedResult = workoutRepository.getFeedWorkouts(testUserId, limit, offset).toList()
        val hasMoreResult = workoutRepository.hasMoreFeedWorkouts(testUserId, offset + limit)

        // Then - Verify complete feed functionality
        assertEquals(1, feedResult.size)
        val feedWorkouts = feedResult.first()
        assertEquals(2, feedWorkouts.size)
        
        // Verify workout order and content
        val personalFeed = feedWorkouts.find { it.isPersonal }!!
        val friendFeed = feedWorkouts.find { !it.isPersonal }!!
        
        assertEquals("Push Day", personalFeed.workout.name)
        assertEquals("Friend User's Leg Day", friendFeed.displayTitle)
        assertTrue(personalFeed.isDisplayable())
        assertTrue(friendFeed.isDisplayable())
        
        // Verify pagination
        assertTrue(hasMoreResult) // offset (5) < totalCount (10)
        
        // Verify all necessary components were called
        verify { workoutDao.getAcceptedFriendIds(testUserId) }
        verify { workoutDao.getFeedWorkouts(testUserId, friendIds, limit, offset) }
        verify { workoutDao.getFeedWorkoutCount(testUserId, friendIds) }
        coVerify { socialRepository.getUserById(friendUserId) }
    }
}