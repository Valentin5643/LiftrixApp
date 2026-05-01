package com.example.liftrix.domain.model.social

/**
 * Simplified user profile model for QR code display and gym buddy pairing
 * 
 * Contains minimal user information needed for QR code generation and buddy pairing.
 * This is separate from the full UserProfile domain model to keep QR functionality lightweight.
 */
data class QRUserProfile(
    val displayName: String,
    val username: String,
    val profilePhotoUrl: String? = null
)