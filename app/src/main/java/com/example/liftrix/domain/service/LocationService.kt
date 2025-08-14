package com.example.liftrix.domain.service

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.location.LocationInfo
import com.example.liftrix.domain.model.location.GymLocationInfo

/**
 * Service interface for location operations and gym location detection
 * Provides current location, gym detection, and location context for buddy pairing
 */
interface LocationService {
    
    /**
     * Gets the current location of the device
     */
    suspend fun getCurrentLocation(): LiftrixResult<LocationInfo>
    
    /**
     * Gets the current location with timeout
     */
    suspend fun getCurrentLocation(timeoutMs: Long): LiftrixResult<LocationInfo>
    
    /**
     * Attempts to identify the current gym or fitness facility
     */
    suspend fun getCurrentGymLocation(): LiftrixResult<GymLocationInfo>
    
    /**
     * Checks if location permissions are granted
     */
    suspend fun hasLocationPermissions(): Boolean
    
    /**
     * Requests location permissions from the user
     */
    suspend fun requestLocationPermissions(): LiftrixResult<Boolean>
    
    /**
     * Gets the last known location (faster but potentially stale)
     */
    suspend fun getLastKnownLocation(): LiftrixResult<LocationInfo?>
    
    /**
     * Validates if two locations are within proximity (for gym buddy pairing)
     */
    fun areLocationsNearby(
        location1: LocationInfo, 
        location2: LocationInfo, 
        radiusMeters: Double = 100.0
    ): Boolean
    
    /**
     * Gets a human-readable address from coordinates
     */
    suspend fun getAddressFromCoordinates(
        latitude: Double, 
        longitude: Double
    ): LiftrixResult<String>
}