package com.example.liftrix.data.repository

import androidx.work.WorkManager
import com.example.liftrix.data.cache.RecommendationCache
import com.example.liftrix.data.local.dao.FriendDao
import com.example.liftrix.data.local.dao.PrivacySettingsDao
import com.example.liftrix.data.local.dao.WorkoutDao
import com.example.liftrix.data.local.entity.FriendEntity
import com.example.liftrix.data.mapper.FriendMapper
import com.example.liftrix.data.mapper.WorkoutMapper
import com.example.liftrix.domain.model.Exercise
import com.example.liftrix.domain.model.ExerciseCategory
import com.example.liftrix.domain.model.ExerciseSet
import com.example.liftrix.domain.model.FriendStatus
import com.example.liftrix.domain.model.RepCount
import com.example.liftrix.domain.model.Weight
import com.example.liftrix.domain.model.Workout
import com.example.liftrix.domain.model.WorkoutId
import com.example.liftrix.domain.model.WorkoutStatus
import com.example.liftrix.domain.repository.AuthRepository
import com.google.firebase.Timestamp
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.android.gms.tasks.Task
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.slot
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
import kotlin.test.assertTrue

/**
 * Integration tests for Firebase integration functionality across repositories
 * Tests INT-003 implementation: Firebase real-time listeners, sync, and presence
 */
@RunWith(RobolectricTestRunner::class) 
class FirebaseIntegrationTest {

    // WorkoutRepository dependencies
    @MockK
    private lateinit var workoutDao: WorkoutDao
    
    @MockK
    private lateinit var workoutMapper: WorkoutMapper
    
    @MockK
    private lateinit var workManager: WorkManager
    
    // SocialRepository dependencies
    @MockK
    private lateinit var friendDao: FriendDao
    
    @MockK
    private lateinit var privacySettingsDao: PrivacySettingsDao
    
    @MockK
    private lateinit var friendMapper: FriendMapper
    
    @MockK
    private lateinit var recommendationCache: RecommendationCache
    
    // Shared dependencies
    @MockK
    private lateinit var authRepository: AuthRepository
    
    @MockK
    private lateinit var firestore: FirebaseFirestore
    
    private lateinit var workoutRepository: WorkoutRepositoryImpl
    private lateinit var socialRepository: SocialRepositoryImpl

    private val testUserId = "test-user-123"
    private val friendUserId = "friend-user-456"
    
    private val testWorkout = Workout(
        id = WorkoutId("workout-123"),
        userId = testUserId,
        name = "Firebase Test Workout",
        date = LocalDate.now(),
        status = WorkoutStatus.COMPLETED,
        exercises = listOf(
            Exercise(
                name = "Push Ups",
                category = ExerciseCategory.CHEST,
                sets = listOf(
                    ExerciseSet(
                        reps = RepCount(20),
                        weight = Weight(0f)
                    )
                )
            )
        ),
        startTime = LocalDateTime.now().minusHours(1),
        endTime = LocalDateTime.now()
    )

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        
        workoutRepository = WorkoutRepositoryImpl(
            workoutDao = workoutDao,
            workoutMapper = workoutMapper,
            workManager = workManager,
            socialRepository = mockk(), // Will be set later
            authRepository = authRepository,
            firestore = firestore
        )
        
        socialRepository = SocialRepositoryImpl(
            friendDao = friendDao,
            privacySettingsDao = privacySettingsDao,
            friendMapper = friendMapper,
            authRepository = authRepository,
            firestore = firestore,
            recommendationCache = recommendationCache
        )
    }

    @Test
    fun `workout repository setupRealtimeFeedListener creates Firebase listener`() = runTest {
        // Given
        val mockCollectionRef = mockk<CollectionReference>()
        val mockQuery1 = mockk<Query>()
        val mockQuery2 = mockk<Query>()
        val mockQuery3 = mockk<Query>()
        val mockQuery4 = mockk<Query>()
        val mockListenerRegistration = mockk<ListenerRegistration>(relaxed = true)
        val listenerSlot = slot<EventListener<QuerySnapshot>>()
        
        every { authRepository.getCurrentUserId() } returns testUserId
        every { firestore.collection("workouts") } returns mockCollectionRef
        every { mockCollectionRef.whereEqualTo("userId", testUserId) } returns mockQuery1
        every { mockQuery1.whereNotEqualTo("completedAt", null) } returns mockQuery2
        every { mockQuery2.orderBy("completedAt", Query.Direction.DESCENDING) } returns mockQuery3
        every { mockQuery3.limit(20) } returns mockQuery4
        every { mockQuery4.addSnapshotListener(capture(listenerSlot)) } returns mockListenerRegistration

        // When
        val flow = workoutRepository.setupRealtimeFeedListener()
        val result = flow.toList()

        // Then
        assertEquals(1, result.size) // Should emit at least once
        
        // Verify Firebase query setup
        verify { firestore.collection("workouts") }
        verify { mockCollectionRef.whereEqualTo("userId", testUserId) }
        verify { mockQuery1.whereNotEqualTo("completedAt", null) }
        verify { mockQuery2.orderBy("completedAt", Query.Direction.DESCENDING) }
        verify { mockQuery3.limit(20) }
        verify { mockQuery4.addSnapshotListener(any<EventListener<QuerySnapshot>>()) }
        
        // Verify listener is captured
        assertTrue(listenerSlot.isCaptured)
    }

    @Test
    fun `workout repository setupRealtimeFeedListener handles unauthenticated user`() = runTest {
        // Given
        every { authRepository.getCurrentUserId() } returns null

        // When
        val flow = workoutRepository.setupRealtimeFeedListener()
        val result = flow.toList()

        // Then
        assertEquals(1, result.size)
        verify { authRepository.getCurrentUserId() }
        // Should not attempt to create Firebase listener
        verify(exactly = 0) { firestore.collection(any()) }
    }

    @Test
    fun `social repository setupRealtimeFeedListener creates Firebase listener for friends workouts`() = runTest {
        // Given
        val friendIds = listOf(friendUserId)
        val friendEntity = mockk<FriendEntity> {
            every { friendUserId } returns this@FirebaseIntegrationTest.friendUserId
            every { status } returns FriendStatus.ACCEPTED.name
        }
        
        val mockCollectionRef = mockk<CollectionReference>()
        val mockQuery1 = mockk<Query>()
        val mockQuery2 = mockk<Query>()
        val mockQuery3 = mockk<Query>()
        val mockQuery4 = mockk<Query>()
        val mockListenerRegistration = mockk<ListenerRegistration>(relaxed = true)
        val listenerSlot = slot<EventListener<QuerySnapshot>>()
        
        every { authRepository.getCurrentUserId() } returns testUserId
        every { friendDao.getFriends(testUserId) } returns flowOf(listOf(friendEntity))
        every { firestore.collection("workouts") } returns mockCollectionRef
        every { mockCollectionRef.whereIn("userId", friendIds) } returns mockQuery1
        every { mockQuery1.whereNotEqualTo("completedAt", null) } returns mockQuery2
        every { mockQuery2.orderBy("completedAt", Query.Direction.DESCENDING) } returns mockQuery3
        every { mockQuery3.limit(20) } returns mockQuery4
        every { mockQuery4.addSnapshotListener(capture(listenerSlot)) } returns mockListenerRegistration

        // When
        val flow = socialRepository.setupRealtimeFeedListener()
        val result = flow.toList()

        // Then
        assertEquals(1, result.size)
        
        // Verify Firebase listener setup for friends' workouts
        verify { friendDao.getFriends(testUserId) }
        verify { firestore.collection("workouts") }
        verify { mockCollectionRef.whereIn("userId", friendIds) }
        verify { mockQuery1.whereNotEqualTo("completedAt", null) }
        verify { mockQuery2.orderBy("completedAt", Query.Direction.DESCENDING) }
        verify { mockQuery3.limit(20) }
        verify { mockQuery4.addSnapshotListener(any<EventListener<QuerySnapshot>>()) }
        
        assertTrue(listenerSlot.isCaptured)
    }

    @Test
    fun `social repository setupRealtimeFeedListener handles no friends gracefully`() = runTest {
        // Given
        every { authRepository.getCurrentUserId() } returns testUserId
        every { friendDao.getFriends(testUserId) } returns flowOf(emptyList<FriendEntity>())

        // When
        val flow = socialRepository.setupRealtimeFeedListener()
        val result = flow.toList()

        // Then
        assertEquals(1, result.size)
        
        verify { authRepository.getCurrentUserId() }
        verify { friendDao.getFriends(testUserId) }
        // Should not create Firebase listener when no friends
        verify(exactly = 0) { firestore.collection(any()) }
    }

    @Test
    fun `social repository syncFriendsWorkouts queries Firebase for friends workouts`() = runTest {
        // Given
        val friendIds = listOf(friendUserId)
        val friendEntity = mockk<FriendEntity> {
            every { friendUserId } returns this@FirebaseIntegrationTest.friendUserId
            every { status } returns FriendStatus.ACCEPTED.name
        }
        
        val mockCollectionRef = mockk<CollectionReference>()
        val mockQuery1 = mockk<Query>()
        val mockQuery2 = mockk<Query>()
        val mockQuery3 = mockk<Query>()
        val mockQuery4 = mockk<Query>()
        val mockTask = mockk<Task<QuerySnapshot>>()
        val mockQuerySnapshot = mockk<QuerySnapshot>()
        val mockDocument = mockk<DocumentSnapshot>()
        
        every { authRepository.getCurrentUserId() } returns testUserId
        every { friendDao.getFriends(testUserId) } returns flowOf(listOf(friendEntity))
        every { firestore.collection("workouts") } returns mockCollectionRef
        every { mockCollectionRef.whereIn("userId", friendIds) } returns mockQuery1
        every { mockQuery1.whereNotEqualTo("completedAt", null) } returns mockQuery2
        every { mockQuery2.orderBy("completedAt", Query.Direction.DESCENDING) } returns mockQuery3
        every { mockQuery3.limit(50) } returns mockQuery4
        every { mockQuery4.get() } returns mockTask
        every { mockQuerySnapshot.documents } returns listOf(mockDocument)
        every { mockDocument.data } returns mapOf(
            "id" to "friend-workout-123",
            "userId" to friendUserId,
            "name" to "Friend's Workout",
            "completedAt" to Timestamp.now()
        )
        
        coEvery { mockTask.await() } returns mockQuerySnapshot

        // When
        val result = socialRepository.syncFriendsWorkouts()

        // Then
        assertTrue(result.isSuccess)
        
        verify { authRepository.getCurrentUserId() }
        verify { friendDao.getFriends(testUserId) }
        verify { firestore.collection("workouts") }
        verify { mockCollectionRef.whereIn("userId", friendIds) }
        verify { mockQuery1.whereNotEqualTo("completedAt", null) }
        verify { mockQuery2.orderBy("completedAt", Query.Direction.DESCENDING) }
        verify { mockQuery3.limit(50) }
        coVerify { mockTask.await() }
    }

    @Test
    fun `social repository syncFriendsWorkouts handles unauthenticated user`() = runTest {
        // Given
        every { authRepository.getCurrentUserId() } returns null

        // When
        val result = socialRepository.syncFriendsWorkouts()

        // Then
        assertTrue(result.isFailure)
        assertEquals("User not authenticated", result.exceptionOrNull()?.message)
        
        verify { authRepository.getCurrentUserId() }
    }

    @Test
    fun `social repository updateUserPresence updates Firebase presence document`() = runTest {
        // Given
        val mockCollectionRef = mockk<CollectionReference>()
        val mockDocumentRef = mockk<DocumentReference>()
        val mockTask = mockk<Task<Void>>()
        val presenceDataSlot = slot<Map<String, Any>>()
        
        every { authRepository.getCurrentUserId() } returns testUserId
        every { firestore.collection("user_presence") } returns mockCollectionRef
        every { mockCollectionRef.document(testUserId) } returns mockDocumentRef
        every { mockDocumentRef.set(capture(presenceDataSlot)) } returns mockTask
        coEvery { mockTask.await() } returns mockk()

        // When
        val result = socialRepository.updateUserPresence()

        // Then
        assertTrue(result.isSuccess)
        
        // Verify presence data structure
        assertTrue(presenceDataSlot.isCaptured)
        val presenceData = presenceDataSlot.captured
        assertEquals("online", presenceData["status"])
        assertEquals(testUserId, presenceData["user_id"])
        assertTrue(presenceData.containsKey("last_active"))
        assertTrue(presenceData["last_active"] is Timestamp)
        
        verify { authRepository.getCurrentUserId() }
        verify { firestore.collection("user_presence") }
        verify { mockCollectionRef.document(testUserId) }
        verify { mockDocumentRef.set(any<Map<String, Any>>()) }
        coVerify { mockTask.await() }
    }

    @Test
    fun `social repository updateUserPresence handles unauthenticated user`() = runTest {
        // Given
        every { authRepository.getCurrentUserId() } returns null

        // When
        val result = socialRepository.updateUserPresence()

        // Then
        assertTrue(result.isFailure)
        assertEquals("User not authenticated", result.exceptionOrNull()?.message)
        
        verify { authRepository.getCurrentUserId() }
    }

    @Test
    fun `workout repository syncWorkoutToFirebase stores completed workouts`() = runTest {
        // Given
        val mockCollectionRef = mockk<CollectionReference>()
        val mockDocumentRef = mockk<DocumentReference>()
        val mockTask = mockk<Task<Void>>()
        val workoutDataSlot = slot<Map<String, Any>>()
        
        every { firestore.collection("workouts") } returns mockCollectionRef
        every { mockCollectionRef.document(testWorkout.id.value) } returns mockDocumentRef
        every { mockDocumentRef.set(capture(workoutDataSlot)) } returns mockTask
        coEvery { mockTask.await() } returns mockk()

        // When - Save a completed workout (this will trigger Firebase sync)
        val entity = mockk<com.example.liftrix.data.local.entity.WorkoutEntity>(relaxed = true)
        every { workoutMapper.toEntity(testWorkout, false) } returns entity
        every { workoutDao.insertWorkout(entity) } returns Unit
        every { workoutDao.getUnsyncedCountForUser(testUserId) } returns 0
        
        val result = workoutRepository.saveWorkout(testWorkout)

        // Then
        assertTrue(result.isSuccess)
        
        // Verify Firebase sync was triggered
        assertTrue(workoutDataSlot.isCaptured)
        val workoutData = workoutDataSlot.captured
        assertEquals(testWorkout.id.value, workoutData["id"])
        assertEquals(testWorkout.userId, workoutData["userId"])
        assertEquals(testWorkout.name, workoutData["name"])
        assertEquals(testWorkout.status.name, workoutData["status"])
        assertEquals(testWorkout.exercises.size, workoutData["exerciseCount"])
        assertTrue(workoutData.containsKey("createdAt"))
        assertTrue(workoutData.containsKey("updatedAt"))
        
        verify { firestore.collection("workouts") }
        verify { mockCollectionRef.document(testWorkout.id.value) }
        verify { mockDocumentRef.set(any<Map<String, Any>>()) }
        coVerify { mockTask.await() }
    }

    @Test
    fun `Firebase sync does not fail local operation when Firebase is unavailable`() = runTest {
        // Given
        val mockCollectionRef = mockk<CollectionReference>()
        val mockDocumentRef = mockk<DocumentReference>()
        val mockTask = mockk<Task<Void>>()
        
        every { firestore.collection("workouts") } returns mockCollectionRef
        every { mockCollectionRef.document(testWorkout.id.value) } returns mockDocumentRef
        every { mockDocumentRef.set(any<Map<String, Any>>()) } returns mockTask
        coEvery { mockTask.await() } throws RuntimeException("Firebase unavailable")
        
        // Local operations should succeed
        val entity = mockk<com.example.liftrix.data.local.entity.WorkoutEntity>(relaxed = true)
        every { workoutMapper.toEntity(testWorkout, false) } returns entity
        every { workoutDao.insertWorkout(entity) } returns Unit
        every { workoutDao.getUnsyncedCountForUser(testUserId) } returns 0

        // When
        val result = workoutRepository.saveWorkout(testWorkout)

        // Then - Local operation should still succeed (offline-first approach)
        assertTrue(result.isSuccess)
        
        verify { workoutDao.insertWorkout(entity) }
        coVerify { mockTask.await() }
    }

    @Test
    fun `Firebase integration end-to-end real-time feed scenario`() = runTest {
        // Given - Setup complete Firebase integration scenario
        val friendEntity = mockk<FriendEntity> {
            every { friendUserId } returns this@FirebaseIntegrationTest.friendUserId
            every { status } returns FriendStatus.ACCEPTED.name
        }
        
        // Mock workout real-time listener setup
        val mockWorkoutCollection = mockk<CollectionReference>()
        val mockWorkoutQuery1 = mockk<Query>()
        val mockWorkoutQuery2 = mockk<Query>()
        val mockWorkoutQuery3 = mockk<Query>()
        val mockWorkoutQuery4 = mockk<Query>()
        val mockWorkoutListener = mockk<ListenerRegistration>(relaxed = true)
        
        // Mock social real-time listener setup
        val mockSocialQuery1 = mockk<Query>()
        val mockSocialQuery2 = mockk<Query>()
        val mockSocialQuery3 = mockk<Query>()
        val mockSocialQuery4 = mockk<Query>()
        val mockSocialListener = mockk<ListenerRegistration>(relaxed = true)
        
        // Mock presence update
        val mockPresenceCollection = mockk<CollectionReference>()
        val mockPresenceDoc = mockk<DocumentReference>()
        val mockPresenceTask = mockk<Task<Void>>()
        
        every { authRepository.getCurrentUserId() } returns testUserId
        
        // Setup workout listener
        every { firestore.collection("workouts") } returns mockWorkoutCollection
        every { mockWorkoutCollection.whereEqualTo("userId", testUserId) } returns mockWorkoutQuery1
        every { mockWorkoutQuery1.whereNotEqualTo("completedAt", null) } returns mockWorkoutQuery2
        every { mockWorkoutQuery2.orderBy("completedAt", Query.Direction.DESCENDING) } returns mockWorkoutQuery3
        every { mockWorkoutQuery3.limit(20) } returns mockWorkoutQuery4
        every { mockWorkoutQuery4.addSnapshotListener(any<EventListener<QuerySnapshot>>()) } returns mockWorkoutListener
        
        // Setup social listener
        every { friendDao.getFriends(testUserId) } returns flowOf(listOf(friendEntity))
        every { mockWorkoutCollection.whereIn("userId", listOf(friendUserId)) } returns mockSocialQuery1
        every { mockSocialQuery1.whereNotEqualTo("completedAt", null) } returns mockSocialQuery2
        every { mockSocialQuery2.orderBy("completedAt", Query.Direction.DESCENDING) } returns mockSocialQuery3
        every { mockSocialQuery3.limit(20) } returns mockSocialQuery4
        every { mockSocialQuery4.addSnapshotListener(any<EventListener<QuerySnapshot>>()) } returns mockSocialListener
        
        // Setup presence update
        every { firestore.collection("user_presence") } returns mockPresenceCollection
        every { mockPresenceCollection.document(testUserId) } returns mockPresenceDoc
        every { mockPresenceDoc.set(any<Map<String, Any>>()) } returns mockPresenceTask
        coEvery { mockPresenceTask.await() } returns mockk()

        // When - Execute complete Firebase integration
        val workoutListenerFlow = workoutRepository.setupRealtimeFeedListener()
        val socialListenerFlow = socialRepository.setupRealtimeFeedListener()
        val presenceUpdateResult = socialRepository.updateUserPresence()
        
        val workoutListenerResult = workoutListenerFlow.toList()
        val socialListenerResult = socialListenerFlow.toList()

        // Then - Verify complete Firebase integration
        assertEquals(1, workoutListenerResult.size)
        assertEquals(1, socialListenerResult.size)
        assertTrue(presenceUpdateResult.isSuccess)
        
        // Verify workout real-time listener
        verify { mockWorkoutCollection.whereEqualTo("userId", testUserId) }
        verify { mockWorkoutQuery4.addSnapshotListener(any<EventListener<QuerySnapshot>>()) }
        
        // Verify social real-time listener
        verify { friendDao.getFriends(testUserId) }
        verify { mockWorkoutCollection.whereIn("userId", listOf(friendUserId)) }
        verify { mockSocialQuery4.addSnapshotListener(any<EventListener<QuerySnapshot>>()) }
        
        // Verify presence update
        verify { mockPresenceCollection.document(testUserId) }
        verify { mockPresenceDoc.set(any<Map<String, Any>>()) }
        coVerify { mockPresenceTask.await() }
        
        // Verify Firebase collections accessed
        verify(atLeast = 2) { firestore.collection("workouts") }
        verify { firestore.collection("user_presence") }
    }
}