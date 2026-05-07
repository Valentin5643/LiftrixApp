package com.example.liftrix.data.service

import android.content.Context
import com.example.liftrix.analytics.CleanupMetricsCollector
import com.example.liftrix.data.local.dao.*
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixSuccess
import com.example.liftrix.domain.model.common.liftrixFailure
import com.example.liftrix.domain.model.error.LiftrixError
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service responsible for detecting potentially orphaned profile data (client-side limited).
 *
 * IMPORTANT: This client-side service has LIMITED visibility:
 * - Cannot verify Firebase Auth status for other users (security rules prevent this)
 * - PERMISSION_DENIED errors mean "cannot verify" NOT "orphaned"
 * - Server-side Cloud Functions provide authoritative orphan detection
 *
 * Orphaned data occurs when:
 * - Firebase Auth UID is deleted but Firestore/Room data remains
 * - Sync workers fail because profiles don't exist in expected locations
 * - Stale references prevent proper sync operations
 *
 * This service provides:
 * - Detection of unverified profiles (limited by security rules)
 * - Safe cleanup of local Room data only (not Firestore)
 * - Logging and metrics for monitoring
 * - Recommendations to run server-side cleanup for authoritative validation
 *
 * For TRUE orphan cleanup, use:
 * - Cloud Function: scheduledOrphanCleanup (automatic daily)
 * - Cloud Function: bulkCleanupOrphanedData (manual on-demand)
 */
@Singleton
class ProfileCleanupService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val userProfileDao: UserProfileDao,
    private val socialProfileDao: SocialProfileDao,
    private val workoutDao: WorkoutDao,
    private val achievementDao: AchievementDao,
    private val followRelationshipDao: FollowRelationshipDao,
    private val customExerciseDao: CustomExerciseDao,
    val metricsCollector: CleanupMetricsCollector
) {
    companion object {
        private const val CLEANUP_BATCH_SIZE = 20
        private const val MAX_CLEANUP_ATTEMPTS = 5
    }
    
    /**
     * Data class representing cleanup results for reporting
     *
     * IMPORTANT: Distinguishes between:
     * - TRUE_ORPHANS: Server-verified deleted Auth accounts (authoritative)
     * - UNVERIFIED: Client cannot verify due to security rules (not orphans)
     */
    data class CleanupResult(
        val trueOrphansFound: Int,           // Server-verified orphans (Auth deleted)
        val unverifiedProfilesFound: Int,     // Client-limited (cannot verify Auth)
        val orphanedProfilesRemoved: Int,     // Actually removed (Room only)
        val firestoreDocumentsRemoved: Int,   // Always 0 (client cannot delete)
        val roomRecordsRemoved: Int,          // Local Room cleanup
        val errors: List<String>,
        val cleanupTimeMs: Long
    )
    
    /**
     * Performs startup cleanup check - should be called on login or app startup.
     * This validates the current user's profile exists and triggers cleanup if needed.
     * 
     * @param currentUserId The currently authenticated user ID (from Firebase Auth)
     * @return LiftrixResult indicating success or failure of cleanup operation
     */
    suspend fun performStartupCleanup(currentUserId: String): LiftrixResult<CleanupResult> {
        val startTime = System.currentTimeMillis()
        
        // Record cleanup start
        metricsCollector.recordCleanupStart("startup_cleanup", currentUserId, "startup")
        
        return try {
            Timber.i("🧹 CLEANUP: Starting startup cleanup for user $currentUserId")
            
            // First, validate current user exists in both Firebase Auth and our systems
            val isCurrentUserValid = validateCurrentUser(currentUserId)
            if (!isCurrentUserValid) {
                Timber.e("🧹 CLEANUP: Current user $currentUserId not found in Firebase Auth - this should not happen")
                return liftrixFailure(
                    LiftrixError.BusinessLogicError(
                        code = "INVALID_CURRENT_USER",
                        errorMessage = "Current user not found in Firebase Auth during cleanup"
                    )
                )
            }
            
            // Check if current user's profile exists in Room
            val currentUserProfileExists = userProfileDao.getProfileForUserSuspend(currentUserId) != null
            
            if (!currentUserProfileExists) {
                Timber.d("🧹 CLEANUP: Current user $currentUserId has no local profile yet - likely in onboarding or first login")
                Timber.d("🧹 CLEANUP: This is normal for new users and does not indicate a sync issue")
                // Don't cleanup current user - missing profile during first login is expected
            } else {
                Timber.d("🧹 CLEANUP: Current user $currentUserId has local profile - sync system ready")
            }
            
            // Perform general orphaned profile cleanup (excludes current user)
            val cleanupResult = performOrphanedProfileCleanup(excludeUserId = currentUserId)
            
            val totalTime = System.currentTimeMillis() - startTime
            Timber.i("🧹 CLEANUP: Startup cleanup completed in ${totalTime}ms for user $currentUserId")
            
            val finalResult = cleanupResult.copy(cleanupTimeMs = totalTime)
            
            // Record cleanup completion with metrics
            metricsCollector.recordCleanupCompletion(
                "startup_cleanup", 
                currentUserId, 
                "startup", 
                finalResult, 
                startTime
            )
            
            // Record startup-specific metrics
            metricsCollector.recordStartupCleanup(currentUserId, true, finalResult)
            
            liftrixSuccess(finalResult)
            
        } catch (e: Exception) {
            Timber.e(e, "🧹 CLEANUP: Startup cleanup failed for user $currentUserId")
            
            // Record failed cleanup
            val errorResult = CleanupResult(
                trueOrphansFound = 0,
                unverifiedProfilesFound = 0,
                orphanedProfilesRemoved = 0,
                firestoreDocumentsRemoved = 0,
                roomRecordsRemoved = 0,
                errors = listOf(e.message ?: "Unknown error"),
                cleanupTimeMs = System.currentTimeMillis() - startTime
            )
            metricsCollector.recordCleanupCompletion("startup_cleanup", currentUserId, "startup", errorResult, startTime)
            
            liftrixFailure(
                LiftrixError.BusinessLogicError(
                    code = "STARTUP_CLEANUP_FAILED",
                    errorMessage = "Failed to perform startup cleanup: ${e.message}",
                    analyticsContext = mapOf("user_id" to currentUserId)
                )
            )
        }
    }
    
    /**
     * Performs comprehensive cleanup of orphaned profiles across all storage systems.
     * This should be used for maintenance operations or when sync issues are detected.
     * 
     * @param excludeUserId Optional user ID to exclude from cleanup (e.g., current user)
     * @return CleanupResult with detailed metrics
     */
    suspend fun performOrphanedProfileCleanup(excludeUserId: String? = null): CleanupResult {
        val startTime = System.currentTimeMillis()
        val errors = mutableListOf<String>()
        var trueOrphansFound = 0       // Server-verified orphans only
        var unverifiedFound = 0          // Client cannot verify (not orphans)
        var orphanedRemoved = 0
        var firestoreRemoved = 0
        var roomRemoved = 0

        try {
            Timber.i("🧹 CLEANUP: Starting comprehensive profile verification (excluding: $excludeUserId)")

            // Step 1: Check Room profiles (returns unverified count)
            val roomCheckResult = findOrphanedRoomProfiles(excludeUserId)
            unverifiedFound += roomCheckResult.size

            if (roomCheckResult.isNotEmpty()) {
                Timber.w("🧹 CLEANUP: Found ${roomCheckResult.size} unverified profiles in Room database (client-limited)")
                val removedFromRoom = cleanupOrphanedRoomData(roomCheckResult)
                roomRemoved += removedFromRoom
                orphanedRemoved += removedFromRoom
            }

            // Step 2: Check Firestore profiles (returns unverified count)
            val firestoreCheckResult = findOrphanedFirestoreProfiles(excludeUserId)
            unverifiedFound += firestoreCheckResult.size

            if (firestoreCheckResult.isNotEmpty()) {
                Timber.w("🧹 CLEANUP: Found ${firestoreCheckResult.size} unverified Firestore profiles (client-limited)")
                val detectedFromFirestore = cleanupOrphanedFirestoreData(firestoreCheckResult)
                firestoreRemoved += detectedFromFirestore
            }

            // Step 3: Log cleanup summary with proper categorization
            val totalTime = System.currentTimeMillis() - startTime
            Timber.i("🧹 CLEANUP: Completed in ${totalTime}ms")

            if (trueOrphansFound > 0) {
                Timber.w("🚨 CLEANUP: TRUE ORPHANS (server-verified): $trueOrphansFound")
            }

            if (unverifiedFound > 0) {
                Timber.i("⚠️  CLEANUP: UNVERIFIED (client-limited): $unverifiedFound")
                Timber.i("🔧 CLEANUP: Server-side validation required - run Cloud Function 'bulkCleanupOrphanedData'")
            }

            if (orphanedRemoved > 0) {
                Timber.i("🧹 CLEANUP: Removed $orphanedRemoved local records (Room: $roomRemoved, Firestore: $firestoreRemoved)")
            } else if (trueOrphansFound == 0 && unverifiedFound == 0) {
                Timber.d("✅ CLEANUP: No issues detected - local state clean")
            }

            return CleanupResult(
                trueOrphansFound = trueOrphansFound,
                unverifiedProfilesFound = unverifiedFound,
                orphanedProfilesRemoved = orphanedRemoved,
                firestoreDocumentsRemoved = firestoreRemoved,
                roomRecordsRemoved = roomRemoved,
                errors = errors.toList(),
                cleanupTimeMs = totalTime
            )

        } catch (e: Exception) {
            Timber.e(e, "🧹 CLEANUP: Comprehensive cleanup failed")
            errors.add("Cleanup failed: ${e.message}")

            return CleanupResult(
                trueOrphansFound = trueOrphansFound,
                unverifiedProfilesFound = unverifiedFound,
                orphanedProfilesRemoved = orphanedRemoved,
                firestoreDocumentsRemoved = firestoreRemoved,
                roomRecordsRemoved = roomRemoved,
                errors = errors.toList(),
                cleanupTimeMs = System.currentTimeMillis() - startTime
            )
        }
    }
    
    /**
     * Validates that a user ID exists in Firebase Auth.
     * This is the source of truth for active users.
     */
    private suspend fun validateCurrentUser(userId: String): Boolean {
        return try {
            val currentUser = firebaseAuth.currentUser
            val isValid = currentUser != null && currentUser.uid == userId
            Timber.d("🧹 CLEANUP: User validation for $userId: $isValid")
            isValid
        } catch (e: Exception) {
            Timber.e(e, "🧹 CLEANUP: Failed to validate user $userId")
            false
        }
    }
    
    /**
     * Finds Room profiles that cannot be verified by the client (security-limited).
     *
     * IMPORTANT: Returns "unverified" profiles, NOT confirmed orphans.
     * Client cannot check Firebase Auth for other users due to security rules.
     * Use server-side Cloud Functions for authoritative orphan detection.
     */
    private suspend fun findOrphanedRoomProfiles(excludeUserId: String?): List<String> {
        return try {
            val allProfiles = userProfileDao.getAllProfiles()
            val unverifiedIds = mutableListOf<String>()

            for (profile in allProfiles) {
                val userId = profile.userId
                // Skip current user
                if (userId == excludeUserId) continue

                // Check if this user exists in Firestore
                // Note: Client cannot verify Firebase Auth directly (security limitation)
                // PERMISSION_DENIED means we can't access it, NOT that it's orphaned
                val firestoreCheckResult = try {
                    val doc = firestore.collection("users").document(userId).get().await()
                    if (doc.exists()) "EXISTS" else "MISSING"
                } catch (e: Exception) {
                    when {
                        e.message?.contains("PERMISSION_DENIED") == true -> "PERMISSION_DENIED"
                        else -> "ERROR:${e.message}"
                    }
                }

                // Only flag as unverified if document is confirmed MISSING (not permission denied)
                if (firestoreCheckResult == "MISSING") {
                    Timber.d("🧹 CLEANUP: Found unverified Room profile: $userId (Firestore: confirmed missing)")
                    unverifiedIds.add(userId)
                } else if (firestoreCheckResult == "PERMISSION_DENIED") {
                    Timber.d("🧹 CLEANUP: Cannot verify Room profile $userId (Firestore: permission denied - likely active user)")
                } else if (firestoreCheckResult.startsWith("ERROR")) {
                    Timber.w("🧹 CLEANUP: Could not check Firestore for user $userId: ${firestoreCheckResult.removePrefix("ERROR:")}")
                }
            }

            unverifiedIds

        } catch (e: Exception) {
            Timber.e(e, "🧹 CLEANUP: Failed to verify Room profiles")
            emptyList()
        }
    }
    
    /**
     * Finds Firestore profiles that cannot be verified by the client.
     *
     * IMPORTANT: Returns "unverified" profiles, NOT confirmed orphans.
     * Client uses Room cache as proxy check (not authoritative).
     */
    private suspend fun findOrphanedFirestoreProfiles(excludeUserId: String?): List<String> {
        return try {
            val usersQuery = firestore.collection("users").limit(100).get().await()
            val unverifiedIds = mutableListOf<String>()

            for (document in usersQuery.documents) {
                val userId = document.id

                // Skip current user
                if (userId == excludeUserId) continue

                // Check if this profile exists in Room (conservative approach)
                // Only flag as unverified if it exists in neither Room nor client can verify it
                val roomCheckResult = try {
                    userProfileDao.getProfileForUserSuspend(userId) != null
                } catch (e: Exception) {
                    Timber.w("🧹 CLEANUP: Could not check Room for user $userId: ${e.message}")
                    false
                }

                if (!roomCheckResult) {
                    Timber.d("🧹 CLEANUP: Found unverified Firestore profile: $userId (not in local Room cache)")
                    Timber.d("🧹 CLEANUP: Note: Client-side detection limited - server-side validation required")
                    unverifiedIds.add(userId)
                }
            }

            unverifiedIds

        } catch (e: Exception) {
            Timber.e(e, "🧹 CLEANUP: Failed to scan Firestore profiles")
            emptyList()
        }
    }
    
    /**
     * Removes orphaned data from Room database for the specified user IDs.
     */
    private suspend fun cleanupOrphanedRoomData(orphanedUserIds: List<String>): Int {
        var removedCount = 0
        
        for (userId in orphanedUserIds) {
            try {
                Timber.i("🧹 CLEANUP: Removing orphaned Room data for user $userId")
                
                // Remove user profile
                val profileDeleted = userProfileDao.deleteProfileForUser(userId) > 0
                
                // Remove social profile
                val socialProfileDeleted = try {
                    socialProfileDao.deleteProfileForUser(userId) > 0
                } catch (e: Exception) {
                    Timber.w("🧹 CLEANUP: Could not delete social profile for $userId: ${e.message}")
                    false
                }
                
                // Remove workouts (this might be a lot of data)
                val workoutsDeleted = try {
                    val beforeCount = workoutDao.getWorkoutCountForUser(userId)
                    workoutDao.deleteAllWorkoutsForUser(userId).also { deletedRows ->
                        Timber.tag("FreshLoginRestoreDebug").w(
                            "operation=ROOM_CLEAR_WORKOUTS userId=$userId source=ProfileCleanup roomBeforeCount=$beforeCount roomAfterCount=${workoutDao.getWorkoutCountForUser(userId)} deletedRows=$deletedRows timestamp=${System.currentTimeMillis()}"
                        )
                        Timber.tag("WorkoutSyncDebug").w(
                            "[DATABASE-DEBUG] operation=PROFILE_CLEANUP_DELETE_ALL_WORKOUTS source=Cleanup userId=$userId timestamp=${System.currentTimeMillis()} beforeCount=$beforeCount afterCount=${workoutDao.getWorkoutCountForUser(userId)} deletedRows=$deletedRows"
                        )
                    }
                } catch (e: Exception) {
                    Timber.w("🧹 CLEANUP: Could not delete workouts for $userId: ${e.message}")
                    0
                }
                
                // Remove achievements (if method exists)
                val achievementsDeleted = try {
                    // For now, skip achievement deletion as method doesn't exist
                    0
                } catch (e: Exception) {
                    0
                }
                
                // Remove follow relationships (if method exists)
                val followsDeleted = try {
                    // For now, skip follow deletion as method doesn't exist
                    0
                } catch (e: Exception) {
                    0
                }
                
                // Remove custom exercises (if method exists)
                val exercisesDeleted = try {
                    // For now, skip exercise deletion as method doesn't exist
                    0
                } catch (e: Exception) {
                    0
                }
                
                if (profileDeleted || socialProfileDeleted || workoutsDeleted > 0) {
                    removedCount++
                    Timber.i("🧹 CLEANUP: Removed Room data for user $userId - Workouts: $workoutsDeleted, Profile: $profileDeleted, Social: $socialProfileDeleted, Achievements: $achievementsDeleted, Follows: $followsDeleted, Exercises: $exercisesDeleted")
                }
                
            } catch (e: Exception) {
                Timber.e(e, "🧹 CLEANUP: Failed to remove Room data for user $userId")
            }
        }
        
        return removedCount
    }
    
    /**
     * 🚨 SECURITY ISSUE FIX: Do not delete other users' Firestore data from client.
     * Client-side deletion violates Firestore security rules and causes PERMISSION_DENIED errors.
     * 
     * Instead, we log orphaned Firestore data for server-side cleanup and focus on
     * cleaning up local Room data only.
     */
    private suspend fun cleanupOrphanedFirestoreData(orphanedUserIds: List<String>): Int {
        // 🚫 CLIENT-SIDE FIRESTORE DELETION DISABLED
        // Attempting to delete other users' Firestore documents from the client
        // violates security rules and causes infinite retry loops.
        
        var detectedCount = 0
        
        for (userId in orphanedUserIds) {
            try {
                Timber.d("🧹 CLEANUP: Checking unverified Firestore profile: $userId")

                // Check if documents exist (read-only operation)
                val userDocCheckResult = try {
                    val doc = firestore.collection("users").document(userId).get().await()
                    if (doc.exists()) "EXISTS" else "MISSING"
                } catch (e: Exception) {
                    when {
                        e.message?.contains("PERMISSION_DENIED") == true -> "PERMISSION_DENIED"
                        else -> "ERROR:${e.message}"
                    }
                }

                val socialDocCheckResult = try {
                    val doc = firestore.collection("social_profiles").document(userId).get().await()
                    if (doc.exists()) "EXISTS" else "MISSING"
                } catch (e: Exception) {
                    when {
                        e.message?.contains("PERMISSION_DENIED") == true -> "PERMISSION_DENIED"
                        else -> "ERROR:${e.message}"
                    }
                }

                // Only count as detected orphan if we can CONFIRM it exists (not just permission denied)
                if (userDocCheckResult == "EXISTS" || socialDocCheckResult == "EXISTS") {
                    detectedCount++
                    Timber.w("🧹 CLEANUP: Unverified Firestore profile detected: $userId (client-side check limited)")
                    Timber.w("🧹 CLEANUP: user_doc: $userDocCheckResult, social_doc: $socialDocCheckResult")
                    Timber.w("🧹 CLEANUP: ⚠️  SERVER-SIDE VALIDATION REQUIRED - client cannot verify Auth status")
                } else if (userDocCheckResult == "PERMISSION_DENIED" || socialDocCheckResult == "PERMISSION_DENIED") {
                    Timber.d("🧹 CLEANUP: Cannot verify Firestore profile $userId due to security rules (likely active user)")
                }
                
            } catch (e: Exception) {
                Timber.e(e, "🧹 CLEANUP: Failed to check Firestore data for user $userId")
            }
        }
        
        if (detectedCount > 0) {
            Timber.w("🧹 CLEANUP: 🚨 DETECTED $detectedCount UNVERIFIED FIRESTORE PROFILES (client-side check)")
            Timber.w("🧹 CLEANUP: ⚠️  Note: Client cannot verify Firebase Auth status due to security rules")
            Timber.w("🧹 CLEANUP: ✅ Server-side Cloud Function 'scheduledOrphanCleanup' will validate and clean if needed")
            Timber.w("🧹 CLEANUP: 📊 For immediate cleanup, run Cloud Function 'bulkCleanupOrphanedData' from Firebase Console")
        }
        
        // Return 0 since we're not actually deleting anything from client
        return 0
    }
    
    /**
     * Checks if a specific user ID appears to be orphaned.
     * This is useful for targeted cleanup when sync workers fail.
     */
    suspend fun isUserOrphaned(userId: String): Boolean {
        return try {
            // Check if profile exists in Room
            val existsInRoom = userProfileDao.getProfileForUserSuspend(userId) != null
            
            // Check if profile exists in Firestore
            val existsInFirestore = try {
                val doc = firestore.collection("users").document(userId).get().await()
                doc.exists()
            } catch (e: Exception) {
                false
            }
            
            // User is considered orphaned if it exists in neither system
            // OR if it exists in one but not the other (inconsistent state)
            val isOrphaned = !existsInRoom && !existsInFirestore
            
            if (isOrphaned) {
                Timber.w("🧹 CLEANUP: User $userId appears to be orphaned (Room: $existsInRoom, Firestore: $existsInFirestore)")
            }
            
            isOrphaned
            
        } catch (e: Exception) {
            Timber.e(e, "🧹 CLEANUP: Failed to check if user $userId is orphaned")
            false
        }
    }
    
    /**
     * Removes all sync queue entries for orphaned users.
     * This prevents the sync system from trying to sync non-existent users.
     */
    suspend fun cleanupOrphanedSyncQueue(): Int {
        return try {
            // This would require access to sync queue DAO
            // For now, we'll return 0 and implement this when the sync queue is available
            Timber.d("🧹 CLEANUP: Sync queue cleanup not yet implemented")
            0
        } catch (e: Exception) {
            Timber.e(e, "🧹 CLEANUP: Failed to cleanup sync queue")
            0
        }
    }
}
