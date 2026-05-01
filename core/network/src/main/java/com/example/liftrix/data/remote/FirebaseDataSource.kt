package com.example.liftrix.data.remote

/**
 * Interface for Firebase Firestore data operations.
 * 
 * This interface defines the contract for all remote data operations with Firebase Firestore.
 * It will be implemented in FB-003 task with actual Firebase integration.
 * 
 * The interface supports CRUD operations for all syncable entity types with proper error handling
 * and result types that integrate with the OfflineQueueManager's ProcessResult system.
 */
interface FirebaseDataSource {
    
    /**
     * Creates a new entity in Firebase Firestore.
     * 
     * @param userId The user ID for data scoping
     * @param entityType The type of entity (WORKOUT, TEMPLATE, PROFILE, ACHIEVEMENT)
     * @param entityId The unique ID of the entity
     * @param data JSON-serialized entity data
     * @return ProcessResult indicating success, failure, or conflict
     */
    suspend fun create(userId: String, entityType: String, entityId: String, data: String): ProcessResult
    
    /**
     * Updates an existing entity in Firebase Firestore.
     * 
     * @param userId The user ID for data scoping
     * @param entityType The type of entity (WORKOUT, TEMPLATE, PROFILE, ACHIEVEMENT)
     * @param entityId The unique ID of the entity
     * @param data JSON-serialized entity data
     * @return ProcessResult indicating success, failure, or conflict
     */
    suspend fun update(userId: String, entityType: String, entityId: String, data: String): ProcessResult
    
    /**
     * Deletes an entity from Firebase Firestore.
     * 
     * @param userId The user ID for data scoping
     * @param entityType The type of entity (WORKOUT, TEMPLATE, PROFILE, ACHIEVEMENT)
     * @param entityId The unique ID of the entity
     * @return ProcessResult indicating success, failure, or conflict
     */
    suspend fun delete(userId: String, entityType: String, entityId: String): ProcessResult
    
    /**
     * Fetches an entity from Firebase Firestore.
     * 
     * @param userId The user ID for data scoping
     * @param entityType The type of entity (WORKOUT, TEMPLATE, PROFILE, ACHIEVEMENT)
     * @param entityId The unique ID of the entity
     * @return ProcessResult with entity data on success
     */
    suspend fun fetch(userId: String, entityType: String, entityId: String): ProcessResult
    
    /**
     * Fetches all entities of a specific type for a user.
     * 
     * @param userId The user ID for data scoping
     * @param entityType The type of entity (WORKOUT, TEMPLATE, PROFILE, ACHIEVEMENT)
     * @return ProcessResult with list of entities on success
     */
    suspend fun fetchAll(userId: String, entityType: String): ProcessResult
}

/**
 * Result types for Firebase data operations.
 * These align with the OfflineQueueManager's ProcessResult expectations.
 */
sealed class ProcessResult {
    object Success : ProcessResult()
    object Conflict : ProcessResult()
    data class Failure(val error: Throwable) : ProcessResult()
    data class Data(val data: String) : ProcessResult() // For fetch operations
    data class DataList(val data: List<String>) : ProcessResult() // For fetchAll operations
}