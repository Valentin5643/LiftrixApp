package com.example.liftrix.data.cache

import android.content.Context
import android.content.SharedPreferences
import com.example.liftrix.domain.model.RecommendedUser
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cache implementation for user recommendations using SharedPreferences with TTL mechanism
 * Follows Clean Architecture patterns with offline-first caching strategy
 */
@Singleton
class RecommendationCache @Inject constructor(
    private val context: Context,
    private val gson: Gson
) {
    
    companion object {
        private const val PREFS_NAME = "liftrix_recommendation_cache"
        private const val KEY_RECOMMENDATIONS = "recommendations"
        private const val KEY_CACHE_TIMESTAMP = "cache_timestamp"
        private const val KEY_USER_ID = "cached_user_id"
    }
    
    private val sharedPreferences: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    /**
     * Retrieves cached recommendations for the specified user
     * Returns null if cache is invalid, expired, or for different user
     * 
     * @param userId Current user's ID for cache validation
     * @return List of cached recommendations or null if cache miss
     */
    suspend fun getCachedRecommendations(userId: String): List<RecommendedUser>? = withContext(Dispatchers.IO) {
        try {
            // Validate cache is for current user
            val cachedUserId = sharedPreferences.getString(KEY_USER_ID, null)
            if (cachedUserId != userId) {
                Timber.d("Cache miss: different user ($cachedUserId vs $userId)")
                return@withContext null
            }
            
            // Check if cache timestamp exists
            val cacheTimestamp = sharedPreferences.getLong(KEY_CACHE_TIMESTAMP, 0L)
            if (cacheTimestamp == 0L) {
                Timber.d("Cache miss: no timestamp found")
                return@withContext null
            }
            
            // Get cached recommendations JSON
            val cachedJson = sharedPreferences.getString(KEY_RECOMMENDATIONS, null)
            if (cachedJson.isNullOrBlank()) {
                Timber.d("Cache miss: no recommendations data")
                return@withContext null
            }
            
            // Deserialize recommendations
            val typeToken = object : TypeToken<List<RecommendedUser>>() {}.type
            val cachedRecommendations: List<RecommendedUser> = gson.fromJson(cachedJson, typeToken)
            
            // Validate TTL using first item's cache validation
            if (cachedRecommendations.isEmpty()) {
                Timber.d("Cache miss: empty recommendations list")
                return@withContext null
            }
            
            val firstRecommendation = cachedRecommendations.first()
            if (!firstRecommendation.isCacheValid) {
                Timber.d("Cache expired: TTL validation failed (age: ${firstRecommendation.cacheAge}ms)")
                clearCache()
                return@withContext null
            }
            
            Timber.d("Cache hit: returning ${cachedRecommendations.size} recommendations for user $userId")
            cachedRecommendations
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to retrieve cached recommendations")
            clearCache() // Clear corrupted cache
            null
        }
    }
    
    /**
     * Caches recommendations for the specified user with current timestamp
     * 
     * @param userId Current user's ID
     * @param recommendations List of recommendations to cache
     */
    suspend fun cacheRecommendations(userId: String, recommendations: List<RecommendedUser>) = withContext(Dispatchers.IO) {
        try {
            if (recommendations.isEmpty()) {
                Timber.w("Attempting to cache empty recommendations list")
                return@withContext
            }
            
            // Refresh cache timestamps on all recommendations
            val timestampedRecommendations = recommendations.map { it.refreshCache() }
            
            // Serialize recommendations to JSON
            val recommendationsJson = gson.toJson(timestampedRecommendations)
            val currentTimestamp = System.currentTimeMillis()
            
            // Store in SharedPreferences
            sharedPreferences.edit()
                .putString(KEY_USER_ID, userId)
                .putString(KEY_RECOMMENDATIONS, recommendationsJson)
                .putLong(KEY_CACHE_TIMESTAMP, currentTimestamp)
                .apply()
            
            Timber.d("Cached ${timestampedRecommendations.size} recommendations for user $userId")
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to cache recommendations")
        }
    }
    
    /**
     * Checks if the cache is valid for the specified user
     * 
     * @param userId Current user's ID
     * @return true if cache is valid and not expired
     */
    suspend fun isCacheValid(userId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val cachedUserId = sharedPreferences.getString(KEY_USER_ID, null)
            if (cachedUserId != userId) {
                return@withContext false
            }
            
            val cacheTimestamp = sharedPreferences.getLong(KEY_CACHE_TIMESTAMP, 0L)
            if (cacheTimestamp == 0L) {
                return@withContext false
            }
            
            // Check TTL using RecommendedUser validation logic
            val cachedJson = sharedPreferences.getString(KEY_RECOMMENDATIONS, null)
            if (cachedJson.isNullOrBlank()) {
                return@withContext false
            }
            
            val typeToken = object : TypeToken<List<RecommendedUser>>() {}.type
            val cachedRecommendations: List<RecommendedUser> = gson.fromJson(cachedJson, typeToken)
            
            if (cachedRecommendations.isEmpty()) {
                return@withContext false
            }
            
            val isValid = cachedRecommendations.first().isCacheValid
            Timber.v("Cache validity check for user $userId: $isValid")
            isValid
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to check cache validity")
            false
        }
    }
    
    /**
     * Clears all cached recommendations and metadata
     * Called when cache is corrupted, expired, or user changes
     */
    suspend fun clearCache() = withContext(Dispatchers.IO) {
        try {
            sharedPreferences.edit()
                .remove(KEY_USER_ID)
                .remove(KEY_RECOMMENDATIONS)
                .remove(KEY_CACHE_TIMESTAMP)
                .apply()
            
            Timber.d("Recommendation cache cleared")
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to clear recommendation cache")
        }
    }
    
    /**
     * Invalidates cache for specific user
     * Used when friend relationships change
     * 
     * @param userId User whose cache should be invalidated
     */
    suspend fun invalidateCacheForUser(userId: String) = withContext(Dispatchers.IO) {
        try {
            val cachedUserId = sharedPreferences.getString(KEY_USER_ID, null)
            if (cachedUserId == userId) {
                clearCache()
                Timber.d("Cache invalidated for user: $userId")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to invalidate cache for user: $userId")
        }
    }
}