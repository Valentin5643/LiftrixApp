package com.example.liftrix.ui.chat

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.liftrix.ui.chat.components.ConversationHistoryPane
import org.junit.Rule
import org.junit.Test

class ChatbotScreenTest {
    @get:Rule val compose = createComposeRule()
    @Test fun emptyHistoryOffersNewChat() {
        compose.setContent { ConversationHistoryPane(emptyList(), null, true, {}, {}, {}, {}) }
        compose.onNodeWithText("New chat").assertIsDisplayed()
    }
}
