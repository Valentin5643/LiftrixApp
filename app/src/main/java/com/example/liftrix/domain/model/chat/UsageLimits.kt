package com.example.liftrix.domain.model.chat

/**
 * Represents the current usage limits and remaining quota for the chat feature.
 */
data class UsageLimits(
    val dailyMessagesRemaining: Int,
    val monthlyTokensRemaining: Int,
    val isNearDailyLimit: Boolean,
    val isNearMonthlyLimit: Boolean
)