package com.example.liftrix.debug

import android.content.Context
import com.example.liftrix.data.local.dao.UserAccountDao
import com.example.liftrix.data.local.dao.UserProfileDao
import com.example.liftrix.data.local.entity.UserAccountEntity
import com.example.liftrix.data.local.entity.UserProfileEntity
import com.example.liftrix.sync.UserPublicSyncWorker
import androidx.work.WorkManager
import com.example.liftrix.core.workmanager.WorkManagerProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

/**
 * Debug utility for generating test user data to validate search functionality.
 * 
 * This class creates sample users with varied usernames, fitness levels, and equipment
 * to test the user search system end-to-end. Only used in debug builds for testing.
 * 
 * Features:
 * - Creates 10+ sample users with searchable usernames
 * - Varies fitness levels, equipment, and goals for comprehensive testing
 * - Automatically triggers sync to populate Firebase collections
 * - Provides realistic test data for search validation
 */
@Singleton
class UserSearchTestDataGenerator @Inject constructor(
    private val userAccountDao: UserAccountDao,
    private val userProfileDao: UserProfileDao,
    @ApplicationContext private val context: Context
) {
    
    private val workManager: WorkManager
        get() = WorkManagerProvider.getInstance(context)

    companion object {
        private const val TEST_USER_PREFIX = "test_user_"
    }

    /**
     * Generates comprehensive test user data for search validation.
     * 
     * @return Number of test users created
     */
    suspend fun generateTestUsers(): Int = withContext(Dispatchers.IO) {
        try {
            Timber.d("UserSearchTestDataGenerator: Starting test user generation")
            
            val testUsers = createTestUserData()
            var createdCount = 0
            
            for (testUser in testUsers) {
                try {
                    // Create user account
                    userAccountDao.insertAccount(testUser.account)
                    
                    // Create user profile
                    userProfileDao.insertProfile(testUser.profile)
                    
                    // Trigger sync to populate Firebase collections
                    val syncRequest = UserPublicSyncWorker.createWorkRequest(
                        userId = testUser.account.userId,
                        forceSync = true
                    )
                    workManager.enqueue(syncRequest)
                    
                    createdCount++
                    Timber.d("Created test user: ${testUser.account.username} (${testUser.account.userId})")
                    
                } catch (e: Exception) {
                    Timber.w(e, "Failed to create test user: ${testUser.account.username}")
                }
            }
            
            Timber.d("UserSearchTestDataGenerator: Created $createdCount test users")
            createdCount
            
        } catch (e: Exception) {
            Timber.e(e, "UserSearchTestDataGenerator: Failed to generate test users")
            0
        }
    }
    
    /**
     * Removes all test users from the database.
     * 
     * @return Number of test users removed
     */
    suspend fun cleanupTestUsers(): Int = withContext(Dispatchers.IO) {
        try {
            Timber.d("UserSearchTestDataGenerator: Starting test user cleanup")
            
            // Get all accounts that start with test user prefix
            val allAccounts = userAccountDao.getAllAccounts()
            val testAccounts = allAccounts.filter { 
                it.username?.startsWith(TEST_USER_PREFIX) == true 
            }
            
            var removedCount = 0
            for (account in testAccounts) {
                try {
                    // Delete account and profile
                    userAccountDao.deleteAccountForUser(account.userId)
                    userProfileDao.deleteProfileForUser(account.userId)
                    
                    removedCount++
                    Timber.d("Removed test user: ${account.username} (${account.userId})")
                    
                } catch (e: Exception) {
                    Timber.w(e, "Failed to remove test user: ${account.username}")
                }
            }
            
            Timber.d("UserSearchTestDataGenerator: Removed $removedCount test users")
            removedCount
            
        } catch (e: Exception) {
            Timber.e(e, "UserSearchTestDataGenerator: Failed to cleanup test users")
            0
        }
    }
    
    /**
     * Creates test user data with realistic variety for search testing.
     */
    private fun createTestUserData(): List<TestUserData> {
        val now = LocalDateTime.now()
        
        return listOf(
            // Fitness-focused users
            createTestUser(
                email = "john.fit@testmail.com",
                username = "${TEST_USER_PREFIX}john_fit",
                displayName = "John Fitness",
                age = 28,
                weightKg = 75.0,
                heightCm = 180.0,
                fitnessLevel = "INTERMEDIATE",
                goals = """["MUSCLE_BUILDING", "STRENGTH"]""",
                availableEquipment = """["DUMBBELLS", "BARBELL", "BENCH"]""",
                workoutFrequency = 4,
                preferredWorkoutDuration = 60,
                bio = "Passionate about strength training and muscle building",
                totalWorkouts = 85,
                currentStreak = 5,
                longestStreak = 12,
                daysAgo = 120,
                now = now
            ),
            
            createTestUser(
                email = "sarah.strength@testmail.com", 
                username = "${TEST_USER_PREFIX}sarah_strength",
                displayName = "Sarah Strong",
                age = 25,
                weightKg = 62.0,
                heightCm = 165.0,
                fitnessLevel = "ADVANCED",
                goals = """["STRENGTH", "ENDURANCE"]""",
                availableEquipment = """["BARBELL", "DUMBBELLS", "KETTLEBELLS"]""",
                workoutFrequency = 5,
                preferredWorkoutDuration = 75,
                bio = "Competitive powerlifter and fitness enthusiast",
                totalWorkouts = 124,
                currentStreak = 8,
                longestStreak = 18,
                daysAgo = 90,
                now = now
            ),
            
            // Cardio-focused users
            createTestUser(
                email = "mike.cardio@testmail.com",
                username = "${TEST_USER_PREFIX}mike_cardio",
                displayName = "Mike Runner",
                age = 32,
                weightKg = 70.0,
                heightCm = 175.0,
                fitnessLevel = "INTERMEDIATE",
                goals = """["WEIGHT_LOSS", "ENDURANCE"]""",
                availableEquipment = """["TREADMILL", "RESISTANCE_BANDS"]""",
                workoutFrequency = 6,
                preferredWorkoutDuration = 45,
                bio = "Marathon runner and endurance athlete",
                totalWorkouts = 78,
                currentStreak = 12,
                longestStreak = 15,
                daysAgo = 60,
                now = now
            ),
            
            createTestUser(
                email = "lisa.yoga@testmail.com",
                username = "${TEST_USER_PREFIX}lisa_yoga",
                displayName = "Lisa Zen",
                age = 29,
                weightKg = 58.0,
                heightCm = 162.0,
                fitnessLevel = "INTERMEDIATE",
                goals = """["FLEXIBILITY", "GENERAL_FITNESS"]""",
                availableEquipment = """["YOGA_MAT", "RESISTANCE_BANDS"]""",
                workoutFrequency = 4,
                preferredWorkoutDuration = 60,
                bio = "Yoga instructor and mindfulness advocate",
                totalWorkouts = 52,
                currentStreak = 6,
                longestStreak = 14,
                daysAgo = 45,
                now = now
            ),
            
            // Beginners
            createTestUser(
                email = "alex.newbie@testmail.com",
                username = "${TEST_USER_PREFIX}alex_newbie",
                displayName = "Alex Beginner",
                age = 22,
                weightKg = 68.0,
                heightCm = 170.0,
                fitnessLevel = "BEGINNER",
                goals = """["GENERAL_FITNESS", "WEIGHT_LOSS"]""",
                availableEquipment = """["BODYWEIGHT"]""",
                workoutFrequency = 3,
                preferredWorkoutDuration = 30,
                bio = "Just started my fitness journey!",
                totalWorkouts = 15,
                currentStreak = 3,
                longestStreak = 4,
                daysAgo = 30,
                now = now
            ),
            
            // CrossFit enthusiasts
            createTestUser(
                email = "tom.crossfit@testmail.com",
                username = "${TEST_USER_PREFIX}tom_crossfit",
                displayName = "Tom CrossFit",
                age = 35,
                weightKg = 82.0,
                heightCm = 185.0,
                fitnessLevel = "ADVANCED",
                goals = """["STRENGTH", "ENDURANCE", "MUSCLE_BUILDING"]""",
                availableEquipment = """["BARBELL", "DUMBBELLS", "KETTLEBELLS", "PULL_UP_BAR"]""",
                workoutFrequency = 5,
                preferredWorkoutDuration = 90,
                bio = "CrossFit athlete and functional fitness coach",
                totalWorkouts = 180,
                currentStreak = 15,
                longestStreak = 25,
                daysAgo = 200,
                now = now
            ),
            
            // Bodyweight specialists
            createTestUser(
                email = "emma.bodyweight@testmail.com",
                username = "${TEST_USER_PREFIX}emma_bodyweight",
                displayName = "Emma Calisthenics",
                age = 26,
                weightKg = 55.0,
                heightCm = 160.0,
                fitnessLevel = "INTERMEDIATE",
                goals = """["STRENGTH", "FLEXIBILITY"]""",
                availableEquipment = """["BODYWEIGHT", "PULL_UP_BAR"]""",
                workoutFrequency = 4,
                preferredWorkoutDuration = 45,
                bio = "Calisthenics enthusiast and movement specialist",
                totalWorkouts = 68,
                currentStreak = 7,
                longestStreak = 11,
                daysAgo = 75,
                now = now
            ),
            
            // More diverse users for testing
            createTestUser(
                email = "david.powerlifter@testmail.com",
                username = "${TEST_USER_PREFIX}david_powerlifter",
                displayName = "David Power",
                age = 31,
                weightKg = 95.0,
                heightCm = 190.0,
                fitnessLevel = "ADVANCED",
                goals = """["STRENGTH"]""",
                availableEquipment = """["BARBELL", "BENCH", "SQUAT_RACK"]""",
                workoutFrequency = 4,
                preferredWorkoutDuration = 120,
                bio = "Powerlifter competing in 105kg division",
                totalWorkouts = 142,
                currentStreak = 4,
                longestStreak = 20,
                daysAgo = 150,
                now = now
            ),
            
            createTestUser(
                email = "jenny.hiit@testmail.com",
                username = "${TEST_USER_PREFIX}jenny_hiit",
                displayName = "Jenny HIIT",
                age = 24,
                weightKg = 60.0,
                heightCm = 168.0,
                fitnessLevel = "INTERMEDIATE",
                goals = """["WEIGHT_LOSS", "ENDURANCE"]""",
                availableEquipment = """["DUMBBELLS", "KETTLEBELLS", "RESISTANCE_BANDS"]""",
                workoutFrequency = 5,
                preferredWorkoutDuration = 40,
                bio = "HIIT trainer and fat loss specialist",
                totalWorkouts = 95,
                currentStreak = 9,
                longestStreak = 13,
                daysAgo = 80,
                now = now
            ),
            
            createTestUser(
                email = "ryan.swimmer@testmail.com",
                username = "${TEST_USER_PREFIX}ryan_swimmer",
                displayName = "Ryan Waters",
                age = 27,
                weightKg = 73.0,
                heightCm = 178.0,
                fitnessLevel = "ADVANCED",
                goals = """["ENDURANCE", "GENERAL_FITNESS"]""",
                availableEquipment = """["RESISTANCE_BANDS", "PULL_UP_BAR"]""",
                workoutFrequency = 6,
                preferredWorkoutDuration = 50,
                bio = "Competitive swimmer and water sports enthusiast",
                totalWorkouts = 108,
                currentStreak = 11,
                longestStreak = 17,
                daysAgo = 110,
                now = now
            )
        )
    }
    
    /**
     * Helper function to create a test user with matching account and profile IDs.
     */
    private fun createTestUser(
        email: String,
        username: String,
        displayName: String,
        age: Int,
        weightKg: Double,
        heightCm: Double,
        fitnessLevel: String,
        goals: String,
        availableEquipment: String,
        workoutFrequency: Int,
        preferredWorkoutDuration: Int,
        bio: String,
        totalWorkouts: Int,
        currentStreak: Int,
        longestStreak: Int,
        daysAgo: Long,
        now: LocalDateTime
    ): TestUserData {
        val userId = generateUserId()
        val accountCreatedAt = now.minusDays(daysAgo)
        
        return TestUserData(
            account = UserAccountEntity(
                userId = userId,
                email = email,
                username = username,
                displayName = displayName,
                emailVerified = true,
                accountCreatedAt = accountCreatedAt
            ),
            profile = UserProfileEntity(
                id = generateProfileId(),
                userId = userId,
                displayName = displayName,
                age = age,
                weightKg = weightKg,
                heightCm = heightCm,
                fitnessLevel = fitnessLevel,
                goals = goals,
                availableEquipment = availableEquipment,
                workoutFrequency = workoutFrequency,
                preferredWorkoutDuration = preferredWorkoutDuration,
                completedAt = accountCreatedAt.plusDays(1), // Profile completed 1 day after account creation
                bio = bio,
                totalWorkouts = totalWorkouts,
                currentStreak = currentStreak,
                longestStreak = longestStreak,
                isPublic = true,
                createdAt = accountCreatedAt,
                updatedAt = now.minusDays(1),
                memberSince = accountCreatedAt
            )
        )
    }
    
    /**
     * Data class to hold test user account and profile data together.
     */
    private data class TestUserData(
        val account: UserAccountEntity,
        val profile: UserProfileEntity
    )
    
    private fun generateUserId(): String = "test_${UUID.randomUUID()}"
    private fun generateProfileId(): String = "profile_${UUID.randomUUID()}"
}