package com.example.liftrix.data.service

import com.example.liftrix.core.identity.UserId
import com.example.liftrix.data.local.dao.AiUsageDao
import com.example.liftrix.data.remote.config.RemoteConfigManager
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.service.AbusePreventionServiceContract
import com.example.liftrix.domain.service.RateLimitingServiceContract
import com.example.liftrix.domain.usecase.admin.CheckAdminPermissionsUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PaidAiCallExecutorTest {

    private lateinit var remoteConfig: RemoteConfigManager
    private lateinit var abusePreventionService: AbusePreventionServiceContract
    private lateinit var rateLimitingService: RateLimitingServiceContract
    private lateinit var aiUsageDao: AiUsageDao
    private lateinit var checkAdminPermissionsUseCase: CheckAdminPermissionsUseCase
    private lateinit var authRepository: AuthRepository
    private lateinit var executor: PaidAiCallExecutor

    @Before
    fun setUp() {
        remoteConfig = mockk()
        abusePreventionService = mockk()
        rateLimitingService = mockk()
        aiUsageDao = mockk(relaxed = true)
        checkAdminPermissionsUseCase = mockk()
        authRepository = mockk()
        executor = PaidAiCallExecutor(
            remoteConfig = remoteConfig,
            abusePreventionService = abusePreventionService,
            rateLimitingService = rateLimitingService,
            aiUsageDao = aiUsageDao,
            checkAdminPermissionsUseCase = checkAdminPermissionsUseCase,
            authRepository = authRepository
        )
    }

    @Test
    fun `matching authenticated UID proceeds into paid controls`() = runTest {
        coEvery { authRepository.getCurrentUserId() } returns UserId(USER_A)
        coEvery { checkAdminPermissionsUseCase(USER_A) } returns Result.success(true)
        coEvery { remoteConfig.ensurePaidAiControlsReady() } returns Result.success(Unit)
        coEvery { remoteConfig.isAiChatEnabled() } returns Result.success(false)

        val error = captureFailure {
            executor.execute<String>(request(USER_A)) {
                error("dispatch must not run while the kill switch is disabled")
            }
        }

        assertTrue(error is PaidAiDisabledException)
        coVerify(exactly = 1) { checkAdminPermissionsUseCase(USER_A) }
        coVerify(exactly = 1) { remoteConfig.ensurePaidAiControlsReady() }
        coVerify(exactly = 1) { remoteConfig.isAiChatEnabled() }
        coVerify(exactly = 0) { aiUsageDao.insert(any()) }
    }

    @Test
    fun `mismatched request UID is denied before every paid downstream call`() = runTest {
        coEvery { authRepository.getCurrentUserId() } returns UserId(USER_A)

        val error = captureFailure {
            executor.execute<String>(request(USER_B)) {
                error("dispatch must not run for mismatched identity")
            }
        }

        assertTrue(error is PaidAiAccessDeniedException)
        verifyNoPaidDownstreamCalls()
    }

    @Test
    fun `missing authenticated UID is denied before every paid downstream call`() = runTest {
        coEvery { authRepository.getCurrentUserId() } returns null

        val error = captureFailure {
            executor.execute<String>(request(USER_B)) {
                error("dispatch must not run without authentication")
            }
        }

        assertTrue(error is PaidAiAccessDeniedException)
        verifyNoPaidDownstreamCalls()
    }

    private suspend fun captureFailure(block: suspend () -> Unit): Throwable = try {
        block()
        AssertionError("Expected paid AI execution to fail")
    } catch (error: Throwable) {
        error
    }

    private fun verifyNoPaidDownstreamCalls() {
        coVerify(exactly = 0) { checkAdminPermissionsUseCase(any()) }
        coVerify(exactly = 0) { remoteConfig.ensurePaidAiControlsReady() }
        coVerify(exactly = 0) { remoteConfig.isAiChatEnabled() }
        coVerify(exactly = 0) { abusePreventionService.detectAbuse(any(), any()) }
        coVerify(exactly = 0) { rateLimitingService.checkLimits(any()) }
        coVerify(exactly = 0) { aiUsageDao.insert(any()) }
    }

    private fun request(userId: String) = PaidAiCallRequest(
        userId = userId,
        operation = PaidAiOperation.CHAT_RESPONSE,
        model = "test-model",
        abuseContent = "Create a safe workout plan",
        estimatedInputTokens = 12
    )

    private companion object {
        const val USER_A = "authenticated-user-a"
        const val USER_B = "authenticated-user-b"
    }
}
