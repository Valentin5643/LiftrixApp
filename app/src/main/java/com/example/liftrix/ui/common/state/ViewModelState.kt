package com.example.liftrix.ui.common.state

import com.example.liftrix.domain.model.Workout
import com.example.liftrix.domain.model.WorkoutTemplate
import com.example.liftrix.domain.model.WorkoutTemplatePreview
import com.example.liftrix.domain.model.Folder
import com.example.liftrix.sync.SyncStatus

/**
 * Screen-specific sealed class hierarchies for type-safe ViewModel state management.
 * 
 * This file provides screen-specific UiState sealed classes that extend the base UiState<T>
 * pattern, enabling exhaustive when expressions and enhanced type safety.
 * 
 * Benefits:
 * - Type-safe state management with exhaustive when expressions
 * - Enhanced IDE support and autocomplete for screen-specific states
 * - Screen-specific state extensions and customizations
 * - Better error handling with LiftrixError integration
 * 
 * Usage:
 * ```kotlin
 * class MyViewModel : BaseViewModel<MyScreenUiState, MyEvent> {
 *     override val _uiState = MutableStateFlow<MyScreenUiState>(MyScreenUiState.Loading)
 * }
 * ```
 */

/**
 * Data class for workout screen state following the sealed class pattern.
 * Contains all business data for the workout dashboard screen.
 * 
 * @property workouts List of user's workouts
 * @property templates List of workout templates available to the user
 * @property folders List of folders for organizing templates
 * @property syncStatus Current synchronization status with remote services
 * @property unsyncedCount Number of unsynced workouts pending upload
 * @property templatePreview Enhanced template preview for workout creation flow
 */
data class WorkoutScreenData(
    val workouts: List<Workout> = emptyList(),
    val templates: List<WorkoutTemplate> = emptyList(),
    val folders: List<Folder> = emptyList(),
    val selectedFolderId: String? = null, // 🔥 NEW: Track selected folder for filtering
    val syncStatus: SyncStatus = SyncStatus.Idle,
    val unsyncedCount: Int = 0,
    val templatePreview: WorkoutTemplatePreview? = null
) {
    /**
     * Indicates whether the screen has content to display
     */
    val hasContent: Boolean
        get() = workouts.isNotEmpty() || templates.isNotEmpty()
    
    /**
     * Indicates whether sync operations are active
     */
    val isSyncing: Boolean
        get() = syncStatus is SyncStatus.Syncing
    
    /**
     * Gets user-friendly sync status message
     */
    val syncStatusMessage: String?
        get() = when (syncStatus) {
            is SyncStatus.Success -> "Synced successfully"
            is SyncStatus.Error -> "Sync failed: ${syncStatus.message}"
            is SyncStatus.Syncing -> "Syncing..."
            else -> null
        }
}

/**
 * Data class for home screen state following the sealed class pattern.
 * Contains all business data for the home dashboard screen.
 * 
 * @property recentWorkouts List of recent workouts for display
 * @property workoutFeedState State of the workout feed with pagination
 * @property recommendationsState State of user recommendations with pagination
 * @property showEndOfFeedMessage Whether to show end of feed indicator
 * @property isRefreshing Whether global refresh is in progress
 */
data class HomeScreenData(
    val recentWorkouts: List<Workout> = emptyList(),
    val workoutFeedState: FeedState = FeedState.Loading,
    val recommendationsState: RecommendationsState = RecommendationsState.Loading,
    val showEndOfFeedMessage: Boolean = false,
    val isRefreshing: Boolean = false
) {
    /**
     * Indicates whether the screen should show empty state
     */
    val shouldShowEmptyState: Boolean
        get() = recentWorkouts.isEmpty() && 
                !isRefreshing &&
                workoutFeedState is FeedState.Success && 
                !workoutFeedState.hasData

    /**
     * Indicates whether the screen should show content
     */
    val shouldShowContent: Boolean
        get() = recentWorkouts.isNotEmpty() || 
                (workoutFeedState is FeedState.Success && workoutFeedState.hasData)

    /**
     * Indicates if any part of the home screen is loading
     */
    val isAnyLoading: Boolean
        get() = isRefreshing || 
                workoutFeedState is FeedState.Loading || 
                recommendationsState is RecommendationsState.Loading
}

/**
 * Sealed class representing the state of the workout feed.
 * Maintains compatibility with existing HomeViewModel implementation.
 */
sealed class FeedState {
    object Loading : FeedState()
    
    data class Success(
        val workouts: List<com.example.liftrix.domain.model.FeedWorkout>,
        val hasMore: Boolean,
        val isLoadingMore: Boolean = false
    ) : FeedState() {
        val hasData: Boolean get() = workouts.isNotEmpty()
        val workoutCount: Int get() = workouts.size
    }
    
    data class Error(val message: String) : FeedState()
}

/**
 * Sealed class representing the state of user recommendations.
 * Maintains compatibility with existing HomeViewModel implementation.
 */
sealed class RecommendationsState {
    object Loading : RecommendationsState()
    
    data class Success(
        val users: List<com.example.liftrix.domain.model.RecommendedUser>,
        val hasMore: Boolean,
        val isLoadingMore: Boolean = false
    ) : RecommendationsState() {
        val hasData: Boolean get() = users.isNotEmpty()
        val userCount: Int get() = users.size
        val isCacheValid: Boolean get() = users.all { it.isCacheValid }
    }
    
    data class Error(val message: String) : RecommendationsState()
}

// =============================================================================
// SCREEN-SPECIFIC SEALED CLASS HIERARCHIES
// =============================================================================

/**
 * Sealed class hierarchy for WorkoutScreen state management.
 * Provides type-safe handling of all possible states for the workout dashboard.
 */
sealed class WorkoutUiState : UiState<WorkoutScreenData>() {
    /**
     * Initial loading state when workout data is being fetched.
     */
    object Loading : WorkoutUiState()
    
    /**
     * Successful state containing workout screen data.
     * 
     * @param data The workout screen data including workouts, templates, and sync status
     * @param isRefreshing Whether a refresh operation is in progress
     * @param timestamp When this state was created for cache validation
     */
    data class Success(
        val data: WorkoutScreenData,
        val isRefreshing: Boolean = false,
        val timestamp: Long = System.currentTimeMillis()
    ) : WorkoutUiState()
    
    /**
     * Error state with workout-specific error information.
     * 
     * @param error The LiftrixError containing error details and recovery information
     * @param previousData Previous workout data for graceful degradation
     */
    data class Error(
        val error: com.example.liftrix.domain.model.error.LiftrixError,
        val previousData: WorkoutScreenData? = null
    ) : WorkoutUiState()
    
    /**
     * Empty state when user has no workouts or templates.
     * Displays onboarding UI with workout creation prompts.
     */
    object Empty : WorkoutUiState()
}

/**
 * Sealed class hierarchy for HomeScreen state management.
 * Provides type-safe handling of all possible states for the home dashboard.
 */
sealed class HomeUiState : UiState<HomeScreenData>() {
    /**
     * Initial loading state when home data is being fetched.
     */
    object Loading : HomeUiState()
    
    /**
     * Successful state containing home screen data.
     * 
     * @param data The home screen data including recent workouts and feed state
     * @param isRefreshing Whether a refresh operation is in progress
     * @param timestamp When this state was created for cache validation
     */
    data class Success(
        val data: HomeScreenData,
        val isRefreshing: Boolean = false,
        val timestamp: Long = System.currentTimeMillis()
    ) : HomeUiState()
    
    /**
     * Error state with home-specific error information.
     * 
     * @param error The LiftrixError containing error details and recovery information
     * @param previousData Previous home data for graceful degradation
     */
    data class Error(
        val error: com.example.liftrix.domain.model.error.LiftrixError,
        val previousData: HomeScreenData? = null
    ) : HomeUiState()
    
    /**
     * Empty state when user has no recent workouts or feed data.
     * Displays onboarding UI with workout creation prompts.
     */
    object Empty : HomeUiState()
}

/**
 * Data class for workout template creation state.
 * Contains all business data for creating and editing workout templates.
 * 🔥 FIXED: Added template metadata to prevent data loss during state transitions
 */
data class WorkoutTemplateCreationData(
    val exercises: List<com.example.liftrix.domain.model.TemplateExercise> = emptyList(),
    val availableExercises: List<com.example.liftrix.domain.usecase.exercise.SearchableExercise> = emptyList(),
    val exerciseSearchQuery: String = "",
    val selectedExercise: com.example.liftrix.domain.usecase.exercise.SearchableExercise? = null,
    val isExerciseSelectorExpanded: Boolean = false,
    val template: com.example.liftrix.domain.model.WorkoutTemplate? = null,
    val availableFolders: List<com.example.liftrix.domain.model.Folder> = emptyList(),
    val selectedFolderId: com.example.liftrix.domain.model.FolderId? = null,
    val defaultFolderId: com.example.liftrix.domain.model.FolderId? = null,
    // 🔥 NEW: Template metadata in ViewModel state to prevent loss
    val templateName: String = "",
    val templateDescription: String = "",
    val targetFolderId: com.example.liftrix.domain.model.FolderId? = null // 🔥 NEW: Explicit target folder context
) {
    /**
     * Indicates whether the template has exercises
     */
    val hasExercises: Boolean
        get() = exercises.isNotEmpty()
    
    /**
     * Gets the total number of exercises in the template
     */
    val exerciseCount: Int
        get() = exercises.size
    
    /**
     * Gets the selected folder or null if none selected
     */
    val selectedFolder: com.example.liftrix.domain.model.Folder?
        get() = selectedFolderId?.let { folderId ->
            availableFolders.firstOrNull { it.id == folderId }
        }
    
    /**
     * Gets the default folder or null if not available
     */
    val defaultFolder: com.example.liftrix.domain.model.Folder?
        get() = defaultFolderId?.let { folderId ->
            availableFolders.firstOrNull { it.id == folderId }
        }
    
    /**
     * Gets the folder that will be used for template creation (target -> selected -> default)
     * 🔥 ENHANCED: Added targetFolderId priority for explicit folder context
     */
    val effectiveFolderId: com.example.liftrix.domain.model.FolderId?
        get() = targetFolderId ?: selectedFolderId ?: defaultFolderId
    
    /**
     * Gets the display name of the selected folder or a default message
     */
    val folderDisplayName: String
        get() = when {
            selectedFolder != null -> selectedFolder!!.name.value
            defaultFolder != null -> defaultFolder!!.name.value
            else -> "Select Folder"
        }
    
    /**
     * Indicates whether the template creation is valid
     * 🔥 FIXED: Allow creation with just name and folder (exercises can be added later)
     */
    fun isValidForCreation(name: String = templateName): Boolean {
        return name.isNotBlank() && 
               name.length <= 50 && // Reasonable template name limit
               effectiveFolderId != null // Only require name and folder
        // Note: exercises check removed - users can create empty templates and add exercises later
    }
    
    /**
     * 🔥 NEW: Indicates whether template data is valid using internal state
     */
    val isValidForCreation: Boolean
        get() = isValidForCreation(templateName)
}

/**
 * Sealed class hierarchy for WorkoutTemplateCreationScreen state management.
 * Provides type-safe handling of all possible states during workout creation.
 */
sealed class WorkoutTemplateCreationUiState : UiState<WorkoutTemplateCreationData>() {
    /**
     * Initial loading state when creation screen is being initialized.
     */
    object Loading : WorkoutTemplateCreationUiState()
    
    /**
     * Successful state containing creation screen data.
     * 
     * @param data The workout creation data including exercises and search state
     * @param isRefreshing Whether a refresh operation is in progress
     * @param timestamp When this state was created for cache validation
     */
    data class Success(
        val data: WorkoutTemplateCreationData,
        val isRefreshing: Boolean = false,
        val timestamp: Long = System.currentTimeMillis()
    ) : WorkoutTemplateCreationUiState()
    
    /**
     * Error state with creation-specific error information.
     * 
     * @param error The LiftrixError containing error details and recovery information
     * @param previousData Previous creation data for graceful degradation
     */
    data class Error(
        val error: com.example.liftrix.domain.model.error.LiftrixError,
        val previousData: WorkoutTemplateCreationData? = null
    ) : WorkoutTemplateCreationUiState()
    
    /**
     * Empty state for initial template creation.
     * Displays empty form with exercise selection prompts.
     */
    object Empty : WorkoutTemplateCreationUiState()
}

/**
 * Extension function to properly extract data from WorkoutTemplateCreationUiState
 * Fixes the dataOrNull() issue with nested sealed class hierarchy
 */
fun WorkoutTemplateCreationUiState.dataOrNull(): WorkoutTemplateCreationData? = when (this) {
    is WorkoutTemplateCreationUiState.Success -> data
    is WorkoutTemplateCreationUiState.Error -> previousData
    WorkoutTemplateCreationUiState.Loading -> null
    WorkoutTemplateCreationUiState.Empty -> null
}

/**
 * Data class for workout editing state.
 * Contains all business data for editing existing workout templates and sessions.
 */
data class EditWorkoutData(
    val originalWorkout: com.example.liftrix.domain.model.Workout? = null,
    val editedWorkout: com.example.liftrix.domain.model.Workout? = null,
    val editedName: String = "",
    val editedDescription: String = "",
    val editedExercises: List<com.example.liftrix.domain.model.Exercise> = emptyList(),
    val lastModified: java.time.Instant? = null,
    val validationErrors: List<String> = emptyList()
) {
    /**
     * Indicates whether there are changes from the original workout
     */
    val hasChanges: Boolean
        get() = originalWorkout != null && (
                originalWorkout.name != editedName ||
                (originalWorkout.notes ?: "") != editedDescription ||
                originalWorkout.exercises != editedExercises
        )
    
    /**
     * Indicates whether the current edit state is valid for saving
     */
    val canSave: Boolean
        get() = editedName.isNotBlank() && 
                editedName.length <= 50 && 
                editedExercises.isNotEmpty() &&
                validationErrors.isEmpty() &&
                hasChanges
    
    /**
     * Gets user-friendly display of last modified time
     */
    fun getLastModifiedDisplay(): String? {
        return lastModified?.let { instant ->
            val now = java.time.Instant.now()
            val duration = java.time.Duration.between(instant, now)
            when {
                duration.toMinutes() < 1 -> "Just now"
                duration.toHours() < 1 -> "${duration.toMinutes()}m ago"
                duration.toDays() < 1 -> "${duration.toHours()}h ago"
                else -> "${duration.toDays()}d ago"
            }
        }
    }
}

/**
 * Sealed class hierarchy for EditWorkoutScreen state management.
 * Provides type-safe handling of all possible states during workout editing.
 */
sealed class EditWorkoutUiState : UiState<EditWorkoutData>() {
    /**
     * Initial loading state when editing screen is loading workout data.
     */
    object Loading : EditWorkoutUiState()
    
    /**
     * Successful state containing editing screen data.
     * 
     * @param data The workout editing data including original and modified workout
     * @param isRefreshing Whether a refresh operation is in progress
     * @param timestamp When this state was created for cache validation
     */
    data class Success(
        val data: EditWorkoutData,
        val isRefreshing: Boolean = false,
        val timestamp: Long = System.currentTimeMillis()
    ) : EditWorkoutUiState()
    
    /**
     * Error state with editing-specific error information.
     * 
     * @param error The LiftrixError containing error details and recovery information
     * @param previousData Previous editing data for graceful degradation
     */
    data class Error(
        val error: com.example.liftrix.domain.model.error.LiftrixError,
        val previousData: EditWorkoutData? = null
    ) : EditWorkoutUiState()
    
    /**
     * Empty state when no workout is loaded for editing.
     * Displays error message with navigation back to workout list.
     */
    object Empty : EditWorkoutUiState()
}

/**
 * Data class for edit session screen state.
 * Contains all business data for editing historical workout sessions.
 */
data class EditSessionData(
    val session: com.example.liftrix.domain.model.UnifiedWorkoutSession,
    val hasChanges: Boolean = false
)

/**
 * Sealed class hierarchy for Edit Session screen UI states.
 * Provides type-safe state management for session editing workflow.
 */
sealed class EditSessionUiState : UiState<EditSessionData>() {
    /**
     * Initial loading state when session data is being fetched.
     */
    object Loading : EditSessionUiState()
    
    /**
     * Successful state containing session editing data.
     * 
     * @param data The session editing data including original session and change tracking
     * @param isRefreshing Whether a refresh operation is in progress
     * @param timestamp When this state was created for cache validation
     */
    data class Success(
        val data: EditSessionData,
        val isRefreshing: Boolean = false,
        val timestamp: Long = System.currentTimeMillis()
    ) : EditSessionUiState()
    
    /**
     * Error state with editing-specific error information.
     * 
     * @param error The LiftrixError containing error details and recovery information
     * @param previousData Previous editing data for graceful degradation
     */
    data class Error(
        val error: com.example.liftrix.domain.model.error.LiftrixError,
        val previousData: EditSessionData? = null
    ) : EditSessionUiState()
    
    /**
     * Empty state when no session is loaded for editing.
     * Displays error message with navigation back to session list.
     */
    object Empty : EditSessionUiState()
}