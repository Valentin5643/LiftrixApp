package com.example.liftrix.domain.usecase.auth

import android.content.Context
import androidx.work.WorkManager
import com.example.liftrix.core.workmanager.WorkManagerProvider
import com.example.liftrix.domain.model.User
import com.example.liftrix.domain.model.UserAccount
import com.example.liftrix.domain.model.UserProfile
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.repository.ProfileRepository
import com.example.liftrix.domain.repository.SettingsRepository
import com.example.liftrix.domain.repository.UserAccountRepository
import com.example.liftrix.domain.repository.social.SocialProfileRepository
import com.example.liftrix.domain.service.AnalyticsService
import com.example.liftrix.domain.service.OnboardingDataStore
import com.example.liftrix.domain.usecase.profile.ProfileCommandUseCase
import com.example.liftrix.domain.usecase.social.SocialProfileCommandUseCase
import com.example.liftrix.sync.FollowRelationshipSyncWorker
import com.example.liftrix.sync.SyncCoordinator
import com.example.liftrix.sync.SyncManager
import com.example.liftrix.sync.UserPublicSyncWorker
import com.example.liftrix.ui.common.state.StateCleanupManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * Consolidated auth command use case for all authentication mutation operations.
 *
 * Consolidates:
 * - SignInWithEmailUseCase (173 lines)
 * - SignInWithGoogleUseCase (534 lines)
 * - SignUpWithEmailUseCase (181 lines)
 * - SignInAnonymouslyUseCase (13 lines)
 * - SignOutUseCase (12 lines)
 * - EnhancedSignOutUseCase (150+ lines)
 * - ForgotPasswordUseCase (27 lines)
 *
 * Total: ~1,090 lines consolidated into single command interface
 *
 * Architecture:
 * - Command operations only (mutations)
 * - LiftrixResult<T> pattern for all operations
 * - Room-First architecture (Room → SyncQueue → WorkManager → Firebase)
 * - Preserves all timing behaviors and profile creation sequences
 * - Maintains backward compatibility for existing users
 */
class AuthCommandUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val userAccountRepository: UserAccountRepository,
    private val profileRepository: ProfileRepository,
    private val socialProfileRepository: SocialProfileRepository,
    private val profileCommandUseCase: ProfileCommandUseCase,
    private val socialProfileCommandUseCase: SocialProfileCommandUseCase,
    private val syncCoordinator: SyncCoordinator,
    private val onboardingDataStore: OnboardingDataStore,
    private val analyticsService: AnalyticsService,
    private val settingsRepository: SettingsRepository,
    private val syncManager: SyncManager,
    private val stateCleanupManager: StateCleanupManager,
    @ApplicationContext private val context: Context
) {

    private val workManager: WorkManager
        get() = WorkManagerProvider.getInstance(context)

    // ============== SIGN IN WITH EMAIL ==============

    /**
     * Sign in with email and password.
     *
     * Replaces: SignInWithEmailUseCase
     *
     * Flow:
     * 1. Validate email/password
     * 2. Authenticate with Firebase
     * 3. Create/update UserAccount for backward compatibility
     * 4. Sync onboarding profile data
     * 5. Restore follow relationships
     * 6. Trigger user searchability sync
     *
     * @param email User email address
     * @param password User password
     * @return LiftrixResult containing authenticated user or error
     */
    suspend fun signInWithEmail(email: String, password: String): LiftrixResult<User> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.AuthenticationError(
                errorMessage = "Sign in failed: ${throwable.message}",
                errorCode = "SIGN_IN_EMAIL_FAILED",
                analyticsContext = mapOf(
                    "email" to email,
                    "operation" to "SIGN_IN_EMAIL"
                )
            )
        }
    ) {
        // Validation
        require(email.isNotBlank()) { "Email cannot be blank" }
        require(password.isNotBlank()) { "Password cannot be blank" }
        require(isValidEmail(email)) { "Invalid email format" }

        // Authenticate
        val signInResult = authRepository.signInWithEmail(email, password)
        val user = signInResult.fold(
            onSuccess = { it },
            onFailure = { throw it }
        )

        // Ensure UserAccount exists for backward compatibility
        handleUserAccountCreation(user, email)

        // Sync onboarding profile after successful login
        syncOnboardingProfileAfterLogin(user.uid)

        // Restore follow relationships
        restoreFollowRelationshipsAfterLogin(user.uid)

        user
    }

    // ============== SIGN IN WITH GOOGLE ==============

    /**
     * Sign in with Google ID token.
     *
     * Replaces: SignInWithGoogleUseCase
     *
     * Flow:
     * 1. Validate ID token
     * 2. Authenticate with Firebase
     * 3. Create/update UserAccount with generated username
     * 4. Create SocialProfile and UserProfile
     * 5. Transfer pending onboarding data
     * 6. Restore follow relationships
     * 7. Trigger searchability sync
     *
     * @param idToken Google ID token from authentication
     * @return LiftrixResult containing authenticated user or error
     */
    suspend fun signInWithGoogle(idToken: String): LiftrixResult<User> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.AuthenticationError(
                errorMessage = "Google sign in failed: ${throwable.message}",
                errorCode = "SIGN_IN_GOOGLE_FAILED",
                analyticsContext = mapOf("operation" to "SIGN_IN_GOOGLE")
            )
        }
    ) {
        require(idToken.isNotBlank()) { "Google ID token cannot be blank" }

        // Authenticate with Google
        val authResult = authRepository.signInWithGoogle(idToken)
        val user = authResult.fold(
            onSuccess = { it },
            onFailure = { throw it }
        )

        try {
            // Check if UserAccount exists
            val existingAccountResult = userAccountRepository.getAccountInfoSuspend(user.uid)
            val existingAccount = existingAccountResult.getOrNull()

            if (existingAccount == null) {
                // Create UserAccount for new Google user
                createGoogleUserAccount(user)
            } else {
                // Update existing account
                updateExistingGoogleAccount(user, existingAccount)
            }

            // Transfer pending onboarding data
            transferOnboardingDataAfterLogin(user.uid)

            // Restore follow relationships
            restoreFollowRelationshipsAfterLogin(user.uid)

        } catch (e: Exception) {
            Timber.e(e, "Error handling UserAccount for Google sign-in")
            // Don't fail sign-in if profile setup fails
        }

        user
    }

    // ============== SIGN UP WITH EMAIL ==============

    /**
     * Sign up with email, password, and username.
     *
     * Replaces: SignUpWithEmailUseCase
     *
     * Flow:
     * 1. Validate email/password/username
     * 2. Authenticate with Firebase
     * 3. Create UserAccount (Room-First)
     * 4. Create UserProfile (Room-First)
     * 5. Create SocialProfile (Room-First)
     * 6. Trigger immediate sync via SyncCoordinator
     *
     * Architecture: Follows Room-First pattern (Room → SyncQueue → WorkManager → Firebase)
     *
     * @param email User email address
     * @param password User password (min 6 characters)
     * @param username Unique username (3-20 chars, alphanumeric + underscore)
     * @return LiftrixResult containing authenticated user or error
     */
    suspend fun signUpWithEmail(
        email: String,
        password: String,
        username: String
    ): LiftrixResult<User> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.AuthenticationError(
                errorMessage = "Sign up failed: ${throwable.message}",
                errorCode = "SIGN_UP_EMAIL_FAILED",
                analyticsContext = mapOf(
                    "email" to email,
                    "username" to username,
                    "operation" to "SIGN_UP_EMAIL"
                )
            )
        }
    ) {
        // Validation
        require(email.isNotBlank()) { "Email cannot be blank" }
        require(password.isNotBlank()) { "Password cannot be blank" }
        require(username.isNotBlank()) { "Username cannot be blank" }
        require(isValidUsername(username)) { "Invalid username format" }
        require(isValidEmail(email)) { "Invalid email format" }
        require(isValidPassword(password)) { "Password must be at least 6 characters" }

        Timber.i("Starting signup for username: $username")

        // Sign up with Firebase
        val userResult = authRepository.signUpWithEmail(email, password, username)
        val user = userResult.fold(
            onSuccess = { it },
            onFailure = { throw it }
        )

        Timber.i("Authentication successful for user: ${user.uid}")

        // Create UserAccount (Room-First)
        val userAccount = UserAccount(
            userId = user.uid,
            email = email,
            username = username,
            emailVerified = false,
            displayName = username,
            lastPasswordChange = null,
            accountCreatedAt = LocalDateTime.now(),
            lastEmailUpdate = null,
            deletionRequestedAt = null
        )
        userAccountRepository.upsertAccountInfo(userAccount)

        // Create UserProfile (Room-First)
        val userProfile = UserProfile(
            userId = user.uid,
            displayName = username,
            bio = null,
            age = null,
            weight = null,
            availableEquipment = emptyList(),
            otherEquipment = null,
            fitnessGoals = emptyList(),
            goalsPriority = emptyMap(),
            isPublic = true,
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

        val userProfileResult = profileCommandUseCase.saveProfile(userProfile, strictValidation = false)
        userProfileResult.fold(
            onSuccess = { Timber.d("UserProfile saved successfully for user: ${user.uid}") },
            onFailure = { error -> Timber.e(error, "UserProfile save failed for user: ${user.uid}") }
        )

        // Create SocialProfile (Room-First)
        val socialProfileResult = socialProfileCommandUseCase.create(
            username = username,
            displayName = username,
            bio = null
        )
        socialProfileResult.fold(
            onSuccess = { Timber.d("SocialProfile created successfully for user: ${user.uid}") },
            onFailure = { error -> Timber.e(error, "SocialProfile creation failed for user: ${user.uid}") }
        )

        // Allow time for all local DB writes to complete
        delay(500)

        // Trigger immediate sync via SyncCoordinator (Room-First pattern)
        try {
            syncCoordinator.triggerImmediateSync(user.uid)
            Timber.d("Immediate sync triggered for user: ${user.uid}")
        } catch (e: Exception) {
            Timber.e(e, "Immediate sync failed, relying on periodic sync")
        }

        Timber.i("Signup flow completed successfully")
        user
    }

    // ============== SIGN IN ANONYMOUSLY ==============

    /**
     * Sign in anonymously (guest mode).
     *
     * Replaces: SignInAnonymouslyUseCase
     *
     * @return LiftrixResult containing anonymous user or error
     */
    suspend fun signInAnonymously(): LiftrixResult<User> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.AuthenticationError(
                errorMessage = "Anonymous sign in failed: ${throwable.message}",
                errorCode = "SIGN_IN_ANONYMOUS_FAILED",
                analyticsContext = mapOf("operation" to "SIGN_IN_ANONYMOUS")
            )
        }
    ) {
        val result = authRepository.signInAnonymously()
        result.fold(
            onSuccess = { it },
            onFailure = { throw it }
        )
    }

    // ============== SIGN OUT (SIMPLE) ==============

    /**
     * Sign out (simple) with comprehensive session cleanup.
     *
     * FIX AUTH-007: Session Fixation Risk (CVSS 7.8)
     * Implements complete session cleanup to prevent session fixation attacks.
     *
     * Replaces: SignOutUseCase
     *
     * @return LiftrixResult with success or error
     */
    suspend fun signOut(): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.AuthenticationError(
                errorMessage = "Sign out failed: ${throwable.message}",
                errorCode = "SIGN_OUT_FAILED",
                analyticsContext = mapOf("operation" to "SIGN_OUT")
            )
        }
    ) {
        // Get user ID before signing out
        val userId = authRepository.getCurrentUserId()

        // 1. Clear Firebase auth session
        val result = authRepository.signOut()
        result.fold(
            onSuccess = { },
            onFailure = { throw it }
        )

        // 2. Clear local session data (AUTH-007 FIX)
        try {
            settingsRepository.clearAllSettings()
        } catch (e: Exception) {
            Timber.w(e, "Failed to clear settings during sign out")
        }

        // 3. Clear WorkManager jobs for this user (AUTH-007 FIX)
        if (userId != null) {
            try {
                workManager.cancelAllWorkByTag("sync_$userId")
                workManager.cancelAllWorkByTag("user_$userId")
            } catch (e: Exception) {
                Timber.w(e, "Failed to cancel WorkManager jobs during sign out")
            }
        }

        // 4. Clear analytics session (AUTH-007 FIX)
        try {
            analyticsService.clearUserProperties()
        } catch (e: Exception) {
            Timber.w(e, "Failed to clear analytics during sign out")
        }

        // Note: FCM token revocation and credential cache invalidation
        // are handled by the enhanced sign-out flow if needed
    }

    // ============== SIGN OUT (ENHANCED) ==============

    /**
     * Sign out with comprehensive cleanup.
     *
     * Replaces: EnhancedSignOutUseCase
     *
     * Flow:
     * 1. Sign out from Firebase
     * 2. Clear local settings/cache
     * 3. Clear UI state
     * 4. Stop background services
     * 5. Clear analytics properties
     * 6. Log sign out event
     *
     * @return LiftrixResult with success or error
     */
    suspend fun signOutEnhanced(): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.AuthenticationError(
                errorMessage = "Enhanced sign out failed: ${throwable.message}",
                errorCode = "SIGN_OUT_ENHANCED_FAILED",
                analyticsContext = mapOf("operation" to "SIGN_OUT_ENHANCED")
            )
        }
    ) {
        Timber.d("Starting enhanced sign out process")

        // Step 1: Get current user ID for analytics
        val currentUserId = authRepository.getCurrentUserId()
        Timber.d("Current user ID: $currentUserId")

        // Step 2: Sign out from Firebase
        val signOutResult = authRepository.signOut()
        if (signOutResult.isFailure) {
            Timber.e("Firebase sign out failed: ${signOutResult.exceptionOrNull()?.message}")
            throw signOutResult.exceptionOrNull() ?: Exception("Sign out failed")
        }
        Timber.d("Firebase sign out successful")

        // Step 3: Clear local data
        try {
            val clearSettingsResult = settingsRepository.clearAllSettings()
            if (clearSettingsResult.isFailure) {
                Timber.w("Failed to clear settings: ${clearSettingsResult.exceptionOrNull()?.message}")
            }
            Timber.d("Local data cleared successfully")
        } catch (e: Exception) {
            Timber.w(e, "Non-critical: Failed to clear some local data")
        }

        // Step 4: Clear UI state
        try {
            val cleanedViewModels = stateCleanupManager.cleanupAllState()
            Timber.d("UI state cleanup completed: $cleanedViewModels ViewModels cleaned")

            analyticsService.logEvent(
                eventName = "ui_state_cleanup",
                parameters = mapOf(
                    "cleaned_viewmodels" to cleanedViewModels,
                    "total_registered" to stateCleanupManager.getRegisteredCount(),
                    "user_id" to (currentUserId ?: "unknown")
                )
            )
        } catch (e: Exception) {
            Timber.w(e, "Non-critical: Failed to cleanup UI state")
        }

        // Step 5: Stop background services
        try {
            syncManager.cancelSync()
            workManager.cancelAllWork()
            Timber.d("Background services stopped successfully")
        } catch (e: Exception) {
            Timber.w(e, "Non-critical: Failed to stop some background services")
        }

        // Step 6: Clear analytics properties
        try {
            val clearAnalyticsResult = analyticsService.clearUserProperties()
            if (clearAnalyticsResult.isFailure) {
                Timber.w("Failed to clear analytics properties: ${clearAnalyticsResult.exceptionOrNull()?.message}")
            }
        } catch (e: Exception) {
            Timber.w(e, "Non-critical: Failed to clear analytics properties")
        }

        // Step 7: Log sign out event
        try {
            analyticsService.logEvent(
                eventName = "user_signed_out",
                parameters = mapOf(
                    "user_id" to (currentUserId ?: "unknown"),
                    "sign_out_method" to "enhanced_sign_out_use_case",
                    "timestamp" to System.currentTimeMillis()
                )
            )
        } catch (e: Exception) {
            Timber.w(e, "Non-critical: Failed to log sign out analytics event")
        }

        Timber.d("Enhanced sign out process completed successfully")
    }

    // ============== RESET PASSWORD ==============

    /**
     * Send password reset email.
     *
     * Replaces: ForgotPasswordUseCase
     *
     * @param email Email address to send reset link
     * @return LiftrixResult with success or error
     */
    suspend fun resetPassword(email: String): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.AuthenticationError(
                errorMessage = "Password reset failed: ${throwable.message}",
                errorCode = "RESET_PASSWORD_FAILED",
                analyticsContext = mapOf(
                    "email" to email,
                    "operation" to "RESET_PASSWORD"
                )
            )
        }
    ) {
        require(email.isNotBlank()) { "Email cannot be blank" }
        require(isValidEmail(email)) { "Invalid email format" }

        val result = authRepository.sendPasswordResetEmail(email)
        result.fold(
            onSuccess = { },
            onFailure = { throw it }
        )
    }

    // ============== HELPER METHODS ==============

    /**
     * Validate email format using Android patterns.
     */
    private fun isValidEmail(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    /**
     * Validate password (minimum 6 characters).
     */
    private fun isValidPassword(password: String): Boolean {
        return password.length >= 6
    }

    /**
     * Validate username format (3-20 chars, alphanumeric + underscore).
     */
    private fun isValidUsername(username: String): Boolean {
        return username.length in 3..20 && username.matches(Regex("^[a-zA-Z0-9_]+$"))
    }

    /**
     * Handle UserAccount creation/update for email sign-in backward compatibility.
     */
    private suspend fun handleUserAccountCreation(user: User, email: String) {
        val accountResult = userAccountRepository.getAccountInfoSuspend(user.uid)
        accountResult.fold(
            onSuccess = { existingAccount ->
                if (existingAccount == null) {
                    // Create UserAccount for backward compatibility
                    Timber.w("UserAccount missing for existing user ${user.uid}, creating now")

                    val userAccount = UserAccount(
                        userId = user.uid,
                        email = email,
                        username = null,
                        emailVerified = user.isEmailVerified,
                        displayName = user.displayName ?: email.substringBefore("@"),
                        lastPasswordChange = null,
                        accountCreatedAt = LocalDateTime.now(),
                        lastEmailUpdate = null,
                        deletionRequestedAt = null
                    )

                    userAccountRepository.upsertAccountInfo(userAccount).fold(
                        onSuccess = {
                            Timber.d("UserAccount created for existing user ${user.uid}")
                            val syncRequest = UserPublicSyncWorker.createWorkRequest(user.uid, forceSync = true)
                            workManager.enqueue(syncRequest)
                        },
                        onFailure = { error ->
                            Timber.e("Failed to create UserAccount: $error")
                        }
                    )
                } else {
                    // Trigger sync for existing users with username
                    if (existingAccount.username != null) {
                        val syncRequest = UserPublicSyncWorker.createWorkRequest(user.uid, forceSync = false)
                        workManager.enqueue(syncRequest)
                    }
                }
            },
            onFailure = { error ->
                Timber.e("Failed to check UserAccount: $error")
            }
        )
    }

    /**
     * Create UserAccount and profiles for new Google user.
     */
    private suspend fun createGoogleUserAccount(user: User) {
        val username = generateUsernameFromGoogle(user)
        val userAccount = UserAccount.create(
            userId = user.uid,
            email = user.email ?: "unknown@google.com",
            displayName = user.displayName,
            username = username
        )

        userAccountRepository.upsertAccountInfo(userAccount).fold(
            onSuccess = {
                Timber.d("UserAccount created for Google user ${user.uid} with username: $username")

                // Create social profile with delay
                delay(1000)
                val socialProfileResult = socialProfileCommandUseCase.create(
                    username = userAccount.username ?: "user_${user.uid.take(8)}",
                    displayName = user.displayName ?: userAccount.username ?: "User",
                    bio = null
                )
                socialProfileResult.fold(
                    onSuccess = { Timber.d("✅ Social profile created for Google user ${user.uid}") },
                    onFailure = { Timber.e("❌ Failed to create social profile: ${it.message}") }
                )

                // Create user profile
                createUserProfileForGoogleUser(user, userAccount)

                // Trigger sync
                val syncRequest = UserPublicSyncWorker.createWorkRequest(user.uid, forceSync = true)
                workManager.enqueue(syncRequest)
            },
            onFailure = { error ->
                Timber.e("CRITICAL: Failed to create UserAccount for user ${user.uid}. Error: $error")
            }
        )
    }

    /**
     * Update existing Google user account.
     */
    private suspend fun updateExistingGoogleAccount(user: User, existingAccount: UserAccount) {
        if (existingAccount.username != null) {
            // Ensure profiles exist
            delay(500)

            val socialProfileResult = socialProfileCommandUseCase.create(
                username = existingAccount.username!!,
                displayName = user.displayName ?: existingAccount.displayName ?: existingAccount.username!!,
                bio = null
            )
            socialProfileResult.fold(
                onSuccess = { Timber.d("✅ Social profile ensured for existing user ${user.uid}") },
                onFailure = { Timber.d("Social profile check: ${it.message}") }
            )

            createUserProfileForGoogleUser(user, existingAccount)

            // Trigger sync
            val syncRequest = UserPublicSyncWorker.createWorkRequest(user.uid, forceSync = false)
            workManager.enqueue(syncRequest)
        } else {
            // Generate and set username
            val username = generateUsernameFromGoogle(user)
            userAccountRepository.updateUsername(user.uid, username).fold(
                onSuccess = {
                    Timber.d("Username set for existing Google user ${user.uid}")
                    val syncRequest = UserPublicSyncWorker.createWorkRequest(user.uid, forceSync = true)
                    workManager.enqueue(syncRequest)
                },
                onFailure = { Timber.e("Failed to set username: $it") }
            )
        }
    }

    /**
     * Create UserProfile for Google user.
     */
    private suspend fun createUserProfileForGoogleUser(user: User, account: UserAccount) {
        val userProfile = UserProfile(
            userId = user.uid,
            displayName = user.displayName ?: account.displayName ?: account.username ?: "User",
            bio = null,
            age = null,
            weight = null,
            availableEquipment = emptyList(),
            otherEquipment = null,
            fitnessGoals = emptyList(),
            goalsPriority = null,
            isPublic = true,
            lastActiveAt = LocalDateTime.now(),
            totalWorkouts = 0,
            currentStreak = 0,
            longestStreak = 0,
            memberSince = account.accountCreatedAt ?: LocalDateTime.now(),
            profileCompletionPercentage = 70,
            achievements = emptyList(),
            completedAt = null,
            updatedAt = LocalDateTime.now(),
            profileVersion = 1L,
            profileImageUrl = user.photoUrl,
            profileImageUpdatedAt = user.photoUrl?.let { LocalDateTime.now() },
            hasCustomProfileImage = user.photoUrl != null
        )

        val userProfileResult = profileCommandUseCase.saveProfile(userProfile, strictValidation = false)
        userProfileResult.fold(
            onSuccess = { Timber.d("✅ User profile created for Google user ${user.uid}") },
            onFailure = { Timber.e("❌ Failed to create user profile: ${it.message}") }
        )
    }

    /**
     * Generate username from Google user data with validation.
     */
    private fun generateUsernameFromGoogle(user: User): String {
        fun sanitizeUsername(input: String): String? {
            val sanitized = input.lowercase()
                .replace(Regex("[^a-z0-9_]"), "_")
                .replace(Regex("_+"), "_")
                .trim('_')
                .take(15)

            return if (sanitized.length >= 3 && sanitized.matches(Regex("^[a-z0-9_]+$"))) {
                sanitized
            } else null
        }

        // Try email prefix
        user.email?.substringBefore("@")?.let { emailPrefix ->
            sanitizeUsername(emailPrefix)?.let { validPrefix ->
                val suffix = (1000..9999).random()
                val candidate = "${validPrefix}_$suffix"
                if (candidate.length <= 20) return candidate
            }
        }

        // Try display name
        user.displayName?.let { displayName ->
            sanitizeUsername(displayName)?.let { validName ->
                val suffix = (1000..9999).random()
                val candidate = "${validName}_$suffix"
                if (candidate.length <= 20) return candidate
            }
        }

        // Fallback to shortened user ID
        val shortUid = user.uid.take(8).lowercase()
        val suffix = (100..999).random()
        return "user_${shortUid}_$suffix"
    }

    /**
     * Sync onboarding profile data after login.
     */
    private suspend fun syncOnboardingProfileAfterLogin(userId: String) {
        withContext(Dispatchers.IO) {
            try {
                Timber.d("Checking for unsynced onboarding profile for user $userId")

                val hasProfile = profileRepository.hasProfile(userId)
                if (!hasProfile) {
                    Timber.d("No profile found for user $userId - skipping profile sync")
                    return@withContext
                }

                val unsyncedCount = profileRepository.getUnsyncedCount(userId)
                if (unsyncedCount > 0) {
                    Timber.d("Found $unsyncedCount unsynced profile entries for user $userId")

                    profileRepository.queueSync(userId)
                    val syncResult = profileRepository.syncNow(userId)

                    if (syncResult.isSuccess) {
                        Timber.d("Successfully synced onboarding profile for user $userId")
                    } else {
                        Timber.w("Failed to sync profile immediately, will retry in background")
                    }
                } else {
                    Timber.d("Profile already synced for user $userId")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error syncing onboarding profile")
            }
        }
    }

    /**
     * Restore follow relationships from Firebase after login.
     */
    private suspend fun restoreFollowRelationshipsAfterLogin(userId: String) {
        withContext(Dispatchers.IO) {
            try {
                Timber.d("🔥 FOLLOW-SYNC-FIX: Starting follow relationship restoration for user $userId")

                val restoreWorkRequest = FollowRelationshipSyncWorker.createRestoreWorkRequest(userId)
                workManager.enqueue(restoreWorkRequest)

                Timber.d("🔥 FOLLOW-SYNC-FIX: Queued follow relationship restoration work for user $userId")
            } catch (e: Exception) {
                Timber.e(e, "🔥 FOLLOW-SYNC-FIX: Error queuing follow restoration work")
            }
        }
    }

    /**
     * Transfer pending onboarding data to authenticated user after Google login.
     */
    private suspend fun transferOnboardingDataAfterLogin(userId: String) {
        withContext(Dispatchers.IO) {
            try {
                Timber.d("🔧 ONBOARDING-FIX: Checking for pending onboarding data for user $userId")

                val hasPendingData = onboardingDataStore.hasPendingOnboardingData()

                if (hasPendingData) {
                    Timber.d("🔧 ONBOARDING-FIX: Found pending onboarding data, initiating transfer")

                    val transferResult = onboardingDataStore.retrievePendingOnboardingData(userId)

                    transferResult.fold(
                        onSuccess = { pendingData ->
                            if (pendingData != null && pendingData.isCompleteForSaving()) {
                                val userProfile = pendingData.toDomainModel()

                                profileRepository.saveProfile(userProfile).fold(
                                    onSuccess = {
                                        Timber.d("🔧 ONBOARDING-FIX: Successfully saved transferred profile")
                                        onboardingDataStore.clearPendingOnboardingData()
                                        profileRepository.queueSync(userId)
                                        profileRepository.syncNow(userId)
                                    },
                                    onFailure = { error ->
                                        Timber.e("🔧 ONBOARDING-FIX: Failed to save transferred profile: ${error.message}")
                                    }
                                )
                            }
                        },
                        onFailure = { error ->
                            Timber.e("🔧 ONBOARDING-FIX: Failed to retrieve pending data: ${error.message}")
                        }
                    )
                } else {
                    Timber.d("🔧 ONBOARDING-FIX: No pending onboarding data found")
                    syncExistingProfileIfNeeded(userId)
                }
            } catch (e: Exception) {
                Timber.e(e, "🔧 ONBOARDING-FIX: Error during onboarding data transfer")
                syncExistingProfileIfNeeded(userId)
            }
        }
    }

    /**
     * Sync existing profile if needed (fallback).
     */
    private suspend fun syncExistingProfileIfNeeded(userId: String) {
        try {
            val hasProfile = profileRepository.hasProfile(userId)
            if (hasProfile) {
                val unsyncedCount = profileRepository.getUnsyncedCount(userId)
                if (unsyncedCount > 0) {
                    profileRepository.queueSync(userId)
                    profileRepository.syncNow(userId)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error syncing existing profile")
        }
    }
}
