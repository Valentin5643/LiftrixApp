package com.example.liftrix.domain.usecase.chat

/**
 * Shared deletion rules for durable AI conversation tombstones.
 *
 * The reserved conversation ID stores the user's clear-all cutoff in the existing
 * conversation metadata table. Real conversations must never use this ID.
 */
object ChatConversationDeletionPolicy {
    const val ALL_HISTORY_CONVERSATION_ID = "__all_history_deleted__"
    const val ALL_HISTORY_TOMBSTONE_TITLE = "All history deleted"

    fun isReservedConversationId(conversationId: String): Boolean =
        conversationId == ALL_HISTORY_CONVERSATION_ID

    fun requireRealConversationId(conversationId: String) {
        require(conversationId.isNotBlank()) { "Conversation ID must not be blank" }
        require(!isReservedConversationId(conversationId)) {
            "Conversation ID is reserved for the all-history deletion tombstone"
        }
    }

    fun shouldSuppressMessage(
        conversationId: String,
        messageCreatedAt: Long,
        conversationDeletedAt: Long?,
        allHistoryDeletedAt: Long?
    ): Boolean =
        isReservedConversationId(conversationId) ||
            conversationDeletedAt != null ||
            (allHistoryDeletedAt != null && messageCreatedAt <= allHistoryDeletedAt)
}
