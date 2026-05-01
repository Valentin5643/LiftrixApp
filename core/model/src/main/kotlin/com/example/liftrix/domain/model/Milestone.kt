package com.example.liftrix.domain.model

import java.time.LocalDate

/**
 * Domain model representing milestones within a goal for enhanced motivation and progress tracking
 * 
 * Milestones break down larger goals into smaller, achievable checkpoints that provide
 * regular celebration opportunities and maintain user motivation throughout the goal period.
 * 
 * @property id Unique milestone identifier
 * @property goalId Associated goal identifier
 * @property title Descriptive milestone title
 * @property description Optional detailed description
 * @property targetValue Target value for this milestone
 * @property targetDate Target completion date
 * @property isCompleted Whether milestone has been achieved
 * @property completedAt Actual completion date (if completed)
 * @property order Milestone ordering within goal (1st, 2nd, 3rd, etc.)
 */
data class Milestone(
    val id: MilestoneId,
    val goalId: GoalId,
    val title: String,
    val description: String? = null,
    val targetValue: Int,
    val targetDate: LocalDate,
    val isCompleted: Boolean = false,
    val completedAt: LocalDate? = null,
    val order: Int
) {
    
    /**
     * Checks if milestone is overdue
     */
    val isOverdue: Boolean 
        get() = LocalDate.now().isAfter(targetDate) && !isCompleted
    
    /**
     * Gets days remaining until target date
     */
    val daysRemaining: Int 
        get() = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), targetDate).toInt()
    
    /**
     * Validates milestone data according to business rules
     */
    fun validate(): List<String> {
        val violations = mutableListOf<String>()
        
        if (title.isBlank()) {
            violations.add("Milestone title cannot be blank")
        }
        
        if (targetValue <= 0) {
            violations.add("Target value must be greater than 0")
        }
        
        if (order <= 0) {
            violations.add("Milestone order must be positive")
        }
        
        if (isCompleted && completedAt == null) {
            violations.add("Completed milestones must have completion date")
        }
        
        if (!isCompleted && completedAt != null) {
            violations.add("Incomplete milestones cannot have completion date")
        }
        
        return violations
    }
    
    /**
     * Checks if milestone is valid
     */
    fun isValid(): Boolean = validate().isEmpty()
    
    /**
     * Marks milestone as completed
     */
    fun markCompleted(): Milestone {
        return copy(
            isCompleted = true,
            completedAt = LocalDate.now()
        )
    }
    
    /**
     * Gets status for UI display
     */
    val status: MilestoneStatus
        get() = when {
            isCompleted -> MilestoneStatus.COMPLETED
            isOverdue -> MilestoneStatus.OVERDUE
            daysRemaining <= 3 -> MilestoneStatus.URGENT
            else -> MilestoneStatus.IN_PROGRESS
        }
    
    companion object {
        /**
         * Creates milestone with generated ID
         */
        fun create(
            goalId: GoalId,
            title: String,
            description: String? = null,
            targetValue: Int,
            targetDate: LocalDate,
            order: Int
        ): Milestone {
            return Milestone(
                id = MilestoneId.generate(),
                goalId = goalId,
                title = title,
                description = description,
                targetValue = targetValue,
                targetDate = targetDate,
                order = order
            )
        }
        
        /**
         * Generates default milestones for a goal based on type and target
         */
        fun generateDefaultMilestones(goal: Goal): List<Milestone> {
            val milestones = mutableListOf<Milestone>()
            val startDate = goal.createdAt
            val endDate = goal.deadline
            val totalDays = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate)
            
            when (goal.type) {
                is GoalType.WeeklyVolume -> {
                    // Weekly milestones
                    val weeks = (totalDays / 7).toInt()
                    repeat(weeks) { week ->
                        val targetDate = startDate.plusWeeks((week + 1).toLong())
                        val targetValue = goal.target / weeks * (week + 1)
                        milestones.add(
                            create(
                                goalId = goal.id,
                                title = "Week ${week + 1}",
                                targetValue = targetValue,
                                targetDate = targetDate,
                                order = week + 1
                            )
                        )
                    }
                }
                
                is GoalType.MonthlyFrequency -> {
                    // Bi-weekly milestones
                    val milestoneCount = 4 // Every ~7-8 days for monthly goal
                    repeat(milestoneCount) { milestone ->
                        val daysInterval = totalDays / milestoneCount
                        val targetDate = startDate.plusDays(daysInterval * (milestone + 1))
                        val targetValue = goal.target / milestoneCount * (milestone + 1)
                        milestones.add(
                            create(
                                goalId = goal.id,
                                title = "Checkpoint ${milestone + 1}",
                                targetValue = targetValue,
                                targetDate = targetDate,
                                order = milestone + 1
                            )
                        )
                    }
                }
                
                is GoalType.StrengthPR -> {
                    // Progressive strength milestones
                    val currentMax = goal.currentProgress // Assuming starting strength
                    val targetMax = goal.target
                    val increment = (targetMax - currentMax) / 3 // 3 milestone progression
                    
                    repeat(3) { milestone ->
                        val targetDate = startDate.plusWeeks((milestone + 1) * 2L) // Every 2 weeks
                        val targetValue = currentMax + increment * (milestone + 1)
                        milestones.add(
                            create(
                                goalId = goal.id,
                                title = "${targetValue} ${goal.unit}",
                                description = "Progressive strength milestone",
                                targetValue = targetValue,
                                targetDate = targetDate,
                                order = milestone + 1
                            )
                        )
                    }
                }
                
                is GoalType.ConsistencyStreak -> {
                    // Weekly consistency checkpoints
                    val weeks = (totalDays / 7).toInt().coerceAtLeast(1)
                    repeat(weeks) { week ->
                        val targetDate = startDate.plusWeeks((week + 1).toLong())
                        val targetValue = 7 * (week + 1) // Days in streak
                        milestones.add(
                            create(
                                goalId = goal.id,
                                title = "Week ${week + 1} Streak",
                                description = "Maintain consistency for ${week + 1} week(s)",
                                targetValue = targetValue,
                                targetDate = targetDate,
                                order = week + 1
                            )
                        )
                    }
                }
            }
            
            return milestones
        }
    }
}

/**
 * Milestone status for UI feedback and progress tracking
 */
enum class MilestoneStatus(val displayName: String, val colorHint: String) {
    COMPLETED("Completed", "success"),
    IN_PROGRESS("In Progress", "info"),
    URGENT("Urgent", "warning"),
    OVERDUE("Overdue", "error")
}

/**
 * Value class for milestone identifiers
 */
@JvmInline
value class MilestoneId(val value: String) {
    companion object {
        fun generate(): MilestoneId = MilestoneId(java.util.UUID.randomUUID().toString())
        fun fromString(value: String): MilestoneId = MilestoneId(value)
    }
    
    override fun toString(): String = value
}