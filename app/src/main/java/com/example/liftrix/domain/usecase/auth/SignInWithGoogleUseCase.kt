package com.example.liftrix.domain.usecase.auth

import com.example.liftrix.domain.model.User
import com.example.liftrix.domain.model.UserAccount
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.repository.UserAccountRepository
import com.example.liftrix.sync.UserPublicSyncWorker
import com.example.liftrix.domain.model.common.LiftrixResult
import androidx.work.WorkManager
import java.time.LocalDateTime
import javax.inject.Inject

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
    private val workManager: WorkManager
) {
    suspend operator fun invoke(idToken: String): Result<User> {
        if (idToken.isBlank()) {
            return Result.failure(IllegalArgumentException("Google ID token cannot be blank"))
        }
        
        // Authenticate with Google
        val authResult = authRepository.signInWithGoogle(idToken)
        
        // Handle successful authentication
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
                            android.util.Log.d("SignInWithGoogleUseCase", "UserAccount created for Google user ${user.uid}")
                            
                            // Trigger sync to make user searchable
                            val syncRequest = UserPublicSyncWorker.createWorkRequest(user.uid, forceSync = true)
                            workManager.enqueue(syncRequest)
                            android.util.Log.d("SignInWithGoogleUseCase", "Triggered UserPublicSyncWorker for Google user ${user.uid}")
                        },
                        onFailure = { error ->
                            android.util.Log.e("SignInWithGoogleUseCase", "Failed to create UserAccount: $error")
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
            } catch (e: Exception) {
                android.util.Log.e("SignInWithGoogleUseCase", "Error handling UserAccount for Google sign-in", e)
                // Don't fail the sign-in if UserAccount creation fails
                // User is still authenticated with Firebase
            }
        }
        
        return authResult
    }
    
    /**
     * Generate a username from Google user data.
     * Uses email prefix or display name as fallback.
     */
    private fun generateUsernameFromGoogle(user: User): String {
        // Try to use email prefix as username
        val emailPrefix = user.email?.substringBefore("@")?.lowercase()
            ?.replace(Regex("[^a-z0-9_]"), "_")
            ?.take(20) // Limit username length
        
        // If email prefix is available and valid, use it
        if (!emailPrefix.isNullOrBlank() && emailPrefix.length >= 3) {
            // Add random suffix to avoid conflicts
            val suffix = (1000..9999).random()
            return "${emailPrefix}_${suffix}"
        }
        
        // Fallback to display name if available
        val displayNameUsername = user.displayName?.lowercase()
            ?.replace(Regex("[^a-z0-9_]"), "_")
            ?.take(20)
        
        if (!displayNameUsername.isNullOrBlank() && displayNameUsername.length >= 3) {
            val suffix = (1000..9999).random()
            return "${displayNameUsername}_${suffix}"
        }
        
        // Last resort: generate a random username
        return "user_${System.currentTimeMillis()}"
    }
} 