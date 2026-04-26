package com.example.liftrix.domain.usecase.social

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.flatMapLiftrix
import com.example.liftrix.domain.model.common.liftrixFailure
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.UserSearchRepository
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.usecase.common.ErrorHandler
import timber.log.Timber
import javax.inject.Inject

/**
 * Use case for generating QR codes for profile sharing
 * 
 * Responsibilities:
 * - Validates QR code generation requests
 * - Creates secure QR code mappings for profile sharing
 * - Manages QR code expiration and security
 * - Tracks QR code usage for analytics
 * 
 * Business Rules:
 * - QR codes are generated for authenticated users only
 * - Each QR code has a unique identifier for security
 * - QR codes respect user privacy settings
 * - QR codes can be disabled by the user at any time
 * - QR code data is an app-native payload and does not depend on external websites
 */
class QRCodeGenerationUseCase @Inject constructor(
    private val userSearchRepository: UserSearchRepository,
    private val authRepository: AuthRepository,
    private val errorHandler: ErrorHandler
) {
    
    /**
     * Generates a QR code for profile sharing
     * 
     * @param request The QR code generation request
     * @return LiftrixResult containing QR code data or error information
     */
    suspend operator fun invoke(request: QRCodeGenerationRequest): LiftrixResult<QRCodeGenerationResult> {
        return try {
            // Get current user ID for authentication
            val currentUserId = getCurrentUserId()
                ?: return liftrixFailure(LiftrixError.AuthenticationError("User not authenticated"))
            
            // Validate request
            val validationResult = validateRequest(request, currentUserId)
            if (validationResult.isFailure) {
                return validationResult as LiftrixResult<QRCodeGenerationResult>
            }
            
            // Determine target user ID (default to current user)
            val targetUserId = request.targetUserId ?: currentUserId
            
            // Validate permission to generate QR code for target user
            if (targetUserId != currentUserId && !canGenerateForUser(targetUserId, currentUserId)) {
                return liftrixFailure(
                    LiftrixError.PermissionError("Cannot generate QR code for other users")
                )
            }
            
            // Generate QR code data through repository
            val qrCodeResult = userSearchRepository.generateProfileQRCode(targetUserId)
            if (qrCodeResult.isFailure) {
                return qrCodeResult as LiftrixResult<QRCodeGenerationResult>
            }
            
            val qrCodeData = qrCodeResult.getOrThrow()
            
            val result = QRCodeGenerationResult(
                qrCodeData = qrCodeData,
                profileUserId = targetUserId,
                expiresAt = if (request.expirationHours > 0) {
                    System.currentTimeMillis() + (request.expirationHours * 60 * 60 * 1000)
                } else null,
                shareableUrl = qrCodeData,
                isTemporary = request.expirationHours > 0
            )
            
            Timber.d("QR code generated for user: $targetUserId")
            LiftrixResult.success(result)
        } catch (e: Exception) {
            Timber.e(e, "Unexpected error during QR code generation")
            val error = LiftrixError.UnknownError("QR code generation failed: ${e.message}")
            errorHandler.handleError(error, mapOf(
                "context" to "QRCodeGenerationUseCase",
                "targetUserId" to (request.targetUserId ?: "current_user")
            ))
            LiftrixResult.failure(error)
        }
    }
    
    /**
     * Resolves QR code data to user profile information
     * 
     * @param request The QR code resolution request
     * @return LiftrixResult containing resolved user ID or error
     */
    suspend fun resolveQRCode(request: QRCodeResolutionRequest): LiftrixResult<QRCodeResolutionResult> {
        return try {
            // Get current user ID for authentication
            val currentUserId = getCurrentUserId()
                ?: return liftrixFailure(LiftrixError.AuthenticationError("User not authenticated"))
            
            // Validate request
            if (request.qrCodeData.isBlank()) {
                return liftrixFailure(
                    LiftrixError.ValidationError(
                        field = "qrCodeData",
                        violations = listOf("QR code data cannot be empty"),
                        errorMessage = "QR code data validation failed"
                    )
                )
            }
            
            // Resolve QR code through repository
            val resolutionResult = userSearchRepository.resolveQRCodeProfile(request.qrCodeData)
            if (resolutionResult.isFailure) {
                return resolutionResult as LiftrixResult<QRCodeResolutionResult>
            }
            
            val resolvedUserId = resolutionResult.getOrThrow()
            
            val result = QRCodeResolutionResult(
                profileUserId = resolvedUserId,
                qrCodeData = request.qrCodeData,
                canViewProfile = resolvedUserId != currentUserId || canViewOwnProfile()
            )
            
            Timber.d("QR code resolved to user: $resolvedUserId")
            LiftrixResult.success(result)
        } catch (e: Exception) {
            Timber.e(e, "Unexpected error during QR code resolution")
            val error = LiftrixError.UnknownError("QR code resolution failed: ${e.message}")
            errorHandler.handleError(error, mapOf(
                "context" to "QRCodeGenerationUseCase.resolveQRCode"
            ))
            LiftrixResult.failure(error)
        }
    }
    
    /**
     * Gets current authenticated user ID
     */
    private suspend fun getCurrentUserId(): String? {
        return try {
            authRepository.getCurrentUserId()
        } catch (e: Exception) {
            Timber.e(e, "Failed to get current user ID")
            null
        }
    }
    
    /**
     * Validates the QR code generation request
     */
    private fun validateRequest(
        request: QRCodeGenerationRequest,
        currentUserId: String
    ): LiftrixResult<QRCodeGenerationRequest> {
        val violations = mutableListOf<String>()
        
        // Validate expiration hours
        if (request.expirationHours < 0) {
            violations.add("Expiration hours cannot be negative")
        } else if (request.expirationHours > MAX_EXPIRATION_HOURS) {
            violations.add("Expiration hours cannot exceed $MAX_EXPIRATION_HOURS")
        }
        
        // Validate target user ID format if provided
        if (request.targetUserId != null && request.targetUserId.length < MIN_USER_ID_LENGTH) {
            violations.add("Target user ID format is invalid")
        }
        
        return if (violations.isEmpty()) {
            LiftrixResult.success(request)
        } else {
            liftrixFailure(
                LiftrixError.ValidationError(
                    field = "QRCodeGenerationRequest",
                    violations = violations,
                    errorMessage = "QR code generation request validation failed"
                )
            )
        }
    }
    
    /**
     * Checks if current user can generate QR code for target user
     */
    private suspend fun canGenerateForUser(targetUserId: String, currentUserId: String): Boolean {
        // For now, only allow generating QR codes for own profile
        // This can be extended later for admin features or shared profiles
        return targetUserId == currentUserId
    }
    
    /**
     * Checks if user can view their own profile via QR code
     */
    private fun canViewOwnProfile(): Boolean {
        // Users should always be able to view their own profile
        return true
    }
    
    companion object {
        private const val MIN_USER_ID_LENGTH = 5
        private const val MAX_EXPIRATION_HOURS = 168 // 7 days maximum
    }
}

/**
 * Request data class for QR code generation
 * 
 * @property targetUserId ID of user for whom to generate QR code (defaults to current user)
 * @property expirationHours Hours until QR code expires (0 for no expiration)
 */
data class QRCodeGenerationRequest(
    val targetUserId: String? = null,
    val expirationHours: Int = 0
)

/**
 * Result data class for QR code generation
 * 
 * @property qrCodeData The QR code data string
 * @property profileUserId ID of the user the QR code points to
 * @property expiresAt Timestamp when QR code expires (null if no expiration)
 * @property shareableUrl Shareable URL version of the QR code
 * @property isTemporary Whether this QR code has an expiration
 */
data class QRCodeGenerationResult(
    val qrCodeData: String,
    val profileUserId: String,
    val expiresAt: Long?,
    val shareableUrl: String,
    val isTemporary: Boolean
)

/**
 * Request data class for QR code resolution
 * 
 * @property qrCodeData The QR code data to resolve
 */
data class QRCodeResolutionRequest(
    val qrCodeData: String
)

/**
 * Result data class for QR code resolution
 * 
 * @property profileUserId ID of the user the QR code resolves to
 * @property qrCodeData The original QR code data
 * @property canViewProfile Whether the current user can view this profile
 */
data class QRCodeResolutionResult(
    val profileUserId: String,
    val qrCodeData: String,
    val canViewProfile: Boolean
)
