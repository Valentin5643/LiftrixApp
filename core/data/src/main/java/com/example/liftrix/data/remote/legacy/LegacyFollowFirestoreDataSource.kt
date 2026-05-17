package com.example.liftrix.data.remote.legacy

import com.example.liftrix.data.local.entity.FollowRelationshipEntity
import com.example.liftrix.data.local.entity.FollowRequestEntity
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LegacyFollowFirestoreDataSource @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    companion object {
        private const val FOLLOW_RELATIONSHIPS_COLLECTION = "follow_relationships"
        private const val FOLLOW_REQUESTS_COLLECTION = "follow_requests"
        private const val SOCIAL_PROFILES_COLLECTION = "social_profiles"
    }

    suspend fun syncRelationship(relationship: FollowRelationshipEntity) {
        val relationshipId = relationshipId(relationship.followerId, relationship.followingId)
        val data = mapOf(
            "id" to relationshipId,
            "followerId" to relationship.followerId,
            "followingId" to relationship.followingId,
            "status" to relationship.status,
            "createdAt" to relationship.createdAt,
            "acceptedAt" to relationship.acceptedAt,
            "blockedAt" to relationship.blockedAt,
            "syncedAt" to System.currentTimeMillis()
        )
        firestore.collection(FOLLOW_RELATIONSHIPS_COLLECTION)
            .document(relationshipId)
            .set(data)
            .await()
    }

    suspend fun syncFollowRequest(request: FollowRequestEntity) {
        val data = mapOf(
            "id" to request.id,
            "requesterId" to request.requesterId,
            "targetId" to request.targetId,
            "status" to request.status,
            "requestMessage" to request.requestMessage,
            "createdAt" to request.createdAt,
            "processedAt" to request.processedAt,
            "expiresAt" to request.expiresAt,
            "requestSource" to request.requestSource,
            "syncedAt" to System.currentTimeMillis()
        )
        firestore.collection(FOLLOW_REQUESTS_COLLECTION)
            .document(request.id)
            .set(data)
            .await()
    }

    suspend fun deleteRelationship(followerId: String, targetUserId: String) {
        val query = firestore.collection(FOLLOW_RELATIONSHIPS_COLLECTION)
            .whereEqualTo("followerId", followerId)
            .whereEqualTo("followingId", targetUserId)
            .get()
            .await()
        query.documents.forEach { doc ->
            doc.reference.delete().await()
        }
    }

    suspend fun fetchFollowerRelationships(userId: String): List<Map<String, Any>> {
        val followerQuery = firestore.collection(FOLLOW_RELATIONSHIPS_COLLECTION)
            .whereEqualTo("followingId", userId)
            .whereEqualTo("status", "ACCEPTED")
            .get()
            .await()
        return followerQuery.documents.mapNotNull { it.data }
    }

    suspend fun fetchFollowingRelationships(userId: String): List<Map<String, Any>> {
        val followingQuery = firestore.collection(FOLLOW_RELATIONSHIPS_COLLECTION)
            .whereEqualTo("followerId", userId)
            .whereEqualTo("status", "ACCEPTED")
            .get()
            .await()
        return followingQuery.documents.mapNotNull { it.data }
    }

    suspend fun fetchSocialProfileData(userId: String): Map<String, Any>? {
        val profileData = firestore.collection(SOCIAL_PROFILES_COLLECTION)
            .document(userId)
            .get()
            .await()
        if (!profileData.exists()) {
            return null
        }
        return profileData.data
    }

    private fun relationshipId(followerId: String, followingId: String): String =
        "${followerId}_${followingId}"
}
