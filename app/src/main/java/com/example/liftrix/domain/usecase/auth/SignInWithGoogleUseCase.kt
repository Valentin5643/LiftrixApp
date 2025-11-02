package com.example.liftrix.domain.usecase.auth

import android.content.Context
import androidx.work.WorkManager
import com.example.liftrix.core.workmanager.WorkManagerProvider
import com.example.liftrix.domain.model.User
import com.example.liftrix.domain.model.UserAccount
import com.example.liftrix.domain.model.UserProfile
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.repository.UserAccountRepository
import com.example.liftrix.domain.repository.ProfileRepository
import com.example.liftrix.domain.usecase.social.SocialProfileCommandUseCase
import com.example.liftrix.domain.usecase.profile.SaveUserProfileUseCase
import com.example.liftrix.sync.UserPublicSyncWorker
import com.example.liftrix.sync.FollowRelationshipSyncWorker
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.service.OnboardingDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDateTime
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runBlocking

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
    private val socialProfileCommandUseCase: SocialProfileCommandUseCase,
    private val saveUserProfileUseCase: SaveUserProfileUseCase,
    private val onboardingDataStore: OnboardingDataStore,
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
                            
                            // SOCIAL PROFILE FIX: Create social profile immediately after UserAccount creation
                            // 🔥 SERIALIZATION FIX: Add delay to prevent race condition with profile queue operations
                            try {
                                kotlinx.coroutines.delay(1000) // Allow UserAccount to be fully committed before social profile creation
                                
                                val socialProfileResult = runBlocking {
                                    socialProfileCommandUseCase.create(
                                        username = userAccount.username ?: "user_${user.uid.take(8)}",
                                        displayName = user.displayName ?: userAccount.username ?: "User",
                                        bio = null
                                    )
                                }
                                
                                socialProfileResult.fold(
                                    onSuccess = {
                                        android.util.Log.d("SignInWithGoogleUseCase", "✅ Social profile created for Google user ${user.uid}")
                                    },
                                    onFailure = { socialError ->
                                        android.util.Log.e("SignInWithGoogleUseCase", "❌ Failed to create social profile: ${socialError.message}")
                                    }
                                )
                            } catch (e: Exception) {
                                android.util.Log.e("SignInWithGoogleUseCase", "❌ Exception creating social profile: ${e.message}")
                            }
                            
                            // USER PROFILE FIX: Create complete UserProfile for Gmail users (matching email signup flow)
                            try {
                                val userProfile = UserProfile(
                                    userId = user.uid,
                                    displayName = user.displayName ?: userAccount.username ?: "User", 
                                    bio = null,
                                    age = null,
                                    weight = null,
                                    availableEquipment = emptyList(),
                                    otherEquipment = null,
                                    fitnessGoals = emptyList(),
                                    goalsPriority = null,
                                    isPublic = true,  // Set to public by default for searchability
                                    lastActiveAt = java.time.LocalDateTime.now(),
                                    totalWorkouts = 0,
                                    currentStreak = 0,
                                    longestStreak = 0,
                                    memberSince = java.time.LocalDateTime.now(),
                                    profileCompletionPercentage = 70, // Gmail provides some data
                                    achievements = emptyList(),
                                    completedAt = null,
                                    updatedAt = java.time.LocalDateTime.now(),
                                    profileVersion = 1L,
                                    profileImageUrl = user.photoUrl,
                                    profileImageUpdatedAt = user.photoUrl?.let { java.time.LocalDateTime.now() },
                                    hasCustomProfileImage = user.photoUrl != null
                                )
                                
                                val userProfileResult = runBlocking {
                                    saveUserProfileUseCase(userProfile)
                                }
                                
                                userProfileResult.fold(
                                    onSuccess = {
                                        android.util.Log.d("SignInWithGoogleUseCase", "✅ User profile created for Google user ${user.uid}")
                                    },
                                    onFailure = { profileError ->
                                        android.util.Log.e("SignInWithGoogleUseCase", "❌ Failed to create user profile: ${profileError.message}")
                                    }
                                )
                            } catch (e: Exception) {
                                android.util.Log.e("SignInWithGoogleUseCase", "❌ Exception creating user profile: ${e.message}")
                            }
                            
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
                    // Account exists, check if social profile also exists
                    if (existingAccount.username != null) {
                        // SOCIAL PROFILE FIX: Ensure existing users have social profiles
                        // 🔥 SERIALIZATION FIX: Add delay to prevent race condition with profile queue operations
                        try {
                            kotlinx.coroutines.delay(500) // Shorter delay for existing users
                            
                            // Check if social profile exists by attempting to create one
                            val socialProfileResult = runBlocking {
                                socialProfileCommandUseCase.create(
                                    username = existingAccount.username!!,
                                    displayName = user.displayName ?: existingAccount.displayName ?: existingAccount.username!!,
                                    bio = null
                                )
                            }
                            
                            socialProfileResult.fold(
                                onSuccess = {
                                    android.util.Log.d("SignInWithGoogleUseCase", "✅ Social profile ensured for existing Google user ${user.uid}")
                                },
                                onFailure = { socialError ->
                                    // Profile might already exist - this is okay
                                    android.util.Log.d("SignInWithGoogleUseCase", "Social profile check for existing user: ${socialError.message}")
                                }
                            )
                        } catch (e: Exception) {
                            android.util.Log.d("SignInWithGoogleUseCase", "Social profile check exception (likely already exists): ${e.message}")
                        }
                        
                        // USER PROFILE FIX: Ensure existing users also have UserProfile documents
                        try {
                            val userProfile = UserProfile(
                                userId = user.uid,
                                displayName = user.displayName ?: existingAccount.displayName ?: existingAccount.username ?: "User",
                                bio = null,
                                age = null,
                                weight = null,
                                availableEquipment = emptyList(),
                                otherEquipment = null,
                                fitnessGoals = emptyList(),
                                goalsPriority = null,
                                isPublic = true,
                                lastActiveAt = java.time.LocalDateTime.now(),
                                totalWorkouts = 0,
                                currentStreak = 0,
                                longestStreak = 0,
                                memberSince = existingAccount.accountCreatedAt ?: java.time.LocalDateTime.now(),
                                profileCompletionPercentage = 70,
                                achievements = emptyList(),
                                completedAt = null,
                                updatedAt = java.time.LocalDateTime.now(),
                                profileVersion = 1L,
                                profileImageUrl = user.photoUrl,
                                profileImageUpdatedAt = user.photoUrl?.let { java.time.LocalDateTime.now() },
                                hasCustomProfileImage = user.photoUrl != null
                            )
                            
                            val userProfileResult = runBlocking {
                                saveUserProfileUseCase(userProfile)
                            }
                            
                            userProfileResult.fold(
                                onSuccess = {
                                    android.util.Log.d("SignInWithGoogleUseCase", "✅ User profile ensured for existing Google user ${user.uid}")
                                },
                                onFailure = { profileError ->
                                    // Profile might already exist - this is okay
                                    android.util.Log.d("SignInWithGoogleUseCase", "User profile check for existing user: ${profileError.message}")
                                }
                            )
                        } catch (e: Exception) {
                            android.util.Log.d("SignInWithGoogleUseCase", "User profile check exception (likely already exists): ${e.message}")
                        }
                        
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
                                
                                // SOCIAL PROFILE FIX: Create social profile after username generation
                                try {
                                    val socialProfileResult = runBlocking {
                                        socialProfileCommandUseCase.create(
                                            username = username,
                                            displayName = user.displayName ?: username,
                                            bio = null
                                        )
                                    }
                                    
                                    socialProfileResult.fold(
                                        onSuccess = {
                                            android.util.Log.d("SignInWithGoogleUseCase", "✅ Social profile created for existing user with new username ${user.uid}")
                                        },
                                        onFailure = { socialError ->
                                            android.util.Log.e("SignInWithGoogleUseCase", "❌ Failed to create social profile for existing user: ${socialError.message}")
                                        }
                                    )
                                } catch (e: Exception) {
                                    android.util.Log.e("SignInWithGoogleUseCase", "❌ Exception creating social profile for existing user: ${e.message}")
                                }
                                
                                // USER PROFILE FIX: Create UserProfile for users with new usernames
                                try {
                                    val userProfile = UserProfile(
                                        userId = user.uid,
                                        displayName = user.displayName ?: username,
                                        bio = null,
                                        age = null,
                                        weight = null,
                                        availableEquipment = emptyList(),
                                        otherEquipment = null,
                                        fitnessGoals = emptyList(),
                                        goalsPriority = null,
                                        isPublic = true,
                                        lastActiveAt = java.time.LocalDateTime.now(),
                                        totalWorkouts = 0,
                                        currentStreak = 0,
                                        longestStreak = 0,
                                        memberSince = java.time.LocalDateTime.now(),
                                        profileCompletionPercentage = 70,
                                        achievements = emptyList(),
                                        completedAt = null,
                                        updatedAt = java.time.LocalDateTime.now(),
                                        profileVersion = 1L,
                                        profileImageUrl = user.photoUrl,
                                        profileImageUpdatedAt = user.photoUrl?.let { java.time.LocalDateTime.now() },
                                        hasCustomProfileImage = user.photoUrl != null
                                    )
                                    
                                    val userProfileResult = runBlocking {
                                        saveUserProfileUseCase(userProfile)
                                    }
                                    
                                    userProfileResult.fold(
                                        onSuccess = {
                                            android.util.Log.d("SignInWithGoogleUseCase", "✅ User profile created for existing user with new username ${user.uid}")
                                        },
                                        onFailure = { profileError ->
                                            android.util.Log.e("SignInWithGoogleUseCase", "❌ Failed to create user profile for existing user: ${profileError.message}")
                                        }
                                    )
                                } catch (e: Exception) {
                                    android.util.Log.e("SignInWithGoogleUseCase", "❌ Exception creating user profile for existing user: ${e.message}")
                                }
                                
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
                
                // ONBOARDING DATA PERSISTENCE FIX: Transfer pending onboarding data after successful authentication
                transferOnboardingDataAfterLogin(user.uid)
                
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
     * ONBOARDING DATA PERSISTENCE FIX: Transfers pending onboarding data to authenticated user.
     * 
     * This critical method solves the guest→authenticated user data loss issue by:
     * 1. Checking for temporarily stored onboarding data
     * 2. Transferring the data to the authenticated user profile
     * 3. Triggering immediate sync to ensure persistence
     * 4. Cleaning up temporary storage
     */
    private suspend fun transferOnboardingDataAfterLogin(userId: String) {
        withContext(Dispatchers.IO) {
            try {
                android.util.Log.d("SignInWithGoogleUseCase", "🔧 ONBOARDING-FIX: Checking for pending onboarding data to transfer to authenticated user $userId")
                
                // Check if there's pending onboarding data
                val hasPendingData = onboardingDataStore.hasPendingOnboardingData()
                
                if (hasPendingData) {
                    android.util.Log.d("SignInWithGoogleUseCase", "🔧 ONBOARDING-FIX: Found pending onboarding data, initiating transfer")
                    
                    val pendingDataSummary = onboardingDataStore.getPendingDataSummary()
                    android.util.Log.d("SignInWithGoogleUseCase", "🔧 ONBOARDING-FIX: Pending data summary: $pendingDataSummary")
                    
                    // Retrieve and transfer the pending data
                    val transferResult = onboardingDataStore.retrievePendingOnboardingData(userId)
                    
                    transferResult.fold(
                        onSuccess = { pendingData ->
                            if (pendingData != null) {
                                android.util.Log.d("SignInWithGoogleUseCase", "🔧 ONBOARDING-FIX: Successfully retrieved pending data, creating profile for user $userId")
                                
                                // Validate the data is complete
                                if (pendingData.isCompleteForSaving()) {
                                    // Convert to domain model and save
                                    val userProfile = pendingData.toDomainModel()
                                    
                                    // Use SaveProfileUseCase to save the profile
                                    profileRepository.saveProfile(userProfile).fold(
                                        onSuccess = {
                                            android.util.Log.d("SignInWithGoogleUseCase", "🔧 ONBOARDING-FIX: Successfully saved transferred profile for user $userId")
                                            
                                            // Clear pending data after successful save
                                            onboardingDataStore.clearPendingOnboardingData()
                                            
                                            // Trigger immediate sync
                                            profileRepository.queueSync(userId)
                                            profileRepository.syncNow(userId)
                                            
                                            android.util.Log.d("SignInWithGoogleUseCase", "🔧 ONBOARDING-FIX: Onboarding data transfer completed successfully")
                                        },
                                        onFailure = { saveError ->
                                            android.util.Log.e("SignInWithGoogleUseCase", "🔧 ONBOARDING-FIX: Failed to save transferred profile: ${saveError.message}")
                                        }
                                    )
                                } else {
                                    android.util.Log.w("SignInWithGoogleUseCase", "🔧 ONBOARDING-FIX: Pending data is incomplete, cannot transfer")
                                }
                            } else {
                                android.util.Log.d("SignInWithGoogleUseCase", "🔧 ONBOARDING-FIX: No valid pending data found")
                            }
                        },
                        onFailure = { error ->
                            android.util.Log.e("SignInWithGoogleUseCase", "🔧 ONBOARDING-FIX: Failed to retrieve pending data: ${error.message}")
                        }
                    )
                } else {
                    android.util.Log.d("SignInWithGoogleUseCase", "🔧 ONBOARDING-FIX: No pending onboarding data found")
                    
                    // Fall back to original sync logic for existing profiles
                    syncExistingProfileIfNeeded(userId)
                }
            } catch (e: Exception) {
                android.util.Log.e("SignInWithGoogleUseCase", "🔧 ONBOARDING-FIX: Error during onboarding data transfer: ${e.message}")
                // Non-critical failure - attempt fallback sync
                syncExistingProfileIfNeeded(userId)
            }
        }
    }
    
    /**
     * Fallback method to sync existing profile data (original logic).
     */
    private suspend fun syncExistingProfileIfNeeded(userId: String) {
        try {
            android.util.Log.d("SignInWithGoogleUseCase", "Checking for existing profile that needs syncing for user $userId")
            
            val hasProfile = profileRepository.hasProfile(userId)
            if (hasProfile) {
                val unsyncedCount = profileRepository.getUnsyncedCount(userId)
                if (unsyncedCount > 0) {
                    android.util.Log.d("SignInWithGoogleUseCase", "Found $unsyncedCount unsynced profile entries for user $userId")
                    
                    profileRepository.queueSync(userId)
                    val syncResult = profileRepository.syncNow(userId)
                    
                    if (syncResult.isSuccess) {
                        android.util.Log.d("SignInWithGoogleUseCase", "Successfully synced existing profile for user $userId")
                    } else {
                        android.util.Log.w("SignInWithGoogleUseCase", "Failed to sync profile immediately, will retry in background")
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("SignInWithGoogleUseCase", "Error syncing existing profile: ${e.message}")
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