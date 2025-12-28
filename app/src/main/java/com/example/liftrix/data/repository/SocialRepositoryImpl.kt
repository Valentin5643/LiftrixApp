package com.example.liftrix.data.repository

import com.example.liftrix.data.cache.RecommendationCache
import com.example.liftrix.data.local.dao.FriendDao
import com.example.liftrix.data.local.dao.PrivacySettingsDao
import com.example.liftrix.data.local.dao.SocialProfileDao
import com.example.liftrix.data.local.dao.WorkoutDao
import com.example.liftrix.data.mapper.FriendMapper
import com.example.liftrix.data.remote.legacy.LegacySocialFirestoreDataSource
import com.example.liftrix.domain.model.Friend
import com.example.liftrix.domain.model.FriendStatus
import com.example.liftrix.domain.model.RecommendedUser
import com.example.liftrix.domain.model.SharedWorkout
import com.example.liftrix.domain.model.User
import com.example.liftrix.domain.model.WorkoutStatus
import java.time.Duration
import com.example.liftrix.config.OfflineArchitectureFlags
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.repository.SocialRepository
import com.example.liftrix.domain.usecase.social.SocialProfileQueryUseCase
import com.example.liftrix.sync.SyncCoordinator
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
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
    private val socialProfileDao: SocialProfileDao,
    private val workoutDao: WorkoutDao,
    private val friendMapper: FriendMapper,
    private val authRepository: AuthRepository,
    private val recommendationCache: RecommendationCache,
    private val socialProfileQueryUseCase: SocialProfileQueryUseCase,
    private val userSuggestionService: com.example.liftrix.domain.service.UserSuggestionService,
    private val syncCoordinator: SyncCoordinator,
    private val legacyDataSource: LegacySocialFirestoreDataSource
) : SocialRepository {

    companion object {
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

            val searchResults = if (OfflineArchitectureFlags.FIX_SOCIAL_REPOSITORY) {
                searchUsersRoomFirst(query, currentUserId.value)
            } else {
                searchUsersLegacy(query, currentUserId.value)
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

            if (currentUserId.value == friendUserId) {
                return Result.failure(Exception("Cannot send friend request to yourself"))
            }

            // Check if relationship already exists
            val existingRelationship = friendDao.getFriendRelationship(currentUserId.value, friendUserId)
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
                currentUserId = currentUserId.value,
                friendUserId = friendUserId,
                isSynced = false
            )

            Timber.d("DEBUG_SEND_FRIEND_REQUEST: Created friend request entity - userId: ${friendRequest.userId}, friendUserId: ${friendRequest.friendUserId}, status: ${friendRequest.status}")

            if (OfflineArchitectureFlags.FIX_SOCIAL_REPOSITORY) {
                friendDao.upsertLocal(friendRequest)
                Timber.d("DEBUG_SEND_FRIEND_REQUEST: Upserted friend request locally")
                triggerImmediateSyncSafely(currentUserId.value)
            } else {
                // Save to local database (legacy path)
                val insertId = friendDao.insertFriend(friendRequest)
                Timber.d("DEBUG_SEND_FRIEND_REQUEST: Inserted friend request to database with ID: $insertId")

                // Sync to Firebase (legacy path)
                legacyDataSource.syncFriendRequest(currentUserId.value, friendUserId)
            }

            // Invalidate recommendation cache since friend relationships changed
            recommendationCache.invalidateCacheForUser(currentUserId.value)

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
            val existingRequest = friendDao.getFriendRelationship(friendUserId, currentUserId.value)
            if (existingRequest == null || existingRequest.status != FriendStatus.PENDING.name) {
                return Result.failure(Exception("No pending friend request found"))
            }

            val newStatus = if (accept) FriendStatus.ACCEPTED.name else "DECLINED"
            val now = Instant.now().toEpochMilli()

            if (OfflineArchitectureFlags.FIX_SOCIAL_REPOSITORY) {
                val updatedRequest = existingRequest.copy(
                    status = newStatus,
                    updatedAt = Instant.ofEpochMilli(now)
                )
                friendDao.upsertLocal(updatedRequest)
            } else {
                // Update the friend request status (legacy path)
                friendDao.updateFriendStatus(friendUserId, currentUserId.value, newStatus, now)
            }

            if (accept) {
                // Create bidirectional friendship
                val reciprocalFriend = friendMapper.createFriendRequest(
                    currentUserId = currentUserId.value,
                    friendUserId = friendUserId,
                    isSynced = false
                ).copy(status = FriendStatus.ACCEPTED.name)

                if (OfflineArchitectureFlags.FIX_SOCIAL_REPOSITORY) {
                    friendDao.upsertLocal(reciprocalFriend)
                } else {
                    friendDao.insertFriend(reciprocalFriend)
                }
            }

            if (OfflineArchitectureFlags.FIX_SOCIAL_REPOSITORY) {
                triggerImmediateSyncSafely(currentUserId.value)
            } else {
                // Sync to Firebase (legacy path)
                legacyDataSource.syncFriendResponse(currentUserId.value, friendUserId, accept)
            }

            // Invalidate recommendation cache for both users since friend relationships changed
            if (accept) {
                recommendationCache.invalidateCacheForUser(currentUserId.value)
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
            val existingRelationship = friendDao.getFriendRelationship(currentUserId.value, friendUserId)
            val now = Instant.now().toEpochMilli()

            if (existingRelationship != null) {
                if (OfflineArchitectureFlags.FIX_SOCIAL_REPOSITORY) {
                    val updatedRelationship = existingRelationship.copy(
                        status = FriendStatus.BLOCKED.name,
                        updatedAt = Instant.ofEpochMilli(now)
                    )
                    friendDao.upsertLocal(updatedRelationship)
                } else {
                    friendDao.updateFriendStatus(currentUserId.value, friendUserId, FriendStatus.BLOCKED.name, now)
                }
            } else {
                val blockedUser = friendMapper.createFriendRequest(currentUserId.value, friendUserId)
                    .copy(status = FriendStatus.BLOCKED.name)
                if (OfflineArchitectureFlags.FIX_SOCIAL_REPOSITORY) {
                    friendDao.upsertLocal(blockedUser)
                } else {
                    friendDao.insertFriend(blockedUser)
                }
            }

            // Remove any reciprocal friendship
            friendDao.deleteFriendRelationship(friendUserId, currentUserId.value)

            if (OfflineArchitectureFlags.FIX_SOCIAL_REPOSITORY) {
                triggerImmediateSyncSafely(currentUserId.value)
            } else {
                // Sync to Firebase (legacy path)
                legacyDataSource.syncBlockUser(currentUserId.value, friendUserId)
            }

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
            friendDao.deleteFriendRelationship(currentUserId.value, friendUserId)

            if (OfflineArchitectureFlags.FIX_SOCIAL_REPOSITORY) {
                triggerImmediateSyncSafely(currentUserId.value)
            } else {
                // Sync to Firebase (legacy path)
                legacyDataSource.syncUnblockUser(currentUserId.value, friendUserId)
            }

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
            friendDao.deleteBidirectionalFriendRelationship(currentUserId.value, friendUserId)

            if (OfflineArchitectureFlags.FIX_SOCIAL_REPOSITORY) {
                triggerImmediateSyncSafely(currentUserId.value)
            } else {
                // Sync to Firebase (legacy path)
                legacyDataSource.syncRemoveFriend(currentUserId.value, friendUserId)
            }

            // Invalidate recommendation cache for both users since friend relationships changed
            recommendationCache.invalidateCacheForUser(currentUserId.value)
            recommendationCache.invalidateCacheForUser(friendUserId)

            Timber.d("Friend removed successfully: $currentUserId removed $friendUserId")
            Result.success(Unit)

        } catch (e: Exception) {
            Timber.e(e, "Failed to remove friend: $friendUserId")
            Result.failure(e)
        }
    }
    
    override suspend fun getUserById(userId: String): User? {
        return if (OfflineArchitectureFlags.FIX_SOCIAL_REPOSITORY) {
            getUserByIdRoomFirst(userId)
        } else {
            getUserByIdLegacy(userId)
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
                val cachedRecommendations = recommendationCache.getCachedRecommendations(currentUserId.value)
                if (cachedRecommendations != null) {
                    val cachedResults = cachedRecommendations.take(limit)
                    Timber.d("Cache hit: returning ${cachedResults.size} cached recommendations")
                    emit(cachedResults)
                    return@flow
                }
                Timber.d("Cache miss: proceeding with fresh API call")
            }

            // Step 2: Get current user's friends to exclude from recommendations
            val existingFriendIds = friendDao.getFriends(currentUserId.value).map { friendEntities ->
                friendEntities.map { it.friendUserId }.toSet()
            }.catch { emit(emptySet()) }

            existingFriendIds.collect { friendIds ->
                val recommendations = mutableListOf<RecommendedUser>()

                // Step 3: Get mutual friends recommendations (50% of limit)
                val mutualFriendsLimit = (limit * MUTUAL_FRIENDS_WEIGHT).toInt()
                if (mutualFriendsLimit > 0) {
                    val mutualFriendsUsers = getMutualFriendsRecommendations(
                        currentUserId = currentUserId.value,
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
                            val suggestionResult = userSuggestionService.getNewUserSuggestions(currentUserId.value, remainingSlots)
                            
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
                                        currentUserId = currentUserId.value,
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
                                currentUserId = currentUserId.value,
                                excludeUserIds = friendIds + recommendations.map { it.userId }.toSet(),
                                limit = remainingSlots,
                                offset = offset
                            )
                            recommendations.addAll(generalUsers)
                        }
                    } else {
                        // For established users, use the basic Firebase query approach
                        val generalUsers = getGeneralRecommendations(
                            currentUserId = currentUserId.value,
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
                    recommendationCache.cacheRecommendations(currentUserId.value, finalRecommendations)
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
            val incomingRequest = friendDao.getFriendRelationship(userId, currentUserId.value)
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
                recommendationCache.invalidateCacheForUser(currentUserId.value)
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
            val friendIds = friendDao.getFriends(currentUserId.value).map { friendEntities ->
                friendEntities.filter { it.status == FriendStatus.ACCEPTED.name }
                    .map { it.friendUserId }
            }.catch { emit(emptyList()) }

            friendIds.collect { friends ->
                if (friends.isNotEmpty()) {
                    if (OfflineArchitectureFlags.FIX_SOCIAL_REPOSITORY) {
                        // Room-first: rely on local workouts + sync workers to refresh data
                        triggerImmediateSyncSafely(currentUserId.value)
                    } else {
                        val fetchedCount = legacyDataSource.fetchFriendsWorkouts(friends)
                        Timber.d("Retrieved $fetchedCount friends' workouts from Firebase")
                    }
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to sync friends' workouts")
            Result.failure(e)
        }
    }

    fun setupRealtimeFeedListener(): Flow<Unit> {
        return if (OfflineArchitectureFlags.FIX_SOCIAL_REPOSITORY) {
            callbackFlow {
                try {
                    val currentUserId = authRepository.getCurrentUserId()
                    if (currentUserId == null) {
                        Timber.w("Cannot setup local feed listener: user not authenticated")
                        trySend(Unit)
                        close()
                        return@callbackFlow
                    }

                    Timber.d("Setting up local feed listener for user: $currentUserId")
                    val job = launch {
                        friendDao.getFriends(currentUserId.value).collect {
                            trySend(Unit)
                        }
                    }

                    awaitClose {
                        job.cancel()
                        Timber.d("Local feed listener removed")
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to setup local feed listener")
                    close(e)
                }
            }
        } else {
            val friendIdsFlow = flow {
                val currentUserId = authRepository.getCurrentUserId()
                if (currentUserId == null) {
                    emit(emptyList())
                    return@flow
                }

                emitAll(
                    friendDao.getFriends(currentUserId.value)
                        .map { friendEntities ->
                            friendEntities.filter { it.status == FriendStatus.ACCEPTED.name }
                                .map { it.friendUserId }
                        }
                        .catch { emit(emptyList()) }
                )
            }

            legacyDataSource.setupRealtimeFeedListener(friendIdsFlow)
        }
    }

    suspend fun updateUserPresence(): Result<Unit> {
        return try {
            val currentUserId = authRepository.getCurrentUserId()
                ?: return Result.failure(Exception("User not authenticated"))

            if (OfflineArchitectureFlags.FIX_SOCIAL_REPOSITORY) {
                val existingProfile = socialProfileDao.getSocialProfileByUserId(currentUserId.value)
                    ?: return Result.failure(Exception("Social profile not found"))
                val now = System.currentTimeMillis()
                val updatedProfile = existingProfile.copy(
                    lastActive = now,
                    updatedAt = now
                )
                socialProfileDao.upsertLocal(updatedProfile)
                triggerImmediateSyncSafely(currentUserId.value)
            } else {
                legacyDataSource.updateUserPresence(currentUserId.value)
            }

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
        return if (OfflineArchitectureFlags.FIX_SOCIAL_REPOSITORY) {
            getMutualFriendsRecommendationsRoomFirst(currentUserId, existingFriendIds, limit, offset)
        } else {
            getMutualFriendsRecommendationsLegacy(currentUserId, existingFriendIds, limit, offset)
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
        return if (OfflineArchitectureFlags.FIX_SOCIAL_REPOSITORY) {
            getGeneralRecommendationsRoomFirst(currentUserId, excludeUserIds, limit, offset)
        } else {
            getGeneralRecommendationsLegacy(currentUserId, excludeUserIds, limit, offset)
        }
    }

    private suspend fun searchUsersRoomFirst(query: String, currentUserId: String): List<User> {
        val displayMatches = socialProfileDao.searchProfilesByDisplayName(query, MAX_SEARCH_RESULTS)
        val usernameMatches = socialProfileDao.searchProfiles(currentUserId, query, MAX_SEARCH_RESULTS)
        return (displayMatches + usernameMatches)
            .distinctBy { it.userId }
            .filter { it.userId != currentUserId }
            .take(MAX_SEARCH_RESULTS)
            .map { mapSocialProfileToUser(it) }
    }

    private suspend fun searchUsersLegacy(query: String, currentUserId: String): List<User> {
        return try {
            legacyDataSource.searchUsers(query, MAX_SEARCH_RESULTS, 5)
                .mapNotNull { record ->
                    if (record.id == currentUserId) {
                        return@mapNotNull null
                    }
                    try {
                        mapLegacyUserRecordToUser(record)
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to parse legacy user record: ${record.id}")
                        null
                    }
                }
        } catch (e: Exception) {
            Timber.e(e, "Legacy user search failed")
            emptyList()
        }
    }

    private suspend fun getUserByIdRoomFirst(userId: String): User? {
        val profile = socialProfileDao.getSocialProfileByUserId(userId) ?: return null
        return mapSocialProfileToUser(profile)
    }

    private suspend fun getUserByIdLegacy(userId: String): User? {
        return try {
            legacyDataSource.getUserRecord(userId)?.let { mapLegacyUserRecordToUser(it) }
        } catch (e: Exception) {
            Timber.e(e, "Failed to get user by ID: $userId")
            null
        }
    }

    private fun mapSocialProfileToUser(profile: com.example.liftrix.data.local.entity.SocialProfileEntity): User {
        return User.forSocialDisplay(
            uid = profile.userId,
            displayName = profile.displayName ?: profile.username,
            photoUrl = profile.profilePhotoUrl
        )
    }

    private fun mapLegacyUserRecordToUser(record: LegacySocialFirestoreDataSource.LegacyUserRecord): User {
        val userData = record.data
        val now = java.time.LocalDateTime.now()
        return User(
            uid = record.id,
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
            createdAt = now,
            lastSignInAt = now,
            updatedAt = now
        )
    }

    private suspend fun getMutualFriendsRecommendationsRoomFirst(
        currentUserId: String,
        existingFriendIds: Set<String>,
        limit: Int,
        offset: Int
    ): List<RecommendedUser> {
        if (existingFriendIds.isEmpty()) {
            return emptyList()
        }

        val suggestions = userSuggestionService
            .getMutualConnectionSuggestions(currentUserId, limit + offset)
            .getOrElse {
                Timber.w(it, "Failed to get mutual connection suggestions")
                emptyList()
            }

        return suggestions
            .mapNotNull { suggestion ->
                val userId = suggestion.userSearchResult.userId
                if (userId == currentUserId || userId in existingFriendIds) {
                    return@mapNotNull null
                }
                val isFollowing = friendDao.getFriendRelationship(currentUserId, userId)?.status == FriendStatus.ACCEPTED.name
                RecommendedUser(
                    userId = userId,
                    username = suggestion.userSearchResult.displayName.ifBlank { "Unknown User" },
                    profileImageUrl = suggestion.userSearchResult.profileImageUrl,
                    isFollowing = isFollowing
                )
            }
            .drop(offset)
            .take(limit)
    }

    private suspend fun getMutualFriendsRecommendationsLegacy(
        currentUserId: String,
        existingFriendIds: Set<String>,
        limit: Int,
        offset: Int
    ): List<RecommendedUser> {
        return try {
            val candidateIds = legacyDataSource.getMutualFriendUserIds(existingFriendIds, limit, offset)
            val recommendations = mutableListOf<RecommendedUser>()

            for (userId in candidateIds) {
                if (userId == currentUserId || userId in existingFriendIds) continue
                val user = getUserByIdLegacy(userId)
                if (user != null) {
                    val isFollowing = friendDao.getFriendRelationship(currentUserId, userId)?.status == FriendStatus.ACCEPTED.name
                    recommendations.add(RecommendedUser.fromUser(user, isFollowing = isFollowing))
                }
            }

            Timber.d("Found ${recommendations.size} mutual friends recommendations")
            recommendations
        } catch (e: Exception) {
            Timber.e(e, "Failed to get mutual friends recommendations")
            emptyList()
        }
    }

    private suspend fun getGeneralRecommendationsRoomFirst(
        currentUserId: String,
        excludeUserIds: Set<String>,
        limit: Int,
        offset: Int
    ): List<RecommendedUser> {
        val suggestions = userSuggestionService
            .getPersonalizedSuggestions(currentUserId, limit + offset)
            .getOrElse {
                Timber.w(it, "Failed to get personalized suggestions")
                emptyList()
            }

        val personalized = suggestions
            .mapNotNull { suggestion ->
                val userId = suggestion.userSearchResult.userId
                if (userId == currentUserId || userId in excludeUserIds) {
                    return@mapNotNull null
                }
                val isFollowing = friendDao.getFriendRelationship(currentUserId, userId)?.status == FriendStatus.ACCEPTED.name
                RecommendedUser(
                    userId = userId,
                    username = suggestion.userSearchResult.displayName.ifBlank { "Unknown User" },
                    profileImageUrl = suggestion.userSearchResult.profileImageUrl,
                    isFollowing = isFollowing
                )
            }
            .drop(offset)
            .take(limit)

        if (personalized.isNotEmpty()) {
            return personalized
        }

        val fallbackProfiles = socialProfileDao.getDiscoverableProfiles(currentUserId, limit + offset)
        return fallbackProfiles
            .filter { it.userId != currentUserId && it.userId !in excludeUserIds }
            .drop(offset)
            .take(limit)
            .map { profile ->
                val isFollowing = friendDao.getFriendRelationship(currentUserId, profile.userId)?.status == FriendStatus.ACCEPTED.name
                RecommendedUser(
                    userId = profile.userId,
                    username = (profile.displayName ?: profile.username).ifBlank { "Unknown User" },
                    profileImageUrl = profile.profilePhotoUrl,
                    isFollowing = isFollowing
                )
            }
    }

    private suspend fun getGeneralRecommendationsLegacy(
        currentUserId: String,
        excludeUserIds: Set<String>,
        limit: Int,
        offset: Int
    ): List<RecommendedUser> {
        val candidates = legacyDataSource.getGeneralRecommendationCandidates(
            currentUserId = currentUserId,
            excludeUserIds = excludeUserIds,
            limit = limit,
            offset = offset
        )

        return candidates.mapNotNull { candidate ->
            val isFollowing = friendDao.getFriendRelationship(currentUserId, candidate.userId)?.status == FriendStatus.ACCEPTED.name
            val displayName = (candidate.displayName ?: candidate.username).ifBlank { "Unknown User" }
            if (displayName.isBlank()) {
                return@mapNotNull null
            }
            RecommendedUser(
                userId = candidate.userId,
                username = displayName,
                profileImageUrl = candidate.profilePhotoUrl,
                isFollowing = isFollowing
            )
        }
    }

    private suspend fun triggerImmediateSyncSafely(userId: String) {
        val result = syncCoordinator.triggerImmediateSync(userId)
        result.exceptionOrNull()?.let { error ->
            Timber.w(error, "Immediate sync failed for user: $userId")
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
