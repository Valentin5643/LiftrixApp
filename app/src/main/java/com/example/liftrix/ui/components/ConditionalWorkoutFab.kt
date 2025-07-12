package com.example.liftrix.ui.components

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
 * 🔥 KEY FIX: Conditional FAB that hides during active workout sessions
 * 
 * This component wraps workout creation FABs and automatically hides them
 * when there's an active workout session to prevent multiple sessions
 * and UI confusion.
 * 
 * Features:
 * - Automatic visibility based on session state
 * - Smooth enter/exit animations
 * - Reusable across different screens
 * - Proper state management with Hilt injection
 * 
 * @param onFabClick Callback triggered when FAB is clicked
 * @param modifier Modifier for styling and positioning
 * @param isExtended Whether to show extended FAB with text or compact with icon only
 * @param fabType Type of FAB to display (Creation or QuickWorkout)
 */
@Composable
fun ConditionalWorkoutFab(
    onFabClick: () -> Unit,
    modifier: Modifier = Modifier,
    isExtended: Boolean = false,
    fabType: FabType = FabType.CREATION,
    viewModel: ConditionalWorkoutFabViewModel = hiltViewModel()
) {
    val hasActiveSession by viewModel.hasActiveSession.collectAsState()
    
    // 🔥 KEY FIX: Hide FAB when there's an active session
    AnimatedVisibility(
        visible = !hasActiveSession,
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

