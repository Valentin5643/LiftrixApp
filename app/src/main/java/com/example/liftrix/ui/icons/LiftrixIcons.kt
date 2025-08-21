package com.example.liftrix.ui.icons

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.liftrix.ui.theme.LiftrixTokens
import com.example.liftrix.ui.theme.LiftrixColors

/**
 * Liftrix Icon System
 * 
 * Consistent icon definitions with semantic usage across all screens.
 * All icons follow Material 3 design principles with Liftrix theming:
 * 
 * - Consistent 24dp sizing (LiftrixTokens.TouchTarget.IconMedium)
 * - Teal color (#20C9B7) for primary icons with semantic variations
 * - Proper accessibility content descriptions
 * - Visual consistency across different screen densities
 * 
 * Icon Categories:
 * - Workflow icons for primary user actions
 * - Action icons for buttons and interactions
 * - State icons for status communication
 * - Navigation icons for screen transitions
 */
object LiftrixIcons {
    
    /**
     * Primary Workflow Icons
     * Core icons representing main user workflows and features
     */
    object Workflow {
        val WorkoutCreation: ImageVector = Icons.Default.Assignment    // Clipboard with checkboxes
        val ActiveSession: ImageVector = Icons.Default.PlayArrow       // Play/start indicator
        val Progress: ImageVector = Icons.Default.TrendingUp           // Progress/analytics trending up
        val Edit: ImageVector = Icons.Default.Edit                     // Editing/modification indicator
        val History: ImageVector = Icons.Default.History               // Historical data access
        val Settings: ImageVector = Icons.Default.Settings             // Configuration and preferences
        val Profile: ImageVector = Icons.Default.Person               // User profile and account
        val Social: ImageVector = Icons.Default.People                // Friends and social features
    }
    
    /**
     * Action Icons
     * Icons for buttons, interactions, and user actions
     */
    object Actions {
        val Add: ImageVector = Icons.Default.Add                       // Add new item
        val Remove: ImageVector = Icons.Default.Remove                 // Remove/delete item
        val Save: ImageVector = Icons.Default.Save                     // Save changes
        val Cancel: ImageVector = Icons.Default.Close                  // Cancel operation
        val More: ImageVector = Icons.Default.MoreVert                 // Additional options menu
        val Search: ImageVector = Icons.Default.Search                 // Search functionality
        val Filter: ImageVector = Icons.Default.FilterList             // Filter/sort options
        val Share: ImageVector = Icons.Default.Share                   // Share content
        val Favorite: ImageVector = Icons.Default.FavoriteBorder       // Mark as favorite
        val FavoriteFilled: ImageVector = Icons.Default.Favorite       // Favorited state
    }
    
    /**
     * State Icons
     * Icons for communicating status, state, and feedback
     */
    object State {
        val Success: ImageVector = Icons.Default.CheckCircle           // Success state
        val Error: ImageVector = Icons.Default.Error                   // Error state
        val Warning: ImageVector = Icons.Default.Warning               // Warning state
        val Info: ImageVector = Icons.Default.Info                     // Information state
        val Loading: ImageVector = Icons.Default.Refresh               // Loading/refreshing state
        val Completed: ImageVector = Icons.Default.Check               // Task completed
        val Pending: ImageVector = Icons.Default.Schedule              // Pending/scheduled state
        val Paused: ImageVector = Icons.Default.Pause                  // Paused state
    }
    
    /**
     * Navigation Icons
     * Icons for navigation, movement, and directional actions
     */
    object Navigation {
        val Back: ImageVector = Icons.AutoMirrored.Filled.ArrowBack    // Navigate back
        val Forward: ImageVector = Icons.Default.ArrowForward          // Navigate forward
        val Up: ImageVector = Icons.Default.KeyboardArrowUp            // Move up
        val Down: ImageVector = Icons.Default.KeyboardArrowDown        // Move down
        val Left: ImageVector = Icons.Default.KeyboardArrowLeft        // Move left
        val Right: ImageVector = Icons.Default.KeyboardArrowRight      // Move right
        val Expand: ImageVector = Icons.Default.ExpandMore             // Expand content
        val Collapse: ImageVector = Icons.Default.ExpandLess           // Collapse content
        val Menu: ImageVector = Icons.Default.Menu                     // Navigation menu
    }
    
    /**
     * Fitness Icons
     * Specific icons for fitness and workout functionality
     */
    object Fitness {
        val Workout: ImageVector = Icons.Default.FitnessCenter         // General fitness/workout
        val Timer: ImageVector = Icons.Default.Timer                   // Workout timer
        val Weight: ImageVector = Icons.Default.MonitorWeight          // Weight tracking
        val Sets: ImageVector = Icons.Default.RepeatOne                // Sets and repetitions
        val Rest: ImageVector = Icons.Default.Pause                    // Rest periods
        val Intensity: ImageVector = Icons.Default.Whatshot            // Workout intensity
        val Achievement: ImageVector = Icons.Default.EmojiEvents       // Achievements/medals
        val Statistics: ImageVector = Icons.Default.BarChart           // Stats and analytics
    }
}

/**
 * Enhanced LiftrixIcon Composable
 * 
 * Consistent icon rendering with proper sizing, tinting, and accessibility.
 * 
 * @param icon The ImageVector icon to display
 * @param contentDescription Accessibility description for screen readers
 * @param modifier Modifier for customizing layout and behavior
 * @param tint Color tint for the icon (defaults to onSurface)
 * @param size Size of the icon (defaults to 24dp from LiftrixTokens)
 */
@Composable
fun LiftrixIcon(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    size: androidx.compose.ui.unit.Dp = LiftrixTokens.TouchTarget.IconMedium
) {
    Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        modifier = modifier
            .size(size)
            .semantics {
                contentDescription?.let { desc ->
                    this.contentDescription = desc
                }
            },
        tint = tint
    )
}

/**
 * Semantic Workout Icon
 * 
 * Context-aware icon selection and tinting based on workout type and state.
 * Provides semantic meaning through consistent color usage.
 * 
 * @param workoutType The type of workout context
 * @param modifier Modifier for layout and styling
 * @param size Size of the icon (defaults to 24dp)
 */
@Composable
fun SemanticWorkoutIcon(
    workoutType: WorkoutIconType,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = LiftrixTokens.TouchTarget.IconMedium
) {
    val (icon, description, tint) = when (workoutType) {
        WorkoutIconType.CREATE -> Triple(
            LiftrixIcons.Workflow.WorkoutCreation,
            "Create workout routine",
            MaterialTheme.colorScheme.primary  // Teal for creation
        )
        WorkoutIconType.ACTIVE -> Triple(
            LiftrixIcons.Workflow.ActiveSession,
            "Active workout session",
            LiftrixColors.TiffanyBlue     // Tiffany Blue for active state
        )
        WorkoutIconType.HISTORY -> Triple(
            LiftrixIcons.Workflow.History,
            "Workout history",
            MaterialTheme.colorScheme.onSurfaceVariant  // Neutral for history
        )
        WorkoutIconType.PROGRESS -> Triple(
            LiftrixIcons.Workflow.Progress,
            "Progress tracking",
            MaterialTheme.colorScheme.primary   // Teal for progress
        )
        WorkoutIconType.EDIT -> Triple(
            LiftrixIcons.Workflow.Edit,
            "Edit workout",
            MaterialTheme.colorScheme.secondary // Indigo for editing
        )
        WorkoutIconType.SETTINGS -> Triple(
            LiftrixIcons.Workflow.Settings,
            "Workout settings",
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    
    LiftrixIcon(
        icon = icon,
        contentDescription = description,
        modifier = modifier,
        tint = tint,
        size = size
    )
}

/**
 * Primary Action Icon
 * 
 * Icon with primary color tinting for main actions and interactive elements.
 * Uses teal color (#20C9B7) for consistency with the Liftrix brand.
 */
@Composable
fun PrimaryActionIcon(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = LiftrixTokens.TouchTarget.IconMedium
) {
    LiftrixIcon(
        icon = icon,
        contentDescription = contentDescription,
        modifier = modifier,
        tint = MaterialTheme.colorScheme.primary,
        size = size
    )
}

/**
 * Status Icon
 * 
 * Icon with semantic status coloring for state communication.
 */
@Composable
fun StatusIcon(
    statusType: StatusIconType,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = LiftrixTokens.TouchTarget.IconMedium
) {
    val (icon, description, tint) = when (statusType) {
        StatusIconType.SUCCESS -> Triple(
            LiftrixIcons.State.Success,
            "Success",
            LiftrixTokens.SemanticColors.Success
        )
        StatusIconType.ERROR -> Triple(
            LiftrixIcons.State.Error,
            "Error",
            LiftrixTokens.SemanticColors.Error
        )
        StatusIconType.WARNING -> Triple(
            LiftrixIcons.State.Warning,
            "Warning",
            MaterialTheme.colorScheme.error
        )
        StatusIconType.INFO -> Triple(
            LiftrixIcons.State.Info,
            "Information",
            LiftrixTokens.SemanticColors.Info
        )
        StatusIconType.LOADING -> Triple(
            LiftrixIcons.State.Loading,
            "Loading",
            MaterialTheme.colorScheme.primary
        )
    }
    
    LiftrixIcon(
        icon = icon,
        contentDescription = description,
        modifier = modifier,
        tint = tint,
        size = size
    )
}

/**
 * Workout Icon Type
 * 
 * Semantic categorization for different workout-related contexts.
 */
enum class WorkoutIconType {
    CREATE,      // Creating new workouts
    ACTIVE,      // Active workout sessions
    HISTORY,     // Historical workout data
    PROGRESS,    // Progress tracking and analytics
    EDIT,        // Editing existing workouts
    SETTINGS     // Workout configuration
}

/**
 * Status Icon Type
 * 
 * Semantic categorization for different status and state communications.
 */
enum class StatusIconType {
    SUCCESS,     // Success state
    ERROR,       // Error state
    WARNING,     // Warning state
    INFO,        // Information state
    LOADING      // Loading state
}

/**
 * Icon Usage Guidelines
 * 
 * Helper object providing usage examples and best practices for consistent
 * icon implementation across the application.
 */
object IconUsageGuidelines {
    
    /**
     * Primary Action Icon Usage
     * Use for main interactive elements and primary user actions
     */
    @Composable
    fun PrimaryActionExample() {
        PrimaryActionIcon(
            icon = LiftrixIcons.Actions.Add,
            contentDescription = "Add new workout"
        )
    }
    
    /**
     * Semantic Workout Icon Usage
     * Use for workout-related contexts with proper semantic meaning
     */
    @Composable
    fun SemanticWorkoutExample() {
        SemanticWorkoutIcon(
            workoutType = WorkoutIconType.CREATE
        )
    }
    
    /**
     * Status Icon Usage
     * Use for communicating states and providing user feedback
     */
    @Composable
    fun StatusExample() {
        StatusIcon(
            statusType = StatusIconType.SUCCESS
        )
    }
    
    /**
     * General Icon Usage
     * Use for custom contexts with manual color and description control
     */
    @Composable
    fun GeneralIconExample() {
        LiftrixIcon(
            icon = LiftrixIcons.Navigation.Back,
            contentDescription = "Navigate back to previous screen",
            tint = MaterialTheme.colorScheme.onSurface
        )
    }
}