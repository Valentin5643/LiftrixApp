package com.example.liftrix.feature.workout.navigation

data class WorkoutNavigationCallbacks(
    val onNavigateToActiveWorkout: (templateId: String?) -> Unit,
    val onNavigateToWorkoutCreation: (folderId: String?) -> Unit,
    val onNavigateToEditWorkout: (workoutId: String) -> Unit,
    val onNavigateToTemplateBuddyShare: (templateId: String) -> Unit,
    val onNavigateToWorkoutDetails: (workoutId: String) -> Unit,
    val onNavigateToPostCreation: (workoutId: String) -> Unit,
    val onNavigateHome: () -> Unit,
    val onNavigateToShareWorkout: (workoutId: String) -> Unit,
    val onNavigateBack: () -> Unit
)
