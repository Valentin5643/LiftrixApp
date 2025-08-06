package com.example.liftrix.service.session

/**
 * Unified interface combining all session management capabilities.
 * 
 * This interface provides backward compatibility with the original UnifiedWorkoutSessionManager
 * while maintaining interface segregation through composition. Clients can use this unified
 * interface or inject specific managers based on their needs.
 * 
 * Implementation delegates to specialized managers:
 * - SessionStateManager: Active session lifecycle and state
 * - SessionPersistenceManager: Data storage and retrieval
 * - SessionRecoveryManager: Error handling and recovery
 */
interface UnifiedWorkoutSessionService : 
    SessionStateManager, 
    SessionPersistenceManager, 
    SessionRecoveryManager {
    
    /**
     * Provides access to the state management component.
     */
    val stateManager: SessionStateManager
    
    /**
     * Provides access to the persistence management component.
     */
    val persistenceManager: SessionPersistenceManager
    
    /**
     * Provides access to the recovery management component.
     */
    val recoveryManager: SessionRecoveryManager
    
    /**
     * Convenience method to check if there's any session (active or persisted).
     * 
     * @return true if there's an active session or persisted session
     */
    suspend fun hasAnySession(): Boolean
    
    /**
     * Convenience method to get the current session from state or persistence.
     * 
     * @return The current session, checking active state first, then persistence
     */
    suspend fun getCurrentSessionFromAnySource(): com.example.liftrix.domain.model.UnifiedWorkoutSession?
}