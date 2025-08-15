package com.example.liftrix.ui.workout.create

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.liftrix.ui.components.WorkoutCreationModal
import com.example.liftrix.ui.workout.components.UnifiedWorkoutCard
import com.example.liftrix.ui.workout.components.PrimaryActionButton

/**
 * Simple workout creation screen that shows the modal immediately.
 * 
 * This screen provides the original simple workout creation experience:
 * - Clean modal with 2 options
 * - No complex validation or form fields
 * - Folder support through navigation callbacks
 * 
 * @param onNavigateBack Callback for back navigation
 * @param initialFolderId Optional folder ID for organizing the workout
 * @param onStartFromTemplate Callback when user chooses template-based creation
 * @param onStartBlankWorkout Callback when user chooses blank workout creation  
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateWorkoutScreen(
    onNavigateBack: () -> Unit,
    initialFolderId: String? = null,
    onStartFromTemplate: (() -> Unit)? = null,
    onStartBlankWorkout: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showModal by remember { mutableStateOf(false) }
    
    // Show modal immediately when screen loads
    LaunchedEffect(Unit) {
        showModal = true
    }
    
    Box(modifier = modifier.fillMaxSize()) {
        // Simple modal-based creation (your original simple version)
        WorkoutCreationModal(
            isVisible = showModal,
            onDismiss = {
                showModal = false
                onNavigateBack()
            },
            onStartFromTemplate = {
                showModal = false
                onStartFromTemplate?.invoke() ?: onNavigateBack()
            },
            onStartBlankWorkout = {
                showModal = false  
                onStartBlankWorkout?.invoke() ?: onNavigateBack()
            }
        )
    }
}