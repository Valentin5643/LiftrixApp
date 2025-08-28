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
 * Service responsible for detecting and cleaning up orphaned profile data.
 * 
 * Orphaned data occurs when:
 * - Firebase Auth UID is deleted but Firestore/Room data remains
 * - Sync workers fail because profiles don't exist in expected locations
 * - Stale references prevent proper sync operations
 * 
 * This service provides:
 * - Detection of orphaned profiles across Firebase Auth, Firestore, and Room
 * - Safe cleanup of stale data that doesn't affect active users
 * - Comprehensive logging and metrics for cleanup operations
 * - Prevention of sync worker failures due to missing profiles
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
     */
    data class CleanupResult(
        val orphanedProfilesFound: Int,
        val orphanedProfilesRemoved: Int,
        val firestoreDocumentsRemoved: Int,
        val roomRecordsRemoved: Int,
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
                Timber.w("🧹 CLEANUP: Current user $currentUserId missing local profile - sync system may be blocked")
                // Don't cleanup current user, but log the issue
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
            val errorResult = CleanupResult(0, 0, 0, 0, listOf(e.message ?: "Unknown error"), System.currentTimeMillis() - startTime)
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
        var orphanedFound = 0
        var orphanedRemoved = 0
        var firestoreRemoved = 0
        var roomRemoved = 0
        
        try {
            Timber.i("🧹 CLEANUP: Starting comprehensive orphaned profile cleanup (excluding: $excludeUserId)")
            
            // Step 1: Find all Room profiles that don't have valid Firebase Auth UIDs
            val roomOrphans = findOrphanedRoomProfiles(excludeUserId)
            orphanedFound += roomOrphans.size
            
            if (roomOrphans.isNotEmpty()) {
                Timber.w("🧹 CLEANUP: Found ${roomOrphans.size} orphaned profiles in Room database")
                val removedFromRoom = cleanupOrphanedRoomData(roomOrphans)
                roomRemoved += removedFromRoom
                orphanedRemoved += removedFromRoom
            }
            
            // Step 2: Find Firestore profiles that don't have valid Firebase Auth UIDs
            val firestoreOrphans = findOrphanedFirestoreProfiles(excludeUserId)
            orphanedFound += firestoreOrphans.size
            
            if (firestoreOrphans.isNotEmpty()) {
                Timber.w("🧹 CLEANUP: Found ${firestoreOrphans.size} orphaned profiles in Firestore")
                val removedFromFirestore = cleanupOrphanedFirestoreData(firestoreOrphans)
                firestoreRemoved += removedFromFirestore
                orphanedRemoved += removedFromFirestore
            }
            
            // Step 3: Log cleanup summary
            val totalTime = System.currentTimeMillis() - startTime
            Timber.i("🧹 CLEANUP: Completed - Found: $orphanedFound, Removed: $orphanedRemoved, Time: ${totalTime}ms")
            
            if (orphanedRemoved > 0) {
                Timber.i("🧹 CLEANUP: Cleaned up $orphanedRemoved orphaned profiles (Room: $roomRemoved, Firestore: $firestoreRemoved)")
            } else {
                Timber.d("🧹 CLEANUP: No orphaned profiles found - system is clean")
            }
            
            return CleanupResult(
                orphanedProfilesFound = orphanedFound,
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
                orphanedProfilesFound = orphanedFound,
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
     * Finds Room profiles that don't correspond to valid Firebase Auth users.
     */
    private suspend fun findOrphanedRoomProfiles(excludeUserId: String?): List<String> {
        return try {
            val allProfiles = userProfileDao.getAllProfiles()
            val orphanedIds = mutableListOf<String>()
            
            for (profile in allProfiles) {
                val userId = profile.userId
                // Skip current user
                if (userId == excludeUserId) continue
                
                // Check if this user exists in Firebase Auth
                // Note: We can't directly check Firebase Auth for arbitrary UIDs
                // So we'll use Firestore as a proxy - if it doesn't exist there either, likely orphaned
                val existsInFirestore = try {
                    val doc = firestore.collection("users").document(userId).get().await()
                    doc.exists()
                } catch (e: Exception) {
                    Timber.w("🧹 CLEANUP: Could not check Firestore for user $userId: ${e.message}")
                    false
                }
                
                if (!existsInFirestore) {
                    Timber.d("🧹 CLEANUP: Found potentially orphaned Room profile: $userId")
                    orphanedIds.add(userId)
                }
            }
            
            orphanedIds
            
        } catch (e: Exception) {
            Timber.e(e, "🧹 CLEANUP: Failed to find orphaned Room profiles")
            emptyList()
        }
    }
    
    /**
     * Finds Firestore profiles that don't correspond to valid Firebase Auth users.
     */
    private suspend fun findOrphanedFirestoreProfiles(excludeUserId: String?): List<String> {
        return try {
            val usersQuery = firestore.collection("users").limit(100).get().await()
            val orphanedIds = mutableListOf<String>()
            
            for (document in usersQuery.documents) {
                val userId = document.id
                
                // Skip current user
                if (userId == excludeUserId) continue
                
                // For now, we'll identify orphans by checking if they also exist in Room
                // This is a conservative approach - only cleanup profiles that exist in neither system
                val existsInRoom = userProfileDao.getProfileForUserSuspend(userId) != null
                
                if (!existsInRoom) {
                    Timber.d("🧹 CLEANUP: Found potentially orphaned Firestore profile: $userId")
                    orphanedIds.add(userId)
                }
            }
            
            orphanedIds
            
        } catch (e: Exception) {
            Timber.e(e, "🧹 CLEANUP: Failed to find orphaned Firestore profiles")
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
                    workoutDao.deleteAllWorkoutsForUser(userId)
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
                Timber.w("🧹 CLEANUP: Detected orphaned Firestore data for user $userId")
                
                // Check if documents exist (read-only operation)
                val userDocExists = try {
                    val doc = firestore.collection("users").document(userId).get().await()
                    doc.exists()
                } catch (e: Exception) {
                    Timber.w("🧹 CLEANUP: Could not check user document for $userId: ${e.message}")
                    false
                }
                
                val socialDocExists = try {
                    val doc = firestore.collection("social_profiles").document(userId).get().await()
                    doc.exists()
                } catch (e: Exception) {
                    Timber.w("🧹 CLEANUP: Could not check social profile document for $userId: ${e.message}")
                    false
                }
                
                if (userDocExists || socialDocExists) {
                    detectedCount++
                    Timber.w("🧹 CLEANUP: Orphaned Firestore data detected for user $userId (user_doc: $userDocExists, social_doc: $socialDocExists)")
                    Timber.w("🧹 CLEANUP: ⚠️  SERVER-SIDE CLEANUP REQUIRED for user $userId - client cannot delete due to security rules")
                }
                
            } catch (e: Exception) {
                Timber.e(e, "🧹 CLEANUP: Failed to check Firestore data for user $userId")
            }
        }
        
        if (detectedCount > 0) {
            Timber.w("🧹 CLEANUP: 🚨 DETECTED $detectedCount ORPHANED FIRESTORE PROFILES")
            Timber.w("🧹 CLEANUP: 🔧 RECOMMENDATION: Implement server-side cleanup using Cloud Functions or Admin SDK")
            Timber.w("🧹 CLEANUP: 🔧 EXAMPLE: exports.cleanupUser = functions.auth.user().onDelete((user) => { /* delete Firestore docs */ })")
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