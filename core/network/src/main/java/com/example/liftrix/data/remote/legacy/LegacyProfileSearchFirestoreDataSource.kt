package com.example.liftrix.data.remote.legacy

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LegacyProfileSearchFirestoreDataSource @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    companion object {
        private const val SEARCH_ANALYTICS_COLLECTION = "search_analytics"
        private const val PROFILE_REPORTS_COLLECTION = "profile_reports"
    }

    suspend fun trackSearch(record: Map<String, Any>) {
        firestore.collection(SEARCH_ANALYTICS_COLLECTION)
            .add(record)
            .await()
    }

    suspend fun reportProfile(record: Map<String, Any>) {
        firestore.collection(PROFILE_REPORTS_COLLECTION)
            .add(record)
            .await()
    }
}
