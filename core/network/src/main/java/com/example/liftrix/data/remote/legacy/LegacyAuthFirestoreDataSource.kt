package com.example.liftrix.data.remote.legacy

import com.example.liftrix.data.remote.dto.UserDto
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LegacyAuthFirestoreDataSource @Inject constructor(
    private val firestore: FirebaseFirestore
) {

    suspend fun createUserProfile(
        userId: String,
        userData: Map<String, Any?>,
        settingsData: Map<String, Any?>,
        subscriptionData: Map<String, Any?>
    ) {
        val batch = firestore.batch()

        val userRef = firestore.collection("users").document(userId)
        batch.set(userRef, userData)

        val settingsRef = firestore.collection("user_settings").document(userId)
        batch.set(settingsRef, settingsData)

        val subscriptionRef = firestore.collection("subscriptions").document()
        batch.set(subscriptionRef, subscriptionData)

        batch.commit().await()
    }

    suspend fun getUserProfile(uid: String): UserDto? {
        val document = firestore.collection("users")
            .document(uid)
            .get()
            .await()

        if (!document.exists()) {
            return null
        }

        return document.toObject(UserDto::class.java)
    }

    suspend fun checkUserProfileExists(uid: String): Boolean {
        val document = firestore.collection("users")
            .document(uid)
            .get()
            .await()
        return document.exists()
    }

    suspend fun updateUserFields(uid: String, updateMap: Map<String, Any?>) {
        firestore.collection("users")
            .document(uid)
            .set(updateMap, com.google.firebase.firestore.SetOptions.merge())
            .await()
    }

    suspend fun deleteUserData(userId: String, collectionsToClean: List<String>) {
        val batch = firestore.batch()

        for (collection in collectionsToClean) {
            try {
                val documents = firestore.collection(collection)
                    .whereEqualTo("userId", userId)
                    .get()
                    .await()
                for (document in documents) {
                    batch.delete(document.reference)
                }

                val documentsAlt = firestore.collection(collection)
                    .whereEqualTo("user_id", userId)
                    .get()
                    .await()
                for (document in documentsAlt) {
                    batch.delete(document.reference)
                }
            } catch (e: Exception) {
                Timber.w(e, "LegacyAuthFirestoreDataSource: Failed to query collection $collection for user deletion")
            }
        }

        val userRef = firestore.collection("users").document(userId)
        batch.delete(userRef)
        batch.commit().await()
    }

    suspend fun verifyFirestoreAccess(userId: String): Boolean {
        firestore.collection("users").document(userId).get().await()
        return true
    }
}
