package com.example.liftrix.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.liftrix.data.local.LiftrixDatabase
import com.example.liftrix.data.local.entity.AiUsageEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AiUsageDaoTest {
    private lateinit var database: LiftrixDatabase
    private lateinit var dao: AiUsageDao

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), LiftrixDatabase::class.java)
            .allowMainThreadQueries().build()
        dao = database.aiUsageDao()
    }

    @After fun tearDown() = database.close()

    @Test fun aggregatesOnlyRequestedUserAndWindow() = runBlocking {
        dao.insert(event("a", "user-a", 100, 10))
        dao.insert(event("b", "user-a", 200, 20))
        dao.insert(event("c", "user-b", 200, 999))

        assertEquals(1, dao.countCallsSince("user-a", 150))
        assertEquals(20, dao.tokenUsageSince("user-a", 150))
        assertEquals(30, dao.tokenUsageSince("user-a", 0))
    }

    private fun event(id: String, userId: String, createdAt: Long, tokens: Int) = AiUsageEntity(
        id = id, userId = userId, createdAt = createdAt, operation = "CHAT_RESPONSE",
        model = "test", inputTokens = tokens / 2, outputTokens = tokens - tokens / 2,
        totalTokens = tokens, successCategory = "MODEL_RESPONSE"
    )
}
