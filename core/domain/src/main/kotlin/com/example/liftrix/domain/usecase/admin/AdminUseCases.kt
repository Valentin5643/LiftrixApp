package com.example.liftrix.domain.usecase.admin

import com.example.liftrix.domain.model.admin.*
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.service.AdminFirebaseService
import com.example.liftrix.domain.util.DomainLogger as Timber
import javax.inject.Inject

/**
 * Collection of admin-related use cases for user management and moderation.
 * 
 * ★ Insight ─────────────────────────────────────
 * - Centralizes all admin operations with proper validation and error handling
 * - Provides secure Firebase Admin SDK integration for user management
 * - Implements comprehensive audit trails and permission checks
 * ─────────────────────────────────────────────────
 */

/**
 * Use case to check if a user has admin permissions
 */
class CheckAdminPermissionsUseCase @Inject constructor(
    private val adminFirebaseService: AdminFirebaseService
) {
    suspend operator fun invoke(userId: String): LiftrixResult<Boolean> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "CHECK_ADMIN_PERMISSIONS_FAILED",
                errorMessage = "Failed to check admin permissions",
                analyticsContext = mapOf(
                    "operation" to "CHECK_ADMIN_PERMISSIONS",
                    "user_id" to userId,
                    "error" to (throwable.message ?: "Unknown error")
                )
            )
        }
    ) {
        adminFirebaseService.checkAdminPermissions(userId)
    }
}

/**
 * Use case to search for users
 */
class SearchUsersUseCase @Inject constructor(
    private val adminFirebaseService: AdminFirebaseService
) {
    suspend operator fun invoke(request: SearchUsersRequest): LiftrixResult<SearchUsersResponse> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "SEARCH_USERS_FAILED",
                errorMessage = "Failed to search users",
                analyticsContext = mapOf(
                    "operation" to "SEARCH_USERS",
                    "search_query" to request.query,
                    "error" to (throwable.message ?: "Unknown error")
                )
            )
        }
    ) {
        require(request.query.length >= 3) { "Search query must be at least 3 characters long" }
        
        adminFirebaseService.searchUsers(request.query, request.limit)
    }
}

/**
 * Use case to unban a user
 */
class UnbanUserUseCase @Inject constructor(
    private val adminFirebaseService: AdminFirebaseService
) {
    suspend operator fun invoke(request: UnbanUserRequest): LiftrixResult<UnbanUserResponse> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "UNBAN_USER_FAILED",
                errorMessage = "Failed to unban user: ${throwable.message}",
                analyticsContext = mapOf(
                    "operation" to "UNBAN_USER",
                    "target_user_id" to request.userId,
                    "unban_reason" to request.reason,
                    "error" to (throwable.message ?: "Unknown error")
                )
            )
        }
    ) {
        Timber.i("Attempting to unban user: ${request.userId} with reason: ${request.reason}")
        
        require(request.userId.isNotBlank()) { "User ID cannot be blank" }
        
        val response = adminFirebaseService.unbanUser(
            userId = request.userId,
            reason = request.reason
        )
        
        Timber.i("Successfully unbanned user: ${request.userId}")
        
        response
    }
}

/**
 * Use case to get detailed ban information for a user
 */
class GetUserBanInfoUseCase @Inject constructor(
    private val adminFirebaseService: AdminFirebaseService
) {
    suspend operator fun invoke(userId: String): LiftrixResult<UserBanInfoResponse> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "GET_USER_BAN_INFO_FAILED",
                errorMessage = "Failed to get user ban info",
                analyticsContext = mapOf(
                    "operation" to "GET_USER_BAN_INFO",
                    "target_user_id" to userId,
                    "error" to (throwable.message ?: "Unknown error")
                )
            )
        }
    ) {
        require(userId.isNotBlank()) { "User ID cannot be blank" }
        
        adminFirebaseService.getUserBanInfo(userId)
    }
}

/**
 * Use case to list all banned users
 */
class ListBannedUsersUseCase @Inject constructor(
    private val adminFirebaseService: AdminFirebaseService
) {
    suspend operator fun invoke(request: ListBannedUsersRequest): LiftrixResult<ListBannedUsersResponse> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "LIST_BANNED_USERS_FAILED",
                errorMessage = "Failed to list banned users",
                analyticsContext = mapOf(
                    "operation" to "LIST_BANNED_USERS",
                    "limit" to request.limit.toString(),
                    "offset" to request.offset.toString(),
                    "error" to (throwable.message ?: "Unknown error")
                )
            )
        }
    ) {
        require(request.limit > 0) { "Limit must be greater than 0" }
        require(request.limit <= 200) { "Limit cannot exceed 200" }
        require(request.offset >= 0) { "Offset cannot be negative" }
        
        adminFirebaseService.listBannedUsers(
            limit = request.limit,
            offset = request.offset,
            severity = request.severity?.value
        )
    }
}

/**
 * Use case to get admin action logs
 */
class GetAdminLogsUseCase @Inject constructor(
    private val adminFirebaseService: AdminFirebaseService
) {
    suspend operator fun invoke(request: GetAdminLogsRequest): LiftrixResult<List<AdminBanInfo>> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "GET_ADMIN_LOGS_FAILED",
                errorMessage = "Failed to get admin logs",
                analyticsContext = mapOf(
                    "operation" to "GET_ADMIN_LOGS",
                    "limit" to request.limit.toString(),
                    "error" to (throwable.message ?: "Unknown error")
                )
            )
        }
    ) {
        require(request.limit > 0) { "Limit must be greater than 0" }
        require(request.limit <= 500) { "Limit cannot exceed 500" }
        
        adminFirebaseService.getAdminLogs(
            limit = request.limit,
            actionType = request.actionType
        )
    }
}
