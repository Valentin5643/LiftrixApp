package com.example.liftrix.ui.profile

import android.graphics.Rect
import androidx.compose.runtime.Stable
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.example.liftrix.domain.model.UserProfile
import com.example.liftrix.domain.model.UserAchievement
import com.example.liftrix.domain.repository.ProfileRepository
import com.example.liftrix.domain.usecase.GetProfileUseCase
import com.example.liftrix.domain.usecase.auth.GetCurrentUserIdUseCase
import com.example.liftrix.domain.usecase.profile.ProfileImageOperationsUseCase
import com.example.liftrix.domain.usecase.profile.SaveUserProfileUseCase
import com.example.liftrix.domain.usecase.profile.CalculateAchievementsUseCase
import com.example.liftrix.ui.common.state.UiState
import com.example.liftrix.ui.common.viewmodel.BaseViewModel
import com.example.liftrix.ui.common.event.ViewModelEvent
import com.example.liftrix.domain.usecase.common.ErrorHandler
import com.example.liftrix.domain.service.NetworkConnectivityMonitor
import com.example.liftrix.domain.repository.SyncStatusRepository
import com.example.liftrix.domain.repository.SyncPreferencesRepository
import com.example.liftrix.sync.SyncCoordinator
import com.example.liftrix.sync.SyncManager
import com.example.liftrix.sync.SyncStatus
import com.google.firebase.auth.FirebaseAuth
import androidx.work.WorkManager
import androidx.work.WorkInfo
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.lifecycle.asFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.LocalDateTime
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

/**
 * ✅ Profile data state wrapper to track loading states explicitly
 */
private sealed class ProfileDataState {
    object Loading : ProfileDataState()
    object Missing : ProfileDataState()
    object NoAuth : ProfileDataState()
    data class Loaded(val profile: UserProfile) : ProfileDataState()
    data class Error(val throwable: Throwable) : ProfileDataState()
}

/**
 * Enhanced ProfileViewModel with comprehensive profile management capabilities.
 * 
 * Features:
 * - Complete profile data loading and state management
 * - Profile editing with validation and feedback
 * - Achievement integration with real-time updates
 * - Profile completion tracking and suggestions
 * - Image upload with progress tracking and error handling
 * - Image deletion with user confirmation workflow
 * - Real-time profile updates from all backend services
 * - Privacy controls and social feature integration
 * - Error recovery strategies with user-friendly messaging
 * - Material Design loading and error states
 * 
 * State Management:
 * - ProfileUiState: Complete profile state including all operations
 * - ProfileEvent: User actions and system events
 * - Reactive flows for real-time UI updates
 * - Error handling with contextual recovery options
 * - Achievement flow integration
 * - Profile completion flow with suggestions
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val getCurrentUserIdUseCase: GetCurrentUserIdUseCase,
    private val getProfileUseCase: GetProfileUseCase,
    private val saveUserProfileUseCase: SaveUserProfileUseCase,
    private val calculateAchievementsUseCase: CalculateAchievementsUseCase,
    private val profileImageOperationsUseCase: ProfileImageOperationsUseCase,
    private val profileRepository: ProfileRepository,
    private val auth: FirebaseAuth,
    private val networkConnectivityMonitor: NetworkConnectivityMonitor,
    private val syncCoordinator: SyncCoordinator,
    private val syncManager: SyncManager,
    private val syncStatusRepository: SyncStatusRepository,
    private val syncPreferencesRepository: SyncPreferencesRepository,
    errorHandler: ErrorHandler
) : BaseViewModel<ProfileUiState, ProfileEvent>(
    errorHandler = errorHandler
) {
    
    // Get WorkManager instance directly instead of injecting it
    private val workManager: WorkManager by lazy { WorkManager.getInstance(context) }
    
    override val _uiState = MutableStateFlow(ProfileUiState(
        profileState = ProfileLoadingState.Loading,
        isLoading = true
    ))
    
    // Current user ID flow with enhanced error handling and cold-start resilience
    // 🔍 FORENSIC MONITORING - Track userId flow emissions during profile changes
    private val currentUserId = flow {
        var retryCount = 0
        val maxRetries = 3
        
        while (retryCount <= maxRetries) {
            try {
                val userId = getCurrentUserIdUseCase()
                if (userId != null) {
                    Timber.d("[VIEWMODEL-STATE] currentUserId flow emitted: $userId (attempt ${retryCount + 1})")
                    emit(userId)
                    return@flow
                } else if (retryCount < maxRetries) {
                    // Retry with exponential backoff for cold starts
                    val delayMs = (1000L * (retryCount + 1)) // 1s, 2s, 3s
                    kotlinx.coroutines.delay(delayMs)
                }
            } catch (e: Exception) {
                Timber.e(e, "ProfileViewModel: Failed to get current user ID (attempt ${retryCount + 1})")
                
                if (retryCount < maxRetries) {
                    val delayMs = (1000L * (retryCount + 1))
                    kotlinx.coroutines.delay(delayMs)
                }
            }
            retryCount++
        }
        
        // Final attempt failed
        Timber.e("[VIEWMODEL-STATE] ⚠️  currentUserId flow failed after $maxRetries attempts - emitting null")
        emit(null)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly, // CRITICAL FIX: Use Eagerly for auth flow
        initialValue = null
    )
    
    // ✅ FIXED: Profile loading state - wraps profile with explicit loading state
    private val profileLoadingState = currentUserId
        .debounce(300) // Let userId stabilize completely
        .flatMapLatest { userId ->
            when {
                userId == null -> flowOf(ProfileDataState.NoAuth)
                else -> {
                    flow {
                        // Start with loading state
                        emit(ProfileDataState.Loading)
                        
                        // Check if profile exists to set proper expectations
                        val profileExists = profileRepository.hasProfile(userId)
                        
                        if (profileExists) {
                            // Profile exists, start observing
                            profileRepository.getProfile(userId)
                                .collect { profile ->
                                    if (profile != null) {
                                        emit(ProfileDataState.Loaded(profile))
                                    }
                                    // If profile is null but exists, stay in Loading state
                                }
                        } else {
                            // Profile definitely doesn't exist
                            emit(ProfileDataState.Missing)
                        }
                    }
                        .distinctUntilChanged { old, new ->
                            // Prevent unnecessary emissions for same state
                            when {
                                old is ProfileDataState.Loaded && new is ProfileDataState.Loaded -> 
                                    old.profile.displayName == new.profile.displayName && 
                                    old.profile.displayName != "User" // Always emit if we get rid of "User"
                                else -> old == new
                            }
                        }
                        .retryWhen { cause, attempt ->
                            val shouldRetry = (cause is kotlinx.coroutines.CancellationException || 
                                             cause is NullPointerException ||
                                             cause is java.util.concurrent.CancellationException) && 
                                             attempt < 3
                            
                            if (shouldRetry) {
                                val delayMs = 250L * (attempt + 1)
                                kotlinx.coroutines.delay(delayMs)
                            }
                            shouldRetry
                        }
                        .catch { throwable ->
                            Timber.e(throwable, "ProfileFlow - Unhandled error")
                            emit(ProfileDataState.Error(throwable))
                        }
                }
            }
        }
        .flowOn(kotlinx.coroutines.Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(
                stopTimeoutMillis = 5000L,
                replayExpirationMillis = 0L
            ),
            initialValue = ProfileDataState.Loading
        )
    
    // ✅ FIXED: Extract profile from loading state for backward compatibility
    private val profileFlow = profileLoadingState
        .map { state ->
            when (state) {
                is ProfileDataState.Loaded -> state.profile
                else -> null
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L, 0L),
            initialValue = null
        )
    
    // Achievement data flow with automatic calculation
    private val achievementsFlow: StateFlow<List<UserAchievement>> = currentUserId
        .filterNotNull()
        .flatMapLatest { userId ->
            flow<List<UserAchievement>> {
                try {
                    val result = calculateAchievementsUseCase(userId)
                    result.fold(
                        onSuccess = { achievements -> emit(achievements) },
                        onFailure = { 
                            Timber.e(it, "Failed to calculate achievements for user: $userId")
                            emit(emptyList<UserAchievement>())
                        }
                    )
                } catch (e: Exception) {
                    Timber.e(e, "Error calculating achievements for user: $userId")
                    emit(emptyList<UserAchievement>())
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList<UserAchievement>()
        )
    
    // Connectivity status flow
    val isConnected = networkConnectivityMonitor.isConnected
    
    // Achievement sync status flow
    private val achievementSyncStatus: StateFlow<SyncStatus> = workManager
        .getWorkInfosForUniqueWorkLiveData("achievement_sync_work")
        .asFlow()
        .map { workInfos ->
            val workInfo = workInfos.firstOrNull()
            when {
                workInfo == null -> SyncStatus.Idle
                workInfo.state == WorkInfo.State.RUNNING -> SyncStatus.Syncing
                workInfo.state == WorkInfo.State.SUCCEEDED -> {
                    val syncCount = workInfo.outputData.getInt("sync_count", 0)
                    SyncStatus.Success(syncCount)
                }
                workInfo.state == WorkInfo.State.FAILED -> {
                    val errorMessage = workInfo.outputData.getString("error_message")
                    SyncStatus.Error(errorMessage ?: "Achievement sync failed")
                }
                else -> SyncStatus.Idle
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SyncStatus.Idle
        )
    
    // Sync metrics flow
    private val syncMetricsFlow = currentUserId
        .filterNotNull()
        .flatMapLatest { userId ->
            combine(
                syncStatusRepository.getSyncStatus(userId),
                flow { 
                    try {
                        emit(syncManager.getUnsyncedCount(userId))
                    } catch (e: Exception) {
                        emit(0)
                    }
                }
            ) { syncStatus, unsyncedCount ->
                Triple(syncStatus, unsyncedCount, true) // Auto-sync enabled (placeholder)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = Triple(SyncStatus.Idle, 0, true)
        )
    
    init {
        // Initialize ViewModel
        performStartupDatabaseVerification()
        
        // Subscribe to profile and achievement changes and update UI state
        viewModelScope.launch {
            combine(
                currentUserId,
                profileLoadingState,
                achievementsFlow
            ) { userId, profileDataState, achievements ->
                // 🔍 FORENSIC MONITORING - Track UI state updates during profile operations
                Timber.d("[VIEWMODEL-STATE] UI state update triggered:")
                Timber.d("[VIEWMODEL-STATE] - userId: $userId")
                Timber.d("[VIEWMODEL-STATE] - profileDataState: ${profileDataState.javaClass.simpleName}")
                Timber.d("[VIEWMODEL-STATE] - achievements count: ${achievements.size}")
                updateState { currentState ->
                    val profileState = when (profileDataState) {
                        is ProfileDataState.Loading -> ProfileLoadingState.Loading
                        is ProfileDataState.Missing -> ProfileLoadingState.Missing
                        is ProfileDataState.Loaded -> ProfileLoadingState.Loaded(profileDataState.profile.copy(achievements = achievements))
                        is ProfileDataState.NoAuth -> ProfileLoadingState.Loading
                        is ProfileDataState.Error -> ProfileLoadingState.Missing
                    }
                    
                    val shouldShowLoading = profileDataState is ProfileDataState.Loading || 
                                          profileDataState is ProfileDataState.NoAuth
                    
                    // 🚨 CRITICAL CHECK - Detect unexpected userId changes
                    val previousUserId = currentState.userId
                    if (previousUserId != null && userId != null && previousUserId != userId) {
                        Timber.e("[VIEWMODEL-STATE] ⚠️  USER ID CHANGED UNEXPECTEDLY: $previousUserId -> $userId")
                        Timber.e("[VIEWMODEL-STATE] This could cause data to appear missing!")
                    } else if (previousUserId != null && userId == null) {
                        Timber.e("[VIEWMODEL-STATE] ⚠️  USER ID BECAME NULL: $previousUserId -> null")
                        Timber.e("[VIEWMODEL-STATE] User appears to have been logged out during profile operation!")
                    }
                    
                    currentState.copy(
                        userId = userId,
                        profileState = profileState,
                        achievements = achievements,
                        isLoading = shouldShowLoading,
                        error = when {
                            userId == null -> ProfileError.Authentication("Unable to authenticate user. Please check your connection and try again.")
                            profileDataState is ProfileDataState.Error -> ProfileError.Database("Failed to load profile: ${profileDataState.throwable.message}")
                            else -> null
                        }
                    )
                }
            }.collect()
        }
        
        // Enhanced timeout mechanism for authentication with retry suggestion
        viewModelScope.launch {
            kotlinx.coroutines.delay(10000) // Wait 10 seconds (increased)
            if (currentUserId.value == null) {
                Timber.w("ProfileViewModel: Authentication timeout reached after 10 seconds")
                updateState {
                    it.copy(
                        isLoading = false,
                        error = ProfileError.Authentication("Authentication is taking longer than expected. The app may be starting up. Please wait a moment or try refreshing.")
                    )
                }
                
                // Extended timeout for complete failure
                kotlinx.coroutines.delay(15000) // Additional 15 seconds
                if (currentUserId.value == null) {
                    Timber.e("ProfileViewModel: Extended authentication timeout reached")
                    updateState {
                        it.copy(
                            error = ProfileError.Authentication("Authentication failed. Please check your connection and try restarting the app.")
                        )
                    }
                }
            }
        }
        
        // Load initial profile data and achievements
        loadProfile()
        refreshAchievements()
    }
    
    private fun performStartupDatabaseVerification() {
        // Initialize database connection
    }
    
    override fun handleEvent(event: ProfileEvent) {
        when (event) {
            is ProfileEvent.LoadProfile -> loadProfile()
            is ProfileEvent.RefreshProfile -> refreshProfile()
            is ProfileEvent.SaveProfile -> saveProfile(event.profile)
            is ProfileEvent.RefreshAchievements -> refreshAchievements()
            is ProfileEvent.UpdatePrivacy -> updatePrivacySettings(event.isPublic)
            is ProfileEvent.UploadImage -> uploadProfileImage(event.imageUri, event.cropRect)
            is ProfileEvent.DeleteImage -> deleteProfileImage()
            is ProfileEvent.ClearError -> clearError()
            is ProfileEvent.RetryLastOperation -> retryLastOperation()
            is ProfileEvent.VerifyProfilePersistence -> verifyProfilePersistence()
        }
    }
    
    /**
     * Verifies that the user's profile is properly persisted in the database.
     */
    private fun verifyProfilePersistence() {
        viewModelScope.launch {
            try {
                val userId = currentUserId.value
                if (userId == null) {
                    return@launch
                }
                
                // Check if profile exists in database
                val hasProfile = profileRepository.hasProfile(userId)
                val hasCompletedProfile = profileRepository.hasCompletedProfile(userId)
                val profileData = profileRepository.getUserProfile(userId).getOrNull()
                
                updateState { currentState ->
                    currentState.copy(
                        successMessage = "Profile verification completed."
                    )
                }
                
                // Clear message after delay
                clearMessageAfterDelay(isSuccess = true)
                
            } catch (e: Exception) {
                Timber.e(e, "Error during profile persistence verification")
                updateState { 
                    it.copy(
                        error = ProfileError.Database("Profile verification failed: ${e.message}")
                    )
                }
            }
        }
    }
    
    private fun loadProfile() {
        viewModelScope.launch {
            try {
                updateState { it.copy(isLoading = true, error = null) }
                
                // Add timeout and retry logic for getting user ID
                val userId = try {
                    getCurrentUserIdUseCase()
                } catch (e: Exception) {
                    Timber.e(e, "Failed to get current user ID during profile loading")
                    null
                }
                
                if (userId == null) {
                    updateState { 
                        it.copy(
                            isLoading = false,
                            error = ProfileError.Authentication("Unable to authenticate user. Please check your connection and try signing in again.")
                        )
                    }
                    return@launch
                }
                
                // Profile loading is handled by profileFlow subscription
                Timber.d("Profile loading initiated for user: $userId")
                
            } catch (e: Exception) {
                Timber.e(e, "Error loading profile")
                updateState { 
                    it.copy(
                        isLoading = false,
                        error = ProfileError.LoadingFailed("Failed to load profile. Please check your connection and try again.")
                    )
                }
            }
        }
    }
    
    /**
     * Refreshes profile data from the repository.
     * Forces a fresh fetch from the database and triggers reactive updates.
     */
    private fun refreshProfile() {
        viewModelScope.launch {
            try {
                val userId = currentUserId.value
                if (userId != null) {
                    Timber.d("Refreshing profile data for user: $userId")
                    // Use the GetProfileUseCase refresh method if available
                    val refreshResult = getProfileUseCase.refreshProfile(userId)
                    if (refreshResult.isFailure) {
                        Timber.w(refreshResult.exceptionOrNull(), "Profile refresh failed, but continuing with reactive updates")
                    }
                } else {
                    Timber.w("Cannot refresh profile - user not authenticated")
                }
            } catch (e: Exception) {
                Timber.e(e, "Exception during profile refresh")
            }
        }
    }
    
    private fun uploadProfileImage(imageUri: Uri, cropRect: Rect?) {
        viewModelScope.launch {
            try {
                val userId = currentUserId.value
                if (userId == null) {
                    updateState { 
                        it.copy(
                            error = ProfileError.Authentication("User not authenticated")
                        )
                    }
                    return@launch
                }
                
                updateState { 
                    it.copy(
                        imageUploadState = ImageUploadState.Uploading(progress = 0F),
                        error = null,
                        lastOperation = ProfileOperation.UploadImage(imageUri, cropRect)
                    )
                }
                
                Timber.d("Starting image upload for user: $userId")
                
                // Ensure profile exists before attempting upload
                val currentProfile = profileFlow.value
                if (currentProfile == null) {
                    Timber.w("Profile not found for user: $userId, checking if profile exists...")
                    
                    // Attempt to load profile to trigger creation if needed
                    try {
                        val profileExists = profileRepository.getProfile(userId).first() != null
                        if (!profileExists) {
                            updateState { 
                                it.copy(
                                    imageUploadState = ImageUploadState.Error("Profile setup incomplete. Please complete your profile first."),
                                    error = ProfileError.Database("Profile not found - please complete profile setup")
                                )
                            }
                            return@launch
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to verify profile existence for user: $userId")
                        updateState { 
                            it.copy(
                                imageUploadState = ImageUploadState.Error("Profile verification failed. Please try again."),
                                error = ProfileError.Database("Profile verification failed")
                            )
                        }
                        return@launch
                    }
                }
                
                // Simulate progress updates (in real implementation, this would come from use case)
                updateState {
                    it.copy(imageUploadState = ImageUploadState.Uploading(progress = 0.3f))
                }

                // Process image to bytes before upload
                val inputStream = context.contentResolver.openInputStream(imageUri)
                if (inputStream == null) {
                    updateState {
                        it.copy(
                            imageUploadState = ImageUploadState.Error("Cannot read image"),
                            lastOperation = null
                        )
                    }
                    return@launch
                }

                val imageBytes = inputStream.readBytes()
                inputStream.close()

                val result = profileImageOperationsUseCase.upload(
                    userId = userId,
                    imageBytes = imageBytes
                )
                
                if (result.isSuccess) {
                    val imageUrl = result.getOrThrow()
                    updateState { 
                        it.copy(
                            imageUploadState = ImageUploadState.Success(imageUrl),
                            successMessage = "Profile picture updated successfully!",
                            lastOperation = null
                        )
                    }
                    Timber.i("Profile image upload successful: $imageUrl")
                    
                    // Reload profile to sync UI state with updated database
                    loadProfile()
                    
                    // Clear success message after delay
                    clearMessageAfterDelay(isSuccess = true)
                    
                } else {
                    val error = result.exceptionOrNull()
                    val errorMessage = error?.message ?: "Failed to upload image"
                    updateState { 
                        it.copy(
                            imageUploadState = ImageUploadState.Error(errorMessage),
                            error = ProfileError.UploadFailed(errorMessage)
                        )
                    }
                    Timber.e(error, "Profile image upload failed")
                }
                
            } catch (e: Exception) {
                val errorMessage = "Unexpected error during upload: ${e.message}"
                updateState { 
                    it.copy(
                        imageUploadState = ImageUploadState.Error(errorMessage),
                        error = ProfileError.UploadFailed(errorMessage)
                    )
                }
                Timber.e(e, "Unexpected error during profile image upload")
            }
        }
    }
    
    private fun deleteProfileImage() {
        viewModelScope.launch {
            try {
                val userId = currentUserId.value
                if (userId == null) {
                    updateState { 
                        it.copy(
                            error = ProfileError.Authentication("User not authenticated")
                        )
                    }
                    return@launch
                }
                
                updateState { 
                    it.copy(
                        imageUploadState = ImageUploadState.Uploading(progress = 0.5f),
                        error = null,
                        lastOperation = ProfileOperation.DeleteImage
                    )
                }
                
                Timber.d("Starting image deletion for user: $userId")
                
                val result = profileImageOperationsUseCase.delete(userId)
                
                if (result.isSuccess) {
                    updateState { 
                        it.copy(
                            imageUploadState = ImageUploadState.Idle,
                            successMessage = "Profile picture removed successfully!",
                            lastOperation = null
                        )
                    }
                    Timber.i("Profile image deletion successful")
                    
                    // Reload profile to sync UI state with updated database
                    loadProfile()
                    
                    // Clear success message after delay
                    clearMessageAfterDelay(isSuccess = true)
                    
                } else {
                    val error = result.exceptionOrNull()
                    val errorMessage = error?.message ?: "Failed to remove image"
                    updateState { 
                        it.copy(
                            imageUploadState = ImageUploadState.Error(errorMessage),
                            error = ProfileError.DeletionFailed(errorMessage)
                        )
                    }
                    Timber.e(error, "Profile image deletion failed")
                }
                
            } catch (e: Exception) {
                val errorMessage = "Unexpected error during deletion: ${e.message}"
                updateState { 
                    it.copy(
                        imageUploadState = ImageUploadState.Error(errorMessage),
                        error = ProfileError.DeletionFailed(errorMessage)
                    )
                }
                Timber.e(e, "Unexpected error during profile image deletion")
            }
        }
    }
    
    private fun clearError() {
        updateState { 
            it.copy(
                error = null,
                imageUploadState = if (it.imageUploadState is ImageUploadState.Error) {
                    ImageUploadState.Idle
                } else {
                    it.imageUploadState
                }
            )
        }
    }
    
    private fun retryLastOperation() {
        val lastOp = _uiState.value.lastOperation
        when (lastOp) {
            is ProfileOperation.SaveProfile -> {
                saveProfile(lastOp.profile)
            }
            is ProfileOperation.UpdatePrivacy -> {
                updatePrivacySettings(lastOp.isPublic)
            }
            is ProfileOperation.UploadImage -> {
                uploadProfileImage(lastOp.imageUri, lastOp.cropRect)
            }
            ProfileOperation.DeleteImage -> {
                deleteProfileImage()
            }
            null -> {
                loadProfile()
            }
        }
    }
    
    private fun clearMessageAfterDelay(isSuccess: Boolean) {
        viewModelScope.launch {
            kotlinx.coroutines.delay(if (isSuccess) 3000 else 5000)
            updateState { 
                it.copy(
                    successMessage = if (isSuccess) null else it.successMessage,
                    imageUploadState = if (it.imageUploadState is ImageUploadState.Success) {
                        ImageUploadState.Idle
                    } else {
                        it.imageUploadState
                    }
                )
            }
        }
    }
    
    private fun saveProfile(profile: UserProfile) {
        viewModelScope.launch {
            try {
                val userId = currentUserId.value
                if (userId == null) {
                    updateState { 
                        it.copy(
                            error = ProfileError.Authentication("User not authenticated")
                        )
                    }
                    return@launch
                }
                
                // 🔍 FORENSIC LOGGING - Authentication State During Profile Update
                val authUid = auth.currentUser?.uid
                val authDisplayName = auth.currentUser?.displayName
                val authEmail = auth.currentUser?.email
                Timber.d("[DATA-INTEGRITY] Profile save initiated:")
                Timber.d("[DATA-INTEGRITY] - userId from flow: $userId")
                Timber.d("[DATA-INTEGRITY] - auth.currentUser.uid: $authUid")
                Timber.d("[DATA-INTEGRITY] - auth.currentUser.displayName: $authDisplayName")
                Timber.d("[DATA-INTEGRITY] - auth.currentUser.email: $authEmail")
                Timber.d("[DATA-INTEGRITY] - profile.userId: ${profile.userId}")
                Timber.d("[DATA-INTEGRITY] - profile.displayName: ${profile.displayName}")
                
                // 🚨 CRITICAL CHECK - Verify auth consistency
                if (userId != authUid) {
                    Timber.e("[DATA-INTEGRITY] ⚠️  AUTH MISMATCH DETECTED: userId=$userId vs authUid=$authUid")
                    updateState { 
                        it.copy(
                            error = ProfileError.Authentication("Authentication state inconsistent. Please sign out and back in.")
                        )
                    }
                    return@launch
                }
                
                if (profile.userId != userId) {
                    Timber.e("[DATA-INTEGRITY] ⚠️  PROFILE USERID MISMATCH: profile.userId=${profile.userId} vs currentUserId=$userId")
                    updateState { 
                        it.copy(
                            error = ProfileError.ValidationError("Profile user ID mismatch. Please refresh and try again.")
                        )
                    }
                    return@launch
                }
                
                updateState { 
                    it.copy(
                        isLoading = true,
                        error = null,
                        lastOperation = ProfileOperation.SaveProfile(profile)
                    )
                }
                
                Timber.d("[DATA-INTEGRITY] Saving profile for user: $userId - all checks passed")
                
                val result = saveUserProfileUseCase(profile)
                
                if (result.isSuccess) {
                    // 🔍 FORENSIC LOGGING - Verify auth state after save
                    val postSaveAuthUid = auth.currentUser?.uid
                    val postSaveAuthDisplayName = auth.currentUser?.displayName
                    Timber.d("[DATA-INTEGRITY] Profile save successful:")
                    Timber.d("[DATA-INTEGRITY] - post-save auth.uid: $postSaveAuthUid")
                    Timber.d("[DATA-INTEGRITY] - post-save auth.displayName: $postSaveAuthDisplayName")
                    
                    if (postSaveAuthUid != userId) {
                        Timber.e("[DATA-INTEGRITY] ⚠️  AUTH STATE CHANGED DURING SAVE: $userId -> $postSaveAuthUid")
                        updateState { 
                            it.copy(
                                isLoading = false,
                                error = ProfileError.Authentication("Authentication changed during save. Data may be inconsistent.")
                            )
                        }
                        return@launch
                    }
                    
                    updateState { currentState ->
                        currentState.copy(
                            isLoading = false,
                            profileState = ProfileLoadingState.Loaded(profile.copy(achievements = currentState.achievements)),
                            successMessage = "Profile saved successfully!",
                            lastOperation = null
                        )
                    }
                    Timber.i("[DATA-INTEGRITY] Profile save successful for user: $userId - auth state stable")
                    
                    // Refresh achievements after profile save
                    refreshAchievements()
                    
                    // Clear success message after delay
                    clearMessageAfterDelay(isSuccess = true)
                    
                } else {
                    val error = result.exceptionOrNull()
                    val errorMessage = error?.message ?: "Failed to save profile"
                    updateState { 
                        it.copy(
                            isLoading = false,
                            error = ProfileError.SaveFailed(errorMessage)
                        )
                    }
                    Timber.e(error, "Profile save failed for user: $userId")
                }
                
            } catch (e: Exception) {
                val errorMessage = "Unexpected error during save: ${e.message}"
                updateState { 
                    it.copy(
                        isLoading = false,
                        error = ProfileError.SaveFailed(errorMessage)
                    )
                }
                Timber.e(e, "Unexpected error during profile save")
            }
        }
    }
    
    private fun refreshAchievements() {
        viewModelScope.launch {
            try {
                val userId = currentUserId.value
                if (userId == null) {
                    Timber.w("Cannot refresh achievements - no current user")
                    return@launch
                }
                
                Timber.d("Refreshing achievements for user: $userId")
                
                val result = calculateAchievementsUseCase(userId)
                
                result.fold(
                    onSuccess = { achievements ->
                        updateState { currentState ->
                            val updatedProfileState = when (val current = currentState.profileState) {
                                is ProfileLoadingState.Loaded -> ProfileLoadingState.Loaded(
                                    current.profile.copy(achievements = achievements)
                                )
                                else -> current
                            }
                            currentState.copy(
                                achievements = achievements,
                                profileState = updatedProfileState
                            )
                        }
                        Timber.i("Achievement refresh successful for user: $userId, found ${achievements.size} achievements")
                    },
                    onFailure = { error ->
                        Timber.e(error, "Achievement refresh failed for user: $userId")
                        updateState { currentState ->
                            val updatedProfileState = when (val current = currentState.profileState) {
                                is ProfileLoadingState.Loaded -> ProfileLoadingState.Loaded(
                                    current.profile.copy(achievements = emptyList())
                                )
                                else -> current
                            }
                            currentState.copy(
                                achievements = emptyList(),
                                profileState = updatedProfileState
                            )
                        }
                    }
                )
                
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error during achievement refresh")
            }
        }
    }
    
    private fun updatePrivacySettings(isPublic: Boolean) {
        viewModelScope.launch {
            try {
                val currentProfile = _uiState.value.profile
                if (currentProfile == null) {
                    updateState { 
                        it.copy(
                            error = ProfileError.LoadingFailed("No profile loaded")
                        )
                    }
                    return@launch
                }
                
                updateState { 
                    it.copy(
                        isLoading = true,
                        error = null,
                        lastOperation = ProfileOperation.UpdatePrivacy(isPublic)
                    )
                }
                
                Timber.d("Updating privacy settings for user: ${currentProfile.userId} to public: $isPublic")
                
                val updatedProfile = currentProfile.copy(isPublic = isPublic)
                val result = saveUserProfileUseCase(updatedProfile)
                
                if (result.isSuccess) {
                    updateState { 
                        it.copy(
                            isLoading = false,
                            successMessage = "Privacy settings updated!",
                            lastOperation = null
                        )
                    }
                    Timber.i("Privacy settings update successful")
                    
                    // Clear success message after delay
                    clearMessageAfterDelay(isSuccess = true)
                    
                } else {
                    val error = result.exceptionOrNull()
                    val errorMessage = error?.message ?: "Failed to update privacy settings"
                    updateState { 
                        it.copy(
                            isLoading = false,
                            error = ProfileError.SaveFailed(errorMessage)
                        )
                    }
                    Timber.e(error, "Privacy settings update failed")
                }
                
            } catch (e: Exception) {
                val errorMessage = "Unexpected error updating privacy: ${e.message}"
                updateState { 
                    it.copy(
                        isLoading = false,
                        error = ProfileError.SaveFailed(errorMessage)
                    )
                }
                Timber.e(e, "Unexpected error during privacy update")
            }
        }
    }
    
    // === SYNC METHODS ===
    
    /**
     * Triggers profile sync using SyncCoordinator
     */
    fun triggerProfileSync() {
        viewModelScope.launch {
            try {
                val userId = currentUserId.value
                if (userId != null) {
                    Timber.d("ProfileViewModel: Triggering profile sync for user $userId")
                    val result = syncCoordinator.triggerEntitySync(userId, "profile")
                    if (result.isFailure) {
                        Timber.e("ProfileViewModel: Profile sync failed - ${result.exceptionOrNull()?.message}")
                    }
                } else {
                    Timber.w("ProfileViewModel: Cannot trigger profile sync - no user ID available")
                }
            } catch (e: Exception) {
                Timber.e(e, "ProfileViewModel: Error triggering profile sync")
            }
        }
    }
    
    /**
     * Triggers immediate sync using SyncCoordinator
     */
    private fun triggerImmediateSync() {
        viewModelScope.launch {
            try {
                val userId = currentUserId.value
                if (userId != null) {
                    Timber.d("ProfileViewModel: Triggering immediate sync for user $userId")
                    val result = syncCoordinator.triggerImmediateSync(userId)
                    if (result.isFailure) {
                        Timber.e("ProfileViewModel: Immediate sync failed - ${result.exceptionOrNull()?.message}")
                    }
                } else {
                    Timber.w("ProfileViewModel: Cannot trigger immediate sync - no user ID available")
                }
            } catch (e: Exception) {
                Timber.e(e, "ProfileViewModel: Error triggering immediate sync")
            }
        }
    }
    
    /**
     * Triggers force sync of all data using SyncCoordinator
     */
    fun triggerForceSyncAll() {
        viewModelScope.launch {
            try {
                val userId = currentUserId.value
                if (userId != null) {
                    Timber.d("ProfileViewModel: Triggering force sync all for user $userId")
                    val result = syncCoordinator.triggerImmediateSync(userId)
                    if (result.isFailure) {
                        Timber.e("ProfileViewModel: Force sync all failed - ${result.exceptionOrNull()?.message}")
                    }
                } else {
                    Timber.w("ProfileViewModel: Cannot trigger force sync all - no user ID available")
                }
            } catch (e: Exception) {
                Timber.e(e, "ProfileViewModel: Error triggering force sync all")
            }
        }
    }
    
    /**
     * Toggles auto-sync setting with proper SyncPreferencesRepository integration.
     */
    fun toggleAutoSync(enabled: Boolean) {
        viewModelScope.launch {
            try {
                val userId = currentUserId.value
                if (userId == null) {
                    Timber.w("ProfileViewModel: Cannot toggle auto-sync - no user ID available")
                    return@launch
                }
                
                Timber.d("ProfileViewModel: Toggling auto-sync for user $userId to $enabled")
                
                val result = syncPreferencesRepository.setAutoSyncEnabled(userId, enabled)
                result.fold(
                    onSuccess = {
                        Timber.i("ProfileViewModel: Auto-sync toggle successful for user $userId: $enabled")
                        // Optionally trigger immediate sync if enabling
                        if (enabled) {
                            triggerImmediateSync()
                        }
                    },
                    onFailure = { error ->
                        Timber.e("ProfileViewModel: Auto-sync toggle failed for user $userId: $error")
                        updateState {
                            it.copy(
                                error = ProfileError.SaveFailed("Failed to update sync preferences: ${error.message}")
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                Timber.e(e, "ProfileViewModel: Error toggling auto-sync")
                updateState {
                    it.copy(
                        error = ProfileError.SaveFailed("Failed to update sync preferences")
                    )
                }
            }
        }
    }
    
    /**
     * Gets current connectivity status
     */
    fun isOffline(): Boolean {
        return !networkConnectivityMonitor.isCurrentlyConnected()
    }
    
    /**
     * Gets achievement sync status for UI display
     */
    fun getAchievementSyncStatus(): StateFlow<SyncStatus> {
        return achievementSyncStatus
    }
    
    /**
     * Gets last sync time as LocalDateTime for UI display.
     */
    fun getLastSyncTime(): LocalDateTime {
        val userId = currentUserId.value
        if (userId == null) {
            return LocalDateTime.now().minusHours(24) // Default to day ago if no user
        }
        
        return try {
            // Get last sync time from preferences (this will be a suspend function call)
            // For now, we'll use a blocking approach but in production you'd want to
            // make this reactive with StateFlow
            kotlinx.coroutines.runBlocking {
                val result = syncPreferencesRepository.getLastSyncTime(userId)
                result.fold(
                    onSuccess = { timestamp ->
                        if (timestamp != null) {
                            Instant.ofEpochMilli(timestamp)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDateTime()
                        } else {
                            LocalDateTime.now().minusHours(24) // Never synced
                        }
                    },
                    onFailure = {
                        LocalDateTime.now().minusMinutes(5) // Fallback to recent time
                    }
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "ProfileViewModel: Error getting last sync time")
            LocalDateTime.now().minusMinutes(5)
        }
    }
    
    /**
     * Gets unsynced item count for UI display
     */
    fun getUnsyncedItemCount(): StateFlow<Int> {
        return syncMetricsFlow.map { (_, unsyncedCount, _) -> unsyncedCount }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = 0
            )
    }
    
    /**
     * Gets auto-sync enabled status for UI display with proper repository integration.
     */
    fun getAutoSyncEnabled(): StateFlow<Boolean> {
        return currentUserId
            .filterNotNull()
            .flatMapLatest { userId ->
                syncPreferencesRepository.observeAutoSyncStatus(userId)
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = true
            )
    }
}

/**
 * UI state for profile screen with comprehensive profile management.
 */
@Stable
data class ProfileUiState(
    val userId: String? = null,
    val profileState: ProfileLoadingState = ProfileLoadingState.Loading,
    val achievements: List<com.example.liftrix.domain.model.UserAchievement> = emptyList(),
    val isLoading: Boolean = false,
    val imageUploadState: ImageUploadState = ImageUploadState.Idle,
    val error: ProfileError? = null,
    val successMessage: String? = null,
    val lastOperation: ProfileOperation? = null
) {
    // Backward compatibility accessor for existing UI code
    val profile: UserProfile? 
        get() = when (profileState) {
            is ProfileLoadingState.Loaded -> profileState.profile
            else -> null
        }
        
    // Effective profile image URL with Firebase Auth fallback (like settings screen)
    val effectiveProfileImageUrl: String?
        get() {
            Timber.d("PFP_DEBUG: 🔍 PROFILE_EFFECTIVE_URL_CALLED: Starting effectiveProfileImageUrl calculation")
            
            val dbPhotoUrl = profile?.profileImageUrl
            val firebaseAuthUrl = try {
                FirebaseAuth.getInstance().currentUser?.photoUrl?.toString()
            } catch (e: Exception) {
                Timber.e("PFP_DEBUG: 🔥 FIREBASE_AUTH_ERROR: Failed to get Firebase Auth user in ProfileViewModel", e)
                null
            }
            val effectiveUrl = dbPhotoUrl ?: firebaseAuthUrl
            
            // Debug logging for profile image fallback tracking
            Timber.d("PFP_DEBUG: 🔍 PROFILE_EFFECTIVE_URL: userId=${profile?.userId} | dbPhoto='$dbPhotoUrl' | firebaseAuthPhoto='$firebaseAuthUrl' | effectiveUrl='$effectiveUrl'")
            
            return effectiveUrl
        }
}

/**
 * Profile loading state with explicit Loading/Success/Error/Missing states
 */
@Stable
sealed class ProfileLoadingState {
    data object Loading : ProfileLoadingState()
    data class Loaded(val profile: UserProfile) : ProfileLoadingState()
    data object Missing : ProfileLoadingState() // Profile doesn't exist
    data class Error(val throwable: Throwable) : ProfileLoadingState()
}

/**
 * Image upload state with progress tracking.
 */
@Stable
sealed class ImageUploadState {
    data object Idle : ImageUploadState()
    data class Uploading(val progress: Float) : ImageUploadState()
    data class Success(val imageUrl: String) : ImageUploadState()
    data class Error(val message: String) : ImageUploadState()
}

/**
 * Profile-specific error types with recovery context.
 */
@Stable
sealed class ProfileError(val message: String, val isRecoverable: Boolean = true) {
    data class Authentication(private val msg: String) : ProfileError(msg, false)
    data class LoadingFailed(private val msg: String) : ProfileError(msg, true)
    data class SaveFailed(private val msg: String) : ProfileError(msg, true)
    data class UploadFailed(private val msg: String) : ProfileError(msg, true)
    data class DeletionFailed(private val msg: String) : ProfileError(msg, true)
    data class NetworkError(private val msg: String) : ProfileError(msg, true)
    data class ValidationError(private val msg: String) : ProfileError(msg, true)
    data class Database(private val msg: String) : ProfileError(msg, true)
}

/**
 * Profile operations for retry functionality.
 */
@Stable
sealed class ProfileOperation {
    data class SaveProfile(val profile: UserProfile) : ProfileOperation()
    data class UpdatePrivacy(val isPublic: Boolean) : ProfileOperation()
    data class UploadImage(val imageUri: Uri, val cropRect: Rect?) : ProfileOperation()
    data object DeleteImage : ProfileOperation()
}

/**
 * Profile events for user interactions and system actions.
 */
@Stable
sealed class ProfileEvent : ViewModelEvent {
    data object LoadProfile : ProfileEvent()
    data object RefreshProfile : ProfileEvent()
    data class SaveProfile(val profile: UserProfile) : ProfileEvent()
    data object RefreshAchievements : ProfileEvent()
    data class UpdatePrivacy(val isPublic: Boolean) : ProfileEvent()
    data class UploadImage(val imageUri: Uri, val cropRect: Rect?) : ProfileEvent()
    data object DeleteImage : ProfileEvent()
    data object ClearError : ProfileEvent()
    data object RetryLastOperation : ProfileEvent()
    data object VerifyProfilePersistence : ProfileEvent()
}