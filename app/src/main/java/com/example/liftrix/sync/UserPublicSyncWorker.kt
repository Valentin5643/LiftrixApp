package com.example.liftrix.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.BackoffPolicy
import androidx.work.workDataOf
import com.example.liftrix.data.local.dao.UserAccountDao
import com.example.liftrix.data.local.dao.UserProfileDao
import com.example.liftrix.data.local.dao.WorkoutDao
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.FieldValue
import com.google.gson.Gson
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * Sync worker that populates the users_public Firebase collection with searchable user data.
 * 
 * This worker combines data from:
 * - UserAccountEntity (username, email, display_name)
 * - UserProfileEntity (bio, fitness_level, goals, equipment, workout stats)
 * - Workout statistics (total workouts, streak data)
 * 
 * The users_public collection is used by the UserSearchRepository for user discovery.
 * This ensures that usernames set in the UsernameChangeScreen become searchable.
 */
@HiltWorker
class UserPublicSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val userAccountDao: UserAccountDao,
    private val userProfileDao: UserProfileDao,
    private val workoutDao: WorkoutDao,
    private val firestore: FirebaseFirestore,
    private val gson: Gson
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "user_public_sync_work"
        const val KEY_SYNC_COUNT = "sync_count"
        const val KEY_ERROR_MESSAGE = "error_message"
        private const val MAX_RETRY_COUNT = 3
        private const val USERS_PUBLIC_COLLECTION = "users_public"
        private const val USER_SEARCH_CACHE_COLLECTION = "user_search_cache"
        
        fun createWorkRequest(userId: String, forceSync: Boolean = false): OneTimeWorkRequest {
            return OneTimeWorkRequestBuilder<UserPublicSyncWorker>()
                .setInputData(workDataOf(
                    "userId" to userId,
                    "forceSync" to forceSync
                ))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    15,
                    TimeUnit.SECONDS
                )
                .addTag("user_public_sync")
                .build()
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val userId = inputData.getString("userId") ?: return@withContext Result.failure(
                Data.Builder()
                    .putString(KEY_ERROR_MESSAGE, "User ID not provided")
                    .build()
            )
            
            val forceSync = inputData.getBoolean("forceSync", false)

            Timber.d("USER_SEARCH_DEBUG", "SyncWorker: Starting sync for userId=$userId (forceSync=$forceSync)")
            
            // Get user account data (username, email, display name)
            val userAccount = userAccountDao.getAccountForUserSuspend(userId)
            if (userAccount == null) {
                Timber.e("USER_SEARCH_DEBUG", "SyncWorker: No user account found for $userId")
                return@withContext Result.failure(
                    Data.Builder()
                        .putString(KEY_ERROR_MESSAGE, "User account not found for user $userId")
                        .build()
                )
            }
            
            Timber.d("USER_SEARCH_DEBUG", "SyncWorker: Found account - username=${userAccount.username}, displayName=${userAccount.displayName}")
            
            // Get user profile data (bio, fitness level, etc.)
            val userProfile = userProfileDao.getProfileForUserSuspend(userId)
            
            // Calculate workout statistics
            val totalWorkouts = workoutDao.getWorkoutCountForUser(userId)
            val workoutStats = calculateWorkoutStats(userId)
            
            // Generate search tokens for better searchability
            val searchTokens = generateSearchTokens(userAccount, userProfile)
            val searchKeywords = generateSearchKeywords(userAccount, userProfile)
            
            Timber.d("USER_SEARCH_DEBUG", "SyncWorker: Generated ${searchTokens.size} tokens: ${searchTokens.joinToString()}")
            Timber.d("USER_SEARCH_DEBUG", "SyncWorker: Generated ${searchKeywords.size} keywords: ${searchKeywords.joinToString()}")
            
            // Prepare public user data
            val isPublicProfile = userProfile?.isPublic ?: true
            Timber.d("USER_SEARCH_DEBUG", "SyncWorker: isPublic=$isPublicProfile for username=${userAccount.username}")
            
            val publicUserData = mutableMapOf<String, Any?>(
                "userId" to userId,
                "username" to userAccount.username,
                "displayName" to (userAccount.displayName ?: userProfile?.displayName ?: userAccount.username ?: userAccount.email?.substringBefore("@") ?: "User"),
                "bio" to userProfile?.bio,
                "age" to userProfile?.age,
                "fitnessLevel" to userProfile?.fitnessLevel,
                "isPublic" to isPublicProfile,
                "isPrivate" to !isPublicProfile,
                "totalWorkouts" to totalWorkouts,
                "currentStreak" to workoutStats.currentStreak,
                "longestStreak" to workoutStats.longestStreak,
                "totalWorkoutTime" to workoutStats.totalWorkoutTime,
                "averageWorkoutTime" to workoutStats.averageWorkoutTime,
                "followersCount" to 0, // Will be calculated by social features
                "followingCount" to 0, // Will be calculated by social features
                "profileImageUrl" to userProfile?.profileImageUrl,
                "coverImageUrl" to null, // Future feature
                "isVerified" to false, // Future feature
                "memberSince" to (userProfile?.memberSince?.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) 
                    ?: userAccount.accountCreatedAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)),
                "lastActiveAt" to (userProfile?.lastActiveAt?.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    ?: LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)),
                "profileViews" to (userProfile?.profileViewsCount ?: 0),
                "profileCompletionPercentage" to (userProfile?.profileCompletionPercentage ?: 0),
                
                // Parse fitness goals and equipment from JSON
                "fitnessGoals" to parseJsonArray(userProfile?.goals),
                "availableEquipment" to parseJsonArray(userProfile?.availableEquipment),
                
                // Achievements (will be populated by achievement sync)
                "publicAchievements" to emptyList<Map<String, Any>>(),
                
                // Search optimization
                "searchTokens" to searchTokens,
                "searchKeywords" to searchKeywords,
                
                // Metadata
                "syncVersion" to System.currentTimeMillis(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            
            Timber.d("USER_SEARCH_DEBUG", "SyncWorker: Writing to $USERS_PUBLIC_COLLECTION")
            
            // Sync to users_public collection
            val publicDocRef = firestore
                .collection(USERS_PUBLIC_COLLECTION)
                .document(userId)
            
            publicDocRef.set(publicUserData, SetOptions.merge()).await()
            
            Timber.d("USER_SEARCH_DEBUG", "SyncWorker: Successfully wrote to $USERS_PUBLIC_COLLECTION")
            
            // Also sync to user_search_cache collection for tokenized search
            val searchCacheData = mapOf(
                "userId" to userId,
                "username" to userAccount.username,
                "displayName" to publicUserData["displayName"],
                "bio" to userProfile?.bio,
                "fitnessLevel" to userProfile?.fitnessLevel,
                "totalWorkouts" to totalWorkouts,
                "memberSince" to publicUserData["memberSince"],
                "lastActiveAt" to publicUserData["lastActiveAt"],
                "profileImageUrl" to userProfile?.profileImageUrl,
                "isPublic" to (userProfile?.isPublic ?: true),
                "searchTokens" to searchTokens,
                "keywords" to searchKeywords,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            
            Timber.d("USER_SEARCH_DEBUG", "SyncWorker: Writing to $USER_SEARCH_CACHE_COLLECTION")
            
            val searchCacheDocRef = firestore
                .collection(USER_SEARCH_CACHE_COLLECTION)
                .document(userId)
                
            searchCacheDocRef.set(searchCacheData, SetOptions.merge()).await()
            
            Timber.d("USER_SEARCH_DEBUG", "SyncWorker: Successfully wrote to $USER_SEARCH_CACHE_COLLECTION")
            Timber.d("USER_SEARCH_DEBUG", "SyncWorker: SYNC COMPLETE - User should be searchable now!")
            
            return@withContext Result.success(
                Data.Builder()
                    .putInt(KEY_SYNC_COUNT, 1)
                    .build()
            )
            
        } catch (e: Exception) {
            Timber.e(e, "UserPublicSyncWorker failed for user ${inputData.getString("userId")}")
            
            if (runAttemptCount < MAX_RETRY_COUNT) {
                Result.retry()
            } else {
                Result.failure(
                    Data.Builder()
                        .putString(KEY_ERROR_MESSAGE, e.message ?: "Unknown error")
                        .build()
                )
            }
        }
    }
    
    /**
     * Calculate workout statistics for the user
     */
    private suspend fun calculateWorkoutStats(userId: String): WorkoutStats {
        return try {
            val totalWorkouts = workoutDao.getWorkoutCountForUser(userId)
            // For now, use simplified statistics. In the future, we'd calculate these from workout data
            val totalWorkoutTime = 0L // Would need to implement duration calculation
            val averageWorkoutTime = 0L // Would need workout duration data
            
            // Calculate streaks (simplified version - would need more sophisticated streak calculation)
            var currentStreak = 0
            var longestStreak = 0
            
            // This is a simplified streak calculation
            // In production, you'd want more sophisticated date-based streak logic
            if (totalWorkouts > 0) {
                currentStreak = minOf(totalWorkouts, 7) // Simple streak based on recent workouts
                longestStreak = minOf(totalWorkouts, 15) // Cap at 15 for now
            }
            
            WorkoutStats(
                currentStreak = currentStreak,
                longestStreak = longestStreak,
                totalWorkoutTime = totalWorkoutTime,
                averageWorkoutTime = averageWorkoutTime
            )
        } catch (e: Exception) {
            Timber.e(e, "Error calculating workout stats for user $userId")
            WorkoutStats(0, 0, 0L, 0L)
        }
    }
    
    /**
     * Generate search tokens for better search performance
     */
    private fun generateSearchTokens(
        userAccount: com.example.liftrix.data.local.entity.UserAccountEntity,
        userProfile: com.example.liftrix.data.local.entity.UserProfileEntity?
    ): List<String> {
        val tokens = mutableSetOf<String>()
        
        // Add username tokens
        userAccount.username?.let { username ->
            val cleanUsername = username.lowercase().trim()
            tokens.add(cleanUsername)
            if (cleanUsername.length >= 3) tokens.add(cleanUsername.take(3))
            if (cleanUsername.length >= 4) tokens.add(cleanUsername.take(4))
            
            // Add parts of username split by underscores or dots
            cleanUsername.split("[_.]".toRegex()).forEach { part ->
                if (part.length >= 2) tokens.add(part)
            }
        }
        
        // Add display name tokens
        val displayName = userAccount.displayName ?: userProfile?.displayName
        displayName?.let { name ->
            val cleanName = name.lowercase().trim()
            tokens.add(cleanName)
            cleanName.split(" ").forEach { word ->
                if (word.length >= 2) tokens.add(word)
            }
        }
        
        // Add fitness level token
        userProfile?.fitnessLevel?.let { level ->
            tokens.add(level.lowercase())
        }
        
        return tokens.toList()
    }
    
    /**
     * Generate search keywords list
     */
    private fun generateSearchKeywords(
        userAccount: com.example.liftrix.data.local.entity.UserAccountEntity,
        userProfile: com.example.liftrix.data.local.entity.UserProfileEntity?
    ): List<String> {
        val keywords = mutableListOf<String>()
        
        userAccount.username?.let { keywords.add(it) }
        userAccount.displayName?.let { keywords.add(it) }
        userProfile?.displayName?.let { keywords.add(it) }
        userProfile?.fitnessLevel?.let { keywords.add(it) }
        
        return keywords.distinct()
    }
    
    /**
     * Parse JSON array string safely
     */
    private fun parseJsonArray(jsonString: String?): List<String> {
        if (jsonString.isNullOrBlank()) return emptyList()
        
        return try {
            gson.fromJson(jsonString, Array<String>::class.java).toList()
        } catch (e: Exception) {
            Timber.w(e, "Failed to parse JSON array: $jsonString")
            emptyList()
        }
    }
    
    /**
     * Data class for workout statistics
     */
    private data class WorkoutStats(
        val currentStreak: Int,
        val longestStreak: Int,
        val totalWorkoutTime: Long,
        val averageWorkoutTime: Long
    )
}