package com.example.liftrix.service

import app.cash.turbine.test
import com.example.liftrix.domain.cache.CacheInvalidationSignal
import com.example.liftrix.domain.event.DomainEvent
import com.example.liftrix.domain.usecase.analytics.AnalyticsQueryUseCase
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CacheInvalidationServiceImplTest {
    private val analyticsQueryUseCase = mockk<AnalyticsQueryUseCase>(relaxed = true)
    private val service = CacheInvalidationServiceImpl(analyticsQueryUseCase)

    @Test
    fun workoutInvalidationPublishesWorkoutChangedAndInvalidatesAnalytics() = runTest {
        every { analyticsQueryUseCase.invalidateCacheForUser("user-1") } returns Unit

        service.invalidationEvents.test {
            val result = service.invalidateWorkoutData(
                userId = "user-1",
                workoutDate = LocalDate(2026, 5, 31),
                exerciseIds = listOf("bench"),
                workoutDuration = 45
            )

            assertTrue(result.isSuccess)
            val event = awaitItem()
            val workoutChanged = assertIs<DomainEvent.WorkoutChanged>(event)
            assertEquals("user-1", workoutChanged.userId)
            assertEquals(LocalDate(2026, 5, 31), workoutChanged.workoutDate)
            assertEquals(listOf("bench"), workoutChanged.exerciseIds)
            assertEquals(setOf(CacheInvalidationSignal.WORKOUT_CHANGED), workoutChanged.invalidationSignals)
            verify(exactly = 1) { analyticsQueryUseCase.invalidateCacheForUser("user-1") }
        }
    }

    @Test
    fun profileInvalidationPublishesWithoutAnalyticsInvalidation() = runTest {
        service.invalidationEvents.test {
            val result = service.publish(DomainEvent.ProfileChanged(userId = "user-1"))

            assertTrue(result.isSuccess)
            val event = assertIs<DomainEvent.ProfileChanged>(awaitItem())
            assertEquals("user-1", event.userId)
            assertEquals(setOf(CacheInvalidationSignal.PROFILE_CHANGED), event.invalidationSignals)
            verify(exactly = 0) { analyticsQueryUseCase.invalidateCacheForUser(any()) }
        }
    }
}
