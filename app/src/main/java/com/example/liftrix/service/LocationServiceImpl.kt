package com.example.liftrix.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.os.Build
import androidx.core.app.ActivityCompat
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.location.LocationInfo
import com.example.liftrix.domain.model.location.GymLocationInfo
import com.example.liftrix.domain.model.location.FacilityType
import com.example.liftrix.domain.service.LocationService
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.Priority
import com.google.android.gms.location.Granularity
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.Tasks
import android.location.Location
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import java.io.IOException
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.math.*

/**
 * Implementation of LocationService using Google Play Services Location API
 * 
 * Provides location services for gym buddy pairing with gym detection capabilities.
 * Handles permissions, location requests, and gym facility identification.
 */
@Singleton
class LocationServiceImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : LocationService {

    private val fusedLocationClient: FusedLocationProviderClient by lazy<FusedLocationProviderClient> {
        LocationServices.getFusedLocationProviderClient(context)
    }

    private val geocoder: Geocoder? by lazy<Geocoder?> {
        if (Geocoder.isPresent()) {
            Geocoder(context, Locale.getDefault())
        } else null
    }

    companion object {
        private const val DEFAULT_TIMEOUT_MS = 10000L // 10 seconds
        private const val DEFAULT_PRIORITY = Priority.PRIORITY_HIGH_ACCURACY
        private const val PROXIMITY_RADIUS_METERS = 100.0
        
        // Gym-related keywords for facility detection
        private val GYM_KEYWORDS = setOf(
            "gym", "fitness", "health club", "recreation center", "athletic",
            "training", "workout", "exercise", "sports club", "wellness"
        )
    }

    override suspend fun getCurrentLocation(): LiftrixResult<LocationInfo> {
        return getCurrentLocation(DEFAULT_TIMEOUT_MS)
    }

    override suspend fun getCurrentLocation(timeoutMs: Long): LiftrixResult<LocationInfo> {
        return liftrixCatching(
            errorMapper = { throwable ->
                when (throwable) {
                    is SecurityException -> LiftrixError.PermissionError(
                        errorMessage = "Location permission not granted",
                        analyticsContext = mapOf("operation" to "get_current_location")
                    )
                    else -> LiftrixError.DataRetrievalError(
                        errorMessage = "Failed to get current location: ${throwable.message}",
                        analyticsContext = mapOf("operation" to "get_current_location")
                    )
                }
            }
        ) {
            if (!hasLocationPermissions()) {
                throw SecurityException("Location permissions not granted")
            }

            val locationRequest = createLocationRequest()
            val cancellationToken = CancellationTokenSource()

            try {
                val location = Tasks.await<Location>(
                    fusedLocationClient.getCurrentLocation(
                        DEFAULT_PRIORITY,
                        cancellationToken.token
                    ),
                    timeoutMs,
                    TimeUnit.MILLISECONDS
                )

                if (location != null) {
                    LocationInfo(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        accuracy = location.accuracy,
                        timestamp = location.time
                    )
                } else {
                    // Fallback to last known location
                    val lastKnown = getLastKnownLocation()
                    lastKnown.getOrElse { 
                        throw Exception("Failed to get location")
                    } ?: throw Exception("No location available")
                }
            } finally {
                cancellationToken.cancel()
            }
        }
    }

    override suspend fun getCurrentGymLocation(): LiftrixResult<GymLocationInfo> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DataRetrievalError(
                    errorMessage = "Failed to get gym location: ${throwable.message}",
                    analyticsContext = mapOf("operation" to "get_gym_location")
                )
            }
        ) {
            val locationResult = getCurrentLocation()
            val location = locationResult.getOrElse { 
                throw Exception("Failed to get current location")
            }

            val addressResult = getAddressFromCoordinates(location.latitude, location.longitude)
            val address = addressResult.getOrNull()

            val gymInfo = detectGymFromAddress(address)
            
            GymLocationInfo(
                location = location.copy(address = address),
                gymName = gymInfo.first,
                facilityType = gymInfo.second,
                confidence = gymInfo.third
            )
        }
    }

    override suspend fun hasLocationPermissions(): Boolean {
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    override suspend fun requestLocationPermissions(): LiftrixResult<Boolean> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.PermissionError(
                    errorMessage = "Failed to request location permissions: ${throwable.message}",
                    analyticsContext = mapOf("operation" to "request_location_permissions")
                )
            }
        ) {
            // Note: This method can't directly request permissions in a service
            // It should be called from an Activity context
            // For now, just check current permissions
            hasLocationPermissions()
        }
    }

    override suspend fun getLastKnownLocation(): LiftrixResult<LocationInfo?> {
        return liftrixCatching(
            errorMapper = { throwable ->
                when (throwable) {
                    is SecurityException -> LiftrixError.PermissionError(
                        errorMessage = "Location permission not granted",
                        analyticsContext = mapOf("operation" to "get_last_known_location")
                    )
                    else -> LiftrixError.DataRetrievalError(
                        errorMessage = "Failed to get last known location: ${throwable.message}",
                        analyticsContext = mapOf("operation" to "get_last_known_location")
                    )
                }
            }
        ) {
            if (!hasLocationPermissions()) {
                throw SecurityException("Location permissions not granted")
            }

            val location = Tasks.await<Location?>(
                fusedLocationClient.lastLocation,
                5000,
                TimeUnit.MILLISECONDS
            )

            location?.let { loc: Location ->
                LocationInfo(
                    latitude = loc.latitude,
                    longitude = loc.longitude,
                    accuracy = loc.accuracy,
                    timestamp = loc.time
                )
            }
        }
    }

    override fun areLocationsNearby(
        location1: LocationInfo,
        location2: LocationInfo,
        radiusMeters: Double
    ): Boolean {
        val distance = calculateDistance(
            location1.latitude, location1.longitude,
            location2.latitude, location2.longitude
        )
        return distance <= radiusMeters
    }

    override suspend fun getAddressFromCoordinates(
        latitude: Double,
        longitude: Double
    ): LiftrixResult<String> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DataRetrievalError(
                    errorMessage = "Failed to get address from coordinates: ${throwable.message}",
                    analyticsContext = mapOf("operation" to "geocode_address")
                )
            }
        ) {
            val geocoder = this.geocoder 
                ?: throw IOException("Geocoder not available on this device")

            val addresses = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Use new async API for Android 13+
                suspendCancellableCoroutine { continuation ->
                    geocoder.getFromLocation(
                        latitude,
                        longitude,
                        1
                    ) { addresses ->
                        continuation.resume(addresses)
                    }
                }
            } else {
                // Use legacy synchronous API
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(latitude, longitude, 1) ?: emptyList()
            }

            if (addresses.isNotEmpty()) {
                val address = addresses[0]
                buildAddressString(address)
            } else {
                "Location: ${String.format("%.6f", latitude)}, ${String.format("%.6f", longitude)}"
            }
        }
    }

    /**
     * Creates a location request with optimal settings
     */
    private fun createLocationRequest(): LocationRequest {
        return LocationRequest.Builder(DEFAULT_PRIORITY, 10000L).apply {
            setGranularity(Granularity.GRANULARITY_PERMISSION_LEVEL)
            setWaitForAccurateLocation(true)
            setMaxUpdateDelayMillis(15000L)
            setMinUpdateIntervalMillis(5000L)
        }.build()
    }

    /**
     * Calculates distance between two points using Haversine formula
     */
    private fun calculateDistance(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val earthRadius = 6371000.0 // Earth's radius in meters
        
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        
        return earthRadius * c
    }

    /**
     * Builds a readable address string from Address object
     */
    private fun buildAddressString(address: Address): String {
        val addressLines = mutableListOf<String>()
        
        // Add premise/sub-premise (building details)
        address.premises?.let { premise: String -> addressLines.add(premise) }
        address.subThoroughfare?.let { subThoroughfare: String -> addressLines.add(subThoroughfare) }
        
        // Add street address
        address.getAddressLine(0)?.let { line ->
            if (addressLines.isEmpty() || !line.contains(addressLines.joinToString(" "))) {
                addressLines.add(line)
            }
        }
        
        return addressLines.joinToString(", ").ifEmpty {
            "Near ${address.locality ?: address.subAdminArea ?: "Unknown Location"}"
        }
    }

    /**
     * Attempts to detect gym information from address
     */
    private fun detectGymFromAddress(address: String?): Triple<String?, FacilityType, Float> {
        if (address.isNullOrBlank()) {
            return Triple(null, FacilityType.UNKNOWN, 0.0f)
        }

        val addressLower = address.lowercase()
        
        // Check for gym-related keywords
        val matchingKeywords = GYM_KEYWORDS.filter { keyword ->
            addressLower.contains(keyword)
        }

        if (matchingKeywords.isNotEmpty()) {
            val facilityType = when {
                addressLower.contains("gym") -> FacilityType.GYM
                addressLower.contains("fitness") -> FacilityType.FITNESS_CENTER
                addressLower.contains("health club") -> FacilityType.HEALTH_CLUB
                addressLower.contains("recreation center") -> FacilityType.RECREATION_CENTER
                else -> FacilityType.FITNESS_CENTER
            }
            
            val confidence = minOf(matchingKeywords.size * 0.3f, 1.0f)
            
            // Try to extract facility name
            val gymName = extractFacilityName(address, matchingKeywords)
            
            return Triple(gymName, facilityType, confidence)
        }

        // Check for residential indicators (potential home gym)
        val homeKeywords = setOf("apartment", "unit", "home", "residence", "street", "drive", "road")
        val hasHomeKeywords = homeKeywords.any { addressLower.contains(it) }
        
        return if (hasHomeKeywords) {
            Triple("Home Gym", FacilityType.HOME_GYM, 0.4f)
        } else {
            Triple(null, FacilityType.UNKNOWN, 0.0f)
        }
    }

    /**
     * Attempts to extract facility name from address
     */
    private fun extractFacilityName(address: String, matchingKeywords: List<String>): String? {
        return try {
            val parts = address.split(",")
            val firstPart = parts.firstOrNull()?.trim() ?: return null
            
            // If the first part contains gym keywords, it's likely the facility name
            if (matchingKeywords.any { firstPart.lowercase().contains(it) }) {
                firstPart
            } else {
                // Look for parts that contain keywords
                parts.find { part ->
                    matchingKeywords.any { keyword ->
                        part.lowercase().contains(keyword)
                    }
                }?.trim()
            }
        } catch (e: Exception) {
            Timber.w(e, "Error extracting facility name from address")
            null
        }
    }
}