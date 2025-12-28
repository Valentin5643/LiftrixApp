package com.example.liftrix.data.remote.legacy

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LegacyAchievementFirestoreDataSource @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    suspend fun deleteAchievement(achievementId: String) {
        firestore.collection("user_achievements")
            .document(achievementId)
            .delete()
            .await()
    }
}
