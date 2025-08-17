package com.example.liftrix.data.repository

import com.example.liftrix.core.error.FirebaseErrorMapper
import com.example.liftrix.data.mapper.UserMapper
import com.example.liftrix.data.remote.dto.UserDto
import com.example.liftrix.domain.model.User
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.AuthRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : AuthRepository {

    override val currentUser: Flow<User?> = callbackFlow {
        var hasEmitted = false
        
        val authStateListener = FirebaseAuth.AuthStateListener { auth ->
            try {
                val firebaseUser = auth.currentUser
                val user = firebaseUser?.let { fbUser ->
                    // Add validation before creating User object
                    if (!fbUser.isAnonymous && fbUser.email.isNullOrBlank()) {
                        Timber.w("Non-anonymous Firebase user has no email: ${fbUser.uid}. Skipping user creation.")
                        null
                    } else {
                        UserMapper.fromFirebaseUser(fbUser)
                    }
                }
                trySend(user)
                hasEmitted = true
                Timber.d("Auth state emitted: ${if (user != null) "User(${user.uid})" else "null"}")
            } catch (exception: Exception) {
                Timber.e(exception, "Error creating User from Firebase user in auth state listener")
                // Send null on error to prevent infinite loading, but only if we haven't emitted yet
                if (!hasEmitted) {
                    trySend(null)
                    hasEmitted = true
                }
            }
        }
        
        firebaseAuth.addAuthStateListener(authStateListener)
        
        // Add timeout mechanism - emit null after 15 seconds if no auth state received (increased for cold starts)
        val timeoutJob = launch {
            delay(15_000) // 15 second timeout - increased to handle cold starts better
            if (!hasEmitted) {
                Timber.w("Auth state timeout reached - emitting null to prevent infinite loading")
                trySend(null)
                hasEmitted = true
            }
        }
        
        awaitClose {
            timeoutJob.cancel()
            firebaseAuth.removeAuthStateListener(authStateListener)
        }
    }

    override suspend fun signInWithEmail(email: String, password: String): LiftrixResult<User> {
        return liftrixCatching(
            errorMapper = { throwable -> FirebaseErrorMapper.handleFirebaseError(throwable) }
        ) {
            val authResult = firebaseAuth.signInWithEmailAndPassword(email, password).await()
            val firebaseUser = authResult.user
                ?: throw RuntimeException("Sign in failed: User is null")
            
            val user = UserMapper.fromFirebaseUser(firebaseUser)
            
            // Check if user profile exists in Firestore
            val profileExists = checkUserProfileExists(user.uid)
            
            if (!profileExists) {
                // Create profile if it doesn't exist (handles users created before fix)
                Timber.w("User profile not found, creating one for uid: ${user.uid}")
                createUserProfile(user).fold(
                    onSuccess = { Timber.d("Successfully created missing user profile") },
                    onFailure = { error -> Timber.e("Failed to create missing user profile: $error") }
                )
            } else {
                // Update last sign in time in Firestore
                updateLastSignInTime(user.uid)
            }
            
            user
        }
    }

    override suspend fun signUpWithEmail(email: String, password: String, displayName: String): LiftrixResult<User> {
        return liftrixCatching(
            errorMapper = { throwable -> FirebaseErrorMapper.handleFirebaseError(throwable) }
        ) {
            val authResult = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
            val firebaseUser = authResult.user
                ?: throw RuntimeException("Sign up failed: User is null")

            // Update display name
            val profileUpdates = UserProfileChangeRequest.Builder()
                .setDisplayName(displayName)
                .build()
            firebaseUser.updateProfile(profileUpdates).await()

            val user = UserMapper.fromFirebaseUser(firebaseUser).copy(displayName = displayName)
            
            // Create user profile in Firestore with retry logic
            var retries = 3
            var profileCreated = false
            var lastError: Exception? = null
            
            while (retries > 0 && !profileCreated) {
                val result = createUserProfile(user)
                result.fold(
                    onSuccess = {
                        profileCreated = true
                    },
                    onFailure = { error ->
                        lastError = Exception(error.toString())
                        retries--
                        if (retries > 0) {
                            // Wait before retrying
                            delay(500)
                            Timber.w("Retrying user profile creation, attempts remaining: $retries")
                        }
                    }
                )
            }
            
            if (!profileCreated) {
                // If profile creation failed after retries, log the error but still return the user
                // The profile will be created on next sign-in attempt
                Timber.e(lastError, "Failed to create user profile after 3 attempts, but auth account exists")
            }
            
            user
        }
    }

    override suspend fun signInWithGoogle(idToken: String): LiftrixResult<User> {
        return liftrixCatching(
            errorMapper = { throwable -> FirebaseErrorMapper.handleFirebaseError(throwable) }
        ) {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = firebaseAuth.signInWithCredential(credential).await()
            val firebaseUser = authResult.user
                ?: throw RuntimeException("Google sign in failed: User is null")

            val user = UserMapper.fromFirebaseUser(firebaseUser)
            
            // Create or update user profile in Firestore
            if (authResult.additionalUserInfo?.isNewUser == true) {
                createUserProfile(user).getOrThrow()
            } else {
                updateLastSignInTime(user.uid)
            }
            
            user
        }
    }

    override suspend fun signInAnonymously(): LiftrixResult<User> {
        return liftrixCatching(
            errorMapper = { throwable -> FirebaseErrorMapper.handleFirebaseError(throwable) }
        ) {
            val authResult = firebaseAuth.signInAnonymously().await()
            val firebaseUser = authResult.user
                ?: throw RuntimeException("Anonymous sign in failed: User is null")

            val user = UserMapper.fromFirebaseUser(firebaseUser)
            
            // Create anonymous user profile in Firestore
            createUserProfile(user).getOrThrow()
            
            user
        }
    }

    override suspend fun signOut(): LiftrixResult<Unit> {
        return liftrixCatching(
            errorMapper = { throwable -> FirebaseErrorMapper.handleFirebaseError(throwable) }
        ) {
            firebaseAuth.signOut()
        }
    }

    override suspend fun sendPasswordResetEmail(email: String): LiftrixResult<Unit> {
        return liftrixCatching(
            errorMapper = { throwable -> FirebaseErrorMapper.handleFirebaseError(throwable) }
        ) {
            firebaseAuth.sendPasswordResetEmail(email).await()
            Timber.d("Password reset email sent to: $email")
        }
    }

    override suspend fun getCurrentUser(): User? {
        return firebaseAuth.currentUser?.let { fbUser ->
            try {
                // Add validation before creating User object
                if (!fbUser.isAnonymous && fbUser.email.isNullOrBlank()) {
                    Timber.w("Non-anonymous Firebase user has no email: ${fbUser.uid}. Returning null.")
                    null
                } else {
                    UserMapper.fromFirebaseUser(fbUser)
                }
            } catch (exception: Exception) {
                Timber.e(exception, "Error creating User from Firebase user in getCurrentUser")
                null
            }
        }
    }

    override suspend fun getCurrentUserId(): String? {
        return firebaseAuth.currentUser?.uid
    }
    
    /**
     * Ensures the current user has a valid, non-expired authentication token.
     * This is critical for Firebase Storage operations which don't auto-refresh tokens.
     * 
     * @param forceRefresh If true, forces a token refresh even if current token hasn't expired
     * @return The current authenticated user's ID if successful, null otherwise
     */
    suspend fun ensureValidAuthToken(forceRefresh: Boolean = false): String? {
        return try {
            val currentUser = firebaseAuth.currentUser ?: return null
            
            // Force token refresh to ensure it's valid for Storage operations
            // This is crucial for preventing 403 errors in long-running sessions
            val tokenResult = currentUser.getIdToken(forceRefresh).await()
            
            if (tokenResult?.token.isNullOrBlank()) {
                Timber.w("Failed to get valid auth token for user ${currentUser.uid}")
                return null
            }
            
            Timber.d("Auth token refreshed for user ${currentUser.uid}")
            currentUser.uid
        } catch (e: Exception) {
            Timber.e(e, "Error refreshing auth token")
            null
        }
    }

    override suspend fun createUserProfile(user: User): LiftrixResult<Unit> {
        return liftrixCatching(
            errorMapper = { throwable -> FirebaseErrorMapper.handleFirebaseError(throwable) }
        ) {
            val userDto = UserMapper.toUserDto(user)
            
            // Convert userDto to a map and ensure it has userId field for security rules
            val userMap = hashMapOf(
                "userId" to user.uid,  // Add userId field that security rules expect
                "uid" to user.uid,     // Keep uid for backward compatibility
                "email" to userDto.email,
                "display_name" to userDto.displayName,
                "photo_url" to userDto.photoUrl,
                "is_anonymous" to userDto.isAnonymous,
                "subscription_tier" to userDto.subscriptionTier,
                "subscription_status" to userDto.subscriptionStatus,
                "subscription_expires_at" to userDto.subscriptionExpiresAt,
                "premium_features_enabled" to userDto.premiumFeaturesEnabled,
                "onboarding_completed" to userDto.onboardingCompleted,
                "profile_version" to userDto.profileVersion,
                "created_at" to userDto.createdAt,
                "last_sign_in_at" to userDto.lastSignInAt,
                "updated_at" to userDto.updatedAt
            )
            
            // Use a batch write to ensure atomicity
            val batch = firestore.batch()
            
            val userRef = firestore.collection("users").document(user.uid)
            batch.set(userRef, userMap)
            
            // Create initial user settings
            val settingsRef = firestore.collection("user_settings").document(user.uid)
            val initialSettings = mapOf(
                "userId" to user.uid,
                "notification_enabled" to true,
                "workout_reminders" to true,
                "pr_notifications" to true,
                "theme" to "system",
                "units" to "metric", // metric or imperial
                "rest_timer_enabled" to true,
                "rest_timer_default_seconds" to 90,
                "auto_start_rest_timer" to true,
                "created_at" to com.google.firebase.Timestamp.now(),
                "updated_at" to com.google.firebase.Timestamp.now()
            )
            batch.set(settingsRef, initialSettings)
            
            // Create initial subscription document (free tier)
            val subscriptionRef = firestore.collection("subscriptions").document()
            val initialSubscription = mapOf(
                "user_id" to user.uid,
                "tier" to "free",
                "status" to "active",
                "provider" to "manual",
                "started_at" to com.google.firebase.Timestamp.now(),
                "auto_renew" to false,
                "features" to emptyList<String>(),
                "metadata" to mapOf("source" to "registration"),
                "claims_updated" to false,
                "created_at" to com.google.firebase.Timestamp.now(),
                "version" to 1L
            )
            batch.set(subscriptionRef, initialSubscription)
            
            // Commit the batch
            batch.commit().await()
            
            Timber.d("User profile created successfully for uid: ${user.uid}")
        }
    }

    override suspend fun getUserProfile(uid: String): LiftrixResult<User?> {
        return liftrixCatching(
            errorMapper = { throwable -> FirebaseErrorMapper.handleFirebaseError(throwable) }
        ) {
            val document = firestore.collection("users")
                .document(uid)
                .get()
                .await()
            
            if (document.exists()) {
                val userDto = document.toObject(UserDto::class.java)
                userDto?.let { UserMapper.fromUserDto(it) }
            } else {
                null
            }
        }
    }

    private suspend fun updateLastSignInTime(uid: String) {
        try {
            firestore.collection("users")
                .document(uid)
                .set(
                    mapOf("last_sign_in_at" to com.google.firebase.Timestamp.now()),
                    com.google.firebase.firestore.SetOptions.merge()
                )
                .await()
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to update last sign in time")
        }
    }
    
    private suspend fun checkUserProfileExists(uid: String): Boolean {
        return try {
            val document = firestore.collection("users")
                .document(uid)
                .get()
                .await()
            document.exists()
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to check if user profile exists")
            false
        }
    }
} 