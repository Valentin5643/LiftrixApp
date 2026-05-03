package com.example.liftrix.sync

import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import kotlinx.coroutines.tasks.await
import timber.log.Timber

object FirestorePrefetcher {
    private const val MAX_WHERE_IN = 10

    suspend fun prefetchByIds(
        collection: CollectionReference,
        ids: List<String>
    ): Map<String, DocumentSnapshot> {
        val safeIds = ids.filter { it.isNotBlank() }.distinct()
        if (safeIds.isEmpty()) {
            return emptyMap()
        }

        val documents = mutableMapOf<String, DocumentSnapshot>()
        safeIds.chunked(MAX_WHERE_IN).forEach { chunk ->
            try {
                val snapshot = collection
                    .whereIn(FieldPath.documentId(), chunk)
                    .get()
                    .await()
                snapshot.documents.forEach { doc ->
                    documents[doc.id] = doc
                }
            } catch (e: Exception) {
                Timber.w(e, "FirestorePrefetcher: Failed to prefetch ${chunk.size} docs")
            }
        }

        return documents
    }
}
