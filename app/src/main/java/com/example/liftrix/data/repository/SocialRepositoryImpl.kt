package com.example.liftrix.data.repository

import com.example.liftrix.data.cache.RecommendationCache
import com.example.liftrix.data.local.dao.FriendDao
import com.example.liftrix.data.local.dao.PrivacySettingsDao
import com.example.liftrix.data.local.dao.WorkoutDao
import com.example.liftrix.data.mapper.FriendMapper
import com.example.liftrix.domain.model.Friend
import com.example.liftrix.domain.model.FriendStatus
import com.example.liftrix.domain.model.RecommendedUser
import com.example.liftrix.domain.model.SharedWorkout
import com.example.liftrix.domain.model.User
import com.example.liftrix.domain.model.WorkoutStatus
import java.time.Duration
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.repository.SocialRepository
import com.example.liftrix.domain.usecase.social.SocialProfileQueryUseCase
import com.google.firebase.auth.FirebaseAuth
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
    private val workoutDao: WorkoutDao,
    private val friendMapper: FriendMapper,
    private val authRepository: AuthRepository,
    private val firestore: FirebaseFirestore,
    private val recommendationCache: RecommendationCache,
    private val socialProfileQueryUseCase: SocialProfileQueryUseCase,
    private val userSuggestionService: com.example.liftrix.domain.service.UserSuggestionService
) : SocialRepository {

    companion object {
        private const val USERS_COLLECTION = "users"
        private const val FRIENDSHIPS_COLLECTION = "friendships"
        private const val MAX_SEARCH_RESULTS = 20
        private const val DISCOVERY_BATCH_SIZE = 10
        private const val MUTUAL_FRIENDS_WEIGHT = 0.5 // 50% mutual friends, 50% general discovery
    }
    
    /**
     * Check if there's a mutual follow relationship between current user and target user
     */
    private suspend fun checkMutualRelationship(currentUserId: String, targetUserId: String): Boolean {
        return try {
            // Check if current user follows target
            val currentFollowsTarget = friendDao.getFriendRelationship(currentUserId, targetUserId) != null
            // Check if target follows current user  
            val targetFollowsCurrent = friendDao.getFriendRelationship(targetUserId, currentUserId) != null
            
            currentFollowsTarget && targetFollowsCurrent
        } catch (e: Exception) {
            Timber.w(e, "Failed to check mutual relationship between $currentUserId and $targetUserId")
            false
        }
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
            
            Timber.d("DEBUG_SEND_FRIEND_REQUEST: Created friend request entity - userId: ${friendRequest.userId}, friendUserId: ${friendRequest.friendUserId}, status: ${friendRequest.status}")

            // Save to local database (offline-first)
            val insertId = friendDao.insertFriend(friendRequest)
            Timber.d("DEBUG_SEND_FRIEND_REQUEST: Inserted friend request to database with ID: $insertId")

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
        Timber.d("DEBUG_REPO: getFriends called for userId: $userId")
        return friendDao.getFriends(userId).map { friendEntities ->
            Timber.d("DEBUG_REPO: Retrieved ${friendEntities.size} friend entities from database")
            friendEntities.forEachIndexed { index, entity ->
                Timber.d("DEBUG_REPO: Entity $index - userId: ${entity.userId}, friendUserId: ${entity.friendUserId}, status: ${entity.status}")
            }
            
            friendEntities.mapNotNull { entity ->
                try {
                    // Get the friend's profile data for proper display
                    val friendProfile = socialProfileQueryUseCase(entity.friendUserId).getOrNull()
                    Timber.d("DEBUG_REPO: Mapping entity ${entity.friendUserId}, profile found: ${friendProfile != null}")
                    
                    val mappedFriend = friendMapper.toDomain(
                        entity = entity,
                        displayName = friendProfile?.displayName ?: "User ${entity.friendUserId.take(8)}",
                        email = null, // Email is not in social profile - could be added if needed
                        avatarUrl = friendProfile?.profilePhotoUrl,
                        presence = null, // Presence service integration can be added when available
                        isMutual = checkMutualRelationship(userId, entity.friendUserId)
                    )
                    Timber.d("DEBUG_REPO: Successfully mapped friend: ${mappedFriend.displayName} (${mappedFriend.userId})")
                    mappedFriend
                } catch (e: Exception) {
                    Timber.w(e, "DEBUG_REPO: Failed to map friend entity: ${entity.friendUserId}")
                    null
                }
            }
        }.catch { e ->
            Timber.e(e, "DEBUG_REPO: Failed to get friends for user: $userId")
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
                        presence = null,
                        isMutual = false // Pending requests are not mutual
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
        try {
            // Get user's friends
            val friends = friendDao.getFriends(userId).map { friendEntities ->
                friendEntities.filter { it.status == FriendStatus.ACCEPTED.name }
                    .map { it.friendUserId }
            }.catch { 
                Timber.w("Failed to get friends for user: $userId")
                emit(emptyList()) 
            }
            
            friends.collect { friendIds ->
                if (friendIds.isEmpty()) {
                    emit(emptyList())
                    return@collect
                }
                
                // Get recent completed workouts from friends
                val sharedWorkouts = mutableListOf<SharedWorkout>()
                
                for (friendId in friendIds.take(10)) { // Limit to 10 friends for performance
                    try {
                        val friendWorkouts = workoutDao.getRecentCompletedWorkouts(friendId, 5).map { workouts ->
                            workouts.filter { it.status == WorkoutStatus.COMPLETED }
                        }.catch { emit(emptyList()) }
                        
                        friendWorkouts.collect { workouts ->
                            for (workout in workouts) {
                                try {
                                    // Get friend's display name
                                    val friendProfile = socialProfileQueryUseCase(friendId).getOrNull()
                                    val friendName = friendProfile?.displayName ?: "Friend"
                                    
                                    // Calculate duration
                                    val duration = if (workout.startTime != null && workout.endTime != null) {
                                        Duration.between(workout.startTime, workout.endTime)
                                    } else {
                                        Duration.ZERO
                                    }
                                    
                                    // Parse exercise count from JSON (simplified)
                                    val exerciseCount = try {
                                        // This is a simplified implementation - in a real scenario, we'd properly parse the JSON
                                        workout.exercisesJson.count { it == '{' }.coerceAtLeast(1)
                                    } catch (e: Exception) {
                                        1 // Default to 1 exercise if parsing fails
                                    }
                                    
                                    val sharedWorkout = SharedWorkout(
                                        id = workout.id,
                                        friendUserId = friendId,
                                        friendDisplayName = friendName,
                                        workoutName = workout.name,
                                        completedAt = workout.endTime ?: workout.updatedAt,
                                        duration = duration,
                                        exerciseCount = exerciseCount,
                                        sharedAt = workout.updatedAt
                                    )
                                    
                                    sharedWorkouts.add(sharedWorkout)
                                } catch (e: Exception) {
                                    Timber.w(e, "Failed to map workout to shared workout: ${workout.id}")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to get workouts for friend: $friendId")
                    }
                }
                
                // Sort by completion time (most recent first) and limit to 50 workouts
                val sortedWorkouts = sharedWorkouts
                    .sortedByDescending { it.completedAt }
                    .take(50)
                
                emit(sortedWorkouts)
                Timber.d("Friend workout feed loaded: ${sortedWorkouts.size} workouts for user: $userId")
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to get friend workout feed for user: $userId")
            emit(emptyList())
        }
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
                
                // Step 4: Get discovery recommendations to fill remaining slots
                val remainingSlots = limit - recommendations.size
                if (remainingSlots > 0) {
                        // For new users (< 3 friends), use sophisticated UserSuggestionService
                    if (friendIds.size < 3) {
                        
                        try {
                            // Use the sophisticated recommendation service for new users
                            val suggestionResult = userSuggestionService.getNewUserSuggestions(currentUserId, remainingSlots)
                            
                            suggestionResult.fold(
                                onSuccess = { suggestions ->
                                    val convertedUsers = suggestions.map { suggestion ->
                                        RecommendedUser(
                                            userId = suggestion.userSearchResult.userId,
                                            username = suggestion.userSearchResult.displayName,
                                            profileImageUrl = suggestion.userSearchResult.profileImageUrl,
                                            isFollowing = false
                                        )
                                    }
                                    recommendations.addAll(convertedUsers)
                                },
                                onFailure = { error ->
                                    // Fallback to basic recommendations
                                    val generalUsers = getGeneralRecommendations(
                                        currentUserId = currentUserId,
                                        excludeUserIds = friendIds + recommendations.map { it.userId }.toSet(),
                                        limit = remainingSlots,
                                        offset = offset
                                    )
                                    recommendations.addAll(generalUsers)
                                }
                            )
                        } catch (e: Exception) {
                            // Fallback to basic recommendations
                            val generalUsers = getGeneralRecommendations(
                                currentUserId = currentUserId,
                                excludeUserIds = friendIds + recommendations.map { it.userId }.toSet(),
                                limit = remainingSlots,
                                offset = offset
                            )
                            recommendations.addAll(generalUsers)
                        }
                    } else {
                        // For established users, use the basic Firebase query approach
                        val generalUsers = getGeneralRecommendations(
                            currentUserId = currentUserId,
                            excludeUserIds = friendIds + recommendations.map { it.userId }.toSet(),
                            limit = remainingSlots,
                            offset = offset
                        )
                        recommendations.addAll(generalUsers)
                    }
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

            Timber.d("DEBUG_REPO_FOLLOW: followUser called - currentUserId: $currentUserId, targetUserId: $userId")

            // Check if there's already a pending request from the target user
            val incomingRequest = friendDao.getFriendRelationship(userId, currentUserId)
            Timber.d("DEBUG_REPO_FOLLOW: Checked for incoming request - found: ${incomingRequest != null}, status: ${incomingRequest?.status}")
            
            if (incomingRequest != null && incomingRequest.status == FriendStatus.PENDING.name) {
                // Accept existing friend request
                Timber.d("DEBUG_REPO_FOLLOW: Accepting existing friend request")
                respondToFriendRequest(userId, accept = true)
            } else {
                // Send new friend request
                Timber.d("DEBUG_REPO_FOLLOW: Sending new friend request")
                val result = sendFriendRequest(userId)
                Timber.d("DEBUG_REPO_FOLLOW: sendFriendRequest result: ${if (result.isSuccess) "SUCCESS" else "FAILURE"}")
                result
            }
            
        } catch (e: Exception) {
            Timber.e(e, "DEBUG_REPO_FOLLOW: Failed to follow user: $userId")
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
                    // Check actual follow status instead of hardcoding false
                    val isFollowing = friendDao.getFriendRelationship(currentUserId, userId)?.status == FriendStatus.ACCEPTED.name
                    recommendations.add(
                        RecommendedUser.fromUser(user, isFollowing = isFollowing)
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
     * Now prioritizes recent public profiles for Instagram-like discovery
     */
    private suspend fun getGeneralRecommendations(
        currentUserId: String,
        excludeUserIds: Set<String>,
        limit: Int,
        offset: Int
    ): List<RecommendedUser> {
        return try {
            // TEMPORARY: Most basic query to test permissions
            // First, try to get any social profiles collection documents
            val socialProfilesQuery = firestore.collection("social_profiles")
                .limit(10) // Very basic query with no where clauses
                .get()
                .await()
            
            val recommendations = mutableListOf<RecommendedUser>()
            
            // Process social profiles first
            for (document in socialProfilesQuery.documents) {
                val userId = document.getString("userId") ?: continue
                
                // Skip current user and excluded users
                if (userId == currentUserId || userId in excludeUserIds) {
                    continue
                }
                
                try {
                    val profileData = document.data ?: continue
                    
                    // TEMPORARY: Client-side filtering for basic query
                    val isPrivate = profileData["isPrivate"] as? Boolean ?: true
                    val hideFromSuggestions = profileData["hideFromSuggestions"] as? Boolean ?: false
                    
                    if (isPrivate) {
                        continue
                    }
                    
                    if (hideFromSuggestions) {
                        continue
                    }
                    
                    val username = profileData["username"] as? String ?: continue
                    val displayName = profileData["displayName"] as? String
                    val profilePhotoUrl = profileData["profilePhotoUrl"] as? String
                    
                    // Check actual follow status instead of hardcoding false
                    val isFollowing = friendDao.getFriendRelationship(currentUserId, userId)?.status == FriendStatus.ACCEPTED.name
                    
                    recommendations.add(
                        RecommendedUser(
                            userId = userId,
                            username = displayName ?: username,
                            profileImageUrl = profilePhotoUrl,
                            isFollowing = isFollowing
                        )
                    )
                    
                    // Stop if we have enough recommendations
                    if (recommendations.size >= limit) {
                        break
                    }
                } catch (e: Exception) {
                    Timber.w(e, "Failed to parse social profile for recommendations: $userId")
                }
            }
            
            // Fallback to users collection if we don't have enough recommendations from social_profiles
            if (recommendations.size < limit) {
                try {
                    // 🔍 DEBUG: Confirm authentication state before query
                    val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                    Timber.d("AUTH_DEBUG: Current user before users query: ${currentUser?.uid}")
                    
                    if (currentUser == null) {
                        Timber.w("AUTH_DEBUG: No authenticated user - query will fail with PERMISSION_DENIED")
                        // Don't use return here, just skip the query
                    } else {
                    
                    Timber.d("AUTH_DEBUG: Executing users collection query with isPublic=true filter")
                    val usersQuery = firestore.collection("users")
                        .whereEqualTo("isPublic", true)
                        .limit((limit - recommendations.size + 5).toLong()) // Get a few extra to account for filtering
                        .get()
                        .await()
                    
                    
                    Timber.d("AUTH_DEBUG: Query returned ${usersQuery.documents.size} documents")
                    
                    for (userDoc in usersQuery.documents) {
                        if (recommendations.size >= limit) break
                        
                        // 🔍 DEBUG: Log document data to verify isPublic field
                        val docData = userDoc.data
                        val hasIsPublic = docData?.containsKey("isPublic") ?: false
                        val isPublicValue = docData?.get("isPublic")
                        Timber.d("AUTH_DEBUG: Doc ${userDoc.id} - hasIsPublic: $hasIsPublic, isPublic: $isPublicValue")
                        
                        val userId = userDoc.id
                        if (userId == currentUserId || userId in excludeUserIds) continue
                        
                        val userData = userDoc.data ?: continue
                        val username = userData["username"] as? String ?: continue
                        val displayName = userData["displayName"] as? String
                        
                        // Check if this user already has a social profile (to avoid duplicates)
                        val hasSocialProfile = recommendations.any { it.userId == userId }
                        if (!hasSocialProfile) {
                            // Check actual follow status instead of hardcoding false
                            val isFollowing = friendDao.getFriendRelationship(currentUserId, userId)?.status == FriendStatus.ACCEPTED.name
                            recommendations.add(
                                RecommendedUser(
                                    userId = userId,
                                    username = displayName ?: username,
                                    profileImageUrl = null,
                                    isFollowing = isFollowing
                                )
                            )
                        }
                    }
                    } // Close the else block here
                } catch (fallbackError: Exception) {
                    when (fallbackError.message?.contains("PERMISSION_DENIED")) {
                        true -> {
                            Timber.w("AUTH_DEBUG: PERMISSION_DENIED error in users collection query")
                            Timber.w("AUTH_DEBUG: Auth state: ${com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid}")
                            Timber.w("AUTH_DEBUG: Error details: ${fallbackError.message}")
                        }
                        else -> Timber.w(fallbackError, "Fallback to users collection failed")
                    }
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
    
    override fun getFollowing(userId: String): Flow<List<Friend>> {
        Timber.d("DEBUG_REPO_FOLLOWING: getFollowing called for userId: $userId")
        return friendDao.getFollowing(userId).map { friendEntities ->
            Timber.d("DEBUG_REPO_FOLLOWING: Retrieved ${friendEntities.size} following entities from database")
            friendEntities.forEachIndexed { index, entity ->
                Timber.d("DEBUG_REPO_FOLLOWING: Entity $index - userId: ${entity.userId}, friendUserId: ${entity.friendUserId}, status: ${entity.status}")
            }
            
            friendEntities.mapNotNull { entity ->
                try {
                    // Get the friend's profile data for proper display
                    val friendProfile = socialProfileQueryUseCase(entity.friendUserId).getOrNull()
                    Timber.d("DEBUG_REPO_FOLLOWING: Mapping entity ${entity.friendUserId}, profile found: ${friendProfile != null}")
                    
                    val mappedFriend = friendMapper.toDomain(
                        entity = entity,
                        displayName = friendProfile?.displayName ?: "User ${entity.friendUserId.take(8)}",
                        email = null,
                        avatarUrl = friendProfile?.profilePhotoUrl,
                        presence = null,
                        isMutual = checkMutualRelationship(userId, entity.friendUserId)
                    )
                    Timber.d("DEBUG_REPO_FOLLOWING: Successfully mapped following: ${mappedFriend.displayName} (${mappedFriend.userId})")
                    mappedFriend
                } catch (e: Exception) {
                    Timber.w(e, "DEBUG_REPO_FOLLOWING: Failed to map following entity: ${entity.friendUserId}")
                    null
                }
            }
        }.catch { e ->
            Timber.e(e, "DEBUG_REPO_FOLLOWING: Failed to get following for user: $userId")
            emit(emptyList())
        }
    }
    
    override fun getFollowers(userId: String): Flow<List<Friend>> {
        Timber.d("DEBUG_REPO_FOLLOWERS: getFollowers called for userId: $userId")
        return friendDao.getFollowers(userId).map { friendEntities ->
            Timber.d("DEBUG_REPO_FOLLOWERS: Retrieved ${friendEntities.size} follower entities from database")
            friendEntities.forEachIndexed { index, entity ->
                Timber.d("DEBUG_REPO_FOLLOWERS: Entity $index - userId: ${entity.userId}, friendUserId: ${entity.friendUserId}, status: ${entity.status}")
            }
            
            friendEntities.mapNotNull { entity ->
                try {
                    // For followers, the "userId" field in the entity represents the follower
                    // Get the follower's profile data for proper display
                    val followerProfile = socialProfileQueryUseCase(entity.userId).getOrNull()
                    Timber.d("DEBUG_REPO_FOLLOWERS: Mapping follower entity ${entity.userId}, profile found: ${followerProfile != null}")
                    
                    val mappedFollower = friendMapper.toDomain(
                        entity = entity.copy(friendUserId = entity.userId), // Swap for proper mapping
                        displayName = followerProfile?.displayName ?: "User ${entity.userId.take(8)}",
                        email = null,
                        avatarUrl = followerProfile?.profilePhotoUrl,
                        presence = null,
                        isMutual = checkMutualRelationship(userId, entity.userId)
                    )
                    Timber.d("DEBUG_REPO_FOLLOWERS: Successfully mapped follower: ${mappedFollower.displayName} (${mappedFollower.userId})")
                    mappedFollower
                } catch (e: Exception) {
                    Timber.w(e, "DEBUG_REPO_FOLLOWERS: Failed to map follower entity: ${entity.userId}")
                    null
                }
            }
        }.catch { e ->
            Timber.e(e, "DEBUG_REPO_FOLLOWERS: Failed to get followers for user: $userId")
            emit(emptyList())
        }
    }
} 