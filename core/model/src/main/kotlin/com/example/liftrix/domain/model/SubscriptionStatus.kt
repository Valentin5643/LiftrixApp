package com.example.liftrix.domain.model

/**
 * Enum representing different subscription statuses.
 * 
 * This enum defines the possible states a subscription can be in,
 * providing a clear and type-safe way to handle subscription status
 * throughout the application.
 */
enum class SubscriptionStatus(val displayName: String) {
    /**
     * The subscription is active and provides premium features.
     */
    ACTIVE("Active"),
    
    /**
     * The subscription has been cancelled but may still be active until expiration.
     */
    CANCELLED("Cancelled"),
    
    /**
     * The subscription has expired and no longer provides premium features.
     */
    EXPIRED("Expired"),
    
    /**
     * The subscription is in a trial period.
     */
    TRIAL("Trial"),
    
    /**
     * The subscription is temporarily paused.
     */
    PAUSED("Paused"),
    
    /**
     * The subscription purchase is pending acknowledgment.
     */
    PENDING("Pending");
    
    /**
     * Checks if the subscription status provides premium access.
     * 
     * @return True if the status allows premium features
     */
    val providesAccess: Boolean
        get() = this in listOf(ACTIVE, TRIAL, PENDING)
    
    /**
     * Checks if the subscription is in a terminal state (cannot be reactivated).
     * 
     * @return True if the subscription is expired or cancelled
     */
    val isTerminal: Boolean
        get() = this in listOf(EXPIRED, CANCELLED)
    
    companion object {
        /**
         * Gets the default subscription status for new users.
         * 
         * @return The default subscription status
         */
        fun default(): SubscriptionStatus = EXPIRED
        
        /**
         * Converts a string representation to SubscriptionStatus.
         * 
         * @param statusString The string representation of the status
         * @return The corresponding SubscriptionStatus, or EXPIRED if invalid
         */
        fun fromString(statusString: String): SubscriptionStatus {
            return when (statusString.lowercase()) {
                "active" -> ACTIVE
                "cancelled", "canceled" -> CANCELLED
                "expired" -> EXPIRED
                "trial" -> TRIAL
                "paused" -> PAUSED
                "pending" -> PENDING
                else -> EXPIRED
            }
        }
    }
}