package com.example.liftrix.feedback

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.service.AnalyticsService
import com.example.liftrix.rollback.RollbackStrategy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * User Feedback Collector for comprehensive UI redesign feedback collection and analysis.
 * 
 * Collects qualitative and quantitative feedback from users experiencing the UI redesign
 * to measure satisfaction, identify issues, and provide data for continuous improvement.
 * Integrates with rollback strategy for automatic response to critical feedback patterns.
 * 
 * Key Features:
 * - Multiple feedback collection methods (rating, surveys, quick feedback)
 * - Real-time feedback analysis and pattern detection
 * - Integration with rollback strategy for critical feedback
 * - A/B testing feedback correlation
 * - Sentiment analysis and categorization
 * - Proactive feedback prompts based on user behavior
 * - Comprehensive analytics integration
 * 
 * Feedback Categories:
 * - UI/UX Design feedback
 * - Performance and responsiveness
 * - Feature usability and accessibility
 * - Bug reports and technical issues
 * - General satisfaction and recommendations
 * 
 * Collection Strategies:
 * - Contextual prompts after key interactions
 * - Periodic satisfaction surveys
 * - Quick emoji-based reactions
 * - Detailed feedback forms for specific issues
 * - Post-workout session feedback
 */
@Singleton
class UserFeedbackCollector @Inject constructor(
    private val analyticsService: AnalyticsService,
    private val rollbackStrategy: RollbackStrategy
) {
    
    companion object {
        // Feedback collection timing
        private const val FEEDBACK_PROMPT_DELAY_MS = 30000L // 30 seconds after interaction
        private const val MIN_SESSION_TIME_FOR_FEEDBACK = 300000L // 5 minutes
        private const val FEEDBACK_COLLECTION_INTERVAL = 604800000L // 1 week
        
        // Feedback analysis thresholds
        private const val CRITICAL_SATISFACTION_THRESHOLD = 2.0f // Out of 5.0
        private const val NEGATIVE_FEEDBACK_PERCENTAGE_THRESHOLD = 0.25f // 25%
        private const val MIN_FEEDBACK_COUNT_FOR_ANALYSIS = 10
        
        // Analytics event names
        private const val EVENT_FEEDBACK_PROMPT_SHOWN = "ui_redesign_feedback_prompt_shown"
        private const val EVENT_FEEDBACK_SUBMITTED = "ui_redesign_feedback_submitted"
        private const val EVENT_FEEDBACK_ANALYSIS = "ui_redesign_feedback_analysis"
        private const val EVENT_CRITICAL_FEEDBACK_PATTERN = "ui_redesign_critical_feedback_pattern"
        private const val EVENT_FEEDBACK_IMPROVEMENT_SUGGESTION = "ui_redesign_feedback_improvement"
        
        // Feedback categories
        const val CATEGORY_UI_DESIGN = "ui_design"
        const val CATEGORY_PERFORMANCE = "performance"
        const val CATEGORY_USABILITY = "usability"
        const val CATEGORY_ACCESSIBILITY = "accessibility"
        const val CATEGORY_BUGS = "bugs"
        const val CATEGORY_GENERAL = "general"
    }
    
    // Feedback state management
    private val _feedbackState = MutableStateFlow<FeedbackCollectionState>(FeedbackCollectionState.Inactive)
    val feedbackState: StateFlow<FeedbackCollectionState> = _feedbackState.asStateFlow()
    
    private val feedbackHistory = mutableListOf<UserFeedback>()
    private var lastFeedbackRequest = 0L
    private var feedbackSessionData: FeedbackSessionData? = null
    
    /**
     * Initialize feedback collection for a user session.
     * 
     * @param userId User identifier
     * @param sessionId Session identifier
     * @param isUsingRedesign Whether user is experiencing redesign
     * @param sessionContext Additional session context
     */
    suspend fun initializeFeedbackCollection(
        userId: String,
        sessionId: String,
        isUsingRedesign: Boolean,
        sessionContext: Map<String, Any> = emptyMap()
    ): LiftrixResult<Unit> {
        return try {
            feedbackSessionData = FeedbackSessionData(
                userId = userId,
                sessionId = sessionId,
                isUsingRedesign = isUsingRedesign,
                sessionStartTime = System.currentTimeMillis(),
                context = sessionContext
            )
            
            _feedbackState.value = FeedbackCollectionState.Active(userId, sessionId, isUsingRedesign)
            
            analyticsService.logEvent("ui_redesign_feedback_session_started", mapOf(
                "user_id" to userId,
                "session_id" to sessionId,
                "using_redesign" to isUsingRedesign,
                "timestamp" to System.currentTimeMillis()
            ) + sessionContext)
            
            Timber.d("Feedback collection initialized for session: $sessionId (redesign: $isUsingRedesign)")
            Result.success(Unit)
            
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to initialize feedback collection")
            Result.failure(LiftrixError.UnknownError(exception.message ?: "Unknown error"))
        }
    }
    
    /**
     * Show contextual feedback prompt to user after significant interactions.
     * 
     * @param interactionType Type of interaction that triggered prompt
     * @param promptType Type of feedback prompt to show
     * @param contextData Additional context for the prompt
     */
    suspend fun showFeedbackPrompt(
        interactionType: String,
        promptType: FeedbackPromptType,
        contextData: Map<String, Any> = emptyMap()
    ): LiftrixResult<FeedbackPromptResult> {
        return try {
            val sessionData = feedbackSessionData ?: return Result.failure(
                LiftrixError.ValidationError("session", listOf("Feedback collection not initialized"))
            )
            
            // Check if enough time has passed since last feedback request
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastFeedbackRequest < FEEDBACK_COLLECTION_INTERVAL) {
                return Result.success(FeedbackPromptResult(
                    shown = false,
                    reason = "Too soon since last feedback request"
                ))
            }
            
            // Check minimum session time
            if (currentTime - sessionData.sessionStartTime < MIN_SESSION_TIME_FOR_FEEDBACK) {
                return Result.success(FeedbackPromptResult(
                    shown = false,
                    reason = "Session too short for meaningful feedback"
                ))
            }
            
            lastFeedbackRequest = currentTime
            
            analyticsService.logEvent(EVENT_FEEDBACK_PROMPT_SHOWN, mapOf(
                "user_id" to sessionData.userId,
                "session_id" to sessionData.sessionId,
                "using_redesign" to sessionData.isUsingRedesign,
                "interaction_type" to interactionType,
                "prompt_type" to promptType.name,
                "timestamp" to currentTime
            ) + contextData)
            
            Result.success(FeedbackPromptResult(
                shown = true,
                promptType = promptType,
                contextData = contextData,
                reason = "Feedback prompt shown successfully"
            ))
            
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to show feedback prompt")
            Result.failure(LiftrixError.UnknownError(exception.message ?: "Unknown error"))
        }
    }
    
    /**
     * Collect user feedback submission.
     * 
     * @param feedback User feedback data
     * @return Result indicating successful collection
     */
    suspend fun collectFeedback(feedback: UserFeedback): LiftrixResult<Unit> {
        return try {
            val sessionData = feedbackSessionData ?: return Result.failure(
                LiftrixError.ValidationError("session", listOf("Feedback collection not initialized"))
            )
            
            // Enrich feedback with session data
            val enrichedFeedback = feedback.copy(
                userId = sessionData.userId,
                sessionId = sessionData.sessionId,
                isUsingRedesign = sessionData.isUsingRedesign,
                timestamp = System.currentTimeMillis(),
                sessionContext = sessionData.context
            )
            
            // Store feedback
            feedbackHistory.add(enrichedFeedback)
            
            // Log feedback submission
            analyticsService.logEvent(EVENT_FEEDBACK_SUBMITTED, buildMap {
                put("user_id", enrichedFeedback.userId)
                put("session_id", enrichedFeedback.sessionId)
                put("using_redesign", enrichedFeedback.isUsingRedesign)
                put("feedback_type", enrichedFeedback.type.name)
                put("category", enrichedFeedback.category)
                put("rating", enrichedFeedback.rating ?: -1)
                put("has_comment", enrichedFeedback.comment?.isNotEmpty() ?: false)
                put("sentiment", enrichedFeedback.sentiment?.name ?: "unknown")
                put("timestamp", enrichedFeedback.timestamp)
                
                enrichedFeedback.additionalData?.let { putAll(it) }
            })
            
            // Analyze feedback for critical patterns
            analyzeFeedbackForCriticalPatterns(enrichedFeedback)
            
            Timber.d("Feedback collected: ${enrichedFeedback.type} rating=${enrichedFeedback.rating}")
            Result.success(Unit)
            
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to collect feedback")
            Result.failure(LiftrixError.UnknownError(exception.message ?: "Unknown error"))
        }
    }
    
    /**
     * Collect quick emoji-based feedback for rapid user sentiment capture.
     * 
     * @param emoji Emoji reaction (😍, 😊, 😐, 😕, 😡)
     * @param context Context where reaction was given
     */
    suspend fun collectEmojiReaction(
        emoji: String,
        context: String
    ): LiftrixResult<Unit> {
        return try {
            val sentiment = mapEmojiToSentiment(emoji)
            val rating = mapEmojiToRating(emoji)
            
            val feedback = UserFeedback(
                userId = "",  // Will be enriched
                sessionId = "", // Will be enriched
                isUsingRedesign = false, // Will be enriched
                type = FeedbackType.EMOJI_REACTION,
                category = CATEGORY_GENERAL,
                rating = rating,
                sentiment = sentiment,
                comment = "Emoji reaction: $emoji in context: $context",
                timestamp = 0L, // Will be enriched
                additionalData = mapOf(
                    "emoji" to emoji,
                    "context" to context
                )
            )
            
            collectFeedback(feedback)
            
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to collect emoji reaction")
            Result.failure(LiftrixError.UnknownError(exception.message ?: "Unknown error"))
        }
    }
    
    /**
     * Generate comprehensive feedback analysis report.
     * 
     * @param timeWindowMs Time window for analysis (default: last 7 days)
     * @return Analysis report with insights and recommendations
     */
    suspend fun generateFeedbackAnalysis(timeWindowMs: Long = 604800000L): LiftrixResult<FeedbackAnalysisReport> {
        return try {
            val cutoffTime = System.currentTimeMillis() - timeWindowMs
            val recentFeedback = feedbackHistory.filter { it.timestamp >= cutoffTime }
            
            if (recentFeedback.size < MIN_FEEDBACK_COUNT_FOR_ANALYSIS) {
                return Result.success(FeedbackAnalysisReport(
                    totalFeedback = recentFeedback.size,
                    sufficientData = false,
                    message = "Insufficient feedback data for meaningful analysis"
                ))
            }
            
            val analysisReport = performFeedbackAnalysis(recentFeedback)
            
            // Log analysis
            analyticsService.logEvent(EVENT_FEEDBACK_ANALYSIS, mapOf(
                "total_feedback" to analysisReport.totalFeedback,
                "average_satisfaction" to analysisReport.averageSatisfaction,
                "positive_percentage" to analysisReport.positivePercentage,
                "negative_percentage" to analysisReport.negativePercentage,
                "critical_issues_count" to analysisReport.criticalIssues.size,
                "improvement_suggestions_count" to analysisReport.improvementSuggestions.size,
                "timestamp" to System.currentTimeMillis()
            ))
            
            Result.success(analysisReport)
            
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to generate feedback analysis")
            Result.failure(LiftrixError.UnknownError(exception.message ?: "Unknown error"))
        }
    }
    
    /**
     * Get real-time feedback insights for monitoring dashboard.
     */
    fun getFeedbackInsights(): FeedbackInsights {
        val recentFeedback = feedbackHistory.takeLast(100)
        val redesignFeedback = recentFeedback.filter { it.isUsingRedesign }
        val controlFeedback = recentFeedback.filter { !it.isUsingRedesign }
        
        return FeedbackInsights(
            totalFeedback = feedbackHistory.size,
            recentFeedback = recentFeedback.size,
            averageSatisfactionRedesign = calculateAverageSatisfaction(redesignFeedback),
            averageSatisfactionControl = calculateAverageSatisfaction(controlFeedback),
            mostCommonCategory = findMostCommonCategory(recentFeedback),
            criticalFeedbackCount = recentFeedback.count { 
                it.sentiment == FeedbackSentiment.VERY_NEGATIVE || (it.rating != null && it.rating < CRITICAL_SATISFACTION_THRESHOLD)
            }
        )
    }
    
    // Private helper methods
    
    private suspend fun analyzeFeedbackForCriticalPatterns(feedback: UserFeedback) {
        // Check for critical individual feedback
        val isCritical = feedback.rating != null && feedback.rating < CRITICAL_SATISFACTION_THRESHOLD ||
                         feedback.sentiment == FeedbackSentiment.VERY_NEGATIVE
        
        if (isCritical) {
            analyticsService.logEvent(EVENT_CRITICAL_FEEDBACK_PATTERN, mapOf(
                "feedback_type" to feedback.type.name,
                "category" to feedback.category,
                "rating" to (feedback.rating ?: -1),
                "sentiment" to (feedback.sentiment?.name ?: "unknown"),
                "comment_length" to (feedback.comment?.length ?: 0),
                "user_id" to feedback.userId,
                "using_redesign" to feedback.isUsingRedesign
            ))
            
            // Potentially trigger rollback evaluation for severe feedback patterns
            if (feedback.isUsingRedesign && shouldTriggerRollbackFromFeedback(feedback)) {
                rollbackStrategy.evaluateRollbackNeed(
                    reason = "Critical user feedback received: ${feedback.category}",
                    severity = "warning",
                    additionalContext = mapOf(
                        "feedback_rating" to (feedback.rating ?: 0),
                        "feedback_category" to feedback.category,
                        "feedback_sentiment" to (feedback.sentiment?.name ?: "unknown")
                    )
                )
            }
        }
    }
    
    private fun shouldTriggerRollbackFromFeedback(feedback: UserFeedback): Boolean {
        // Check if this feedback represents a severe enough issue to consider rollback
        return feedback.rating != null && feedback.rating < 1.5f ||
               feedback.category == CATEGORY_BUGS && feedback.sentiment == FeedbackSentiment.VERY_NEGATIVE
    }
    
    private fun performFeedbackAnalysis(feedback: List<UserFeedback>): FeedbackAnalysisReport {
        val totalFeedback = feedback.size
        val ratingsWithValues = feedback.mapNotNull { it.rating }
        val averageSatisfaction = if (ratingsWithValues.isNotEmpty()) {
            ratingsWithValues.average().toFloat()
        } else 3.0f
        
        val positiveCount = feedback.count { 
            it.sentiment == FeedbackSentiment.POSITIVE || it.sentiment == FeedbackSentiment.VERY_POSITIVE ||
            (it.rating != null && it.rating >= 4.0f)
        }
        val negativeCount = feedback.count {
            it.sentiment == FeedbackSentiment.NEGATIVE || it.sentiment == FeedbackSentiment.VERY_NEGATIVE ||
            (it.rating != null && it.rating <= 2.0f)
        }
        
        val positivePercentage = positiveCount.toFloat() / totalFeedback
        val negativePercentage = negativeCount.toFloat() / totalFeedback
        
        val categoryBreakdown = feedback.groupBy { it.category }.mapValues { it.value.size }
        
        val criticalIssues = identifyCriticalIssues(feedback)
        val improvementSuggestions = generateImprovementSuggestions(feedback)
        
        return FeedbackAnalysisReport(
            totalFeedback = totalFeedback,
            averageSatisfaction = averageSatisfaction,
            positivePercentage = positivePercentage,
            negativePercentage = negativePercentage,
            categoryBreakdown = categoryBreakdown,
            criticalIssues = criticalIssues,
            improvementSuggestions = improvementSuggestions,
            sufficientData = true
        )
    }
    
    private fun identifyCriticalIssues(feedback: List<UserFeedback>): List<String> {
        return feedback.filter { 
            it.rating != null && it.rating < CRITICAL_SATISFACTION_THRESHOLD ||
            it.sentiment == FeedbackSentiment.VERY_NEGATIVE
        }.mapNotNull { it.comment }
          .filter { it.isNotEmpty() }
          .distinct()
          .take(5) // Top 5 critical issues
    }
    
    private fun generateImprovementSuggestions(feedback: List<UserFeedback>): List<String> {
        val suggestions = mutableListOf<String>()
        
        // Analyze feedback patterns for suggestions
        val categoryIssues = feedback.groupBy { it.category }
        
        categoryIssues.forEach { (category, feedbackItems) ->
            val negativeInCategory = feedbackItems.filter { 
                it.sentiment == FeedbackSentiment.NEGATIVE || it.sentiment == FeedbackSentiment.VERY_NEGATIVE
            }
            
            if (negativeInCategory.size >= feedbackItems.size * 0.3) { // 30% negative in category
                suggestions.add("Address ${category.replace("_", " ")} issues - high negative feedback concentration")
            }
        }
        
        return suggestions.take(3) // Top 3 suggestions
    }
    
    private fun mapEmojiToSentiment(emoji: String): FeedbackSentiment {
        return when (emoji) {
            "😍", "🥰", "😘" -> FeedbackSentiment.VERY_POSITIVE
            "😊", "😄", "😃", "👍" -> FeedbackSentiment.POSITIVE
            "😐", "🤔", "😑" -> FeedbackSentiment.NEUTRAL
            "😕", "😟", "👎" -> FeedbackSentiment.NEGATIVE
            "😡", "😤", "😭" -> FeedbackSentiment.VERY_NEGATIVE
            else -> FeedbackSentiment.NEUTRAL
        }
    }
    
    private fun mapEmojiToRating(emoji: String): Float {
        return when (emoji) {
            "😍", "🥰", "😘" -> 5.0f
            "😊", "😄", "😃", "👍" -> 4.0f
            "😐", "🤔", "😑" -> 3.0f
            "😕", "😟", "👎" -> 2.0f
            "😡", "😤", "😭" -> 1.0f
            else -> 3.0f
        }
    }
    
    private fun calculateAverageSatisfaction(feedback: List<UserFeedback>): Float {
        val ratings = feedback.mapNotNull { it.rating }
        return if (ratings.isNotEmpty()) ratings.average().toFloat() else 0.0f
    }
    
    private fun findMostCommonCategory(feedback: List<UserFeedback>): String {
        return feedback.groupBy { it.category }
                      .maxByOrNull { it.value.size }
                      ?.key ?: CATEGORY_GENERAL
    }
}

// Data classes and enums

sealed class FeedbackCollectionState {
    object Inactive : FeedbackCollectionState()
    data class Active(val userId: String, val sessionId: String, val isUsingRedesign: Boolean) : FeedbackCollectionState()
}

data class FeedbackSessionData(
    val userId: String,
    val sessionId: String,
    val isUsingRedesign: Boolean,
    val sessionStartTime: Long,
    val context: Map<String, Any>
)

enum class FeedbackType {
    RATING,
    SURVEY_RESPONSE,
    BUG_REPORT,
    FEATURE_REQUEST,
    EMOJI_REACTION,
    QUICK_FEEDBACK,
    POST_INTERACTION
}

enum class FeedbackPromptType {
    CONTEXTUAL_RATING,
    POST_WORKOUT_SURVEY,
    PERIODIC_SATISFACTION,
    ISSUE_SPECIFIC,
    EMOJI_REACTION
}

enum class FeedbackSentiment {
    VERY_POSITIVE,
    POSITIVE, 
    NEUTRAL,
    NEGATIVE,
    VERY_NEGATIVE
}

data class UserFeedback(
    val userId: String,
    val sessionId: String,
    val isUsingRedesign: Boolean,
    val type: FeedbackType,
    val category: String,
    val rating: Float? = null, // 1-5 scale
    val comment: String? = null,
    val sentiment: FeedbackSentiment? = null,
    val timestamp: Long,
    val sessionContext: Map<String, Any>? = null,
    val additionalData: Map<String, Any>? = null
)

data class FeedbackPromptResult(
    val shown: Boolean,
    val promptType: FeedbackPromptType? = null,
    val contextData: Map<String, Any>? = null,
    val reason: String
)

data class FeedbackAnalysisReport(
    val totalFeedback: Int,
    val averageSatisfaction: Float = 0.0f,
    val positivePercentage: Float = 0.0f,
    val negativePercentage: Float = 0.0f,
    val categoryBreakdown: Map<String, Int> = emptyMap(),
    val criticalIssues: List<String> = emptyList(),
    val improvementSuggestions: List<String> = emptyList(),
    val sufficientData: Boolean,
    val message: String = ""
)

data class FeedbackInsights(
    val totalFeedback: Int,
    val recentFeedback: Int,
    val averageSatisfactionRedesign: Float,
    val averageSatisfactionControl: Float,
    val mostCommonCategory: String,
    val criticalFeedbackCount: Int
)