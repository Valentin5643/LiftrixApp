package com.example.liftrix.domain.model.location

/**
 * Basic location information for gym buddy pairing and location context
 */
data class LocationInfo(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val address: String? = null
) {
    /**
     * Returns a formatted coordinate string for display
     */
    fun getFormattedCoordinates(): String {
        return "${String.format("%.6f", latitude)}, ${String.format("%.6f", longitude)}"
    }
    
    /**
     * Returns a display-friendly address or coordinates
     */
    fun getDisplayAddress(): String {
        return address?.takeIf { it.isNotBlank() } ?: getFormattedCoordinates()
    }
}

/**
 * Extended location information for gym/fitness facilities
 */
data class GymLocationInfo(
    val location: LocationInfo,
    val gymName: String? = null,
    val facilityType: FacilityType = FacilityType.UNKNOWN,
    val confidence: Float = 0.0f, // 0.0 to 1.0
    val placeId: String? = null
) {
    /**
     * Returns the best available name for the location
     */
    fun getBestLocationName(): String {
        return gymName?.takeIf { it.isNotBlank() }
            ?: facilityType.getDisplayName()
            ?: location.getDisplayAddress()
    }
    
    /**
     * Checks if this is likely a gym or fitness facility
     */
    fun isLikelyGym(): Boolean {
        return confidence >= 0.3f && facilityType != FacilityType.UNKNOWN
    }
}

/**
 * Types of fitness facilities
 */
enum class FacilityType {
    GYM,
    FITNESS_CENTER,
    HEALTH_CLUB,
    RECREATION_CENTER,
    HOME_GYM,
    OUTDOOR_SPACE,
    UNKNOWN;
    
    /**
     * Returns a user-friendly display name
     */
    fun getDisplayName(): String? {
        return when (this) {
            GYM -> "Gym"
            FITNESS_CENTER -> "Fitness Center"
            HEALTH_CLUB -> "Health Club"
            RECREATION_CENTER -> "Recreation Center"
            HOME_GYM -> "Home Gym"
            OUTDOOR_SPACE -> "Outdoor Space"
            UNKNOWN -> null
        }
    }
}