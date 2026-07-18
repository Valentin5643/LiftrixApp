package com.example.liftrix.domain.service

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixFailure
import com.example.liftrix.domain.model.error.LiftrixError
import java.net.URL
import javax.inject.Inject

/**
 * Implementation of ProfileValidationService.
 *
 * Provides comprehensive profile validation with detailed error messages
 * and analytics context for tracking validation failures.
 */
class ProfileValidationServiceImpl @Inject constructor() : ProfileValidationService {

    override fun validateUsername(username: String): LiftrixResult<String> {
        val violations = mutableListOf<String>()

        // Check blank/empty
        if (username.isBlank()) {
            violations.add("Username is required")
        }

        // Check length
        if (username.length < ProfileValidationService.MIN_USERNAME_LENGTH) {
            violations.add("Username must be at least ${ProfileValidationService.MIN_USERNAME_LENGTH} characters")
        }
        if (username.length > ProfileValidationService.MAX_USERNAME_LENGTH) {
            violations.add("Username cannot exceed ${ProfileValidationService.MAX_USERNAME_LENGTH} characters")
        }

        // Check allowed characters (alphanumeric, underscore, period)
        val validUsernameRegex = Regex("^[a-zA-Z0-9_.]+$")
        if (!validUsernameRegex.matches(username)) {
            violations.add("Username can only contain letters, numbers, underscores, and periods")
        }

        // Check start/end characters
        if (username.startsWith("_") || username.startsWith(".")) {
            violations.add("Username cannot start with underscore or period")
        }
        if (username.endsWith("_") || username.endsWith(".")) {
            violations.add("Username cannot end with underscore or period")
        }

        // Check consecutive special characters
        if (Regex("[_.]{2,}").containsMatchIn(username)) {
            violations.add("Username cannot have consecutive underscores or periods")
        }

        // Check reserved usernames
        if (username.lowercase() in ProfileValidationService.RESERVED_USERNAMES) {
            violations.add("Username is reserved and cannot be used")
        }

        return if (violations.isEmpty()) {
            Result.success(username.trim().lowercase())
        } else {
            liftrixFailure(
                LiftrixError.ValidationError(
                    field = "username",
                    violations = violations,
                    analyticsContext = mapOf("operation" to "VALIDATE_USERNAME")
                )
            )
        }
    }

    override fun validateDisplayName(displayName: String): LiftrixResult<String> {
        val violations = mutableListOf<String>()

        // Check blank/empty
        if (displayName.isBlank()) {
            violations.add("Display name is required")
        }

        // Check length
        if (displayName.length < ProfileValidationService.MIN_DISPLAY_NAME_LENGTH) {
            violations.add("Display name must be at least ${ProfileValidationService.MIN_DISPLAY_NAME_LENGTH} character")
        }
        if (displayName.length > ProfileValidationService.MAX_DISPLAY_NAME_LENGTH) {
            violations.add("Display name cannot exceed ${ProfileValidationService.MAX_DISPLAY_NAME_LENGTH} characters")
        }

        // Check allowed characters (letters, spaces, hyphens, apostrophes)
        val validNameRegex = Regex("^[a-zA-Z\\s'-]+$")
        if (!validNameRegex.matches(displayName)) {
            violations.add("Display name can only contain letters, spaces, hyphens, and apostrophes")
        }

        // Check for profanity (basic check, expand as needed)
        val profanityWords = setOf("badword1", "badword2") // Expand this list
        if (profanityWords.any { displayName.lowercase().contains(it) }) {
            violations.add("Display name contains prohibited content")
        }

        return if (violations.isEmpty()) {
            Result.success(displayName.trim())
        } else {
            liftrixFailure(
                LiftrixError.ValidationError(
                    field = "displayName",
                    violations = violations,
                    analyticsContext = mapOf("operation" to "VALIDATE_DISPLAY_NAME")
                )
            )
        }
    }

    override fun validateBio(bio: String?): LiftrixResult<String?> {
        val violations = mutableListOf<String>()

        // Null or blank is valid
        if (bio.isNullOrBlank()) {
            return Result.success(bio)
        }

        // Check length
        if (bio.length > ProfileValidationService.MAX_BIO_LENGTH) {
            violations.add("Bio cannot exceed ${ProfileValidationService.MAX_BIO_LENGTH} characters")
        }

        // Check for special characters that break database constraints
        val invalidChars = Regex("[<>\"%;()&+]")
        if (invalidChars.containsMatchIn(bio)) {
            violations.add("Bio contains invalid special characters")
        }

        // Check for spam patterns
        val spamPatterns = listOf(
            Regex("(http|www\\.)[^\\s]+", RegexOption.IGNORE_CASE),  // URLs
            Regex("(\\d{3}[-.]?\\d{3}[-.]?\\d{4})"),  // Phone numbers
            Regex("([a-zA-Z0-9_.+-]+@[a-zA-Z0-9-]+\\.[a-zA-Z0-9-.]+)")  // Email addresses
        )
        if (spamPatterns.any { it.containsMatchIn(bio) }) {
            violations.add("Bio cannot contain URLs, phone numbers, or email addresses")
        }

        return if (violations.isEmpty()) {
            Result.success(bio.trim())
        } else {
            liftrixFailure(
                LiftrixError.ValidationError(
                    field = "bio",
                    violations = violations,
                    analyticsContext = mapOf("operation" to "VALIDATE_BIO")
                )
            )
        }
    }

    override fun validateProfileImageUrl(imageUrl: String?): LiftrixResult<String?> {
        val violations = mutableListOf<String>()

        // Null is valid (no custom image)
        if (imageUrl == null) {
            return Result.success(null)
        }

        // Blank is not valid if provided
        if (imageUrl.isBlank()) {
            violations.add("Profile image URL cannot be blank if provided")
        }

        // Check length
        if (imageUrl.length > ProfileValidationService.MAX_URL_LENGTH) {
            violations.add("Profile image URL cannot exceed ${ProfileValidationService.MAX_URL_LENGTH} characters")
        }

        // Validate URL format
        try {
            val url = URL(imageUrl)

            // Check protocol
            if (url.protocol != "https") {
                violations.add("Profile image URL must use HTTPS protocol")
            }

            // Check approved domains (Firebase Storage or approved CDN)
            val approvedDomains = setOf(
                "firebasestorage.googleapis.com",
                "storage.googleapis.com",
                "cdn.liftrix.com"  // Example CDN
            )
            if (!approvedDomains.any { url.host.contains(it) }) {
                violations.add("Profile image URL must point to Firebase Storage or approved CDN")
            }
        } catch (e: Exception) {
            violations.add("Profile image URL is not a valid URL format")
        }

        return if (violations.isEmpty()) {
            Result.success(imageUrl.trim())
        } else {
            liftrixFailure(
                LiftrixError.ValidationError(
                    field = "profileImageUrl",
                    violations = violations,
                    analyticsContext = mapOf("operation" to "VALIDATE_PROFILE_IMAGE_URL")
                )
            )
        }
    }

    override fun validateLocation(location: String?): LiftrixResult<String?> {
        val violations = mutableListOf<String>()

        // Null or blank is valid
        if (location.isNullOrBlank()) {
            return Result.success(location)
        }

        // Check length
        if (location.length > ProfileValidationService.MAX_LOCATION_LENGTH) {
            violations.add("Location cannot exceed ${ProfileValidationService.MAX_LOCATION_LENGTH} characters")
        }

        // Check allowed characters (alphanumeric, spaces, commas, hyphens)
        val validLocationRegex = Regex("^[a-zA-Z0-9\\s,'-]+$")
        if (!validLocationRegex.matches(location)) {
            violations.add("Location can only contain letters, numbers, spaces, commas, hyphens, and apostrophes")
        }

        return if (violations.isEmpty()) {
            Result.success(location.trim())
        } else {
            liftrixFailure(
                LiftrixError.ValidationError(
                    field = "location",
                    violations = violations,
                    analyticsContext = mapOf("operation" to "VALIDATE_LOCATION")
                )
            )
        }
    }

    override fun validateWebsite(website: String?): LiftrixResult<String?> {
        val violations = mutableListOf<String>()

        // Null or blank is valid
        if (website.isNullOrBlank()) {
            return Result.success(website)
        }

        // Check length
        if (website.length > ProfileValidationService.MAX_URL_LENGTH) {
            violations.add("Website URL cannot exceed ${ProfileValidationService.MAX_URL_LENGTH} characters")
        }

        // Validate URL format
        try {
            val url = URL(website)

            // Check protocol
            if (url.protocol !in setOf("http", "https")) {
                violations.add("Website URL must use HTTP or HTTPS protocol")
            }

            // Check prohibited domains
            val prohibitedDomains = setOf(
                "spam.com",
                "phishing.com",
                "malware.com"
            )
            if (prohibitedDomains.any { url.host.contains(it) }) {
                violations.add("Website URL links to prohibited domain")
            }
        } catch (e: Exception) {
            violations.add("Website URL is not a valid URL format")
        }

        return if (violations.isEmpty()) {
            Result.success(website.trim())
        } else {
            liftrixFailure(
                LiftrixError.ValidationError(
                    field = "website",
                    violations = violations,
                    analyticsContext = mapOf("operation" to "VALIDATE_WEBSITE")
                )
            )
        }
    }

    override suspend fun validateUsernameAvailability(
        username: String,
        currentUserId: String,
        checkAvailability: suspend (String) -> Boolean
    ): LiftrixResult<Unit> {
        val violations = mutableListOf<String>()

        // Check if username is available
        val isAvailable = checkAvailability(username)
        if (!isAvailable) {
            violations.add("Username is already taken")
        }

        return if (violations.isEmpty()) {
            Result.success(Unit)
        } else {
            liftrixFailure(
                LiftrixError.ValidationError(
                    field = "username",
                    violations = violations,
                    analyticsContext = mapOf(
                        "operation" to "VALIDATE_USERNAME_AVAILABILITY",
                        "username" to username,
                        "user_id" to currentUserId
                    )
                )
            )
        }
    }

    override fun validateProfileImageFile(
        imageData: ByteArray,
        mimeType: String,
        sizeBytes: Long
    ): LiftrixResult<Unit> {
        val violations = mutableListOf<String>()

        // Check if image data is provided
        if (imageData.isEmpty()) {
            violations.add("Profile image data is required")
        }

        // Check file size
        if (sizeBytes > ProfileValidationService.MAX_IMAGE_SIZE_BYTES) {
            val sizeMB = sizeBytes / (1024 * 1024)
            violations.add("Profile image size (${sizeMB}MB) exceeds maximum allowed (${ProfileValidationService.MAX_IMAGE_SIZE_MB}MB)")
        }

        // Check MIME type
        if (mimeType !in ProfileValidationService.ALLOWED_IMAGE_FORMATS) {
            violations.add("Profile image format not supported. Allowed formats: JPEG, PNG, WebP")
        }

        // Image dimensions are decoded and validated in the Android repository layer;
        // this domain service validates only transport-safe metadata and content presence.

        return if (violations.isEmpty()) {
            Result.success(Unit)
        } else {
            liftrixFailure(
                LiftrixError.ValidationError(
                    field = "profileImage",
                    violations = violations,
                    analyticsContext = mapOf(
                        "operation" to "VALIDATE_PROFILE_IMAGE_FILE",
                        "mime_type" to mimeType,
                        "size_bytes" to sizeBytes.toString()
                    )
                )
            )
        }
    }
}
