package com.example.liftrix.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import java.text.NumberFormat
import java.util.*

/**
 * Semantic content descriptions and labels for complex UI elements.
 * Provides comprehensive screen reader support and accessibility descriptions.
 */
object SemanticDescriptions {
    
    /**
     * Workout-related semantic descriptions
     */
    object Workout {
        fun exerciseCard(
            exerciseName: String,
            sets: Int,
            reps: Int,
            weight: Double? = null,
            isCompleted: Boolean = false
        ): String {
            val weightText = weight?.let { " with ${formatWeight(it)}" } ?: ""
            val statusText = if (isCompleted) "Completed" else "Pending"
            return "$exerciseName, $sets sets of $reps repetitions$weightText. Status: $statusText"
        }
        
        fun workoutSummary(
            workoutName: String,
            duration: String,
            exerciseCount: Int,
            totalVolume: Double? = null
        ): String {
            val volumeText = totalVolume?.let { " Total volume: ${formatWeight(it)}" } ?: ""
            return "$workoutName workout completed in $duration. $exerciseCount exercises.$volumeText"
        }
        
        fun restTimer(remainingSeconds: Int): String {
            val minutes = remainingSeconds / 60
            val seconds = remainingSeconds % 60
            return when {
                minutes > 0 -> "Rest timer: $minutes minutes and $seconds seconds remaining"
                seconds > 30 -> "Rest timer: $seconds seconds remaining"
                seconds > 10 -> "Rest timer: $seconds seconds remaining. Almost done"
                seconds > 0 -> "Rest timer: $seconds seconds remaining. Get ready"
                else -> "Rest time complete. Ready for next set"
            }
        }
        
        fun setProgress(currentSet: Int, totalSets: Int, reps: Int, weight: Double? = null): String {
            val weightText = weight?.let { " at ${formatWeight(it)}" } ?: ""
            return "Set $currentSet of $totalSets. $reps repetitions$weightText"
        }
        
        fun workoutProgress(completedExercises: Int, totalExercises: Int, elapsedTime: String): String {
            val percentage = ((completedExercises.toFloat() / totalExercises) * 100).toInt()
            return "Workout progress: $completedExercises of $totalExercises exercises completed. $percentage percent done. Elapsed time: $elapsedTime"
        }
    }
    
    /**
     * Progress and statistics semantic descriptions
     */
    object Progress {
        fun statisticsCard(
            title: String,
            value: String,
            change: Double? = null,
            period: String = "this week"
        ): String {
            val changeText = change?.let { 
                val direction = if (it > 0) "increased" else "decreased"
                val percentage = String.format("%.1f", kotlin.math.abs(it))
                " $direction by $percentage percent compared to last $period"
            } ?: ""
            return "$title: $value$changeText"
        }
        
        fun progressChart(
            chartType: String,
            dataPoints: Int,
            trend: String? = null,
            period: String
        ): String {
            val trendText = trend?.let { " showing $it trend" } ?: ""
            return "$chartType chart with $dataPoints data points for $period$trendText"
        }
        
        fun achievementBadge(
            title: String,
            description: String,
            isUnlocked: Boolean,
            unlockedDate: String? = null
        ): String {
            return if (isUnlocked) {
                val dateText = unlockedDate?.let { " unlocked on $it" } ?: ""
                "Achievement unlocked: $title. $description$dateText"
            } else {
                "Achievement locked: $title. $description"
            }
        }
        
        fun heatmapCell(
            date: String,
            workoutCount: Int,
            intensity: String
        ): String {
            return when (workoutCount) {
                0 -> "No workouts on $date"
                1 -> "1 workout on $date. $intensity intensity"
                else -> "$workoutCount workouts on $date. $intensity intensity"
            }
        }
    }
    
    /**
     * Navigation and UI element descriptions
     */
    object Navigation {
        fun bottomNavItem(
            label: String,
            isSelected: Boolean,
            hasNotification: Boolean = false
        ): String {
            val selectionText = if (isSelected) "Selected" else "Not selected"
            val notificationText = if (hasNotification) " Has notifications" else ""
            return "$label tab. $selectionText$notificationText"
        }
        
        fun fabButton(action: String, context: String = ""): String {
            val contextText = if (context.isNotEmpty()) " $context" else ""
            return "$action button$contextText"
        }
        
        fun searchField(
            placeholder: String,
            currentQuery: String? = null,
            resultCount: Int? = null
        ): String {
            val queryText = currentQuery?.let { " Current search: $it" } ?: ""
            val resultsText = resultCount?.let { " $it results found" } ?: ""
            return "$placeholder$queryText$resultsText"
        }
    }
    
    /**
     * Form and input descriptions
     */
    object Forms {
        fun textField(
            label: String,
            value: String? = null,
            isRequired: Boolean = false,
            hasError: Boolean = false,
            errorMessage: String? = null
        ): String {
            val valueText = value?.let { " Current value: $it" } ?: ""
            val requiredText = if (isRequired) " Required field" else ""
            val errorText = if (hasError) {
                errorMessage?.let { " Error: $it" } ?: " Has error"
            } else ""
            return "$label$valueText$requiredText$errorText"
        }
        
        fun slider(
            label: String,
            currentValue: Float,
            minValue: Float,
            maxValue: Float,
            unit: String = ""
        ): String {
            val unitText = if (unit.isNotEmpty()) " $unit" else ""
            return "$label slider. Current value: $currentValue$unitText. Range: $minValue to $maxValue$unitText"
        }
        
        fun dropdown(
            label: String,
            selectedOption: String? = null,
            optionCount: Int,
            isExpanded: Boolean = false
        ): String {
            val selectionText = selectedOption?.let { " Selected: $it" } ?: " No selection"
            val stateText = if (isExpanded) " Expanded" else " Collapsed"
            return "$label dropdown$selectionText. $optionCount options available$stateText"
        }
    }
    
    /**
     * Social and sharing descriptions
     */
    object Social {
        fun workoutShare(
            workoutName: String,
            achievements: List<String>,
            duration: String
        ): String {
            val achievementText = if (achievements.isNotEmpty()) {
                " Achievements: ${achievements.joinToString(", ")}"
            } else ""
            return "Share $workoutName workout completed in $duration$achievementText"
        }
        
        fun leaderboardEntry(
            rank: Int,
            userName: String,
            score: String,
            isCurrentUser: Boolean = false
        ): String {
            val userText = if (isCurrentUser) "You" else userName
            return "Rank $rank: $userText with score $score"
        }
        
        fun socialFeedItem(
            userName: String,
            action: String,
            timeAgo: String,
            hasLikes: Boolean = false,
            likeCount: Int = 0
        ): String {
            val likesText = if (hasLikes) " $likeCount likes" else ""
            return "$userName $action $timeAgo$likesText"
        }
    }
    
    /**
     * Utility functions for formatting
     */
    private fun formatWeight(weight: Double): String {
        return if (weight == weight.toInt().toDouble()) {
            "${weight.toInt()} kg"
        } else {
            "${String.format("%.1f", weight)} kg"
        }
    }
    
    /**
     * Format duration in a human-readable way
     */
    fun formatDuration(seconds: Int): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val remainingSeconds = seconds % 60
        
        return when {
            hours > 0 -> "$hours hours and $minutes minutes"
            minutes > 0 -> "$minutes minutes and $remainingSeconds seconds"
            else -> "$remainingSeconds seconds"
        }
    }
    
    /**
     * Format percentage with accessibility context
     */
    fun formatPercentage(value: Double, context: String = ""): String {
        val percentage = String.format("%.1f", value)
        val contextText = if (context.isNotEmpty()) " $context" else ""
        return "$percentage percent$contextText"
    }
    
    /**
     * Format date for accessibility
     */
    fun formatAccessibleDate(date: String): String {
        // Convert date format to more natural language
        // This is a simplified implementation - in production, use proper date parsing
        return date.replace("-", " ").replace("/", " ")
    }
    
    /**
     * Generate contextual announcements for state changes
     */
    object Announcements {
        fun workoutStarted(workoutName: String): String {
            return "Started $workoutName workout"
        }
        
        fun workoutCompleted(workoutName: String, duration: String): String {
            return "Completed $workoutName workout in $duration"
        }
        
        fun setCompleted(exerciseName: String, setNumber: Int, totalSets: Int): String {
            return "Completed set $setNumber of $totalSets for $exerciseName"
        }
        
        fun exerciseCompleted(exerciseName: String): String {
            return "Completed $exerciseName exercise"
        }
        
        fun restStarted(duration: Int): String {
            return "Rest timer started for ${formatDuration(duration)}"
        }
        
        fun achievementUnlocked(title: String): String {
            return "Achievement unlocked: $title"
        }
        
        fun dataLoaded(itemCount: Int, dataType: String): String {
            return "Loaded $itemCount $dataType"
        }
        
        fun errorOccurred(errorType: String, canRetry: Boolean = false): String {
            val retryText = if (canRetry) " Tap to retry" else ""
            return "Error: $errorType$retryText"
        }
    }
}

/**
 * Composable function to get localized semantic descriptions
 */
@Composable
fun rememberSemanticDescriptions(): SemanticDescriptions {
    // In a real implementation, this would handle localization
    // For now, return the default English descriptions
    return SemanticDescriptions
}

/**
 * Extension function to create accessible content descriptions for complex UI elements
 */
fun String.withAccessibilityContext(
    context: String,
    state: String? = null,
    action: String? = null
): String {
    val stateText = state?.let { " $it" } ?: ""
    val actionText = action?.let { " $it" } ?: ""
    return "$this$stateText in $context$actionText"
} 