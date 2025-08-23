package com.example.liftrix.domain.model.admin

import com.example.liftrix.domain.model.common.LiftrixResult
import java.util.*

/**
 * Domain models for admin functionality including user management and banning.
 * 
 * ★ Insight ─────────────────────────────────────
 * - Provides type-safe admin operations with comprehensive audit trail support
 * - Implements ban severity levels and duration controls for flexible moderation
 * - Integrates with Firebase Admin SDK for secure server-side user management
 * ─────────────────────────────────────────────────
 */

/**
 * Information about a user from admin perspective
 */
data class AdminUserInfo(
    val uid: String,
    val email: String?,
    val displayName: String?,
    val accountStatus: String = "active",
    val createdAt: String?,
    val lastActive: String?,
    val currentlyBanned: Boolean = false
)

/**
 * Detailed ban information for a user
 */
data class AdminBanInfo(
    val id: String,
    val userId: String,
    val bannedBy: String,
    val reason: String,
    val severity: BanSeverity,
    val banDuration: String?, // null means permanent
    val bannedAt: Date,
    val unbannedAt: Date? = null,
    val unbannedBy: String? = null,
    val unbanReason: String? = null,
    val status: String, // "active" or "inactive"
    val userEmail: String?,
    val userDisplayName: String?,
    val metadata: AdminBanMetadata?
)

/**
 * Metadata associated with a ban
 */
data class AdminBanMetadata(
    val userCreatedAt: String?,
    val lastSignIn: String?,
    val providerData: List<String>
)

/**
 * Ban severity levels
 */
enum class BanSeverity(val displayName: String, val value: String) {
    MINOR("Minor Violation", "minor"),
    MODERATE("Moderate Violation", "moderate"), 
    SEVERE("Severe Violation", "severe"),
    CRITICAL("Critical Violation", "critical");
    
    companion object {
        fun fromValue(value: String): BanSeverity {
            return values().find { it.value == value } ?: MODERATE
        }
    }
}

/**
 * Comprehensive user ban information response
 */
data class UserBanInfoResponse(
    val userInfo: FirebaseUserInfo,
    val userProfile: UserProfileInfo?,
    val banHistory: List<AdminBanInfo>,
    val currentlyBanned: Boolean
)

/**
 * Firebase user information
 */
data class FirebaseUserInfo(
    val uid: String,
    val email: String?,
    val displayName: String?,
    val disabled: Boolean,
    val createdAt: String,
    val lastSignIn: String?,
    val customClaims: Map<String, Any>,
    val providerData: List<ProviderInfo>
)

/**
 * Provider information for authentication
 */
data class ProviderInfo(
    val providerId: String,
    val email: String?,
    val displayName: String?
)

/**
 * User profile information
 */
data class UserProfileInfo(
    val displayName: String?,
    val username: String?,
    val email: String?,
    val accountStatus: String?,
    val bannedAt: Date?,
    val bannedBy: String?,
    val banReason: String?,
    val banSeverity: String?
)

// Request/Response models for use cases

/**
 * Request to ban a user
 */
data class BanUserRequest(
    val userId: String,
    val reason: String,
    val severity: BanSeverity = BanSeverity.MODERATE,
    val banDuration: String? = null // null = permanent, e.g., "7d", "30d", "1y"
)

/**
 * Response from banning a user
 */
data class BanUserResponse(
    val success: Boolean,
    val userId: String,
    val banId: String,
    val bannedAt: Date,
    val message: String
)

/**
 * Request to unban a user
 */
data class UnbanUserRequest(
    val userId: String,
    val reason: String = "Appeal approved"
)

/**
 * Response from unbanning a user
 */
data class UnbanUserResponse(
    val success: Boolean,
    val userId: String,
    val unbannedAt: Date,
    val message: String
)

/**
 * Request to search users
 */
data class SearchUsersRequest(
    val query: String,
    val limit: Int = 20
)

/**
 * Response from searching users
 */
data class SearchUsersResponse(
    val users: List<AdminUserInfo>,
    val searchQuery: String
)

/**
 * Request to list banned users
 */
data class ListBannedUsersRequest(
    val limit: Int = 50,
    val offset: Int = 0,
    val severity: BanSeverity? = null
)

/**
 * Response from listing banned users
 */
data class ListBannedUsersResponse(
    val bannedUsers: List<AdminBanInfo>,
    val total: Int,
    val hasMore: Boolean,
    val nextOffset: Int
)

/**
 * Request to get admin logs
 */
data class GetAdminLogsRequest(
    val limit: Int = 100,
    val actionType: String? = null // e.g., "BAN_USER", "UNBAN_USER"
)

/**
 * Admin action log entry
 */
data class AdminActionLog(
    val id: String,
    val actionType: String,
    val performedBy: String,
    val targetUserId: String?,
    val details: Map<String, Any>,
    val timestamp: Date,
    val outcome: String // "success" or "failed"
)