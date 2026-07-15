package com.example.liftrix.data.service

import com.example.liftrix.data.local.dao.AiUsageDao
import com.example.liftrix.data.remote.config.RemoteConfigManager
import com.example.liftrix.domain.service.AIUsageStats
import com.example.liftrix.domain.service.AnalyticsTracker
import com.example.liftrix.domain.service.RateLimitStatus
import com.example.liftrix.domain.service.RateLimitingServiceContract
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * Sole owner of AI quota arithmetic.
 *
 * Limits come from Remote Config and consumption comes from the append-only ai_usage ledger.
 * Chat retention, chat preferences, and subscription state never influence enforcement.
 */
@Singleton
class RateLimitingService @Inject constructor(
    private val aiUsageDao: AiUsageDao,
    private val remoteConfig: RemoteConfigManager,
    private val analyticsTracker: AnalyticsTracker
) : RateLimitingServiceContract {

    override suspend fun checkLimits(userId: String): RateLimitStatus = try {
        val usage = loadUsage(userId)

        when {
            usage.dailyMessagesRemaining == 0 -> {
                analyticsTracker.trackRateLimit(
                    userId = userId,
                    limitType = "DAILY_MESSAGES",
                    currentUsage = usage.dailyMessages,
                    limit = usage.dailyMessageLimit,
                    timeToReset = tomorrowMidnight()
                )
                usage.toStatus(
                    isLimited = true,
                    reason = "Daily AI operation limit reached (${usage.dailyMessageLimit} operations)",
                    resetTime = tomorrowMidnight()
                )
            }

            usage.monthlyTokensRemaining == 0 -> {
                analyticsTracker.trackRateLimit(
                    userId = userId,
                    limitType = "MONTHLY_TOKENS",
                    currentUsage = usage.monthlyTokens,
                    limit = usage.monthlyTokenLimit,
                    timeToReset = nextMonthStart()
                )
                usage.toStatus(
                    isLimited = true,
                    reason = "Monthly AI token limit reached (${usage.monthlyTokenLimit} tokens)",
                    resetTime = nextMonthStart()
                )
            }

            usage.estimatedHourlyCost > usage.hourlyCostThreshold -> {
                analyticsTracker.trackAIChatCost(
                    userId = userId,
                    estimatedCost = usage.estimatedHourlyCost,
                    tokensUsed = usage.hourlyTokens,
                    timeWindow = "HOUR",
                    isNearThreshold = true
                )
                analyticsTracker.trackRateLimit(
                    userId = userId,
                    limitType = "HOURLY_COST",
                    currentUsage = usage.estimatedHourlyCost.toInt(),
                    limit = usage.hourlyCostThreshold.toInt(),
                    timeToReset = System.currentTimeMillis() + ONE_HOUR_MS
                )
                usage.toStatus(
                    isLimited = true,
                    reason = "Hourly AI cost threshold exceeded",
                    resetTime = System.currentTimeMillis() + ONE_HOUR_MS
                )
            }

            else -> {
                val isNearLimit = usage.isNearDailyLimit || usage.isNearMonthlyLimit
                analyticsTracker.trackAIChatUsage(
                    userId = userId,
                    dailyMessagesUsed = usage.dailyMessages,
                    monthlyTokensUsed = usage.monthlyTokens,
                    isNearLimit = isNearLimit
                )
                usage.toStatus(
                    isLimited = false,
                    reason = when {
                        usage.isNearDailyLimit && usage.isNearMonthlyLimit ->
                            "Approaching daily operation and monthly token limits"
                        usage.isNearDailyLimit ->
                            "Approaching daily AI operation limit (${usage.dailyMessagesRemaining} remaining)"
                        usage.isNearMonthlyLimit ->
                            "Approaching monthly AI token limit (${usage.monthlyTokensRemaining} remaining)"
                        else -> null
                    }
                )
            }
        }
    } catch (error: Exception) {
        Timber.e(error, "AI quota verification failed closed")
        RateLimitStatus(
            isLimited = true,
            reason = "Unable to verify AI usage limits",
            messagesRemaining = 0,
            tokensRemaining = 0
        )
    }

    override suspend fun getUsageStats(userId: String): AIUsageStats {
        val usage = loadUsage(userId)
        return AIUsageStats(
            dailyMessages = usage.dailyMessages,
            monthlyTokens = usage.monthlyTokens,
            hourlyTokens = usage.hourlyTokens,
            estimatedMonthlyCost = estimateCost(usage.monthlyTokens),
            dailyMessageLimit = usage.dailyMessageLimit,
            monthlyTokenLimit = usage.monthlyTokenLimit,
            dailyMessagesRemaining = usage.dailyMessagesRemaining,
            monthlyTokensRemaining = usage.monthlyTokensRemaining,
            isNearDailyLimit = usage.isNearDailyLimit,
            isNearMonthlyLimit = usage.isNearMonthlyLimit
        )
    }

    private suspend fun loadUsage(userId: String): UsageSnapshot {
        require(userId.isNotBlank()) { "User ID is required for AI quota checks" }

        val now = System.currentTimeMillis()
        val dailyLimit = remoteConfig.getAiDailyMessageLimit().getOrThrow()
            .validatedLimit("daily AI operation limit")
        val monthlyLimit = remoteConfig.getAiMonthlyTokenLimit().getOrThrow()
            .validatedLimit("monthly AI token limit")
        val hourlyCostThreshold = remoteConfig.getAiCostThresholdPerHour().getOrThrow()
        require(hourlyCostThreshold > 0.0) { "Hourly AI cost threshold must be positive" }

        val dailyMessages = aiUsageDao.countCallsSince(userId, todayStart())
        val monthlyTokens = aiUsageDao.tokenUsageSince(userId, monthStart())
        val hourlyTokens = aiUsageDao.tokenUsageSince(userId, now - ONE_HOUR_MS)

        return UsageSnapshot(
            dailyMessages = dailyMessages,
            monthlyTokens = monthlyTokens,
            hourlyTokens = hourlyTokens,
            dailyMessageLimit = dailyLimit,
            monthlyTokenLimit = monthlyLimit,
            hourlyCostThreshold = hourlyCostThreshold
        )
    }

    private fun Long.validatedLimit(name: String): Int {
        require(this in 1..Int.MAX_VALUE.toLong()) { "$name must be between 1 and ${Int.MAX_VALUE}" }
        return toInt()
    }

    private fun estimateCost(tokens: Int): Double {
        val inputTokens = tokens * INPUT_TOKEN_SHARE
        val outputTokens = tokens - inputTokens
        return (inputTokens / 1_000.0) * COST_PER_1K_INPUT_TOKENS +
            (outputTokens / 1_000.0) * COST_PER_1K_OUTPUT_TOKENS
    }

    private fun todayStart(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun monthStart(): Long = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun tomorrowMidnight(): Long = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun nextMonthStart(): Long = Calendar.getInstance().apply {
        add(Calendar.MONTH, 1)
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private data class UsageSnapshot(
        val dailyMessages: Int,
        val monthlyTokens: Int,
        val hourlyTokens: Int,
        val dailyMessageLimit: Int,
        val monthlyTokenLimit: Int,
        val hourlyCostThreshold: Double
    ) {
        val dailyMessagesRemaining = (dailyMessageLimit - dailyMessages).coerceAtLeast(0)
        val monthlyTokensRemaining = (monthlyTokenLimit - monthlyTokens).coerceAtLeast(0)
        val isNearDailyLimit = dailyMessages >= dailyMessageLimit * WARNING_THRESHOLD
        val isNearMonthlyLimit = monthlyTokens >= monthlyTokenLimit * WARNING_THRESHOLD
        val estimatedHourlyCost = estimateTokenCost(hourlyTokens)

        fun toStatus(
            isLimited: Boolean,
            reason: String?,
            resetTime: Long? = null
        ) = RateLimitStatus(
            isLimited = isLimited,
            reason = reason,
            resetTime = resetTime,
            messagesRemaining = dailyMessagesRemaining,
            tokensRemaining = monthlyTokensRemaining,
            isNearLimit = isNearDailyLimit || isNearMonthlyLimit
        )

        private fun estimateTokenCost(tokens: Int): Double {
            val inputTokens = tokens * INPUT_TOKEN_SHARE
            val outputTokens = tokens - inputTokens
            return (inputTokens / 1_000.0) * COST_PER_1K_INPUT_TOKENS +
                (outputTokens / 1_000.0) * COST_PER_1K_OUTPUT_TOKENS
        }
    }

    private companion object {
        const val ONE_HOUR_MS = 3_600_000L
        const val WARNING_THRESHOLD = 0.8
        const val INPUT_TOKEN_SHARE = 0.3
        const val COST_PER_1K_INPUT_TOKENS = 0.01
        const val COST_PER_1K_OUTPUT_TOKENS = 0.03
    }
}
