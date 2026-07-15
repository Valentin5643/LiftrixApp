package com.example.liftrix.domain.usecase.chat

import com.example.liftrix.domain.model.chat.UsageLimits
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.service.AIUsageStats
import com.example.liftrix.domain.service.RateLimitStatus
import com.example.liftrix.domain.service.RateLimitingServiceContract
import com.example.liftrix.domain.util.DomainLogger as Timber
import javax.inject.Inject

/**
 * Use case for checking AI chat usage limits and rate limit status.
 * Provides comprehensive usage information for UI display and enforcement.
 */
class CheckUsageLimitsUseCase @Inject constructor(
    private val rateLimitingService: RateLimitingServiceContract
) {
    
    /**
     * Gets detailed usage limits for a user.
     * Includes daily/monthly usage, remaining quotas, and warning states.
     * 
     * @param userId The authenticated user's ID
     * @return LiftrixResult containing current usage limits
     */
    suspend fun getUserUsageLimits(userId: String): LiftrixResult<UsageLimits> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "USAGE_LIMITS_CHECK_FAILED",
                errorMessage = "Unable to check usage limits. Please try again.",
                analyticsContext = mapOf(
                    "user_id" to userId,
                    "operation" to "CHECK_USAGE_LIMITS",
                    "error" to (throwable.message ?: "Unknown error")
                )
            )
        }
    ) {
        Timber.d("CheckUsageLimitsUseCase: Checking usage limits")
        
        val usageLimits = rateLimitingService.getUsageStats(userId).toUsageLimits()
        
        Timber.d(
            "CheckUsageLimitsUseCase: ${usageLimits.dailyMessagesRemaining} daily operations " +
            "and ${usageLimits.monthlyTokensRemaining} monthly tokens remaining"
        )
        
        usageLimits
    }
    
    /**
     * Checks if a user can send a message based on current rate limits.
     * Provides detailed information about limit status and remaining quotas.
     * 
     * @param userId The authenticated user's ID
     * @return LiftrixResult containing rate limit status
     */
    suspend fun canSendMessage(userId: String): LiftrixResult<RateLimitStatus> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "RATE_LIMIT_CHECK_FAILED",
                errorMessage = "Unable to verify sending permissions. Please try again.",
                analyticsContext = mapOf(
                    "user_id" to userId,
                    "operation" to "CHECK_RATE_LIMITS",
                    "error" to (throwable.message ?: "Unknown error")
                )
            )
        }
    ) {
        Timber.d("CheckUsageLimitsUseCase: Checking rate limits")
        
        val rateLimitStatus = rateLimitingService.checkLimits(userId)
        
        if (rateLimitStatus.isLimited) {
            Timber.w("CheckUsageLimitsUseCase: AI usage is rate limited: ${rateLimitStatus.reason}")
        } else if (rateLimitStatus.isNearLimit) {
            Timber.w("CheckUsageLimitsUseCase: AI usage is near limits: ${rateLimitStatus.reason}")
        } else {
            Timber.d("CheckUsageLimitsUseCase: AI request allowed (${rateLimitStatus.messagesRemaining} remaining)")
        }
        
        rateLimitStatus
    }
    
    /**
     * Gets usage statistics for analytics and user information.
     * Includes cost estimates and usage patterns.
     * 
     * @param userId The authenticated user's ID
     * @return LiftrixResult containing detailed usage statistics
     */
    suspend fun getUsageStats(userId: String): LiftrixResult<AIUsageStats> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "USAGE_STATS_FAILED",
                errorMessage = "Unable to retrieve usage statistics. Please try again.",
                analyticsContext = mapOf(
                    "user_id" to userId,
                    "operation" to "GET_USAGE_STATS",
                    "error" to (throwable.message ?: "Unknown error")
                )
            )
        }
    ) {
        Timber.d("CheckUsageLimitsUseCase: Getting usage statistics")
        
        val usageStats = rateLimitingService.getUsageStats(userId)
        
        Timber.d(
            "CheckUsageLimitsUseCase: usage stats - Daily: ${usageStats.dailyMessages}, " +
            "Monthly: ${usageStats.monthlyTokens} tokens, Hourly: ${usageStats.hourlyTokens} tokens, " +
            "Estimated cost: $${String.format("%.2f", usageStats.estimatedMonthlyCost)}"
        )
        
        usageStats
    }
    
    /**
     * Validates if a user can perform a specific action (send message, etc.).
     * Returns detailed validation result with user-friendly messages.
     * 
     * @param userId The authenticated user's ID
     * @param actionType The type of action to validate ("SEND_MESSAGE", "BULK_SEND", etc.)
     * @return LiftrixResult containing validation result
     */
    suspend fun validateAction(
        userId: String,
        actionType: String = "SEND_MESSAGE"
    ): LiftrixResult<UsageValidationResult> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "ACTION_VALIDATION_FAILED",
                errorMessage = "Unable to validate action. Please try again.",
                analyticsContext = mapOf(
                    "user_id" to userId,
                    "action_type" to actionType,
                    "operation" to "VALIDATE_ACTION",
                    "error" to (throwable.message ?: "Unknown error")
                )
            )
        }
    ) {
        Timber.d("CheckUsageLimitsUseCase: Validating action '$actionType'")
        
        val rateLimitStatus = rateLimitingService.checkLimits(userId)
        val usageLimits = rateLimitingService.getUsageStats(userId).toUsageLimits()
        
        val result = when {
            rateLimitStatus.isLimited -> {
                UsageValidationResult(
                    isAllowed = false,
                    reason = rateLimitStatus.reason ?: "Rate limit exceeded",
                    resetTime = rateLimitStatus.resetTime,
                    messagesRemaining = rateLimitStatus.messagesRemaining ?: 0,
                    tokensRemaining = rateLimitStatus.tokensRemaining ?: 0,
                    severity = ValidationSeverity.ERROR
                )
            }
            rateLimitStatus.isNearLimit -> {
                UsageValidationResult(
                    isAllowed = true,
                    reason = rateLimitStatus.reason,
                    messagesRemaining = rateLimitStatus.messagesRemaining ?: usageLimits.dailyMessagesRemaining,
                    tokensRemaining = rateLimitStatus.tokensRemaining ?: usageLimits.monthlyTokensRemaining,
                    severity = ValidationSeverity.WARNING
                )
            }
            else -> {
                UsageValidationResult(
                    isAllowed = true,
                    messagesRemaining = rateLimitStatus.messagesRemaining ?: usageLimits.dailyMessagesRemaining,
                    tokensRemaining = rateLimitStatus.tokensRemaining ?: usageLimits.monthlyTokensRemaining,
                    severity = ValidationSeverity.INFO
                )
            }
        }
        
        Timber.d("CheckUsageLimitsUseCase: Action validation result - allowed: ${result.isAllowed}, severity: ${result.severity}")
        
        result
    }
}

private fun AIUsageStats.toUsageLimits(): UsageLimits = UsageLimits(
    dailyMessagesRemaining = dailyMessagesRemaining,
    monthlyTokensRemaining = monthlyTokensRemaining,
    isNearDailyLimit = isNearDailyLimit,
    isNearMonthlyLimit = isNearMonthlyLimit
)

/**
 * Result of usage validation with user-friendly information.
 */
data class UsageValidationResult(
    val isAllowed: Boolean,
    val reason: String? = null,
    val resetTime: Long? = null,
    val messagesRemaining: Int = 0,
    val tokensRemaining: Int = 0,
    val severity: ValidationSeverity = ValidationSeverity.INFO
)

/**
 * Severity levels for usage validation results.
 */
enum class ValidationSeverity {
    INFO,       // Normal usage, no concerns
    WARNING,    // Approaching limits, user should be aware
    ERROR       // Limits exceeded, action blocked
}
