package com.example.liftrix.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.example.liftrix.ui.theme.LiftrixColors
import java.util.Calendar
import androidx.compose.animation.core.animateFloatAsState
import com.example.liftrix.ui.theme.LiftrixAnimations

/**
 * Contextual color overlay component that adapts colors based on workout intensity,
 * time of day, and user preferences throughout the navigation and screen headers.
 * 
 * Features:
 * - Workout intensity-based color adaptation
 * - Time-based color overlays for enhanced user experience
 * - User preference integration
 * - Accessibility-compliant color combinations
 * - Smooth animated transitions between color states
 */

/**
 * Sealed class representing different color contexts for contextual overlays
 */
sealed class ColorContext {
    /**
     * Workout intensity context for exercise-related screens
     * @param intensity Float value from 0.0 (rest) to 1.0 (maximum intensity)
     * @param workoutType Optional workout type for specialized intensity colors
     */
    data class WorkoutIntensity(
        val intensity: Float,
        val workoutType: WorkoutType = WorkoutType.GENERAL
    ) : ColorContext() {
        init {
            require(intensity in 0.0f..1.0f) { "Intensity must be between 0.0 and 1.0: $intensity" }
        }
    }
    
    /**
     * Time of day context for time-based color adaptation
     * @param hour Hour of day (0-23), defaults to current time
     * @param customTimeColors Optional custom color scheme
     */
    data class TimeOfDay(
        val hour: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
        val customTimeColors: TimeBasedColorScheme? = null
    ) : ColorContext() {
        init {
            require(hour in 0..23) { "Hour must be between 0 and 23: $hour" }
        }
    }
    
    /**
     * User preference context for personalized color schemes
     * @param preferredColor User's preferred primary color
     * @param accentColor Optional accent color
     * @param intensity Optional intensity modifier for preference colors
     */
    data class UserPreference(
        val preferredColor: Color,
        val accentColor: Color? = null,
        val intensity: Float = 0.5f
    ) : ColorContext() {
        init {
            require(intensity in 0.0f..1.0f) { "Intensity must be between 0.0 and 1.0: $intensity" }
        }
    }
}

/**
 * Workout types for specialized intensity color mapping
 */
enum class WorkoutType {
    GENERAL,        // General workout colors
    CARDIO,         // Cardio-focused colors (blues, energizing)
    STRENGTH,       // Strength-focused colors (reds, powerful)
    FLEXIBILITY,    // Flexibility/yoga colors (greens, calming)
    HIIT,          // High-intensity colors (oranges, energetic)
    RECOVERY       // Recovery colors (purples, restorative)
}

/**
 * Time-based color scheme data class
 */
data class TimeBasedColorScheme(
    val primary: Color,
    val accent: Color,
    val background: Color
)

/**
 * Main contextual color overlay composable
 * 
 * @param context ColorContext defining the overlay behavior
 * @param modifier Modifier for styling
 * @param alpha Overall alpha for the overlay effect
 * @param animated Whether to animate color transitions
 * @param content Content to overlay with contextual colors
 */
@Composable
fun ContextualColorOverlay(
    context: ColorContext,
    modifier: Modifier = Modifier,
    alpha: Float = 0.1f,
    animated: Boolean = true,
    content: @Composable () -> Unit
) {
    val overlayColor = remember(context) { getContextualOverlayColor(context) }
    
    val animatedAlpha by animateFloatAsState(
        targetValue = if (animated) alpha else alpha,
        animationSpec = LiftrixAnimations.fastTransitionSpec,
        label = "overlay_alpha"
    )
    
    Box(
        modifier = modifier.background(
            brush = createContextualGradient(overlayColor, animatedAlpha)
        )
    ) {
        content()
    }
}

/**
 * Gets the overlay color based on the provided context
 */
private fun getContextualOverlayColor(context: ColorContext): Color {
    return when (context) {
        is ColorContext.WorkoutIntensity -> getWorkoutIntensityColor(context.intensity, context.workoutType)
        is ColorContext.TimeOfDay -> getTimeBasedOverlay(context.hour, context.customTimeColors)
        is ColorContext.UserPreference -> getUserPreferenceColor(context)
    }
}

/**
 * Calculates workout intensity color based on intensity level and workout type
 * 
 * @param intensity Float from 0.0 (rest) to 1.0 (maximum intensity)
 * @param workoutType Type of workout for specialized color mapping
 * @return Color representing the intensity level
 */
fun getWorkoutIntensityColor(intensity: Float, workoutType: WorkoutType = WorkoutType.GENERAL): Color {
    require(intensity in 0.0f..1.0f) { "Intensity must be between 0.0 and 1.0" }
    
    return when (workoutType) {
        WorkoutType.CARDIO -> {
            when {
                intensity < 0.3f -> LiftrixColors.Primary.copy(alpha = 0.3f) // Light teal for low cardio
                intensity < 0.6f -> Color(0xFF42A5F5).copy(alpha = 0.5f) // Blue for moderate cardio
                intensity < 0.8f -> Color(0xFF1E88E5).copy(alpha = 0.7f) // Deeper blue for high cardio
                else -> Color(0xFF1565C0).copy(alpha = 0.9f) // Deep blue for max cardio
            }
        }
        
        WorkoutType.STRENGTH -> {
            when {
                intensity < 0.3f -> LiftrixColors.Accent.copy(alpha = 0.3f) // Light coral for warm-up
                intensity < 0.6f -> Color(0xFFFF7043).copy(alpha = 0.5f) // Orange for moderate strength
                intensity < 0.8f -> Color(0xFFE53935).copy(alpha = 0.7f) // Red for high strength
                else -> Color(0xFFB71C1C).copy(alpha = 0.9f) // Deep red for max strength
            }
        }
        
        WorkoutType.FLEXIBILITY -> {
            when {
                intensity < 0.3f -> Color(0xFF81C784).copy(alpha = 0.3f) // Light green for gentle stretching
                intensity < 0.6f -> Color(0xFF66BB6A).copy(alpha = 0.5f) // Green for moderate flexibility
                intensity < 0.8f -> Color(0xFF4CAF50).copy(alpha = 0.7f) // Deeper green for active stretching
                else -> Color(0xFF2E7D32).copy(alpha = 0.9f) // Deep green for intense flexibility
            }
        }
        
        WorkoutType.HIIT -> {
            when {
                intensity < 0.3f -> Color(0xFFFFB74D).copy(alpha = 0.3f) // Light orange for prep
                intensity < 0.6f -> Color(0xFFFF9800).copy(alpha = 0.5f) // Orange for moderate HIIT
                intensity < 0.8f -> Color(0xFFFF5722).copy(alpha = 0.7f) // Deep orange for high HIIT
                else -> Color(0xFFD84315).copy(alpha = 0.9f) // Very deep orange for max HIIT
            }
        }
        
        WorkoutType.RECOVERY -> {
            when {
                intensity < 0.3f -> Color(0xFFBA68C8).copy(alpha = 0.3f) // Light purple for gentle recovery
                intensity < 0.6f -> Color(0xFF9C27B0).copy(alpha = 0.5f) // Purple for moderate recovery
                intensity < 0.8f -> Color(0xFF7B1FA2).copy(alpha = 0.7f) // Deeper purple for active recovery
                else -> Color(0xFF4A148C).copy(alpha = 0.9f) // Deep purple for intense recovery
            }
        }
        
        WorkoutType.GENERAL -> {
            when {
                intensity < 0.2f -> LiftrixColors.Primary.copy(alpha = 0.2f) // Very light for rest
                intensity < 0.4f -> LiftrixColors.Primary.copy(alpha = 0.3f) // Light for warm-up
                intensity < 0.6f -> LiftrixColors.Primary.copy(alpha = 0.5f) // Medium for moderate
                intensity < 0.8f -> LiftrixColors.Secondary.copy(alpha = 0.6f) // Deeper for high
                else -> LiftrixColors.Secondary.copy(alpha = 0.8f) // Deepest for maximum
            }
        }
    }
}

/**
 * Gets time-based overlay color using existing TimeBasedColors system
 * 
 * @param hour Hour of day (0-23)
 * @param customColors Optional custom color scheme
 * @return Color appropriate for the time of day
 */
fun getTimeBasedOverlay(hour: Int, customColors: TimeBasedColorScheme? = null): Color {
    require(hour in 0..23) { "Hour must be between 0 and 23" }
    
    return customColors?.primary?.copy(alpha = 0.1f) ?: when (hour) {
        in 6..11 -> LiftrixColors.TimeBasedColors.MorningPrimary.copy(alpha = 0.1f)
        in 12..17 -> LiftrixColors.TimeBasedColors.AfternoonPrimary.copy(alpha = 0.1f)
        in 18..23 -> LiftrixColors.TimeBasedColors.EveningPrimary.copy(alpha = 0.1f)
        else -> LiftrixColors.TimeBasedColors.NightPrimary.copy(alpha = 0.1f)
    }
}

/**
 * Gets user preference-based overlay color
 * 
 * @param context User preference context
 * @return Color based on user preferences
 */
private fun getUserPreferenceColor(context: ColorContext.UserPreference): Color {
    val baseColor = context.accentColor ?: context.preferredColor
    return baseColor.copy(alpha = context.intensity * 0.2f) // Scale intensity to reasonable alpha
}

/**
 * Creates a contextual gradient brush for overlay effects
 * 
 * @param overlayColor Primary overlay color
 * @param alpha Alpha modifier for the gradient
 * @return Brush for background gradient
 */
private fun createContextualGradient(overlayColor: Color, alpha: Float): Brush {
    return Brush.verticalGradient(
        colors = listOf(
            overlayColor.copy(alpha = alpha),
            overlayColor.copy(alpha = alpha * 0.5f),
            Color.Transparent
        )
    )
}

/**
 * Calculates workout intensity from workout completion percentage and duration
 * 
 * @param completionPercentage Workout completion percentage (0.0 to 100.0)
 * @param duration Duration in minutes
 * @param workoutType Type of workout for intensity calculation
 * @return Normalized intensity from 0.0 to 1.0
 */
fun calculateWorkoutIntensity(
    completionPercentage: Double,
    duration: Long? = null,
    workoutType: WorkoutType = WorkoutType.GENERAL
): Float {
    val baseIntensity = (completionPercentage / 100.0).toFloat().coerceIn(0.0f, 1.0f)
    
    // Adjust intensity based on duration (shorter intense workouts vs longer moderate workouts)
    val durationModifier = duration?.let { minutes ->
        when {
            minutes < 15 -> 1.2f // Short workouts are typically more intense
            minutes < 30 -> 1.0f // Normal duration
            minutes < 60 -> 0.9f // Longer workouts typically less intense
            else -> 0.8f // Very long workouts are typically lower intensity
        }
    } ?: 1.0f
    
    // Apply workout type modifier
    val typeModifier = when (workoutType) {
        WorkoutType.HIIT -> 1.3f
        WorkoutType.STRENGTH -> 1.1f
        WorkoutType.CARDIO -> 1.0f
        WorkoutType.FLEXIBILITY -> 0.7f
        WorkoutType.RECOVERY -> 0.5f
        WorkoutType.GENERAL -> 1.0f
    }
    
    return (baseIntensity * durationModifier * typeModifier).coerceIn(0.0f, 1.0f)
} 