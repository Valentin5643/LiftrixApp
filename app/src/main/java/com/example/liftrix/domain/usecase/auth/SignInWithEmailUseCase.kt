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
import com.example.liftrix.domain.model.common.LiftrixResult
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDateTime
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SignInWithEmailUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val userAccountRepository: UserAccountRepository,
    private val profileRepository: ProfileRepository,
    @ApplicationContext private val context: Context
) {
    private val workManager: WorkManager
        get() = WorkManagerProvider.getInstance(context)
    suspend operator fun invoke(email: String, password: String): LiftrixResult<User> {
        if (email.isBlank()) {
            return LiftrixResult.failure(IllegalArgumentException("Email cannot be blank"))
        }
        
        if (password.isBlank()) {
            return LiftrixResult.failure(IllegalArgumentException("Password cannot be blank"))
        }
        
        if (!isValidEmail(email)) {
            return LiftrixResult.failure(IllegalArgumentException("Invalid email format"))
        }
        
        val signInResult = authRepository.signInWithEmail(email, password)
        
        // After successful sign in, ensure UserAccount exists for searchability
        return signInResult.fold(
            onSuccess = { user ->
                // Check if UserAccount exists, create if missing (for backward compatibility)
                val accountResult = userAccountRepository.getAccountInfoSuspend(user.uid)
                accountResult.fold(
                    onSuccess = { existingAccount ->
                        if (existingAccount == null) {
                            // No account exists, create one for backward compatibility
                            android.util.Log.w("SignInWithEmailUseCase", "UserAccount missing for existing user ${user.uid}, creating now")
                            
                            val userAccount = UserAccount(
                                userId = user.uid,
                                email = email,
                                username = null,  // Will be set later when user sets username
                                emailVerified = user.isEmailVerified,
                                displayName = user.displayName ?: email.substringBefore("@"),
                                lastPasswordChange = null,
                                accountCreatedAt = LocalDateTime.now(),
                                lastEmailUpdate = null,
                                deletionRequestedAt = null
                            )
                            
                            // Save UserAccount to local database
                            val upsertResult = userAccountRepository.upsertAccountInfo(userAccount)
                            upsertResult.fold(
                                onSuccess = {
                                    android.util.Log.d("SignInWithEmailUseCase", "UserAccount created for existing user ${user.uid}")
                                    
                                    // Trigger sync to make searchable
                                    val syncRequest = UserPublicSyncWorker.createWorkRequest(user.uid, forceSync = true)
                                    workManager.enqueue(syncRequest)
                                    android.util.Log.d("SignInWithEmailUseCase", "Triggered UserPublicSyncWorker for existing user ${user.uid}")
                                },
                                onFailure = { error ->
                                    android.util.Log.e("SignInWithEmailUseCase", "Failed to create UserAccount: $error")
                                }
                            )
                        } else {
                            // Account exists, check if we need to trigger sync
                            if (existingAccount.username != null) {
                                // Trigger sync to ensure searchability is up to date
                                val syncRequest = UserPublicSyncWorker.createWorkRequest(user.uid, forceSync = false)
                                workManager.enqueue(syncRequest)
                                android.util.Log.d("SignInWithEmailUseCase", "Triggered UserPublicSyncWorker for user ${user.uid}")
                            }
                        }
                    },
                    onFailure = { error ->
                        android.util.Log.e("SignInWithEmailUseCase", "Failed to check UserAccount: $error")
                    }
                )
                
                // ONBOARDING FIX: Sync profile data after successful login
                syncOnboardingProfileAfterLogin(user.uid)
                
                LiftrixResult.success(user)
            },
            onFailure = { LiftrixResult.failure(it) }
        )
    }
    
    private fun isValidEmail(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }
    
    /**
     * Syncs any onboarding profile data that was collected during Getting Started
     * but not yet synced to Firebase. This ensures profile data persists after login.
     */
    private suspend fun syncOnboardingProfileAfterLogin(userId: String) {
        withContext(Dispatchers.IO) {
            try {
                android.util.Log.d("SignInWithEmailUseCase", "Checking for unsynced onboarding profile for user $userId")
                
                // Check if user has a profile that needs syncing
                val hasProfile = profileRepository.hasProfile(userId)
                if (!hasProfile) {
                    android.util.Log.d("SignInWithEmailUseCase", "No profile found for user $userId - skipping profile sync")
                    return@withContext
                }
                
                // Check for unsynced profile data
                val unsyncedCount = profileRepository.getUnsyncedCount(userId)
                if (unsyncedCount > 0) {
                    android.util.Log.d("SignInWithEmailUseCase", "Found $unsyncedCount unsynced profile entries for user $userId")
                    
                    // Queue and trigger immediate sync
                    profileRepository.queueSync(userId)
                    val syncResult = profileRepository.syncNow(userId)
                    
                    if (syncResult.isSuccess) {
                        android.util.Log.d("SignInWithEmailUseCase", "Successfully synced onboarding profile for user $userId")
                    } else {
                        android.util.Log.w("SignInWithEmailUseCase", "Failed to sync profile immediately, will retry in background: ${syncResult.exceptionOrNull()?.message}")
                    }
                } else {
                    android.util.Log.d("SignInWithEmailUseCase", "Profile already synced for user $userId")
                }
            } catch (e: Exception) {
                android.util.Log.e("SignInWithEmailUseCase", "Error syncing onboarding profile: ${e.message}")
                // Non-critical failure - profile will sync eventually through background workers
            }
        }
    }
} 