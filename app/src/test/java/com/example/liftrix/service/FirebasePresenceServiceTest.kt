package com.example.liftrix.service

import com.example.liftrix.domain.model.PresenceStatus
import com.example.liftrix.domain.model.UserPresence
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.Timestamp
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import java.time.Instant
import java.util.Date

/**
 * Unit tests for FirebasePresenceService.
 * Tests presence tracking, workout status updates, and real-time presence observation.
 */
class FirebasePresenceServiceTest {

    @MockK
    private lateinit var firestore: FirebaseFirestore
    
    @MockK
    private lateinit var auth: FirebaseAuth
    
    @MockK
    private lateinit var mockUser: FirebaseUser
    
    @MockK
    private lateinit var presenceCollection: CollectionReference
    
    @MockK
    private lateinit var presenceDocument: DocumentReference
    
    @MockK
    private lateinit var query: Query
    
    @MockK
    private lateinit var listenerRegistration: ListenerRegistration
    
    private lateinit var presenceService: FirebasePresenceService
    
    private val testUserId = "test-user-123"
    private val testWorkoutId = "workout-456"
    
    @Before
    fun setup() {
        MockKAnnotations.init(this)
        presenceService = FirebasePresenceService(firestore, auth)
        
        // Default mock setup
        every { firestore.collection("user_presence") } returns presenceCollection
        every { presenceCollection.document(any()) } returns presenceDocument
        every { auth.currentUser } returns mockUser
        every { mockUser.uid } returns testUserId
        every { mockUser.isEmailVerified } returns true
        every { mockUser.isAnonymous } returns false
    }

    @Test
    fun `startPresenceTracking sets user online status when authenticated`() = runTest {
        // Given
        val mockTask = mockk<Task<Void>>()
        every { presenceDocument.set(any()) } returns mockTask
        every { mockTask.await() } returns mockk()
        
        val expectedData = mapOf(
            "status" to PresenceStatus.ONLINE.name,
            "last_active" to FieldValue.serverTimestamp(),
            "user_id" to testUserId,
            "current_workout_id" to null
        )
        
        // When
        val result = presenceService.startPresenceTracking()
        
        // Then
        assertTrue(result.isSuccess)
        verify { presenceDocument.set(expectedData) }
        verify { mockTask.await() }
    }
    
    @Test
    fun `startPresenceTracking fails when user not authenticated`() = runTest {
        // Given
        every { auth.currentUser } returns null
        
        // When
        val result = presenceService.startPresenceTracking()
        
        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
        assertEquals("User not authenticated", result.exceptionOrNull()?.message)
        verify(exactly = 0) { presenceDocument.set(any()) }
    }
    
    @Test
    fun `startPresenceTracking succeeds but skips when email not verified`() = runTest {
        // Given
        every { mockUser.isEmailVerified } returns false
        every { mockUser.isAnonymous } returns false
        
        // When
        val result = presenceService.startPresenceTracking()
        
        // Then
        assertTrue(result.isSuccess)
        verify(exactly = 0) { presenceDocument.set(any()) }
    }
    
    @Test
    fun `startPresenceTracking works for anonymous users`() = runTest {
        // Given
        every { mockUser.isEmailVerified } returns false
        every { mockUser.isAnonymous } returns true
        val mockTask = mockk<Task<Void>>()
        every { presenceDocument.set(any()) } returns mockTask
        every { mockTask.await() } returns mockk()
        
        // When
        val result = presenceService.startPresenceTracking()
        
        // Then
        assertTrue(result.isSuccess)
        verify { presenceDocument.set(any()) }
    }
    
    @Test
    fun `startPresenceTracking handles permission denied gracefully`() = runTest {
        // Given
        val mockTask = mockk<Task<Void>>()
        every { presenceDocument.set(any()) } returns mockTask
        val permissionException = FirebaseFirestoreException(
            "Permission denied",
            FirebaseFirestoreException.Code.PERMISSION_DENIED
        )
        every { mockTask.await() } throws permissionException
        
        // When
        val result = presenceService.startPresenceTracking()
        
        // Then
        assertTrue(result.isSuccess) // Should not fail the app
        verify { presenceDocument.set(any()) }
    }
    
    @Test
    fun `startPresenceTracking handles general firestore exceptions`() = runTest {
        // Given
        val mockTask = mockk<Task<Void>>()
        every { presenceDocument.set(any()) } returns mockTask
        val networkException = FirebaseFirestoreException(
            "Network error",
            FirebaseFirestoreException.Code.UNAVAILABLE
        )
        every { mockTask.await() } throws networkException
        
        // When
        val result = presenceService.startPresenceTracking()
        
        // Then
        assertTrue(result.isFailure)
        assertEquals(networkException, result.exceptionOrNull())
    }
    
    @Test
    fun `updateWorkoutStatus sets working out status when workout starts`() = runTest {
        // Given
        val mockTask = mockk<Task<Void>>()
        every { presenceDocument.update(any()) } returns mockTask
        every { mockTask.await() } returns mockk()
        
        val expectedData = mapOf(
            "status" to PresenceStatus.WORKING_OUT.name,
            "last_active" to FieldValue.serverTimestamp(),
            "current_workout_id" to testWorkoutId
        )
        
        // When
        val result = presenceService.updateWorkoutStatus(testWorkoutId)
        
        // Then
        assertTrue(result.isSuccess)
        verify { presenceDocument.update(expectedData) }
    }
    
    @Test
    fun `updateWorkoutStatus sets online status when workout ends`() = runTest {
        // Given
        val mockTask = mockk<Task<Void>>()
        every { presenceDocument.update(any()) } returns mockTask
        every { mockTask.await() } returns mockk()
        
        val expectedData = mapOf(
            "status" to PresenceStatus.ONLINE.name,
            "last_active" to FieldValue.serverTimestamp(),
            "current_workout_id" to null
        )
        
        // When
        val result = presenceService.updateWorkoutStatus(null)
        
        // Then
        assertTrue(result.isSuccess)
        verify { presenceDocument.update(expectedData) }
    }
    
    @Test
    fun `updateWorkoutStatus fails when user not authenticated`() = runTest {
        // Given
        every { auth.currentUser } returns null
        
        // When
        val result = presenceService.updateWorkoutStatus(testWorkoutId)
        
        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
        verify(exactly = 0) { presenceDocument.update(any()) }
    }
    
    @Test
    fun `updateWorkoutStatus handles firestore exceptions`() = runTest {
        // Given
        val mockTask = mockk<Task<Void>>()
        every { presenceDocument.update(any()) } returns mockTask
        val exception = RuntimeException("Network error")
        every { mockTask.await() } throws exception
        
        // When
        val result = presenceService.updateWorkoutStatus(testWorkoutId)
        
        // Then
        assertTrue(result.isFailure)
        assertEquals(exception, result.exceptionOrNull())
    }
    
    @Test
    fun `observeFriendsPresence returns empty map for empty friend list`() = runTest {
        // When
        val result = presenceService.observeFriendsPresence(emptyList()).first()
        
        // Then
        assertTrue(result.isEmpty())
        verify(exactly = 0) { presenceCollection.whereIn(any(), any()) }
    }
    
    @Test
    fun `observeFriendsPresence returns friend presence data`() = runTest {
        // Given
        val friendIds = listOf("friend1", "friend2")
        val mockSnapshot = mockk<QuerySnapshot>()
        val mockDoc1 = mockk<DocumentSnapshot>()
        val mockDoc2 = mockk<DocumentSnapshot>()
        
        every { presenceCollection.whereIn("user_id", friendIds) } returns query
        every { query.addSnapshotListener(any()) } answers {
            val listener = firstArg<(QuerySnapshot?, Exception?) -> Unit>()
            listener(mockSnapshot, null)
            listenerRegistration
        }
        
        every { mockSnapshot.documents } returns listOf(mockDoc1, mockDoc2)
        
        // Setup first document (online user)
        every { mockDoc1.getString("user_id") } returns "friend1"
        every { mockDoc1.data } returns mapOf(
            "status" to "ONLINE",
            "last_active" to Timestamp(Date.from(Instant.now())),
            "current_workout_id" to null,
            "user_id" to "friend1"
        )
        
        // Setup second document (working out user)
        every { mockDoc2.getString("user_id") } returns "friend2"
        every { mockDoc2.data } returns mapOf(
            "status" to "WORKING_OUT",
            "last_active" to Timestamp(Date.from(Instant.now())),
            "current_workout_id" to "workout-123",
            "user_id" to "friend2"
        )
        
        // When
        val result = presenceService.observeFriendsPresence(friendIds).first()
        
        // Then
        assertEquals(2, result.size)
        assertEquals(PresenceStatus.ONLINE, result["friend1"]?.status)
        assertEquals(PresenceStatus.WORKING_OUT, result["friend2"]?.status)
        assertEquals("workout-123", result["friend2"]?.currentWorkoutId)
        verify { query.addSnapshotListener(any()) }
    }
    
    @Test
    fun `observeFriendsPresence handles snapshot listener errors`() = runTest {
        // Given
        val friendIds = listOf("friend1")
        val error = RuntimeException("Firestore error")
        
        every { presenceCollection.whereIn("user_id", friendIds) } returns query
        every { query.addSnapshotListener(any()) } answers {
            val listener = firstArg<(QuerySnapshot?, Exception?) -> Unit>()
            listener(null, error)
            listenerRegistration
        }
        
        // When/Then
        try {
            presenceService.observeFriendsPresence(friendIds).first()
            fail("Expected exception to be thrown")
        } catch (e: Exception) {
            assertEquals(error, e)
        }
    }
    
    @Test
    fun `observeFriendsPresence handles malformed document data gracefully`() = runTest {
        // Given
        val friendIds = listOf("friend1")
        val mockSnapshot = mockk<QuerySnapshot>()
        val mockDoc = mockk<DocumentSnapshot>()
        
        every { presenceCollection.whereIn("user_id", friendIds) } returns query
        every { query.addSnapshotListener(any()) } answers {
            val listener = firstArg<(QuerySnapshot?, Exception?) -> Unit>()
            listener(mockSnapshot, null)
            listenerRegistration
        }
        
        every { mockSnapshot.documents } returns listOf(mockDoc)
        every { mockDoc.getString("user_id") } returns null // Malformed data
        every { mockDoc.data } returns emptyMap<String, Any>()
        
        // When
        val result = presenceService.observeFriendsPresence(friendIds).first()
        
        // Then
        assertTrue(result.isEmpty()) // Should filter out malformed documents
    }
    
    @Test
    fun `stopPresenceTracking sets user offline`() = runTest {
        // Given
        val mockTask = mockk<Task<Void>>()
        every { presenceDocument.update(any()) } returns mockTask
        every { mockTask.await() } returns mockk()
        
        val expectedData = mapOf(
            "status" to PresenceStatus.OFFLINE.name,
            "last_active" to FieldValue.serverTimestamp(),
            "current_workout_id" to null
        )
        
        // When
        val result = presenceService.stopPresenceTracking()
        
        // Then
        assertTrue(result.isSuccess)
        verify { presenceDocument.update(expectedData) }
    }
    
    @Test
    fun `stopPresenceTracking fails when user not authenticated`() = runTest {
        // Given
        every { auth.currentUser } returns null
        
        // When
        val result = presenceService.stopPresenceTracking()
        
        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
        verify(exactly = 0) { presenceDocument.update(any()) }
    }
    
    @Test
    fun `updatePresenceStatus updates status correctly`() = runTest {
        // Given
        val mockTask = mockk<Task<Void>>()
        every { presenceDocument.update(any()) } returns mockTask
        every { mockTask.await() } returns mockk()
        
        val expectedData = mapOf(
            "status" to PresenceStatus.IDLE.name,
            "last_active" to FieldValue.serverTimestamp(),
            "current_workout_id" to null
        )
        
        // When
        val result = presenceService.updatePresenceStatus(PresenceStatus.IDLE)
        
        // Then
        assertTrue(result.isSuccess)
        verify { presenceDocument.update(expectedData) }
    }
    
    @Test
    fun `updatePresenceStatus includes workout id for working out status`() = runTest {
        // Given
        val mockTask = mockk<Task<Void>>()
        every { presenceDocument.update(any()) } returns mockTask
        every { mockTask.await() } returns mockk()
        
        val expectedData = mapOf(
            "status" to PresenceStatus.WORKING_OUT.name,
            "last_active" to FieldValue.serverTimestamp(),
            "current_workout_id" to testWorkoutId
        )
        
        // When
        val result = presenceService.updatePresenceStatus(PresenceStatus.WORKING_OUT, testWorkoutId)
        
        // Then
        assertTrue(result.isSuccess)
        verify { presenceDocument.update(expectedData) }
    }
    
    @Test
    fun `updatePresenceStatus clears workout id for non-working status`() = runTest {
        // Given
        val mockTask = mockk<Task<Void>>()
        every { presenceDocument.update(any()) } returns mockTask
        every { mockTask.await() } returns mockk()
        
        val expectedData = mapOf(
            "status" to PresenceStatus.ONLINE.name,
            "last_active" to FieldValue.serverTimestamp(),
            "current_workout_id" to null
        )
        
        // When
        val result = presenceService.updatePresenceStatus(PresenceStatus.ONLINE, "should-be-ignored")
        
        // Then
        assertTrue(result.isSuccess)
        verify { presenceDocument.update(expectedData) }
    }
    
    @Test
    fun `updatePresenceStatus handles firestore exceptions`() = runTest {
        // Given
        val mockTask = mockk<Task<Void>>()
        every { presenceDocument.update(any()) } returns mockTask
        val exception = RuntimeException("Update failed")
        every { mockTask.await() } throws exception
        
        // When
        val result = presenceService.updatePresenceStatus(PresenceStatus.ONLINE)
        
        // Then
        assertTrue(result.isFailure)
        assertEquals(exception, result.exceptionOrNull())
    }
    
    @Test
    fun `observeFriendsPresence removes listener on flow cancellation`() = runTest {
        // Given
        val friendIds = listOf("friend1")
        
        every { presenceCollection.whereIn("user_id", friendIds) } returns query
        every { query.addSnapshotListener(any()) } returns listenerRegistration
        every { listenerRegistration.remove() } just Runs
        
        // When
        val flow = presenceService.observeFriendsPresence(friendIds)
        // Flow is cancelled when scope ends
        
        // Then
        verify { listenerRegistration.remove() }
    }
}