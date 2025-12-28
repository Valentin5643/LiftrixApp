package com.example.liftrix.data.remote.legacy

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LegacyProfileFirestoreDataSource @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    suspend fun deleteProfile(userId: String) {
        firestore.collection("user_profiles")
            .document(userId)
            .delete()
            .await()
    }
}
