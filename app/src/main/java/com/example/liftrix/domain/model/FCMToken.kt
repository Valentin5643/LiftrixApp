package com.example.liftrix.domain.model

/**
 * Domain model representing an FCM token for push notifications.
 * Part of notification system from SPEC-20250113-notifications-privacy.
 */
data class FCMToken(
    val id: String,
    val userId: String,
    val token: String,
    
    // Device information
    val deviceId: String,
    val deviceName: String? = null,
    val platform: Platform,
    val appVersion: String? = null,
    
    // Status
    val isActive: Boolean = true,
    val lastUsed: Long? = null,
    
    // Timestamps
    val createdAt: Long,
    val updatedAt: Long,
    
    // Sync status
    val isSynced: Boolean = false
) {
    enum class Platform(val value: String) {
        ANDROID("ANDROID"),
        IOS("IOS");
        
        companion object {
            fun fromString(value: String): Platform {
                return values().firstOrNull { it.value == value } ?: ANDROID
            }
        }
    }
    
    /**
     * Checks if this token is recently used (within the last 7 days)
     */
    fun isRecentlyUsed(): Boolean {
        val lastUsedTime = lastUsed ?: createdAt
        val sevenDaysAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000)
        return lastUsedTime > sevenDaysAgo
    }
    
    /**
     * Checks if this token is stale (not used in the last 30 days)
     */
    fun isStale(): Boolean {
        val lastUsedTime = lastUsed ?: createdAt
        val thirtyDaysAgo = System.currentTimeMillis() - (30 * 24 * 60 * 60 * 1000)
        return lastUsedTime < thirtyDaysAgo
    }
    
    /**
     * Gets a display name for this device
     */
    fun getDisplayName(): String {
        return deviceName ?: "${platform.value} Device"
    }
    
    /**
     * Masks the token for display/logging purposes
     */
    fun getMaskedToken(): String {
        return if (token.length > 20) {
            "${token.take(10)}...${token.takeLast(10)}"
        } else {
            "${token.take(token.length / 2)}..."
        }
    }
}