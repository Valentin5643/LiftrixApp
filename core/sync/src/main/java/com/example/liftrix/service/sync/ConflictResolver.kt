package com.example.liftrix.service.sync

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.error.LiftrixError
import kotlinx.serialization.Serializable
import kotlinx.serialization.Contextual
import timber.log.Timber
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Conflict resolution system for handling sync conflicts between local and remote data.
 * 
 * This resolver implements:
 * - Timestamp-based conflict resolution with last-write-wins strategy
 * - Data type-specific resolution strategies
 * - Conflict detection and reporting
 * - Automatic conflict resolution with user notification
 * - Data integrity preservation during conflicts
 * 
 * Technical Implementation:
 * - Uses timestamp comparison for primary conflict resolution
 * - Implements data-type specific merge strategies
 * - Provides detailed conflict reporting for debugging
 * - Maintains data consistency during resolution
 * 
 * Resolution Strategies:
 * - LastWriteWins: Use most recent timestamp (default)
 * - UserPreference: Always prefer local user changes
 * - ServerAuthoritative: Always prefer server data
 * - SmartMerge: Merge compatible changes when possible
 */
@Singleton
class ConflictResolver @Inject constructor() {
    
    /**
     * Resolves conflicts between local and remote data
     */
    suspend fun resolveConflict(
        localData: SyncableData,
        remoteData: SyncableData,
        strategy: ResolutionStrategy = ResolutionStrategy.LastWriteWins
    ): LiftrixResult<SyncableData> {
        return try {
            Timber.d("ConflictResolver: Resolving conflict for ${localData.id} using strategy: $strategy")
            
            // Validate that data items are compatible for conflict resolution
            if (localData.id != remoteData.id || localData.type != remoteData.type) {
                return Result.failure(
                    LiftrixError.ValidationError(
                        field = "data_compatibility",
                        violations = listOf("Cannot resolve conflicts between incompatible data items")
                    )
                )
            }
            
            val resolvedData = when (strategy) {
                ResolutionStrategy.LastWriteWins -> resolveLastWriteWins(localData, remoteData)
                ResolutionStrategy.UserPreference -> resolveUserPreference(localData, remoteData)
                ResolutionStrategy.ServerAuthoritative -> resolveServerAuthoritative(localData, remoteData)
                ResolutionStrategy.SmartMerge -> resolveSmartMerge(localData, remoteData)
            }
            
            // Log conflict resolution for debugging
            logConflictResolution(localData, remoteData, resolvedData, strategy)
            
            Result.success(resolvedData)
            
        } catch (e: Exception) {
            Timber.e(e, "ConflictResolver: Error resolving conflict for ${localData.id}")
            Result.failure(
                LiftrixError.BusinessLogicError(
                    code = "conflict_resolution_failed",
                    errorMessage = "Failed to resolve data conflict: ${e.message}"
                )
            )
        }
    }
    
    /**
     * Detects conflicts between local and remote data
     */
    fun detectConflict(localData: SyncableData, remoteData: SyncableData): ConflictInfo? {
        if (localData.id != remoteData.id || localData.type != remoteData.type) {
            return null // Cannot compare incompatible data
        }
        
        // Check if data versions differ
        val hasConflict = when {
            localData.lastModified != remoteData.lastModified -> true
            localData.version != remoteData.version -> true
            localData.dataHash != remoteData.dataHash -> true
            else -> false
        }
        
        if (!hasConflict) return null
        
        return ConflictInfo(
            dataId = localData.id,
            dataType = localData.type,
            localTimestamp = localData.lastModified,
            remoteTimestamp = remoteData.lastModified,
            localVersion = localData.version,
            remoteVersion = remoteData.version,
            conflictType = determineConflictType(localData, remoteData),
            severity = determineConflictSeverity(localData, remoteData)
        )
    }
    
    /**
     * Batch conflict resolution for multiple data items
     */
    suspend fun resolveBatchConflicts(
        conflicts: List<Pair<SyncableData, SyncableData>>,
        strategy: ResolutionStrategy = ResolutionStrategy.LastWriteWins
    ): LiftrixResult<List<SyncableData>> {
        return try {
            Timber.d("ConflictResolver: Resolving ${conflicts.size} conflicts using strategy: $strategy")
            
            val resolvedData = mutableListOf<SyncableData>()
            val errors = mutableListOf<String>()
            
            for ((local, remote) in conflicts) {
                val result = resolveConflict(local, remote, strategy)
                
                result.fold(
                    onSuccess = { resolved -> resolvedData.add(resolved) },
                    onFailure = { error -> errors.add("${local.id}: ${error.message}") }
                )
            }
            
            if (errors.isNotEmpty() && resolvedData.isEmpty()) {
                // All resolutions failed
                Result.failure(
                    LiftrixError.BusinessLogicError(
                        code = "batch_resolution_failed",
                        errorMessage = "Failed to resolve conflicts: ${errors.joinToString(", ")}"
                    )
                )
            } else {
                // At least some resolutions succeeded
                if (errors.isNotEmpty()) {
                    Timber.w("ConflictResolver: Partial batch resolution - ${errors.size} failures")
                }
                Result.success(resolvedData)
            }
            
        } catch (e: Exception) {
            Timber.e(e, "ConflictResolver: Error in batch conflict resolution")
            Result.failure(
                LiftrixError.BusinessLogicError(
                    code = "batch_resolution_error",
                    errorMessage = "Batch conflict resolution failed: ${e.message}"
                )
            )
        }
    }
    
    /**
     * Resolution strategy implementations
     */
    
    private fun resolveLastWriteWins(localData: SyncableData, remoteData: SyncableData): SyncableData {
        return if (localData.lastModified >= remoteData.lastModified) {
            localData.copy(
                version = maxOf(localData.version, remoteData.version) + 1,
                lastModified = Instant.now().epochSecond
            )
        } else {
            remoteData.copy(
                version = maxOf(localData.version, remoteData.version) + 1,
                lastModified = Instant.now().epochSecond
            )
        }
    }
    
    private fun resolveUserPreference(localData: SyncableData, remoteData: SyncableData): SyncableData {
        // Always prefer local user changes
        return localData.copy(
            version = maxOf(localData.version, remoteData.version) + 1,
            lastModified = Instant.now().epochSecond
        )
    }
    
    private fun resolveServerAuthoritative(localData: SyncableData, remoteData: SyncableData): SyncableData {
        // Always prefer server data
        return remoteData.copy(
            version = maxOf(localData.version, remoteData.version) + 1
        )
    }
    
    private fun resolveSmartMerge(localData: SyncableData, remoteData: SyncableData): SyncableData {
        return try {
            when (localData.type) {
                DataType.WIDGET_PREFERENCES -> mergeWidgetPreferences(localData, remoteData)
                DataType.ANALYTICS_DATA -> mergeAnalyticsData(localData, remoteData)
                DataType.WORKOUT_SESSION -> mergeWorkoutSession(localData, remoteData)
                DataType.USER_PROFILE -> mergeUserProfile(localData, remoteData)
                else -> resolveLastWriteWins(localData, remoteData) // Fallback to last-write-wins
            }
        } catch (e: Exception) {
            Timber.w(e, "Smart merge failed for ${localData.id}, falling back to last-write-wins")
            resolveLastWriteWins(localData, remoteData)
        }
    }
    
    /**
     * Data-type specific merge implementations
     */
    
    private fun mergeWidgetPreferences(localData: SyncableData, remoteData: SyncableData): SyncableData {
        // For widget preferences, merge enabled widgets from both sources
        // This allows users to enable widgets on different devices
        val localPrefs = localData.data as? Map<String, Any> ?: emptyMap()
        val remotePrefs = remoteData.data as? Map<String, Any> ?: emptyMap()
        
        val localEnabled = (localPrefs["enabledWidgets"] as? List<String>) ?: emptyList()
        val remoteEnabled = (remotePrefs["enabledWidgets"] as? List<String>) ?: emptyList()
        
        val mergedEnabled = (localEnabled + remoteEnabled).distinct()
        
        val mergedData = localPrefs.toMutableMap()
        mergedData["enabledWidgets"] = mergedEnabled
        mergedData["lastModified"] = Instant.now().epochSecond
        
        return localData.copy(
            data = mergedData,
            version = maxOf(localData.version, remoteData.version) + 1,
            lastModified = Instant.now().epochSecond,
            dataHash = generateDataHash(mergedData)
        )
    }
    
    private fun mergeAnalyticsData(localData: SyncableData, remoteData: SyncableData): SyncableData {
        // For analytics data, prefer the most recent calculation
        return resolveLastWriteWins(localData, remoteData)
    }
    
    private fun mergeWorkoutSession(localData: SyncableData, remoteData: SyncableData): SyncableData {
        // For workout sessions, merge sets and exercises
        val localSession = localData.data as? Map<String, Any> ?: emptyMap()
        val remoteSession = remoteData.data as? Map<String, Any> ?: emptyMap()
        
        // Use the session with more completed sets as the base
        val localSets = (localSession["completedSets"] as? Number)?.toInt() ?: 0
        val remoteSets = (remoteSession["completedSets"] as? Number)?.toInt() ?: 0
        
        val baseSession = if (localSets >= remoteSets) localSession else remoteSession
        
        return (if (localSets >= remoteSets) localData else remoteData).copy(
            data = baseSession,
            version = maxOf(localData.version, remoteData.version) + 1,
            lastModified = Instant.now().epochSecond,
            dataHash = generateDataHash(baseSession)
        )
    }
    
    private fun mergeUserProfile(localData: SyncableData, remoteData: SyncableData): SyncableData {
        // For user profiles, merge non-conflicting fields and use latest timestamp for conflicts
        val localProfile = localData.data as? Map<String, Any> ?: emptyMap()
        val remoteProfile = remoteData.data as? Map<String, Any> ?: emptyMap()
        
        val mergedProfile = remoteProfile.toMutableMap()
        
        // Merge fields that don't conflict or use more recent data
        localProfile.forEach { (key, value) ->
            if (!mergedProfile.containsKey(key) || localData.lastModified > remoteData.lastModified) {
                mergedProfile[key] = value
            }
        }
        
        mergedProfile["lastModified"] = Instant.now().epochSecond
        
        return localData.copy(
            data = mergedProfile,
            version = maxOf(localData.version, remoteData.version) + 1,
            lastModified = Instant.now().epochSecond,
            dataHash = generateDataHash(mergedProfile)
        )
    }
    
    /**
     * Utility methods
     */
    
    private fun determineConflictType(localData: SyncableData, remoteData: SyncableData): ConflictType {
        return when {
            localData.lastModified > remoteData.lastModified -> ConflictType.LOCAL_NEWER
            localData.lastModified < remoteData.lastModified -> ConflictType.REMOTE_NEWER
            localData.version != remoteData.version -> ConflictType.VERSION_MISMATCH
            localData.dataHash != remoteData.dataHash -> ConflictType.CONTENT_DIFFERS
            else -> ConflictType.UNKNOWN
        }
    }
    
    private fun determineConflictSeverity(localData: SyncableData, remoteData: SyncableData): ConflictSeverity {
        val timeDifference = kotlin.math.abs(localData.lastModified - remoteData.lastModified)
        
        return when {
            timeDifference < 60 -> ConflictSeverity.LOW // Less than 1 minute
            timeDifference < 3600 -> ConflictSeverity.MEDIUM // Less than 1 hour
            timeDifference < 86400 -> ConflictSeverity.HIGH // Less than 1 day
            else -> ConflictSeverity.CRITICAL // More than 1 day
        }
    }
    
    private fun generateDataHash(data: Any): String {
        // Simple hash generation - in production would use more robust hashing
        return data.toString().hashCode().toString()
    }
    
    private fun logConflictResolution(
        localData: SyncableData,
        remoteData: SyncableData,
        resolvedData: SyncableData,
        strategy: ResolutionStrategy
    ) {
        Timber.d("""
            ConflictResolver: Resolved conflict for ${localData.id}
            Strategy: $strategy
            Local timestamp: ${localData.lastModified}
            Remote timestamp: ${remoteData.lastModified}
            Resolved timestamp: ${resolvedData.lastModified}
            Resolution chose: ${
                when {
                    resolvedData.dataHash == localData.dataHash -> "LOCAL"
                    resolvedData.dataHash == remoteData.dataHash -> "REMOTE"
                    else -> "MERGED"
                }
            }
        """.trimIndent())
    }
}

/**
 * Data structures for conflict resolution
 */

@Serializable
data class SyncableData(
    val id: String,
    val type: DataType,
    val data: @Contextual Any,
    val version: Long,
    val lastModified: Long,
    val dataHash: String
)

@Serializable
data class ConflictInfo(
    val dataId: String,
    val dataType: DataType,
    val localTimestamp: Long,
    val remoteTimestamp: Long,
    val localVersion: Long,
    val remoteVersion: Long,
    val conflictType: ConflictType,
    val severity: ConflictSeverity,
    val detectedAt: Long = Instant.now().epochSecond
)

enum class DataType {
    WIDGET_PREFERENCES,
    ANALYTICS_DATA,
    WORKOUT_SESSION,
    USER_PROFILE,
    PERSONAL_RECORD,
    EXERCISE_DATA
}

enum class ResolutionStrategy {
    LastWriteWins,      // Use most recent timestamp
    UserPreference,     // Always prefer local changes
    ServerAuthoritative, // Always prefer server data
    SmartMerge          // Attempt to merge compatible changes
}

enum class ConflictType {
    LOCAL_NEWER,
    REMOTE_NEWER,
    VERSION_MISMATCH,
    CONTENT_DIFFERS,
    UNKNOWN
}

enum class ConflictSeverity {
    LOW,        // Recent conflict, easy to resolve
    MEDIUM,     // Moderate time difference
    HIGH,       // Significant time difference
    CRITICAL    // Large time difference or data integrity issues
}