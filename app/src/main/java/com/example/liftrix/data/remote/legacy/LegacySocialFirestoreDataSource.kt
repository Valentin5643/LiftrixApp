package com.example.liftrix.data.remote.legacy

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Deprecated(
    message = "Legacy Firestore social data source is deprecated. Use Room-first repositories and sync workers.",
    level = DeprecationLevel.WARNING
)
@Singleton
class LegacySocialFirestoreDataSource @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    companion object {
        private const val USERS_COLLECTION = "users"
        private const val FRIENDSHIPS_COLLECTION = "friendships"
        private const val SOCIAL_PROFILES_COLLECTION = "social_profiles"
        private const val WORKOUTS_COLLECTION = "workouts"
        private const val USER_PRESENCE_COLLECTION = "user_presence"
    }

    data class LegacyUserRecord(
        val id: String,
        val data: Map<String, Any>
    )

    data class RecommendationCandidate(
        val userId: String,
        val username: String,
        val displayName: String?,
        val profilePhotoUrl: String?
    )

    suspend fun searchUsers(
        query: String,
        displayNameLimit: Int,
        emailLimit: Int
    ): List<LegacyUserRecord> {
        val displayNameQuery = firestore.collection(USERS_COLLECTION)
            .whereGreaterThanOrEqualTo("displayName", query)
            .whereLessThanOrEqualTo("displayName", "$query\uf8ff")
            .limit(displayNameLimit.toLong())
            .get()
            .await()

        val emailQuery = firestore.collection(USERS_COLLECTION)
            .whereEqualTo("email", query.lowercase())
            .limit(emailLimit.toLong())
            .get()
            .await()

        val allResults = (displayNameQuery.documents + emailQuery.documents)
            .distinctBy { it.id }

        return allResults.mapNotNull { document ->
            val data = document.data ?: return@mapNotNull null
            LegacyUserRecord(id = document.id, data = data)
        }
    }

    suspend fun getUserRecord(userId: String): LegacyUserRecord? {
        val document = firestore.collection(USERS_COLLECTION)
            .document(userId)
            .get()
            .await()
        if (!document.exists()) {
            return null
        }
        val data = document.data ?: return null
        return LegacyUserRecord(id = document.id, data = data)
    }

    suspend fun getMutualFriendUserIds(
        existingFriendIds: Set<String>,
        limit: Int,
        offset: Int
    ): List<String> {
        if (existingFriendIds.isEmpty()) {
            return emptyList()
        }

        val snapshot = firestore.collection(FRIENDSHIPS_COLLECTION)
            .whereIn("senderId", existingFriendIds.toList())
            .whereEqualTo("status", "accepted")
            .limit((limit + offset).toLong())
            .get()
            .await()

        val potentialFriends = LinkedHashSet<String>()
        for (document in snapshot.documents) {
            val receiverId = document.getString("receiverId") ?: continue
            potentialFriends.add(receiverId)
        }
        return potentialFriends.toList()
            .drop(offset)
            .take(limit)
    }

    suspend fun getGeneralRecommendationCandidates(
        currentUserId: String,
        excludeUserIds: Set<String>,
        limit: Int,
        offset: Int
    ): List<RecommendationCandidate> {
        return try {
            val recommendations = mutableListOf<RecommendationCandidate>()

            val socialProfilesQuery = firestore.collection(SOCIAL_PROFILES_COLLECTION)
                .limit(10)
                .get()
                .await()

            for (document in socialProfilesQuery.documents) {
                val userId = document.getString("userId") ?: continue
                if (userId == currentUserId || userId in excludeUserIds) {
                    continue
                }

                val profileData = document.data ?: continue
                val isPrivate = profileData["isPrivate"] as? Boolean ?: true
                val hideFromSuggestions = profileData["hideFromSuggestions"] as? Boolean ?: false
                if (isPrivate || hideFromSuggestions) {
                    continue
                }

                val username = profileData["username"] as? String ?: continue
                val displayName = profileData["displayName"] as? String
                val profilePhotoUrl = profileData["profilePhotoUrl"] as? String

                recommendations.add(
                    RecommendationCandidate(
                        userId = userId,
                        username = username,
                        displayName = displayName,
                        profilePhotoUrl = profilePhotoUrl
                    )
                )

                if (recommendations.size >= limit) {
                    break
                }
            }

            if (recommendations.size < limit) {
                try {
                    val currentUser = FirebaseAuth.getInstance().currentUser
                    Timber.d("AUTH_DEBUG: Current user before users query: ${currentUser?.uid}")

                    if (currentUser == null) {
                        Timber.w("AUTH_DEBUG: No authenticated user - query will fail with PERMISSION_DENIED")
                    } else {
                        Timber.d("AUTH_DEBUG: Executing users collection query with isPublic=true filter")
                        val usersQuery = firestore.collection(USERS_COLLECTION)
                            .whereEqualTo("isPublic", true)
                            .limit((limit - recommendations.size + 5).toLong())
                            .get()
                            .await()

                        Timber.d("AUTH_DEBUG: Query returned ${usersQuery.documents.size} documents")

                        for (userDoc in usersQuery.documents) {
                            if (recommendations.size >= limit) break

                            val docData = userDoc.data
                            val hasIsPublic = docData?.containsKey("isPublic") ?: false
                            val isPublicValue = docData?.get("isPublic")
                            Timber.d("AUTH_DEBUG: Doc ${userDoc.id} - hasIsPublic: $hasIsPublic, isPublic: $isPublicValue")

                            val userId = userDoc.id
                            if (userId == currentUserId || userId in excludeUserIds) continue

                            val userData = userDoc.data ?: continue
                            val username = userData["username"] as? String ?: continue
                            val displayName = userData["displayName"] as? String

                            val alreadyIncluded = recommendations.any { it.userId == userId }
                            if (!alreadyIncluded) {
                                recommendations.add(
                                    RecommendationCandidate(
                                        userId = userId,
                                        username = username,
                                        displayName = displayName,
                                        profilePhotoUrl = null
                                    )
                                )
                            }
                        }
                    }
                } catch (fallbackError: Exception) {
                    when (fallbackError.message?.contains("PERMISSION_DENIED")) {
                        true -> {
                            Timber.w("AUTH_DEBUG: PERMISSION_DENIED error in users collection query")
                            Timber.w("AUTH_DEBUG: Auth state: ${FirebaseAuth.getInstance().currentUser?.uid}")
                            Timber.w("AUTH_DEBUG: Error details: ${fallbackError.message}")
                        }
                        else -> Timber.w(fallbackError, "Fallback to users collection failed")
                    }
                }
            }

            recommendations.drop(offset).take(limit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get general recommendations")
            emptyList()
        }
    }

    suspend fun syncFriendRequest(senderId: String, receiverId: String) {
        val friendshipData = mapOf(
            "senderId" to senderId,
            "receiverId" to receiverId,
            "status" to "pending",
            "created_at" to Timestamp.now(),
            "updated_at" to Timestamp.now()
        )

        firestore.collection(FRIENDSHIPS_COLLECTION)
            .document("${senderId}_${receiverId}")
            .set(friendshipData)
            .await()
    }

    suspend fun syncFriendResponse(receiverId: String, senderId: String, accepted: Boolean) {
        val status = if (accepted) "accepted" else "declined"
        val updateData = mapOf(
            "status" to status,
            "updated_at" to Timestamp.now()
        )

        firestore.collection(FRIENDSHIPS_COLLECTION)
            .document("${senderId}_${receiverId}")
            .update(updateData)
            .await()
    }

    suspend fun syncBlockUser(blockerId: String, blockedId: String) {
        val blockData = mapOf(
            "senderId" to blockerId,
            "receiverId" to blockedId,
            "status" to "blocked",
            "created_at" to Timestamp.now(),
            "updated_at" to Timestamp.now()
        )

        firestore.collection(FRIENDSHIPS_COLLECTION)
            .document("${blockerId}_${blockedId}")
            .set(blockData)
            .await()
    }

    suspend fun syncUnblockUser(unblockerId: String, unblockedId: String) {
        firestore.collection(FRIENDSHIPS_COLLECTION)
            .document("${unblockerId}_${unblockedId}")
            .delete()
            .await()
    }

    suspend fun syncRemoveFriend(userId: String, friendId: String) {
        firestore.collection(FRIENDSHIPS_COLLECTION)
            .document("${userId}_${friendId}")
            .delete()
            .await()

        firestore.collection(FRIENDSHIPS_COLLECTION)
            .document("${friendId}_${userId}")
            .delete()
            .await()
    }

    suspend fun fetchFriendsWorkouts(friendIds: List<String>): Int {
        if (friendIds.isEmpty()) {
            return 0
        }

        val friendsWorkouts = firestore.collection(WORKOUTS_COLLECTION)
            .whereIn("userId", friendIds.take(10))
            .whereNotEqualTo("completedAt", null)
            .orderBy("completedAt", Query.Direction.DESCENDING)
            .limit(50)
            .get()
            .await()

        return friendsWorkouts.documents.size
    }

    fun setupRealtimeFeedListener(friendIdsFlow: Flow<List<String>>): Flow<Unit> {
        return callbackFlow {
            var listener: ListenerRegistration? = null
            val job = launch {
                friendIdsFlow.collect { friends ->
                    listener?.remove()

                    if (friends.isNotEmpty()) {
                        listener = firestore.collection(WORKOUTS_COLLECTION)
                            .whereIn("userId", friends.take(10))
                            .whereNotEqualTo("completedAt", null)
                            .orderBy("completedAt", Query.Direction.DESCENDING)
                            .limit(20)
                            .addSnapshotListener { snapshot, error ->
                                if (error != null) {
                                    Timber.e(error, "Error in real-time feed listener")
                                    return@addSnapshotListener
                                }

                                val workoutCount = snapshot?.documents?.size ?: 0
                                Timber.d("Real-time feed update: $workoutCount friends' workouts")
                                trySend(Unit)
                            }

                        Timber.d("Real-time feed listener established for ${friends.size} friends")
                    } else {
                        Timber.d("No friends found - real-time feed listener not needed")
                    }

                    trySend(Unit)
                }
            }

            awaitClose {
                listener?.remove()
                job.cancel()
                Timber.d("Real-time feed listener removed")
            }
        }
    }

    suspend fun updateUserPresence(userId: String) {
        val presenceData = mapOf(
            "status" to "online",
            "last_active" to Timestamp.now(),
            "user_id" to userId
        )

        firestore.collection(USER_PRESENCE_COLLECTION)
            .document(userId)
            .set(presenceData)
            .await()
    }
}
