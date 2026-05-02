package com.example.liftrix.domain.service

import com.example.liftrix.domain.model.common.LiftrixResult

/**
 * NotificationService - Interface for handling push notifications and in-app notifications
 * 
 * Handles:
 * - Follow request notifications
 * - Follow acceptance notifications
 * - Gym buddy notifications  
 * - Achievement notifications
 * 
 * Implementation: NotificationServiceImpl using Firebase Cloud Messaging
 */
interface NotificationService {
    
    /**
     * Send follow request notification to target user
     */
    suspend fun sendFollowRequestNotification(
        targetUserId: String,
        requesterUserId: String,
        requesterName: String
    ): LiftrixResult<Unit>
    
    /**
     * Send follow acceptance notification
     */
    suspend fun sendFollowAcceptedNotification(
        targetUserId: String,
        accepterUserId: String,
        accepterName: String
    ): LiftrixResult<Unit>
    
    /**
     * Send follow notification (for public profiles)
     */
    suspend fun sendFollowNotification(
        targetUserId: String,
        followerUserId: String,
        followerName: String
    ): LiftrixResult<Unit>
    
    /**
     * Send gym buddy invitation notification
     */
    suspend fun sendGymBuddyInviteNotification(
        targetUserId: String,
        inviterUserId: String,
        inviterName: String
    ): LiftrixResult<Unit>
    
    /**
     * Send PR (Personal Record) notification to gym buddies
     */
    suspend fun sendPRNotificationToBuddies(
        achieverUserId: String,
        achieverName: String,
        achievement: String,
        buddyUserIds: List<String>
    ): LiftrixResult<Unit>
    
    /**
     * Send general achievement notification
     */
    suspend fun sendAchievementNotification(
        userId: String,
        achievementTitle: String,
        achievementDescription: String
    ): LiftrixResult<Unit>
}