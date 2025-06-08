package com.example.liftrix.ui.onboarding.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.liftrix.ui.onboarding.OnboardingEvent
import com.example.liftrix.ui.onboarding.OnboardingState
import com.example.liftrix.ui.onboarding.OnboardingViewModel
import com.example.liftrix.ui.onboarding.animation.OnboardingAnimations
import com.example.liftrix.ui.onboarding.animation.AnimationPerformanceUtils
import com.example.liftrix.ui.onboarding.components.AgeInputScreen
import com.example.liftrix.ui.onboarding.components.CompletionScreen
import com.example.liftrix.ui.onboarding.components.EquipmentSelectionScreen
import com.example.liftrix.ui.onboarding.components.GoalsSelectionScreen
import com.example.liftrix.ui.onboarding.components.IntroScreen
import com.example.liftrix.ui.onboarding.components.OnboardingScreenTemplate
import com.example.liftrix.ui.onboarding.components.WeightInputScreen
import com.example.liftrix.ui.onboarding.model.OnboardingStep
import timber.log.Timber

/**
 * Navigation routes for onboarding flow.
 */
object OnboardingRoutes {
    const val INTRO = "onboarding/intro"
    const val AGE = "onboarding/age"
    const val WEIGHT = "onboarding/weight"
    const val EQUIPMENT = "onboarding/equipment"
    const val GOALS = "onboarding/goals"
    const val COMPLETION = "onboarding/completion"
}

/**
 * Main onboarding navigation composable.
 * Manages the complete onboarding flow with shared ViewModel state.
 */
@Composable
fun OnboardingNavigation(
    userId: String,
    onComplete: () -> Unit,
    onSkip: () -> Unit,
    navController: NavHostController = rememberNavController(),
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    // Initialize onboarding for the user
    LaunchedEffect(userId) {
        viewModel.initializeOnboarding(userId)
    }
    
    // Observe state changes
    val state by viewModel.state.collectAsState()
    val currentNavigationStep by viewModel.currentNavigationStep.collectAsState()
    
    // Synchronize navigation with ViewModel state
    LaunchedEffect(currentNavigationStep) {
        currentNavigationStep?.let { step ->
            val targetRoute = getRouteForStep(step)
            val currentRoute = navController.currentBackStackEntry?.destination?.route
            
            if (currentRoute != targetRoute) {
                navController.navigate(targetRoute) {
                    // Prevent building up a large back stack
                    launchSingleTop = true
                    // Only pop if we're going backwards
                    if (step.ordinal < getCurrentStepFromRoute(currentRoute)?.ordinal ?: 0) {
                        popUpTo(targetRoute) { inclusive = false }
                    }
                }
                Timber.d("Navigation synced to route: $targetRoute")
            }
        }
    }
    
    // Handle state-based actions
    when (state) {
        is OnboardingState.Loading -> {
            // Show loading screen or keep current screen
        }
        is OnboardingState.StepActive -> {
            // Navigation is handled by NavHost with state synchronization
        }
        is OnboardingState.Completed -> {
            LaunchedEffect(state) {
                onComplete()
            }
        }
        is OnboardingState.Error -> {
            // Handle error state - could show error dialog or retry
            val errorState = state as OnboardingState.Error
            Timber.e("Onboarding error: ${errorState.exception.message}")
        }
    }
    
    // Performance-optimized animation configuration
    val isLowPowerMode = remember { false } // Could be detected from system state
    val optimizedDuration = AnimationPerformanceUtils.getBatteryOptimizedDuration(
        OnboardingAnimations.DURATION_MEDIUM, 
        isLowPowerMode
    )
    
    NavHost(
        navController = navController,
        startDestination = OnboardingRoutes.INTRO,
        enterTransition = {
            OnboardingAnimations.slideInForward()
        },
        exitTransition = {
            OnboardingAnimations.slideOutForward()
        },
        popEnterTransition = {
            OnboardingAnimations.slideInBackward()
        },
        popExitTransition = {
            OnboardingAnimations.slideOutBackward()
        }
    ) {
        onboardingGraph(
            viewModel = viewModel,
            navController = navController,
            onComplete = onComplete,
            onSkip = onSkip
        )
    }
}

/**
 * Defines the onboarding navigation graph.
 */
fun NavGraphBuilder.onboardingGraph(
    viewModel: OnboardingViewModel,
    navController: NavHostController,
    onComplete: () -> Unit,
    onSkip: () -> Unit
) {
    composable(OnboardingRoutes.INTRO) {
        IntroScreen(
            viewModel = viewModel,
            onNavigateNext = { viewModel.handleEvent(OnboardingEvent.NavigateNext) },
            onSkip = onSkip
        )
    }
    
    composable(OnboardingRoutes.AGE) {
        AgeInputScreen(
            viewModel = viewModel,
            onNavigateNext = { viewModel.handleEvent(OnboardingEvent.NavigateNext) },
            onNavigateBack = { 
                if (!viewModel.handleBackNavigation()) {
                    navController.popBackStack()
                }
            },
            onSkip = onSkip
        )
    }
    
    composable(OnboardingRoutes.WEIGHT) {
        WeightInputScreen(
            viewModel = viewModel,
            onNavigateNext = { viewModel.handleEvent(OnboardingEvent.NavigateNext) },
            onNavigateBack = { 
                if (!viewModel.handleBackNavigation()) {
                    navController.popBackStack()
                }
            },
            onSkip = onSkip
        )
    }
    
    composable(OnboardingRoutes.EQUIPMENT) {
        EquipmentSelectionScreen(
            viewModel = viewModel,
            onNavigateNext = { viewModel.handleEvent(OnboardingEvent.NavigateNext) },
            onNavigateBack = { 
                if (!viewModel.handleBackNavigation()) {
                    navController.popBackStack()
                }
            },
            onSkip = onSkip
        )
    }
    
    composable(OnboardingRoutes.GOALS) {
        GoalsSelectionScreen(
            viewModel = viewModel,
            onNavigateNext = { viewModel.handleEvent(OnboardingEvent.SaveProfile) },
            onNavigateBack = { 
                if (!viewModel.handleBackNavigation()) {
                    navController.popBackStack()
                }
            },
            onSkip = onSkip
        )
    }
    
    composable(OnboardingRoutes.COMPLETION) {
        CompletionScreenWrapper(
            viewModel = viewModel,
            onComplete = onComplete,
            onNavigateBack = { 
                if (!viewModel.handleBackNavigation()) {
                    navController.popBackStack()
                }
            }
        )
    }
}

/**
 * Navigate to a specific onboarding step.
 */
private fun navigateToStep(navController: NavHostController, step: OnboardingStep) {
    val route = getRouteForStep(step)
    navController.navigate(route) {
        launchSingleTop = true
    }
}

/**
 * Get navigation route for a specific step.
 */
private fun getRouteForStep(step: OnboardingStep): String {
    return when (step) {
        OnboardingStep.INTRO -> OnboardingRoutes.INTRO
        OnboardingStep.AGE -> OnboardingRoutes.AGE
        OnboardingStep.WEIGHT -> OnboardingRoutes.WEIGHT
        OnboardingStep.EQUIPMENT -> OnboardingRoutes.EQUIPMENT
        OnboardingStep.GOALS -> OnboardingRoutes.GOALS
        OnboardingStep.COMPLETION -> OnboardingRoutes.COMPLETION
    }
}

/**
 * Get onboarding step from navigation route.
 */
private fun getCurrentStepFromRoute(route: String?): OnboardingStep? {
    return when (route) {
        OnboardingRoutes.INTRO -> OnboardingStep.INTRO
        OnboardingRoutes.AGE -> OnboardingStep.AGE
        OnboardingRoutes.WEIGHT -> OnboardingStep.WEIGHT
        OnboardingRoutes.EQUIPMENT -> OnboardingStep.EQUIPMENT
        OnboardingRoutes.GOALS -> OnboardingStep.GOALS
        OnboardingRoutes.COMPLETION -> OnboardingStep.COMPLETION
        else -> null
    }
}

/**
 * Implemented screen components for onboarding flow.
 */
@Composable
private fun IntroScreen(
    viewModel: OnboardingViewModel,
    onNavigateNext: () -> Unit,
    onSkip: () -> Unit
) {
    // Sync navigation step with ViewModel
    LaunchedEffect(Unit) {
        viewModel.onNavigationStepChanged(OnboardingStep.INTRO)
    }
    
    IntroScreen(
        onStart = onNavigateNext,
        onSkip = onSkip
    )
}

@Composable
private fun AgeInputScreen(
    viewModel: OnboardingViewModel,
    onNavigateNext: () -> Unit,
    onNavigateBack: () -> Unit,
    onSkip: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    
    // Sync navigation step with ViewModel
    LaunchedEffect(Unit) {
        viewModel.onNavigationStepChanged(OnboardingStep.AGE)
    }
    
    val profileData = (state as? OnboardingState.StepActive)?.profileData
    
    if (profileData != null) {
        AgeInputScreen(
            currentAge = profileData.ageInput,
            onAgeChange = { age: String -> 
                viewModel.handleEvent(OnboardingEvent.UpdateAge(age))
            },
            onContinue = onNavigateNext,
            onBack = onNavigateBack,
            onSkip = onSkip
        )
    }
}

@Composable
private fun WeightInputScreen(
    viewModel: OnboardingViewModel,
    onNavigateNext: () -> Unit,
    onNavigateBack: () -> Unit,
    onSkip: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    
    // Sync navigation step with ViewModel
    LaunchedEffect(Unit) {
        viewModel.onNavigationStepChanged(OnboardingStep.WEIGHT)
    }
    
    val profileData = (state as? OnboardingState.StepActive)?.profileData
    
    if (profileData != null) {
        WeightInputScreen(
            currentWeight = profileData.weightInput,
            currentUnit = profileData.weightUnit,
            onWeightChange = { weight: String -> 
                viewModel.handleEvent(OnboardingEvent.UpdateWeight(weight))
            },
            onUnitChange = { unit: com.example.liftrix.ui.onboarding.WeightUnit -> 
                viewModel.handleEvent(OnboardingEvent.UpdateWeightUnit(unit))
            },
            onContinue = onNavigateNext,
            onBack = onNavigateBack,
            onSkip = onSkip
        )
    }
}

@Composable
private fun EquipmentSelectionScreen(
    viewModel: OnboardingViewModel,
    onNavigateNext: () -> Unit,
    onNavigateBack: () -> Unit,
    onSkip: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    
    // Sync navigation step with ViewModel
    LaunchedEffect(Unit) {
        viewModel.onNavigationStepChanged(OnboardingStep.EQUIPMENT)
    }
    
    val profileData = (state as? OnboardingState.StepActive)?.profileData
    
    if (profileData != null) {
        EquipmentSelectionScreen(
            selectedEquipment = profileData.selectedEquipment,
            otherEquipment = profileData.otherEquipmentInput,
            onEquipmentToggle = { equipment: com.example.liftrix.domain.model.Equipment -> 
                viewModel.handleEvent(OnboardingEvent.ToggleEquipment(equipment))
            },
            onOtherEquipmentChange = { description: String -> 
                viewModel.handleEvent(OnboardingEvent.UpdateOtherEquipment(description))
            },
            onContinue = onNavigateNext,
            onBack = onNavigateBack,
            onSkip = onSkip
        )
    }
}

@Composable
private fun GoalsSelectionScreen(
    viewModel: OnboardingViewModel,
    onNavigateNext: () -> Unit,
    onNavigateBack: () -> Unit,
    onSkip: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    
    // Sync navigation step with ViewModel
    LaunchedEffect(Unit) {
        viewModel.onNavigationStepChanged(OnboardingStep.GOALS)
    }
    
    val profileData = (state as? OnboardingState.StepActive)?.profileData
    
    if (profileData != null) {
        GoalsSelectionScreen(
            selectedGoals = profileData.selectedGoals,
            goalsPriority = profileData.goalsPriority,
            onGoalToggle = { goal: com.example.liftrix.domain.model.FitnessGoal -> 
                viewModel.handleEvent(OnboardingEvent.ToggleGoal(goal))
            },
            onGoalReorder = { goal: com.example.liftrix.domain.model.FitnessGoal, priority: Int -> 
                viewModel.handleEvent(OnboardingEvent.UpdateGoalPriority(goal, priority))
            },
            onContinue = onNavigateNext,
            onBack = onNavigateBack,
            onSkip = onSkip
        )
    }
}

@Composable
private fun CompletionScreenWrapper(
    viewModel: OnboardingViewModel,
    onComplete: () -> Unit,
    onNavigateBack: () -> Unit
) {
    // Sync navigation step with ViewModel
    LaunchedEffect(Unit) {
        viewModel.onNavigationStepChanged(OnboardingStep.COMPLETION)
    }
    
    CompletionScreen(
        viewModel = viewModel,
        onComplete = onComplete,
        onNavigateBack = onNavigateBack
    )
} 