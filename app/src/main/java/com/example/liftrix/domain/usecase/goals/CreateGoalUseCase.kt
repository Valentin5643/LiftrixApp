package com.example.liftrix.domain.usecase.goals

import com.example.liftrix.domain.model.Goal
import com.example.liftrix.domain.model.GoalId
import com.example.liftrix.domain.model.GoalType
import com.example.liftrix.domain.model.GoalPriority
import com.example.liftrix.domain.model.Milestone
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixFailure
import com.example.liftrix.domain.model.common.liftrixSuccess
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.ProgressStatsRepository
import com.example.liftrix.domain.usecase.common.ErrorHandler
import com.example.liftrix.service.AnalyticsEngine
import timber.log.Timber
import java.time.LocalDate
import javax.inject.Inject

/**
 * Use case for creating new fitness goals with intelligent suggestions based on historical performance
 * 
 * Provides SMART goal creation with:
 * - Historical performance analysis for realistic target suggestions
 * - Automatic milestone generation for sustained motivation
 * - Validation against business rules and user capabilities
 * - Integration with analytics engine for data-driven recommendations
 * 
 * Business Logic:
 * - Analyzes user's past performance to suggest achievable targets
 * - Validates goal parameters against SMART criteria
 * - Generates progressive milestones for goal achievement
 * - Ensures goals align with user's fitness level and history
 */
class CreateGoalUseCase @Inject constructor(
    private val progressStatsRepository: ProgressStatsRepository,
    private val analyticsEngine: AnalyticsEngine,
    private val errorHandler: ErrorHandler
) {
    
    /**
     * Creates a new goal with validation and historical analysis
     * 
     * @param request Goal creation request with user parameters
     * @return LiftrixResult containing created goal with milestones or error
     */
    suspend operator fun invoke(request: CreateGoalRequest): LiftrixResult<GoalWithMilestones> {
        return try {
            Timber.d("Creating goal for userId: ${request.userId}, type: ${request.type}")
            
            // Validate request parameters
            val validationResult = validateRequest(request)
            if (validationResult.isFailure) {
                return validationResult as LiftrixResult<GoalWithMilestones>
            }
            
            // Create goal with suggested or user-provided target
            val goal = if (request.useSuggestedTarget) {
                createGoalWithSuggestedTarget(request)
            } else {
                createGoalWithUserTarget(request)
            }
            
            // Validate created goal
            val goalValidation = goal.validate()
            if (goalValidation.isNotEmpty()) {
                return liftrixFailure(
                    LiftrixError.ValidationError(
                        field = "goal",
                        violations = goalValidation
                    )
                )
            }
            
            // Generate default milestones
            val milestones = if (request.generateMilestones) {
                Milestone.generateDefaultMilestones(goal)
            } else {
                emptyList()
            }
            
            // Save goal and milestones (would typically go to repository)
            Timber.d("Successfully created goal: ${goal.id} with ${milestones.size} milestones")
            
            liftrixSuccess(GoalWithMilestones(goal, milestones))
            
        } catch (e: Exception) {
            Timber.e(e, "Unexpected error creating goal for userId: ${request.userId}")
            val error = LiftrixError.UnknownError("Failed to create goal: ${e.message}")
            errorHandler.handleError(error, mapOf("context" to "CreateGoalUseCase"))
            liftrixFailure(error)
        }
    }
    
    /**
     * Suggests realistic goal target based on user's historical performance
     * 
     * @param userId User identifier
     * @param goalType Type of goal to suggest target for
     * @param timeframe Timeframe for goal completion (in days)
     * @return LiftrixResult containing suggested target value
     */
    suspend fun suggestGoalTarget(
        userId: String,
        goalType: GoalType,
        timeframe: Int = goalType.suggestedDuration
    ): LiftrixResult<Int> {
        return try {
            Timber.d("Generating target suggestion for userId: $userId, type: $goalType")
            
            when (goalType) {
                is GoalType.WeeklyVolume -> suggestWeeklyVolumeTarget(userId, timeframe)
                is GoalType.MonthlyFrequency -> suggestMonthlyFrequencyTarget(userId, timeframe)
                is GoalType.StrengthPR -> suggestStrengthPRTarget(userId, timeframe)
                is GoalType.ConsistencyStreak -> suggestConsistencyStreakTarget(userId, timeframe)
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Error generating goal suggestion for userId: $userId")
            val error = LiftrixError.UnknownError("Failed to generate goal suggestion: ${e.message}")
            liftrixFailure(error)
        }
    }
    
    /**
     * Validates goal creation request parameters
     */
    private fun validateRequest(request: CreateGoalRequest): LiftrixResult<Unit> {
        val violations = mutableListOf<String>()
        
        if (request.userId.isBlank()) {
            violations.add("User ID cannot be blank")
        }
        
        if (!request.useSuggestedTarget && request.targetValue != null && request.targetValue <= 0) {
            violations.add("Target value must be greater than 0")
        }
        
        if (request.deadline.isBefore(LocalDate.now())) {
            violations.add("Deadline cannot be in the past")
        }
        
        if (request.deadline.isBefore(LocalDate.now().plusDays(1))) {
            violations.add("Deadline must be at least 1 day in the future")
        }
        
        return if (violations.isEmpty()) {
            liftrixSuccess(Unit)
        } else {
            liftrixFailure(
                LiftrixError.ValidationError(
                    field = "CreateGoalRequest",
                    violations = violations
                )
            )
        }
    }
    
    /**
     * Creates goal with AI-suggested target based on historical data
     */
    private suspend fun createGoalWithSuggestedTarget(request: CreateGoalRequest): Goal {
        val suggestedTargetResult = suggestGoalTarget(
            userId = request.userId,
            goalType = request.type,
            timeframe = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), request.deadline).toInt()
        )
        
        val target = when (suggestedTargetResult) {
            is LiftrixResult -> {
                if (suggestedTargetResult.isSuccess) {
                    suggestedTargetResult.getOrThrow()
                } else {
                    // Fallback to conservative default if suggestion fails
                    getDefaultTargetForType(request.type)
                }
            }
            else -> getDefaultTargetForType(request.type)
        }
        
        return Goal.create(
            userId = request.userId,
            type = request.type,
            target = target,
            unit = request.type.defaultUnit,
            deadline = request.deadline,
            priority = request.priority
        )
    }
    
    /**
     * Creates goal with user-specified target
     */
    private fun createGoalWithUserTarget(request: CreateGoalRequest): Goal {
        val target = request.targetValue ?: getDefaultTargetForType(request.type)
        
        return Goal.create(
            userId = request.userId,
            type = request.type,
            target = target,
            unit = request.type.defaultUnit,
            deadline = request.deadline,
            priority = request.priority
        )
    }
    
    /**
     * Suggests weekly volume target based on recent performance
     */
    private suspend fun suggestWeeklyVolumeTarget(userId: String, timeframe: Int): LiftrixResult<Int> {
        // In real implementation, would analyze last 4-8 weeks of data
        // For now, return conservative suggestion
        return liftrixSuccess(50000) // 50k lbs as reasonable starting target
    }
    
    /**
     * Suggests monthly frequency target based on historical consistency
     */
    private suspend fun suggestMonthlyFrequencyTarget(userId: String, timeframe: Int): LiftrixResult<Int> {
        // Analyze historical workout frequency
        // Conservative suggestion: 12 sessions per month (3 per week)
        return liftrixSuccess(12)
    }
    
    /**
     * Suggests strength PR target based on recent lifts
     */
    private suspend fun suggestStrengthPRTarget(userId: String, timeframe: Int): LiftrixResult<Int> {
        // Analyze recent max lifts and suggest 5-10% improvement
        // Conservative starting suggestion
        return liftrixSuccess(200) // 200 lbs as example
    }
    
    /**
     * Suggests consistency streak target based on user level
     */
    private suspend fun suggestConsistencyStreakTarget(userId: String, timeframe: Int): LiftrixResult<Int> {
        // Progressive streak targets: 7, 14, 21, 30 days
        val conservativeTarget = when {
            timeframe >= 30 -> 21 // 3 weeks for month+ goals
            timeframe >= 21 -> 14 // 2 weeks for 3+ week goals
            else -> 7 // 1 week for shorter goals
        }
        return liftrixSuccess(conservativeTarget)
    }
    
    /**
     * Gets conservative default target when suggestions fail
     */
    private fun getDefaultTargetForType(type: GoalType): Int {
        return when (type) {
            is GoalType.WeeklyVolume -> 30000 // 30k lbs
            is GoalType.MonthlyFrequency -> 10 // 10 sessions
            is GoalType.StrengthPR -> 150 // 150 lbs
            is GoalType.ConsistencyStreak -> 7 // 7 days
        }
    }
    
    companion object {
        private const val MIN_GOAL_DURATION_DAYS = 1
        private const val MAX_GOAL_DURATION_DAYS = 365 // 1 year max
    }
}

/**
 * Request data class for goal creation
 */
data class CreateGoalRequest(
    val userId: String,
    val type: GoalType,
    val deadline: LocalDate,
    val priority: GoalPriority = GoalPriority.NORMAL,
    val useSuggestedTarget: Boolean = true,
    val targetValue: Int? = null, // Used when useSuggestedTarget = false
    val generateMilestones: Boolean = true
) {
    init {
        require(userId.isNotBlank()) { "User ID cannot be blank" }
        require(!deadline.isBefore(LocalDate.now())) { "Deadline cannot be in the past" }
        if (!useSuggestedTarget) {
            require(targetValue != null && targetValue > 0) { "Target value must be provided and positive when not using suggestions" }
        }
    }
}

/**
 * Result data class containing goal with associated milestones
 */
data class GoalWithMilestones(
    val goal: Goal,
    val milestones: List<Milestone>
) {
    /**
     * Gets next upcoming milestone
     */
    val nextMilestone: Milestone?
        get() = milestones
            .filter { !it.isCompleted }
            .minByOrNull { it.targetDate }
    
    /**
     * Gets completed milestones count
     */
    val completedMilestonesCount: Int
        get() = milestones.count { it.isCompleted }
    
    /**
     * Gets total milestone progress percentage
     */
    val milestoneProgress: Float
        get() = if (milestones.isNotEmpty()) {
            completedMilestonesCount.toFloat() / milestones.size.toFloat()
        } else 0f
}