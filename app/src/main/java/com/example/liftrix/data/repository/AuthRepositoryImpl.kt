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
            
            // Update last sign in time in Firestore
            updateLastSignInTime(user.uid)
            
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
            
            // Create user profile in Firestore
            createUserProfile(user).getOrThrow()
            
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

    override suspend fun createUserProfile(user: User): LiftrixResult<Unit> {
        return liftrixCatching(
            errorMapper = { throwable -> FirebaseErrorMapper.handleFirebaseError(throwable) }
        ) {
            val userDto = UserMapper.toUserDto(user)
            
            // Use a batch write to ensure atomicity
            val batch = firestore.batch()
            
            val userRef = firestore.collection("users").document(user.uid)
            batch.set(userRef, userDto)
            
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
} 