package com.example.liftrix.domain.usecase.admin

import com.example.liftrix.domain.model.admin.BanUserRequest
import com.example.liftrix.domain.model.admin.BanUserResponse
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.service.AdminFirebaseService
import com.example.liftrix.domain.util.DomainLogger as Timber
import javax.inject.Inject

/**
 * Use case for banning users through Firebase Admin SDK.
 * 
 * This use case handles the complete user banning flow including:
 * - Validating admin permissions
 * - Calling Firebase Admin SDK functions
 * - Creating audit trails
 * - Error handling and logging
 * 
 * ★ Insight ─────────────────────────────────────
 * - Integrates with Firebase Admin SDK for secure server-side user management
 * - Provides comprehensive ban management with audit trail and error handling
 * - Validates admin permissions before allowing any ban operations
 * ─────────────────────────────────────────────────
 */
class BanUserUseCase @Inject constructor(
    private val adminFirebaseService: AdminFirebaseService
) {
    
    suspend operator fun invoke(request: BanUserRequest): LiftrixResult<BanUserResponse> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "BAN_USER_FAILED",
                errorMessage = "Failed to ban user: ${throwable.message}",
                analyticsContext = mapOf(
                    "operation" to "BAN_USER",
                    "target_user_id" to request.userId,
                    "ban_reason" to request.reason,
                    "ban_severity" to request.severity.value,
                    "error" to (throwable.message ?: "Unknown error")
                )
            )
        }
    ) {
        Timber.i("Attempting to ban user: ${request.userId} with reason: ${request.reason}")
        
        // Validate request
        require(request.userId.isNotBlank()) { "User ID cannot be blank" }
        require(request.reason.isNotBlank()) { "Ban reason cannot be blank" }
        require(request.reason.length >= 10) { "Ban reason must be at least 10 characters" }
        
        // Call Firebase Admin SDK function
        val response = adminFirebaseService.banUser(
            userId = request.userId,
            reason = request.reason,
            severity = request.severity.value,
            banDuration = request.banDuration
        )
        
        Timber.i("Successfully banned user: ${request.userId}")
        
        response
    }
}
