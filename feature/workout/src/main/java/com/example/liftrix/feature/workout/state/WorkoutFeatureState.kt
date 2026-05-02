package com.example.liftrix.feature.workout.state

import com.example.liftrix.domain.model.Folder
import com.example.liftrix.domain.model.Workout
import com.example.liftrix.domain.model.WorkoutTemplate
import com.example.liftrix.domain.model.WorkoutTemplatePreview
import com.example.liftrix.domain.service.SyncStatus

data class WorkoutScreenData(
    val workouts: List<Workout> = emptyList(),
    val templates: List<WorkoutTemplate> = emptyList(),
    val folders: List<Folder> = emptyList(),
    val selectedFolderId: String? = null,
    val syncStatus: SyncStatus = SyncStatus.Idle,
    val unsyncedCount: Int = 0,
    val templatePreview: WorkoutTemplatePreview? = null
) {
    val hasContent: Boolean
        get() = workouts.isNotEmpty() || templates.isNotEmpty()

    val isSyncing: Boolean
        get() = syncStatus is SyncStatus.Syncing

    val syncStatusMessage: String?
        get() = when (syncStatus) {
            is SyncStatus.Success -> "Synced successfully"
            is SyncStatus.Error -> "Sync failed: ${syncStatus.message}"
            is SyncStatus.Syncing -> "Syncing..."
            else -> null
        }
}

sealed class WorkoutUiState {
    data object Loading : WorkoutUiState()
    data class Success(
        val data: WorkoutScreenData,
        val isRefreshing: Boolean = false,
        val timestamp: Long = System.currentTimeMillis()
    ) : WorkoutUiState()
    data class Error(
        val error: com.example.liftrix.domain.model.error.LiftrixError,
        val previousData: WorkoutScreenData? = null
    ) : WorkoutUiState()
    data object Empty : WorkoutUiState()
}

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
    val templateName: String = "",
    val templateDescription: String = "",
    val targetFolderId: com.example.liftrix.domain.model.FolderId? = null
) {
    val hasExercises: Boolean
        get() = exercises.isNotEmpty()
    val exerciseCount: Int
        get() = exercises.size
    val selectedFolder: com.example.liftrix.domain.model.Folder?
        get() = selectedFolderId?.let { id -> availableFolders.firstOrNull { it.id == id } }
    val defaultFolder: com.example.liftrix.domain.model.Folder?
        get() = defaultFolderId?.let { id -> availableFolders.firstOrNull { it.id == id } }
    val effectiveFolderId: com.example.liftrix.domain.model.FolderId?
        get() = targetFolderId ?: selectedFolderId ?: defaultFolderId
    val folderDisplayName: String
        get() = selectedFolder?.name?.value ?: defaultFolder?.name?.value ?: "Select Folder"
    val isValidForCreation: Boolean
        get() = isValidForCreation(templateName)

    fun isValidForCreation(name: String = templateName): Boolean =
        name.isNotBlank() && name.length <= 50 && effectiveFolderId != null
}

sealed class WorkoutTemplateCreationUiState {
    data object Loading : WorkoutTemplateCreationUiState()
    data class Success(
        val data: WorkoutTemplateCreationData,
        val isRefreshing: Boolean = false,
        val timestamp: Long = System.currentTimeMillis()
    ) : WorkoutTemplateCreationUiState()
    data class Error(
        val error: com.example.liftrix.domain.model.error.LiftrixError,
        val previousData: WorkoutTemplateCreationData? = null
    ) : WorkoutTemplateCreationUiState()
    data object Empty : WorkoutTemplateCreationUiState()
}

fun WorkoutTemplateCreationUiState.dataOrNull(): WorkoutTemplateCreationData? = when (this) {
    is WorkoutTemplateCreationUiState.Success -> data
    is WorkoutTemplateCreationUiState.Error -> previousData
    WorkoutTemplateCreationUiState.Loading -> null
    WorkoutTemplateCreationUiState.Empty -> null
}

data class EditWorkoutData(
    val originalWorkout: Workout? = null,
    val editedWorkout: Workout? = null,
    val editedName: String = "",
    val editedDescription: String = "",
    val editedExercises: List<com.example.liftrix.domain.model.Exercise> = emptyList(),
    val lastModified: java.time.Instant? = null,
    val validationErrors: List<String> = emptyList()
) {
    val hasChanges: Boolean
        get() = originalWorkout != null && (
            originalWorkout.name != editedName ||
                (originalWorkout.notes ?: "") != editedDescription ||
                originalWorkout.exercises != editedExercises
            )
    val canSave: Boolean
        get() = editedName.isNotBlank() &&
            editedName.length <= 50 &&
            editedExercises.isNotEmpty() &&
            validationErrors.isEmpty() &&
            hasChanges

    fun getLastModifiedDisplay(): String? {
        return lastModified?.let { instant ->
            val duration = java.time.Duration.between(instant, java.time.Instant.now())
            when {
                duration.toMinutes() < 1 -> "Just now"
                duration.toHours() < 1 -> "${duration.toMinutes()}m ago"
                duration.toDays() < 1 -> "${duration.toHours()}h ago"
                else -> "${duration.toDays()}d ago"
            }
        }
    }
}

sealed class EditWorkoutUiState {
    data object Loading : EditWorkoutUiState()
    data class Success(
        val data: EditWorkoutData,
        val isRefreshing: Boolean = false,
        val timestamp: Long = System.currentTimeMillis()
    ) : EditWorkoutUiState()
    data class Error(
        val error: com.example.liftrix.domain.model.error.LiftrixError,
        val previousData: EditWorkoutData? = null
    ) : EditWorkoutUiState()
    data object Empty : EditWorkoutUiState()
}

data class EditSessionData(
    val session: com.example.liftrix.domain.model.UnifiedWorkoutSession,
    val hasChanges: Boolean = false
)

sealed class EditSessionUiState {
    data object Loading : EditSessionUiState()
    data class Success(
        val data: EditSessionData,
        val isRefreshing: Boolean = false,
        val timestamp: Long = System.currentTimeMillis()
    ) : EditSessionUiState()
    data class Error(
        val error: com.example.liftrix.domain.model.error.LiftrixError,
        val previousData: EditSessionData? = null
    ) : EditSessionUiState()
    data object Empty : EditSessionUiState()
}
