package com.example.liftrix.data.repository

import com.example.liftrix.core.error.FirebaseErrorMapper
import com.example.liftrix.core.identity.UserId
import com.example.liftrix.data.mapper.UserMapper
import com.example.liftrix.data.remote.dto.UserDto
import com.example.liftrix.domain.model.User
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.AuthRepository
import com.google.firebase.auth.EmailAuthProvider
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
import kotlin.math.pow

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val syncCoordinator: com.example.liftrix.sync.SyncCoordinator
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
                    onSuccess = {
                        Timber.i("User profile created successfully for uid: ${user.uid}")
                    },
                    onFailure = { error ->
                        Timber.e("Failed to create missing user profile: $error")
                    }
                )
            } else {
                // Update last sign in time in Firestore
                updateLastSignInTime(user.uid)
            }
            
            // Trigger bidirectional sync on login to fetch remote workouts
            triggerLoginSync(user.uid)
            
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
            
            // Trigger bidirectional sync on signup to initialize user data
            triggerLoginSync(user.uid)
            
            user
        }
    }

    override suspend fun signInWithGoogle(idToken: String): LiftrixResult<User> {
        // SEAMLESS FIRST-TIME FIX: Separate Firebase Auth from profile operations
        return try {
            // ONBOARDING FIX: Clear any potential stale auth state from guest session
            val wasAnonymous = firebaseAuth.currentUser?.isAnonymous == true
            if (wasAnonymous) {
                Timber.d("Converting anonymous user to Google authenticated user")
            }
            
            // Step 1: Authenticate with Firebase Auth (this should never fail due to profile issues)
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = firebaseAuth.signInWithCredential(credential).await()
            val firebaseUser = authResult.user
                ?: return LiftrixResult.failure(
                    LiftrixError.AuthenticationError(
                        errorMessage = "Google sign in failed: User is null",
                        errorCode = "NULL_USER",
                        analyticsContext = mapOf("operation" to "GOOGLE_SIGNIN")
                    )
                )

            val user = UserMapper.fromFirebaseUser(firebaseUser)
            Timber.i("[GMAIL-AUTH] ✅ Google authentication successful for user: ${user.uid}")
            Timber.d("[GMAIL-AUTH]   - Email: ${user.email}")
            Timber.d("[GMAIL-AUTH]   - Display Name: ${user.displayName}")
            Timber.d("[GMAIL-AUTH]   - Is New User: ${authResult.additionalUserInfo?.isNewUser == true}")

            // ONBOARDING FIX: Ensure Firestore auth state synchronization
            Timber.d("Verifying Firestore auth state synchronization after Google sign-in")
            val authSyncResult = ensureFirestoreAuthSynchronization(user.uid)
            if (!authSyncResult) {
                Timber.w("Failed to synchronize Firestore auth state for user ${user.uid}, will retry in background")
            }

            // Step 2: Handle profile creation/update gracefully (non-blocking)
            handleProfileOperationsGracefully(user)
            
            // Step 3: Always trigger sync regardless of profile creation status
            Timber.d("[GMAIL-AUTH] 🔄 Triggering login sync for user: ${user.uid}")
            triggerLoginSync(user.uid)
            
            // Step 4: Always return successful authentication
            LiftrixResult.success(user)
            
        } catch (authException: Exception) {
            // Only fail for actual Firebase Auth errors, not profile creation errors
            Timber.e(authException, "Google authentication failed at Firebase Auth level")
            LiftrixResult.failure(FirebaseErrorMapper.handleFirebaseError(authException))
        }
    }

    /**
     * Handles profile creation and updates gracefully without blocking authentication.
     * This ensures that Firestore permission errors don't prevent successful login.
     */
    private suspend fun handleProfileOperationsGracefully(user: User) {
        try {
            // Check if profile actually exists rather than relying on isNewUser flag
            val profileExists = checkUserProfileExists(user.uid)
            
            if (!profileExists) {
                Timber.i("[GMAIL-AUTH] 🔨 Creating profile for new Google user: ${user.uid}")
                Timber.d("[GMAIL-AUTH]   - Will create user profile in Firestore")
                Timber.d("[GMAIL-AUTH]   - Will create social profile for discoverability")
                // Try to create profile, but don't fail authentication if it fails
                createUserProfile(user).fold(
                    onSuccess = {
                        Timber.i("[GMAIL-AUTH] ✅ Profile created successfully for Google user: ${user.uid}")
                        Timber.d("[GMAIL-AUTH]   - User profile saved to Firestore users collection")
                        Timber.d("[GMAIL-AUTH]   - User should now be searchable and discoverable")
                    },
                    onFailure = { error ->
                        Timber.w("[GMAIL-AUTH] ⚠️ Profile creation failed for Google user ${user.uid}: $error")
                        Timber.w("[GMAIL-AUTH]   - User may not appear in search until profile is created")
                        Timber.w("[GMAIL-AUTH]   - Will retry profile creation in background")
                        // Schedule background profile creation retry
                        scheduleBackgroundProfileCreation(user)
                    }
                )
            } else {
                Timber.d("[GMAIL-AUTH] ✅ Profile exists for Google user: ${user.uid}")
                Timber.d("[GMAIL-AUTH]   - User should already be discoverable in search")
                Timber.d("[GMAIL-AUTH]   - Updating last sign-in time")
                // Update last sign-in time, but don't fail if this fails either
                try {
                    updateLastSignInTime(user.uid)
                } catch (updateError: Exception) {
                    Timber.w(updateError, "Failed to update last sign-in time for ${user.uid}, continuing with authentication")
                }
            }
        } catch (profileError: Exception) {
            Timber.w(profileError, "Profile operations failed for Google user ${user.uid}, but authentication will still succeed")
            // Schedule background profile creation as fallback
            scheduleBackgroundProfileCreation(user)
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
            
            // Trigger bidirectional sync on anonymous sign-in
            triggerLoginSync(user.uid)
            
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

    override suspend fun getCurrentUserId(): UserId? {
        return firebaseAuth.currentUser?.uid?.let { UserId(it) }
    }

    override fun observeAuthState(): Flow<UserId?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            val userId = auth.currentUser?.uid?.let { UserId(it) }
            trySend(userId)
        }
        firebaseAuth.addAuthStateListener(listener)
        awaitClose { firebaseAuth.removeAuthStateListener(listener) }
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
            
            currentUser.uid
        } catch (e: Exception) {
            Timber.e(e, "Error refreshing auth token")
            null
        }
    }

    override suspend fun createUserProfile(user: User): LiftrixResult<Unit> {
        return liftrixCatching(
            errorMapper = { throwable -> 
                // ONBOARDING DEBUG: Enhanced error logging for profile creation failures
                Timber.e(throwable, "Profile creation failed for user ${user.uid}")
                when {
                    throwable.message?.contains("PERMISSION_DENIED") == true -> {
                        Timber.e("PERMISSION_DENIED: Firestore security rules rejected profile creation")
                        Timber.e("User ID: ${user.uid}")
                        Timber.e("Auth UID: ${firebaseAuth.currentUser?.uid}")
                        Timber.e("Are they equal? ${user.uid == firebaseAuth.currentUser?.uid}")
                    }
                }
                FirebaseErrorMapper.handleFirebaseError(throwable) 
            }
        ) {
            Timber.d("Creating user profile for: ${user.uid}")
            
            // ONBOARDING FIX: Ensure Firestore client auth state is properly synchronized
            val authValidationResult = validateFirestoreAuthState(user.uid)
            if (!authValidationResult) {
                throw IllegalStateException("Firestore auth state validation failed for user ${user.uid}")
            }
            
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
            Timber.d("User map created with ${userMap.size} fields for user: ${user.uid}")
            
            // Use a batch write to ensure atomicity
            val batch = firestore.batch()
            
            val userRef = firestore.collection("users").document(user.uid)
            batch.set(userRef, userMap)
            Timber.d("Added user document to batch - path: users/${user.uid}")
            
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
            Timber.d("Added user_settings document to batch for: ${user.uid}")
            
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
            Timber.d("Added subscriptions document to batch for: ${user.uid}")
            
            // Commit the batch
            Timber.d("[PROFILE-CREATE] 📦 Committing batch write for profile creation: ${user.uid}")
            batch.commit().await()
            Timber.i("[PROFILE-CREATE] ✅ Batch write successful for profile creation: ${user.uid}")
            Timber.i("[PROFILE-CREATE]   - User profile is now available for search and discovery")
            Timber.i("[PROFILE-CREATE]   - Profile should sync to social_profiles collection via workers")
            
        }
    }

    override suspend fun getUserProfile(uid: UserId): LiftrixResult<User?> {
        return liftrixCatching(
            errorMapper = { throwable -> FirebaseErrorMapper.handleFirebaseError(throwable) }
        ) {
            val document = firestore.collection("users")
                .document(uid.value)
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
            // CRITICAL FIX: Include userId field required by Firestore security rules
            val updateMap = mapOf(
                "userId" to uid,  // Required by security rules for user document writes
                "last_sign_in_at" to com.google.firebase.Timestamp.now(),
                "updated_at" to com.google.firebase.Timestamp.now()
            )
            
            
            firestore.collection("users")
                .document(uid)
                .set(updateMap, com.google.firebase.firestore.SetOptions.merge())
                .await()
                
        } catch (exception: Exception) {
            Timber.e(exception, "CRITICAL: Failed to update last sign-in time for user $uid")
            
            // Enhanced error logging for debugging Firestore permission issues
            when {
                exception.message?.contains("PERMISSION_DENIED") == true -> {
                    Timber.e("Firestore PERMISSION_DENIED: Check security rules for users/$uid write permissions")
                    Timber.e("Attempted to write fields: userId, last_sign_in_at, updated_at")
                }
                exception.message?.contains("NOT_FOUND") == true -> {
                    Timber.e("User document not found: users/$uid - user may need to be created first")
                }
                else -> {
                    Timber.e("Unexpected Firestore error: ${exception.javaClass.simpleName} - ${exception.message}")
                }
            }
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

    // Account Management Methods Implementation

    override suspend fun reauthenticate(password: String): LiftrixResult<Unit> {
        return liftrixCatching(
            errorMapper = { throwable ->
                when {
                    throwable.message?.contains("INVALID_PASSWORD") == true ||
                    throwable.message?.contains("wrong-password") == true -> {
                        LiftrixError.AuthenticationError(
                            errorMessage = "Current password is incorrect",
                            authProvider = "email",
                            errorCode = "INVALID_PASSWORD",
                            analyticsContext = mapOf("operation" to "REAUTHENTICATE")
                        )
                    }
                    throwable.message?.contains("user-not-found") == true -> {
                        LiftrixError.AuthenticationError(
                            errorMessage = "User not found",
                            authProvider = "email",
                            errorCode = "USER_NOT_FOUND",
                            analyticsContext = mapOf("operation" to "REAUTHENTICATE")
                        )
                    }
                    throwable.message?.contains("too-many-requests") == true -> {
                        LiftrixError.AuthenticationError(
                            errorMessage = "Too many failed attempts. Please try again later.",
                            authProvider = "email",
                            errorCode = "TOO_MANY_REQUESTS",
                            isRecoverable = true,
                            retryAfter = 300_000L, // 5 minutes
                            analyticsContext = mapOf("operation" to "REAUTHENTICATE")
                        )
                    }
                    else -> FirebaseErrorMapper.handleFirebaseError(throwable)
                }
            }
        ) {
            val user = firebaseAuth.currentUser ?: throw LiftrixError.AuthenticationError(
                errorMessage = "No authenticated user found",
                errorCode = "NO_USER",
                analyticsContext = mapOf("operation" to "REAUTHENTICATE")
            )
            
            val email = user.email ?: throw LiftrixError.AuthenticationError(
                errorMessage = "User email not available for reauthentication",
                errorCode = "NO_EMAIL",
                analyticsContext = mapOf("operation" to "REAUTHENTICATE")
            )
            
            val credential = EmailAuthProvider.getCredential(email, password)
            user.reauthenticate(credential).await()
            
        }
    }

    override suspend fun updateEmail(newEmail: String): LiftrixResult<Unit> {
        return liftrixCatching(
            errorMapper = { throwable ->
                when {
                    throwable.message?.contains("email-already-in-use") == true -> {
                        LiftrixError.ValidationError(
                            field = "email",
                            violations = listOf("Email address is already in use by another account"),
                            analyticsContext = mapOf("operation" to "UPDATE_EMAIL")
                        )
                    }
                    throwable.message?.contains("invalid-email") == true -> {
                        LiftrixError.ValidationError(
                            field = "email",
                            violations = listOf("Email address format is invalid"),
                            analyticsContext = mapOf("operation" to "UPDATE_EMAIL")
                        )
                    }
                    throwable.message?.contains("requires-recent-login") == true -> {
                        LiftrixError.AuthenticationError(
                            errorMessage = "This operation requires recent authentication. Please reauthenticate first.",
                            errorCode = "REQUIRES_RECENT_LOGIN",
                            analyticsContext = mapOf("operation" to "UPDATE_EMAIL")
                        )
                    }
                    else -> FirebaseErrorMapper.handleFirebaseError(throwable)
                }
            }
        ) {
            val user = firebaseAuth.currentUser ?: throw LiftrixError.AuthenticationError(
                errorMessage = "No authenticated user found",
                errorCode = "NO_USER",
                analyticsContext = mapOf("operation" to "UPDATE_EMAIL")
            )
            
            val oldEmail = user.email
            
            // Update email in Firebase Auth
            user.updateEmail(newEmail).await()
            
            // Send verification email to new address
            user.sendEmailVerification().await()
            
            // Update email in Firestore user profile
            try {
                firestore.collection("users")
                    .document(user.uid)
                    .set(
                        mapOf(
                            "email" to newEmail,
                            "updated_at" to com.google.firebase.Timestamp.now()
                        ),
                        com.google.firebase.firestore.SetOptions.merge()
                    )
                    .await()
            } catch (firestoreError: Exception) {
                Timber.w(firestoreError, "Failed to update email in Firestore, but Firebase Auth was updated")
                // Don't fail the entire operation if Firestore update fails
            }
            
        }
    }

    override suspend fun updatePassword(currentPassword: String, newPassword: String): LiftrixResult<Unit> {
        return liftrixCatching(
            errorMapper = { throwable ->
                when {
                    throwable.message?.contains("INVALID_PASSWORD") == true ||
                    throwable.message?.contains("wrong-password") == true -> {
                        LiftrixError.AuthenticationError(
                            errorMessage = "Current password is incorrect",
                            authProvider = "email",
                            errorCode = "INVALID_PASSWORD",
                            analyticsContext = mapOf("operation" to "UPDATE_PASSWORD")
                        )
                    }
                    throwable.message?.contains("weak-password") == true -> {
                        LiftrixError.ValidationError(
                            field = "newPassword",
                            violations = listOf("Password is too weak. Must be at least 6 characters."),
                            analyticsContext = mapOf("operation" to "UPDATE_PASSWORD")
                        )
                    }
                    throwable.message?.contains("requires-recent-login") == true -> {
                        LiftrixError.AuthenticationError(
                            errorMessage = "This operation requires recent authentication. Please reauthenticate first.",
                            errorCode = "REQUIRES_RECENT_LOGIN",
                            analyticsContext = mapOf("operation" to "UPDATE_PASSWORD")
                        )
                    }
                    else -> FirebaseErrorMapper.handleFirebaseError(throwable)
                }
            }
        ) {
            val user = firebaseAuth.currentUser ?: throw LiftrixError.AuthenticationError(
                errorMessage = "No authenticated user found",
                errorCode = "NO_USER",
                analyticsContext = mapOf("operation" to "UPDATE_PASSWORD")
            )
            
            val email = user.email ?: throw LiftrixError.AuthenticationError(
                errorMessage = "User email not available for password update",
                errorCode = "NO_EMAIL",
                analyticsContext = mapOf("operation" to "UPDATE_PASSWORD")
            )
            
            // Validate that new password is different from current
            if (currentPassword == newPassword) {
                throw LiftrixError.ValidationError(
                    field = "newPassword",
                    violations = listOf("New password must be different from current password"),
                    analyticsContext = mapOf("operation" to "UPDATE_PASSWORD")
                )
            }
            
            // First reauthenticate with current password
            val credential = EmailAuthProvider.getCredential(email, currentPassword)
            user.reauthenticate(credential).await()
            
            // Update to new password
            user.updatePassword(newPassword).await()
            
            // Update password change timestamp in Firestore
            try {
                firestore.collection("users")
                    .document(user.uid)
                    .set(
                        mapOf(
                            "last_password_change" to com.google.firebase.Timestamp.now(),
                            "updated_at" to com.google.firebase.Timestamp.now()
                        ),
                        com.google.firebase.firestore.SetOptions.merge()
                    )
                    .await()
            } catch (firestoreError: Exception) {
                Timber.w(firestoreError, "Failed to update password timestamp in Firestore")
                // Don't fail the entire operation if Firestore update fails
            }
            
        }
    }

    override suspend fun deleteAccount(): LiftrixResult<Unit> {
        return liftrixCatching(
            errorMapper = { throwable ->
                when {
                    throwable.message?.contains("requires-recent-login") == true -> {
                        LiftrixError.AuthenticationError(
                            errorMessage = "Account deletion requires recent authentication. Please reauthenticate first.",
                            errorCode = "REQUIRES_RECENT_LOGIN",
                            analyticsContext = mapOf("operation" to "DELETE_ACCOUNT")
                        )
                    }
                    else -> FirebaseErrorMapper.handleFirebaseError(throwable)
                }
            }
        ) {
            val user = firebaseAuth.currentUser ?: throw LiftrixError.AuthenticationError(
                errorMessage = "No authenticated user found",
                errorCode = "NO_USER",
                analyticsContext = mapOf("operation" to "DELETE_ACCOUNT")
            )
            
            val userId = user.uid
            
            // Create a batch to delete all user data atomically
            val batch = firestore.batch()
            
            // List of collections to delete user data from
            val collectionsToClean = listOf(
                "users",
                "user_settings", 
                "subscriptions",
                "workouts",
                "workout_templates",
                "custom_exercises",
                "user_profiles",
                "analytics_cache",
                "social_profiles",
                "follow_relationships",
                "workout_posts",
                "notifications"
            )
            
            // Delete user documents from all collections
            for (collection in collectionsToClean) {
                try {
                    val documents = firestore.collection(collection)
                        .whereEqualTo("userId", userId)
                        .get()
                        .await()
                    
                    for (document in documents) {
                        batch.delete(document.reference)
                    }
                    
                    // Also check for user_id field (different naming convention)
                    val documentsAlt = firestore.collection(collection)
                        .whereEqualTo("user_id", userId)
                        .get()
                        .await()
                    
                    for (document in documentsAlt) {
                        batch.delete(document.reference)
                    }
                } catch (e: Exception) {
                    Timber.w(e, "Failed to query collection $collection for user deletion")
                    // Continue with other collections
                }
            }
            
            // Also delete the main user document
            val userRef = firestore.collection("users").document(userId)
            batch.delete(userRef)
            
            try {
                // Commit the batch delete
                batch.commit().await()
            } catch (firestoreError: Exception) {
                Timber.e(firestoreError, "Failed to delete Firestore data, but proceeding with auth account deletion")
                // Don't fail the entire operation if Firestore cleanup fails
            }
            
            // Finally, delete the Firebase Auth account
            user.delete().await()
            
        }
    }
    
    /**
     * Schedules background profile creation with retry logic for users where initial profile creation failed.
     * This ensures that authentication never fails due to profile creation issues, while still ensuring
     * the user profile gets created eventually.
     */
    private fun scheduleBackgroundProfileCreation(user: User) {
        try {
            Timber.i("Scheduling background profile creation for user: ${user.uid}")
            
            // Use coroutine scope to avoid blocking the authentication process
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob()).launch {
                // Add a small delay to avoid immediate retry of a potentially failing operation
                kotlinx.coroutines.delay(2000) // 2 second delay
                
                var retryAttempts = 0
                val maxRetries = 3
                var lastError: Exception? = null
                
                while (retryAttempts < maxRetries) {
                    try {
                        Timber.d("Background profile creation attempt ${retryAttempts + 1}/$maxRetries for user: ${user.uid}")
                        
                        // Check if profile was created by another process in the meantime
                        val profileExists = checkUserProfileExists(user.uid)
                        if (profileExists) {
                            Timber.i("Profile already exists for user ${user.uid}, background creation no longer needed")
                            return@launch
                        }
                        
                        // Attempt to create the profile
                        val result = createUserProfile(user)
                        result.fold(
                            onSuccess = {
                                Timber.i("Background profile creation successful for user: ${user.uid}")
                                return@launch // Success, exit the retry loop
                            },
                            onFailure = { error ->
                                lastError = Exception(error.toString())
                                retryAttempts++
                                Timber.w("Background profile creation attempt $retryAttempts failed for user ${user.uid}: $error")
                                
                                if (retryAttempts < maxRetries) {
                                    // Exponential backoff: 5s, 15s, 45s
                                    val backoffDelay = (5000 * 3.0.pow((retryAttempts - 1).toDouble())).toLong()
                                    Timber.d("Waiting ${backoffDelay}ms before next retry for user: ${user.uid}")
                                    kotlinx.coroutines.delay(backoffDelay)
                                }
                            }
                        )
                    } catch (e: Exception) {
                        lastError = e
                        retryAttempts++
                        Timber.w(e, "Exception during background profile creation attempt $retryAttempts for user: ${user.uid}")
                        
                        if (retryAttempts < maxRetries) {
                            val backoffDelay = (5000 * 3.0.pow((retryAttempts - 1).toDouble())).toLong()
                            kotlinx.coroutines.delay(backoffDelay)
                        }
                    }
                }
                
                // All retry attempts failed
                if (retryAttempts >= maxRetries) {
                    Timber.e(lastError, "All background profile creation attempts failed for user: ${user.uid}. Profile creation will be attempted on next login.")
                    // The user is still authenticated and can use the app, but their profile might be incomplete
                    // This will be resolved on next login attempt or by periodic sync workers
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to schedule background profile creation for user: ${user.uid}")
        }
    }

    /**
     * This is the critical fix that ensures workouts are never lost when switching devices.
     */
    private fun triggerLoginSync(userId: String) {
        try {
            
            // Use coroutine scope to avoid blocking the login process
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob()).launch {
                try {
                    // Schedule periodic sync for this user (if not already scheduled)
                    syncCoordinator.schedulePeriodicSync(userId)
                    
                    // Trigger immediate bidirectional sync to fetch remote data
                    val syncResult = syncCoordinator.triggerImmediateSync(userId)
                    
                    syncResult.fold(
                        onSuccess = {
                        },
                        onFailure = { error ->
                            Timber.w("[LOGIN-SYNC] ⚠️ Login sync failed, but user can still proceed: $error")
                            Timber.w("[LOGIN-SYNC]   - User will need to manually refresh or sync will retry in background")
                        }
                    )
                } catch (e: Exception) {
                    Timber.e(e, "[LOGIN-SYNC] ❌ Error during login sync for user: $userId")
                    // Don't fail login if sync fails - it will retry in background
                }
            }
            
        } catch (e: Exception) {
            Timber.e(e, "[LOGIN-SYNC] ❌ Failed to initiate login sync for user: $userId")
            // Don't fail the login process if sync setup fails
        }
    }

    /**
     * Ensures that Firestore's auth state is properly synchronized with Firebase Auth.
     * This actively verifies that Firestore recognizes the authenticated user rather than using arbitrary delays.
     * 
     * @param userId The user ID to validate
     * @return true if auth synchronization is verified, false if it fails after retries
     */
    private suspend fun ensureFirestoreAuthSynchronization(userId: String): Boolean {
        return try {
            // Perform active synchronization check with exponential backoff
            var attempts = 0
            val maxAttempts = 5
            val baseDelayMs = 200L
            
            while (attempts < maxAttempts) {
                try {
                    // Validate auth state by attempting to get a fresh token
                    val currentUser = firebaseAuth.currentUser
                    if (currentUser?.uid != userId) {
                        Timber.w("Firebase Auth user mismatch on attempt ${attempts + 1}: expected $userId, got ${currentUser?.uid}")
                        attempts++
                        if (attempts < maxAttempts) {
                            val delayMs = baseDelayMs * (2.0.pow(attempts.toDouble())).toLong()
                            delay(delayMs)
                            continue
                        }
                        return false
                    }
                    
                    // Force token refresh to ensure Firestore has the latest auth state
                    val tokenResult = currentUser.getIdToken(true).await()
                    if (tokenResult?.token.isNullOrBlank()) {
                        Timber.w("Failed to get valid auth token on attempt ${attempts + 1}")
                        attempts++
                        if (attempts < maxAttempts) {
                            val delayMs = baseDelayMs * (2.0.pow(attempts.toDouble())).toLong()
                            delay(delayMs)
                            continue
                        }
                        return false
                    }
                    
                    // Perform a simple Firestore operation to verify auth state
                    try {
                        firestore.collection("users").document(userId).get().await()
                        // If we can perform a Firestore operation, auth is synchronized
                        Timber.d("Firestore auth state synchronized successfully for user $userId on attempt ${attempts + 1}")
                        return true
                    } catch (firestoreError: Exception) {
                        if (firestoreError.message?.contains("PERMISSION_DENIED") == true) {
                            // Firestore hasn't synchronized yet
                            Timber.d("Firestore auth not synchronized yet on attempt ${attempts + 1}: ${firestoreError.message}")
                            attempts++
                            if (attempts < maxAttempts) {
                                val delayMs = baseDelayMs * (2.0.pow(attempts.toDouble())).toLong()
                                delay(delayMs)
                                continue
                            }
                        } else {
                            // Some other error - document doesn't exist is OK, permission denied is not
                            Timber.d("Firestore auth check completed (document access verified) on attempt ${attempts + 1}")
                            return true
                        }
                    }
                    
                } catch (e: Exception) {
                    Timber.w(e, "Auth synchronization check failed on attempt ${attempts + 1}")
                    attempts++
                    if (attempts < maxAttempts) {
                        val delayMs = baseDelayMs * (2.0.pow(attempts.toDouble())).toLong()
                        delay(delayMs)
                    }
                }
            }
            
            Timber.w("Failed to verify Firestore auth synchronization after $maxAttempts attempts for user $userId")
            return false
            
        } catch (e: Exception) {
            Timber.e(e, "Exception during Firestore auth synchronization check for user $userId")
            return false
        }
    }

    /**
     * Validates that Firestore's auth state is ready for database operations.
     * This is a lightweight check that should be performed before critical operations like profile creation.
     * 
     * @param userId The user ID to validate
     * @return true if validation passes, false otherwise
     */
    private suspend fun validateFirestoreAuthState(userId: String): Boolean {
        return try {
            // Quick validation that Firebase Auth state is correct
            val currentAuth = firebaseAuth.currentUser
            if (currentAuth == null) {
                Timber.e("Firestore auth validation failed: Firebase Auth currentUser is null")
                return false
            }
            if (currentAuth.uid != userId) {
                Timber.e("Firestore auth validation failed: UID mismatch - Firebase Auth: ${currentAuth.uid}, Expected: $userId")
                return false
            }
            
            // Ensure we have a valid, non-expired token
            val tokenResult = currentAuth.getIdToken(false).await() // false = use cached token if valid
            if (tokenResult?.token.isNullOrBlank()) {
                Timber.e("Firestore auth validation failed: No valid auth token available")
                // Try to get a fresh token
                val freshToken = currentAuth.getIdToken(true).await()
                if (freshToken?.token.isNullOrBlank()) {
                    Timber.e("Firestore auth validation failed: Failed to refresh auth token")
                    return false
                }
            }
            
            Timber.d("Firestore auth state validation successful for user: $userId")
            return true
            
        } catch (e: Exception) {
            Timber.e(e, "Exception during Firestore auth state validation for user $userId")
            return false
        }
    }
}
