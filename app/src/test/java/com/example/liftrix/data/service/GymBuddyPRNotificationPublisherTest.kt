package com.example.liftrix.data.service

import com.example.liftrix.data.local.dao.GymBuddyDao
import com.example.liftrix.data.local.dao.PRNotificationDao
import com.example.liftrix.data.local.entity.GymBuddyEntity
import com.example.liftrix.data.local.entity.PRNotificationEntity
import com.example.liftrix.data.local.entity.PersonalRecordEntity
import com.example.liftrix.domain.service.PRType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GymBuddyPRNotificationPublisherTest {

    private lateinit var gymBuddyDao: GymBuddyDao
    private lateinit var prNotificationDao: PRNotificationDao
    private lateinit var publisher: GymBuddyPRNotificationPublisher

    @Before
    fun setUp() {
        gymBuddyDao = mockk()
        prNotificationDao = mockk()
        publisher = GymBuddyPRNotificationPublisher(gymBuddyDao, prNotificationDao)
    }

    @Test
    fun `mutual gym buddy receives notification for new PR`() = runTest {
        val notification = slot<PRNotificationEntity>()
        coEvery { gymBuddyDao.getGymBuddies(OWNER_ID) } returns listOf(gymBuddy(BUDDY_ID))
        coEvery { gymBuddyDao.areMutualGymBuddies(OWNER_ID, BUDDY_ID) } returns true
        coEvery { prNotificationDao.insertPRNotification(capture(notification)) } returns 1L
        coEvery { gymBuddyDao.updatePrNotificationSent(OWNER_ID, BUDDY_ID, any()) } returns 1

        publisher.publish(listOf(personalRecord()))

        assertEquals(OWNER_ID, notification.captured.fromUserId)
        assertEquals(BUDDY_ID, notification.captured.toUserId)
        assertEquals(WORKOUT_ID, notification.captured.workoutId)
        assertEquals("Bench Press", notification.captured.exerciseName)
        assertEquals(PRType.ONE_RM.name, notification.captured.prType)
        assertTrue(notification.captured.cooldownKey.contains(WORKOUT_ID))
        coVerify { gymBuddyDao.updatePrNotificationSent(OWNER_ID, BUDDY_ID, any()) }
    }

    @Test
    fun `non gym buddies do not receive notifications`() = runTest {
        coEvery { gymBuddyDao.getGymBuddies(OWNER_ID) } returns emptyList()

        publisher.publish(listOf(personalRecord()))

        coVerify(exactly = 0) { prNotificationDao.insertPRNotification(any()) }
        coVerify(exactly = 0) { gymBuddyDao.updatePrNotificationSent(any(), any(), any()) }
    }

    @Test
    fun `removed or one sided gym buddy does not receive notification`() = runTest {
        coEvery { gymBuddyDao.getGymBuddies(OWNER_ID) } returns listOf(gymBuddy(BUDDY_ID))
        coEvery { gymBuddyDao.areMutualGymBuddies(OWNER_ID, BUDDY_ID) } returns false

        publisher.publish(listOf(personalRecord()))

        coVerify(exactly = 0) { prNotificationDao.insertPRNotification(any()) }
        coVerify(exactly = 0) { gymBuddyDao.updatePrNotificationSent(any(), any(), any()) }
    }

    @Test
    fun `no gym buddies is a no-op`() = runTest {
        coEvery { gymBuddyDao.getGymBuddies(OWNER_ID) } returns emptyList()

        publisher.publish(listOf(personalRecord()))

        coVerify(exactly = 0) { gymBuddyDao.areMutualGymBuddies(any(), any()) }
        coVerify(exactly = 0) { prNotificationDao.insertPRNotification(any()) }
    }

    private fun personalRecord(): PersonalRecordEntity {
        return PersonalRecordEntity(
            id = PR_ID,
            userId = OWNER_ID,
            exerciseName = "Bench Press",
            prType = PRType.ONE_RM.name,
            weightKg = 100.0,
            reps = 3,
            volume = 300.0,
            estimatedOneRM = 110.0,
            achievedAt = 1_700_000_000_000,
            workoutId = WORKOUT_ID,
            previousBest = 105.0,
            improvementPercent = 0.047
        )
    }

    private fun gymBuddy(buddyId: String): GymBuddyEntity {
        return GymBuddyEntity(
            id = "relation-$buddyId",
            userId = OWNER_ID,
            buddyId = buddyId,
            createdAt = 1_700_000_000_000,
            pairedViaQr = true
        )
    }

    private companion object {
        const val OWNER_ID = "owner-user"
        const val BUDDY_ID = "buddy-user"
        const val WORKOUT_ID = "workout-1"
        const val PR_ID = "pr-1"
    }
}
