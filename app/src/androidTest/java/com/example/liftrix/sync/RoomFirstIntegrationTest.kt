package com.example.liftrix.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.liftrix.config.OfflineArchitectureFlags
import com.example.liftrix.data.local.LiftrixDatabase
import com.example.liftrix.data.local.dao.WorkoutDao
import com.example.liftrix.data.local.dao.WorkoutPostDao
import com.example.liftrix.data.local.dao.SyncQueueDao
import com.example.liftrix.data.local.entity.WorkoutEntity
import com.example.liftrix.data.local.entity.WorkoutPostEntity
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import timber.log.Timber
import java.time.Instant
import javax.inject.Inject

/**
 * SPEC-20241228 - Phase 5: Integration & Testing
 *
 * Integration test suite for Room-First Offline Architecture.
 * Validates core architectural principles:
 * - Firestore offline persistence is disabled
 * - Origin-aware writes (upsertLocal vs upsertFromRemote)
 * - Dirty flag sync gating
 * - Timestamp-based deduplication
 * - Idempotent listener operations
 * - Feedback loop prevention
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class RoomFirstIntegrationTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var database: LiftrixDatabase

    @Inject
    lateinit var firestore: FirebaseFirestore

    private lateinit var workoutDao: WorkoutDao
    private lateinit var workoutPostDao: WorkoutPostDao
    private lateinit var syncQueueDao: SyncQueueDao

    private val testUserId = "test_user_integration"

    @Before
    fun setup() {
        hiltRule.inject()

        workoutDao = database.workoutDao()
        workoutPostDao = database.workoutPostDao()
        syncQueueDao = database.syncQueueDao()

        // Clear test data
        clearTestData()
    }

    @After
    fun tearDown() {
        clearTestData()
    }

    private fun clearTestData() = runTest {
        // Delete test user data
        database.clearAllTables()
    }

    /**
     * TS-001: Verify Firestore Offline Persistence Disabled
     * SPEC: FR-001
     */
    @Test
    fun firestoreOfflinePersistenceIsDisabled() {
        val settings = firestore.firestoreSettings

        // If Room-first is enabled, persistence should be disabled
        if (OfflineArchitectureFlags.DISABLE_FIRESTORE_PERSISTENCE) {
            assertFalse(
                "Firestore offline persistence should be disabled in Room-first mode",
                settings.isPersistenceEnabled
            )
            assertEquals(
                "Cache size should be 0 when persistence is disabled",
                0,
                settings.cacheSizeBytes
            )
            Timber.i("✅ TS-001: Firestore offline persistence confirmed DISABLED")
        } else {
            assertTrue(
                "Firestore offline persistence should be enabled in legacy mode",
                settings.isPersistenceEnabled
            )
            Timber.w("⚠️ TS-001: Running in LEGACY mode with Firestore persistence enabled")
        }
    }

    /**
     * TS-002: Verify Origin-Aware Writes - upsertLocal sets isDirty
     * SPEC: FR-002, FR-007
     */
    @Test
    fun upsertLocalSetsDirtyAndTriggersSyncQueue() = runTest {
        // Given
        val workout = WorkoutEntity(
            id = "w_test_001",
            userId = testUserId,
            name = "Test Workout Local",
            date = Instant.now(),
            exercises = "[]",
            totalDuration = 3600,
            isDirty = false,
            lastModified = 0L
        )

        // When: upsertLocal is called
        workoutDao.upsertLocal(workout)

        // Then: Workout is marked as dirty
        val saved = workoutDao.getWorkoutById("w_test_001", testUserId)
        assertNotNull("Workout should be saved", saved)
        assertTrue("isDirty should be true after upsertLocal", saved!!.isDirty)
        assertTrue("lastModified should be set", saved.lastModified > 0L)

        // And: Dirty workout appears in getDirtyWorkouts query
        val dirtyWorkouts = workoutDao.getDirtyWorkouts(testUserId)
        assertTrue("Dirty workouts should contain saved workout", dirtyWorkouts.any { it.id == "w_test_001" })

        Timber.i("✅ TS-002: upsertLocal correctly sets isDirty=true and updates lastModified")
    }

    /**
     * TS-002b: Verify Origin-Aware Writes - upsertFromRemote sets clean
     * SPEC: FR-002
     */
    @Test
    fun upsertFromRemoteSetsCleanAndDoesNotTriggerSync() = runTest {
        // Given
        val remoteWorkout = WorkoutEntity(
            id = "w_test_002",
            userId = testUserId,
            name = "Test Workout Remote",
            date = Instant.now(),
            exercises = "[]",
            totalDuration = 3600,
            isDirty = true, // Simulate that this was dirty before
            lastModified = System.currentTimeMillis()
        )

        // When: upsertFromRemote is called
        workoutDao.upsertFromRemote(remoteWorkout)

        // Then: Workout is marked as clean
        val saved = workoutDao.getWorkoutById("w_test_002", testUserId)
        assertNotNull("Workout should be saved", saved)
        assertFalse("isDirty should be false after upsertFromRemote", saved!!.isDirty)
        assertTrue("isSynced should be true after upsertFromRemote", saved.isSynced)

        // And: Workout does NOT appear in dirty query
        val dirtyWorkouts = workoutDao.getDirtyWorkouts(testUserId)
        assertFalse("Dirty workouts should NOT contain remote workout", dirtyWorkouts.any { it.id == "w_test_002" })

        Timber.i("✅ TS-002b: upsertFromRemote correctly sets isDirty=false and isSynced=true")
    }

    /**
     * TS-003: Verify Deduplication Rules - Remote Only Applied if Newer
     * SPEC: FR-005
     */
    @Test
    fun remoteUpdateOnlyAppliedIfNewer() = runTest {
        // Given: Local workout modified at T+100
        val localWorkout = WorkoutEntity(
            id = "w_test_003",
            userId = testUserId,
            name = "Local V2",
            date = Instant.now(),
            exercises = "[]",
            totalDuration = 3600,
            lastModified = 100L,
            isDirty = true
        )
        workoutDao.upsertLocal(localWorkout)

        // When: Remote update arrives with OLDER timestamp T+50
        val olderRemote = WorkoutEntity(
            id = "w_test_003",
            userId = testUserId,
            name = "Remote V1",
            date = Instant.now(),
            exercises = "[]",
            totalDuration = 3000,
            lastModified = 50L
        )
        workoutDao.upsertFromRemote(olderRemote)

        // Then: Local is preserved (newer timestamp wins)
        val saved = workoutDao.getWorkoutById("w_test_003", testUserId)
        assertNotNull("Workout should exist", saved)
        assertEquals("Local version should be preserved", "Local V2", saved!!.name)
        assertEquals("Local lastModified should be preserved", 100L, saved.lastModified)
        assertTrue("isDirty should still be true", saved.isDirty)

        Timber.i("✅ TS-003: Older remote update correctly rejected, local preserved")
    }

    /**
     * TS-003b: Verify Deduplication - Newer Remote Updates Local
     * SPEC: FR-005
     */
    @Test
    fun newerRemoteUpdateReplacesLocal() = runTest {
        // Given: Local workout modified at T+50
        val localWorkout = WorkoutEntity(
            id = "w_test_004",
            userId = testUserId,
            name = "Local V1",
            date = Instant.now(),
            exercises = "[]",
            totalDuration = 3000,
            lastModified = 50L,
            isDirty = false
        )
        workoutDao.upsertLocal(localWorkout)

        // When: Remote update arrives with NEWER timestamp T+100
        val newerRemote = WorkoutEntity(
            id = "w_test_004",
            userId = testUserId,
            name = "Remote V2",
            date = Instant.now(),
            exercises = "[]",
            totalDuration = 3600,
            lastModified = 100L
        )
        workoutDao.upsertFromRemote(newerRemote)

        // Then: Remote replaces local
        val saved = workoutDao.getWorkoutById("w_test_004", testUserId)
        assertNotNull("Workout should exist", saved)
        assertEquals("Remote version should replace local", "Remote V2", saved!!.name)
        assertEquals("Remote lastModified should be applied", 100L, saved.lastModified)
        assertFalse("isDirty should be false after remote update", saved.isDirty)

        Timber.i("✅ TS-003b: Newer remote update correctly replaces local")
    }

    /**
     * TS-004: Verify Only Dirty Workouts Are Uploaded
     * SPEC: FR-003
     */
    @Test
    fun onlyDirtyWorkoutsAreQueried() = runTest {
        // Given: 1 dirty workout, 1 clean workout
        workoutDao.upsertLocal(WorkoutEntity(
            id = "w_dirty",
            userId = testUserId,
            name = "Dirty Workout",
            date = Instant.now(),
            exercises = "[]",
            totalDuration = 3600,
            isDirty = true
        ))

        workoutDao.upsertFromRemote(WorkoutEntity(
            id = "w_clean",
            userId = testUserId,
            name = "Clean Workout",
            date = Instant.now(),
            exercises = "[]",
            totalDuration = 3600,
            isDirty = false
        ))

        // When: Sync worker queries dirty workouts
        val dirtyWorkouts = workoutDao.getDirtyWorkouts(testUserId)

        // Then: Only dirty workout is returned
        assertEquals("Should only return dirty workout", 1, dirtyWorkouts.size)
        assertEquals("Should return w_dirty", "w_dirty", dirtyWorkouts[0].id)

        Timber.i("✅ TS-004: getDirtyWorkouts correctly filters by isDirty=true")
    }

    /**
     * TS-005: Verify Idempotency - Same Remote Update 10x Results in Identical State
     * SPEC: FR-004
     */
    @Test
    fun applyingSameRemoteUpdate10TimesResultsInIdenticalState() = runTest {
        // Given
        val remoteWorkout = WorkoutEntity(
            id = "w_idempotent",
            userId = testUserId,
            name = "Idempotent Test",
            date = Instant.now(),
            exercises = "[]",
            totalDuration = 3600,
            lastModified = 1000L
        )

        // When: Apply same update 10 times
        repeat(10) {
            workoutDao.upsertFromRemote(remoteWorkout)
        }

        // Then: State is identical
        val saved = workoutDao.getWorkoutById("w_idempotent", testUserId)
        assertNotNull("Workout should exist", saved)
        assertEquals("Name should match", "Idempotent Test", saved!!.name)
        assertEquals("lastModified should match", 1000L, saved.lastModified)
        assertFalse("isDirty should be false", saved.isDirty)

        // And: Only 1 entity exists (not 10 duplicates)
        val allWorkouts = workoutDao.getAllWorkoutsForUser(testUserId)
        assertEquals("Should only have 1 workout", 1, allWorkouts.filter { it.id == "w_idempotent" }.size)

        Timber.i("✅ TS-005: Idempotency verified - 10x same update = identical state")
    }

    /**
     * TS-006: Verify Feedback Loop Prevention - Real-time Update Doesn't Trigger Sync
     * SPEC: FR-006
     */
    @Test
    fun realtimeListenerUpdateDoesNotCreateFeedbackLoop() = runTest {
        // Given: Simulate a post exists
        val initialPost = WorkoutPostEntity(
            id = "p_test_001",
            userId = testUserId,
            authorId = testUserId,
            workoutId = "w_test_001",
            caption = "Test Post",
            likeCount = 5,
            commentCount = 2,
            shareCount = 1,
            saveCount = 0,
            lastModified = 1000L,
            isDirty = false
        )
        workoutPostDao.upsertFromRemote(initialPost)

        // When: Real-time listener updates engagement (simulating remote event)
        workoutPostDao.upsertEngagementFromRemote(
            postId = "p_test_001",
            likeCount = 10,
            commentCount = 5,
            shareCount = 2,
            saveCount = 1,
            lastModified = 2000L
        )

        // Then: Post is NOT marked as dirty (no feedback loop)
        val updated = workoutPostDao.getPostById("p_test_001")
        assertNotNull("Post should exist", updated)
        assertFalse("Post should NOT be dirty after real-time update", updated!!.isDirty)
        assertEquals("Like count should be updated", 10, updated.likeCount)
        assertEquals("lastModified should be updated", 2000L, updated.lastModified)

        Timber.i("✅ TS-006: Real-time engagement update does NOT set isDirty=true (no feedback loop)")
    }

    /**
     * TS-007: Verify markAsClean Functionality
     * SPEC: INT-003
     */
    @Test
    fun markAsCleanSetsDirtyToFalse() = runTest {
        // Given: Create a dirty workout
        workoutDao.upsertLocal(WorkoutEntity(
            id = "w_mark_clean",
            userId = testUserId,
            name = "To Be Cleaned",
            date = Instant.now(),
            exercises = "[]",
            totalDuration = 3600,
            isDirty = true
        ))

        // Verify it's dirty
        val dirty = workoutDao.getWorkoutById("w_mark_clean", testUserId)
        assertTrue("Workout should be dirty initially", dirty!!.isDirty)

        // When: Mark as clean (simulate successful upload)
        workoutDao.markAsClean(listOf("w_mark_clean"), testUserId)

        // Then: Workout is no longer dirty
        val clean = workoutDao.getWorkoutById("w_mark_clean", testUserId)
        assertFalse("isDirty should be false after markAsClean", clean!!.isDirty)
        assertTrue("isSynced should be true after markAsClean", clean.isSynced)
        assertTrue("syncVersion should be updated", clean.syncVersion > 0L)

        Timber.i("✅ TS-007: markAsClean correctly clears isDirty flag and sets isSynced=true")
    }
}
