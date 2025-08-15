package com.example.liftrix.data.repository

import com.example.liftrix.data.cache.RecommendationCache
import com.example.liftrix.data.local.dao.FriendDao
import com.example.liftrix.data.local.dao.PrivacySettingsDao
import com.example.liftrix.data.mapper.FriendMapper
import com.example.liftrix.domain.model.Friend
import com.example.liftrix.domain.model.FriendStatus
import com.example.liftrix.domain.model.RecommendedUser
import com.example.liftrix.domain.model.SharedWorkout
import com.example.liftrix.domain.model.User
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.repository.SocialRepository
import com.example.liftrix.domain.usecase.social.GetSocialProfileUseCase
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of SocialRepository following Clean Architecture patterns
 * Provides offline-first friend management with Firebase sync
 */
@Singleton
class SocialRepositoryImpl @Inject constructor(
    private val friendDao: FriendDao,
    private val privacySettingsDao: PrivacySettingsDao,
    private val friendMapper: FriendMapper,
    private val authRepository: AuthRepository,
    private val firestore: FirebaseFirestore,
    private val recommendationCache: RecommendationCache,
    private val getSocialProfileUseCase: GetSocialProfileUseCase
) : SocialRepository {

    companion object {
        private const val USERS_COLLECTION = "users"
        private const val FRIENDSHIPS_COLLECTION = "friendships"
        private const val MAX_SEARCH_RESULTS = 20
        private const val DISCOVERY_BATCH_SIZE = 10
        private const val MUTUAL_FRIENDS_WEIGHT = 0.5 // 50% mutual friends, 50% general discovery
    }

    override fun searchUsers(query: String): Flow<List<User>> = flow {
        if (query.isBlank()) {
            emit(emptyList())
            return@flow
        }

        try {
            val currentUserId = authRepository.getCurrentUserId()
            if (currentUserId == null) {
                Timber.w("User not authenticated for search")
                emit(emptyList())
                return@flow
            }

            // Search Firebase users collection by display name and email
            val searchResults = mutableListOf<User>()

            // Search by display name (case-insensitive prefix search)
            val displayNameQuery = firestore.collection(USERS_COLLECTION)
                .whereGreaterThanOrEqualTo("displayName", query)
                .whereLessThanOrEqualTo("displayName", query + "\uf8ff")
                .limit(MAX_SEARCH_RESULTS.toLong())
                .get()
                .await()

            // Search by email (exact match for privacy)
            val emailQuery = firestore.collection(USERS_COLLECTION)
                .whereEqualTo("email", query.lowercase())
                .limit(5)
                .get()
                .await()

            // Combine and deduplicate results
            val allResults = (displayNameQuery.documents + emailQuery.documents).distinctBy { it.id }
            
            for (document in allResults) {
                try {
                    // Skip current user from results
                    if (document.id == currentUserId) continue

                    val userData = document.data ?: continue
                    val user = User(
                        uid = document.id,
                        email = userData["email"] as? String ?: "",
                        displayName = userData["displayName"] as? String,
                        photoUrl = userData["photoUrl"] as? String,
                        isAnonymous = userData["isAnonymous"] as? Boolean ?: false,
                        subscriptionTier = com.example.liftrix.domain.model.SubscriptionTier.FREE, // Default
                        subscriptionStatus = com.example.liftrix.domain.model.SubscriptionStatus.ACTIVE, // Default
                        subscriptionExpiresAt = null,
                        premiumFeaturesEnabled = false,
                        onboardingCompleted = userData["onboardingCompleted"] as? Boolean ?: false,
                        profileVersion = userData["profileVersion"] as? Long ?: 1L,
                        createdAt = java.time.LocalDateTime.now(), // Placeholder
                        lastSignInAt = java.time.LocalDateTime.now(), // Placeholder
                        updatedAt = java.time.LocalDateTime.now() // Placeholder
                    )
                    searchResults.add(user)
                } catch (e: Exception) {
                    Timber.w(e, "Failed to parse user document: ${document.id}")
                }
            }

            emit(searchResults)
            Timber.d("User search completed: ${searchResults.size} results for query '$query'")

        } catch (e: Exception) {
            Timber.e(e, "Failed to search users with query: $query")
            emit(emptyList())
        }
    }.catch { e ->
        Timber.e(e, "User search flow error")
        emit(emptyList())
    }

    override suspend fun sendFriendRequest(friendUserId: String): Result<Unit> {
        return try {
            val currentUserId = authRepository.getCurrentUserId()
                ?: return Result.failure(Exception("User not authenticated"))

            if (currentUserId == friendUserId) {
                return Result.failure(Exception("Cannot send friend request to yourself"))
            }

            // Check if relationship already exists
            val existingRelationship = friendDao.getFriendRelationship(currentUserId, friendUserId)
            if (existingRelationship != null) {
                return Result.failure(Exception("Friend relationship already exists"))
            }

            // Check privacy settings of target user
            val targetPrivacySettings = privacySettingsDao.getPrivacySettingsOnce(friendUserId)
            if (targetPrivacySettings?.allowFriendRequests == false) {
                return Result.failure(Exception("User is not accepting friend requests"))
            }

            // Create friend request entity
            val friendRequest = friendMapper.createFriendRequest(
                currentUserId = currentUserId,
                friendUserId = friendUserId,
                isSynced = false
            )

            // Save to local database (offline-first)
            friendDao.insertFriend(friendRequest)

            // Sync to Firebase
            syncFriendRequestToFirebase(currentUserId, friendUserId)

            // Invalidate recommendation cache since friend relationships changed
            recommendationCache.invalidateCacheForUser(currentUserId)

            Timber.d("Friend request sent successfully: $currentUserId -> $friendUserId")
            Result.success(Unit)

        } catch (e: Exception) {
            Timber.e(e, "Failed to send friend request to: $friendUserId")
            Result.failure(e)
        }
    }

    override suspend fun respondToFriendRequest(friendUserId: String, accept: Boolean): Result<Unit> {
        return try {
            val currentUserId = authRepository.getCurrentUserId()
                ?: return Result.failure(Exception("User not authenticated"))

            // Find the pending friend request
            val existingRequest = friendDao.getFriendRelationship(friendUserId, currentUserId)
            if (existingRequest == null || existingRequest.status != FriendStatus.PENDING.name) {
                return Result.failure(Exception("No pending friend request found"))
            }

            val newStatus = if (accept) FriendStatus.ACCEPTED.name else "DECLINED"
            val now = Instant.now().toEpochMilli()

            // Update the friend request status
            friendDao.updateFriendStatus(friendUserId, currentUserId, newStatus, now)

            if (accept) {
                // Create bidirectional friendship
                val reciprocalFriend = friendMapper.createFriendRequest(
                    currentUserId = currentUserId,
                    friendUserId = friendUserId,
                    isSynced = false
                ).copy(status = FriendStatus.ACCEPTED.name)

                friendDao.insertFriend(reciprocalFriend)
            }

            // Sync to Firebase
            syncFriendResponseToFirebase(currentUserId, friendUserId, accept)

            // Invalidate recommendation cache for both users since friend relationships changed
            if (accept) {
                recommendationCache.invalidateCacheForUser(currentUserId)
                recommendationCache.invalidateCacheForUser(friendUserId)
            }

            val action = if (accept) "accepted" else "declined"
            Timber.d("Friend request $action: $friendUserId -> $currentUserId")
            Result.success(Unit)

        } catch (e: Exception) {
            Timber.e(e, "Failed to respond to friend request from: $friendUserId")
            Result.failure(e)
        }
    }

    override fun getFriends(userId: String): Flow<List<Friend>> {
        return friendDao.getFriends(userId).map { friendEntities ->
            friendEntities.mapNotNull { entity ->
                try {
                    // Get the friend's profile data for proper display
                    val friendProfile = getSocialProfileUseCase(entity.friendUserId).getOrNull()
                    
                    friendMapper.toDomain(
                        entity = entity,
                        displayName = friendProfile?.displayName ?: "User ${entity.friendUserId.take(8)}",
                        email = null, // Email is not in social profile - could be added if needed
                        avatarUrl = friendProfile?.profilePhotoUrl,
                        presence = null // TODO: Integrate with presence service when available
                    )
                } catch (e: Exception) {
                    Timber.w(e, "Failed to map friend entity: ${entity.friendUserId}")
                    null
                }
            }
        }.catch { e ->
            Timber.e(e, "Failed to get friends for user: $userId")
            emit(emptyList())
        }
    }

    override fun getPendingFriendRequests(userId: String): Flow<List<Friend>> {
        return friendDao.getIncomingFriendRequests(userId).map { friendEntities ->
            friendEntities.mapNotNull { entity ->
                try {
                    friendMapper.toDomain(
                        entity = entity,
                        displayName = "User ${entity.userId.take(8)}", // Placeholder
                        email = null,
                        avatarUrl = null,
                        presence = null
                    )
                } catch (e: Exception) {
                    Timber.w(e, "Failed to map pending request entity: ${entity.userId}")
                    null
                }
            }
        }.catch { e ->
            Timber.e(e, "Failed to get pending friend requests for user: $userId")
            emit(emptyList())
        }
    }

    override fun getFriendWorkoutFeed(userId: String): Flow<List<SharedWorkout>> = flow {
        // Placeholder implementation - will be integrated with workout sharing in future tasks
        emit(emptyList<SharedWorkout>())
        Timber.d("Friend workout feed requested for user: $userId (placeholder implementation)")
    }.catch { e ->
        Timber.e(e, "Failed to get friend workout feed for user: $userId")
        emit(emptyList())
    }

    override suspend fun blockUser(friendUserId: String): Result<Unit> {
        return try {
            val currentUserId = authRepository.getCurrentUserId()
                ?: return Result.failure(Exception("User not authenticated"))

            // Update or create blocked relationship
            val existingRelationship = friendDao.getFriendRelationship(currentUserId, friendUserId)
            val now = Instant.now().toEpochMilli()

            if (existingRelationship != null) {
                friendDao.updateFriendStatus(currentUserId, friendUserId, FriendStatus.BLOCKED.name, now)
            } else {
                val blockedUser = friendMapper.createFriendRequest(currentUserId, friendUserId)
                    .copy(status = FriendStatus.BLOCKED.name)
                friendDao.insertFriend(blockedUser)
            }

            // Remove any reciprocal friendship
            friendDao.deleteFriendRelationship(friendUserId, currentUserId)

            // Sync to Firebase
            syncBlockUserToFirebase(currentUserId, friendUserId)

            Timber.d("User blocked successfully: $currentUserId blocked $friendUserId")
            Result.success(Unit)

        } catch (e: Exception) {
            Timber.e(e, "Failed to block user: $friendUserId")
            Result.failure(e)
        }
    }

    override suspend fun unblockUser(friendUserId: String): Result<Unit> {
        return try {
            val currentUserId = authRepository.getCurrentUserId()
                ?: return Result.failure(Exception("User not authenticated"))

            // Remove blocked relationship
            friendDao.deleteFriendRelationship(currentUserId, friendUserId)

            // Sync to Firebase
            syncUnblockUserToFirebase(currentUserId, friendUserId)

            Timber.d("User unblocked successfully: $currentUserId unblocked $friendUserId")
            Result.success(Unit)

        } catch (e: Exception) {
            Timber.e(e, "Failed to unblock user: $friendUserId")
            Result.failure(e)
        }
    }

    override suspend fun removeFriend(friendUserId: String): Result<Unit> {
        return try {
            val currentUserId = authRepository.getCurrentUserId()
                ?: return Result.failure(Exception("User not authenticated"))

            // Remove bidirectional friendship
            friendDao.deleteBidirectionalFriendRelationship(currentUserId, friendUserId)

            // Sync to Firebase
            syncRemoveFriendToFirebase(currentUserId, friendUserId)

            // Invalidate recommendation cache for both users since friend relationships changed
            recommendationCache.invalidateCacheForUser(currentUserId)
            recommendationCache.invalidateCacheForUser(friendUserId)

            Timber.d("Friend removed successfully: $currentUserId removed $friendUserId")
            Result.success(Unit)

        } catch (e: Exception) {
            Timber.e(e, "Failed to remove friend: $friendUserId")
            Result.failure(e)
        }
    }
    
    override suspend fun getUserById(userId: String): User? {
        return try {
            val document = firestore.collection(USERS_COLLECTION)
                .document(userId)
                .get()
                .await()
                
            if (!document.exists()) {
                Timber.w("User not found: $userId")
                return null
            }
            
            val userData = document.data ?: return null
            User(
                uid = document.id,
                email = userData["email"] as? String ?: "",
                displayName = userData["displayName"] as? String,
                photoUrl = userData["photoUrl"] as? String,
                isAnonymous = userData["isAnonymous"] as? Boolean ?: false,
                subscriptionTier = com.example.liftrix.domain.model.SubscriptionTier.FREE,
                subscriptionStatus = com.example.liftrix.domain.model.SubscriptionStatus.ACTIVE,
                subscriptionExpiresAt = null,
                premiumFeaturesEnabled = false,
                onboardingCompleted = userData["onboardingCompleted"] as? Boolean ?: false,
                profileVersion = userData["profileVersion"] as? Long ?: 1L,
                createdAt = java.time.LocalDateTime.now(),
                lastSignInAt = java.time.LocalDateTime.now(),
                updatedAt = java.time.LocalDateTime.now()
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to get user by ID: $userId")
            null
        }
    }

    override fun getRecommendedUsers(limit: Int, offset: Int): Flow<List<RecommendedUser>> = flow {
        try {
            val currentUserId = authRepository.getCurrentUserId()
            if (currentUserId == null) {
                Timber.w("User not authenticated for recommendations")
                emit(emptyList())
                return@flow
            }

            Timber.v("Loading recommended users: limit=$limit, offset=$offset")
            
            // Step 1: Check cache first (only for first page)
            if (offset == 0) {
                val cachedRecommendations = recommendationCache.getCachedRecommendations(currentUserId)
                if (cachedRecommendations != null) {
                    val cachedResults = cachedRecommendations.take(limit)
                    Timber.d("Cache hit: returning ${cachedResults.size} cached recommendations")
                    emit(cachedResults)
                    return@flow
                }
                Timber.d("Cache miss: proceeding with fresh API call")
            }
            
            // Step 2: Get current user's friends to exclude from recommendations
            val existingFriendIds = friendDao.getFriends(currentUserId).map { friendEntities ->
                friendEntities.map { it.friendUserId }.toSet()
            }.catch { emit(emptySet()) }
                
            existingFriendIds.collect { friendIds ->
                val recommendations = mutableListOf<RecommendedUser>()
                
                // Step 3: Get mutual friends recommendations (50% of limit)
                val mutualFriendsLimit = (limit * MUTUAL_FRIENDS_WEIGHT).toInt()
                if (mutualFriendsLimit > 0) {
                    val mutualFriendsUsers = getMutualFriendsRecommendations(
                        currentUserId = currentUserId,
                        existingFriendIds = friendIds,
                        limit = mutualFriendsLimit,
                        offset = offset / 2
                    )
                    recommendations.addAll(mutualFriendsUsers)
                }
                
                // Step 4: Get general discovery recommendations to fill remaining slots
                val remainingSlots = limit - recommendations.size
                if (remainingSlots > 0) {
                    val generalUsers = getGeneralRecommendations(
                        currentUserId = currentUserId,
                        excludeUserIds = friendIds + recommendations.map { it.userId }.toSet(),
                        limit = remainingSlots,
                        offset = offset
                    )
                    recommendations.addAll(generalUsers)
                }
                
                // Remove duplicates and limit results
                val finalRecommendations = recommendations
                    .distinctBy { it.userId }
                    .take(limit)
                
                // Step 5: Cache fresh recommendations (only for first page)
                if (offset == 0 && finalRecommendations.isNotEmpty()) {
                    recommendationCache.cacheRecommendations(currentUserId, finalRecommendations)
                }
                
                Timber.d("Generated ${finalRecommendations.size} recommendations for user: $currentUserId")
                emit(finalRecommendations)
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to get recommended users")
            emit(emptyList())
        }
    }.catch { e ->
        Timber.e(e, "Recommended users flow error")
        emit(emptyList())
    }

    override suspend fun followUser(userId: String): Result<Unit> {
        return try {
            val currentUserId = authRepository.getCurrentUserId()
                ?: return Result.failure(Exception("User not authenticated"))

            // Check if there's already a pending request from the target user
            val incomingRequest = friendDao.getFriendRelationship(userId, currentUserId)
            
            if (incomingRequest != null && incomingRequest.status == FriendStatus.PENDING.name) {
                // Accept existing friend request
                respondToFriendRequest(userId, accept = true)
            } else {
                // Send new friend request
                sendFriendRequest(userId)
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to follow user: $userId")
            Result.failure(e)
        }
    }

    override suspend fun refreshDiscoveryCache(): Result<Unit> {
        return try {
            val currentUserId = authRepository.getCurrentUserId()
            if (currentUserId != null) {
                // Clear cached recommendations to trigger fresh discovery
                recommendationCache.invalidateCacheForUser(currentUserId)
                Timber.d("Discovery cache refreshed successfully for user: $currentUserId")
            } else {
                Timber.w("Cannot refresh cache: user not authenticated")
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to refresh discovery cache")
            Result.failure(e)
        }
    }

    suspend fun syncFriendsWorkouts(): Result<Unit> {
        return try {
            val currentUserId = authRepository.getCurrentUserId()
                ?: return Result.failure(Exception("User not authenticated"))

            Timber.d("Starting friends' workouts sync for user: $currentUserId")

            // Get list of accepted friends
            val friendIds = friendDao.getFriends(currentUserId).map { friendEntities ->
                friendEntities.filter { it.status == FriendStatus.ACCEPTED.name }
                    .map { it.friendUserId }
            }.catch { emit(emptyList()) }

            friendIds.collect { friends ->
                if (friends.isNotEmpty()) {
                    // Query Firebase for friends' recent workouts
                    val friendsWorkouts = firestore.collection("workouts")
                        .whereIn("userId", friends.take(10)) // Firestore limit of 10 items in whereIn
                        .whereNotEqualTo("completedAt", null)
                        .orderBy("completedAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                        .limit(50)
                        .get()
                        .await()

                    Timber.d("Retrieved ${friendsWorkouts.documents.size} friends' workouts from Firebase")

                    // Process and cache friends' workouts locally for feed display
                    // Note: This is a placeholder for future workout caching implementation
                    // Current focus is on real-time listener setup
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to sync friends' workouts")
            Result.failure(e)
        }
    }

    fun setupRealtimeFeedListener(): Flow<Unit> {
        return kotlinx.coroutines.flow.callbackFlow {
            var listener: com.google.firebase.firestore.ListenerRegistration? = null

            try {
                val currentUserId = authRepository.getCurrentUserId()
                if (currentUserId == null) {
                    Timber.w("Cannot setup realtime feed listener: user not authenticated")
                    trySend(Unit)
                    close()
                    return@callbackFlow
                }

                Timber.d("Setting up real-time feed listener for user: $currentUserId")

                // Get friend IDs for real-time workout updates
                val friendIds = friendDao.getFriends(currentUserId).map { friendEntities ->
                    friendEntities.filter { it.status == FriendStatus.ACCEPTED.name }
                        .map { it.friendUserId }
                }.catch { emit(emptyList()) }

                friendIds.collect { friends ->
                    // Remove existing listener if any
                    listener?.remove()

                    if (friends.isNotEmpty()) {
                        // Setup real-time listener for friends' workouts
                        listener = firestore.collection("workouts")
                            .whereIn("userId", friends.take(10))
                            .whereNotEqualTo("completedAt", null)
                            .orderBy("completedAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                            .limit(20)
                            .addSnapshotListener { snapshot, error ->
                                if (error != null) {
                                    Timber.e(error, "Error in real-time feed listener")
                                    return@addSnapshotListener
                                }

                                snapshot?.let { querySnapshot ->
                                    val workoutCount = querySnapshot.documents.size
                                    Timber.d("Real-time feed update: $workoutCount friends' workouts")

                                    // Notify that feed data has been updated
                                    trySend(Unit)
                                }
                            }

                        Timber.d("Real-time feed listener established for ${friends.size} friends")
                    } else {
                        Timber.d("No friends found - real-time feed listener not needed")
                    }

                    trySend(Unit)
                }

            } catch (e: Exception) {
                Timber.e(e, "Failed to setup real-time feed listener")
                close(e)
            }

            awaitClose {
                listener?.remove()
                Timber.d("Real-time feed listener removed")
            }
        }
    }

    suspend fun updateUserPresence(): Result<Unit> {
        return try {
            val currentUserId = authRepository.getCurrentUserId()
                ?: return Result.failure(Exception("User not authenticated"))

            val presenceData = mapOf(
                "status" to "online",
                "last_active" to com.google.firebase.Timestamp.now(),
                "user_id" to currentUserId
            )

            firestore.collection("user_presence")
                .document(currentUserId)
                .set(presenceData)
                .await()

            Timber.d("User presence updated successfully for user: $currentUserId")
            Result.success(Unit)

        } catch (e: Exception) {
            Timber.e(e, "Failed to update user presence")
            Result.failure(e)
        }
    }

    /**
     * Get user recommendations based on mutual friends
     */
    private suspend fun getMutualFriendsRecommendations(
        currentUserId: String,
        existingFriendIds: Set<String>,
        limit: Int,
        offset: Int
    ): List<RecommendedUser> {
        return try {
            if (existingFriendIds.isEmpty()) {
                return emptyList()
            }
            
            // Query Firebase for users who are friends with current user's friends
            val mutualFriendsQuery = firestore.collection(FRIENDSHIPS_COLLECTION)
                .whereIn("senderId", existingFriendIds.toList())
                .whereEqualTo("status", "accepted")
                .limit((limit + offset).toLong())
                .get()
                .await()
                
            val potentialFriends = mutableSetOf<String>()
            
            for (document in mutualFriendsQuery.documents) {
                val receiverId = document.getString("receiverId")
                if (receiverId != null && 
                    receiverId != currentUserId && 
                    receiverId !in existingFriendIds) {
                    potentialFriends.add(receiverId)
                }
            }
            
            // Get user details for mutual friends recommendations
            val recommendations = mutableListOf<RecommendedUser>()
            
            for (userId in potentialFriends.drop(offset).take(limit)) {
                val user = getUserById(userId)
                if (user != null) {
                    recommendations.add(
                        RecommendedUser.fromUser(user, isFollowing = false)
                    )
                }
            }
            
            Timber.d("Found ${recommendations.size} mutual friends recommendations")
            recommendations
            
        } catch (e: com.google.firebase.firestore.FirebaseFirestoreException) {
            when (e.code) {
                com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED -> {
                    Timber.w("Permission denied for mutual friends recommendations - social features may be disabled")
                    emptyList()
                }
                else -> {
                    Timber.e(e, "Failed to get mutual friends recommendations")
                    emptyList()
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to get mutual friends recommendations")
            emptyList()
        }
    }

    /**
     * Get general user recommendations from Firebase
     */
    private suspend fun getGeneralRecommendations(
        currentUserId: String,
        excludeUserIds: Set<String>,
        limit: Int,
        offset: Int
    ): List<RecommendedUser> {
        return try {
            // Get random sample of users from Firebase
            val generalQuery = firestore.collection(USERS_COLLECTION)
                .whereEqualTo("onboardingCompleted", true)
                .limit((limit * 2 + offset).toLong()) // Get extra to account for filtering
                .get()
                .await()
                
            val recommendations = mutableListOf<RecommendedUser>()
            
            for (document in generalQuery.documents) {
                val userId = document.id
                
                // Skip current user and existing friends
                if (userId == currentUserId || userId in excludeUserIds) {
                    continue
                }
                
                try {
                    val userData = document.data ?: continue
                    val user = User(
                        uid = userId,
                        email = userData["email"] as? String ?: "",
                        displayName = userData["displayName"] as? String,
                        photoUrl = userData["photoUrl"] as? String,
                        isAnonymous = userData["isAnonymous"] as? Boolean ?: false,
                        subscriptionTier = com.example.liftrix.domain.model.SubscriptionTier.FREE,
                        subscriptionStatus = com.example.liftrix.domain.model.SubscriptionStatus.ACTIVE,
                        subscriptionExpiresAt = null,
                        premiumFeaturesEnabled = false,
                        onboardingCompleted = userData["onboardingCompleted"] as? Boolean ?: false,
                        profileVersion = userData["profileVersion"] as? Long ?: 1L,
                        createdAt = java.time.LocalDateTime.now(),
                        lastSignInAt = java.time.LocalDateTime.now(),
                        updatedAt = java.time.LocalDateTime.now()
                    )
                    
                    recommendations.add(
                        RecommendedUser.fromUser(user, isFollowing = false)
                    )
                    
                } catch (e: Exception) {
                    Timber.w(e, "Failed to parse user for general recommendations: $userId")
                }
            }
            
            val finalRecommendations = recommendations.drop(offset).take(limit)
            Timber.d("Found ${finalRecommendations.size} general recommendations")
            finalRecommendations
            
        } catch (e: com.google.firebase.firestore.FirebaseFirestoreException) {
            when (e.code) {
                com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED -> {
                    Timber.w("Permission denied for user recommendations - social features may be disabled")
                    emptyList()
                }
                else -> {
                    Timber.e(e, "Failed to get general recommendations")
                    emptyList()
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to get general recommendations")
            emptyList()
        }
    }

    // Private helper methods for Firebase sync

    private suspend fun syncFriendRequestToFirebase(senderId: String, receiverId: String) {
        try {
            val friendshipData = mapOf(
                "senderId" to senderId,
                "receiverId" to receiverId,
                "status" to "pending",
                "created_at" to com.google.firebase.Timestamp.now(),
                "updated_at" to com.google.firebase.Timestamp.now()
            )

            firestore.collection(FRIENDSHIPS_COLLECTION)
                .document("${senderId}_${receiverId}")
                .set(friendshipData)
                .await()

            Timber.d("Friend request synced to Firebase: $senderId -> $receiverId")
        } catch (e: Exception) {
            Timber.e(e, "Failed to sync friend request to Firebase")
            // Don't fail the operation - offline-first approach
        }
    }

    private suspend fun syncFriendResponseToFirebase(receiverId: String, senderId: String, accepted: Boolean) {
        try {
            val status = if (accepted) "accepted" else "declined"
            val updateData = mapOf(
                "status" to status,
                "updated_at" to com.google.firebase.Timestamp.now()
            )

            firestore.collection(FRIENDSHIPS_COLLECTION)
                .document("${senderId}_${receiverId}")
                .update(updateData)
                .await()

            Timber.d("Friend response synced to Firebase: $receiverId $status $senderId")
        } catch (e: Exception) {
            Timber.e(e, "Failed to sync friend response to Firebase")
        }
    }

    private suspend fun syncBlockUserToFirebase(blockerId: String, blockedId: String) {
        try {
            val blockData = mapOf(
                "senderId" to blockerId,
                "receiverId" to blockedId,
                "status" to "blocked",
                "created_at" to com.google.firebase.Timestamp.now(),
                "updated_at" to com.google.firebase.Timestamp.now()
            )

            firestore.collection(FRIENDSHIPS_COLLECTION)
                .document("${blockerId}_${blockedId}")
                .set(blockData)
                .await()

            Timber.d("User block synced to Firebase: $blockerId blocked $blockedId")
        } catch (e: Exception) {
            Timber.e(e, "Failed to sync block user to Firebase")
        }
    }

    private suspend fun syncUnblockUserToFirebase(unblockerId: String, unblockedId: String) {
        try {
            firestore.collection(FRIENDSHIPS_COLLECTION)
                .document("${unblockerId}_${unblockedId}")
                .delete()
                .await()

            Timber.d("User unblock synced to Firebase: $unblockerId unblocked $unblockedId")
        } catch (e: Exception) {
            Timber.e(e, "Failed to sync unblock user to Firebase")
        }
    }

    private suspend fun syncRemoveFriendToFirebase(userId: String, friendId: String) {
        try {
            // Remove both directions of the friendship
            firestore.collection(FRIENDSHIPS_COLLECTION)
                .document("${userId}_${friendId}")
                .delete()
                .await()

            firestore.collection(FRIENDSHIPS_COLLECTION)
                .document("${friendId}_${userId}")
                .delete()
                .await()

            Timber.d("Friend removal synced to Firebase: $userId removed $friendId")
        } catch (e: Exception) {
            Timber.e(e, "Failed to sync friend removal to Firebase")
        }
    }
} 