package com.example.liftrix.sync

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.Operation
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.liftrix.data.service.ProfileCleanupService
import com.example.liftrix.data.sync.OfflineQueueManager
import com.example.liftrix.data.sync.RealtimeSyncService
import com.example.liftrix.domain.repository.SyncStatusRepository
import com.google.common.util.concurrent.Futures
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SyncCoordinatorTruthfulnessTest {
    private lateinit var context: Context
    private lateinit var workManager: WorkManager
    private lateinit var restoreGate: StartupRestoreGate
    private lateinit var realtimeSyncService: RealtimeSyncService
    private lateinit var offlineQueueManager: OfflineQueueManager
    private lateinit var syncStatusRepository: SyncStatusRepository
    private lateinit var profileCleanupService: ProfileCleanupService

    @Before
    fun setUp() {
        context = mockk()
        workManager = mockk(relaxed = true)
        restoreGate = StartupRestoreGate()
        realtimeSyncService = mockk(relaxed = true)
        offlineQueueManager = mockk(relaxed = true)
        syncStatusRepository = mockk(relaxed = true)
        profileCleanupService = mockk(relaxed = true)

        mockkObject(WorkManager.Companion)
        every { WorkManager.getInstance(context) } returns workManager
    }

    @After
    fun tearDown() {
        unmockkObject(WorkManager.Companion)
    }

    @Test
    fun `eligible entity request enqueues canonical unique work`() = runTest {
        every {
            workManager.enqueueUniqueWork(
                any(),
                ExistingWorkPolicy.KEEP,
                any<OneTimeWorkRequest>()
            )
        } returns failedOperation("entity monitor sentinel")

        val result = coordinator().triggerEntitySync("user-a", "workout")

        assertTrue(result.isSuccess)
        verify(exactly = 1) {
            workManager.enqueueUniqueWork(
                any(),
                ExistingWorkPolicy.KEEP,
                any<OneTimeWorkRequest>()
            )
        }
    }

    @Test
    fun `unknown entity request is rejected without enqueue`() = runTest {
        val result = coordinator().triggerEntitySync("user-a", "unknown-type")

        assertTrue(result.isFailure)
        verify(exactly = 0) {
            workManager.enqueueUniqueWork(any(), any<ExistingWorkPolicy>(), any<OneTimeWorkRequest>())
        }
    }

    @Test
    fun `incomplete restore is an explicit failure without enqueue`() = runTest {
        val result = coordinator().triggerImmediateSync("user-a")

        assertTrue(result.isFailure)
        verify(exactly = 0) {
            workManager.enqueueUniqueWork(any(), any<ExistingWorkPolicy>(), any<OneTimeWorkRequest>())
        }
    }

    @Test
    fun `immediate sync enqueues required before optional and isolates optional failure`() = runTest {
        val userId = "user-a"
        val requiredWorkName = "liftrix_unified_sync_immediate_$userId"
        val optionalWorkName = ChatSyncWorker.getImmediateWorkName(userId)
        val enqueuedNames = mutableListOf<String>()
        restoreGate.transition(userId, StartupRestoreState.RESTORE_COMPLETE, "test")
        val requiredOperation = failedOperation("required monitor sentinel")

        every {
            workManager.enqueueUniqueWork(any(), ExistingWorkPolicy.KEEP, any<OneTimeWorkRequest>())
        } answers {
            val workName = firstArg<String>()
            enqueuedNames += workName
            if (workName == optionalWorkName) {
                throw IllegalStateException("optional chat unavailable")
            }
            requiredOperation
        }

        val result = coordinator().triggerImmediateSync(userId)

        assertTrue("Immediate sync failed: ${result.exceptionOrNull()}", result.isSuccess)
        assertEquals(listOf(requiredWorkName, optionalWorkName), enqueuedNames)
        verify(exactly = 0) {
            workManager.beginUniqueWork(any(), any<ExistingWorkPolicy>(), any<OneTimeWorkRequest>())
        }
    }

    @Test
    fun `immediate required enqueue failure skips optional work`() = runTest {
        val userId = "user-a"
        val enqueuedNames = mutableListOf<String>()
        restoreGate.transition(userId, StartupRestoreState.RESTORE_COMPLETE, "test")
        every {
            workManager.enqueueUniqueWork(any(), ExistingWorkPolicy.KEEP, any<OneTimeWorkRequest>())
        } answers {
            enqueuedNames += firstArg<String>()
            throw IllegalStateException("required enqueue unavailable")
        }

        val result = coordinator().triggerImmediateSync(userId)

        assertTrue(result.isFailure)
        assertEquals(
            "Immediate sync failure occurred before required enqueue: ${result.exceptionOrNull()}",
            listOf("liftrix_unified_sync_immediate_$userId"),
            enqueuedNames
        )
    }

    @Test
    fun `startup sync enqueues required before optional and isolates optional failure`() = runTest {
        val userId = "user-a"
        val requiredWorkName = "startup_sync_$userId"
        val optionalWorkName = ChatSyncWorker.getStartupWorkName(userId)
        val enqueuedNames = mutableListOf<String>()
        stubStartupPrerequisites(userId)
        val requiredOperation = failedOperation("required monitor sentinel")

        every {
            workManager.enqueueUniqueWork(any(), ExistingWorkPolicy.KEEP, any<OneTimeWorkRequest>())
        } answers {
            val workName = firstArg<String>()
            enqueuedNames += workName
            if (workName == optionalWorkName) {
                throw IllegalStateException("optional chat unavailable")
            }
            requiredOperation
        }

        val result = coordinator().triggerStartupSync(userId, source = "test")

        assertTrue(result.isSuccess)
        assertEquals(listOf(requiredWorkName, optionalWorkName), enqueuedNames)
        verify(exactly = 0) {
            workManager.beginUniqueWork(any(), any<ExistingWorkPolicy>(), any<OneTimeWorkRequest>())
        }
    }

    @Test
    fun `startup required enqueue failure skips optional work`() = runTest {
        val userId = "user-a"
        val enqueuedNames = mutableListOf<String>()
        stubStartupPrerequisites(userId)
        every {
            workManager.enqueueUniqueWork(any(), ExistingWorkPolicy.KEEP, any<OneTimeWorkRequest>())
        } answers {
            enqueuedNames += firstArg<String>()
            throw IllegalStateException("required enqueue unavailable")
        }

        val result = coordinator().triggerStartupSync(userId, source = "test")

        assertTrue(result.isFailure)
        assertEquals(listOf("startup_sync_$userId"), enqueuedNames)
        assertEquals(StartupRestoreState.RESTORE_FAILED, restoreGate.currentState(userId))
    }

    @Test
    fun `repeated immediate sync keeps stable user scoped required and optional names`() = runTest {
        val userId = "user-a"
        val enqueuedNames = mutableListOf<String>()
        val policies = mutableListOf<ExistingWorkPolicy>()
        restoreGate.transition(userId, StartupRestoreState.RESTORE_COMPLETE, "test")
        every {
            workManager.enqueueUniqueWork(any(), any<ExistingWorkPolicy>(), any<OneTimeWorkRequest>())
        } answers {
            enqueuedNames += firstArg<String>()
            policies += secondArg<ExistingWorkPolicy>()
            failedOperation("enqueue observer sentinel")
        }

        val firstResult = coordinator().triggerImmediateSync(userId)
        val secondResult = coordinator().triggerImmediateSync(userId)

        assertTrue("First immediate sync failed: ${firstResult.exceptionOrNull()}", firstResult.isSuccess)
        assertTrue("Second immediate sync failed: ${secondResult.exceptionOrNull()}", secondResult.isSuccess)
        assertEquals(
            listOf(
                "liftrix_unified_sync_immediate_$userId",
                ChatSyncWorker.getImmediateWorkName(userId),
                "liftrix_unified_sync_immediate_$userId",
                ChatSyncWorker.getImmediateWorkName(userId)
            ),
            enqueuedNames
        )
        assertTrue(policies.all { it == ExistingWorkPolicy.KEEP })
        assertNotEquals(
            ChatSyncWorker.getImmediateWorkName(userId),
            ChatSyncWorker.getImmediateWorkName("user-b")
        )
        assertNotEquals(
            ChatSyncWorker.getStartupWorkName(userId),
            ChatSyncWorker.getStartupWorkName("user-b")
        )
    }

    private fun stubStartupPrerequisites(userId: String) {
        every {
            workManager.getWorkInfosForUniqueWork("startup_sync_$userId")
        } returns Futures.immediateFuture(emptyList<WorkInfo>())
        coEvery {
            profileCleanupService.performStartupCleanup(userId)
        } returns Result.failure(IllegalStateException("cleanup not relevant to scheduling test"))
        coEvery {
            offlineQueueManager.queueOperation(
                userId = userId,
                entityType = any(),
                entityId = any(),
                operation = "FETCH",
                data = any()
            )
        } returns Result.success(Unit)
    }

    private fun failedOperation(message: String): Operation {
        val operation = mockk<Operation>()
        every { operation.result } returns Futures.immediateFailedFuture(
            IllegalStateException(message)
        )
        return operation
    }

    private fun coordinator() = SyncCoordinator(
        context = context,
        realtimeSyncService = realtimeSyncService,
        offlineQueueManager = offlineQueueManager,
        syncStatusRepository = syncStatusRepository,
        profileCleanupService = profileCleanupService,
        startupRestoreGate = restoreGate
    )
}
