package com.example.liftrix.data.service

import com.example.liftrix.domain.service.ProfileImageUrlResolver
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for resolving Firebase Storage paths to fresh download URLs.
 * 
 * This eliminates the token invalidation problem by:
 * 1. Storing only storage paths (e.g., "profile_images/userId/avatar.jpg") 
 * 2. Resolving to fresh download URLs at runtime
 * 3. Avoiding cached/stale tokenized URLs that cause 403 errors
 * 
 * Key Benefits:
 * - Always gets fresh, valid download tokens
 * - Eliminates race conditions between token rotation and caching
 * - Single source of truth for URL resolution
 * - Handles token regeneration automatically
 */
@Singleton
class FirebaseStorageUrlResolver @Inject constructor(
    private val firebaseStorage: FirebaseStorage
) : ProfileImageUrlResolver {
    
    /**
     * Resolves a Firebase Storage path to a fresh download URL.
     * Automatically handles both storage paths and URLs for backward compatibility.
     * 
     * @param storagePathOrUrl Storage path like "profile_images/userId/avatar.jpg" or full URL
     * @return Fresh download URL with valid token, or null if file doesn't exist
     */
    override suspend fun resolveUrl(storagePathOrUrl: String): String? {
        return try {
            // Check if input is already a full URL or a storage path
            val storagePath = if (storagePathOrUrl.startsWith("http://") || storagePathOrUrl.startsWith("https://")) {
                // Extract storage path from URL for backward compatibility
                val pathMatch = Regex("/o/([^?]+)\\?").find(storagePathOrUrl)
                val extractedPath = pathMatch?.groupValues?.get(1)?.replace("%2F", "/")
                
                if (extractedPath != null) {
                    Timber.d("[URL_RESOLVER] Extracted storage path from URL: $storagePathOrUrl -> $extractedPath")
                    extractedPath
                } else {
                    Timber.w("[URL_RESOLVER] Failed to extract storage path from URL: $storagePathOrUrl")
                    return null
                }
            } else {
                // Already a storage path
                Timber.d("[URL_RESOLVER] Using storage path directly: $storagePathOrUrl")
                storagePathOrUrl
            }
            
            val storageRef = firebaseStorage.reference.child(storagePath)
            val downloadUrl = storageRef.downloadUrl.await()
            val urlString = downloadUrl.toString()
            
            // Extract token for logging
            val token = urlString.substringAfter("token=").substringBefore("&")
            Timber.d("[URL_RESOLVER] ✅ Resolved fresh URL for path: $storagePath | token=$token")
            
            urlString
        } catch (e: Exception) {
            Timber.w(e, "[URL_RESOLVER] Failed to resolve URL for input: $storagePathOrUrl")
            null
        }
    }
    
    /**
     * Resolves profile image path to download URL.
     * Convenience method for the common profile image use case.
     * 
     * @param userId User ID for profile image
     * @param filename Image filename (defaults to "avatar.jpg")
     * @return Fresh download URL or null if not found
     */
    suspend fun resolveProfileImageUrl(userId: String, filename: String = "avatar.jpg"): String? {
        val storagePath = "profile_images/$userId/$filename"
        return resolveUrl(storagePath)
    }
    
    /**
     * Creates a storage path for profile images.
     * Use this when storing paths instead of full URLs.
     * 
     * @param userId User ID
     * @param filename Image filename (defaults to "avatar.jpg")
     * @return Storage path string
     */
    fun createProfileImagePath(userId: String, filename: String = "avatar.jpg"): String {
        return "profile_images/$userId/$filename"
    }
}
