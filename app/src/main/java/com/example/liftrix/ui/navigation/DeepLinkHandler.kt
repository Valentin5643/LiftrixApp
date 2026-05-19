package com.example.liftrix.ui.navigation

import android.content.Intent
import android.net.Uri
import androidx.navigation.NavController
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DeepLinkHandler - Centralized deep link processing for profile sharing and navigation
 * Part of user profiles and follow system from SPEC-20250113-user-profiles-follow.
 * 
 * Handles incoming deep links from various sources:
 * - Profile sharing links (liftrix.app/profile/{username})
 * - QR code scans for profile discovery
 * - External app integrations and sharing
 * - Notification actions (follow requests, profile updates)
 * - Web-based profile links from social media
 * 
 * Features:
 * - Type-safe route parsing and validation
 * - Fallback handling for invalid/expired links
 * - Analytics tracking for deep link usage
 * - Security validation to prevent malicious links
 * - Support for multiple link formats and versions
 * - Graceful degradation for unsupported links
 */
@Singleton
class DeepLinkHandler @Inject constructor() {
    
    companion object {
        // Supported deep link domains and schemes
        private const val LIFTRIX_DOMAIN = "liftrix.app"
        private const val LIFTRIX_DOMAIN_ALT = "www.liftrix.app"
        private const val LIFTRIX_SCHEME = "liftrix"
        
        // Profile link patterns
        private val PROFILE_PATTERN = Regex("^/profile/([a-zA-Z0-9_-]+)$")
        private val PROFILE_WITH_ACTION_PATTERN = Regex("^/profile/([a-zA-Z0-9_-]+)/(follow|message)$")
        
        // User ID validation pattern
        private val USER_ID_PATTERN = Regex("^[a-zA-Z0-9_-]{3,50}$")
        
        // Supported deep link paths
        private val SUPPORTED_PATHS = setOf(
            "/profile/",
            "/workout/",
            "/exercise/",
            "/share/",
            "/invite/",
            "/qr/"
        )
    }
    
    /**
     * Process an incoming deep link and navigate to the appropriate screen
     * 
     * @param intent The incoming intent containing the deep link
     * @param navController The navigation controller for routing
     * @return true if the deep link was successfully processed, false otherwise
     */
    fun processDeepLink(intent: Intent, navController: NavController): Boolean {
        val uri = intent.data ?: return false
        
        Timber.d("Processing deep link: $uri")
        
        return try {
            when {
                isProfileLink(uri) -> handleProfileLink(uri, navController)
                isWorkoutLink(uri) -> handleWorkoutLink(uri, navController)
                isExerciseLink(uri) -> handleExerciseLink(uri, navController)
                isShareLink(uri) -> handleShareLink(uri, navController)
                isQRLink(uri) -> handleQRLink(uri, navController)
                else -> handleUnknownLink(uri, navController)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error processing deep link")
            handleErrorLink(uri, navController)
        }
    }
    
    /**
     * Generate a profile deep link for sharing
     * 
     * @param userId The user ID to generate a profile link for
     * @param action Optional action (follow, message) to include in link
     * @return The formatted deep link URL
     */
    fun generateProfileLink(userId: String, action: String? = null): String {
        if (!isValidUserId(userId)) {
            throw IllegalArgumentException("Invalid user ID for profile link: $userId")
        }
        
        val baseUrl = "https://$LIFTRIX_DOMAIN/profile/$userId"
        return if (action != null) {
            "$baseUrl/$action"
        } else {
            baseUrl
        }
    }
    
    /**
     * Generate a QR code deep link for profile sharing
     * 
     * @param userId The user ID to generate a QR link for
     * @return The formatted QR deep link URL
     */
    fun generateQRLink(userId: String): String {
        if (!isValidUserId(userId)) {
            throw IllegalArgumentException("Invalid user ID for QR link: $userId")
        }
        
        return "https://$LIFTRIX_DOMAIN/qr/$userId"
    }
    
    /**
     * Check if a URI is a supported Liftrix deep link
     */
    fun isSupportedDeepLink(uri: Uri): Boolean {
        return when {
            uri.scheme == LIFTRIX_SCHEME -> true
            uri.host == LIFTRIX_DOMAIN || uri.host == LIFTRIX_DOMAIN_ALT -> {
                val path = uri.path ?: ""
                SUPPORTED_PATHS.any { supportedPath -> path.startsWith(supportedPath) }
            }
            else -> false
        }
    }
    
    // MARK: - Private Helper Methods
    
    private fun isProfileLink(uri: Uri): Boolean {
        if (!isLiftrixDomain(uri)) return false
        val path = uri.path ?: return false
        return PROFILE_PATTERN.matches(path) || PROFILE_WITH_ACTION_PATTERN.matches(path)
    }
    
    private fun handleProfileLink(uri: Uri, navController: NavController): Boolean {
        val path = uri.path ?: return false
        
        // Try to match profile with action first
        val actionMatch = PROFILE_WITH_ACTION_PATTERN.matchEntire(path)
        if (actionMatch != null) {
            val userId = actionMatch.groupValues[1]
            val action = actionMatch.groupValues[2]
            
            if (!isValidUserId(userId)) {
                Timber.w("Invalid user ID in profile link")
                return false
            }
            
            return handleProfileWithAction(userId, action, navController)
        }
        
        // Try regular profile match
        val match = PROFILE_PATTERN.matchEntire(path)
        if (match != null) {
            val userId = match.groupValues[1]
            
            if (!isValidUserId(userId)) {
                Timber.w("Invalid user ID in profile link")
                return false
            }
            
            Timber.i("Navigating to profile")
            navController.navigateFromDeepLink(LiftrixRoute.PublicProfile(userId))
            return true
        }
        
        return false
    }
    
    private fun handleProfileWithAction(userId: String, action: String, navController: NavController): Boolean {
        Timber.i("Navigating to profile with action: $action")
        
        // Navigate to profile with the specified action
        navController.navigateFromDeepLink(
            LiftrixRoute.PublicProfile(
                userId = userId,
                initialAction = when (action) {
                    "follow", "message" -> action
                    else -> {
                        Timber.w("Unknown profile action: $action, ignoring")
                        null
                    }
                }
            )
        )
        
        return true
    }
    
    private fun isWorkoutLink(uri: Uri): Boolean {
        if (!isLiftrixDomain(uri)) return false
        val path = uri.path ?: return false
        return path.startsWith("/workout/")
    }
    
    private fun handleWorkoutLink(uri: Uri, navController: NavController): Boolean {
        val path = uri.path ?: return false
        val workoutId = path.removePrefix("/workout/").takeIf { it.isNotBlank() }
        
        if (workoutId != null && isValidWorkoutId(workoutId)) {
            Timber.i("Navigating to workout: $workoutId")
            navController.navigateFromDeepLink(LiftrixRoute.WorkoutDetails(workoutId))
            return true
        }
        
        Timber.w("Invalid workout ID in deep link: $workoutId")
        return false
    }
    
    private fun isExerciseLink(uri: Uri): Boolean {
        if (!isLiftrixDomain(uri)) return false
        val path = uri.path ?: return false
        return path.startsWith("/exercise/")
    }
    
    private fun handleExerciseLink(uri: Uri, navController: NavController): Boolean {
        val path = uri.path ?: return false
        val exerciseId = path.removePrefix("/exercise/").takeIf { it.isNotBlank() }
        
        if (exerciseId != null && isValidExerciseId(exerciseId)) {
            Timber.i("Navigating to exercise: $exerciseId")
            navController.navigateFromDeepLink(LiftrixRoute.ExerciseDetails(exerciseId))
            return true
        }
        
        Timber.w("Invalid exercise ID in deep link: $exerciseId")
        return false
    }
    
    private fun isShareLink(uri: Uri): Boolean {
        if (!isLiftrixDomain(uri)) return false
        val path = uri.path ?: return false
        return path.startsWith("/share/")
    }
    
    private fun handleShareLink(uri: Uri, navController: NavController): Boolean {
        val path = uri.path ?: return false
        
        when {
            path.startsWith("/share/profile/") -> {
                val userId = path.removePrefix("/share/profile/").takeIf { it.isNotBlank() }
                if (userId != null && isValidUserId(userId)) {
                    Timber.i("Navigating to shared profile")
                    navController.navigateFromDeepLink(LiftrixRoute.PublicProfile(userId))
                    return true
                }
            }
            path.startsWith("/share/workout/") -> {
                val workoutId = path.removePrefix("/share/workout/").takeIf { it.isNotBlank() }
                if (workoutId != null && isValidWorkoutId(workoutId)) {
                    Timber.i("Navigating to shared workout: $workoutId")
                    navController.navigateFromDeepLink(LiftrixRoute.WorkoutDetails(workoutId))
                    return true
                }
            }
        }
        
        Timber.w("Unsupported share link format: $path")
        return false
    }
    
    private fun isQRLink(uri: Uri): Boolean {
        if (!isLiftrixDomain(uri)) return false
        val path = uri.path ?: return false
        return path.startsWith("/qr/")
    }
    
    private fun handleQRLink(uri: Uri, navController: NavController): Boolean {
        val path = uri.path ?: return false
        val userId = path.removePrefix("/qr/").takeIf { it.isNotBlank() }
        
        if (userId != null && isValidUserId(userId)) {
            Timber.i("Navigating to QR profile")
            navController.navigateFromDeepLink(LiftrixRoute.PublicProfile(userId))
            return true
        }
        
        Timber.w("Invalid user ID in QR link")
        return false
    }
    
    private fun handleUnknownLink(uri: Uri, navController: NavController): Boolean {
        Timber.w("Unknown deep link format")
        
        // Try to extract any user ID from query parameters as fallback
        val userIdParam = uri.getQueryParameter("user_id") ?: uri.getQueryParameter("profile")
        if (userIdParam != null && isValidUserId(userIdParam)) {
            Timber.i("Fallback navigation to profile from query parameter")
            navController.navigateFromDeepLink(LiftrixRoute.PublicProfile(userIdParam))
            return true
        }
        
        // Fallback to home screen
        Timber.i("Fallback navigation to home screen")
        navController.navigateFromDeepLink(LiftrixRoute.Home)
        return false
    }
    
    private fun handleErrorLink(uri: Uri, navController: NavController): Boolean {
        Timber.e("Error handling deep link, navigating to home")
        navController.navigateFromDeepLink(LiftrixRoute.Home)
        return false
    }
    
    // MARK: - Validation Helper Methods
    
    private fun isLiftrixDomain(uri: Uri): Boolean {
        return when (uri.scheme) {
            LIFTRIX_SCHEME -> true
            "http", "https" -> uri.host in listOf(LIFTRIX_DOMAIN, LIFTRIX_DOMAIN_ALT)
            else -> false
        }
    }
    
    private fun isValidUserId(userId: String): Boolean {
        return USER_ID_PATTERN.matches(userId) && userId.length >= 3
    }
    
    private fun isValidWorkoutId(workoutId: String): Boolean {
        // Basic validation for workout IDs
        return workoutId.isNotBlank() && workoutId.length >= 3 && workoutId.length <= 100
    }
    
    private fun isValidExerciseId(exerciseId: String): Boolean {
        // Basic validation for exercise IDs
        return exerciseId.isNotBlank() && exerciseId.length >= 3 && exerciseId.length <= 100
    }
    
    /**
     * Data class for deep link parsing results
     */
    data class DeepLinkResult(
        val success: Boolean,
        val route: LiftrixRoute?,
        val action: String?,
        val error: String?
    )
    
    /**
     * Enum for supported deep link actions
     */
    enum class DeepLinkAction(val value: String) {
        FOLLOW("follow"),
        MESSAGE("message"),
        SHARE("share"),
        VIEW("view")
    }
    
    /**
     * Enum for deep link sources for analytics
     */
    enum class DeepLinkSource(val value: String) {
        QR_CODE("qr_code"),
        SOCIAL_SHARE("social_share"),
        NOTIFICATION("notification"),
        WEB_LINK("web_link"),
        OTHER_APP("other_app"),
        UNKNOWN("unknown")
    }
}

/**
 * Extension function to detect deep link source from Intent
 */
fun Intent.getDeepLinkSource(context: android.content.Context): DeepLinkHandler.DeepLinkSource {
    return when {
        hasExtra("from_notification") -> DeepLinkHandler.DeepLinkSource.NOTIFICATION
        hasExtra("from_qr") -> DeepLinkHandler.DeepLinkSource.QR_CODE
        action == Intent.ACTION_SEND || action == Intent.ACTION_SEND_MULTIPLE -> DeepLinkHandler.DeepLinkSource.SOCIAL_SHARE
        `package` != null && `package` != getPackageName(context) -> DeepLinkHandler.DeepLinkSource.OTHER_APP
        else -> DeepLinkHandler.DeepLinkSource.WEB_LINK
    }
}

/**
 * Extension function to get package name from Intent
 */
private fun Intent.getPackageName(context: android.content.Context): String? {
    return resolveActivity(context.packageManager)?.packageName
}
