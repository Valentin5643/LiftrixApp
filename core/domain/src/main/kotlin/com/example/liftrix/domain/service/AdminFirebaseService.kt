package com.example.liftrix.domain.service

import com.example.liftrix.domain.model.admin.*

/**
 * Service interface for admin operations using Firebase Admin SDK.
 * 
 * This service handles all server-side admin operations including user banning,
 * unbanning, and admin data retrieval. All operations are performed securely
 * on the server-side using Firebase Cloud Functions with Admin SDK.
 * 
 * ★ Insight ─────────────────────────────────────
 * - Provides secure admin operations through Firebase Cloud Functions
 * - Implements comprehensive user management with audit trails
 * - Uses Firebase Admin SDK for server-side user authentication management
 * ─────────────────────────────────────────────────
 */
interface AdminFirebaseService {
    
    /**
     * Check if the current user has admin permissions
     */
    suspend fun checkAdminPermissions(userId: String): Boolean
    
    /**
     * Ban a user with specified reason, severity, and duration
     */
    suspend fun banUser(
        userId: String,
        reason: String,
        severity: String,
        banDuration: String?
    ): BanUserResponse
    
    /**
     * Unban a user with specified reason
     */
    suspend fun unbanUser(
        userId: String,
        reason: String
    ): UnbanUserResponse
    
    /**
     * Get comprehensive ban information for a user
     */
    suspend fun getUserBanInfo(userId: String): UserBanInfoResponse
    
    /**
     * Search for users by email or display name
     */
    suspend fun searchUsers(query: String, limit: Int): SearchUsersResponse
    
    /**
     * List all currently banned users
     */
    suspend fun listBannedUsers(
        limit: Int,
        offset: Int,
        severity: String?
    ): ListBannedUsersResponse
    
    /**
     * Get admin action logs
     */
    suspend fun getAdminLogs(
        limit: Int,
        actionType: String?
    ): List<AdminBanInfo>
}