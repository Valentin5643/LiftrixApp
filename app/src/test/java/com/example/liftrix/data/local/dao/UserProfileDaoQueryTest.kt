package com.example.liftrix.data.local.dao

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.liftrix.data.local.LiftrixDatabase
import com.example.liftrix.data.local.entity.UserProfileEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime

/**
 * Room query test harness to validate UserProfileDao SQL queries
 * and catch schema mismatches before compilation.
 */
@RunWith(AndroidJUnit4::class)
class UserProfileDaoQueryTest {

    private lateinit var database: LiftrixDatabase
    private lateinit var userProfileDao: UserProfileDao

    @Before
    fun setUp() {
        // Create in-memory database for testing
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().context,
            LiftrixDatabase::class.java
        ).allowMainThreadQueries().build()
        
        userProfileDao = database.userProfileDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun validateAllUserProfileDaoQueries() = runBlocking {
        // Create a test profile to work with
        val testProfile = UserProfileEntity(
            id = "test_profile_1",
            userId = "test_user_1",
            displayName = "Test User",
            age = 25,
            weightKg = 70.0,
            heightCm = 175.0,
            fitnessLevel = "beginner",
            goals = "fitness",
            availableEquipment = "home",
            workoutFrequency = 3,
            preferredWorkoutDuration = 30,
            completedAt = null, // Not completed yet
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
            isSynced = false,
            syncVersion = 1L
        )

        // Test Insert
        val insertResult = userProfileDao.insertProfile(testProfile)
        assert(insertResult > 0) { "Profile insertion should return positive ID" }

        // Test Basic Queries
        val profileFlow = userProfileDao.getProfileForUser("test_user_1")
        assert(profileFlow != null) { "getProfileForUser should return Flow" }

        val profileSuspend = userProfileDao.getProfileForUserSuspend("test_user_1")
        assert(profileSuspend != null) { "getProfileForUserSuspend should return profile" }
        assert(profileSuspend!!.userId == "test_user_1") { "Retrieved profile should match" }

        // Test Sync Queries
        val unsyncedProfiles = userProfileDao.getUnsyncedProfiles("test_user_1")
        assert(unsyncedProfiles.isNotEmpty()) { "Should have unsynced profiles" }

        val unsyncedCount = userProfileDao.getUnsyncedProfilesCount("test_user_1")
        assert(unsyncedCount > 0) { "Should have positive unsynced count" }

        // Test Completion Queries (these were causing the compilation errors)
        val incompleteProfiles = userProfileDao.getIncompleteProfiles("test_user_1")
        assert(incompleteProfiles.isNotEmpty()) { "Should have incomplete profiles" }

        val completedProfiles = userProfileDao.getCompletedProfiles("test_user_1")
        assert(completedProfiles.isEmpty()) { "Should have no completed profiles initially" }

        val hasProfile = userProfileDao.hasProfile("test_user_1")
        assert(hasProfile) { "Should have profile for test_user_1" }

        val hasCompletedProfile = userProfileDao.hasCompletedProfile("test_user_1")
        assert(!hasCompletedProfile) { "Should not have completed profile initially" }

        // Test Update Operations
        val updateResult = userProfileDao.updateSyncStatus("test_user_1", true, 123L)
        assert(updateResult > 0) { "Update sync status should affect rows" }

        val markSyncedResult = userProfileDao.markProfilesAsSynced(listOf("test_user_1"), 456L)
        assert(markSyncedResult > 0) { "Mark as synced should affect rows" }

        // Test Mark as Completed
        val markCompletedResult = userProfileDao.markProfileAsCompleted(
            "test_user_1", 
            LocalDateTime.now().toString()
        )
        assert(markCompletedResult > 0) { "Mark as completed should affect rows" }

        // Verify completion status changed
        val hasCompletedProfileAfter = userProfileDao.hasCompletedProfile("test_user_1")
        assert(hasCompletedProfileAfter) { "Should have completed profile after marking" }

        val completedProfilesAfter = userProfileDao.getCompletedProfiles("test_user_1")
        assert(completedProfilesAfter.isNotEmpty()) { "Should have completed profiles after marking" }

        val incompleteProfilesAfter = userProfileDao.getIncompleteProfiles("test_user_1")
        assert(incompleteProfilesAfter.isEmpty()) { "Should have no incomplete profiles after marking" }

        // Test Delete Operations
        val deleteUserResult = userProfileDao.deleteProfileForUser("test_user_1")
        assert(deleteUserResult > 0) { "Delete profile for user should affect rows" }

        val hasProfileAfterDelete = userProfileDao.hasProfile("test_user_1")
        assert(!hasProfileAfterDelete) { "Should not have profile after deletion" }
    }

    @Test
    fun validateBulkOperations() = runBlocking {
        // Test bulk insert
        val profiles = listOf(
            UserProfileEntity(
                id = "bulk_1",
                userId = "user_1",
                displayName = "User 1",
                age = 25,
                weightKg = 70.0,
                heightCm = 175.0,
                fitnessLevel = "beginner",
                goals = "fitness",
                availableEquipment = "home",
                workoutFrequency = 3,
                preferredWorkoutDuration = 30,
                completedAt = LocalDateTime.now(),
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now(),
                isSynced = false,
                syncVersion = 1L
            ),
            UserProfileEntity(
                id = "bulk_2",
                userId = "user_2",
                displayName = "User 2",
                age = 30,
                weightKg = 65.0,
                heightCm = 170.0,
                fitnessLevel = "intermediate",
                goals = "strength",
                availableEquipment = "gym",
                workoutFrequency = 4,
                preferredWorkoutDuration = 45,
                completedAt = null,
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now(),
                isSynced = true,
                syncVersion = 2L
            )
        )

        val bulkInsertResults = userProfileDao.insertProfiles(profiles)
        assert(bulkInsertResults.size == 2) { "Bulk insert should return 2 results" }
        assert(bulkInsertResults.all { it > 0 }) { "All bulk insert results should be positive" }

        // Test bulk sync operations
        val bulkSyncResult = userProfileDao.markProfilesAsSynced(listOf("user_1", "user_2"), 999L)
        assert(bulkSyncResult > 0) { "Bulk sync should affect rows" }

        // Test delete all
        val deleteAllResult = userProfileDao.deleteAllProfiles()
        assert(deleteAllResult > 0) { "Delete all should affect rows" }
    }

    @Test
    fun validateEdgeCases() = runBlocking {
        // Test queries with non-existent user
        val nonExistentProfile = userProfileDao.getProfileForUserSuspend("non_existent")
        assert(nonExistentProfile == null) { "Non-existent profile should return null" }

        val hasNonExistentProfile = userProfileDao.hasProfile("non_existent")
        assert(!hasNonExistentProfile) { "Non-existent profile should return false" }

        val hasNonExistentCompletedProfile = userProfileDao.hasCompletedProfile("non_existent")
        assert(!hasNonExistentCompletedProfile) { "Non-existent completed profile should return false" }

        // Test update operations on non-existent profiles
        val updateNonExistentResult = userProfileDao.updateSyncStatus("non_existent", true, 123L)
        assert(updateNonExistentResult == 0) { "Update non-existent should affect 0 rows" }

        val markNonExistentResult = userProfileDao.markProfileAsCompleted("non_existent", LocalDateTime.now().toString())
        assert(markNonExistentResult == 0) { "Mark non-existent as completed should affect 0 rows" }

        val deleteNonExistentResult = userProfileDao.deleteProfileForUser("non_existent")
        assert(deleteNonExistentResult == 0) { "Delete non-existent should affect 0 rows" }

        // Test empty states
        val emptyUnsyncedProfiles = userProfileDao.getUnsyncedProfiles("non_existent_user")
        assert(emptyUnsyncedProfiles.isEmpty()) { "Should have no unsynced profiles in empty database" }

        val emptyUnsyncedCount = userProfileDao.getUnsyncedProfilesCount("non_existent_user")
        assert(emptyUnsyncedCount == 0) { "Should have 0 unsynced count in empty database" }

        val emptyCompletedProfiles = userProfileDao.getCompletedProfiles("non_existent_user")
        assert(emptyCompletedProfiles.isEmpty()) { "Should have no completed profiles in empty database" }

        val emptyIncompleteProfiles = userProfileDao.getIncompleteProfiles("non_existent_user")
        assert(emptyIncompleteProfiles.isEmpty()) { "Should have no incomplete profiles in empty database" }
    }
} 