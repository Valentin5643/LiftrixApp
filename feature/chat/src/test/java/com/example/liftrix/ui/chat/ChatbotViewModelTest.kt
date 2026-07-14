package com.example.liftrix.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatbotViewModelTest {
    @Test fun failedSubmissionKeepsStableRequestIdentity() {
        val failed = FailedSubmission("request-1", "same text")
        assertEquals("request-1", failed.copy(content = "same text").requestId)
    }
}
