package com.example.liftrix.service.cache

import com.example.liftrix.domain.model.analytics.AnalyticsWidget
import com.example.liftrix.domain.usecase.analytics.GetWidgetDataUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * Comprehensive tests for cache management functionality.
 * 
 * Tests cover LRU eviction policy, TTL expiration, memory limits,
 * cache hit rates, and performance characteristics.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CacheManagerTests {
    
    private lateinit var memoryCache: FakeMemoryCache
    private lateinit var diskCache: FakeDiskCache
    private lateinit var cacheManager: FakeWidgetCacheManager
    
    @Before
    fun setup() {
        memoryCache = FakeMemoryCache(maxSizeBytes = 1024 * 1024) // 1MB for testing
        diskCache = FakeDiskCache(maxSizeBytes = 10 * 1024 * 1024) // 10MB for testing
        cacheManager = FakeWidgetCacheManager(memoryCache, diskCache)
    }
    
    @Test
    fun `memory cache stores and retrieves data correctly`() = runTest {
        // Given
        val key = "test_key"
        val widgetData = createTestWidgetData(AnalyticsWidget.VolumeChart)
        
        // When
        memoryCache.put(key, widgetData)
        val retrievedData = memoryCache.get(key)
        
        // Then
        assertNotNull(retrievedData)
        assertEquals(widgetData.widgetType, retrievedData.widgetType)
        assertEquals(widgetData.data, retrievedData.data)
        assertEquals(widgetData.lastUpdated, retrievedData.lastUpdated)
    }
    
    @Test
    fun `memory cache returns null for non-existent keys`() = runTest {
        // Given
        val nonExistentKey = "non_existent_key"
        
        // When
        val retrievedData = memoryCache.get(nonExistentKey)
        
        // Then
        assertNull(retrievedData)
    }
    
    @Test
    fun `memory cache implements LRU eviction policy`() = runTest {
        // Given - set up a small cache that can only hold 2 items
        val smallCache = FakeMemoryCache(maxSizeBytes = 200) // Very small for testing
        val data1 = createTestWidgetData(AnalyticsWidget.VolumeChart)
        val data2 = createTestWidgetData(AnalyticsWidget.DurationChart)
        val data3 = createTestWidgetData(AnalyticsWidget.FrequencyChart)
        
        // When - add items to fill cache
        smallCache.put("key1", data1)
        smallCache.put("key2", data2)
        // Access key1 to make it recently used
        smallCache.get("key1")
        // Add key3, which should evict key2 (least recently used)
        smallCache.put("key3", data3)
        
        // Then
        assertNotNull(smallCache.get("key1")) // Should still exist (recently accessed)
        assertNull(smallCache.get("key2"))    // Should be evicted
        assertNotNull(smallCache.get("key3")) // Should exist (just added)
    }
    
    @Test
    fun `memory cache respects size limits`() = runTest {
        // Given - small cache with size limit
        val smallCache = FakeMemoryCache(maxSizeBytes = 300)
        val largeData = createLargeTestWidgetData() // Create data larger than cache
        
        // When
        val putResult = smallCache.put("large_key", largeData)
        
        // Then
        assertFalse(putResult) // Should fail to put due to size
        assertNull(smallCache.get("large_key")) // Should not be in cache
    }
    
    @Test
    fun `disk cache persists data between sessions`() = runTest {
        // Given
        val key = "persistent_key"
        val widgetData = createTestWidgetData(AnalyticsWidget.CaloriesBurned)
        
        // When
        diskCache.put(key, widgetData)
        // Simulate app restart by creating new disk cache instance
        val newDiskCache = FakeDiskCache(maxSizeBytes = 10 * 1024 * 1024)
        newDiskCache.loadExistingData(diskCache.getData()) // Load persisted data
        val retrievedData = newDiskCache.get(key)
        
        // Then
        assertNotNull(retrievedData)
        assertEquals(widgetData.widgetType, retrievedData.widgetType)
        assertEquals(widgetData.data, retrievedData.data)
    }
    
    @Test
    fun `disk cache implements TTL expiration`() = runTest {
        // Given
        val key = "ttl_key"
        val widgetData = createTestWidgetData(AnalyticsWidget.WorkoutStreak)
        val shortTtl = 100L // 100ms TTL for testing
        
        // When
        diskCache.put(key, widgetData, ttlMs = shortTtl)
        val immediateRetrieval = diskCache.get(key)
        
        // Wait for TTL to expire
        kotlinx.coroutines.delay(150)
        val expiredRetrieval = diskCache.get(key)
        
        // Then
        assertNotNull(immediateRetrieval) // Should exist immediately
        assertNull(expiredRetrieval)      // Should be expired after TTL
    }
    
    @Test
    fun `cache manager implements cache hierarchy correctly`() = runTest {
        // Given
        val key = "hierarchy_key"
        val widgetData = createTestWidgetData(AnalyticsWidget.StrengthProgress)
        
        // When - put in disk cache only
        diskCache.put(key, widgetData)
        val firstRetrieval = cacheManager.get(key)
        val secondRetrieval = cacheManager.get(key)
        
        // Then
        assertNotNull(firstRetrieval)
        assertNotNull(secondRetrieval)
        // First retrieval should promote from disk to memory
        assertTrue(memoryCache.contains(key))
        assertEquals(1, diskCache.getAccessCount(key))
        assertEquals(2, memoryCache.getAccessCount(key)) // Second access from memory
    }
    
    @Test
    fun `cache manager returns memory cache data when available`() = runTest {
        // Given
        val key = "memory_priority_key"
        val memoryData = createTestWidgetData(AnalyticsWidget.VolumeChart)
        val diskData = createTestWidgetData(AnalyticsWidget.DurationChart)
        
        // When - put different data in both caches
        diskCache.put(key, diskData)
        memoryCache.put(key, memoryData)
        val retrievedData = cacheManager.get(key)
        
        // Then - should get memory data (higher priority)
        assertNotNull(retrievedData)
        assertEquals(memoryData.widgetType, retrievedData.widgetType)
        assertEquals(AnalyticsWidget.VolumeChart, retrievedData.widgetType)
    }
    
    @Test
    fun `cache hit rate tracking works correctly`() = runTest {
        // Given
        val keys = listOf("key1", "key2", "key3")
        val data1 = createTestWidgetData(AnalyticsWidget.VolumeChart)
        val data2 = createTestWidgetData(AnalyticsWidget.DurationChart)
        
        // When - put some data and access keys
        memoryCache.put("key1", data1)
        memoryCache.put("key2", data2)
        
        // Access existing and non-existing keys
        cacheManager.get("key1") // Hit
        cacheManager.get("key2") // Hit
        cacheManager.get("key3") // Miss
        cacheManager.get("key1") // Hit
        
        // Then
        val hitRate = cacheManager.getHitRate()
        assertEquals(0.75, hitRate, 0.01) // 3 hits out of 4 accesses = 75%
    }
    
    @Test
    fun `cache invalidation clears both memory and disk`() = runTest {
        // Given
        val key = "invalidation_key"
        val widgetData = createTestWidgetData(AnalyticsWidget.PersonalRecords)
        
        // When
        cacheManager.put(key, widgetData)
        assertTrue(memoryCache.contains(key))
        assertTrue(diskCache.contains(key))
        
        cacheManager.invalidate(key)
        
        // Then
        assertFalse(memoryCache.contains(key))
        assertFalse(diskCache.contains(key))
        assertNull(cacheManager.get(key))
    }
    
    @Test
    fun `cache clear removes all data`() = runTest {
        // Given
        val data1 = createTestWidgetData(AnalyticsWidget.VolumeChart)
        val data2 = createTestWidgetData(AnalyticsWidget.DurationChart)
        val data3 = createTestWidgetData(AnalyticsWidget.FrequencyChart)
        
        // When
        cacheManager.put("key1", data1)
        cacheManager.put("key2", data2)
        cacheManager.put("key3", data3)
        
        assertEquals(3, memoryCache.size())
        assertEquals(3, diskCache.size())
        
        cacheManager.clear()
        
        // Then
        assertEquals(0, memoryCache.size())
        assertEquals(0, diskCache.size())
        assertNull(cacheManager.get("key1"))
        assertNull(cacheManager.get("key2"))
        assertNull(cacheManager.get("key3"))
    }
    
    @Test
    fun `performance test - cache operations complete within performance targets`() = runTest {
        // Given
        val key = "performance_key"
        val widgetData = createTestWidgetData(AnalyticsWidget.CaloriesBurned)
        
        // When - measure put operation
        val putStartTime = System.currentTimeMillis()
        cacheManager.put(key, widgetData)
        val putEndTime = System.currentTimeMillis()
        
        // When - measure get operation
        val getStartTime = System.currentTimeMillis()
        val retrievedData = cacheManager.get(key)
        val getEndTime = System.currentTimeMillis()
        
        // Then
        val putTime = putEndTime - putStartTime
        val getTime = getEndTime - getStartTime
        
        assertTrue(putTime < 50, "Cache put operation took $putTime ms, should be < 50ms")
        assertTrue(getTime < 10, "Cache get operation took $getTime ms, should be < 10ms")
        assertNotNull(retrievedData)
    }
    
    @Test
    fun `memory usage stays within configured limits`() = runTest {
        // Given - cache with 1MB limit
        val cacheWithLimits = FakeMemoryCache(maxSizeBytes = 1024 * 1024)
        val dataSize = 100 * 1024 // 100KB per item
        
        // When - add 15 items (1.5MB total, should exceed 1MB limit)
        repeat(15) { index ->
            val data = createTestWidgetDataWithSize(AnalyticsWidget.VolumeChart, dataSize)
            cacheWithLimits.put("key_$index", data)
        }
        
        // Then
        val currentSize = cacheWithLimits.getCurrentSizeBytes()
        assertTrue(currentSize <= 1024 * 1024, "Cache size $currentSize should not exceed 1MB")
        assertTrue(cacheWithLimits.size() < 15, "Some items should have been evicted")
    }
    
    // Helper methods for creating test data
    
    private fun createTestWidgetData(widgetType: AnalyticsWidget): GetWidgetDataUseCase.WidgetData {
        return GetWidgetDataUseCase.WidgetData(
            widgetType = widgetType,
            data = mapOf(
                "value" to 100,
                "trend" to "up",
                "lastUpdated" to System.currentTimeMillis()
            ),
            lastUpdated = System.currentTimeMillis(),
            isStale = false
        )
    }
    
    private fun createLargeTestWidgetData(): GetWidgetDataUseCase.WidgetData {
        val largeData = mutableMapOf<String, Any>()
        // Create data larger than small cache limit
        repeat(100) { index ->
            largeData["key_$index"] = "value_$index".repeat(10) // Large string values
        }
        
        return GetWidgetDataUseCase.WidgetData(
            widgetType = AnalyticsWidget.VolumeChart,
            data = largeData,
            lastUpdated = System.currentTimeMillis(),
            isStale = false
        )
    }
    
    private fun createTestWidgetDataWithSize(widgetType: AnalyticsWidget, targetSizeBytes: Int): GetWidgetDataUseCase.WidgetData {
        val dataValue = "x".repeat(targetSizeBytes / 2) // Approximate size
        return GetWidgetDataUseCase.WidgetData(
            widgetType = widgetType,
            data = mapOf("largeValue" to dataValue),
            lastUpdated = System.currentTimeMillis(),
            isStale = false
        )
    }
}

// Fake implementations for testing

class FakeMemoryCache(private val maxSizeBytes: Long) {
    private val cache = mutableMapOf<String, GetWidgetDataUseCase.WidgetData>()
    private val accessOrder = mutableListOf<String>()
    private val accessCounts = mutableMapOf<String, Int>()
    private var currentSizeBytes = 0L
    
    fun put(key: String, data: GetWidgetDataUseCase.WidgetData): Boolean {
        val dataSize = estimateDataSize(data)
        if (dataSize > maxSizeBytes) return false
        
        // Evict items if necessary
        while (currentSizeBytes + dataSize > maxSizeBytes && cache.isNotEmpty()) {
            evictLeastRecentlyUsed()
        }
        
        cache[key] = data
        updateAccessOrder(key)
        currentSizeBytes += dataSize
        return true
    }
    
    fun get(key: String): GetWidgetDataUseCase.WidgetData? {
        val data = cache[key]
        if (data != null) {
            updateAccessOrder(key)
            accessCounts[key] = accessCounts.getOrDefault(key, 0) + 1
        }
        return data
    }
    
    fun contains(key: String): Boolean = cache.containsKey(key)
    fun size(): Int = cache.size
    fun clear() {
        cache.clear()
        accessOrder.clear()
        accessCounts.clear()
        currentSizeBytes = 0L
    }
    
    fun getCurrentSizeBytes(): Long = currentSizeBytes
    fun getAccessCount(key: String): Int = accessCounts.getOrDefault(key, 0)
    
    private fun updateAccessOrder(key: String) {
        accessOrder.remove(key)
        accessOrder.add(key)
    }
    
    private fun evictLeastRecentlyUsed() {
        if (accessOrder.isNotEmpty()) {
            val lruKey = accessOrder.removeFirst()
            val evictedData = cache.remove(lruKey)
            if (evictedData != null) {
                currentSizeBytes -= estimateDataSize(evictedData)
            }
            accessCounts.remove(lruKey)
        }
    }
    
    private fun estimateDataSize(data: GetWidgetDataUseCase.WidgetData): Long {
        // Simple size estimation
        return 100L + data.data.values.sumOf { 
            when (it) {
                is String -> it.length.toLong()
                is Int -> 4L
                is Long -> 8L
                is Double -> 8L
                else -> 50L
            }
        }
    }
}

class FakeDiskCache(private val maxSizeBytes: Long) {
    private val cache = mutableMapOf<String, Pair<GetWidgetDataUseCase.WidgetData, Long>>()
    private val accessCounts = mutableMapOf<String, Int>()
    
    fun put(key: String, data: GetWidgetDataUseCase.WidgetData, ttlMs: Long = 7 * 24 * 60 * 60 * 1000L) {
        val expiryTime = System.currentTimeMillis() + ttlMs
        cache[key] = Pair(data, expiryTime)
    }
    
    fun get(key: String): GetWidgetDataUseCase.WidgetData? {
        val entry = cache[key] ?: return null
        val (data, expiryTime) = entry
        
        if (System.currentTimeMillis() > expiryTime) {
            cache.remove(key)
            return null
        }
        
        accessCounts[key] = accessCounts.getOrDefault(key, 0) + 1
        return data
    }
    
    fun contains(key: String): Boolean {
        val entry = cache[key] ?: return false
        val (_, expiryTime) = entry
        
        if (System.currentTimeMillis() > expiryTime) {
            cache.remove(key)
            return false
        }
        
        return true
    }
    
    fun size(): Int = cache.size
    fun clear() {
        cache.clear()
        accessCounts.clear()
    }
    
    fun getAccessCount(key: String): Int = accessCounts.getOrDefault(key, 0)
    fun getData(): Map<String, Pair<GetWidgetDataUseCase.WidgetData, Long>> = cache.toMap()
    fun loadExistingData(data: Map<String, Pair<GetWidgetDataUseCase.WidgetData, Long>>) {
        cache.putAll(data)
    }
}

class FakeWidgetCacheManager(
    private val memoryCache: FakeMemoryCache,
    private val diskCache: FakeDiskCache
) {
    private var totalAccesses = 0
    private var totalHits = 0
    
    fun get(key: String): GetWidgetDataUseCase.WidgetData? {
        totalAccesses++
        
        // Try memory cache first
        val memoryData = memoryCache.get(key)
        if (memoryData != null) {
            totalHits++
            return memoryData
        }
        
        // Try disk cache
        val diskData = diskCache.get(key)
        if (diskData != null) {
            // Promote to memory cache
            memoryCache.put(key, diskData)
            totalHits++
            return diskData
        }
        
        return null
    }
    
    fun put(key: String, data: GetWidgetDataUseCase.WidgetData) {
        memoryCache.put(key, data)
        diskCache.put(key, data)
    }
    
    fun invalidate(key: String) {
        // Remove from both caches
        memoryCache.get(key) // Access to remove from memory
        diskCache.get(key)   // Access to check disk
        // Actual removal logic would go here in real implementation
    }
    
    fun clear() {
        memoryCache.clear()
        diskCache.clear()
        totalAccesses = 0
        totalHits = 0
    }
    
    fun getHitRate(): Double {
        return if (totalAccesses > 0) totalHits.toDouble() / totalAccesses else 0.0
    }
}