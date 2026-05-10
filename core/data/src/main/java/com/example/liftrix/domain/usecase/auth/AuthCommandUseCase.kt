package com.example.liftrix.domain.usecase.auth

import com.example.liftrix.core.identity.UserId
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
import com.example.liftrix.domain.service.SessionStateCleanup
import com.example.liftrix.domain.sync.SyncScheduler
import com.example.liftrix.domain.usecase.profile.ProfileCommandUseCase
import com.example.liftrix.domain.usecase.social.ApplyOfficialOnboardingFollowsUseCase
import com.example.liftrix.domain.usecase.social.SocialProfileCommandUseCase
import com.example.liftrix.domain.model.onboarding.UserProfileData
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
class AuthCommandUseCaseImpl @Inject constructor(
    private val authRepository: AuthRepository,
    private val userAccountRepository: UserAccountRepository,
    private val profileRepository: ProfileRepository,
    private val socialProfileRepository: SocialProfileRepository,
    private val profileCommandUseCase: ProfileCommandUseCase,
    private val socialProfileCommandUseCase: SocialProfileCommandUseCase,
    private val applyOfficialOnboardingFollowsUseCase: ApplyOfficialOnboardingFollowsUseCase,
    private val syncScheduler: SyncScheduler,
    private val onboardingDataStore: OnboardingDataStore,
    private val analyticsService: AnalyticsService,
    private val settingsRepository: SettingsRepository,
    private val stateCleanupManager: SessionStateCleanup
) : AuthCommandUseCase {

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
    override suspend fun signInWithEmail(email: String, password: String): LiftrixResult<User> = liftrixCatching(
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

        // Transfer any pre-auth onboarding data, then sync the resulting profile.
        transferOnboardingDataAfterLogin(UserId(user.uid))

        // Restore follow relationships
        restoreFollowRelationshipsAfterLogin(UserId(user.uid))

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
    override suspend fun signInWithGoogle(idToken: String): LiftrixResult<User> = liftrixCatching(
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
            transferOnboardingDataAfterLogin(UserId(user.uid))

            // Restore follow relationships
            restoreFollowRelationshipsAfterLogin(UserId(user.uid))

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
    override suspend fun signUpWithEmail(
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

        // Create SocialProfile (Room-First) - BLOCKING
        // CRITICAL FIX: Make social profile creation mandatory to prevent incomplete user states
        Timber.d("PROFILE_SOCIAL_CREATE_TRIGGER userId=${user.uid} source=signup_email username=$username")
        val socialProfileResult = socialProfileCommandUseCase.create(
            username = username,
            displayName = username,
            bio = null
        )
        val socialProfile = socialProfileResult.fold(
            onSuccess = {
                Timber.d("PROFILE_SOCIAL_CREATE_SUCCESS userId=${user.uid} source=signup_email")
                it
            },
            onFailure = { error ->
                Timber.e(error, "PROFILE_SOCIAL_CREATE_FAIL userId=${user.uid} source=signup_email error=${error.message}")
                // Clean up partial state before throwing
                try {
                    profileCommandUseCase.deleteProfile(user.uid)
                    userAccountRepository.deleteAccount(user.uid)
                    Timber.d("Cleaned up partial user state for failed signup: ${user.uid}")
                } catch (cleanupError: Exception) {
                    Timber.e(cleanupError, "Failed to cleanup partial state, manual cleanup may be required")
                }
                throw IllegalStateException("Failed to create complete user profile: ${error.message}")
            }
        )

        // Apply any onboarding data collected before account creation to this new user.
        transferOnboardingDataAfterLogin(UserId(user.uid))

        // Allow time for all local DB writes to complete
        delay(500)

        // Trigger immediate sync via SyncCoordinator (Room-First pattern)
        try {
            syncScheduler.triggerImmediateSync(user.uid)
            Timber.d("Immediate sync triggered for user: ${user.uid}")
        } catch (e: Exception) {
            Timber.e(e, "Immediate sync failed, relying on periodic sync")
        }

        Timber.i("Signup flow completed successfully")
        user
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
    override suspend fun signOut(): LiftrixResult<Unit> = liftrixCatching(
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
                syncScheduler.cancelSyncForUser(userId.value)
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
    override suspend fun signOutEnhanced(): LiftrixResult<Unit> = liftrixCatching(
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
        val currentUserId = authRepository.getCurrentUserId()?.value
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
            syncScheduler.cancelAllSync()
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
    override suspend fun resetPassword(email: String): LiftrixResult<Unit> = liftrixCatching(
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
                            syncScheduler.enqueueUserPublicSync(user.uid, forceSync = true)
                        },
                        onFailure = { error ->
                            Timber.e("Failed to create UserAccount: $error")
                        }
                    )
                } else {
                    // Trigger sync for existing users with username
                    if (existingAccount.username != null) {
                        syncScheduler.enqueueUserPublicSync(user.uid, forceSync = false)
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
                Timber.d("PROFILE_SOCIAL_CREATE_TRIGGER userId=${user.uid} source=google_new username=${userAccount.username}")
                val socialProfileResult = socialProfileCommandUseCase.create(
                    username = userAccount.username ?: "user_${user.uid.take(8)}",
                    displayName = user.displayName ?: userAccount.username ?: "User",
                    bio = null,
                    profilePhotoUrl = user.photoUrl
                )
                socialProfileResult.fold(
                    onSuccess = { Timber.d("PROFILE_SOCIAL_CREATE_SUCCESS userId=${user.uid} source=google_new") },
                    onFailure = { Timber.e("PROFILE_SOCIAL_CREATE_FAIL userId=${user.uid} source=google_new error=${it.message}") }
                )

                // Create user profile
                createUserProfileForGoogleUser(user, userAccount)

                // Trigger sync
                syncScheduler.enqueueUserPublicSync(user.uid, forceSync = true)
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

            Timber.d("PROFILE_SOCIAL_CREATE_TRIGGER userId=${user.uid} source=google_existing username=${existingAccount.username}")
            val socialProfileResult = socialProfileCommandUseCase.create(
                username = existingAccount.username!!,
                displayName = user.displayName ?: existingAccount.displayName ?: existingAccount.username!!,
                bio = null,
                profilePhotoUrl = user.photoUrl
            )
            socialProfileResult.fold(
                onSuccess = { Timber.d("PROFILE_SOCIAL_CREATE_SUCCESS userId=${user.uid} source=google_existing") },
                onFailure = { Timber.d("PROFILE_SOCIAL_CREATE_FAIL userId=${user.uid} source=google_existing error=${it.message}") }
            )

            createUserProfileForGoogleUser(user, existingAccount)

            // Trigger sync
            syncScheduler.enqueueUserPublicSync(user.uid, forceSync = false)
        } else {
            // Generate and set username
            val username = generateUsernameFromGoogle(user)
            userAccountRepository.updateUsername(user.uid, username).fold(
                onSuccess = {
                    Timber.d("Username set for existing Google user ${user.uid}")
                    Timber.d("PROFILE_SOCIAL_CREATE_TRIGGER userId=${user.uid} source=google_existing username=$username")
                    val socialProfileResult = socialProfileCommandUseCase.create(
                        username = username,
                        displayName = user.displayName ?: username,
                        bio = null,
                        profilePhotoUrl = user.photoUrl
                    )
                    socialProfileResult.fold(
                        onSuccess = { Timber.d("PROFILE_SOCIAL_CREATE_SUCCESS userId=${user.uid} source=google_existing") },
                        onFailure = { Timber.d("PROFILE_SOCIAL_CREATE_FAIL userId=${user.uid} source=google_existing error=${it.message}") }
                    )
                    syncScheduler.enqueueUserPublicSync(user.uid, forceSync = true)
                },
                onFailure = { Timber.e("Failed to set username: $it") }
            )
        }
    }

    /**
     * Create UserProfile for Google user.
     */
    private suspend fun createUserProfileForGoogleUser(user: User, account: UserAccount) {
        val now = LocalDateTime.now()
        val existingProfile = profileRepository.getUserProfile(user.uid).getOrNull()
        val displayName = existingProfile?.displayName
            ?.takeIf { it.isNotBlank() && it != "User" }
            ?: user.displayName
            ?: account.displayName
            ?: account.username
            ?: "User"
        val profileImageUrl = existingProfile?.profileImageUrl ?: user.photoUrl

        val userProfile = existingProfile?.copy(
            displayName = displayName,
            profileImageUrl = profileImageUrl,
            profileImageUpdatedAt = existingProfile.profileImageUpdatedAt ?: user.photoUrl?.let { now },
            hasCustomProfileImage = existingProfile.hasCustomProfileImage || user.photoUrl != null,
            lastActiveAt = now,
            updatedAt = now
        ) ?: UserProfile(
            userId = user.uid,
            displayName = displayName,
            bio = null,
            age = null,
            weight = null,
            availableEquipment = emptyList(),
            otherEquipment = null,
            fitnessGoals = emptyList(),
            goalsPriority = null,
            isPublic = true,
            lastActiveAt = now,
            totalWorkouts = 0,
            currentStreak = 0,
            longestStreak = 0,
            memberSince = account.accountCreatedAt ?: now,
            profileCompletionPercentage = 0,
            achievements = emptyList(),
            completedAt = null,
            updatedAt = now,
            profileVersion = 1L,
            profileImageUrl = profileImageUrl,
            profileImageUpdatedAt = user.photoUrl?.let { now },
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
    private suspend fun syncOnboardingProfileAfterLogin(userId: UserId) {
        withContext(Dispatchers.IO) {
            try {
                Timber.d("Checking for unsynced onboarding profile for user ${userId.value}")

                val hasProfile = profileRepository.hasProfile(userId.value)
                if (!hasProfile) {
                    Timber.d("No profile found for user ${userId.value} - skipping profile sync")
                    return@withContext
                }

                val unsyncedCount = profileRepository.getUnsyncedCount(userId.value)
                if (unsyncedCount > 0) {
                    Timber.d("Found $unsyncedCount unsynced profile entries for user ${userId.value}")

                    profileRepository.queueSync(userId.value)
                    val syncResult = profileRepository.syncNow(userId.value)

                    if (syncResult.isSuccess) {
                        Timber.d("Successfully synced onboarding profile for user ${userId.value}")
                    } else {
                        Timber.w("Failed to sync profile immediately, will retry in background")
                    }
                } else {
                    Timber.d("Profile already synced for user ${userId.value}")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error syncing onboarding profile")
            }
        }
    }

    /**
     * Restore follow relationships from Firebase after login.
     */
    private suspend fun restoreFollowRelationshipsAfterLogin(userId: UserId) {
        withContext(Dispatchers.IO) {
            try {
                Timber.d("🔥 FOLLOW-SYNC-FIX: Starting follow relationship restoration for user ${userId.value}")

                syncScheduler.enqueueFollowRelationshipSync(
                    userId = userId.value,
                    restoreFromFirebase = true
                )

                Timber.d("🔥 FOLLOW-SYNC-FIX: Queued follow relationship restoration work for user ${userId.value}")
            } catch (e: Exception) {
                Timber.e(e, "🔥 FOLLOW-SYNC-FIX: Error queuing follow restoration work")
            }
        }
    }

    /**
     * Transfer pending onboarding data to authenticated user after Google login.
     */
    private suspend fun transferOnboardingDataAfterLogin(userId: UserId) {
        withContext(Dispatchers.IO) {
            try {
                Timber.d("🔧 ONBOARDING-FIX: Checking for pending onboarding data for user ${userId.value}")

                val hasPendingData = onboardingDataStore.hasPendingOnboardingData()

                if (hasPendingData) {
                    Timber.d("🔧 ONBOARDING-FIX: Found pending onboarding data, initiating transfer")

                    val transferResult = onboardingDataStore.retrievePendingOnboardingData(userId.value)

                    transferResult.fold(
                        onSuccess = { pendingData ->
                            val userProfile = pendingData?.let { buildMergedOnboardingProfile(userId, it) }
                            if (userProfile != null) {

                                profileCommandUseCase.saveProfile(
                                    profile = userProfile,
                                    strictValidation = false
                                ).fold(
                                    onSuccess = {
                                        Timber.d("🔧 ONBOARDING-FIX: Successfully saved transferred profile")
                                        applyOfficialOnboardingFollowsUseCase(
                                            userId = userId.value,
                                            onboardingData = pendingData
                                        ).fold(
                                            onSuccess = { followedAccountIds ->
                                                Timber.d("Applied official onboarding follows for user ${userId.value}: ${followedAccountIds.size}")
                                            },
                                            onFailure = { error ->
                                                Timber.w(error, "Failed to apply official onboarding follows for user ${userId.value}")
                                            }
                                        )
                                        onboardingDataStore.clearPendingOnboardingData()
                                        profileRepository.queueSync(userId.value)
                                        profileRepository.syncNow(userId.value)
                                    },
                                    onFailure = { error ->
                                        Timber.e("🔧 ONBOARDING-FIX: Failed to save transferred profile: ${error.message}")
                                    }
                                )
                            } else {
                                Timber.w("Pending onboarding data was missing or incomplete; skipping transfer")
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
     * Merge pre-auth onboarding fields into the authenticated user's profile.
     * This preserves identity, social, image, and workout stats that may already exist.
     */
    private suspend fun buildMergedOnboardingProfile(
        userId: UserId,
        pendingData: com.example.liftrix.domain.model.onboarding.OnboardingDataSnapshot
    ): UserProfile? {
        val profileData = UserProfileData.fromSnapshot(pendingData)
        if (!profileData.isCompleteForSaving()) {
            return null
        }

        val onboardingProfile = profileData.toDomainModel()
        val existingProfile = profileRepository.getUserProfile(userId.value).getOrNull()
        val account = userAccountRepository.getAccountInfoSuspend(userId.value).getOrNull()
        val now = LocalDateTime.now()
        val displayName = existingProfile?.displayName
            ?.takeIf { it.isNotBlank() && it != "User" }
            ?: account?.displayName?.takeIf { it.isNotBlank() }
            ?: account?.username?.takeIf { it.isNotBlank() }
            ?: account?.email?.substringBefore("@")?.takeIf { it.isNotBlank() }
            ?: "User"

        val baseProfile = existingProfile ?: onboardingProfile.copy(
            userId = userId.value,
            displayName = displayName,
            bio = null,
            isPublic = true,
            lastActiveAt = now,
            totalWorkouts = 0,
            currentStreak = 0,
            longestStreak = 0,
            memberSince = account?.accountCreatedAt ?: now,
            achievements = emptyList(),
            profileImageUrl = null,
            profileImageUpdatedAt = null,
            hasCustomProfileImage = false
        )

        return baseProfile.copy(
            userId = userId.value,
            displayName = displayName,
            age = onboardingProfile.age,
            weight = onboardingProfile.weight,
            availableEquipment = onboardingProfile.availableEquipment,
            otherEquipment = onboardingProfile.otherEquipment,
            fitnessGoals = onboardingProfile.fitnessGoals,
            goalsPriority = onboardingProfile.goalsPriority,
            completedAt = now,
            updatedAt = now,
            lastActiveAt = baseProfile.lastActiveAt ?: now
        )
    }

    /**
     * Sync existing profile if needed (fallback).
     */
    private suspend fun syncExistingProfileIfNeeded(userId: UserId) {
        try {
            val hasProfile = profileRepository.hasProfile(userId.value)
            if (hasProfile) {
                val unsyncedCount = profileRepository.getUnsyncedCount(userId.value)
                if (unsyncedCount > 0) {
                    profileRepository.queueSync(userId.value)
                    profileRepository.syncNow(userId.value)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error syncing existing profile")
        }
    }
}
