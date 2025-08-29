package com.example.liftrix.domain.usecase.auth

import android.content.Context
import androidx.work.WorkManager
import com.example.liftrix.core.workmanager.WorkManagerProvider
import com.example.liftrix.domain.model.User
import com.example.liftrix.domain.model.UserAccount
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.repository.UserAccountRepository
import com.example.liftrix.domain.repository.ProfileRepository
import com.example.liftrix.sync.UserPublicSyncWorker
import com.example.liftrix.sync.FollowRelationshipSyncWorker
import com.example.liftrix.domain.model.common.LiftrixResult
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDateTime
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Enhanced Google sign-in use case that ensures user searchability.
 * 
 * This use case:
 * 1. Authenticates with Google
 * 2. Creates/updates UserAccount in local database
 * 3. Triggers sync to make user searchable
 */
class SignInWithGoogleUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val userAccountRepository: UserAccountRepository,
    private val profileRepository: ProfileRepository,
    @ApplicationContext private val context: Context
) {
    private val workManager: WorkManager
        get() = WorkManagerProvider.getInstance(context)
    suspend operator fun invoke(idToken: String): Result<User> {
        if (idToken.isBlank()) {
            return Result.failure(IllegalArgumentException("Google ID token cannot be blank"))
        }
        
        // Authenticate with Google - this now handles profile creation gracefully
        val authResult = authRepository.signInWithGoogle(idToken)
        
        // Handle successful authentication - profile creation is now handled in the repository
        authResult.onSuccess { user ->
            try {
                // Check if UserAccount exists in local database
                val existingAccountResult = userAccountRepository.getAccountInfoSuspend(user.uid)
                val existingAccount = existingAccountResult.getOrNull()
                
                if (existingAccount == null) {
                    // Create UserAccount for new Google user
                    val username = generateUsernameFromGoogle(user)
                    val userAccount = UserAccount.create(
                        userId = user.uid,
                        email = user.email ?: "unknown@google.com",
                        displayName = user.displayName,
                        username = username
                    )
                    
                    userAccountRepository.upsertAccountInfo(userAccount).fold(
                        onSuccess = {
                            android.util.Log.d("SignInWithGoogleUseCase", "UserAccount created successfully for Google user ${user.uid} with username: ${userAccount.username}")
                            
                            // Trigger sync to make user searchable
                            val syncRequest = UserPublicSyncWorker.createWorkRequest(user.uid, forceSync = true)
                            workManager.enqueue(syncRequest)
                            android.util.Log.d("SignInWithGoogleUseCase", "Triggered UserPublicSyncWorker for Google user ${user.uid}")
                        },
                        onFailure = { error ->
                            android.util.Log.e("SignInWithGoogleUseCase", "CRITICAL: Failed to create UserAccount for user ${user.uid}. " +
                                    "Email: ${user.email}, DisplayName: ${user.displayName}, Username: ${userAccount.username}. " +
                                    "Error: $error")
                            
                            // Log detailed error information for debugging
                            when {
                                error.message?.contains("PERMISSION_DENIED") == true -> {
                                    android.util.Log.e("SignInWithGoogleUseCase", "Firestore PERMISSION_DENIED error - check security rules for users collection")
                                }
                                error.message?.contains("Username must be between 3 and 20 characters") == true -> {
                                    android.util.Log.e("SignInWithGoogleUseCase", "Username validation failed for: '${userAccount.username}' (length: ${userAccount.username?.length})")
                                }
                                else -> {
                                    android.util.Log.e("SignInWithGoogleUseCase", "Unexpected UserAccount creation error: ${error.javaClass.simpleName}")
                                }
                            }
                        }
                    )
                } else {
                    // Account exists, just trigger sync to ensure searchability is up to date
                    if (existingAccount.username != null) {
                        // Trigger sync to ensure searchability is up to date
                        val syncRequest = UserPublicSyncWorker.createWorkRequest(user.uid, forceSync = false)
                        workManager.enqueue(syncRequest)
                        android.util.Log.d("SignInWithGoogleUseCase", "Triggered sync for existing Google user ${user.uid}")
                    } else {
                        // User exists but has no username, generate one
                        val username = generateUsernameFromGoogle(user)
                        userAccountRepository.updateUsername(user.uid, username).fold(
                            onSuccess = {
                                android.util.Log.d("SignInWithGoogleUseCase", "Username set for existing Google user ${user.uid}")
                                
                                // Trigger sync after username update
                                val syncRequest = UserPublicSyncWorker.createWorkRequest(user.uid, forceSync = true)
                                workManager.enqueue(syncRequest)
                                android.util.Log.d("SignInWithGoogleUseCase", "Triggered sync after username update for ${user.uid}")
                            },
                            onFailure = { error ->
                                android.util.Log.e("SignInWithGoogleUseCase", "Failed to set username: $error")
                            }
                        )
                    }
                }
                
                // ONBOARDING FIX: Sync profile data after successful Google login
                syncOnboardingProfileAfterLogin(user.uid)
                
                // 🔥 FOLLOW-SYNC-FIX: Restore follow relationships after Google login
                restoreFollowRelationshipsAfterLogin(user.uid)
                
            } catch (e: Exception) {
                android.util.Log.e("SignInWithGoogleUseCase", "Error handling UserAccount for Google sign-in", e)
                // Don't fail the sign-in if UserAccount creation fails
                // User is still authenticated with Firebase
            }
        }
        
        return authResult
    }
    
    /**
     * Generate a username from Google user data with robust validation.
     * Ensures the generated username always meets domain requirements:
     * - Length between 3-20 characters
     * - Contains only alphanumeric characters and underscores
     * - No consecutive underscores or leading/trailing underscores
     */
    private fun generateUsernameFromGoogle(user: User): String {
        // Helper function to sanitize and validate username candidates
        fun sanitizeUsername(input: String): String? {
            val sanitized = input.lowercase()
                .replace(Regex("[^a-z0-9_]"), "_") // Replace invalid chars with underscore
                .replace(Regex("_+"), "_") // Replace multiple underscores with single
                .trim('_') // Remove leading/trailing underscores
                .take(15) // Leave space for suffix (max 20 - 5 for suffix)
            
            return if (sanitized.length >= 3 && sanitized.matches(Regex("^[a-z0-9_]+$"))) {
                sanitized
            } else null
        }
        
        // Strategy 1: Try email prefix
        user.email?.substringBefore("@")?.let { emailPrefix ->
            sanitizeUsername(emailPrefix)?.let { validPrefix ->
                val suffix = (1000..9999).random()
                val candidate = "${validPrefix}_$suffix"
                if (candidate.length <= 20) {
                    android.util.Log.d("SignInWithGoogleUseCase", "Generated username from email: $candidate")
                    return candidate
                }
            }
        }
        
        // Strategy 2: Try display name
        user.displayName?.let { displayName ->
            sanitizeUsername(displayName)?.let { validName ->
                val suffix = (1000..9999).random()
                val candidate = "${validName}_$suffix"
                if (candidate.length <= 20) {
                    android.util.Log.d("SignInWithGoogleUseCase", "Generated username from display name: $candidate")
                    return candidate
                }
            }
        }
        
        // Strategy 3: Use shortened user ID (guaranteed to work)
        val shortUid = user.uid.take(8).lowercase()
        val suffix = (100..999).random()
        val fallbackUsername = "user_${shortUid}_$suffix"
        
        // Final validation - this should never fail, but safety check
        if (fallbackUsername.length > 20) {
            val timestamp = (System.currentTimeMillis() % 10000).toString()
            return "user_$timestamp"
        }
        
        android.util.Log.d("SignInWithGoogleUseCase", "Generated fallback username: $fallbackUsername")
        return fallbackUsername
    }
    
    /**
     * Syncs any onboarding profile data that was collected during Getting Started
     * but not yet synced to Firebase. This ensures profile data persists after login.
     */
    private suspend fun syncOnboardingProfileAfterLogin(userId: String) {
        withContext(Dispatchers.IO) {
            try {
                android.util.Log.d("SignInWithGoogleUseCase", "Checking for unsynced onboarding profile for user $userId")
                
                // Check if user has a profile that needs syncing
                val hasProfile = profileRepository.hasProfile(userId)
                if (!hasProfile) {
                    android.util.Log.d("SignInWithGoogleUseCase", "No profile found for user $userId - skipping profile sync")
                    return@withContext
                }
                
                // Check for unsynced profile data
                val unsyncedCount = profileRepository.getUnsyncedCount(userId)
                if (unsyncedCount > 0) {
                    android.util.Log.d("SignInWithGoogleUseCase", "Found $unsyncedCount unsynced profile entries for user $userId")
                    
                    // Queue and trigger immediate sync
                    profileRepository.queueSync(userId)
                    val syncResult = profileRepository.syncNow(userId)
                    
                    if (syncResult.isSuccess) {
                        android.util.Log.d("SignInWithGoogleUseCase", "Successfully synced onboarding profile for user $userId")
                    } else {
                        android.util.Log.w("SignInWithGoogleUseCase", "Failed to sync profile immediately, will retry in background: ${syncResult.exceptionOrNull()?.message}")
                    }
                } else {
                    android.util.Log.d("SignInWithGoogleUseCase", "Profile already synced for user $userId")
                }
            } catch (e: Exception) {
                android.util.Log.e("SignInWithGoogleUseCase", "Error syncing onboarding profile: ${e.message}")
                // Non-critical failure - profile will sync eventually through background workers
            }
        }
    }
    
    /**
     * 🔥 FOLLOW-SYNC-FIX: Restores follow relationships from Firebase after Google login.
     * This ensures that follow relationships persist even if local database was cleared during logout.
     */
    private suspend fun restoreFollowRelationshipsAfterLogin(userId: String) {
        withContext(Dispatchers.IO) {
            try {
                android.util.Log.d("SignInWithGoogleUseCase", "🔥 FOLLOW-SYNC-FIX: Starting follow relationship restoration for user $userId")
                
                // Schedule follow relationship restoration work
                val restoreWorkRequest = FollowRelationshipSyncWorker.createRestoreWorkRequest(userId)
                workManager.enqueue(restoreWorkRequest)
                
                android.util.Log.d("SignInWithGoogleUseCase", "🔥 FOLLOW-SYNC-FIX: Queued follow relationship restoration work for user $userId")
                
            } catch (e: Exception) {
                android.util.Log.e("SignInWithGoogleUseCase", "🔥 FOLLOW-SYNC-FIX: Error queuing follow restoration work: ${e.message}")
                // Non-critical failure - follow relationships may be restored later by regular sync
            }
        }
    }
} 