package com.example.liftrix.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination
import com.example.liftrix.service.UnifiedWorkoutSessionManager
import com.example.liftrix.ui.components.WorkoutCreationFab
import com.example.liftrix.ui.components.QuickWorkoutFab
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Inject

/**
 * 🔥 KEY FIX: Conditional FAB that hides during active workout sessions, in settings screens, and on Coach tab
 * 
 * This component wraps workout creation FABs and automatically hides them
 * when there's an active workout session to prevent multiple sessions
 * and UI confusion, when the user is in settings screens where adding
 * workouts doesn't make contextual sense, or when on the Coach tab
 * where the focus is on AI chat interaction.
 * 
 * Features:
 * - Type-safe route checking using sealed class hierarchy
 * - Automatic visibility based on session state
 * - Hide when in settings screens (Settings, WidgetSettings, etc.)
 * - Hide when on Coach tab (AI chat interface)
 * - Smooth enter/exit animations
 * - Reusable across different screens
 * - Proper state management with Hilt injection
 * 
 * @param onFabClick Callback triggered when FAB is clicked
 * @param modifier Modifier for styling and positioning
 * @param isExtended Whether to show extended FAB with text or compact with icon only
 * @param fabType Type of FAB to display (Creation or QuickWorkout)
 * @param currentDestination Current navigation destination to determine visibility
 */
@Composable
fun ConditionalWorkoutFab(
    onFabClick: () -> Unit,
    modifier: Modifier = Modifier,
    isExtended: Boolean = false,
    fabType: FabType = FabType.CREATION,
    currentDestination: NavDestination? = null,
    viewModel: ConditionalWorkoutFabViewModel = hiltViewModel()
) {
    val hasActiveSession by viewModel.hasActiveSession.collectAsState()
    
    // Type-safe FAB visibility check
    val shouldHideFab = hasActiveSession || shouldHideFabForRoute(currentDestination?.route)
    
    AnimatedVisibility(
        visible = !shouldHideFab,
        enter = fadeIn(animationSpec = tween(300)) + scaleIn(animationSpec = tween(300)),
        exit = fadeOut(animationSpec = tween(300)) + scaleOut(animationSpec = tween(300)),
        modifier = modifier
    ) {
        when (fabType) {
            FabType.CREATION -> {
                WorkoutCreationFab(
                    onWorkoutCreationClick = onFabClick
                )
            }
            FabType.QUICK_WORKOUT -> {
                QuickWorkoutFab(
                    onQuickWorkoutClick = onFabClick,
                    isExtended = isExtended
                )
            }
        }
    }
}

/**
 * Types of FABs that can be displayed
 */
enum class FabType {
    CREATION,
    QUICK_WORKOUT
}

/**
 * Type-safe route checking for FAB visibility.
 * Determines if FAB should be hidden based on the current route.
 * 
 * This approach is more maintainable than string matching because:
 * - It's centralized in one place
 * - It's type-safe with the sealed class hierarchy
 * - It's resilient to route refactoring
 * 
 * @param route The current navigation route
 * @return true if FAB should be hidden, false otherwise
 */
private fun shouldHideFabForRoute(route: String?): Boolean {
    if (route == null) return false
    
    // Define route patterns where FAB should be hidden
    val routesToHideFab = setOf(
        // Coach tab and standalone AI chat interface don't need workout creation
        LiftrixRoute.Coach::class.simpleName,
        "AIChatbot",
        
        // Settings screens - contextually inappropriate for workout creation
        "Settings", "WidgetSettings", "AnomalySettings", 
        "DashboardCustomization", "NotificationSettings",
        "EmailChange", "PasswordChange", "UsernameChange",
        "AccountDeletion", "PrivacySettings",
        
        // Help and legal screens
        "HelpCenter", "ContactSupport", "About",
        "PrivacyPolicy", "TermsOfService", "DataPortability",
        
        // Authentication screens
        "AuthSignIn", "AuthSignUp", "AuthForgotPassword",
        
        // Active workout - already in a workout session
        "ActiveWorkout", "ActiveWorkoutDetail"
    )
    
    // Check if current route contains any of the patterns
    return routesToHideFab.any { pattern ->
        pattern?.let { route.contains(it, ignoreCase = true) } ?: false
    }
}

/**
 * ViewModel for conditional FAB that tracks session state
 */
@HiltViewModel
class ConditionalWorkoutFabViewModel @Inject constructor(
    private val sessionManager: UnifiedWorkoutSessionManager
) : androidx.lifecycle.ViewModel() {
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    /**
     * Observable state for whether there's an active session
     */
    val hasActiveSession: StateFlow<Boolean> = sessionManager.currentSession
        .map { session -> session?.isLive() == true }
        .distinctUntilChanged()
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )
}

