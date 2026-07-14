package com.example.liftrix.domain.usecase.chat

import javax.inject.Inject

class ChatConversationTitlePolicy @Inject constructor() {
    fun titleFor(message: String): String {
        val cleaned = message
            .replace(Regex("[`*_#>~\\[\\]()]"), " ")
            .replace(Regex("[\\r\\n\\t]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .replace(PREFIX, "")
            .trim()
        if (cleaned.isBlank()) return DEFAULT_TITLE
        return cleaned.split(' ')
            .filter(String::isNotBlank)
            .take(MAX_WORDS)
            .joinToString(" ")
            .take(MAX_LENGTH)
            .trimEnd()
            .ifBlank { DEFAULT_TITLE }
    }

    companion object {
        const val MAX_LENGTH = 60
        const val DEFAULT_TITLE = "New chat"
        private const val MAX_WORDS = 8
        private val PREFIX = Regex(
            "^(hi|hello|hey|please|can you|could you|help me|salut|bun[ăa]|te rog|poți|poti|ajută-mă|ajuta-ma)[,!:;\\s-]+",
            RegexOption.IGNORE_CASE
        )
    }
}
