package com.example.liftrix.core.performance

import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.example.liftrix.core.data.BuildConfig

/**
 * Performance monitoring service for JSON serialization operations.
 *
 * Tracks key performance metrics for workout serialization:
 * - Serialization/deserialization times
 * - Memory allocation patterns
 * - Cache hit/miss ratios
 * - Performance regression detection
 */
@Singleton
class SerializationPerformanceMonitor @Inject constructor() {

    private val mutex = Mutex()
    private val performanceData = mutableListOf<SerializationMetric>()
    private val cacheMetrics = mutableMapOf<String, CacheMetric>()

    companion object {
        const val MAX_METRICS_HISTORY = 1000
        const val PERFORMANCE_THRESHOLD_MS = 50L // Target: <50ms
        const val CACHE_SIZE_LIMIT = 100
    }

    /**
     * Records a serialization performance metric.
     */
    suspend fun recordSerializationMetric(
        operation: SerializationOperation,
        durationMs: Long,
        dataSize: Int,
        exerciseCount: Int,
        format: String,
        success: Boolean
    ) = mutex.withLock {
        val metric = SerializationMetric(
            timestamp = System.currentTimeMillis(),
            operation = operation,
            durationMs = durationMs,
            dataSize = dataSize,
            exerciseCount = exerciseCount,
            format = format,
            success = success
        )

        performanceData.add(metric)

        // Keep only recent metrics
        if (performanceData.size > MAX_METRICS_HISTORY) {
            performanceData.removeFirst()
        }

        // Log performance warnings
        if (durationMs > PERFORMANCE_THRESHOLD_MS) {
            if (BuildConfig.DEBUG) {
                Timber.w("⚠️ PERFORMANCE: ${operation.name} took ${durationMs}ms (threshold: ${PERFORMANCE_THRESHOLD_MS}ms) for $exerciseCount exercises")
            }
        }

        // Log successful optimizations
        if (success && durationMs < PERFORMANCE_THRESHOLD_MS) {
            if (BuildConfig.DEBUG) {
                Timber.d("✅ PERFORMANCE: ${operation.name} completed in ${durationMs}ms for $exerciseCount exercises ($format)")
            }
        }
    }

    /**
     * Records cache performance metrics.
     */
    suspend fun recordCacheMetric(
        cacheKey: String,
        hit: Boolean,
        retrievalTimeMs: Long = 0L
    ) = mutex.withLock {
        val existing = cacheMetrics[cacheKey] ?: CacheMetric(
            cacheKey = cacheKey,
            hits = 0,
            misses = 0,
            totalRetrievalTimeMs = 0L,
            lastAccessTime = System.currentTimeMillis()
        )

        val updated = if (hit) {
            existing.copy(
                hits = existing.hits + 1,
                totalRetrievalTimeMs = existing.totalRetrievalTimeMs + retrievalTimeMs,
                lastAccessTime = System.currentTimeMillis()
            )
        } else {
            existing.copy(
                misses = existing.misses + 1,
                lastAccessTime = System.currentTimeMillis()
            )
        }

        cacheMetrics[cacheKey] = updated

        // Remove old cache metrics if limit exceeded
        if (cacheMetrics.size > CACHE_SIZE_LIMIT) {
            val oldestKey = cacheMetrics.minByOrNull { it.value.lastAccessTime }?.key
            oldestKey?.let { cacheMetrics.remove(it) }
        }

        if (BuildConfig.DEBUG && hit) {
            val hitRate = updated.hits.toDouble() / (updated.hits + updated.misses) * 100
            Timber.d("📊 CACHE: Hit for '$cacheKey' (${hitRate.toInt()}% hit rate)")
        }
    }

    /**
     * Gets performance summary for monitoring dashboard.
     */
    suspend fun getPerformanceSummary(): PerformanceSummary = mutex.withLock {
        val recentMetrics = performanceData.takeLast(100)

        if (recentMetrics.isEmpty()) {
            return PerformanceSummary(
                averageSerializationTimeMs = 0.0,
                averageDeserializationTimeMs = 0.0,
                successRate = 100.0,
                cacheHitRate = 0.0,
                performanceThresholdViolations = 0,
                totalOperations = 0
            )
        }

        val serializationMetrics = recentMetrics.filter { it.operation == SerializationOperation.SERIALIZE }
        val deserializationMetrics = recentMetrics.filter { it.operation == SerializationOperation.DESERIALIZE }

        val avgSerializationTime = serializationMetrics.map { it.durationMs }.average().takeIf { !it.isNaN() } ?: 0.0
        val avgDeserializationTime = deserializationMetrics.map { it.durationMs }.average().takeIf { !it.isNaN() } ?: 0.0
        val successRate = recentMetrics.count { it.success }.toDouble() / recentMetrics.size * 100
        val thresholdViolations = recentMetrics.count { it.durationMs > PERFORMANCE_THRESHOLD_MS }

        val totalCacheOperations = cacheMetrics.values.sumOf { it.hits + it.misses }
        val totalCacheHits = cacheMetrics.values.sumOf { it.hits }
        val cacheHitRate = if (totalCacheOperations > 0) {
            totalCacheHits.toDouble() / totalCacheOperations * 100
        } else 0.0

        PerformanceSummary(
            averageSerializationTimeMs = avgSerializationTime,
            averageDeserializationTimeMs = avgDeserializationTime,
            successRate = successRate,
            cacheHitRate = cacheHitRate,
            performanceThresholdViolations = thresholdViolations,
            totalOperations = recentMetrics.size
        )
    }

    /**
     * Detects performance regressions by comparing recent vs historical performance.
     */
    suspend fun detectPerformanceRegression(): PerformanceRegression? = mutex.withLock {
        if (performanceData.size < 50) return null

        val recent = performanceData.takeLast(20).filter { it.success }
        val historical = performanceData.dropLast(20).takeLast(50).filter { it.success }

        if (recent.isEmpty() || historical.isEmpty()) return null

        val recentAvg = recent.map { it.durationMs }.average()
        val historicalAvg = historical.map { it.durationMs }.average()

        val regressionPercentage = ((recentAvg - historicalAvg) / historicalAvg) * 100

        return if (regressionPercentage > 20.0) { // 20% performance regression
            PerformanceRegression(
                regressionPercentage = regressionPercentage,
                recentAverageMs = recentAvg,
                historicalAverageMs = historicalAvg,
                detectedAt = System.currentTimeMillis()
            )
        } else null
    }

    /**
     * Clears all performance metrics (for testing/reset).
     */
    suspend fun clearMetrics() = mutex.withLock {
        performanceData.clear()
        cacheMetrics.clear()
        if (BuildConfig.DEBUG) {
            Timber.d("🧹 PERFORMANCE: All metrics cleared")
        }
    }
}

/**
 * Types of serialization operations being monitored.
 */
enum class SerializationOperation {
    SERIALIZE,
    DESERIALIZE,
    VALIDATE,
    MIGRATE
}

/**
 * Individual serialization performance metric.
 */
data class SerializationMetric(
    val timestamp: Long,
    val operation: SerializationOperation,
    val durationMs: Long,
    val dataSize: Int,
    val exerciseCount: Int,
    val format: String,
    val success: Boolean
)

/**
 * Cache performance metric for tracking hit/miss ratios.
 */
data class CacheMetric(
    val cacheKey: String,
    val hits: Int,
    val misses: Int,
    val totalRetrievalTimeMs: Long,
    val lastAccessTime: Long
) {
    val hitRate: Double
        get() = if (hits + misses > 0) hits.toDouble() / (hits + misses) * 100 else 0.0

    val averageRetrievalTimeMs: Double
        get() = if (hits > 0) totalRetrievalTimeMs.toDouble() / hits else 0.0
}

/**
 * Performance summary for monitoring dashboard.
 */
data class PerformanceSummary(
    val averageSerializationTimeMs: Double,
    val averageDeserializationTimeMs: Double,
    val successRate: Double,
    val cacheHitRate: Double,
    val performanceThresholdViolations: Int,
    val totalOperations: Int
)

/**
 * Performance regression detection result.
 */
data class PerformanceRegression(
    val regressionPercentage: Double,
    val recentAverageMs: Double,
    val historicalAverageMs: Double,
    val detectedAt: Long
)
