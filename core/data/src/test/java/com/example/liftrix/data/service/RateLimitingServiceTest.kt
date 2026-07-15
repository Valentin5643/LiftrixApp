package com.example.liftrix.data.service

import com.example.liftrix.data.local.dao.AiUsageDao
import com.example.liftrix.data.remote.config.RemoteConfigManager
import com.example.liftrix.domain.service.AnalyticsTracker
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RateLimitingServiceTest {

    @Test
    fun `usage stats come from the requested user's ledger rows`() = runTest {
        val dao = mockk<AiUsageDao>()
        val remoteConfig = mockk<RemoteConfigManager>()
        val analytics = mockk<AnalyticsTracker>(relaxed = true)
        val userId = "user-a"

        coEvery { remoteConfig.getAiDailyMessageLimit() } returns Result.success(50L)
        coEvery { remoteConfig.getAiMonthlyTokenLimit() } returns Result.success(100_000L)
        coEvery { remoteConfig.getAiCostThresholdPerHour() } returns Result.success(1.0)
        coEvery { dao.countCallsSince(userId, any()) } returns 7
        coEvery { dao.tokenUsageSince(userId, any()) } returnsMany listOf(12_000, 500)

        val service = RateLimitingService(dao, remoteConfig, analytics)
        val stats = service.getUsageStats(userId)

        assertEquals(7, stats.dailyMessages)
        assertEquals(12_000, stats.monthlyTokens)
        assertEquals(500, stats.hourlyTokens)
        assertEquals(43, stats.dailyMessagesRemaining)
        assertEquals(88_000, stats.monthlyTokensRemaining)
        assertFalse(stats.isNearDailyLimit)
        assertFalse(stats.isNearMonthlyLimit)
        coVerify(exactly = 1) { dao.countCallsSince(userId, any()) }
        coVerify(exactly = 2) { dao.tokenUsageSince(userId, any()) }
    }
}
