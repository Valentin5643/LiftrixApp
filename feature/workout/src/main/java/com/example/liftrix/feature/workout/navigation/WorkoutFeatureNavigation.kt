package com.example.liftrix.feature.workout.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.example.liftrix.domain.model.ExerciseLibrary
import com.example.liftrix.domain.model.WorkoutId
import com.example.liftrix.domain.usecase.exercise.ExerciseQueryUseCase
import com.example.liftrix.ui.navigation.LiftrixRoute
import com.example.liftrix.ui.workout.WorkoutScreen
import com.example.liftrix.ui.workout.active.RedesignedActiveWorkoutScreen
import com.example.liftrix.ui.workout.completion.PostCreationScreen
import com.example.liftrix.ui.workout.completion.PostWorkoutSummaryScreen
import com.example.liftrix.ui.workout.create.CreateWorkoutScreen
import com.example.liftrix.ui.workout.create.RedesignedCreateTemplateScreen
import com.example.liftrix.ui.workout.custom.CustomExerciseCreationScreen
import com.example.liftrix.ui.workout.custom.CustomExerciseEditScreen
import com.example.liftrix.ui.workout.custom.CustomExerciseListScreen
import com.example.liftrix.ui.workout.details.WorkoutDetailsScreen
import com.example.liftrix.ui.workout.edit.EditSessionScreen
import com.example.liftrix.ui.workout.edit.EditWorkoutEvent
import com.example.liftrix.ui.workout.edit.EditWorkoutViewModel
import com.example.liftrix.ui.workout.edit.RedesignedEditWorkoutScreen
import com.example.liftrix.ui.workout.selection.ExerciseSelectionScreen
import com.example.liftrix.ui.workouts.UserWorkoutsScreen
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WorkoutNavigationEntryPoint {
    fun exerciseQueryUseCase(): ExerciseQueryUseCase
}

@Composable
private fun rememberExerciseQueryUseCase(): ExerciseQueryUseCase {
    val applicationContext = LocalContext.current.applicationContext
    return remember(applicationContext) {
        EntryPointAccessors.fromApplication(
            applicationContext,
            WorkoutNavigationEntryPoint::class.java
        ).exerciseQueryUseCase()
    }
}

@Composable
fun WorkoutRoute(
    onNavigateToActiveWorkout: (String?) -> Unit,
    onNavigateToWorkoutCreation: (String?) -> Unit,
    onNavigateToEditWorkout: (String) -> Unit,
    onNavigateToTemplateBuddyShare: (String) -> Unit
) {
    WorkoutScreen(
        onNavigateToActiveWorkout = onNavigateToActiveWorkout,
        onNavigateToWorkoutCreation = onNavigateToWorkoutCreation,
        onNavigateToEditWorkout = onNavigateToEditWorkout,
        onNavigateToTemplateBuddyShare = onNavigateToTemplateBuddyShare
    )
}

@Composable
fun CustomExerciseCreationRoute(
    onNavigateBack: () -> Unit,
    onExerciseCreated: (String) -> Unit
) {
    CustomExerciseCreationScreen(
        onNavigateBack = onNavigateBack,
        onExerciseCreated = onExerciseCreated
    )
}

@Composable
fun CustomExerciseEditRoute(
    exerciseId: String,
    onNavigateBack: () -> Unit,
    onExerciseUpdated: (String) -> Unit
) {
    CustomExerciseEditScreen(
        exerciseId = exerciseId,
        onNavigateBack = onNavigateBack,
        onExerciseUpdated = onExerciseUpdated
    )
}

@Composable
fun CustomExerciseListRoute(
    onNavigateBack: () -> Unit,
    onCreateExercise: () -> Unit,
    onEditExercise: (String) -> Unit,
    onExerciseSelected: ((String) -> Unit)?
) {
    CustomExerciseListScreen(
        onNavigateBack = onNavigateBack,
        onCreateExercise = onCreateExercise,
        onEditExercise = onEditExercise,
        onExerciseSelected = onExerciseSelected
    )
}

@Composable
fun ExerciseSelectionRoute(
    isForTemplate: Boolean,
    replaceExerciseIndex: Int?,
    backStackEntry: NavBackStackEntry,
    onNavigateBack: () -> Unit,
    onSessionExerciseSelected: (ExerciseLibrary) -> Unit,
    onCreateCustomExercise: () -> Unit,
    onManageCustomExercises: () -> Unit
) {
    ExerciseSelectionScreen(
        onNavigateBack = onNavigateBack,
        onExerciseSelected = { exerciseLibrary ->
            if (isForTemplate) {
                if (replaceExerciseIndex != null) {
                    backStackEntry.savedStateHandle.set("replace_exercise_index", replaceExerciseIndex)
                    backStackEntry.savedStateHandle.set("replace_exercise_id", exerciseLibrary.id)
                } else {
                    backStackEntry.savedStateHandle.set("selected_exercise_id", exerciseLibrary.id)
                }
                onNavigateBack()
            } else {
                onSessionExerciseSelected(exerciseLibrary)
                onNavigateBack()
            }
        },
        onCreateCustomExercise = onCreateCustomExercise,
        onManageCustomExercises = onManageCustomExercises
    )
}

@Composable
fun ActiveWorkoutRoute(
    navController: NavController,
    isBlankWorkout: Boolean,
    templateId: String?,
    onNavigateBack: () -> Unit,
    onNavigateToExerciseLibrary: () -> Unit,
    onNavigateToPostCreation: (String) -> Unit,
    onNavigateToPostWorkoutSummary: (String) -> Unit
) {
    RedesignedActiveWorkoutScreen(
        navController = navController,
        onNavigateBack = onNavigateBack,
        onNavigateToExerciseLibrary = onNavigateToExerciseLibrary,
        onNavigateToPostCreation = onNavigateToPostCreation,
        onNavigateToPostWorkoutSummary = onNavigateToPostWorkoutSummary,
        isBlankWorkout = isBlankWorkout,
        templateId = templateId
    )
}

@Composable
fun TemplateCreationRoute(
    initialFolderId: String?,
    navBackStackEntry: NavBackStackEntry,
    onNavigateBack: () -> Unit,
    onNavigateToExerciseSelection: () -> Unit
) {
    RedesignedCreateTemplateScreen(
        onNavigateBack = onNavigateBack,
        onNavigateToExerciseSelection = onNavigateToExerciseSelection,
        editTemplateId = null,
        navBackStackEntry = navBackStackEntry,
        initialFolderId = initialFolderId
    )
}

@Composable
fun CreateWorkoutRoute(
    initialFolderId: String?,
    onNavigateBack: () -> Unit,
    onStartFromTemplate: () -> Unit,
    onStartBlankWorkout: () -> Unit
) {
    CreateWorkoutScreen(
        initialFolderId = initialFolderId,
        onNavigateBack = onNavigateBack,
        onStartFromTemplate = onStartFromTemplate,
        onStartBlankWorkout = onStartBlankWorkout
    )
}

@Composable
fun PostCreationRoute(
    workoutId: String,
    onNavigateBack: () -> Unit,
    onPostCreated: (String) -> Unit
) {
    PostCreationScreen(
        workoutId = workoutId,
        onNavigateBack = onNavigateBack,
        onPostCreated = onPostCreated,
        showTopBar = false
    )
}

@Composable
fun PostWorkoutSummaryRoute(
    workoutId: String,
    navController: NavController,
    onNavigateToWorkoutDetails: (String) -> Unit,
    onNavigateToPostCreation: (String) -> Unit,
    onNavigateToShareWorkout: (String) -> Unit,
    onNavigateHome: () -> Unit
) {
    PostWorkoutSummaryScreen(
        workoutId = workoutId,
        navController = navController,
        onNavigateToWorkoutDetails = onNavigateToWorkoutDetails,
        onNavigateToPostCreation = onNavigateToPostCreation,
        onNavigateToShareWorkout = onNavigateToShareWorkout,
        onNavigateHome = onNavigateHome
    )
}

@Composable
fun WorkoutDetailsRoute(
    workoutId: String,
    ownerId: String?,
    navController: NavController,
    onNavigateToEditWorkout: (String) -> Unit,
    onNavigateToShareWorkout: (String) -> Unit
) {
    WorkoutDetailsScreen(
        workoutId = workoutId,
        ownerId = ownerId,
        navController = navController,
        onNavigateToEditWorkout = onNavigateToEditWorkout,
        onNavigateToShareWorkout = onNavigateToShareWorkout
    )
}

@Composable
fun EditWorkoutRoute(
    workoutId: String,
    backStackEntry: NavBackStackEntry,
    onNavigateBack: () -> Unit,
    onNavigateToExerciseSelection: () -> Unit,
    onNavigateToExerciseSelectionWithReplacement: (Int) -> Unit,
    onNavigateToPostCreation: (String) -> Unit,
    viewModel: EditWorkoutViewModel = hiltViewModel()
) {
    val exerciseQueryUseCase = rememberExerciseQueryUseCase()

    LaunchedEffect(backStackEntry.savedStateHandle) {
        backStackEntry.savedStateHandle.getStateFlow<String?>(
            "replace_exercise_id",
            null
        ).collect { replacementExerciseId ->
            val replacementIndex = backStackEntry.savedStateHandle.get<Int>("replace_exercise_index")
            if (replacementExerciseId != null && replacementIndex != null) {
                exerciseQueryUseCase.getExerciseById(replacementExerciseId).onSuccess { exercise ->
                    if (exercise != null) {
                        viewModel.replaceExercise(replacementIndex, exercise)
                    }
                }
                backStackEntry.savedStateHandle.remove<String>("replace_exercise_id")
                backStackEntry.savedStateHandle.remove<Int>("replace_exercise_index")
            }
        }
    }

    LaunchedEffect(backStackEntry.savedStateHandle) {
        backStackEntry.savedStateHandle.getStateFlow<String?>(
            "selected_exercise_id",
            null
        ).collect { selectedExerciseId ->
            if (selectedExerciseId != null) {
                viewModel.handleEvent(EditWorkoutEvent.AddExercise(selectedExerciseId))
                backStackEntry.savedStateHandle.remove<String>("selected_exercise_id")
            }
        }
    }

    RedesignedEditWorkoutScreen(
        workoutId = WorkoutId(workoutId),
        onNavigateBack = onNavigateBack,
        onNavigateToExerciseSelection = onNavigateToExerciseSelection,
        onNavigateToExerciseSelectionWithReplacement = onNavigateToExerciseSelectionWithReplacement,
        onNavigateToPostCreation = onNavigateToPostCreation,
        viewModel = viewModel
    )
}

@Composable
fun EditSessionRoute(
    sessionId: String,
    onNavigateBack: () -> Unit
) {
    EditSessionScreen(
        sessionId = WorkoutId(sessionId),
        onNavigateBack = onNavigateBack
    )
}

@Composable
fun UserWorkoutsRoute(
    onNavigateBack: () -> Unit,
    onNavigateToWorkoutDetails: (String) -> Unit,
    onNavigateToPostComments: (String) -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToCreateWorkout: () -> Unit,
    onNavigateToEditWorkout: (String) -> Unit
) {
    UserWorkoutsScreen(
        onNavigateBack = onNavigateBack,
        onNavigateToWorkoutDetails = onNavigateToWorkoutDetails,
        onNavigateToPostComments = onNavigateToPostComments,
        onNavigateToProfile = onNavigateToProfile,
        onNavigateToCreateWorkout = onNavigateToCreateWorkout,
        onNavigateToEditWorkout = onNavigateToEditWorkout
    )
}

@Suppress("DEPRECATION")
fun NavGraphBuilder.workoutGraph(
    navController: NavHostController,
    onSessionExerciseSelected: (ExerciseLibrary) -> Unit
) {
    composable<LiftrixRoute.Workout> {
        WorkoutRoute(
            onNavigateToActiveWorkout = { templateId ->
                navController.navigate(
                    LiftrixRoute.ActiveWorkout(
                        templateId = templateId,
                        isBlankWorkout = templateId == null
                    )
                )
            },
            onNavigateToWorkoutCreation = { folderId ->
                navController.navigate(LiftrixRoute.TemplateCreation(folderId))
            },
            onNavigateToEditWorkout = { workoutId ->
                navController.navigate(LiftrixRoute.EditWorkout(workoutId))
            },
            onNavigateToTemplateBuddyShare = { templateId ->
                navController.navigate(LiftrixRoute.TemplateBuddyShare(templateId))
            }
        )
    }

    composable<LiftrixRoute.ExerciseSelection> { backStackEntry ->
        val route = backStackEntry.toRoute<LiftrixRoute.ExerciseSelection>()
        ExerciseSelectionRoute(
            isForTemplate = route.isForTemplate,
            replaceExerciseIndex = route.replaceExerciseIndex,
            backStackEntry = navController.previousBackStackEntry ?: backStackEntry,
            onNavigateBack = { navController.popBackStackSafely() },
            onSessionExerciseSelected = onSessionExerciseSelected,
            onCreateCustomExercise = {
                navController.navigate(LiftrixRoute.CustomExerciseCreation)
            },
            onManageCustomExercises = {
                navController.navigate(LiftrixRoute.CustomExerciseList(selectionMode = false))
            }
        )
    }

    composable<LiftrixRoute.CustomExerciseCreation> {
        CustomExerciseCreationRoute(
            onNavigateBack = { navController.popBackStackSafely() },
            onExerciseCreated = { _ -> navController.popBackStackSafely() }
        )
    }

    composable<LiftrixRoute.CustomExerciseEdit> { backStackEntry ->
        val route = backStackEntry.toRoute<LiftrixRoute.CustomExerciseEdit>()
        CustomExerciseEditRoute(
            exerciseId = route.exerciseId,
            onNavigateBack = { navController.popBackStackSafely() },
            onExerciseUpdated = { _ -> navController.popBackStackSafely() }
        )
    }

    composable<LiftrixRoute.CustomExerciseList> { backStackEntry ->
        val route = backStackEntry.toRoute<LiftrixRoute.CustomExerciseList>()
        CustomExerciseListRoute(
            onNavigateBack = { navController.popBackStackSafely() },
            onCreateExercise = {
                navController.navigate(LiftrixRoute.CustomExerciseCreation)
            },
            onEditExercise = { exerciseId ->
                navController.navigate(LiftrixRoute.CustomExerciseEdit(exerciseId))
            },
            onExerciseSelected = if (route.selectionMode) { exerciseId ->
                navController.previousBackStackEntry?.savedStateHandle?.set(
                    "selected_custom_exercise",
                    exerciseId
                )
                navController.popBackStackSafely()
            } else {
                null
            }
        )
    }

    composable<LiftrixRoute.TemplateCreation> { backStackEntry ->
        val route = backStackEntry.toRoute<LiftrixRoute.TemplateCreation>()
        TemplateCreationRoute(
            initialFolderId = route.folderId,
            navBackStackEntry = backStackEntry,
            onNavigateBack = { navController.popBackStackSafely() },
            onNavigateToExerciseSelection = {
                navController.navigate(
                    LiftrixRoute.ExerciseSelection(isForTemplate = true)
                )
            }
        )
    }

    composable<LiftrixRoute.ExerciseDetails> { backStackEntry ->
        val route = backStackEntry.toRoute<LiftrixRoute.ExerciseDetails>()
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Exercise Details Screen")
                Text("Exercise ID: ${route.exerciseId}")
                Button(onClick = { navController.popBackStackSafely() }) {
                    Text("Back")
                }
            }
        }
    }

    composable<LiftrixRoute.EditWorkout> { backStackEntry ->
        val route = backStackEntry.toRoute<LiftrixRoute.EditWorkout>()
        EditWorkoutRoute(
            workoutId = route.workoutId,
            backStackEntry = backStackEntry,
            onNavigateBack = { navController.popBackStackSafely() },
            onNavigateToExerciseSelection = {
                navController.navigate(
                    LiftrixRoute.ExerciseSelection(isForTemplate = true)
                )
            },
            onNavigateToExerciseSelectionWithReplacement = { exerciseIndex ->
                navController.navigate(
                    LiftrixRoute.ExerciseSelection(
                        isForTemplate = true,
                        replaceExerciseIndex = exerciseIndex
                    )
                )
            },
            onNavigateToPostCreation = { workoutId ->
                navController.navigate(LiftrixRoute.PostCreation(workoutId)) {
                    popUpTo(LiftrixRoute.EditWorkout(workoutId)) {
                        inclusive = true
                    }
                }
            }
        )
    }

    composable<LiftrixRoute.EditSession> { backStackEntry ->
        val route = backStackEntry.toRoute<LiftrixRoute.EditSession>()
        EditSessionRoute(
            sessionId = route.sessionId,
            onNavigateBack = { navController.popBackStackSafely() }
        )
    }

    composable<LiftrixRoute.CreateWorkout> { backStackEntry ->
        val route = backStackEntry.toRoute<LiftrixRoute.CreateWorkout>()
        CreateWorkoutRoute(
            initialFolderId = route.folderId,
            onNavigateBack = { navController.popBackStackSafely() },
            onStartFromTemplate = { navController.navigateToWorkoutTab() },
            onStartBlankWorkout = {
                navController.navigate(LiftrixRoute.TemplateCreation())
            }
        )
    }

    composable<LiftrixRoute.PostWorkoutSummary> { backStackEntry ->
        val route = backStackEntry.toRoute<LiftrixRoute.PostWorkoutSummary>()
        PostWorkoutSummaryRoute(
            workoutId = route.workoutId,
            navController = navController,
            onNavigateToWorkoutDetails = { workoutId ->
                navController.navigate(LiftrixRoute.WorkoutDetails(workoutId))
            },
            onNavigateToPostCreation = { workoutId ->
                navController.navigate(LiftrixRoute.PostCreation(workoutId))
            },
            onNavigateToShareWorkout = { workoutId ->
                navController.navigate(LiftrixRoute.ShareWorkout(workoutId))
            },
            onNavigateHome = {
                navController.navigate(LiftrixRoute.Home) {
                    popUpTo(LiftrixRoute.Home) {
                        inclusive = false
                    }
                }
            }
        )
    }

    composable<LiftrixRoute.WorkoutDetails> { backStackEntry ->
        val route = backStackEntry.toRoute<LiftrixRoute.WorkoutDetails>()
        WorkoutDetailsRoute(
            workoutId = route.workoutId,
            ownerId = route.ownerId,
            navController = navController,
            onNavigateToEditWorkout = { workoutId ->
                navController.navigate(LiftrixRoute.EditWorkout(workoutId))
            },
            onNavigateToShareWorkout = { workoutId ->
                navController.navigate(LiftrixRoute.ShareWorkout(workoutId))
            }
        )
    }

    composable<LiftrixRoute.UserWorkouts> {
        UserWorkoutsRoute(
            onNavigateBack = { navController.popBackStackSafely() },
            onNavigateToWorkoutDetails = { workoutId ->
                navController.navigate(LiftrixRoute.WorkoutDetails(workoutId))
            },
            onNavigateToPostComments = { postId ->
                navController.navigate(LiftrixRoute.PostComments(postId))
            },
            onNavigateToProfile = {
                navController.navigate(LiftrixRoute.Profile())
            },
            onNavigateToCreateWorkout = {
                navController.navigate(LiftrixRoute.CreateWorkout(folderId = null))
            },
            onNavigateToEditWorkout = { workoutId ->
                navController.navigate(LiftrixRoute.EditWorkout(workoutId))
            }
        )
    }

    composable<LiftrixRoute.PostCreation> { backStackEntry ->
        val route = backStackEntry.toRoute<LiftrixRoute.PostCreation>()
        PostCreationRoute(
            workoutId = route.workoutId,
            onNavigateBack = { navController.popBackStackSafely() },
            onPostCreated = { _ ->
                navController.navigate(LiftrixRoute.Home) {
                    popUpTo(LiftrixRoute.Home) {
                        inclusive = true
                    }
                }
            }
        )
    }
}

fun NavGraphBuilder.activeWorkoutGraph(navController: NavHostController) {
    composable<LiftrixRoute.ActiveWorkout> { backStackEntry ->
        val route = backStackEntry.toRoute<LiftrixRoute.ActiveWorkout>()
        ActiveWorkoutRoute(
            navController = navController,
            isBlankWorkout = route.isBlankWorkout,
            templateId = route.templateId,
            onNavigateBack = { navController.popBackStackSafely() },
            onNavigateToExerciseLibrary = {
                navController.navigate(LiftrixRoute.ExerciseSelection())
            },
            onNavigateToPostCreation = { workoutId ->
                navController.navigate(LiftrixRoute.PostCreation(workoutId))
            },
            onNavigateToPostWorkoutSummary = { workoutId ->
                navController.navigate(LiftrixRoute.PostWorkoutSummary(workoutId))
            }
        )
    }
}

private fun NavController.popBackStackSafely(): Boolean {
    return if (previousBackStackEntry != null) {
        popBackStack()
    } else {
        false
    }
}

private fun NavHostController.navigateToWorkoutTab() {
    navigate(LiftrixRoute.Workout) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
