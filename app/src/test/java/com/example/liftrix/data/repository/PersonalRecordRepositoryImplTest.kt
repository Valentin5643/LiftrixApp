package com.example.liftrix.data.repository

import com.example.liftrix.data.local.dao.PersonalRecordDao
import com.example.liftrix.data.local.entity.PersonalRecordEntity
import com.example.liftrix.data.service.GymBuddyPRNotificationPublisher
import com.example.liftrix.domain.service.PRType
import com.example.liftrix.domain.service.PersonalRecord
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PersonalRecordRepositoryImplTest {

    private lateinit var personalRecordDao: PersonalRecordDao
    private lateinit var publisher: GymBuddyPRNotificationPublisher
    private lateinit var repository: PersonalRecordRepositoryImpl

    @Before
    fun setUp() {
        personalRecordDao = mockk()
        publisher = mockk()
        repository = PersonalRecordRepositoryImpl(personalRecordDao, publisher)
    }

    @Test
    fun `savePRs still persists personal records and publishes buddy visibility`() = runTest {
        val personalRecords = listOf(personalRecord())
        coEvery { personalRecordDao.insertPRs(any<List<PersonalRecordEntity>>()) } returns listOf(1L)
        coEvery { publisher.publish(any()) } just Runs

        val result = repository.savePRs(
            personalRecords = personalRecords,
            userId = USER_ID,
            workoutId = WORKOUT_ID
        )

        assertTrue(result.isSuccess)
        coVerify { personalRecordDao.insertPRs(match { it.size == 1 && it.first().userId == USER_ID }) }
        coVerify { publisher.publish(match { it.size == 1 && it.first().workoutId == WORKOUT_ID }) }
    }

    private fun personalRecord(): PersonalRecord {
        return PersonalRecord(
            exerciseName = "Bench Press",
            prType = PRType.ONE_RM,
            weight = 100.0,
            reps = 3,
            estimatedOneRM = 110.0,
            volume = 300.0,
            achievedAt = 1_700_000_000_000,
            previousBest = 105.0,
            improvementPercent = 0.047
        )
    }

    private companion object {
        const val USER_ID = "owner-user"
        const val WORKOUT_ID = "workout-1"
    }
}
