package com.example.liftrix.domain.model.social

import android.graphics.Bitmap

/**
 * Domain model for QR code data used in gym buddy pairing
 * 
 * Contains the QR code metadata and bitmap for display purposes.
 * Used for secure gym buddy pairing via QR code scanning.
 */
data class QRCodeData(
    val token: String,
    val expiresAt: Long,
    val bitmap: Bitmap? = null
) {
    /**
     * Checks if the QR code has expired
     */
    fun isExpired(): Boolean {
        return System.currentTimeMillis() > expiresAt
    }
    
    /**
     * Gets the remaining time in milliseconds until expiration
     */
    fun getRemainingTimeMs(): Long {
        return maxOf(0L, expiresAt - System.currentTimeMillis())
    }
}