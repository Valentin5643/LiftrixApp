package com.example.liftrix.domain.service

interface RateLimitingServiceContract {
    suspend fun checkLimits(userId: String): RateLimitStatus
    suspend fun getUsageStats(userId: String): AIUsageStats
}

interface AbusePreventionServiceContract {
    suspend fun detectAbuse(userId: String, message: String): AbuseDetection
}

data class AIUsageStats(
    val dailyMessages: Int = 0,
    val monthlyTokens: Int = 0,
    val hourlyTokens: Int = 0,
    val estimatedMonthlyCost: Double = 0.0
)
