package com.example.liftrix.sync

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.Operation
import androidx.work.WorkManager
import com.example.liftrix.data.service.ProfileCleanupService
import com.example.liftrix.data.sync.OfflineQueueManager
import com.example.liftrix.data.sync.RealtimeSyncService
import com.example.liftrix.domain.repository.SyncStatusRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SyncCoordinatorTruthfulnessTest {
    private val context = mockk<Context>()
    private val workManager = mockk<WorkManager>()
    private val restoreGate = StartupRestoreGate()

    @Before
    fun setUp() {
        mockkStatic(WorkManager::class)
        every { WorkManager.getInstance(context) } returns workManager
    }

    @After
    fun tearDown() {
        unmockkStatic(WorkManager::class)
    }

    @Test
    fun `eligible entity request enqueues canonical unique work`() = runTest {
        every {
            workManager.enqueueUniqueWork(
                any(),
                ExistingWorkPolicy.KEEP,
                any<OneTimeWorkRequest>()
            )
        } returns mockk<Operation>(relaxed = true)

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
    fun `incomplete restore is an explicit successful no-op without enqueue`() = runTest {
        val result = coordinator().triggerImmediateSync("user-a")

        assertTrue(result.isSuccess)
        verify(exactly = 0) {
            workManager.enqueueUniqueWork(any(), any<ExistingWorkPolicy>(), any<OneTimeWorkRequest>())
        }
    }

    private fun coordinator() = SyncCoordinator(
        context = context,
        realtimeSyncService = mockk<RealtimeSyncService>(relaxed = true),
        offlineQueueManager = mockk<OfflineQueueManager>(relaxed = true),
        syncStatusRepository = mockk<SyncStatusRepository>(relaxed = true),
        profileCleanupService = mockk<ProfileCleanupService>(relaxed = true),
        startupRestoreGate = restoreGate
    )
}
