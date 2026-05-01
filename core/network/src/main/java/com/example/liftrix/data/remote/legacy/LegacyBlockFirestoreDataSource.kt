package com.example.liftrix.data.remote.legacy

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LegacyBlockFirestoreDataSource @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    companion object {
        private const val BLOCKS_COLLECTION = "user_blocks"
    }

    suspend fun blockUser(blockId: String, blockerId: String, blockedUserId: String, blockedAt: Long) {
        val blockData = mapOf(
            "id" to blockId,
            "blockerId" to blockerId,
            "blockedUserId" to blockedUserId,
            "blockedAt" to blockedAt
        )

        firestore.collection(BLOCKS_COLLECTION)
            .document(blockId)
            .set(blockData)
            .await()
    }

    suspend fun unblockUser(blockerId: String, blockedUserId: String) {
        val query = firestore.collection(BLOCKS_COLLECTION)
            .whereEqualTo("blockerId", blockerId)
            .whereEqualTo("blockedUserId", blockedUserId)
            .get()
            .await()

        for (document in query.documents) {
            document.reference.delete().await()
        }
    }
}
