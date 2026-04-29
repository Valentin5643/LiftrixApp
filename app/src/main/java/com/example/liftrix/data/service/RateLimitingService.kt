package com.example.liftrix.data.service

import com.example.liftrix.data.local.dao.ChatHistoryDao
import com.example.liftrix.data.remote.config.RemoteConfigManager
import com.example.liftrix.domain.repository.ChatRepository
import com.example.liftrix.domain.service.AnalyticsTracker
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.service.RateLimitStatus
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for managing AI chat rate limits and usage quotas.
 * Implements daily message limits, monthly token limits, and cost guardrails.
 */
@Singleton
class RateLimitingService @Inject constructor(
    private val chatRepository: ChatRepository,
    private val chatHistoryDao: ChatHistoryDao,
    private val remoteConfig: RemoteConfigManager,
    private val analyticsTracker: AnalyticsTracker
) {
    
    companion object {
        // Default limits (can be overridden by Remote Config)
        private const val DEFAULT_DAILY_MESSAGES = 50
        private const val DEFAULT_MONTHLY_TOKENS = 100000
        private const val DEFAULT_COST_THRESHOLD_PER_HOUR = 1.0 // $1.00
        
        // Gemini 2.5 Flash Lite pricing
        private const val COST_PER_1K_INPUT_TOKENS = 0.01 // $0.01 per 1k tokens
        private const val COST_PER_1K_OUTPUT_TOKENS = 0.03 // $0.03 per 1k tokens
        
        // Warning thresholds
        private const val WARNING_THRESHOLD_PERCENTAGE = 0.8 // 80%
    }
    
    /**
     * Checks if a user has exceeded their rate limits.
     *
     * @param userId The user to check
     * @return Current rate limit status with details
     */
    suspend fun checkLimits(userId: String): RateLimitStatus {
        try {
            // Get configurable thresholds from Remote Config
            val maxDailyMessages = remoteConfig.getLong("ai_max_daily_messages").getOrDefault(DEFAULT_DAILY_MESSAGES.toLong()).toInt()
            val maxMonthlyTokens = remoteConfig.getLong("ai_max_monthly_tokens").getOrDefault(DEFAULT_MONTHLY_TOKENS.toLong()).toInt()
            val costThreshold = remoteConfig.getDouble("ai_cost_threshold_per_hour").getOrDefault(DEFAULT_COST_THRESHOLD_PER_HOUR)
            val rateLimitEnabled = remoteConfig.getBoolean("ai_rate_limit_enabled").getOrDefault(true)
            val todayMessagesUsed = getTodayMessageUsage(userId)
            val monthlyTokensUsed = getMonthlyTokenUsage(userId)
            val dailyMessagesRemaining = (maxDailyMessages - todayMessagesUsed).coerceAtLeast(0)
            val monthlyTokensRemaining = (maxMonthlyTokens - monthlyTokensUsed).coerceAtLeast(0)
            val isNearDailyLimit = todayMessagesUsed >= (maxDailyMessages * WARNING_THRESHOLD_PERCENTAGE).toInt()
            val isNearMonthlyLimit = monthlyTokensUsed >= (maxMonthlyTokens * WARNING_THRESHOLD_PERCENTAGE).toInt()
            
            // If rate limiting is disabled (for testing/emergency), allow all
            if (!rateLimitEnabled) {
                Timber.d("Rate limiting disabled for user $userId")
                return RateLimitStatus(
                    isLimited = false,
                    messagesRemaining = Int.MAX_VALUE,
                    tokensRemaining = Int.MAX_VALUE
                )
            }
            
            // Check daily message limit
            if (dailyMessagesRemaining <= 0) {
                Timber.w("User $userId exceeded daily message limit")
                analyticsTracker.trackRateLimit(
                    userId = userId,
                    limitType = "DAILY_MESSAGES",
                    currentUsage = todayMessagesUsed,
                    limit = maxDailyMessages,
                    timeToReset = getTomorrowMidnight()
                )
                return RateLimitStatus(
                    isLimited = true,
                    reason = "Daily message limit reached ($maxDailyMessages messages)",
                    resetTime = getTomorrowMidnight(),
                    messagesRemaining = 0,
                    tokensRemaining = monthlyTokensRemaining
                )
            }
            
            // Check monthly token limit
            if (monthlyTokensRemaining <= 0) {
                Timber.w("User $userId exceeded monthly token limit")
                analyticsTracker.trackRateLimit(
                    userId = userId,
                    limitType = "MONTHLY_TOKENS",
                    currentUsage = monthlyTokensUsed,
                    limit = maxMonthlyTokens,
                    timeToReset = getNextMonthStart()
                )
                return RateLimitStatus(
                    isLimited = true,
                    reason = "Monthly token limit reached ($maxMonthlyTokens tokens)",
                    resetTime = getNextMonthStart(),
                    messagesRemaining = dailyMessagesRemaining,
                    tokensRemaining = 0
                )
            }
            
            // Check cost guardrail (hourly spending)
            val estimatedHourlyCost = calculateHourlyCost(userId)
            if (estimatedHourlyCost > costThreshold) {
                Timber.w("User $userId exceeded hourly cost threshold: $$estimatedHourlyCost > $$costThreshold")
                analyticsTracker.trackAIChatCost(
                    userId = userId,
                    estimatedCost = estimatedHourlyCost,
                    tokensUsed = getHourlyTokenUsage(userId),
                    timeWindow = "HOUR",
                    isNearThreshold = true
                )
                analyticsTracker.trackRateLimit(
                    userId = userId,
                    limitType = "HOURLY_COST",
                    currentUsage = estimatedHourlyCost.toInt(),
                    limit = costThreshold.toInt(),
                    timeToReset = System.currentTimeMillis() + 3600000
                )
                return RateLimitStatus(
                    isLimited = true,
                    reason = "Hourly cost threshold exceeded ($${String.format("%.2f", costThreshold)}/hour)",
                    resetTime = System.currentTimeMillis() + 3600000, // 1 hour from now
                    messagesRemaining = dailyMessagesRemaining,
                    tokensRemaining = monthlyTokensRemaining
                )
            }
            
            // Check if user is approaching limits
            val isNearLimit = isNearDailyLimit || isNearMonthlyLimit
            
            // Track usage patterns for analytics
            analyticsTracker.trackAIChatUsage(
                userId = userId,
                dailyMessagesUsed = todayMessagesUsed.coerceAtLeast(0),
                monthlyTokensUsed = monthlyTokensUsed.coerceAtLeast(0),
                isNearLimit = isNearLimit
            )
            
            return RateLimitStatus(
                isLimited = false,
                messagesRemaining = dailyMessagesRemaining,
                tokensRemaining = monthlyTokensRemaining,
                isNearLimit = isNearLimit,
                reason = if (isNearLimit) {
                    when {
                        isNearDailyLimit && isNearMonthlyLimit ->
                            "Approaching daily message and monthly token limits"
                        isNearDailyLimit ->
                            "Approaching daily message limit ($dailyMessagesRemaining remaining)"
                        else -> 
                            "Approaching monthly token limit ($monthlyTokensRemaining remaining)"
                    }
                } else null
            )
            
        } catch (e: Exception) {
            Timber.e(e, "Error checking rate limits for user $userId")
            // In case of error, be conservative and allow the request
            return RateLimitStatus(
                isLimited = false,
                reason = "Unable to verify limits"
            )
        }
    }
    
    /**
     * Calculates the estimated hourly cost for a user based on recent usage.
     *
     * @param userId The user to calculate cost for
     * @return Estimated cost in dollars
     */
    private suspend fun calculateHourlyCost(userId: String): Double {
        try {
            val oneHourAgo = System.currentTimeMillis() - 3600000
            
            // Get token usage in the last hour
            val hourlyTokens = chatHistoryDao.getHourlyTokenUsage(userId, oneHourAgo) ?: 0
            
            // Estimate input/output token split (typically 30% input, 70% output for responses)
            val inputTokens = (hourlyTokens * 0.3).toInt()
            val outputTokens = (hourlyTokens * 0.7).toInt()
            
            // Calculate cost
            val inputCost = (inputTokens / 1000.0) * COST_PER_1K_INPUT_TOKENS
            val outputCost = (outputTokens / 1000.0) * COST_PER_1K_OUTPUT_TOKENS
            
            val totalCost = inputCost + outputCost
            
            Timber.d("Hourly cost for user $userId: $$totalCost (${hourlyTokens} tokens)")
            
            return totalCost
        } catch (e: Exception) {
            Timber.e(e, "Error calculating hourly cost for user $userId")
            return 0.0
        }
    }

    private suspend fun getTodayMessageUsage(userId: String): Int {
        return try {
            val todayStart = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            chatHistoryDao.getTodayMessageCount(userId, todayStart)
        } catch (e: Exception) {
            Timber.e(e, "Error getting daily message usage for user $userId")
            0
        }
    }

    private suspend fun getMonthlyTokenUsage(userId: String): Int {
        return try {
            val monthStart = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            chatHistoryDao.getMonthlyTokenUsage(userId, monthStart) ?: 0
        } catch (e: Exception) {
            Timber.e(e, "Error getting monthly token usage for user $userId")
            0
        }
    }
    
    /**
     * Gets hourly token usage for analytics tracking.
     */
    private suspend fun getHourlyTokenUsage(userId: String): Int {
        return try {
            val oneHourAgo = System.currentTimeMillis() - 3600000
            chatHistoryDao.getHourlyTokenUsage(userId, oneHourAgo) ?: 0
        } catch (e: Exception) {
            Timber.e(e, "Error getting hourly token usage for user $userId")
            0
        }
    }
    
    /**
     * Gets the timestamp for tomorrow at midnight (local time).
     */
    private fun getTomorrowMidnight(): Long {
        return Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    
    /**
     * Gets the timestamp for the start of next month (local time).
     */
    private fun getNextMonthStart(): Long {
        return Calendar.getInstance().apply {
            add(Calendar.MONTH, 1)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    
    /**
     * Resets daily limits for all users (typically called by a scheduled job).
     */
    suspend fun resetDailyLimits() {
        Timber.d("Resetting daily limits for all users")
        // This would typically be handled by a WorkManager job
        // The actual reset happens through the database queries that check timestamps
    }
    
    /**
     * Gets usage statistics for analytics.
     */
    suspend fun getUsageStats(userId: String): UsageStats {
        try {
            val todayStart = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            
            val monthStart = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            
            val todayMessages = chatHistoryDao.getTodayMessageCount(userId, todayStart)
            val monthTokens = chatHistoryDao.getMonthlyTokenUsage(userId, monthStart) ?: 0
            val hourlyTokens = chatHistoryDao.getHourlyTokenUsage(
                userId, 
                System.currentTimeMillis() - 3600000
            ) ?: 0
            
            val estimatedMonthlyCost = (monthTokens / 1000.0) * 
                ((COST_PER_1K_INPUT_TOKENS + COST_PER_1K_OUTPUT_TOKENS) / 2)
            
            return UsageStats(
                dailyMessages = todayMessages,
                monthlyTokens = monthTokens,
                hourlyTokens = hourlyTokens,
                estimatedMonthlyCost = estimatedMonthlyCost
            )
        } catch (e: Exception) {
            Timber.e(e, "Error getting usage stats for user $userId")
            return UsageStats()
        }
    }
    
    /**
     * Data class for usage statistics.
     */
    data class UsageStats(
        val dailyMessages: Int = 0,
        val monthlyTokens: Int = 0,
        val hourlyTokens: Int = 0,
        val estimatedMonthlyCost: Double = 0.0
    )
}
