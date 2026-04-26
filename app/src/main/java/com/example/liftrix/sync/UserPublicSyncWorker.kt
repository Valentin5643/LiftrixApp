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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.FieldValue
import com.example.liftrix.config.OfflineArchitectureFlags
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
    private val auth: FirebaseAuth,
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
            val useDirtyFlagGating = OfflineArchitectureFlags.ROOM_FIRST_ENABLED &&
                OfflineArchitectureFlags.USE_DIRTY_FLAG_GATING

            // AUTHENTICATION FIX: Verify auth state and user context
            Timber.d("[USER-PUBLIC-SYNC] 🔐 Verifying authentication for user: $userId")
            val currentUser = auth.currentUser
            if (currentUser == null) {
                Timber.e("[USER-PUBLIC-SYNC] ❌ No authenticated user found")
                Timber.e("[USER-PUBLIC-SYNC]   - User will not be discoverable until authenticated")
                return@withContext Result.failure(
                    Data.Builder()
                        .putString(KEY_ERROR_MESSAGE, "User not authenticated")
                        .build()
                )
            }
            
            if (currentUser.uid != userId) {
                Timber.e("[USER-PUBLIC-SYNC] ❌ Authentication UID mismatch")
                Timber.e("[USER-PUBLIC-SYNC]   - Auth UID: ${currentUser.uid}")
                Timber.e("[USER-PUBLIC-SYNC]   - Requested: $userId")
                Timber.e("[USER-PUBLIC-SYNC]   - User will not be discoverable until resolved")
                return@withContext Result.failure(
                    Data.Builder()
                        .putString(KEY_ERROR_MESSAGE, "User ID mismatch with authentication")
                        .build()
                )
            }

            Timber.i("[USER-PUBLIC-SYNC] 🔄 Starting user public sync for: $userId")
            Timber.d("[USER-PUBLIC-SYNC]   - Force sync: $forceSync")
            Timber.d("[USER-PUBLIC-SYNC]   - Target collections: $USERS_PUBLIC_COLLECTION, $USER_SEARCH_CACHE_COLLECTION")
            Timber.d("[USER-PUBLIC-SYNC]   - This sync is critical for user discoverability")
            
            // Get user account data (username, email, display name)
            Timber.d("[USER-PUBLIC-SYNC] 📋 Fetching user account data for: $userId")
            val userAccount = userAccountDao.getAccountForUserSuspend(userId)
            if (userAccount == null) {
                Timber.w("[USER-PUBLIC-SYNC] ⚠️ No user account found for: $userId")
                Timber.w("[USER-PUBLIC-SYNC]   - This may be expected during initial user creation")
                Timber.w("[USER-PUBLIC-SYNC]   - Creating minimal profile to ensure basic discoverability")
                
                // Create a minimal public profile for new users without a full account yet
                // This prevents sync failures during the sign-up process
                val minimalPublicData = mapOf(
                    "userId" to userId,
                    "username" to null,
                    "displayName" to "New User",
                    "bio" to null,
                    "isPublic" to true,
                    "isPrivate" to false,
                    "isSearchable" to true, // 🔥 CRITICAL FIX: Make minimal profiles searchable
                    "totalWorkouts" to 0,
                    "currentStreak" to 0,
                    "longestStreak" to 0,
                    "followersCount" to 0,
                    "followingCount" to 0,
                    "profileImageUrl" to null,
                    "memberSince" to LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                    "lastActiveAt" to LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                    "profileViews" to 0,
                    "profileCompletionPercentage" to 0,
                    "fitnessGoals" to emptyList<String>(),
                    "availableEquipment" to emptyList<String>(),
                    "publicAchievements" to emptyList<Map<String, Any>>(),
                    "searchTokens" to emptyList<String>(),
                    "searchKeywords" to emptyList<String>(),
                    "syncVersion" to 1L,
                    "lastModified" to System.currentTimeMillis(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                
                // Write minimal public data
                val publicDocRef = firestore
                    .collection(USERS_PUBLIC_COLLECTION)
                    .document(userId)
                
                publicDocRef.set(minimalPublicData, SetOptions.merge()).await()
                
                Timber.i("[USER-PUBLIC-SYNC] ✅ Created minimal public profile for new user: $userId")
                Timber.i("[USER-PUBLIC-SYNC]   - User is now discoverable with basic information")
                Timber.i("[USER-PUBLIC-SYNC]   - Profile will be enhanced when full account data is available")
                
                return@withContext Result.success(
                    Data.Builder()
                        .putInt(KEY_SYNC_COUNT, 1)
                        .putString("status", "minimal_profile_created")
                        .build()
                )
            }
            
            // Get user profile data (bio, fitness level, etc.)
            Timber.d("[USER-PUBLIC-SYNC] 📋 Fetching user profile data for: $userId")
            val userProfile = userProfileDao.getProfileForUserSuspend(userId)
            Timber.d("[USER-PUBLIC-SYNC]   - Account username: ${userAccount.username}")
            Timber.d("[USER-PUBLIC-SYNC]   - Profile found: ${userProfile != null}")
            if (userAccount.username == null) {
                Timber.w("[USER-PUBLIC-SYNC] ⚠️ UserAccount has null username for user: $userId")
                Timber.w("[USER-PUBLIC-SYNC]   - User may not be fully discoverable without username")
            }

            val dirtyAccounts = if (useDirtyFlagGating) {
                userAccountDao.getDirtyUserAccounts(userId)
            } else {
                emptyList()
            }
            val dirtyProfiles = if (useDirtyFlagGating) {
                userProfileDao.getDirtyUserProfiles(userId)
            } else {
                emptyList()
            }
            if (useDirtyFlagGating && dirtyAccounts.isEmpty() && dirtyProfiles.isEmpty() && !forceSync) {
                Timber.d("[USER-PUBLIC-SYNC] ✅ No dirty account/profile data for user $userId, skipping")
                return@withContext Result.success(
                    Data.Builder()
                        .putInt(KEY_SYNC_COUNT, 0)
                        .build()
                )
            }
            
            // Calculate workout statistics
            Timber.d("[USER-PUBLIC-SYNC] 📊 Calculating workout statistics for: $userId")
            val totalWorkouts = workoutDao.getWorkoutCountForUser(userId)
            val workoutStats = calculateWorkoutStats(userId)
            Timber.d("[USER-PUBLIC-SYNC]   - Total workouts: $totalWorkouts")
            Timber.d("[USER-PUBLIC-SYNC]   - Current streak: ${workoutStats.currentStreak}")
            Timber.d("[USER-PUBLIC-SYNC]   - Longest streak: ${workoutStats.longestStreak}")
            
            // Generate search tokens for better searchability
            Timber.d("[USER-PUBLIC-SYNC] 🔍 Generating search tokens and keywords for: $userId")
            val searchTokens = generateSearchTokens(userAccount, userProfile)
            val searchKeywords = generateSearchKeywords(userAccount, userProfile)
            Timber.d("[USER-PUBLIC-SYNC]   - Search tokens: $searchTokens")
            Timber.d("[USER-PUBLIC-SYNC]   - Search keywords: $searchKeywords")
            
            // Prepare public user data
            val isPublicProfile = userProfile?.isPublic ?: true
            Timber.d("[USER-PUBLIC-SYNC] 📝 Preparing public user data")
            Timber.d("[USER-PUBLIC-SYNC]   - Is public profile: $isPublicProfile")
            Timber.d("[USER-PUBLIC-SYNC]   - Display name: ${userAccount.displayName ?: userProfile?.displayName}")
            Timber.d("[USER-PUBLIC-SYNC]   - Username: ${userAccount.username}")
            
            val localLastModified = maxOf(
                userAccount.lastModified,
                userProfile?.lastModified ?: 0L
            )

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
                "syncVersion" to 1L, // Use simple integer version for Firestore rules compatibility
                "lastModified" to localLastModified,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            
            // Sync to users_public collection
            val publicDocRef = firestore
                .collection(USERS_PUBLIC_COLLECTION)
                .document(userId)

            val remotePublicDoc = publicDocRef.get().await()
            if (remotePublicDoc.exists()) {
                val remoteLastModified = when (val remoteValue = remotePublicDoc.get("lastModified")) {
                    is com.google.firebase.Timestamp -> remoteValue.toDate().time
                    is Number -> remoteValue.toLong()
                    else -> 0L
                }
                if (remoteLastModified > localLastModified) {
                    Timber.d("[USER-PUBLIC-SYNC] ✅ Remote public profile newer; upload skipped")
                    return@withContext Result.success(
                        Data.Builder()
                            .putInt(KEY_SYNC_COUNT, 0)
                            .build()
                    )
                }
            }
            
            Timber.d("[USER-PUBLIC-SYNC] 📦 Uploading to $USERS_PUBLIC_COLLECTION/$userId")
            publicDocRef.set(publicUserData, SetOptions.merge()).await()
            Timber.i("[USER-PUBLIC-SYNC] ✅ Successfully uploaded to users_public collection")
            
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
                "isSearchable" to true, // 🔥 CRITICAL FIX: Missing field that search queries require
                "searchTokens" to searchTokens,
                "keywords" to searchKeywords,
                "lastModified" to localLastModified,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            
            val searchCacheDocRef = firestore
                .collection(USER_SEARCH_CACHE_COLLECTION)
                .document(userId)
            
            Timber.d("[USER-PUBLIC-SYNC] 📦 Uploading to $USER_SEARCH_CACHE_COLLECTION/$userId")
            searchCacheDocRef.set(searchCacheData, SetOptions.merge()).await()
            Timber.i("[USER-PUBLIC-SYNC] ✅ Successfully uploaded to user_search_cache collection")
            
            Timber.i("[USER-PUBLIC-SYNC] ✅ User public data sync completed successfully for user: $userId")
            Timber.i("[USER-PUBLIC-SYNC]   - User is now discoverable in search with ${searchTokens.size} tokens")
            Timber.i("[USER-PUBLIC-SYNC]   - Profile available in both collections for optimal search performance")
            
            return@withContext Result.success(
                Data.Builder()
                    .putInt(KEY_SYNC_COUNT, 1)
                    .build()
            )
            
        } catch (e: Exception) {
            val userId = inputData.getString("userId")
            val currentUser = auth.currentUser
            
            val errorMessage = when {
                e.message?.contains("PERMISSION_DENIED") == true -> {
                    val authInfo = if (currentUser != null) {
                        "User authenticated as ${currentUser.uid}, writing for $userId"
                    } else {
                        "No authenticated user found"
                    }
                    "Permission denied writing user public data. Auth context: $authInfo. Check Firestore security rules."
                }
                e.message?.contains("INVALID_ARGUMENT") == true -> {
                    "Invalid data format for user public sync. Check field types and validation rules."
                }
                else -> {
                    e.message ?: "Unknown error during user public sync"
                }
            }
            
            Timber.e(e, "[USER-PUBLIC-SYNC] ❌ UserPublicSyncWorker failed for user $userId")
            Timber.e("[USER-PUBLIC-SYNC]   - Error: $errorMessage")
            Timber.e("[USER-PUBLIC-SYNC]   - User will remain undiscoverable until sync succeeds")
            Timber.e("[USER-PUBLIC-SYNC]   - Retry attempt: ${runAttemptCount + 1}/$MAX_RETRY_COUNT")
            
            if (runAttemptCount < MAX_RETRY_COUNT) {
                Result.retry()
            } else {
                Result.failure(
                    Data.Builder()
                        .putString(KEY_ERROR_MESSAGE, errorMessage)
                        .putInt("attempt_count", runAttemptCount)
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
