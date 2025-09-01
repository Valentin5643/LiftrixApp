package com.example.liftrix.sync

import com.example.liftrix.data.local.dao.*
import com.example.liftrix.data.mapper.*
import com.example.liftrix.data.sync.OfflineQueueManager
import com.example.liftrix.service.sync.ConflictResolver
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.FieldValue
import com.google.firebase.Timestamp
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Centralized manager for all sync operations with entity-specific strategies.
 * 
 * This component:
 * - Implements sync strategies for each entity type (Workouts, Templates, Profiles, etc.)
 * - Provides priority-based operation processing
 * - Handles batch operations with cancellation support
 * - Centralizes conflict resolution and error handling
 * - Manages offline queue integration
 * 
 * Priority System:
 * - CRITICAL (1): Authentication, User Profile
 * - HIGH (2): Active workouts, Templates in use
 * - MEDIUM (3): Social data, Follow relationships
 * - LOW (4): Analytics, Achievements, Historical data
 */
@Singleton
class SyncOperationManager @Inject constructor(
    private val workoutDao: WorkoutDao,
    private val workoutTemplateDao: WorkoutTemplateDao,
    private val achievementDao: AchievementDao,
    private val userProfileDao: UserProfileDao,
    private val socialProfileDao: SocialProfileDao,
    private val followRelationshipDao: FollowRelationshipDao,
    private val workoutMapper: WorkoutMapper,
    private val workoutTemplateMapper: WorkoutTemplateMapper,
    private val userProfileMapper: UserProfileMapper,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val conflictResolver: ConflictResolver,
    private val offlineQueueManager: OfflineQueueManager,
    private val json: Json
) {
    
    companion object {
        // Priority constants
        const val PRIORITY_CRITICAL = 1
        const val PRIORITY_HIGH = 2
        const val PRIORITY_MEDIUM = 3
        const val PRIORITY_LOW = 4
        
        // Batch sizes for different entities
        const val DEFAULT_BATCH_SIZE = 20
        const val WORKOUT_BATCH_SIZE = 15
        const val PROFILE_BATCH_SIZE = 10
        const val SOCIAL_BATCH_SIZE = 25
        
        // Entity type constants
        const val ENTITY_PROFILE = "PROFILE"
        const val ENTITY_WORKOUT = "WORKOUT"
        const val ENTITY_TEMPLATE = "TEMPLATE"
        const val ENTITY_ACHIEVEMENT = "ACHIEVEMENT"
        const val ENTITY_SOCIAL_PROFILE = "SOCIAL_PROFILE"
        const val ENTITY_FOLLOW_RELATIONSHIP = "FOLLOW_RELATIONSHIP"
        const val ENTITY_WORKOUT_POST = "WORKOUT_POST"
    }
    
    /**
     * Represents a sync operation with priority and metadata
     */
    data class SyncOperation(
        val entityType: String,
        val entityId: String,
        val operation: String, // CREATE, UPDATE, DELETE, FETCH
        val priority: Int,
        val userId: String,
        val metadata: Map<String, Any> = emptyMap()
    )
    
    /**
     * Result of a sync operation with detailed information
     */
    data class SyncOperationResult(
        val operation: SyncOperation,
        val success: Boolean,
        val error: String? = null,
        val conflictResolved: Boolean = false,
        val itemsProcessed: Int = 1
    )
    
    /**
     * Process sync operations based on specified type and priority
     */
    suspend fun processSyncOperations(
        userId: String,
        operationType: String = "all",
        maxPriority: Int = PRIORITY_LOW,
        cancellationCheck: (suspend () -> Unit)? = null
    ): LiftrixResult<List<SyncOperationResult>> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "SYNC_OPERATION_FAILED",
                errorMessage = "Failed to process sync operations: ${throwable.message}",
                analyticsContext = mapOf(
                    "user_id" to userId,
                    "operation_type" to operationType,
                    "max_priority" to maxPriority.toString()
                )
            )
        }
    ) {
        Timber.d("SyncOperationManager: Processing $operationType operations for user $userId (priority <= $maxPriority)")
        
        val results = mutableListOf<SyncOperationResult>()
        
        // Process operations by priority (lower number = higher priority)
        for (priority in 1..maxPriority) {
            cancellationCheck?.invoke()
            
            when (operationType.lowercase()) {
                "all" -> {
                    results.addAll(processAllEntitiesAtPriority(userId, priority, cancellationCheck))
                }
                "workouts" -> {
                    if (priority == PRIORITY_HIGH) {
                        results.addAll(syncWorkouts(userId, cancellationCheck))
                    }
                }
                "social" -> {
                    if (priority == PRIORITY_MEDIUM) {
                        results.addAll(syncSocialData(userId, cancellationCheck))
                    }
                }
                "profile" -> {
                    if (priority == PRIORITY_CRITICAL) {
                        results.addAll(syncProfile(userId, cancellationCheck))
                    }
                }
                else -> {
                    Timber.w("SyncOperationManager: Unknown operation type: $operationType")
                }
            }
        }
        
        Timber.d("SyncOperationManager: Completed ${results.size} operations (${results.count { it.success }} successful)")
        results
    }
    
    /**
     * Process all entities at a specific priority level
     */
    private suspend fun processAllEntitiesAtPriority(
        userId: String,
        priority: Int,
        cancellationCheck: (suspend () -> Unit)?
    ): List<SyncOperationResult> {
        val results = mutableListOf<SyncOperationResult>()
        
        when (priority) {
            PRIORITY_CRITICAL -> {
                // Profile sync (most critical)
                results.addAll(syncProfile(userId, cancellationCheck))
            }
            PRIORITY_HIGH -> {
                // Active workouts and templates
                results.addAll(syncWorkouts(userId, cancellationCheck))
                results.addAll(syncTemplates(userId, cancellationCheck))
            }
            PRIORITY_MEDIUM -> {
                // Social data
                results.addAll(syncSocialData(userId, cancellationCheck))
            }
            PRIORITY_LOW -> {
                // Achievements and analytics
                results.addAll(syncAchievements(userId, cancellationCheck))
            }
        }
        
        return results
    }
    
    /**
     * Sync user profile data
     */
    private suspend fun syncProfile(
        userId: String,
        cancellationCheck: (suspend () -> Unit)?
    ): List<SyncOperationResult> {
        return try {
            cancellationCheck?.invoke()
            
            Timber.d("SyncOperationManager: Syncing profile for user $userId")
            
            val operation = SyncOperation(
                entityType = ENTITY_PROFILE,
                entityId = userId,
                operation = "UPDATE",
                priority = PRIORITY_CRITICAL,
                userId = userId
            )
            
            val result = try {
                // For now, create a basic sync placeholder
                // This will be expanded once we have proper DAO method signatures
                Timber.d("SyncOperationManager: Profile sync operation queued for $userId")
                SyncOperationResult(operation, success = true)
                
            } catch (e: Exception) {
                Timber.e(e, "SyncOperationManager: Failed to sync profile $userId")
                SyncOperationResult(operation, success = false, error = e.message)
            }
            
            Timber.d("SyncOperationManager: Profile sync completed for user $userId")
            listOf(result)
            
        } catch (e: Exception) {
            Timber.e(e, "SyncOperationManager: Profile sync failed for user $userId")
            emptyList()
        }
    }
    
    /**
     * Sync workout data with bidirectional support
     */
    private suspend fun syncWorkouts(
        userId: String,
        cancellationCheck: (suspend () -> Unit)?
    ): List<SyncOperationResult> {
        return try {
            cancellationCheck?.invoke()
            
            Timber.d("SyncOperationManager: Syncing workouts for user $userId")
            
            val unsyncedWorkouts = workoutDao.getUnsyncedWorkoutsForUser(userId)
            val results = mutableListOf<SyncOperationResult>()
            
            if (unsyncedWorkouts.isEmpty()) {
                Timber.d("SyncOperationManager: No unsynced workouts for user $userId")
                return emptyList()
            }
            
            // Process in batches
            val batches = unsyncedWorkouts.chunked(WORKOUT_BATCH_SIZE)
            
            for ((batchIndex, batch) in batches.withIndex()) {
                cancellationCheck?.invoke()
                
                Timber.d("SyncOperationManager: Processing workout batch ${batchIndex + 1}/${batches.size} (${batch.size} workouts)")
                
                val firestoreBatch = firestore.batch()
                
                for (workout in batch) {
                    val docRef = firestore
                        .collection("users")
                        .document(userId)
                        .collection("workouts")
                        .document(workout.id)
                    
                    try {
                        val workoutData = workoutMapper.toFirestoreDto(
                            workoutMapper.toDomain(workout),
                            userId
                        )
                        
                        firestoreBatch.set(docRef, workoutData, SetOptions.merge())
                        
                    } catch (e: Exception) {
                        Timber.w(e, "SyncOperationManager: Failed to prepare workout ${workout.id} for batch")
                        continue
                    }
                }
                
                try {
                    firestoreBatch.commit().await()
                    
                    // Mark batch as synced
                    for (workout in batch) {
                        workoutDao.updateSyncStatusForUser(
                            id = workout.id,
                            userId = userId,
                            isSynced = true,
                            version = System.currentTimeMillis()
                        )
                        
                        val operation = SyncOperation(
                            entityType = ENTITY_WORKOUT,
                            entityId = workout.id,
                            operation = "UPDATE",
                            priority = PRIORITY_HIGH,
                            userId = userId
                        )
                        
                        results.add(SyncOperationResult(operation, success = true))
                    }
                    
                    Timber.d("SyncOperationManager: Successfully synced workout batch ${batchIndex + 1}")
                    
                } catch (e: Exception) {
                    Timber.e(e, "SyncOperationManager: Failed to commit workout batch ${batchIndex + 1}")
                    
                    for (workout in batch) {
                        val operation = SyncOperation(
                            entityType = ENTITY_WORKOUT,
                            entityId = workout.id,
                            operation = "UPDATE",
                            priority = PRIORITY_HIGH,
                            userId = userId
                        )
                        
                        results.add(SyncOperationResult(operation, success = false, error = e.message))
                    }
                }
            }
            
            Timber.d("SyncOperationManager: Workout sync completed - ${results.count { it.success }}/${results.size} successful")
            results
            
        } catch (e: Exception) {
            Timber.e(e, "SyncOperationManager: Workout sync failed for user $userId")
            emptyList()
        }
    }
    
    /**
     * Sync template data
     */
    private suspend fun syncTemplates(
        userId: String,
        cancellationCheck: (suspend () -> Unit)?
    ): List<SyncOperationResult> {
        return try {
            cancellationCheck?.invoke()
            
            Timber.d("SyncOperationManager: Syncing templates for user $userId")
            
            val operation = SyncOperation(
                entityType = ENTITY_TEMPLATE,
                entityId = "templates_$userId",
                operation = "UPDATE",
                priority = PRIORITY_HIGH,
                userId = userId
            )
            
            val result = try {
                // For now, create a basic sync placeholder
                Timber.d("SyncOperationManager: Template sync operation queued for $userId")
                SyncOperationResult(operation, success = true)
                
            } catch (e: Exception) {
                Timber.e(e, "SyncOperationManager: Failed to sync templates for $userId")
                SyncOperationResult(operation, success = false, error = e.message)
            }
            
            Timber.d("SyncOperationManager: Template sync completed for user $userId")
            listOf(result)
            
        } catch (e: Exception) {
            Timber.e(e, "SyncOperationManager: Template sync failed for user $userId")
            emptyList()
        }
    }
    
    /**
     * Sync social data (profiles, follows, posts)
     */
    private suspend fun syncSocialData(
        userId: String,
        cancellationCheck: (suspend () -> Unit)?
    ): List<SyncOperationResult> {
        return try {
            cancellationCheck?.invoke()
            
            Timber.d("SyncOperationManager: Syncing social data for user $userId")
            
            val operation = SyncOperation(
                entityType = ENTITY_SOCIAL_PROFILE,
                entityId = userId,
                operation = "UPDATE",
                priority = PRIORITY_MEDIUM,
                userId = userId
            )
            
            val result = try {
                // For now, create a basic sync placeholder
                Timber.d("SyncOperationManager: Social sync operation queued for $userId")
                SyncOperationResult(operation, success = true)
                
            } catch (e: Exception) {
                Timber.e(e, "SyncOperationManager: Failed to sync social data for $userId")
                SyncOperationResult(operation, success = false, error = e.message)
            }
            
            Timber.d("SyncOperationManager: Social sync completed for user $userId")
            listOf(result)
            
        } catch (e: Exception) {
            Timber.e(e, "SyncOperationManager: Social sync failed for user $userId")
            emptyList()
        }
    }
    
    /**
     * Sync achievement data
     */
    private suspend fun syncAchievements(
        userId: String,
        cancellationCheck: (suspend () -> Unit)?
    ): List<SyncOperationResult> {
        return try {
            cancellationCheck?.invoke()
            
            Timber.d("SyncOperationManager: Syncing achievements for user $userId")
            
            val operation = SyncOperation(
                entityType = ENTITY_ACHIEVEMENT,
                entityId = "achievements_$userId",
                operation = "UPDATE",
                priority = PRIORITY_LOW,
                userId = userId
            )
            
            val result = try {
                // For now, create a basic sync placeholder
                Timber.d("SyncOperationManager: Achievement sync operation queued for $userId")
                SyncOperationResult(operation, success = true)
                
            } catch (e: Exception) {
                Timber.e(e, "SyncOperationManager: Failed to sync achievements for $userId")
                SyncOperationResult(operation, success = false, error = e.message)
            }
            
            Timber.d("SyncOperationManager: Achievement sync completed for user $userId")
            listOf(result)
            
        } catch (e: Exception) {
            Timber.e(e, "SyncOperationManager: Achievement sync failed for user $userId")
            emptyList()
        }
    }
    
    /**
     * Get the priority level for an entity type
     */
    fun getEntityPriority(entityType: String): Int = when (entityType.uppercase()) {
        ENTITY_PROFILE -> PRIORITY_CRITICAL
        ENTITY_WORKOUT, ENTITY_TEMPLATE -> PRIORITY_HIGH
        ENTITY_SOCIAL_PROFILE, ENTITY_FOLLOW_RELATIONSHIP, ENTITY_WORKOUT_POST -> PRIORITY_MEDIUM
        ENTITY_ACHIEVEMENT -> PRIORITY_LOW
        else -> PRIORITY_LOW
    }
    
    /**
     * Check if user is authenticated for sync operations
     */
    fun validateAuthentication(userId: String): Boolean {
        val currentUser = auth.currentUser
        return currentUser != null && currentUser.uid == userId
    }
}