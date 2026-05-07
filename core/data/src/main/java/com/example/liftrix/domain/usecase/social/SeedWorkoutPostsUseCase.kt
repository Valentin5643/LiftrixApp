package com.example.liftrix.domain.usecase.social

import com.example.liftrix.data.local.dao.WorkoutPostDao
import com.example.liftrix.data.local.dao.SocialProfileDao
import com.example.liftrix.data.local.dao.WorkoutDao
import com.example.liftrix.data.local.entity.WorkoutPostEntity
import com.example.liftrix.data.local.entity.SocialProfileEntity
import com.example.liftrix.data.local.entity.WorkoutEntity
import com.example.liftrix.domain.model.WorkoutStatus
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.random.Random

/**
 * Use case to seed sample workout posts for testing the Explore feed
 * Creates realistic workout posts with varying timestamps within the last 7 days
 */
class SeedWorkoutPostsUseCase @Inject constructor(
    private val workoutPostDao: WorkoutPostDao,
    private val socialProfileDao: SocialProfileDao,
    private val workoutDao: WorkoutDao
) {
    
    suspend operator fun invoke(currentUserId: String): LiftrixResult<Int> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "SEED_POSTS_FAILED",
                errorMessage = "Failed to seed workout posts: ${throwable.message}",
                analyticsContext = mapOf("operation" to "SEED_WORKOUT_POSTS")
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            // Check if posts already exist
            val existingPostCount = workoutPostDao.getUserPostCount(currentUserId)
            if (existingPostCount > 0) {
                Timber.d("Posts already exist, skipping seeding")
                return@withContext 0
            }
            
            // Create sample users
            val sampleUsers = createSampleUsers()
            sampleUsers.forEach { profile ->
                socialProfileDao.insertProfile(profile)
            }
            
            // Create sample workouts and posts
            val posts = mutableListOf<WorkoutPostEntity>()
            val now = System.currentTimeMillis()
            val sevenDaysAgo = now - TimeUnit.DAYS.toMillis(7)
            
            // Sample workout names and exercises
            val workoutTemplates = listOf(
                WorkoutTemplate("Push Day", listOf("Bench Press", "Shoulder Press", "Dips"), "Upper body strength session"),
                WorkoutTemplate("Pull Day", listOf("Deadlifts", "Pull-ups", "Barbell Rows"), "Back and biceps focused"),
                WorkoutTemplate("Leg Day", listOf("Squats", "Leg Press", "Calf Raises"), "Never skip leg day!"),
                WorkoutTemplate("HIIT Cardio", listOf("Burpees", "Mountain Climbers", "Jump Rope"), "High intensity interval training"),
                WorkoutTemplate("Full Body", listOf("Clean & Jerk", "Thrusters", "Box Jumps"), "Complete body workout"),
                WorkoutTemplate("Arms & Abs", listOf("Bicep Curls", "Tricep Extensions", "Planks"), "Arm pump and core work"),
                WorkoutTemplate("Chest & Triceps", listOf("Incline Press", "Cable Flyes", "Skull Crushers"), "Chest pump day"),
                WorkoutTemplate("Back & Biceps", listOf("T-Bar Rows", "Lat Pulldowns", "Hammer Curls"), "Building that V-taper"),
                WorkoutTemplate("Olympic Lifting", listOf("Snatch", "Clean", "Front Squat"), "Technical lifting session"),
                WorkoutTemplate("CrossFit WOD", listOf("Wall Balls", "Double Unders", "Muscle Ups"), "Workout of the day completed")
            )
            
            // Create 30 posts distributed over the last 7 days
            for (i in 0 until 30) {
                val user = sampleUsers.random()
                val template = workoutTemplates.random()
                val postId = UUID.randomUUID().toString()
                val workoutId = UUID.randomUUID().toString()
                
                // Create workout entity with proper structure
                val startInstant = Instant.ofEpochMilli(now - TimeUnit.HOURS.toMillis(Random.nextLong(1, 3)))
                val endInstant = Instant.ofEpochMilli(now)
                
                // Create simple exercise JSON
                val exercisesJson = Json.encodeToString(
                    template.exercises.map { exerciseName ->
                        mapOf(
                            "name" to exerciseName,
                            "sets" to Random.nextInt(3, 5),
                            "reps" to Random.nextInt(8, 15),
                            "weight" to Random.nextDouble(20.0, 100.0)
                        )
                    }
                )
                
                val workoutEntity = WorkoutEntity(
                    id = workoutId,
                    userId = user.userId,
                    name = template.name,
                    date = LocalDate.now().minusDays(Random.nextLong(0, 7)),
                    exercisesJson = exercisesJson,
                    status = WorkoutStatus.COMPLETED,
                    startTime = startInstant,
                    endTime = endInstant,
                    notes = template.caption,
                    templateId = null,
                    createdAt = Instant.ofEpochMilli(now),
                    updatedAt = Instant.ofEpochMilli(now),
                    isSynced = true,
                    syncVersion = 1
                )
                workoutDao.insertWorkout(workoutEntity)
                
                // Random timestamp within the last 7 days
                val randomDaysAgo = Random.nextLong(0, 7)
                val randomHoursOffset = Random.nextLong(0, 24)
                val postTimestamp = now - TimeUnit.DAYS.toMillis(randomDaysAgo) - TimeUnit.HOURS.toMillis(randomHoursOffset)
                
                // Create post entity
                val post = WorkoutPostEntity(
                    id = postId,
                    userId = user.userId,
                    workoutId = workoutId,
                    caption = "${template.caption} 💪 #fitness #workout #gym",
                    mediaUrls = null,
                    mediaThumbnails = null,
                    workoutDuration = Random.nextInt(30, 120) * 60, // 30-120 minutes in seconds
                    totalVolume = Random.nextDouble(5000.0, 25000.0),
                    exercisesCount = template.exercises.size,
                    prsCount = if (Random.nextBoolean() && Random.nextBoolean()) Random.nextInt(1, 3) else 0, // 25% chance of PRs
                    likeCount = Random.nextInt(0, 50),
                    commentCount = Random.nextInt(0, 15),
                    shareCount = Random.nextInt(0, 5),
                    saveCount = Random.nextInt(0, 10),
                    visibility = "PUBLIC", // All posts public for Explore feed
                    createdAt = postTimestamp,
                    updatedAt = postTimestamp,
                    isSynced = true,
                    syncVersion = 1
                )
                posts.add(post)
            }
            
            // Also create a few posts from the current user
            for (i in 0 until 5) {
                val template = workoutTemplates.random()
                val postId = UUID.randomUUID().toString()
                val workoutId = UUID.randomUUID().toString()
                
                // Create workout entity for current user
                val startInstant = Instant.ofEpochMilli(now - TimeUnit.HOURS.toMillis(2))
                val endInstant = Instant.ofEpochMilli(now)
                
                // Create simple exercise JSON
                val exercisesJson = Json.encodeToString(
                    template.exercises.map { exerciseName ->
                        mapOf(
                            "name" to exerciseName,
                            "sets" to Random.nextInt(3, 5),
                            "reps" to Random.nextInt(8, 15),
                            "weight" to Random.nextDouble(20.0, 100.0)
                        )
                    }
                )
                
                val workoutEntity = WorkoutEntity(
                    id = workoutId,
                    userId = currentUserId,
                    name = template.name,
                    date = LocalDate.now().minusDays(Random.nextLong(0, 3)),
                    exercisesJson = exercisesJson,
                    status = WorkoutStatus.COMPLETED,
                    startTime = startInstant,
                    endTime = endInstant,
                    notes = "My personal workout",
                    templateId = null,
                    createdAt = Instant.ofEpochMilli(now),
                    updatedAt = Instant.ofEpochMilli(now),
                    isSynced = true,
                    syncVersion = 1
                )
                workoutDao.insertWorkout(workoutEntity)
                
                val randomDaysAgo = Random.nextLong(0, 3)
                val postTimestamp = now - TimeUnit.DAYS.toMillis(randomDaysAgo)
                
                val post = WorkoutPostEntity(
                    id = postId,
                    userId = currentUserId,
                    workoutId = workoutId,
                    caption = "Great workout today! ${template.caption}",
                    mediaUrls = null,
                    mediaThumbnails = null,
                    workoutDuration = Random.nextInt(45, 90) * 60,
                    totalVolume = Random.nextDouble(5000.0, 25000.0),
                    exercisesCount = template.exercises.size,
                    prsCount = if (Random.nextBoolean()) 1 else 0,
                    likeCount = Random.nextInt(10, 100),
                    commentCount = Random.nextInt(5, 30),
                    shareCount = Random.nextInt(0, 10),
                    saveCount = Random.nextInt(5, 20),
                    visibility = "PUBLIC",
                    createdAt = postTimestamp,
                    updatedAt = postTimestamp,
                    isSynced = true,
                    syncVersion = 1
                )
                posts.add(post)
            }
            
            // Insert all posts
            workoutPostDao.insertPosts(posts)
            
            Timber.d("Successfully seeded ${posts.size} workout posts")
            posts.size
        }
    }
    
    private fun createSampleUsers(): List<SocialProfileEntity> {
        val userNames = listOf(
            "FitnessPro", "IronWarrior", "GymRat", "SwolePatrol", "LiftingLegend",
            "CardioKing", "YogaMaster", "CrossFitChamp", "PowerLifter", "BodyBuilder",
            "FitnessGuru", "WorkoutWarrior", "GymShark", "FitFam", "BeastMode"
        )
        
        val userBios = listOf(
            "Lifting heavy, living happy",
            "No pain, no gain",
            "Fitness is a lifestyle",
            "Building strength one rep at a time",
            "Chasing PRs and crushing goals"
        )
        
        return userNames.map { username ->
            val userId = UUID.randomUUID().toString()
            val memberSinceTimestamp = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(Random.nextLong(30, 365))
            SocialProfileEntity(
                userId = userId,
                username = username.lowercase(),
                displayName = username,
                bio = userBios.random(),
                profilePhotoUrl = null,
                coverPhotoUrl = null,
                workoutCount = Random.nextInt(10, 200),
                followerCount = Random.nextInt(100, 5000),
                followingCount = Random.nextInt(50, 500),
                memberSince = memberSinceTimestamp,
                lastActive = System.currentTimeMillis(),
                isVerified = Random.nextBoolean() && Random.nextBoolean(), // 25% chance
                isPrivate = false, // All public for Explore feed
                hideFromSuggestions = false,
                allowFriendRequests = true,
                instagramHandle = null,
                youtubeChannel = null,
                personalWebsite = null,
                isSynced = true,
                syncVersion = 1,
                createdAt = memberSinceTimestamp,
                updatedAt = System.currentTimeMillis()
            )
        }
    }
    
    data class WorkoutTemplate(
        val name: String,
        val exercises: List<String>,
        val caption: String
    )
}