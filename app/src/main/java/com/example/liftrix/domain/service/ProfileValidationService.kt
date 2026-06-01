package com.example.liftrix.domain.service

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.error.LiftrixError

/**
 * Domain service for profile validation logic.
 *
 * Centralizes profile validation rules previously duplicated across:
 * - UpdateSocialProfileUseCase
 * - CreateSocialProfileUseCase
 * - UpdateSocialPrivacySettingsUseCase
 * - UploadProfileImageUseCase
 *
 * Provides comprehensive validation for user profiles with consistent error handling.
 */
interface ProfileValidationService {

    /**
     * Validates username against business rules.
     *
     * Rules:
     * - Cannot be blank or empty
     * - Must be between MIN_USERNAME_LENGTH and MAX_USERNAME_LENGTH characters
     * - Can only contain alphanumeric characters, underscores, and periods
     * - Cannot start or end with underscore or period
     * - Cannot have consecutive underscores or periods
     * - Must not be a reserved username (admin, system, etc.)
     *
     * @param username Username to validate
     * @return LiftrixResult with validated username or ValidationError
     */
    fun validateUsername(username: String): LiftrixResult<String>

    /**
     * Validates display name (full name) against business rules.
     *
     * Rules:
     * - Cannot be blank or empty
     * - Must be between MIN_DISPLAY_NAME_LENGTH and MAX_DISPLAY_NAME_LENGTH characters
     * - Can contain letters, spaces, hyphens, and apostrophes
     * - Must not contain numbers or special characters
     *
     * @param displayName Display name to validate
     * @return LiftrixResult with validated display name or ValidationError
     */
    fun validateDisplayName(displayName: String): LiftrixResult<String>

    /**
     * Validates bio against business rules.
     *
     * Rules:
     * - Optional (null/blank is valid)
     * - Cannot exceed MAX_BIO_LENGTH characters
     * - Must not contain special characters that break database constraints
     * - Must not contain prohibited content (profanity, spam, etc.)
     *
     * @param bio User bio to validate
     * @return LiftrixResult with validated bio or ValidationError
     */
    fun validateBio(bio: String?): LiftrixResult<String?>

    /**
     * Validates profile image URL against business rules.
     *
     * Rules:
     * - Optional (null is valid for no custom image)
     * - Must be valid URL format if provided
     * - Must use HTTPS protocol
     * - Must point to Firebase Storage or approved CDN
     * - Must not exceed MAX_URL_LENGTH characters
     *
     * @param imageUrl Profile image URL to validate
     * @return LiftrixResult with validated URL or ValidationError
     */
    fun validateProfileImageUrl(imageUrl: String?): LiftrixResult<String?>

    /**
     * Validates location string against business rules.
     *
     * Rules:
     * - Optional (null/blank is valid)
     * - Cannot exceed MAX_LOCATION_LENGTH characters
     * - Must be alphanumeric with spaces, commas, and hyphens
     * - Should follow "City, Country" format (not enforced, just recommended)
     *
     * @param location User location to validate
     * @return LiftrixResult with validated location or ValidationError
     */
    fun validateLocation(location: String?): LiftrixResult<String?>

    /**
     * Validates website URL against business rules.
     *
     * Rules:
     * - Optional (null/blank is valid)
     * - Must be valid URL format if provided
     * - Must use HTTP or HTTPS protocol
     * - Cannot exceed MAX_URL_LENGTH characters
     * - Must not link to prohibited domains
     *
     * @param website Website URL to validate
     * @return LiftrixResult with validated URL or ValidationError
     */
    fun validateWebsite(website: String?): LiftrixResult<String?>

    /**
     * Validates that username is available (not taken by another user).
     *
     * This is a separate validation that requires repository access.
     * Should be called after basic username validation passes.
     *
     * @param username Username to check availability
     * @param currentUserId Current user ID (to allow keeping same username)
     * @param checkAvailability Lambda to check availability in repository
     * @return LiftrixResult with Unit if available or ValidationError if taken
     */
    suspend fun validateUsernameAvailability(
        username: String,
        currentUserId: String,
        checkAvailability: suspend (String) -> Boolean
    ): LiftrixResult<Unit>

    /**
     * Validates profile image file.
     *
     * Rules:
     * - Cannot be null (required for upload)
     * - Must be valid image format (JPEG, PNG, WebP)
     * - Cannot exceed MAX_IMAGE_SIZE_MB
     * - Dimensions must be at least MIN_IMAGE_DIMENSION x MIN_IMAGE_DIMENSION
     * - Dimensions cannot exceed MAX_IMAGE_DIMENSION x MAX_IMAGE_DIMENSION
     *
     * @param imageData Image file data
     * @param mimeType MIME type of the image
     * @param sizeBytes Size in bytes
     * @return LiftrixResult with Unit or ValidationError
     */
    fun validateProfileImageFile(
        imageData: ByteArray,
        mimeType: String,
        sizeBytes: Long
    ): LiftrixResult<Unit>

    companion object {
        const val MIN_USERNAME_LENGTH = 3
        const val MAX_USERNAME_LENGTH = 30
        const val MIN_DISPLAY_NAME_LENGTH = 1
        const val MAX_DISPLAY_NAME_LENGTH = 50
        const val MAX_BIO_LENGTH = 300
        const val MAX_LOCATION_LENGTH = 100
        const val MAX_URL_LENGTH = 500
        const val MAX_IMAGE_SIZE_MB = 5
        const val MAX_IMAGE_SIZE_BYTES = MAX_IMAGE_SIZE_MB * 1024 * 1024L
        const val MIN_IMAGE_DIMENSION = 100
        const val MAX_IMAGE_DIMENSION = 4096

        val RESERVED_USERNAMES = setOf(
            "admin", "administrator", "system", "support", "help",
            "liftrix", "official", "verified", "team", "staff",
            "moderator", "mod", "root", "null", "undefined"
        )

        val ALLOWED_IMAGE_FORMATS = setOf(
            "image/jpeg", "image/jpg", "image/png", "image/webp"
        )
    }
}
