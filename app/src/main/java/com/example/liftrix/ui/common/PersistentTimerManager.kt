package com.example.liftrix.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.liftrix.service.TimerServiceManager
import com.example.liftrix.service.WorkoutTimerService
import com.example.liftrix.ui.workout.active.ActiveWorkoutViewModel

/**
 * Manager component for persistent timer display across all screens in the app.
 * 
 * This component should be placed at the root level of the app navigation to ensure
 * the timer remains visible regardless of navigation state. It integrates with the
 * timer service and active workout session to provide seamless workout tracking.
 * 
 * Key features:
 * - Always visible during active workout sessions
 * - Persistent across navigation and screen changes
 * - Minimized UI that doesn't interfere with content
 * - Integrates with timer service for real-time updates
 * - Provides quick access to session controls
 * 
 * @param onNavigateToActiveWorkout Callback to navigate to active workout screen
 * @param modifier Modifier for styling
 * @param content The main app content to display
 */
@Composable
fun PersistentTimerManager(
    onNavigateToActiveWorkout: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val activeWorkoutViewModel: ActiveWorkoutViewModel = hiltViewModel()
    val workoutState by activeWorkoutViewModel.uiState.collectAsStateWithLifecycle()
    
    var isTimerExpanded by remember { mutableStateOf(false) }
    
    // Determine if timer should be visible
    val isTimerVisible = workoutState.isTimerServiceConnected && 
                        workoutState.timerState.timerState !is WorkoutTimerService.TimerState.Stopped
    
    Box(modifier = modifier.fillMaxSize()) {
        // Main app content
        content()
        
        // Persistent timer overlay (positioned at bottom)
        if (isTimerVisible) {
            MinimizedTimerComponent(
                timerState = workoutState.timerState,
                connectionState = if (workoutState.isTimerServiceConnected) {
                    TimerServiceManager.ConnectionState.Connected
                } else {
                    TimerServiceManager.ConnectionState.Disconnected
                },
                isVisible = true,
                isExpanded = isTimerExpanded,
                onToggleExpanded = { isTimerExpanded = !isTimerExpanded },
                onNavigateToWorkout = onNavigateToActiveWorkout,
                onPause = { activeWorkoutViewModel.onEvent(com.example.liftrix.ui.workout.active.ActiveWorkoutEvent.PauseSession) },
                onResume = { activeWorkoutViewModel.onEvent(com.example.liftrix.ui.workout.active.ActiveWorkoutEvent.ResumeSession) },
                onStop = { activeWorkoutViewModel.onEvent(com.example.liftrix.ui.workout.active.ActiveWorkoutEvent.StopSession) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}

/**
 * Integration helper for screens that need to be aware of the persistent timer.
 * 
 * This can be used to adjust content padding or layout when the timer is visible
 * to prevent overlap or provide appropriate spacing.
 * 
 * @param isTimerVisible Whether the persistent timer is currently visible
 * @param isTimerExpanded Whether the timer is in expanded state
 * @return Recommended bottom padding to account for timer overlay
 */
@Composable
fun getTimerAwarePadding(
    isTimerVisible: Boolean,
    isTimerExpanded: Boolean = false
): androidx.compose.foundation.layout.PaddingValues {
    return if (isTimerVisible) {
        androidx.compose.foundation.layout.PaddingValues(
            bottom = if (isTimerExpanded) 120.dp else 72.dp
        )
    } else {
        androidx.compose.foundation.layout.PaddingValues()
    }
}

/**
 * Timer state provider for screens that need timer information without the UI
 */
@Composable
fun rememberTimerState(): TimerState {
    val activeWorkoutViewModel: ActiveWorkoutViewModel = hiltViewModel()
    val workoutState by activeWorkoutViewModel.uiState.collectAsStateWithLifecycle()
    
    return TimerState(
        isVisible = workoutState.isTimerServiceConnected && 
                   workoutState.timerState.timerState !is WorkoutTimerService.TimerState.Stopped,
        isConnected = workoutState.isTimerServiceConnected,
        timerState = workoutState.timerState,
        hasActiveSession = workoutState.hasActiveWorkout
    )
}

/**
 * Data class representing timer state for UI components
 */
data class TimerState(
    val isVisible: Boolean,
    val isConnected: Boolean,
    val timerState: WorkoutTimerService.TimerServiceState,
    val hasActiveSession: Boolean
)