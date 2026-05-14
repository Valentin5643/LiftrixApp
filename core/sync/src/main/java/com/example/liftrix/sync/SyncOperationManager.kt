package com.example.liftrix.sync

import com.example.liftrix.data.local.dao.*
import com.example.liftrix.data.mapper.*
import com.example.liftrix.data.sync.OfflineQueueManager
import com.example.liftrix.service.sync.ConflictResolver
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.config.OfflineArchitectureFlags
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
    private val json: Json,
    private val startupRestoreGate: StartupRestoreGate
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
        if (!startupRestoreGate.isRestoreComplete(userId)) {
            Timber.tag("StartupRestoreFix").w(
                "operation=SYNC_OPERATION_MANAGER_BLOCKED userId=$userId operationType=$operationType maxPriority=$maxPriority gateState=${startupRestoreGate.currentState(userId)} timestamp=${System.currentTimeMillis()}"
            )
            return@liftrixCatching emptyList()
        }
        Timber.tag("StartupRestoreFix").d(
            "operation=SYNC_OPERATION_MANAGER_ALLOWED userId=$userId operationType=$operationType maxPriority=$maxPriority gateState=${startupRestoreGate.currentState(userId)} timestamp=${System.currentTimeMillis()}"
        )
        
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
            
            val results = mutableListOf<SyncOperationResult>()

            try {
                // Get user profile that needs syncing
                val userProfile = userProfileDao.getProfileForUserSuspend(userId)

                if (userProfile == null) {
                    Timber.d("SyncOperationManager: No profile found for user $userId")
                    return emptyList()
                }

                if (userProfile.isSynced) {
                    Timber.d("SyncOperationManager: Profile already synced for user $userId")
                    return emptyList()
                }

                // Convert to Firestore and upload
                val docRef = firestore
                    .collection("users")
                    .document(userId)

                val profileData = userProfileMapper.toFirestoreDto(
                    userProfileMapper.toDomain(userProfile)
                )

                docRef.set(profileData, SetOptions.merge()).await()

                // Mark as synced
                userProfileDao.updateSyncStatus(
                    userId = userId,
                    isSynced = true,
                    version = System.currentTimeMillis()
                )

                results.add(SyncOperationResult(operation, success = true))
                Timber.d("SyncOperationManager: Successfully synced profile for $userId")

            } catch (e: Exception) {
                Timber.e(e, "SyncOperationManager: Failed to sync profile $userId")
                results.add(SyncOperationResult(operation, success = false, error = e.message))
            }

            Timber.d("SyncOperationManager: Profile sync completed for user $userId")
            results
            
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
            
            val useDirtyFlagGating = OfflineArchitectureFlags.ROOM_FIRST_ENABLED &&
                OfflineArchitectureFlags.USE_DIRTY_FLAG_GATING
            val unsyncedWorkouts = if (useDirtyFlagGating) {
                workoutDao.getDirtyWorkouts(userId)
            } else {
                workoutDao.getUnsyncedWorkoutsForUser(userId)
            }
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
                        require(workout.userId == userId) {
                            "User ownership validation failed: workout.userId=${workout.userId}, expected=$userId"
                        }

                        // 🔥 SYNC-FIX: Implement fallback to prevent exercise data loss during schema mismatches
                        val baseWorkoutData = try {
                            // First attempt: Use normal domain conversion
                            val domainWorkout = workoutMapper.toDomain(workout)

                            // Check if domain conversion preserved exercise data
                            if (domainWorkout.exercises.isEmpty() && !workout.exercisesJson.isNullOrBlank()) {
                                Timber.w("[SYNC-FALLBACK] 🚨 toDomain conversion lost exercises! JSON length: ${workout.exercisesJson.length}")
                                Timber.w("[SYNC-FALLBACK] Falling back to direct entity→DTO conversion to preserve data")

                                // Use the bypass method to preserve exercise data
                                workoutMapper.entityToFirestoreDto(workout, userId)
                            } else {
                                Timber.d("[SYNC-NORMAL] toDomain conversion successful, ${domainWorkout.exercises.size} exercises preserved")
                                workoutMapper.toFirestoreDto(domainWorkout, userId)
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "[SYNC-FALLBACK] 🚨 toDomain failed completely! Exception: ${e.message}")
                            Timber.w("[SYNC-FALLBACK] Falling back to direct entity→DTO conversion")

                            // Use the bypass method when toDomain throws an exception
                            workoutMapper.entityToFirestoreDto(workout, userId)
                        }
                        
                        val syncVersion = System.currentTimeMillis()
                        val workoutData = baseWorkoutData.copy(
                            syncVersion = syncVersion,
                            synced = true,
                            lastModified = workout.lastModified,
                            updatedAt = null
                        )

                        Timber.d(
                            "SyncOperationManager: Queuing workout write path=${docRef.path} " +
                                "authUid=${auth.currentUser?.uid} userId=$userId workoutUserId=${workout.userId} " +
                                "lastModified=${workout.lastModified} syncVersion=$syncVersion"
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
                        workoutDao.markAsClean(
                            ids = batch.map { it.id },
                            userId = userId,
                            syncVersion = System.currentTimeMillis()
                        )

                        for (workout in batch) {
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
            
            val results = mutableListOf<SyncOperationResult>()

            try {
                // Get unsynced templates
                val useDirtyFlagGating = OfflineArchitectureFlags.ROOM_FIRST_ENABLED &&
                    OfflineArchitectureFlags.USE_DIRTY_FLAG_GATING
                val unsyncedTemplates = if (useDirtyFlagGating) {
                    workoutTemplateDao.getDirtyWorkoutTemplates(userId)
                } else {
                    workoutTemplateDao.getUnsyncedTemplates(userId)
                }

                if (unsyncedTemplates.isEmpty()) {
                    Timber.d("SyncOperationManager: No unsynced templates for user $userId")
                    return emptyList()
                }

                // Process templates in batches
                val batches = unsyncedTemplates.chunked(DEFAULT_BATCH_SIZE)

                for ((batchIndex, batch) in batches.withIndex()) {
                    cancellationCheck?.invoke()

                    Timber.d("SyncOperationManager: Processing template batch ${batchIndex + 1}/${batches.size} (${batch.size} templates)")

                    val firestoreBatch = firestore.batch()

                    for (template in batch) {
                        val docRef = firestore
                            .collection("users")
                            .document(userId)
                            .collection("templates")
                            .document(template.id)

                        try {
                            // Create basic template data since we don't have a mapper
                            val templateData = mapOf(
                                "id" to template.id,
                                "userId" to template.userId,
                                "name" to template.name,
                                "description" to template.description,
                                "templateExercisesJson" to template.templateExercisesJson,
                                "estimatedDurationMinutes" to template.estimatedDurationMinutes,
                                "difficultyLevel" to template.difficultyLevel,
                                "folderId" to template.folderId,
                                "usageCount" to template.usageCount,
                                "lastUpdated" to FieldValue.serverTimestamp(),
                                "version" to System.currentTimeMillis()
                            )

                            firestoreBatch.set(docRef, templateData, SetOptions.merge())

                        } catch (e: Exception) {
                            Timber.w(e, "SyncOperationManager: Failed to prepare template ${template.id} for batch")
                            continue
                        }
                    }

                    try {
                        firestoreBatch.commit().await()

                        // Mark batch as synced
                        workoutTemplateDao.markAsClean(
                            ids = batch.map { it.id },
                            userId = userId,
                            syncVersion = System.currentTimeMillis()
                        )

                        for (template in batch) {
                            results.add(SyncOperationResult(operation, success = true))
                        }

                        Timber.d("SyncOperationManager: Successfully synced template batch ${batchIndex + 1}")

                    } catch (e: Exception) {
                        Timber.e(e, "SyncOperationManager: Failed to commit template batch ${batchIndex + 1}")

                        for (template in batch) {
                            results.add(SyncOperationResult(operation, success = false, error = e.message))
                        }
                    }
                }

            } catch (e: Exception) {
                Timber.e(e, "SyncOperationManager: Failed to sync templates for $userId")
                results.add(SyncOperationResult(operation, success = false, error = e.message))
            }
            Timber.d("SyncOperationManager: Template sync completed for user $userId")
            results
            
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
            
            val results = mutableListOf<SyncOperationResult>()

            try {
                // Get unsynced social profiles
                val useDirtyFlagGating = OfflineArchitectureFlags.ROOM_FIRST_ENABLED &&
                    OfflineArchitectureFlags.USE_DIRTY_FLAG_GATING
                val unsyncedSocialProfiles = if (useDirtyFlagGating) {
                    socialProfileDao.getDirtySocialProfiles(userId)
                } else {
                    socialProfileDao.getUnsyncedProfiles(userId)
                }

                if (unsyncedSocialProfiles.isEmpty()) {
                    Timber.d("SyncOperationManager: No unsynced social profiles for user $userId")
                    return emptyList()
                }

                // Process social data in batches
                val batches = unsyncedSocialProfiles.chunked(SOCIAL_BATCH_SIZE)

                for ((batchIndex, batch) in batches.withIndex()) {
                    cancellationCheck?.invoke()

                    Timber.d("SyncOperationManager: Processing social batch ${batchIndex + 1}/${batches.size} (${batch.size} profiles)")

                    val firestoreBatch = firestore.batch()

                    for (profile in batch) {
                        val docRef = firestore
                            .collection("social_profiles")
                            .document(profile.userId)

                        try {
                            // Create a basic social profile data map since we don't have a mapper
                            val socialData = mapOf(
                                "userId" to profile.userId,
                                "username" to profile.username,
                                "displayName" to (profile.displayName ?: ""),
                                "bio" to (profile.bio ?: ""),
                                "profilePhotoUrl" to profile.profilePhotoUrl,
                                "isPrivate" to profile.isPrivate,
                                "followerCount" to profile.followerCount,
                                "followingCount" to profile.followingCount,
                                "workoutCount" to profile.workoutCount,
                                "lastUpdated" to FieldValue.serverTimestamp(),
                                "version" to System.currentTimeMillis()
                            )

                            firestoreBatch.set(docRef, socialData, SetOptions.merge())

                        } catch (e: Exception) {
                            Timber.w(e, "SyncOperationManager: Failed to prepare social profile ${profile.userId} for batch")
                            continue
                        }
                    }

                    try {
                        firestoreBatch.commit().await()

                        // Mark batch as synced
                        socialProfileDao.markAsClean(
                            ids = batch.map { it.userId },
                            userId = userId,
                            syncVersion = System.currentTimeMillis()
                        )

                        for (profile in batch) {
                            results.add(SyncOperationResult(operation, success = true))
                        }

                        Timber.d("SyncOperationManager: Successfully synced social batch ${batchIndex + 1}")

                    } catch (e: Exception) {
                        Timber.e(e, "SyncOperationManager: Failed to commit social batch ${batchIndex + 1}")

                        for (profile in batch) {
                            results.add(SyncOperationResult(operation, success = false, error = e.message))
                        }
                    }
                }

            } catch (e: Exception) {
                Timber.e(e, "SyncOperationManager: Failed to sync social data for $userId")
                results.add(SyncOperationResult(operation, success = false, error = e.message))
            }
            Timber.d("SyncOperationManager: Social sync completed for user $userId")
            results
            
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
            
            val results = mutableListOf<SyncOperationResult>()

            try {
                // Get unsynced achievements
                val useDirtyFlagGating = OfflineArchitectureFlags.ROOM_FIRST_ENABLED &&
                    OfflineArchitectureFlags.USE_DIRTY_FLAG_GATING
                val unsyncedAchievements = if (useDirtyFlagGating) {
                    achievementDao.getDirtyUserAchievements(userId)
                } else {
                    achievementDao.getUnsyncedAchievements(userId)
                }

                if (unsyncedAchievements.isEmpty()) {
                    Timber.d("SyncOperationManager: No unsynced achievements for user $userId")
                    return emptyList()
                }

                // Process achievements in batches
                val batches = unsyncedAchievements.chunked(DEFAULT_BATCH_SIZE)

                for ((batchIndex, batch) in batches.withIndex()) {
                    cancellationCheck?.invoke()

                    Timber.d("SyncOperationManager: Processing achievement batch ${batchIndex + 1}/${batches.size} (${batch.size} achievements)")

                    val firestoreBatch = firestore.batch()

                    for (achievement in batch) {
                        val docRef = firestore
                            .collection("users")
                            .document(userId)
                            .collection("achievements")
                            .document(achievement.id)

                        try {
                            // Create basic achievement data since we don't have a mapper
                            val achievementData = mapOf(
                                "id" to achievement.id,
                                "userId" to achievement.userId,
                                "type" to achievement.achievementType,
                                "title" to achievement.achievementTitle,
                                "description" to achievement.achievementDescription,
                                "unlockedAt" to achievement.unlockedAt.let {
                                    Timestamp(it.toEpochSecond(java.time.ZoneOffset.UTC), it.nano)
                                },
                                "isDisplayed" to achievement.isDisplayed,
                                "lastUpdated" to FieldValue.serverTimestamp(),
                                "version" to System.currentTimeMillis()
                            )

                            firestoreBatch.set(docRef, achievementData, SetOptions.merge())

                        } catch (e: Exception) {
                            Timber.w(e, "SyncOperationManager: Failed to prepare achievement ${achievement.id} for batch")
                            continue
                        }
                    }

                    try {
                        firestoreBatch.commit().await()

                        // Mark batch as synced
                        achievementDao.markAsClean(
                            ids = batch.map { it.id },
                            userId = userId,
                            syncVersion = System.currentTimeMillis()
                        )

                        for (achievement in batch) {
                            results.add(SyncOperationResult(operation, success = true))
                        }

                        Timber.d("SyncOperationManager: Successfully synced achievement batch ${batchIndex + 1}")

                    } catch (e: Exception) {
                        Timber.e(e, "SyncOperationManager: Failed to commit achievement batch ${batchIndex + 1}")

                        for (achievement in batch) {
                            results.add(SyncOperationResult(operation, success = false, error = e.message))
                        }
                    }
                }

            } catch (e: Exception) {
                Timber.e(e, "SyncOperationManager: Failed to sync achievements for $userId")
                results.add(SyncOperationResult(operation, success = false, error = e.message))
            }
            Timber.d("SyncOperationManager: Achievement sync completed for user $userId")
            results
            
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
