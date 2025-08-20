package com.example.liftrix.domain.usecase.auth

import com.example.liftrix.domain.model.User
import com.example.liftrix.domain.model.UserAccount
import com.example.liftrix.domain.model.UserProfile
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.repository.UserAccountRepository
import com.example.liftrix.domain.usecase.social.CreateSocialProfileUseCase
import com.example.liftrix.domain.usecase.profile.SaveUserProfileUseCase
import com.example.liftrix.sync.UserPublicSyncWorker
import androidx.work.WorkManager
import com.example.liftrix.domain.model.common.LiftrixResult
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

class SignUpWithEmailUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val userAccountRepository: UserAccountRepository,
    private val createSocialProfileUseCase: CreateSocialProfileUseCase,
    private val saveUserProfileUseCase: SaveUserProfileUseCase,
    private val workManager: WorkManager,
    private val firestore: FirebaseFirestore
) {
    suspend operator fun invoke(email: String, password: String, username: String): Result<User> {
        android.util.Log.i("SignUpWithEmailUseCase", "Starting signup for username: $username")
        
        if (email.isBlank()) {
            return Result.failure(IllegalArgumentException("Email cannot be blank"))
        }
        
        if (password.isBlank()) {
            return Result.failure(IllegalArgumentException("Password cannot be blank"))
        }
        
        if (username.isBlank()) {
            return Result.failure(IllegalArgumentException("Username cannot be blank"))
        }
        
        if (username.length < 3) {
            return Result.failure(IllegalArgumentException("Username must be at least 3 characters"))
        }
        
        if (username.length > 20) {
            return Result.failure(IllegalArgumentException("Username must be 20 characters or less"))
        }
        
        if (!username.matches(Regex("^[a-zA-Z0-9_]+$"))) {
            return Result.failure(IllegalArgumentException("Username can only contain letters, numbers, and underscores"))
        }
        
        if (!isValidEmail(email)) {
            return Result.failure(IllegalArgumentException("Invalid email format"))
        }
        
        if (!isValidPassword(password)) {
            return Result.failure(IllegalArgumentException("Password must be at least 6 characters long"))
        }
        
        // Sign up with email using username as the display name initially
        val userResult = authRepository.signUpWithEmail(email, password, username)
        
        // If sign up successful, create both profiles
        return userResult.fold(
            onSuccess = { user ->
                android.util.Log.i("SignUpWithEmailUseCase", "Authentication successful for user: ${user.uid}")
                
                // Create UserAccount entity first to ensure username is saved locally
                val userAccount = UserAccount(
                    userId = user.uid,
                    email = email,
                    username = username,  // Save the username
                    emailVerified = false,
                    displayName = username,  // Use username as display name initially
                    lastPasswordChange = null,
                    accountCreatedAt = LocalDateTime.now(),
                    lastEmailUpdate = null,
                    deletionRequestedAt = null
                )
                
                // Save UserAccount to local database
                val accountResult = userAccountRepository.upsertAccountInfo(userAccount)
                accountResult.fold(
                    onSuccess = { 
                        android.util.Log.i("SignUpWithEmailUseCase", "UserAccount saved successfully")
                    },
                    onFailure = { error -> 
                        android.util.Log.e("SignUpWithEmailUseCase", "UserAccount save failed: $error")
                    }
                )
                
                // Create UserProfile with username as displayName
                val userProfile = UserProfile(
                    userId = user.uid,
                    displayName = username, // Use username as display name
                    bio = null,
                    age = null,
                    weight = null,
                    availableEquipment = emptyList(),
                    otherEquipment = null,
                    fitnessGoals = emptyList(),
                    goalsPriority = emptyMap(),
                    isPublic = true,  // Set to public by default for searchability
                    lastActiveAt = LocalDateTime.now(),
                    totalWorkouts = 0,
                    currentStreak = 0,
                    longestStreak = 0,
                    memberSince = LocalDateTime.now(),
                    profileCompletionPercentage = 0,
                    achievements = emptyList(),
                    completedAt = null,
                    updatedAt = LocalDateTime.now(),
                    profileVersion = 1,
                    profileImageUrl = null,
                    profileImageUpdatedAt = null,
                    hasCustomProfileImage = false
                )
                
                // Save the UserProfile
                val userProfileResult = saveUserProfileUseCase(userProfile)
                
                // Create social profile with the username
                val socialProfileResult = createSocialProfileUseCase(
                    username = username,
                    displayName = username, // Use username as display name initially
                    bio = null
                )
                
                // Log any profile creation failures but still return success for auth
                userProfileResult.fold(
                    onSuccess = { 
                        android.util.Log.i("SignUpWithEmailUseCase", "UserProfile saved successfully")
                    },
                    onFailure = { 
                        android.util.Log.e("SignUpWithEmailUseCase", "UserProfile save failed: ${it.message}")
                    }
                )
                
                socialProfileResult.fold(
                    onSuccess = { 
                        android.util.Log.i("SignUpWithEmailUseCase", "SocialProfile created successfully")
                    },
                    onFailure = { 
                        android.util.Log.e("SignUpWithEmailUseCase", "SocialProfile creation failed: ${it.message}")
                    }
                )
                
                // Trigger immediate sync to ensure searchability
                // Even though userAccountRepository.upsertAccountInfo() triggers sync,
                // we'll also trigger it explicitly here with a delay to ensure all data is ready
                kotlinx.coroutines.delay(500) // Allow time for all local DB writes to complete
                
                val syncRequest = UserPublicSyncWorker.createWorkRequest(
                    userId = user.uid,
                    forceSync = true
                )
                workManager.enqueue(syncRequest)
                
                // Also trigger an immediate direct Firebase write for critical search data
                // This ensures the user is searchable even if WorkManager is delayed
                try {
                    ensureImmediateSearchability(user.uid, username, userProfile)
                } catch (e: Exception) {
                    android.util.Log.w("SignUpWithEmailUseCase", "Immediate searchability failed, relying on WorkManager sync: ${e.message}")
                    // Don't fail signup - WorkManager will eventually make user searchable
                }
                
                android.util.Log.i("SignUpWithEmailUseCase", "Signup flow completed successfully")
                
                // Return the user even if profile creation fails
                // (we can retry profile creation later)
                Result.success(user)
            },
            onFailure = { 
                android.util.Log.e("SignUpWithEmailUseCase", "Authentication failed: $it")
                Result.failure(it) 
            }
        )
    }
    
    private fun isValidEmail(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }
    
    private fun isValidPassword(password: String): Boolean {
        return password.length >= 6
    }
    
    /**
     * Ensures the user is immediately searchable by directly writing to Firebase.
     * This bypasses WorkManager delays and ensures the user can be found right after signup.
     */
    private suspend fun ensureImmediateSearchability(
        userId: String,
        username: String,
        userProfile: UserProfile
    ) {
        try {
            
            val now = LocalDateTime.now()
            val nowStr = now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            
            // Generate search tokens for the username
            val searchTokens = generateSearchTokens(username, userProfile.displayName)
            
            // Create minimal public profile data for immediate searchability
            val publicUserData = mutableMapOf(
                "userId" to userId,
                "username" to username.lowercase().trim(),
                "displayName" to (userProfile.displayName ?: username),
                "isPublic" to true, // Ensure it's public for searchability
                "isPrivate" to false,
                "memberSince" to nowStr,
                "lastActiveAt" to nowStr,
                "searchTokens" to searchTokens,
                "totalWorkouts" to 0,
                "currentStreak" to 0,
                "longestStreak" to 0,
                "followersCount" to 0,
                "followingCount" to 0,
                "syncVersion" to System.currentTimeMillis(),
                "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )
            
            // Only include bio if it's not null
            userProfile.bio?.let { bio ->
                publicUserData["bio"] = bio
            }
            
            // Write to users_public collection
            firestore.collection("users_public")
                .document(userId)
                .set(publicUserData, SetOptions.merge())
                .await()
            
            // Also write to user_search_cache for tokenized search
            val searchCacheData = mutableMapOf(
                "userId" to userId,
                "username" to username.lowercase().trim(),
                "displayName" to (userProfile.displayName ?: username),
                "isPublic" to true,
                "searchTokens" to searchTokens,
                "keywords" to listOf(username, userProfile.displayName ?: username),
                "memberSince" to nowStr,
                "lastActiveAt" to nowStr,
                "totalWorkouts" to 0,
                "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )
            
            // Only include bio if it's not null
            userProfile.bio?.let { bio ->
                searchCacheData["bio"] = bio
            }
            
            firestore.collection("user_search_cache")
                .document(userId)
                .set(searchCacheData, SetOptions.merge())
                .await()
            
            android.util.Log.i("SignUpWithEmailUseCase", "User searchability setup completed successfully")
            
        } catch (e: Exception) {
            // Don't fail signup if immediate searchability fails
            // The background sync will eventually make the user searchable
            android.util.Log.e("SignUpWithEmailUseCase", "Failed to setup immediate searchability: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Generates search tokens for better search performance.
     */
    private fun generateSearchTokens(username: String, displayName: String?): List<String> {
        val tokens = mutableSetOf<String>()
        
        // Add username tokens
        val cleanUsername = username.lowercase().trim()
        tokens.add(cleanUsername)
        if (cleanUsername.length >= 3) tokens.add(cleanUsername.take(3))
        if (cleanUsername.length >= 4) tokens.add(cleanUsername.take(4))
        
        // Add parts of username split by underscores or dots
        cleanUsername.split("[_.]".toRegex()).forEach { part ->
            if (part.length >= 2) tokens.add(part)
        }
        
        // Add display name tokens if different from username
        displayName?.let { name ->
            if (name.lowercase() != cleanUsername) {
                val cleanName = name.lowercase().trim()
                tokens.add(cleanName)
                cleanName.split(" ").forEach { word ->
                    if (word.length >= 2) tokens.add(word)
                }
            }
        }
        
        return tokens.toList()
    }
} 