package com.example.liftrix.data.service

import com.example.liftrix.domain.model.admin.*
import com.example.liftrix.domain.service.AdminFirebaseService
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of AdminFirebaseService using Firebase Cloud Functions.
 * 
 * This service provides secure admin operations by calling Firebase Cloud Functions
 * that use the Firebase Admin SDK on the server side. All operations require admin
 * permissions and are logged for audit purposes.
 * 
 * ★ Insight ─────────────────────────────────────
 * - Implements secure admin operations through Firebase Cloud Functions with Admin SDK
 * - Provides comprehensive error handling and data mapping for all admin operations
 * - Validates admin permissions and provides detailed audit trails for all actions
 * ─────────────────────────────────────────────────
 */
@Singleton
class AdminFirebaseServiceImpl @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firebaseFunctions: FirebaseFunctions
) : AdminFirebaseService {
    
    companion object {
        private const val FUNCTION_BAN_USER = "banUser"
        private const val FUNCTION_UNBAN_USER = "unbanUser"
        private const val FUNCTION_GET_USER_BAN_INFO = "getUserBanInfo"
        private const val FUNCTION_SEARCH_USERS = "searchUsers"
        private const val FUNCTION_LIST_BANNED_USERS = "listBannedUsers"
        private const val FUNCTION_GET_ADMIN_LOGS = "getAdminLogs"
    }
    
    override suspend fun checkAdminPermissions(userId: String): Boolean {
        return try {
            val user = firebaseAuth.currentUser ?: return false
            val tokenResult = user.getIdToken(true).await()
            val claims = tokenResult.claims
            
            val isAdmin = claims["admin"] as? Boolean ?: false
            
            Timber.d("Admin permission check for user $userId: $isAdmin")
            isAdmin
        } catch (e: Exception) {
            Timber.e(e, "Error checking admin permissions for user: $userId")
            false
        }
    }
    
    override suspend fun banUser(
        userId: String,
        reason: String,
        severity: String,
        banDuration: String?
    ): BanUserResponse {
        return try {
            // Verify authentication and admin permissions before attempting ban
            val currentUser = firebaseAuth.currentUser
                ?: throw Exception("Authentication required. Please sign in to perform admin operations.")
            
            Timber.d("Verifying admin permissions for current user: ${currentUser.uid}")
            
            // Force refresh ID token to get latest custom claims
            val tokenResult = currentUser.getIdToken(true).await()
            val claims = tokenResult.claims
            val isAdmin = claims["admin"] as? Boolean ?: false
            
            if (!isAdmin) {
                throw Exception("Admin permissions required. Current user does not have admin privileges.")
            }
            
            Timber.i("Admin verification successful. Calling Firebase function to ban user: $userId")
            
            val data = hashMapOf(
                "userId" to userId,
                "reason" to reason,
                "severity" to severity,
                "banDuration" to banDuration
            )
            
            val result = firebaseFunctions
                .getHttpsCallable(FUNCTION_BAN_USER)
                .call(data)
                .await()
            
            val responseData = result.data as Map<String, Any>
            
            BanUserResponse(
                success = responseData["success"] as? Boolean ?: false,
                userId = responseData["userId"] as? String ?: userId,
                banId = responseData["banId"] as? String ?: "",
                bannedAt = parseDate(responseData["bannedAt"]) ?: Date(),
                message = responseData["message"] as? String ?: "User banned successfully"
            )
        } catch (e: Exception) {
            Timber.e(e, "Error banning user: $userId")
            when {
                e.message?.contains("UNAUTHENTICATED", ignoreCase = true) == true -> 
                    throw Exception("Authentication failed. Please sign in and ensure you have admin permissions.", e)
                e.message?.contains("admin", ignoreCase = true) == true -> 
                    throw Exception("Admin permissions required. Contact system administrator to grant admin access.", e)
                else -> throw Exception("Failed to ban user: ${e.message}", e)
            }
        }
    }
    
    override suspend fun unbanUser(userId: String, reason: String): UnbanUserResponse {
        return try {
            // Verify authentication and admin permissions
            verifyAdminAuthentication()
            
            val data = hashMapOf(
                "userId" to userId,
                "reason" to reason
            )
            
            Timber.i("Admin verification successful. Calling Firebase function to unban user: $userId")
            
            val result = firebaseFunctions
                .getHttpsCallable(FUNCTION_UNBAN_USER)
                .call(data)
                .await()
            
            val responseData = result.data as Map<String, Any>
            
            UnbanUserResponse(
                success = responseData["success"] as? Boolean ?: false,
                userId = responseData["userId"] as? String ?: userId,
                unbannedAt = parseDate(responseData["unbannedAt"]) ?: Date(),
                message = responseData["message"] as? String ?: "User unbanned successfully"
            )
        } catch (e: Exception) {
            Timber.e(e, "Error unbanning user: $userId")
            when {
                e.message?.contains("UNAUTHENTICATED", ignoreCase = true) == true -> 
                    throw Exception("Authentication failed. Please sign in and ensure you have admin permissions.", e)
                e.message?.contains("admin", ignoreCase = true) == true -> 
                    throw Exception("Admin permissions required. Contact system administrator to grant admin access.", e)
                else -> throw Exception("Failed to unban user: ${e.message}", e)
            }
        }
    }
    
    override suspend fun getUserBanInfo(userId: String): UserBanInfoResponse {
        return try {
            val data = hashMapOf("userId" to userId)
            
            val result = firebaseFunctions
                .getHttpsCallable(FUNCTION_GET_USER_BAN_INFO)
                .call(data)
                .await()
            
            val responseData = result.data as Map<String, Any>
            
            // Parse user info
            val userInfoData = responseData["userInfo"] as Map<String, Any>
            val userInfo = FirebaseUserInfo(
                uid = userInfoData["uid"] as String,
                email = userInfoData["email"] as? String,
                displayName = userInfoData["displayName"] as? String,
                disabled = userInfoData["disabled"] as? Boolean ?: false,
                createdAt = userInfoData["createdAt"] as? String ?: "",
                lastSignIn = userInfoData["lastSignIn"] as? String,
                customClaims = userInfoData["customClaims"] as? Map<String, Any> ?: emptyMap(),
                providerData = parseProviderData(userInfoData["providerData"])
            )
            
            // Parse user profile
            val userProfileData = responseData["userProfile"] as? Map<String, Any>
            val userProfile = userProfileData?.let { profile ->
                UserProfileInfo(
                    displayName = profile["displayName"] as? String,
                    username = profile["username"] as? String,
                    email = profile["email"] as? String,
                    accountStatus = profile["accountStatus"] as? String,
                    bannedAt = parseDate(profile["bannedAt"]),
                    bannedBy = profile["bannedBy"] as? String,
                    banReason = profile["banReason"] as? String,
                    banSeverity = profile["banSeverity"] as? String
                )
            }
            
            // Parse ban history
            val banHistoryData = responseData["banHistory"] as List<Map<String, Any>>
            val banHistory = banHistoryData.map { ban ->
                AdminBanInfo(
                    id = ban["id"] as String,
                    userId = ban["userId"] as String,
                    bannedBy = ban["bannedBy"] as String,
                    reason = ban["reason"] as String,
                    severity = BanSeverity.fromValue(ban["severity"] as? String ?: "moderate"),
                    banDuration = ban["banDuration"] as? String,
                    bannedAt = parseDate(ban["bannedAt"]) ?: Date(),
                    unbannedAt = parseDate(ban["unbannedAt"]),
                    unbannedBy = ban["unbannedBy"] as? String,
                    unbanReason = ban["unbanReason"] as? String,
                    status = ban["status"] as String,
                    userEmail = ban["userEmail"] as? String,
                    userDisplayName = ban["userDisplayName"] as? String,
                    metadata = parseBanMetadata(ban["metadata"])
                )
            }
            
            UserBanInfoResponse(
                userInfo = userInfo,
                userProfile = userProfile,
                banHistory = banHistory,
                currentlyBanned = responseData["currentlyBanned"] as? Boolean ?: false
            )
        } catch (e: Exception) {
            Timber.e(e, "Error getting user ban info: $userId")
            throw Exception("Failed to get user ban info: ${e.message}", e)
        }
    }
    
    override suspend fun searchUsers(query: String, limit: Int): SearchUsersResponse {
        return try {
            // Verify authentication and admin permissions
            verifyAdminAuthentication()
            
            val data = hashMapOf(
                "query" to query,
                "limit" to limit
            )
            
            val result = firebaseFunctions
                .getHttpsCallable(FUNCTION_SEARCH_USERS)
                .call(data)
                .await()
            
            val responseData = result.data as Map<String, Any>
            val usersData = responseData["users"] as List<Map<String, Any>>
            
            val users = usersData.map { user ->
                AdminUserInfo(
                    uid = user["uid"] as String,
                    email = user["email"] as? String,
                    displayName = user["displayName"] as? String,
                    accountStatus = user["accountStatus"] as? String ?: "active",
                    createdAt = user["createdAt"] as? String,
                    lastActive = user["lastActive"] as? String,
                    currentlyBanned = user["currentlyBanned"] as? Boolean ?: false
                )
            }
            
            SearchUsersResponse(
                users = users,
                searchQuery = responseData["searchQuery"] as? String ?: query
            )
        } catch (e: Exception) {
            Timber.e(e, "Error searching users with query: $query")
            throw Exception("Failed to search users: ${e.message}", e)
        }
    }
    
    override suspend fun listBannedUsers(
        limit: Int,
        offset: Int,
        severity: String?
    ): ListBannedUsersResponse {
        return try {
            val data = hashMapOf(
                "limit" to limit,
                "offset" to offset,
                "severity" to severity
            )
            
            val result = firebaseFunctions
                .getHttpsCallable(FUNCTION_LIST_BANNED_USERS)
                .call(data)
                .await()
            
            val responseData = result.data as Map<String, Any>
            val bannedUsersData = responseData["bannedUsers"] as List<Map<String, Any>>
            
            val bannedUsers = bannedUsersData.map { ban ->
                AdminBanInfo(
                    id = ban["id"] as String,
                    userId = ban["userId"] as String,
                    bannedBy = ban["bannedBy"] as String,
                    reason = ban["reason"] as String,
                    severity = BanSeverity.fromValue(ban["severity"] as? String ?: "moderate"),
                    banDuration = ban["banDuration"] as? String,
                    bannedAt = parseDate(ban["bannedAt"]) ?: Date(),
                    unbannedAt = parseDate(ban["unbannedAt"]),
                    unbannedBy = ban["unbannedBy"] as? String,
                    unbanReason = ban["unbanReason"] as? String,
                    status = ban["status"] as String,
                    userEmail = ban["userEmail"] as? String,
                    userDisplayName = ban["userDisplayName"] as? String,
                    metadata = parseBanMetadata(ban["metadata"])
                )
            }
            
            ListBannedUsersResponse(
                bannedUsers = bannedUsers,
                total = responseData["total"] as? Int ?: bannedUsers.size,
                hasMore = responseData["hasMore"] as? Boolean ?: false,
                nextOffset = responseData["nextOffset"] as? Int ?: (offset + bannedUsers.size)
            )
        } catch (e: Exception) {
            Timber.e(e, "Error listing banned users")
            throw Exception("Failed to list banned users: ${e.message}", e)
        }
    }
    
    override suspend fun getAdminLogs(limit: Int, actionType: String?): List<AdminBanInfo> {
        return try {
            val data = hashMapOf(
                "limit" to limit,
                "actionType" to actionType
            )
            
            val result = firebaseFunctions
                .getHttpsCallable(FUNCTION_GET_ADMIN_LOGS)
                .call(data)
                .await()
            
            val responseData = result.data as List<Map<String, Any>>
            
            responseData.map { log ->
                AdminBanInfo(
                    id = log["id"] as String,
                    userId = log["userId"] as String,
                    bannedBy = log["bannedBy"] as String,
                    reason = log["reason"] as String,
                    severity = BanSeverity.fromValue(log["severity"] as? String ?: "moderate"),
                    banDuration = log["banDuration"] as? String,
                    bannedAt = parseDate(log["bannedAt"]) ?: Date(),
                    unbannedAt = parseDate(log["unbannedAt"]),
                    unbannedBy = log["unbannedBy"] as? String,
                    unbanReason = log["unbanReason"] as? String,
                    status = log["status"] as String,
                    userEmail = log["userEmail"] as? String,
                    userDisplayName = log["userDisplayName"] as? String,
                    metadata = parseBanMetadata(log["metadata"])
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Error getting admin logs")
            throw Exception("Failed to get admin logs: ${e.message}", e)
        }
    }
    
    // Helper functions
    
    /**
     * Verifies that the current user is authenticated and has admin permissions.
     * Forces a token refresh to ensure latest custom claims are checked.
     * 
     * @throws Exception if user is not authenticated or doesn't have admin permissions
     */
    private suspend fun verifyAdminAuthentication() {
        val currentUser = firebaseAuth.currentUser
            ?: throw Exception("Authentication required. Please sign in to perform admin operations.")
        
        Timber.d("Verifying admin permissions for current user: ${currentUser.uid}")
        
        // Force refresh ID token to get latest custom claims
        val tokenResult = currentUser.getIdToken(true).await()
        val claims = tokenResult.claims
        val isAdmin = claims["admin"] as? Boolean ?: false
        
        if (!isAdmin) {
            throw Exception("Admin permissions required. Current user does not have admin privileges.")
        }
        
        Timber.d("Admin verification successful for user: ${currentUser.uid}")
    }
    
    private fun parseDate(dateValue: Any?): Date? {
        return when (dateValue) {
            is String -> {
                try {
                    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).parse(dateValue)
                } catch (e: Exception) {
                    null
                }
            }
            is Map<*, *> -> {
                // Firebase Timestamp format
                val seconds = dateValue["_seconds"] as? Long ?: return null
                Date(seconds * 1000)
            }
            else -> null
        }
    }
    
    private fun parseProviderData(providerDataValue: Any?): List<ProviderInfo> {
        return try {
            val providerList = providerDataValue as? List<Map<String, Any>> ?: return emptyList()
            providerList.map { provider ->
                ProviderInfo(
                    providerId = provider["providerId"] as String,
                    email = provider["email"] as? String,
                    displayName = provider["displayName"] as? String
                )
            }
        } catch (e: Exception) {
            Timber.w(e, "Error parsing provider data")
            emptyList()
        }
    }
    
    private fun parseBanMetadata(metadataValue: Any?): AdminBanMetadata? {
        return try {
            val metadata = metadataValue as? Map<String, Any> ?: return null
            AdminBanMetadata(
                userCreatedAt = metadata["userCreatedAt"] as? String,
                lastSignIn = metadata["lastSignIn"] as? String,
                providerData = (metadata["providerData"] as? List<String>) ?: emptyList()
            )
        } catch (e: Exception) {
            Timber.w(e, "Error parsing ban metadata")
            null
        }
    }
}