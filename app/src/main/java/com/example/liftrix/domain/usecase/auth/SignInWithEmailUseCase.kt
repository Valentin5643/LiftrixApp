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

class SignInWithEmailUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val userAccountRepository: UserAccountRepository,
    private val workManager: WorkManager
) {
    suspend operator fun invoke(email: String, password: String): Result<User> {
        if (email.isBlank()) {
            return Result.failure(IllegalArgumentException("Email cannot be blank"))
        }
        
        if (password.isBlank()) {
            return Result.failure(IllegalArgumentException("Password cannot be blank"))
        }
        
        if (!isValidEmail(email)) {
            return Result.failure(IllegalArgumentException("Invalid email format"))
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
                
                Result.success(user)
            },
            onFailure = { Result.failure(it) }
        )
    }
    
    private fun isValidEmail(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }
} 