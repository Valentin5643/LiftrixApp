package com.example.liftrix.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.testing.TestListenableWorkerBuilder
import app.cash.turbine.test
import com.example.liftrix.TestDataFactory
import com.example.liftrix.data.local.dao.WorkoutDao
import com.example.liftrix.data.mapper.WorkoutMapper
import com.example.liftrix.domain.model.WorkoutStatus
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class WorkoutRepositoryIntegrationTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var workoutDao: WorkoutDao
    
    @Inject
    lateinit var workoutMapper: WorkoutMapper
    
    @Inject
    lateinit var workoutRepository: WorkoutRepository

    private lateinit var firestore: FirebaseFirestore

    @Before
    fun setup() {
        hiltRule.inject()
        
        // Configure Firestore to use emulator
        if (!FirebaseApp.getApps(androidx.test.InstrumentationRegistry.getInstrumentation().targetContext).isNullOrEmpty()) {
            firestore = FirebaseFirestore.getInstance()
            val settings = FirebaseFirestoreSettings.Builder()
                .setHost("10.0.2.2:8080") // Android emulator host
                .setSslEnabled(false)
                .setPersistenceEnabled(true)
                .build()
            firestore.firestoreSettings = settings
        }
    }

    @Test
    fun saveWorkout_shouldStoreInLocalDatabase() = runTest {
        val workout = TestDataFactory.createWorkout(
            userId = "test-user-integration",
            name = "Integration Test Workout"
        )

        val result = workoutRepository.saveWorkout(workout)

        assertTrue(result.isSuccess)
        
        // Verify saved in local database
        val savedWorkouts = workoutRepository.getAllWorkoutsForUser(workout.userId).first()
        assertEquals(1, savedWorkouts.size)
        assertEquals(workout.name, savedWorkouts.first().name)
    }

    @Test
    fun saveWorkout_shouldMarkAsUnsynced() = runTest {
        val workout = TestDataFactory.createWorkout(
            userId = "test-user-unsynced",
            name = "Unsynced Workout"
        )

        workoutRepository.saveWorkout(workout)

        val unsyncedCount = workoutRepository.getUnsyncedCountForUser(workout.userId)
        assertEquals(1, unsyncedCount)
    }

    @Test
    fun updateWorkout_shouldUpdateLocalDatabase() = runTest {
        val workout = TestDataFactory.createWorkout(
            userId = "test-user-update",
            name = "Original Name"
        )
        
        // Save original
        workoutRepository.saveWorkout(workout)
        
        // Update
        val updatedWorkout = workout.copy(
            name = "Updated Name",
            status = WorkoutStatus.COMPLETED
        )
        val result = workoutRepository.updateWorkout(updatedWorkout)

        assertTrue(result.isSuccess)
        
        // Verify update
        val savedWorkouts = workoutRepository.getAllWorkoutsForUser(workout.userId).first()
        assertEquals(1, savedWorkouts.size)
        assertEquals("Updated Name", savedWorkouts.first().name)
        assertEquals(WorkoutStatus.COMPLETED, savedWorkouts.first().status)
    }

    @Test
    fun deleteWorkoutForUser_shouldRemoveFromDatabase() = runTest {
        val workout = TestDataFactory.createWorkout(
            userId = "test-user-delete",
            name = "To Be Deleted"
        )
        
        // Save workout
        workoutRepository.saveWorkout(workout)
        var savedWorkouts = workoutRepository.getAllWorkoutsForUser(workout.userId).first()
        assertEquals(1, savedWorkouts.size)
        
        // Delete workout
        val result = workoutRepository.deleteWorkoutForUser(workout.id, workout.userId)
        assertTrue(result.isSuccess)
        
        // Verify deletion
        savedWorkouts = workoutRepository.getAllWorkoutsForUser(workout.userId).first()
        assertEquals(0, savedWorkouts.size)
    }

    @Test
    fun getUserScopedMethods_shouldIsolateUserData() = runTest {
        val user1Id = "user-1"
        val user2Id = "user-2"
        
        val workout1 = TestDataFactory.createWorkout(userId = user1Id, name = "User 1 Workout")
        val workout2 = TestDataFactory.createWorkout(userId = user2Id, name = "User 2 Workout")
        
        // Save workouts for different users
        workoutRepository.saveWorkout(workout1)
        workoutRepository.saveWorkout(workout2)
        
        // Verify user data isolation
        val user1Workouts = workoutRepository.getAllWorkoutsForUser(user1Id).first()
        val user2Workouts = workoutRepository.getAllWorkoutsForUser(user2Id).first()
        
        assertEquals(1, user1Workouts.size)
        assertEquals(1, user2Workouts.size)
        assertEquals("User 1 Workout", user1Workouts.first().name)
        assertEquals("User 2 Workout", user2Workouts.first().name)
    }

    @Test
    fun getUnsyncedWorkoutsForUser_shouldReturnOnlyUnsyncedForUser() = runTest {
        val userId = "test-user-unsynced-list"
        
        // Create and save multiple workouts
        val workout1 = TestDataFactory.createWorkout(userId = userId, name = "Workout 1")
        val workout2 = TestDataFactory.createWorkout(userId = userId, name = "Workout 2")
        val workout3 = TestDataFactory.createWorkout(userId = "other-user", name = "Other User Workout")
        
        workoutRepository.saveWorkout(workout1)
        workoutRepository.saveWorkout(workout2)
        workoutRepository.saveWorkout(workout3)
        
        // Get unsynced workouts for specific user
        val unsyncedWorkouts = workoutRepository.getUnsyncedWorkoutsForUser(userId)
        
        assertEquals(2, unsyncedWorkouts.size)
        assertTrue(unsyncedWorkouts.all { it.userId == userId })
        assertTrue(unsyncedWorkouts.any { it.name == "Workout 1" })
        assertTrue(unsyncedWorkouts.any { it.name == "Workout 2" })
    }

    @Test
    fun getActiveWorkoutForUser_shouldReturnInProgressWorkout() = runTest {
        val userId = "test-user-active"
        
        val completedWorkout = TestDataFactory.createWorkout(
            userId = userId,
            name = "Completed Workout",
            status = WorkoutStatus.COMPLETED
        )
        val activeWorkout = TestDataFactory.createWorkout(
            userId = userId,
            name = "Active Workout",
            status = WorkoutStatus.IN_PROGRESS
        )
        
        workoutRepository.saveWorkout(completedWorkout)
        workoutRepository.saveWorkout(activeWorkout)
        
        val retrievedActiveWorkout = workoutRepository.getActiveWorkoutForUser(userId)
        
        assertNotNull(retrievedActiveWorkout)
        assertEquals("Active Workout", retrievedActiveWorkout!!.name)
        assertEquals(WorkoutStatus.IN_PROGRESS, retrievedActiveWorkout.status)
    }

    @Test
    fun deleteAllWorkoutsForUser_shouldOnlyDeleteForSpecifiedUser() = runTest {
        val user1Id = "user-1-delete-all"
        val user2Id = "user-2-delete-all"
        
        // Create workouts for both users
        val user1Workout = TestDataFactory.createWorkout(userId = user1Id, name = "User 1 Workout")
        val user2Workout = TestDataFactory.createWorkout(userId = user2Id, name = "User 2 Workout")
        
        workoutRepository.saveWorkout(user1Workout)
        workoutRepository.saveWorkout(user2Workout)
        
        // Delete all workouts for user 1
        val result = workoutRepository.deleteAllWorkoutsForUser(user1Id)
        assertTrue(result.isSuccess)
        
        // Verify user 1 workouts deleted, user 2 workouts preserved
        val user1Workouts = workoutRepository.getAllWorkoutsForUser(user1Id).first()
        val user2Workouts = workoutRepository.getAllWorkoutsForUser(user2Id).first()
        
        assertEquals(0, user1Workouts.size)
        assertEquals(1, user2Workouts.size)
        assertEquals("User 2 Workout", user2Workouts.first().name)
    }

    @Test
    fun workoutFlow_shouldEmitUpdatesReactively() = runTest {
        val userId = "test-user-reactive"
        
        workoutRepository.getAllWorkoutsForUser(userId).test {
            // Initial empty state
            assertEquals(0, awaitItem().size)
            
            // Add first workout
            val workout1 = TestDataFactory.createWorkout(userId = userId, name = "Workout 1")
            workoutRepository.saveWorkout(workout1)
            assertEquals(1, awaitItem().size)
            
            // Add second workout
            val workout2 = TestDataFactory.createWorkout(userId = userId, name = "Workout 2")
            workoutRepository.saveWorkout(workout2)
            assertEquals(2, awaitItem().size)
            
            // Delete one workout
            workoutRepository.deleteWorkoutForUser(workout1.id, userId)
            val remainingWorkouts = awaitItem()
            assertEquals(1, remainingWorkouts.size)
            assertEquals("Workout 2", remainingWorkouts.first().name)
        }
    }

    @Test
    fun markWorkoutsAsSyncedForUser_shouldUpdateSyncStatus() = runTest {
        val userId = "test-user-sync-status"
        
        // Create and save workout
        val workout = TestDataFactory.createWorkout(userId = userId, name = "Test Sync Workout")
        workoutRepository.saveWorkout(workout)
        
        // Verify initially unsynced
        assertEquals(1, workoutRepository.getUnsyncedCountForUser(userId))
        
        // Mark as synced
        val result = workoutRepository.markWorkoutsAsSyncedForUser(listOf(workout.id.value), userId)
        assertTrue(result.isSuccess)
        
        // Verify now synced
        assertEquals(0, workoutRepository.getUnsyncedCountForUser(userId))
    }
} 