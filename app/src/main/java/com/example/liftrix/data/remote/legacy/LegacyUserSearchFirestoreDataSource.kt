package com.example.liftrix.data.remote.legacy

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LegacyUserSearchFirestoreDataSource @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    companion object {
        private const val USERS_PUBLIC_COLLECTION = "social_profiles"
        private const val USER_SEARCH_CACHE_COLLECTION = "user_search_cache"
        private const val QR_CODE_COLLECTION = "qr_codes"
        private const val PROFILE_VIEWS_COLLECTION = "profile_views"
    }

    suspend fun getPublicProfileData(userId: String): Map<String, Any>? {
        val document = firestore.collection(USERS_PUBLIC_COLLECTION)
            .document(userId)
            .get()
            .await()
        if (!document.exists()) {
            return null
        }
        return document.data
    }

    suspend fun profileExists(userId: String): Boolean {
        val document = firestore.collection(USERS_PUBLIC_COLLECTION)
            .document(userId)
            .get()
            .await()
        return document.exists()
    }

    suspend fun storeQRCode(qrCodeId: String, data: Map<String, Any>) {
        firestore.collection(QR_CODE_COLLECTION)
            .document(qrCodeId)
            .set(data)
            .await()
    }

    suspend fun getQRCode(qrCodeId: String): Map<String, Any>? {
        val document = firestore.collection(QR_CODE_COLLECTION)
            .document(qrCodeId)
            .get()
            .await()
        if (!document.exists()) {
            return null
        }
        return document.data
    }

    suspend fun getValidQRCode(userId: String): String? {
        val snapshot = firestore.collection(QR_CODE_COLLECTION)
            .whereEqualTo("userId", userId)
            .orderBy("expiresAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .await()
        return snapshot.documents.firstOrNull()?.data?.get("qrData") as? String
    }

    suspend fun incrementQRCodeUsage(qrCodeId: String) {
        val qrRef = firestore.collection(QR_CODE_COLLECTION).document(qrCodeId)
        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(qrRef)
            val currentCount = (snapshot.data?.get("usageCount") as? Number)?.toInt() ?: 0
            transaction.update(qrRef, "usageCount", currentCount + 1)
            transaction.update(qrRef, "lastUsedAt", java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME))
        }.await()
    }

    suspend fun updateSearchKeywords(userId: String, data: Map<String, Any>) {
        firestore.collection(USER_SEARCH_CACHE_COLLECTION)
            .document(userId)
            .set(data, com.google.firebase.firestore.SetOptions.merge())
            .await()
    }

    suspend fun addProfileView(viewRecord: Map<String, Any>) {
        firestore.collection(PROFILE_VIEWS_COLLECTION)
            .add(viewRecord)
            .await()
    }

    suspend fun incrementProfileViewCount(profileUserId: String, lastViewedAt: String) {
        val profileRef = firestore.collection(USERS_PUBLIC_COLLECTION).document(profileUserId)
        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(profileRef)
            val currentViews = (snapshot.data?.get("profileViews") as? Number)?.toLong() ?: 0L
            transaction.update(profileRef, "profileViews", currentViews + 1)
            transaction.update(profileRef, "lastViewedAt", lastViewedAt)
        }.await()
    }

    suspend fun searchUsersWithTokens(
        searchTokens: List<String>,
        limit: Int
    ): List<Map<String, Any>> {
        return try {
            val snapshot = firestore.collection(USER_SEARCH_CACHE_COLLECTION)
                .whereEqualTo("isSearchable", true)
                .whereArrayContainsAny("searchTokens", searchTokens)
                .orderBy("lastActiveAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(limit.toLong())
                .get()
                .await()
            snapshot.documents.mapNotNull { it.data }
        } catch (e: Exception) {
            Timber.e(e, "Legacy searchUsersWithTokens failed")
            emptyList()
        }
    }

    suspend fun searchUsersBasic(limit: Int): List<Map<String, Any>> {
        return try {
            val snapshot = firestore.collection(USERS_PUBLIC_COLLECTION)
                .whereEqualTo("isSearchable", true)
                .limit(limit.toLong())
                .get()
                .await()
            snapshot.documents.mapNotNull { document ->
                document.data?.toMutableMap()?.apply { put("userId", document.id) }
            }
        } catch (e: Exception) {
            Timber.e(e, "Legacy searchUsersBasic failed")
            emptyList()
        }
    }
}
