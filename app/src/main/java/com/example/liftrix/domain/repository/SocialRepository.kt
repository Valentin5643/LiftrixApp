package com.example.liftrix.domain.repository

import com.example.liftrix.domain.model.Friend
import com.example.liftrix.domain.model.RecommendedUser
import com.example.liftrix.domain.model.SharedWorkout
import com.example.liftrix.domain.model.User
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for social features including friend management and workout sharing
 * Follows Clean Architecture patterns with Flow-based reactive data and Result error handling
 */
interface SocialRepository {

    /**
     * Search for users by query string (name, email)
     * Searches Firebase Firestore users collection
     * 
     * @param query Search query string
     * @return Flow of matching users
     */
    fun searchUsers(query: String): Flow<List<User>>

    /**
     * Send a friend request to another user
     * Creates local FriendEntity and syncs to Firebase
     * 
     * @param friendUserId ID of user to send request to
     * @return Result indicating success or failure
     */
    suspend fun sendFriendRequest(friendUserId: String): Result<Unit>

    /**
     * Respond to a friend request (accept or decline)
     * Updates local FriendEntity status and syncs to Firebase
     * 
     * @param friendUserId ID of user who sent the request
     * @param accept True to accept, false to decline
     * @return Result indicating success or failure
     */
    suspend fun respondToFriendRequest(friendUserId: String, accept: Boolean): Result<Unit>

    /**
     * Get current user's friends list
     * Returns Flow of accepted friends with presence information
     * 
     * @param userId Current user's ID
     * @return Flow of friends list
     */
    fun getFriends(userId: String): Flow<List<Friend>>

    /**
     * Get pending friend requests for current user
     * Returns Flow of incoming friend requests
     * 
     * @param userId Current user's ID
     * @return Flow of pending friend requests
     */
    fun getPendingFriendRequests(userId: String): Flow<List<Friend>>

    /**
     * Get friend workout activity feed
     * Returns shared workouts from friends based on privacy settings
     * 
     * @param userId Current user's ID
     * @return Flow of shared workouts from friends
     */
    fun getFriendWorkoutFeed(userId: String): Flow<List<SharedWorkout>>

    /**
     * Block a user (prevents friend requests and removes existing friendship)
     * 
     * @param friendUserId ID of user to block
     * @return Result indicating success or failure
     */
    suspend fun blockUser(friendUserId: String): Result<Unit>

    /**
     * Unblock a previously blocked user
     * 
     * @param friendUserId ID of user to unblock
     * @return Result indicating success or failure
     */
    suspend fun unblockUser(friendUserId: String): Result<Unit>

    /**
     * Remove a friend (unfriend)
     * 
     * @param friendUserId ID of friend to remove
     * @return Result indicating success or failure
     */
    suspend fun removeFriend(friendUserId: String): Result<Unit>
    
    /**
     * Get user information by user ID
     * Used for displaying friend context in workout feeds
     * 
     * @param userId ID of user to retrieve
     * @return User information or null if not found
     */
    suspend fun getUserById(userId: String): User?
    
    /**
     * Get recommended users for discovery carousel
     * Returns users based on mutual friends and general discovery algorithm
     * 
     * @param limit Maximum number of recommendations to return
     * @param offset Pagination offset for loading more recommendations
     * @return Flow of recommended users with caching metadata
     */
    fun getRecommendedUsers(limit: Int, offset: Int): Flow<List<RecommendedUser>>
    
    /**
     * Follow a user (send friend request or accept existing request)
     * Updates local state and syncs to Firebase
     * 
     * @param userId ID of user to follow
     * @return Result indicating success or failure
     */
    suspend fun followUser(userId: String): Result<Unit>
    
    /**
     * Refresh discovery cache by clearing stale recommendations
     * Triggers fresh discovery algorithm on next getRecommendedUsers call
     * 
     * @return Result indicating success or failure
     */
    suspend fun refreshDiscoveryCache(): Result<Unit>
    
    /**
     * Get following list (people the user follows)
     * Returns Flow of users that the current user is following
     * 
     * @param userId Current user's ID
     * @return Flow of following list
     */
    fun getFollowing(userId: String): Flow<List<Friend>>
    
    /**
     * Get followers list (people who follow the user)
     * Returns Flow of users that follow the current user
     * 
     * @param userId Current user's ID
     * @return Flow of followers list
     */
    fun getFollowers(userId: String): Flow<List<Friend>>
} 