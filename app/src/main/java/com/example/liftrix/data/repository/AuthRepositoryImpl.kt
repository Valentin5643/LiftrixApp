package com.example.liftrix.data.repository

import com.example.liftrix.data.mapper.UserMapper
import com.example.liftrix.data.remote.dto.UserDto
import com.example.liftrix.domain.model.User
import com.example.liftrix.domain.repository.AuthRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : AuthRepository {

    override val currentUser: Flow<User?> = callbackFlow {
        val authStateListener = FirebaseAuth.AuthStateListener { auth ->
            val firebaseUser = auth.currentUser
            val user = firebaseUser?.let { UserMapper.fromFirebaseUser(it) }
            trySend(user)
        }
        
        firebaseAuth.addAuthStateListener(authStateListener)
        
        awaitClose {
            firebaseAuth.removeAuthStateListener(authStateListener)
        }
    }

    override suspend fun signInWithEmail(email: String, password: String): Result<User> {
        return try {
            val authResult = firebaseAuth.signInWithEmailAndPassword(email, password).await()
            val firebaseUser = authResult.user
                ?: return Result.failure(Exception("Sign in failed: User is null"))
            
            val user = UserMapper.fromFirebaseUser(firebaseUser)
            
            // Update last sign in time in Firestore
            updateLastSignInTime(user.uid)
            
            Result.success(user)
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to sign in with email")
            Result.failure(exception)
        }
    }

    override suspend fun signUpWithEmail(email: String, password: String, displayName: String): Result<User> {
        return try {
            val authResult = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
            val firebaseUser = authResult.user
                ?: return Result.failure(Exception("Sign up failed: User is null"))

            // Update display name
            val profileUpdates = UserProfileChangeRequest.Builder()
                .setDisplayName(displayName)
                .build()
            firebaseUser.updateProfile(profileUpdates).await()

            val user = UserMapper.fromFirebaseUser(firebaseUser).copy(displayName = displayName)
            
            // Create user profile in Firestore
            createUserProfile(user)
            
            Result.success(user)
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to sign up with email")
            Result.failure(exception)
        }
    }

    override suspend fun signInWithGoogle(idToken: String): Result<User> {
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = firebaseAuth.signInWithCredential(credential).await()
            val firebaseUser = authResult.user
                ?: return Result.failure(Exception("Google sign in failed: User is null"))

            val user = UserMapper.fromFirebaseUser(firebaseUser)
            
            // Create or update user profile in Firestore
            if (authResult.additionalUserInfo?.isNewUser == true) {
                createUserProfile(user)
            } else {
                updateLastSignInTime(user.uid)
            }
            
            Result.success(user)
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to sign in with Google")
            Result.failure(exception)
        }
    }

    override suspend fun signInAnonymously(): Result<User> {
        return try {
            val authResult = firebaseAuth.signInAnonymously().await()
            val firebaseUser = authResult.user
                ?: return Result.failure(Exception("Anonymous sign in failed: User is null"))

            val user = UserMapper.fromFirebaseUser(firebaseUser)
            
            // Create anonymous user profile in Firestore
            createUserProfile(user)
            
            Result.success(user)
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to sign in anonymously")
            Result.failure(exception)
        }
    }

    override suspend fun signOut(): Result<Unit> {
        return try {
            firebaseAuth.signOut()
            Result.success(Unit)
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to sign out")
            Result.failure(exception)
        }
    }

    override suspend fun sendPasswordResetEmail(email: String): Result<Unit> {
        return try {
            firebaseAuth.sendPasswordResetEmail(email).await()
            Timber.d("Password reset email sent to: $email")
            Result.success(Unit)
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to send password reset email")
            Result.failure(exception)
        }
    }

    override suspend fun getCurrentUser(): User? {
        return firebaseAuth.currentUser?.let { UserMapper.fromFirebaseUser(it) }
    }

    override suspend fun getCurrentUserId(): String? {
        return firebaseAuth.currentUser?.uid
    }

    override suspend fun createUserProfile(user: User): Result<Unit> {
        return try {
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
            Result.success(Unit)
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to create user profile for uid: ${user.uid}")
            Result.failure(exception)
        }
    }

    override suspend fun getUserProfile(uid: String): Result<User?> {
        return try {
            val document = firestore.collection("users")
                .document(uid)
                .get()
                .await()
            
            if (document.exists()) {
                val userDto = document.toObject(UserDto::class.java)
                val user = userDto?.let { UserMapper.fromUserDto(it) }
                Result.success(user)
            } else {
                Result.success(null)
            }
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to get user profile")
            Result.failure(exception)
        }
    }

    private suspend fun updateLastSignInTime(uid: String) {
        try {
            firestore.collection("users")
                .document(uid)
                .update("last_sign_in_at", com.google.firebase.Timestamp.now())
                .await()
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to update last sign in time")
        }
    }
} 