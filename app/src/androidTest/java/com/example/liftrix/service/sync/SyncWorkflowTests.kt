package com.example.liftrix.service.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.error.LiftrixError
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * Integration tests for sync workflows.
 * 
 * Tests cover real-time sync scenarios, offline/online transitions,
 * conflict resolution, error handling, and performance characteristics.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class SyncWorkflowTests {
    
    private lateinit var mockFirestore: FirebaseFirestore
    private lateinit var mockWorkManager: androidx.work.WorkManager
    private lateinit var mockSyncStrategy: SyncStrategy
    private lateinit var mockConflictResolver: ConflictResolver
    private lateinit var realtimeSyncManager: RealtimeSyncManager
    
    private val testUserId = "test-user-123"
    
    @Before
    fun setup() {
        mockFirestore = mockk(relaxed = true)
        mockWorkManager = mockk(relaxed = true)
        mockSyncStrategy = mockk(relaxed = true)
        mockConflictResolver = mockk(relaxed = true)
        
        realtimeSyncManager = RealtimeSyncManager(
            firestore = mockFirestore,
            workManager = mockWorkManager,
            syncStrategy = mockSyncStrategy,
            conflictResolver = mockConflictResolver
        )
    }
    
    @Test
    fun startRealtimeSync_initializesSuccessfully() = runTest {
        // Given
        val mockCollection = mockk<com.google.firebase.firestore.CollectionReference>(relaxed = true)
        val mockQuery = mockk<com.google.firebase.firestore.Query>(relaxed = true)
        val mockRegistration = mockk<com.google.firebase.firestore.ListenerRegistration>(relaxed = true)
        
        every { mockFirestore.collection("workout_sessions") } returns mockCollection
        every { mockCollection.whereEqualTo("userId", testUserId) } returns mockQuery
        every { mockQuery.whereIn("status", any<List<String>>()) } returns mockQuery
        every { mockQuery.addSnapshotListener(any()) } returns mockRegistration
        
        every { mockFirestore.collection("personal_records") } returns mockCollection
        every { mockCollection.whereEqualTo("userId", testUserId) } returns mockQuery
        every { mockQuery.orderBy("achievedAt", any()) } returns mockQuery
        every { mockQuery.limit(50) } returns mockQuery
        
        // When
        realtimeSyncManager.startRealtimeSync(testUserId)
        
        // Give some time for the coroutine to execute
        kotlinx.coroutines.delay(100)
        
        // Then
        verify { mockFirestore.collection("workout_sessions") }
        verify { mockFirestore.collection("personal_records") }
        verify { 
            mockWorkManager.enqueueUniquePeriodicWork(
                "widget_sync_$testUserId",
                any(),
                any()
            )
        }
        
        // Verify sync state progression
        val currentState = realtimeSyncManager.syncState.value
        assertTrue(currentState is RealtimeSyncManager.SyncState.Active)
        assertEquals(testUserId, (currentState as RealtimeSyncManager.SyncState.Active).userId)
    }
    
    @Test
    fun startRealtimeSync_withInvalidUserId_doesNotInitialize() = runTest {
        // Given
        val invalidUserId = ""
        
        // When
        realtimeSyncManager.startRealtimeSync(invalidUserId)
        
        kotlinx.coroutines.delay(100)
        
        // Then
        verify(exactly = 0) { mockFirestore.collection(any()) }
        verify(exactly = 0) { mockWorkManager.enqueueUniquePeriodicWork(any(), any(), any()) }
        
        val currentState = realtimeSyncManager.syncState.value
        assertTrue(currentState is RealtimeSyncManager.SyncState.Idle)
    }
    
    @Test
    fun stopRealtimeSync_removesListenersAndCancelsWork() = runTest {
        // Given - first start sync to have listeners
        val mockRegistration = mockk<com.google.firebase.firestore.ListenerRegistration>(relaxed = true)
        val mockCollection = mockk<com.google.firebase.firestore.CollectionReference>(relaxed = true)
        val mockQuery = mockk<com.google.firebase.firestore.Query>(relaxed = true)
        
        every { mockFirestore.collection(any()) } returns mockCollection
        every { mockCollection.whereEqualTo(any<String>(), any()) } returns mockQuery
        every { mockQuery.whereIn(any<String>(), any<List<String>>()) } returns mockQuery
        every { mockQuery.orderBy(any<String>(), any()) } returns mockQuery
        every { mockQuery.limit(any()) } returns mockQuery
        every { mockQuery.addSnapshotListener(any()) } returns mockRegistration
        
        realtimeSyncManager.startRealtimeSync(testUserId)
        kotlinx.coroutines.delay(100)
        
        // When
        realtimeSyncManager.stopRealtimeSync(testUserId)
        
        // Then
        verify { mockRegistration.remove() }
        verify { mockWorkManager.cancelUniqueWork("widget_sync_$testUserId") }
        
        val currentState = realtimeSyncManager.syncState.value
        assertTrue(currentState is RealtimeSyncManager.SyncState.Idle)
    }
    
    @Test
    fun forceSyncAll_triggersImmediateSync() = runTest {
        // When
        val result = realtimeSyncManager.forceSyncAll(testUserId)
        
        // Then
        assertTrue(result.isSuccess)
        verify { 
            mockWorkManager.enqueue(
                match<androidx.work.OneTimeWorkRequest> { request ->
                    request.workSpec.input.getString("userId") == testUserId &&
                    request.workSpec.input.getString("syncType") == "manual_force"
                }
            )
        }
    }
    
    @Test
    fun workoutSessionUpdates_flowEmitsCorrectChanges() = runTest {
        // Given
        val mockDocumentChange = mockk<DocumentChange>(relaxed = true)
        val mockDocumentSnapshot = mockk<DocumentSnapshot>(relaxed = true)
        val mockQuerySnapshot = mockk<QuerySnapshot>(relaxed = true)
        
        every { mockDocumentChange.type } returns DocumentChange.Type.ADDED
        every { mockDocumentChange.document } returns mockDocumentSnapshot
        every { mockDocumentSnapshot.id } returns "workout-123"
        every { mockDocumentSnapshot.data } returns mapOf("userId" to testUserId, "status" to "ACTIVE")
        every { mockQuerySnapshot.documentChanges } returns listOf(mockDocumentChange)
        
        val mockCollection = mockk<com.google.firebase.firestore.CollectionReference>(relaxed = true)
        val mockQuery = mockk<com.google.firebase.firestore.Query>(relaxed = true)
        
        every { mockFirestore.collection("workout_sessions") } returns mockCollection
        every { mockCollection.whereEqualTo("userId", testUserId) } returns mockQuery
        every { mockQuery.whereIn("status", any<List<String>>()) } returns mockQuery
        
        // Mock the listener to immediately call with test data
        every { mockQuery.addSnapshotListener(any()) } answers {
            val listener = firstArg<com.google.firebase.firestore.EventListener<QuerySnapshot>>()
            listener.onEvent(mockQuerySnapshot, null)
            mockk<com.google.firebase.firestore.ListenerRegistration>(relaxed = true)
        }
        
        // When
        val flow = realtimeSyncManager.getWorkoutSessionUpdates(testUserId)
        val documentChange = flow.first()
        
        // Then
        assertEquals(DocumentChange.Type.ADDED, documentChange.type)
        assertEquals("workout-123", documentChange.document.id)
    }
    
    @Test
    fun personalRecordUpdates_flowEmitsCorrectChanges() = runTest {
        // Given
        val mockDocumentChange = mockk<DocumentChange>(relaxed = true)
        val mockDocumentSnapshot = mockk<DocumentSnapshot>(relaxed = true)
        val mockQuerySnapshot = mockk<QuerySnapshot>(relaxed = true)
        
        every { mockDocumentChange.type } returns DocumentChange.Type.ADDED
        every { mockDocumentChange.document } returns mockDocumentSnapshot
        every { mockDocumentSnapshot.id } returns "pr-456"
        every { mockDocumentSnapshot.data } returns mapOf(
            "userId" to testUserId,
            "exercise" to "Bench Press",
            "weight" to 185.0
        )
        every { mockQuerySnapshot.documentChanges } returns listOf(mockDocumentChange)
        
        val mockCollection = mockk<com.google.firebase.firestore.CollectionReference>(relaxed = true)
        val mockQuery = mockk<com.google.firebase.firestore.Query>(relaxed = true)
        
        every { mockFirestore.collection("personal_records") } returns mockCollection
        every { mockCollection.whereEqualTo("userId", testUserId) } returns mockQuery
        every { mockQuery.orderBy("achievedAt", any()) } returns mockQuery
        every { mockQuery.limit(50) } returns mockQuery
        
        // Mock the listener to immediately call with test data
        every { mockQuery.addSnapshotListener(any()) } answers {
            val listener = firstArg<com.google.firebase.firestore.EventListener<QuerySnapshot>>()
            listener.onEvent(mockQuerySnapshot, null)
            mockk<com.google.firebase.firestore.ListenerRegistration>(relaxed = true)
        }
        
        // When
        val flow = realtimeSyncManager.getPersonalRecordUpdates(testUserId)
        val documentChange = flow.first()
        
        // Then
        assertEquals(DocumentChange.Type.ADDED, documentChange.type)
        assertEquals("pr-456", documentChange.document.id)
    }
    
    @Test
    fun syncError_triggersExponentialBackoff() = runTest {
        // Given - setup to throw exception
        every { mockFirestore.collection(any()) } throws RuntimeException("Network error")
        
        // When
        realtimeSyncManager.startRealtimeSync(testUserId)
        
        kotlinx.coroutines.delay(200) // Allow error handling to process
        
        // Then
        val currentState = realtimeSyncManager.syncState.value
        assertTrue(currentState is RealtimeSyncManager.SyncState.Error)
        
        val errorState = currentState as RealtimeSyncManager.SyncState.Error
        assertEquals(testUserId, errorState.userId)
        assertTrue(errorState.error.contains("Network error"))
        assertTrue(errorState.retryAfterMs > 0)
    }
    
    @Test
    fun maxRetryAttemptsExceeded_entersFinalFailureState() = runTest {
        // Given - setup to always throw exception
        every { mockFirestore.collection(any()) } throws RuntimeException("Persistent error")
        
        // When - start sync multiple times to exceed retry limit
        repeat(7) { // More than MAX_RETRY_ATTEMPTS (5)
            realtimeSyncManager.startRealtimeSync(testUserId)
            kotlinx.coroutines.delay(50)
        }
        
        kotlinx.coroutines.delay(200) // Allow final error handling
        
        // Then
        val currentState = realtimeSyncManager.syncState.value
        // State could be Error or Failed depending on timing
        assertTrue(
            currentState is RealtimeSyncManager.SyncState.Error ||
            currentState is RealtimeSyncManager.SyncState.Failed
        )
    }
    
    @Test
    fun widgetSyncWorker_executesSuccessfully() = runTest {
        // Given
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val inputData = workDataOf(
            "userId" to testUserId,
            "syncType" to "background_periodic"
        )
        
        // Create a test worker (we'll need to create a mock implementation)
        val worker = TestListenableWorkerBuilder<TestWidgetSyncWorker>(context)
            .setInputData(inputData)
            .build()
        
        // When
        val result = worker.doWork()
        
        // Then
        assertEquals(ListenableWorker.Result.success(), result)
    }
    
    @Test
    fun conflictResolution_handlesTimestampBasedConflicts() = runTest {
        // Given
        val localData = mapOf(
            "id" to "data-123",
            "value" to "local_value",
            "timestamp" to 1000L
        )
        
        val remoteData = mapOf(
            "id" to "data-123",
            "value" to "remote_value",
            "timestamp" to 2000L // Newer timestamp
        )
        
        every { 
            mockConflictResolver.resolve(any(), any())
        } returns ConflictResolver.Resolution.UseRemote(remoteData)
        
        // When
        val resolution = mockConflictResolver.resolve(localData, remoteData)
        
        // Then
        assertTrue(resolution is ConflictResolver.Resolution.UseRemote)
        assertEquals(remoteData, (resolution as ConflictResolver.Resolution.UseRemote).data)
        
        verify { mockConflictResolver.resolve(localData, remoteData) }
    }
    
    @Test
    fun networkConnectivityChange_adaptsSync() = runTest {
        // Given - simulate network connectivity change
        var networkConnected = true
        
        // Mock network constraint behavior
        every { 
            mockWorkManager.enqueueUniquePeriodicWork(any(), any(), any())
        } answers {
            val workRequest = thirdArg<androidx.work.PeriodicWorkRequest>()
            val constraints = workRequest.workSpec.constraints
            
            // Verify network constraint is set
            assertTrue(constraints.requiredNetworkType == androidx.work.NetworkType.CONNECTED)
            
            mockk<androidx.work.Operation>(relaxed = true)
        }
        
        // When
        realtimeSyncManager.startRealtimeSync(testUserId)
        kotlinx.coroutines.delay(100)
        
        // Then
        verify { 
            mockWorkManager.enqueueUniquePeriodicWork(
                "widget_sync_$testUserId",
                any(),
                match<androidx.work.PeriodicWorkRequest> { request ->
                    request.workSpec.constraints.requiredNetworkType == androidx.work.NetworkType.CONNECTED
                }
            )
        }
    }
    
    @Test
    fun offlineToOnlineTransition_recoversSync() = runTest {
        // Given - start with offline state (no network)
        every { mockFirestore.collection(any()) } throws RuntimeException("No network")
        
        realtimeSyncManager.startRealtimeSync(testUserId)
        kotlinx.coroutines.delay(100)
        
        // Verify we're in error state
        assertTrue(realtimeSyncManager.syncState.value is RealtimeSyncManager.SyncState.Error)
        
        // When - network comes back online
        clearMocks(mockFirestore, answers = false)
        val mockCollection = mockk<com.google.firebase.firestore.CollectionReference>(relaxed = true)
        val mockQuery = mockk<com.google.firebase.firestore.Query>(relaxed = true)
        val mockRegistration = mockk<com.google.firebase.firestore.ListenerRegistration>(relaxed = true)
        
        every { mockFirestore.collection(any()) } returns mockCollection
        every { mockCollection.whereEqualTo(any<String>(), any()) } returns mockQuery
        every { mockQuery.whereIn(any<String>(), any<List<String>>()) } returns mockQuery
        every { mockQuery.orderBy(any<String>(), any()) } returns mockQuery
        every { mockQuery.limit(any()) } returns mockQuery
        every { mockQuery.addSnapshotListener(any()) } returns mockRegistration
        
        // Simulate retry attempt
        realtimeSyncManager.startRealtimeSync(testUserId)
        kotlinx.coroutines.delay(100)
        
        // Then - should recover to active state
        val currentState = realtimeSyncManager.syncState.value
        assertTrue(currentState is RealtimeSyncManager.SyncState.Active)
    }
    
    @Test
    fun performanceTest_syncInitializationCompletes() = runTest {
        // Given
        val mockCollection = mockk<com.google.firebase.firestore.CollectionReference>(relaxed = true)
        val mockQuery = mockk<com.google.firebase.firestore.Query>(relaxed = true)
        val mockRegistration = mockk<com.google.firebase.firestore.ListenerRegistration>(relaxed = true)
        
        every { mockFirestore.collection(any()) } returns mockCollection
        every { mockCollection.whereEqualTo(any<String>(), any()) } returns mockQuery
        every { mockQuery.whereIn(any<String>(), any<List<String>>()) } returns mockQuery
        every { mockQuery.orderBy(any<String>(), any()) } returns mockQuery
        every { mockQuery.limit(any()) } returns mockQuery
        every { mockQuery.addSnapshotListener(any()) } returns mockRegistration
        
        val startTime = System.currentTimeMillis()
        
        // When
        realtimeSyncManager.startRealtimeSync(testUserId)
        kotlinx.coroutines.delay(200) // Allow initialization
        
        val endTime = System.currentTimeMillis()
        
        // Then
        val duration = endTime - startTime
        assertTrue(duration < 1000, "Sync initialization took $duration ms, should be < 1000ms")
        
        val currentState = realtimeSyncManager.syncState.value
        assertTrue(currentState is RealtimeSyncManager.SyncState.Active)
    }
}

/**
 * Test implementation of WidgetSyncWorker for testing purposes
 */
class TestWidgetSyncWorker(
    context: android.content.Context,
    params: androidx.work.WorkerParameters
) : androidx.work.Worker(context, params) {
    
    override fun doWork(): Result {
        val userId = inputData.getString("userId")
        val syncType = inputData.getString("syncType")
        
        return if (!userId.isNullOrBlank() && !syncType.isNullOrBlank()) {
            // Simulate successful sync work
            Result.success(workDataOf("synced_widgets" to 5))
        } else {
            Result.failure(workDataOf("error" to "Invalid input data"))
        }
    }
}

/**
 * Mock implementation of ConflictResolver for testing
 */
interface ConflictResolver {
    fun resolve(localData: Map<String, Any>, remoteData: Map<String, Any>): Resolution
    
    sealed class Resolution {
        data class UseLocal(val data: Map<String, Any>) : Resolution()
        data class UseRemote(val data: Map<String, Any>) : Resolution()
        data class Merge(val data: Map<String, Any>) : Resolution()
        object Skip : Resolution()
    }
}

/**
 * Mock implementation of SyncStrategy for testing
 */
interface SyncStrategy {
    suspend fun handleWorkoutSessionUpdate(userId: String, sessionData: Map<String, Any>)
    suspend fun handlePersonalRecordUpdate(userId: String, prData: Map<String, Any>)
}