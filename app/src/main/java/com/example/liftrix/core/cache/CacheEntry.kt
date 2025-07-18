package com.example.liftrix.core.cache

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Immutable cache entry with metadata and expiration support.
 * 
 * This data class represents a single cache entry containing:
 * - The cached data of any type
 * - Timestamp when the entry was created
 * - Time-to-live (TTL) duration
 * - Utility methods for expiration checking
 * - Metadata for cache management
 * 
 * Key Features:
 * - Immutable data structure for thread safety
 * - Generic type parameter for storing any data type
 * - Automatic expiration checking with current time
 * - Extensible metadata system for cache analytics
 * - Consistent time handling using kotlinx.datetime
 * 
 * Usage:
 * ```
 * // Create cache entry with default TTL
 * val entry = CacheEntry(
 *     data = myData,
 *     timestamp = Clock.System.now(),
 *     ttl = 15.minutes
 * )
 * 
 * // Check if entry is expired
 * if (!entry.isExpired()) {
 *     // Use cached data
 *     val data = entry.data
 * }
 * 
 * // Get remaining time to live
 * val remainingTime = entry.remainingTtl()
 * ```
 * 
 * @param T The type of data stored in this cache entry
 * @param data The cached data
 * @param timestamp When this entry was created
 * @param ttl How long this entry should remain valid
 */
data class CacheEntry<T>(
    val data: T,
    val timestamp: Instant,
    val ttl: Duration
) {
    
    /**
     * The absolute time when this entry expires.
     */
    val expiresAt: Instant = timestamp + ttl
    
    /**
     * Checks if this cache entry has expired.
     * 
     * @param currentTime The current time to check against (defaults to system time)
     * @return true if the entry has expired, false otherwise
     */
    fun isExpired(currentTime: Instant = Clock.System.now()): Boolean {
        return currentTime >= expiresAt
    }
    
    /**
     * Checks if this cache entry is still valid (not expired).
     * 
     * @param currentTime The current time to check against (defaults to system time)
     * @return true if the entry is still valid, false otherwise
     */
    fun isValid(currentTime: Instant = Clock.System.now()): Boolean {
        return !isExpired(currentTime)
    }
    
    /**
     * Returns the remaining time-to-live for this entry.
     * 
     * @param currentTime The current time to check against (defaults to system time)
     * @return Duration remaining until expiration, or Duration.ZERO if expired
     */
    fun remainingTtl(currentTime: Instant = Clock.System.now()): Duration {
        if (isExpired(currentTime)) {
            return Duration.ZERO
        }
        return expiresAt - currentTime
    }
    
    /**
     * Returns the age of this cache entry.
     * 
     * @param currentTime The current time to check against (defaults to system time)
     * @return Duration since this entry was created
     */
    fun age(currentTime: Instant = Clock.System.now()): Duration {
        return currentTime - timestamp
    }
    
    /**
     * Returns the percentage of TTL that has elapsed.
     * 
     * @param currentTime The current time to check against (defaults to system time)
     * @return Double between 0.0 and 1.0 representing TTL consumption (1.0 = fully expired)
     */
    fun ttlConsumption(currentTime: Instant = Clock.System.now()): Double {
        val elapsed = age(currentTime)
        return if (ttl.inWholeMilliseconds > 0) {
            (elapsed.inWholeMilliseconds.toDouble() / ttl.inWholeMilliseconds.toDouble()).coerceIn(0.0, 1.0)
        } else {
            1.0
        }
    }
    
    /**
     * Creates a copy of this entry with the same data but refreshed timestamp.
     * 
     * @param newTimestamp The new timestamp to use (defaults to current time)
     * @param newTtl The new TTL to use (defaults to original TTL)
     * @return A new CacheEntry with updated metadata
     */
    fun refresh(
        newTimestamp: Instant = Clock.System.now(),
        newTtl: Duration = ttl
    ): CacheEntry<T> {
        return CacheEntry(
            data = data,
            timestamp = newTimestamp,
            ttl = newTtl
        )
    }
    
    /**
     * Creates a copy of this entry with new data but same metadata.
     * 
     * @param newData The new data to store
     * @return A new CacheEntry with updated data
     */
    fun withData(newData: T): CacheEntry<T> {
        return CacheEntry(
            data = newData,
            timestamp = timestamp,
            ttl = ttl
        )
    }
    
    /**
     * Creates a copy of this entry with extended TTL.
     * 
     * @param additionalTtl The additional time to add to current TTL
     * @return A new CacheEntry with extended expiration
     */
    fun extendTtl(additionalTtl: Duration): CacheEntry<T> {
        return CacheEntry(
            data = data,
            timestamp = timestamp,
            ttl = ttl + additionalTtl
        )
    }
    
    /**
     * Maps the data in this cache entry to a new type.
     * 
     * @param transform Function to transform the data
     * @return A new CacheEntry with transformed data
     */
    fun <R> map(transform: (T) -> R): CacheEntry<R> {
        return CacheEntry(
            data = transform(data),
            timestamp = timestamp,
            ttl = ttl
        )
    }
    
    /**
     * Returns cache entry metadata for debugging and monitoring.
     * 
     * @return CacheEntryMetadata containing entry statistics
     */
    fun getMetadata(): CacheEntryMetadata {
        val currentTime = Clock.System.now()
        return CacheEntryMetadata(
            createdAt = timestamp,
            expiresAt = expiresAt,
            ttl = ttl,
            age = age(currentTime),
            remainingTtl = remainingTtl(currentTime),
            isExpired = isExpired(currentTime),
            ttlConsumption = ttlConsumption(currentTime)
        )
    }
    
    /**
     * Returns a string representation of this cache entry for debugging.
     */
    override fun toString(): String {
        return "CacheEntry(data=${data?.javaClass?.simpleName}, timestamp=$timestamp, ttl=$ttl, expired=${isExpired()})"
    }
}

/**
 * Metadata about a cache entry for monitoring and debugging.
 */
data class CacheEntryMetadata(
    val createdAt: Instant,
    val expiresAt: Instant,
    val ttl: Duration,
    val age: Duration,
    val remainingTtl: Duration,
    val isExpired: Boolean,
    val ttlConsumption: Double
)

/**
 * Factory object for creating cache entries with common configurations.
 */
object CacheEntryFactory {
    
    /**
     * Creates a cache entry with short TTL (5 minutes).
     */
    fun <T> createShortLived(data: T): CacheEntry<T> {
        return CacheEntry(
            data = data,
            timestamp = Clock.System.now(),
            ttl = 5.minutes
        )
    }
    
    /**
     * Creates a cache entry with medium TTL (15 minutes).
     */
    fun <T> createMediumLived(data: T): CacheEntry<T> {
        return CacheEntry(
            data = data,
            timestamp = Clock.System.now(),
            ttl = 15.minutes
        )
    }
    
    /**
     * Creates a cache entry with long TTL (1 hour).
     */
    fun <T> createLongLived(data: T): CacheEntry<T> {
        return CacheEntry(
            data = data,
            timestamp = Clock.System.now(),
            ttl = 60.minutes
        )
    }
    
    /**
     * Creates a cache entry with custom TTL.
     */
    fun <T> createCustom(data: T, ttl: Duration): CacheEntry<T> {
        return CacheEntry(
            data = data,
            timestamp = Clock.System.now(),
            ttl = ttl
        )
    }
    
    /**
     * Creates a cache entry that never expires (use with caution).
     */
    fun <T> createPermanent(data: T): CacheEntry<T> {
        return CacheEntry(
            data = data,
            timestamp = Clock.System.now(),
            ttl = Duration.INFINITE
        )
    }
}

/**
 * Extension functions for working with cache entries.
 */

/**
 * Extension function to create a cache entry from any data.
 */
fun <T> T.toCacheEntry(ttl: Duration = 15.minutes): CacheEntry<T> {
    return CacheEntry(
        data = this,
        timestamp = Clock.System.now(),
        ttl = ttl
    )
}

/**
 * Extension function to check if a nullable cache entry is valid.
 */
fun <T> CacheEntry<T>?.isValidOrNull(): Boolean {
    return this?.isValid() ?: false
}

/**
 * Extension function to get data from a cache entry only if it's valid.
 */
fun <T> CacheEntry<T>?.getDataIfValid(): T? {
    return if (this?.isValid() == true) data else null
}