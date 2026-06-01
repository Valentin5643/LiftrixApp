package com.example.liftrix.data.local.dao

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.liftrix.data.local.LiftrixDatabase
import com.example.liftrix.data.local.entity.DeadLetterQueueEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeadLetterQueueDaoTest {

    private lateinit var database: LiftrixDatabase
    private lateinit var deadLetterQueueDao: DeadLetterQueueDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().context,
            LiftrixDatabase::class.java
        ).allowMainThreadQueries().build()

        deadLetterQueueDao = database.deadLetterQueueDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun clearOldItemsDeletesEntriesPastCutoffForUser() = runBlocking {
        val userId = "test_user_1"
        val otherUserId = "test_user_2"
        val now = System.currentTimeMillis()
        val cutoff = now - (30L * 24 * 60 * 60 * 1000L)

        val oldItem = DeadLetterQueueEntity(
            id = "old_item",
            originalId = "old_item",
            userId = userId,
            entityType = "WORKOUT",
            entityId = "workout_old",
            operation = "SYNC_UPLOAD",
            data = "{}",
            priority = 1,
            retryCount = 5,
            createdAt = now - 40_000L,
            failedAt = cutoff - 1_000L,
            errorCategory = "VALIDATION_FAILURE",
            errorMessage = "old"
        )

        val recentItem = DeadLetterQueueEntity(
            id = "recent_item",
            originalId = "recent_item",
            userId = userId,
            entityType = "WORKOUT",
            entityId = "workout_recent",
            operation = "SYNC_UPLOAD",
            data = "{}",
            priority = 1,
            retryCount = 2,
            createdAt = now - 20_000L,
            failedAt = cutoff + 1_000L,
            errorCategory = "VALIDATION_FAILURE",
            errorMessage = "recent"
        )

        val otherUserOldItem = DeadLetterQueueEntity(
            id = "other_old_item",
            originalId = "other_old_item",
            userId = otherUserId,
            entityType = "WORKOUT",
            entityId = "workout_other_old",
            operation = "SYNC_UPLOAD",
            data = "{}",
            priority = 1,
            retryCount = 5,
            createdAt = now - 40_000L,
            failedAt = cutoff - 1_000L,
            errorCategory = "VALIDATION_FAILURE",
            errorMessage = "other"
        )

        deadLetterQueueDao.insert(oldItem)
        deadLetterQueueDao.insert(recentItem)
        deadLetterQueueDao.insert(otherUserOldItem)

        deadLetterQueueDao.clearOldItems(userId, cutoff)

        val remaining = deadLetterQueueDao.getDeadLetterItems(userId)
        assert(remaining.size == 1) { "Expected 1 remaining item after cleanup" }
        assert(remaining.first().id == "recent_item") { "Expected recent item to remain" }

        val otherUserRemaining = deadLetterQueueDao.getDeadLetterItems(otherUserId)
        assert(otherUserRemaining.size == 1) { "Expected other user's item to remain" }
        assert(otherUserRemaining.first().id == "other_old_item") {
            "Expected other user's item to remain after cleanup"
        }
    }
}
