package com.example.liftrix.domain.sync

/**
 * Domain interface for resolving conflicts between local and remote data during synchronization.
 * 
 * This interface defines the contract for conflict resolution as specified in the Firebase sync
 * infrastructure specification. It provides a clean abstraction for different conflict resolution
 * strategies while maintaining type safety through generic constraints.
 * 
 * The primary strategy is last-write-wins based on timestamp comparison, with extensibility
 * for additional strategies as needed.
 */
interface ConflictResolver {
    
    /**
     * Resolves conflicts between local and remote entities using the specified strategy.
     * 
     * @param T The type of entity being resolved, must implement SyncableEntity
     * @param local The local version of the entity
     * @param remote The remote version of the entity
     * @param strategy The conflict resolution strategy to apply
     * @return The resolved entity with updated version and timestamp
     */
    fun <T : SyncableEntity> resolve(
        local: T,
        remote: T,
        strategy: ConflictStrategy = ConflictStrategy.LAST_WRITE_WINS
    ): T
}

/**
 * Defines the available conflict resolution strategies.
 */
enum class ConflictStrategy {
    /**
     * Uses the entity with the most recent lastModified timestamp.
     * This is the default strategy as specified in the sync infrastructure requirements.
     */
    LAST_WRITE_WINS,
    
    /**
     * Always prefers the local version of the entity.
     * Useful for user preference scenarios.
     */
    LOCAL_WINS,
    
    /**
     * Always prefers the remote version of the entity.
     * Useful for server-authoritative data scenarios.
     */
    REMOTE_WINS
}

/**
 * Marker interface for entities that can be synchronized and participate in conflict resolution.
 * 
 * All entities that need to be synced with Firebase must implement this interface to provide
 * the necessary metadata for conflict detection and resolution.
 */
interface SyncableEntity {
    /**
     * Indicates whether this entity has been successfully synced to the remote storage.
     */
    val isSynced: Boolean
    
    /**
     * Version number for optimistic locking and conflict detection.
     * Should be incremented on each modification.
     */
    val syncVersion: Long
    
    /**
     * Timestamp of the last modification to this entity.
     * Used for last-write-wins conflict resolution.
     */
    val lastModified: Long
}