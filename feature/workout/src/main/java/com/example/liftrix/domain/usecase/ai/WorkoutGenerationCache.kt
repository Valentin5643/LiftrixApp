package com.example.liftrix.domain.usecase.ai

import com.example.liftrix.domain.model.ai.WorkoutGenerationResult
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkoutGenerationCache @Inject constructor() {
    private val entries = ConcurrentHashMap<String, CacheEntry>()

    fun get(key: String, nowMillis: Long = System.currentTimeMillis()): WorkoutGenerationResult? {
        val entry = entries[key] ?: return null
        if (nowMillis - entry.createdAtMillis > TTL_MILLIS) {
            entries.remove(key)
            return null
        }
        return entry.result
    }

    fun put(key: String, result: WorkoutGenerationResult, nowMillis: Long = System.currentTimeMillis()) {
        evictExpired(nowMillis)
        if (entries.size >= MAX_ENTRIES && !entries.containsKey(key)) {
            val oldestKey = entries.minByOrNull { it.value.createdAtMillis }?.key
            oldestKey?.let(entries::remove)
        }
        entries[key] = CacheEntry(result, nowMillis)
    }

    fun keyFor(
        userId: String,
        normalizedPrompt: String,
        constraints: String,
        language: String,
        catalogHash: String
    ): String = sha256("$userId|$normalizedPrompt|$constraints|$language|$catalogHash")

    fun keyForOperation(
        userId: String,
        operation: String,
        normalizedPrompt: String,
        sourceId: String,
        contextHash: String,
        catalogHash: String
    ): String = sha256("$userId|$operation|$normalizedPrompt|$sourceId|$contextHash|$catalogHash")

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun evictExpired(nowMillis: Long) {
        entries.entries.removeIf { (_, entry) -> nowMillis - entry.createdAtMillis > TTL_MILLIS }
    }

    private data class CacheEntry(
        val result: WorkoutGenerationResult,
        val createdAtMillis: Long
    )

    companion object {
        const val TTL_MILLIS = 10 * 60 * 1000L
        private const val MAX_ENTRIES = 50
    }
}
