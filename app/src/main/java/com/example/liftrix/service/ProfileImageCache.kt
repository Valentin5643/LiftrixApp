package com.example.liftrix.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.collection.LruCache
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Multi-level caching service for profile images with LRU eviction policy.
 * 
 * Caching Strategy:
 * 1. Memory Cache (L1) - LRU cache for immediate access, sized at 1/8 of available heap
 * 2. Disk Cache (L2) - Persistent storage in app's cache directory
 * 3. Network Load (L3) - Fallback to download from Firebase Storage URL
 * 
 * Performance Targets:
 * - Memory cache hit: <5ms access time
 * - Disk cache hit: <50ms access time
 * - Network fallback: <2s load time
 * - 90%+ cache hit rate for repeated profile loads
 * 
 * Cache Management:
 * - Automatic cleanup of expired/corrupted cache entries
 * - Memory pressure handling via LRU eviction
 * - Disk space management with configurable size limits
 */
@Singleton
class ProfileImageCache @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    companion object {
        private const val CACHE_DIR_NAME = "profile_images"
        private const val MAX_DISK_CACHE_SIZE_MB = 50L // 50MB disk cache limit
        private const val DISK_CACHE_MAX_AGE_DAYS = 30L // 30 days cache retention
        private const val MEMORY_CACHE_SIZE_FRACTION = 8 // 1/8 of available heap
        private const val IMAGE_QUALITY_CACHE = 90 // High quality for cached images
    }
    
    // Memory cache with LRU eviction policy
    private val memoryCache: LruCache<String, Bitmap> by lazy {
        val maxMemory = Runtime.getRuntime().maxMemory()
        val cacheSize = (maxMemory / MEMORY_CACHE_SIZE_FRACTION).toInt()
        
        Timber.d("📱 Initializing memory cache with size: ${cacheSize / 1024 / 1024}MB")
        
        object : LruCache<String, Bitmap>(cacheSize) {
            override fun sizeOf(key: String, bitmap: Bitmap): Int {
                return bitmap.byteCount
            }
            
            override fun entryRemoved(evicted: Boolean, key: String, oldValue: Bitmap, newValue: Bitmap?) {
                if (evicted) {
                    Timber.v("🧹 Memory cache evicted entry: $key")
                }
            }
        }
    }
    
    // Disk cache directory
    private val diskCacheDir: File by lazy {
        File(context.cacheDir, CACHE_DIR_NAME).apply {
            if (!exists()) {
                mkdirs()
                Timber.d("📁 Created disk cache directory: $absolutePath")
            }
        }
    }
    
    /**
     * Loads a profile image with multi-level caching strategy.
     * 
     * @param imageUrl Firebase Storage URL or other image URL
     * @param userId User ID for cache key scoping (optional for better organization)
     * @return Bitmap if successfully loaded, null if all cache levels fail
     */
    suspend fun loadImage(imageUrl: String, userId: String? = null): Bitmap? {
        return withContext(Dispatchers.IO) {
            try {
                val cacheKey = generateCacheKey(imageUrl, userId)
                
                // L1: Check memory cache first
                memoryCache.get(cacheKey)?.let { bitmap ->
                    Timber.v("💾 Memory cache hit for: $cacheKey")
                    return@withContext bitmap
                }
                
                // L2: Check disk cache
                loadFromDiskCache(cacheKey)?.let { bitmap ->
                    Timber.v("💿 Disk cache hit for: $cacheKey")
                    // Promote to memory cache
                    memoryCache.put(cacheKey, bitmap)
                    return@withContext bitmap
                }
                
                // L3: Load from network and cache
                loadFromNetwork(imageUrl)?.let { bitmap ->
                    Timber.d("🌐 Network load successful for: $cacheKey")
                    // Cache in both memory and disk
                    memoryCache.put(cacheKey, bitmap)
                    saveToDiskCache(cacheKey, bitmap)
                    return@withContext bitmap
                }
                
                Timber.w("❌ Failed to load image from all cache levels: $imageUrl")
                null
                
            } catch (e: Exception) {
                Timber.e(e, "Exception while loading image: $imageUrl")
                null
            }
        }
    }
    
    /**
     * Preloads an image into cache without returning it.
     * Useful for warming cache before UI display.
     */
    suspend fun preloadImage(imageUrl: String, userId: String? = null) {
        loadImage(imageUrl, userId)
    }
    
    /**
     * Invalidates cache entry for specific image URL.
     * Call when image is updated to ensure fresh load.
     */
    suspend fun invalidateImage(imageUrl: String, userId: String? = null) {
        val cacheKey = generateCacheKey(imageUrl, userId)
        
        // Remove from memory cache
        memoryCache.remove(cacheKey)
        
        // Remove from disk cache
        withContext(Dispatchers.IO) {
            val diskFile = File(diskCacheDir, cacheKey)
            if (diskFile.exists()) {
                diskFile.delete()
                Timber.d("🗑️ Invalidated disk cache for: $cacheKey")
            }
        }
    }
    
    /**
     * Clears all cached images to free space.
     * Use when user logs out or on low memory situations.
     */
    suspend fun clearCache() {
        // Clear memory cache
        memoryCache.evictAll()
        
        // Clear disk cache
        withContext(Dispatchers.IO) {
            diskCacheDir.listFiles()?.forEach { file ->
                file.delete()
            }
            Timber.i("🧹 Profile image cache cleared completely")
        }
    }
    
    /**
     * Performs cache maintenance: removes expired entries and enforces size limits.
     * Should be called periodically by app lifecycle or WorkManager.
     */
    suspend fun performMaintenance() {
        withContext(Dispatchers.IO) {
            try {
                val files = diskCacheDir.listFiles() ?: return@withContext
                var totalSize = 0L
                val currentTime = System.currentTimeMillis()
                val maxAge = DISK_CACHE_MAX_AGE_DAYS * 24 * 60 * 60 * 1000 // Convert to ms
                
                // Remove expired files and calculate total size
                files.forEach { file ->
                    if (currentTime - file.lastModified() > maxAge) {
                        file.delete()
                        Timber.v("🧹 Removed expired cache file: ${file.name}")
                    } else {
                        totalSize += file.length()
                    }
                }
                
                // Enforce size limit by removing oldest files
                if (totalSize > MAX_DISK_CACHE_SIZE_MB * 1024 * 1024) {
                    val remainingFiles = diskCacheDir.listFiles()
                        ?.sortedBy { it.lastModified() } // Oldest first
                        ?: return@withContext
                    
                    var currentSize = totalSize
                    for (file in remainingFiles) {
                        if (currentSize <= MAX_DISK_CACHE_SIZE_MB * 1024 * 1024) break
                        
                        currentSize -= file.length()
                        file.delete()
                        Timber.v("🧹 Removed oversized cache file: ${file.name}")
                    }
                }
                
                Timber.d("✅ Cache maintenance completed, final size: ${currentSize / 1024 / 1024}MB")
                
            } catch (e: Exception) {
                Timber.e(e, "Failed to perform cache maintenance")
            }
        }
    }
    
    /**
     * Gets current cache statistics for monitoring.
     */
    suspend fun getCacheStats(): CacheStats {
        return withContext(Dispatchers.IO) {
            val memorySize = memoryCache.size()
            val memoryHitCount = memoryCache.hitCount()
            val memoryMissCount = memoryCache.missCount()
            val memoryHitRate = if (memoryHitCount + memoryMissCount > 0) {
                memoryHitCount.toFloat() / (memoryHitCount + memoryMissCount)
            } else 0f
            
            val diskFiles = diskCacheDir.listFiles()?.size ?: 0
            val diskSizeBytes = diskCacheDir.listFiles()?.sumOf { it.length() } ?: 0L
            
            CacheStats(
                memoryEntriesCount = memoryCache.size(),
                memoryHitRate = memoryHitRate,
                diskEntriesCount = diskFiles,
                diskSizeBytes = diskSizeBytes
            )
        }
    }
    
    /**
     * Generates consistent cache key from URL and optional user ID.
     */
    private fun generateCacheKey(imageUrl: String, userId: String?): String {
        val baseKey = imageUrl.hashCode().toString()
        return if (userId != null) "${userId}_$baseKey" else baseKey
    }
    
    /**
     * Loads bitmap from disk cache if available and valid.
     */
    private suspend fun loadFromDiskCache(cacheKey: String): Bitmap? {
        return try {
            val cacheFile = File(diskCacheDir, cacheKey)
            if (cacheFile.exists() && cacheFile.canRead()) {
                FileInputStream(cacheFile).use { inputStream ->
                    BitmapFactory.decodeStream(inputStream)
                }
            } else null
        } catch (e: Exception) {
            Timber.w(e, "Failed to load from disk cache: $cacheKey")
            null
        }
    }
    
    /**
     * Saves bitmap to disk cache with error handling.
     */
    private suspend fun saveToDiskCache(cacheKey: String, bitmap: Bitmap) {
        try {
            val cacheFile = File(diskCacheDir, cacheKey)
            FileOutputStream(cacheFile).use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, IMAGE_QUALITY_CACHE, outputStream)
            }
            Timber.v("💿 Saved to disk cache: $cacheKey")
        } catch (e: Exception) {
            Timber.w(e, "Failed to save to disk cache: $cacheKey")
        }
    }
    
    /**
     * Downloads image from network URL and decodes to bitmap.
     */
    private suspend fun loadFromNetwork(imageUrl: String): Bitmap? {
        return try {
            val url = URL(imageUrl)
            val connection = url.openConnection()
            connection.doInput = true
            connection.connect()
            
            connection.getInputStream().use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            }
        } catch (e: IOException) {
            Timber.w(e, "Network load failed for URL: $imageUrl")
            null
        } catch (e: Exception) {
            Timber.e(e, "Unexpected error loading from network: $imageUrl")
            null
        }
    }
}

/**
 * Data class containing cache performance metrics.
 */
data class CacheStats(
    val memoryEntriesCount: Int,
    val memoryHitRate: Float,
    val diskEntriesCount: Int,
    val diskSizeBytes: Long
) {
    val diskSizeMB: Float get() = diskSizeBytes / 1024f / 1024f
    val memoryHitRatePercent: Float get() = memoryHitRate * 100f
}