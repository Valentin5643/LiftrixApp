package com.example.liftrix.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.liftrix.data.local.LiftrixDatabase
import com.example.liftrix.data.local.entity.ChatHistoryEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatHistoryDaoTest {
    private lateinit var database: LiftrixDatabase
    private lateinit var dao: ChatHistoryDao
    @Before fun setUp() { database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), LiftrixDatabase::class.java).allowMainThreadQueries().build(); dao = database.chatHistoryDao() }
    @After fun tearDown() = database.close()

    @Test fun summariesAreScopedOrderedAndTombstonesHideRestores() = runBlocking {
        dao.insertMessageWithConversation(message("u1-a", "u1", "a", "USER", 10), "First")
        dao.insertMessageWithConversation(message("u1-b", "u1", "b", "USER", 20), "Second")
        dao.insertMessageWithConversation(message("u2-b", "u2", "b", "USER", 30), "Other user")
        assertEquals(listOf("b", "a"), dao.observeConversationSummaries("u1").first().map { it.conversationId })
        dao.tombstoneAndDeleteConversation("u1", "b", 40)
        dao.upsertFromRemote(message("restored", "u1", "b", "AI_RESPONSE", 50))
        assertEquals(listOf("a"), dao.observeConversationSummaries("u1").first().map { it.conversationId })
        assertTrue(dao.getConversationMessages("u1", "b").first().isEmpty())
    }

    @Test fun equalTimestampsUseUserAssistantSystemThenId() = runBlocking {
        dao.insertMessageWithConversation(message("z", "u", "c", "SYSTEM", 1), "Title")
        dao.insertMessageWithConversation(message("b", "u", "c", "AI_RESPONSE", 1), "Title")
        dao.insertMessageWithConversation(message("a", "u", "c", "USER", 1), "Title")
        assertEquals(listOf("USER", "AI_RESPONSE", "SYSTEM"), dao.getConversationMessages("u", "c").first().map { it.messageType })
    }

    private fun message(id: String, user: String, conversation: String, type: String, time: Long) = ChatHistoryEntity(
        id = id, userId = user, conversationId = conversation, messageType = type, content = id, createdAt = time
    )
}
