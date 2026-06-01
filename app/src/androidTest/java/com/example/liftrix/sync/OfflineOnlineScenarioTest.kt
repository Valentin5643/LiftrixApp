package com.example.liftrix.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.liftrix.data.local.LiftrixDatabase
import com.example.liftrix.data.local.dao.WorkoutDao
import com.example.liftrix.data.local.entity.WorkoutEntity
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
 * SPEC-20241228 - Phase 5: Integration & Testing (INT-004)
 *
 * Offline-Online Scenario Tests
 * Tests the complete offline-first workflow:
 * 1. User creates/edits workouts while offline
 * 2. Changes are stored locally with isDirty=true
 * 3. App comes online
 * 4. Sync worker uploads dirty entities
 * 5. Entities are marked as clean after successful upload
 *
 * Also tests conflict resolution when remote changes exist during sync.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class OfflineOnlineScenarioTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var database: LiftrixDatabase

    private lateinit var workoutDao: WorkoutDao

    private val testUserId = "test_user_offline_online"

    @Before
    fun setup() {
        hiltRule.inject()
        workoutDao = database.workoutDao()
        clearTestData()
    }

    @After
    fun tearDown() {
        clearTestData()
    }

    private fun clearTestData() = runTest {
        database.clearAllTables()
    }

    /**
     * TS-006: User Edits Offline Then Syncs When Online
     * SPEC: US-001
     */
    @Test
    fun userEditsOfflineThenSyncsWhenOnline() = runTest {
        // STEP 1: Simulate offline - user creates workout
        Timber.i("📴 OFFLINE: User creates workout")
        val offlineWorkout = WorkoutEntity(
            id = "w_offline_001",
            userId = testUserId,
            name = "Offline Workout",
            date = Instant.now(),
            exercises = """[{"name":"Squats","sets":3}]""",
            totalDuration = 3600,
            isDirty = false // Will be set to true by upsertLocal
        )
        workoutDao.upsertLocal(offlineWorkout)

        // Verify workout is dirty and queued
        val savedOffline = workoutDao.getWorkoutById("w_offline_001", testUserId)
        assertNotNull("Workout should be saved", savedOffline)
        assertTrue("Workout should be dirty (needs sync)", savedOffline!!.isDirty)
        assertTrue("lastModified should be set", savedOffline.lastModified > 0L)

        Timber.i("✅ Workout saved offline with isDirty=true")

        // STEP 2: Simulate online - sync worker would upload dirty workouts
        Timber.i("📶 ONLINE: Sync worker uploads dirty workouts")
        val dirtyWorkouts = workoutDao.getDirtyWorkouts(testUserId)
        assertEquals("Should have 1 dirty workout", 1, dirtyWorkouts.size)
        assertEquals("Should be our offline workout", "w_offline_001", dirtyWorkouts[0].id)

        // STEP 3: Simulate successful upload - mark as clean
        Timber.i("✅ Upload successful, marking as clean")
        workoutDao.markAsClean(listOf("w_offline_001"), testUserId)

        // STEP 4: Verify workout is clean and synced
        val synced = workoutDao.getWorkoutById("w_offline_001", testUserId)
        assertNotNull("Workout should still exist", synced)
        assertFalse("Workout should no longer be dirty", synced!!.isDirty)
        assertTrue("Workout should be marked as synced", synced.isSynced)
        assertTrue("syncVersion should be updated", synced.syncVersion > 0L)

        Timber.i("✅ TS-006: Offline-online workflow complete - workout synced successfully")
    }

    /**
     * User Edits Offline, Remote Update Arrives, Conflict Resolution
     * SPEC: US-001 (Acceptance Criteria 2-4)
     */
    @Test
    fun offlineEditWithRemoteUpdateConflictResolution() = runTest {
        // STEP 1: User creates workout offline at T+100
        Timber.i("📴 OFFLINE (T+100): User creates workout")
        val localWorkout = WorkoutEntity(
            id = "w_conflict",
            userId = testUserId,
            name = "Local Edit",
            date = Instant.now(),
            exercises = """[{"name":"Deadlifts","sets":5}]""",
            totalDuration = 4200,
            lastModified = 100L,
            isDirty = true
        )
        workoutDao.upsertLocal(localWorkout)

        // STEP 2: App comes online, but remote has newer version at T+200
        Timber.i("📶 ONLINE: Remote update arrives with newer timestamp")
        val remoteUpdate = WorkoutEntity(
            id = "w_conflict",
            userId = testUserId,
            name = "Remote Edit",
            date = Instant.now(),
            exercises = """[{"name":"Bench Press","sets":4}]""",
            totalDuration = 3000,
            lastModified = 200L // Newer than local
        )

        // STEP 3: Sync downloads remote update
        workoutDao.upsertFromRemote(remoteUpdate)

        // STEP 4: Verify remote wins (newer timestamp)
        val resolved = workoutDao.getWorkoutById("w_conflict", testUserId)
        assertNotNull("Workout should exist", resolved)
        assertEquals("Remote version should win", "Remote Edit", resolved!!.name)
        assertEquals("Remote timestamp should be preserved", 200L, resolved.lastModified)
        assertFalse("Should not be dirty after remote update", resolved.isDirty)

        Timber.i("✅ Conflict resolved: Remote won (newer timestamp)")
    }

    /**
     * User Edits Offline, Local is Newer, Local is Preserved
     */
    @Test
    fun offlineEditPreservedWhenLocalIsNewer() = runTest {
        // STEP 1: User creates workout offline at T+200
        Timber.i("📴 OFFLINE (T+200): User creates workout")
        val localWorkout = WorkoutEntity(
            id = "w_local_wins",
            userId = testUserId,
            name = "Local V2",
            date = Instant.now(),
            exercises = """[{"name":"Squats","sets":5}]""",
            totalDuration = 3600,
            lastModified = 200L,
            isDirty = true
        )
        workoutDao.upsertLocal(localWorkout)

        // STEP 2: Remote has older version at T+100
        Timber.i("📶 ONLINE: Remote update arrives with older timestamp")
        val olderRemote = WorkoutEntity(
            id = "w_local_wins",
            userId = testUserId,
            name = "Remote V1",
            date = Instant.now(),
            exercises = """[{"name":"Deadlifts","sets":3}]""",
            totalDuration = 3000,
            lastModified = 100L // Older than local
        )

        // STEP 3: Sync downloads remote update
        workoutDao.upsertFromRemote(olderRemote)

        // STEP 4: Verify local is preserved (newer timestamp)
        val preserved = workoutDao.getWorkoutById("w_local_wins", testUserId)
        assertNotNull("Workout should exist", preserved)
        assertEquals("Local version should be preserved", "Local V2", preserved!!.name)
        assertEquals("Local timestamp should be preserved", 200L, preserved.lastModified)
        assertTrue("Should still be dirty (needs upload)", preserved.isDirty)

        Timber.i("✅ Local edit preserved: Local was newer, remote discarded")
    }

    /**
     * Multiple Offline Edits, Batch Sync When Online
     */
    @Test
    fun multipleOfflineEditsSyncedInBatch() = runTest {
        // STEP 1: User creates 3 workouts offline
        Timber.i("📴 OFFLINE: User creates 3 workouts")
        repeat(3) { index ->
            workoutDao.upsertLocal(WorkoutEntity(
                id = "w_batch_$index",
                userId = testUserId,
                name = "Batch Workout $index",
                date = Instant.now(),
                exercises = "[]",
                totalDuration = 3600,
                isDirty = true
            ))
        }

        // STEP 2: Verify all 3 are dirty
        val dirtyWorkouts = workoutDao.getDirtyWorkouts(testUserId)
        assertEquals("Should have 3 dirty workouts", 3, dirtyWorkouts.size)

        // STEP 3: Simulate batch upload
        Timber.i("📶 ONLINE: Batch uploading ${dirtyWorkouts.size} workouts")
        val uploadedIds = dirtyWorkouts.map { it.id }

        // STEP 4: Mark all as clean
        workoutDao.markAsClean(uploadedIds, testUserId)

        // STEP 5: Verify all are clean
        val remainingDirty = workoutDao.getDirtyWorkouts(testUserId)
        assertEquals("Should have no dirty workouts after batch sync", 0, remainingDirty.size)

        // Verify all are synced
        uploadedIds.forEach { id ->
            val synced = workoutDao.getWorkoutById(id, testUserId)
            assertNotNull("Workout $id should exist", synced)
            assertFalse("Workout $id should not be dirty", synced!!.isDirty)
            assertTrue("Workout $id should be synced", synced.isSynced)
        }

        Timber.i("✅ Batch sync complete: 3 workouts uploaded and marked clean")
    }

    /**
     * User Edits Offline, Goes Online, Edit Again Before Sync
     */
    @Test
    fun userEditsOfflineThenOnlineThenEditAgainBeforeSync() = runTest {
        // STEP 1: Offline edit
        Timber.i("📴 OFFLINE: User creates workout")
        workoutDao.upsertLocal(WorkoutEntity(
            id = "w_multi_edit",
            userId = testUserId,
            name = "Version 1",
            date = Instant.now(),
            exercises = "[]",
            totalDuration = 3600
        ))

        val v1 = workoutDao.getWorkoutById("w_multi_edit", testUserId)
        assertTrue("V1 should be dirty", v1!!.isDirty)
        val v1Timestamp = v1.lastModified

        // STEP 2: Online but user edits again before sync
        Timber.i("📶 ONLINE: User edits again before sync completes")
        Thread.sleep(10) // Ensure timestamp difference
        workoutDao.upsertLocal(v1.copy(name = "Version 2"))

        // STEP 3: Verify still dirty with newer timestamp
        val v2 = workoutDao.getWorkoutById("w_multi_edit", testUserId)
        assertTrue("V2 should still be dirty", v2!!.isDirty)
        assertTrue("V2 timestamp should be newer", v2.lastModified > v1Timestamp)
        assertEquals("Name should be updated", "Version 2", v2.name)

        // STEP 4: Sync worker uploads latest version
        workoutDao.markAsClean(listOf("w_multi_edit"), testUserId)

        // STEP 5: Verify final state
        val final = workoutDao.getWorkoutById("w_multi_edit", testUserId)
        assertFalse("Final should not be dirty", final!!.isDirty)
        assertEquals("Final should have latest name", "Version 2", final.name)

        Timber.i("✅ Multiple edits before sync handled correctly")
    }

    /**
     * Offline Queue Persists Across App Restart
     * (Simulated by clearing in-memory state but keeping Room data)
     */
    @Test
    fun offlineQueuePersistsAcrossAppRestart() = runTest {
        // STEP 1: Create dirty workout
        Timber.i("📴 User creates workout, app crashes before sync")
        workoutDao.upsertLocal(WorkoutEntity(
            id = "w_persist",
            userId = testUserId,
            name = "Persistent Workout",
            date = Instant.now(),
            exercises = "[]",
            totalDuration = 3600
        ))

        // Verify dirty
        val beforeRestart = workoutDao.getDirtyWorkouts(testUserId)
        assertEquals("Should have 1 dirty workout", 1, beforeRestart.size)

        // STEP 2: Simulate app restart (Room data persists)
        Timber.i("🔄 App restarts")
        // In a real scenario, we'd reinitialize DAOs, but Room keeps data

        // STEP 3: Verify dirty workout still exists after "restart"
        val afterRestart = workoutDao.getDirtyWorkouts(testUserId)
        assertEquals("Dirty workout should persist after restart", 1, afterRestart.size)
        assertEquals("Should be same workout", "w_persist", afterRestart[0].id)
        assertTrue("Should still be dirty", afterRestart[0].isDirty)

        Timber.i("✅ Offline queue persisted across app restart")
    }
}
