package com.example.liftrix.domain.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Domain model representing a user-defined fitness goal with SMART goal principles
 * 
 * Encapsulates goal setting, progress tracking, and achievement validation with
 * business rules for realistic goal suggestions based on historical performance.
 * 
 * @property id Unique goal identifier
 * @property userId Owner of the goal
 * @property type Type of goal (volume, frequency, strength, consistency)
 * @property target Numeric target value to achieve
 * @property currentProgress Current progress toward target
 * @property unit Unit of measurement for the target (kg, lbs, sessions, etc.)
 * @property deadline Goal completion deadline
 * @property createdAt Goal creation timestamp
 * @property isCompleted Whether the goal has been achieved
 * @property priority Goal priority level for display ordering
 */
data class Goal(
    val id: GoalId,
    val userId: String,
    val type: GoalType,
    val target: Int,
    val currentProgress: Int = 0,
    val unit: String,
    val deadline: LocalDate,
    val createdAt: LocalDate = LocalDate.now(),
    val isCompleted: Boolean = false,
    val priority: GoalPriority = GoalPriority.NORMAL
) {
    
    /**
     * Calculates progress percentage toward goal completion
     * 
     * @return Progress as percentage (0.0 to 1.0)
     */
    val progressPercentage: Float 
        get() = if (target > 0) (currentProgress.toFloat() / target.toFloat()).coerceIn(0f, 1f) else 0f
    
    /**
     * Calculates remaining days until deadline
     * 
     * @return Number of days remaining (negative if overdue)
     */
    val daysRemaining: Int 
        get() = ChronoUnit.DAYS.between(LocalDate.now(), deadline).toInt()
    
    /**
     * Checks if goal is overdue
     */
    val isOverdue: Boolean 
        get() = LocalDate.now().isAfter(deadline) && !isCompleted
    
    /**
     * Checks if goal is nearing deadline (within 7 days)
     */
    val isNearingDeadline: Boolean 
        get() = daysRemaining in 1..7 && !isCompleted
    
    /**
     * Returns goal achievement status
     */
    val achievementStatus: AchievementStatus
        get() = when {
            isCompleted -> AchievementStatus.COMPLETED
            isOverdue -> AchievementStatus.OVERDUE
            isNearingDeadline -> AchievementStatus.URGENT
            progressPercentage >= 0.8f -> AchievementStatus.NEARLY_COMPLETE
            progressPercentage >= 0.5f -> AchievementStatus.ON_TRACK
            progressPercentage >= 0.25f -> AchievementStatus.BEHIND
            else -> AchievementStatus.STARTED
        }
    
    /**
     * Validates goal parameters for business rule compliance
     * 
     * @return List of validation violations (empty if valid)
     */
    fun validate(): List<String> {
        val violations = mutableListOf<String>()
        
        if (userId.isBlank()) {
            violations.add("User ID cannot be blank")
        }
        
        if (target <= 0) {
            violations.add("Target must be greater than 0")
        }
        
        if (currentProgress < 0) {
            violations.add("Current progress cannot be negative")
        }
        
        if (currentProgress > target && !isCompleted) {
            violations.add("Current progress cannot exceed target unless goal is completed")
        }
        
        if (unit.isBlank()) {
            violations.add("Unit cannot be blank")
        }
        
        if (deadline.isBefore(createdAt)) {
            violations.add("Deadline cannot be before creation date")
        }
        
        // Business rule: Goals should be achievable within reasonable timeframe
        val goalDuration = ChronoUnit.DAYS.between(createdAt, deadline)
        when (type) {
            GoalType.WeeklyVolume -> {
                if (goalDuration < 7) violations.add("Weekly goals should span at least 7 days")
            }
            GoalType.MonthlyFrequency -> {
                if (goalDuration < 28) violations.add("Monthly goals should span at least 28 days")
            }
            GoalType.StrengthPR -> {
                if (goalDuration < 14) violations.add("Strength goals should allow at least 2 weeks for progression")
            }
            GoalType.ConsistencyStreak -> {
                if (goalDuration < target) violations.add("Consistency streaks require sufficient time to achieve target days")
            }
        }
        
        return violations
    }
    
    /**
     * Checks if goal is valid according to business rules
     */
    fun isValid(): Boolean = validate().isEmpty()
    
    /**
     * Updates progress toward goal completion
     * 
     * @param newProgress New progress value
     * @return Updated goal with new progress
     */
    fun updateProgress(newProgress: Int): Goal {
        val updatedProgress = newProgress.coerceAtLeast(0)
        val completed = updatedProgress >= target
        
        return copy(
            currentProgress = updatedProgress,
            isCompleted = completed
        )
    }
    
    /**
     * Marks goal as completed
     * 
     * @return Updated goal marked as completed
     */
    fun markCompleted(): Goal {
        return copy(
            currentProgress = target,
            isCompleted = true
        )
    }
    
    /**
     * Gets display text for goal progress
     * 
     * @return Human-readable progress description
     */
    fun getProgressText(): String {
        return "$currentProgress / $target $unit"
    }
    
    /**
     * Gets motivational message based on current progress
     * 
     * @return Encouraging message for user
     */
    fun getMotivationalMessage(): String {
        return when (achievementStatus) {
            AchievementStatus.COMPLETED -> "🎉 Goal achieved! Amazing work!"
            AchievementStatus.NEARLY_COMPLETE -> "🔥 So close! You've got this!"
            AchievementStatus.ON_TRACK -> "💪 Great progress! Keep it up!"
            AchievementStatus.BEHIND -> "📈 Time to push harder! You can do it!"
            AchievementStatus.URGENT -> "⏰ Deadline approaching! Let's finish strong!"
            AchievementStatus.OVERDUE -> "🎯 Let's get back on track with a new timeline"
            AchievementStatus.STARTED -> "🚀 Every journey starts with a single step!"
        }
    }
    
    companion object {
        /**
         * Creates a new goal with generated ID
         * 
         * @param userId Owner of the goal
         * @param type Goal type
         * @param target Target value
         * @param unit Unit of measurement
         * @param deadline Goal deadline
         * @param priority Goal priority level
         * @return New goal instance
         */
        fun create(
            userId: String,
            type: GoalType,
            target: Int,
            unit: String,
            deadline: LocalDate,
            priority: GoalPriority = GoalPriority.NORMAL
        ): Goal {
            return Goal(
                id = GoalId.generate(),
                userId = userId,
                type = type,
                target = target,
                unit = unit,
                deadline = deadline,
                priority = priority
            )
        }
    }
}

/**
 * Types of fitness goals supported by the system
 */
sealed class GoalType(
    val displayName: String,
    val description: String,
    val defaultUnit: String,
    val suggestedDuration: Int // days
) {
    data object WeeklyVolume : GoalType(
        displayName = "Weekly Volume",
        description = "Total weight lifted per week",
        defaultUnit = "lbs",
        suggestedDuration = 28 // 4 weeks
    )
    
    data object MonthlyFrequency : GoalType(
        displayName = "Monthly Frequency", 
        description = "Number of workouts per month",
        defaultUnit = "sessions",
        suggestedDuration = 30
    )
    
    data object StrengthPR : GoalType(
        displayName = "Strength PR",
        description = "Personal record for specific exercise",
        defaultUnit = "lbs",
        suggestedDuration = 42 // 6 weeks
    )
    
    data object ConsistencyStreak : GoalType(
        displayName = "Consistency Streak",
        description = "Consecutive days with workouts",
        defaultUnit = "days",
        suggestedDuration = 30
    )
    
    companion object {
        fun getAllTypes(): List<GoalType> = listOf(
            WeeklyVolume,
            MonthlyFrequency,
            StrengthPR,
            ConsistencyStreak
        )
    }
}

// GoalPriority moved to its own file - GoalPriority.kt

/**
 * Achievement status for progress motivation and UI feedback
 */
enum class AchievementStatus(val displayName: String, val colorHint: String) {
    COMPLETED("Completed", "success"),
    NEARLY_COMPLETE("Nearly Complete", "warning"),
    ON_TRACK("On Track", "success"),
    BEHIND("Behind Schedule", "warning"), 
    URGENT("Urgent", "error"),
    OVERDUE("Overdue", "error"),
    STARTED("Just Started", "info")
}