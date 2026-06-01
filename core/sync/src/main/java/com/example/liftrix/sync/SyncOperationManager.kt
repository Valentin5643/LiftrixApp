package com.example.liftrix.sync

import com.example.liftrix.data.local.dao.*
import com.example.liftrix.data.local.entity.WorkoutTemplateEntity
import com.example.liftrix.data.local.entity.UserAccountEntity
import com.example.liftrix.data.local.entity.SettingsEntity
import com.example.liftrix.data.local.entity.FollowRelationshipEntity
import com.example.liftrix.data.local.entity.WorkoutPostEntity
import com.example.liftrix.data.local.entity.GymBuddyEntity
import com.example.liftrix.data.mapper.*
import com.example.liftrix.data.remote.dto.WorkoutDto
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
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.Timestamp
import com.google.gson.Gson
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import java.time.Instant

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
    private val analyticsReadModelDao: AnalyticsReadModelDao,
    private val workoutTemplateDao: WorkoutTemplateDao,
    private val achievementDao: AchievementDao,
    private val userProfileDao: UserProfileDao,
    private val userAccountDao: UserAccountDao,
    private val socialProfileDao: SocialProfileDao,
    private val followRelationshipDao: FollowRelationshipDao,
    private val workoutPostDao: WorkoutPostDao,
    private val gymBuddyDao: GymBuddyDao,
    private val settingsDao: SettingsDao,
    private val workoutMapper: WorkoutMapper,
    private val workoutTemplateMapper: WorkoutTemplateMapper,
    private val userProfileMapper: UserProfileMapper,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val conflictResolver: ConflictResolver,
    private val offlineQueueManager: OfflineQueueManager,
    private val json: Json,
    private val gson: Gson,
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
        const val ENTITY_USER_PUBLIC = "USER_PUBLIC"
        const val ENTITY_SOCIAL_PROFILE = "SOCIAL_PROFILE"
        const val ENTITY_FOLLOW_RELATIONSHIP = "FOLLOW_RELATIONSHIP"
        const val ENTITY_WORKOUT_POST = "WORKOUT_POST"
        const val ENTITY_GYM_BUDDY = "GYM_BUDDY"
        const val ENTITY_SETTINGS = "SETTINGS"
    }

    private suspend fun upsertRemoteWorkoutAndRefreshReadModels(entity: com.example.liftrix.data.local.entity.WorkoutEntity) {
        val previousReadModelDate = analyticsReadModelDao.getReadModelDateForWorkout(entity.userId, entity.id)
        val previousExerciseIds = analyticsReadModelDao.getExerciseLibraryIdsForWorkout(entity.userId, entity.id)
        workoutDao.upsertFromRemote(entity)
        analyticsReadModelDao.refreshWorkoutReadModels(
            userId = entity.userId,
            workoutId = entity.id,
            oldWorkoutDate = previousReadModelDate,
            oldExerciseLibraryIds = previousExerciseIds
        )
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
        val isStartupSync = operationType.equals("startup", ignoreCase = true)
        Timber.d("SyncOperationManager: Processing $operationType operations for user $userId (priority <= $maxPriority)")
        if (!isStartupSync && !startupRestoreGate.isRestoreComplete(userId)) {
            Timber.tag("StartupRestoreFix").w(
                "operation=SYNC_OPERATION_MANAGER_BLOCKED userId=$userId operationType=$operationType maxPriority=$maxPriority gateState=${startupRestoreGate.currentState(userId)} timestamp=${System.currentTimeMillis()}"
            )
            return@liftrixCatching emptyList()
        }
        Timber.tag("StartupRestoreFix").d(
            "operation=SYNC_OPERATION_MANAGER_ALLOWED userId=$userId operationType=$operationType maxPriority=$maxPriority gateState=${startupRestoreGate.currentState(userId)} timestamp=${System.currentTimeMillis()}"
        )
        
        val results = mutableListOf<SyncOperationResult>()

        if (isStartupSync) {
            results.addAll(restoreRemoteStateForStartup(userId, cancellationCheck))
        }
        
        // Process operations by priority (lower number = higher priority)
        for (priority in 1..maxPriority) {
            cancellationCheck?.invoke()
            
            when (operationType.lowercase()) {
                "all", "startup" -> {
                    results.addAll(processAllEntitiesAtPriority(userId, priority, cancellationCheck))
                }
                "workouts" -> {
                    if (priority == PRIORITY_HIGH) {
                        results.addAll(syncWorkouts(userId, cancellationCheck))
                    }
                }
                "templates" -> {
                    if (priority == PRIORITY_HIGH) {
                        results.addAll(syncTemplates(userId, cancellationCheck))
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
                        results.addAll(syncUserPublic(userId, cancellationCheck))
                    }
                }
                "settings" -> {
                    if (priority == PRIORITY_MEDIUM) {
                        results.addAll(syncSettings(userId, cancellationCheck))
                    }
                }
                "analytics", "achievements" -> {
                    if (priority == PRIORITY_LOW) {
                        results.addAll(syncAchievements(userId, cancellationCheck))
                    }
                }
                "chat" -> {
                    Timber.d("SyncOperationManager: Chat sync is queue-driven; no bulk processor for user $userId")
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
                results.addAll(syncUserPublic(userId, cancellationCheck))
            }
            PRIORITY_HIGH -> {
                // Active workouts and templates
                results.addAll(syncWorkouts(userId, cancellationCheck))
                results.addAll(syncTemplates(userId, cancellationCheck))
            }
            PRIORITY_MEDIUM -> {
                // Social data
                results.addAll(syncSocialData(userId, cancellationCheck))
                results.addAll(syncSettings(userId, cancellationCheck))
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
     * Sync searchable public profile data. This replaces UserPublicSyncWorker scheduling.
     */
    private suspend fun syncUserPublic(
        userId: String,
        cancellationCheck: (suspend () -> Unit)?
    ): List<SyncOperationResult> {
        val operation = SyncOperation(
            entityType = ENTITY_USER_PUBLIC,
            entityId = userId,
            operation = "UPDATE",
            priority = PRIORITY_CRITICAL,
            userId = userId
        )

        return try {
            cancellationCheck?.invoke()

            val userAccount = userAccountDao.getAccountForUserSuspend(userId)
            val userProfile = userProfileDao.getProfileForUserSuspend(userId)
            val useDirtyFlagGating = OfflineArchitectureFlags.ROOM_FIRST_ENABLED &&
                OfflineArchitectureFlags.USE_DIRTY_FLAG_GATING

            if (useDirtyFlagGating) {
                val dirtyAccounts = userAccountDao.getDirtyUserAccounts(userId)
                val dirtyProfiles = userProfileDao.getDirtyUserProfiles(userId)
                if (dirtyAccounts.isEmpty() && dirtyProfiles.isEmpty()) {
                    Timber.d("SyncOperationManager: Public profile already clean for user $userId")
                    return emptyList()
                }
            }

            val displayName = userAccount?.displayName
                ?: userProfile?.displayName
                ?: userAccount?.username
                ?: userAccount?.email?.substringBefore("@")
                ?: "User"
            val username = userAccount?.username
            val searchTokens = buildSearchTokens(username, displayName, userProfile?.bio)
            val totalWorkouts = workoutDao.getWorkoutCountForUser(userId)
            val localLastModified = maxOf(
                userAccount?.lastModified ?: 0L,
                userProfile?.lastModified ?: 0L,
                System.currentTimeMillis()
            )
            val isPublicProfile = userProfile?.isPublic ?: true

            val publicUserData = mapOf(
                "userId" to userId,
                "username" to username,
                "displayName" to displayName,
                "bio" to userProfile?.bio,
                "age" to userProfile?.age,
                "fitnessLevel" to userProfile?.fitnessLevel,
                "isPublic" to isPublicProfile,
                "isPrivate" to !isPublicProfile,
                "isSearchable" to isPublicProfile,
                "totalWorkouts" to totalWorkouts,
                "currentStreak" to (userProfile?.currentStreak ?: 0),
                "longestStreak" to (userProfile?.longestStreak ?: 0),
                "followersCount" to 0,
                "followingCount" to 0,
                "profileImageUrl" to userProfile?.profileImageUrl,
                "memberSince" to (userProfile?.memberSince ?: userAccount?.accountCreatedAt)?.toString(),
                "lastActiveAt" to (userProfile?.lastActiveAt ?: java.time.LocalDateTime.now()).toString(),
                "profileViews" to (userProfile?.profileViewsCount ?: 0),
                "profileCompletionPercentage" to (userProfile?.profileCompletionPercentage ?: 0),
                "fitnessGoals" to parseStoredJsonArray(userProfile?.goals),
                "availableEquipment" to parseStoredJsonArray(userProfile?.availableEquipment),
                "publicAchievements" to emptyList<Map<String, Any>>(),
                "searchTokens" to searchTokens,
                "searchKeywords" to searchTokens,
                "syncVersion" to System.currentTimeMillis(),
                "lastModified" to localLastModified,
                "updatedAt" to FieldValue.serverTimestamp()
            )

            val publicDocRef = firestore.collection("users_public").document(userId)
            val remoteLastModified = publicDocRef.get().await().let { doc ->
                if (doc.exists()) millisFromFirestoreValue(doc.get("lastModified")) ?: 0L else 0L
            }
            if (remoteLastModified > localLastModified) {
                Timber.d("SyncOperationManager: Remote public profile newer for user $userId, upload skipped")
                return listOf(SyncOperationResult(operation, success = true, itemsProcessed = 0))
            }

            publicDocRef.set(publicUserData, SetOptions.merge()).await()
            firestore.collection("user_search_cache").document(userId).set(
                mapOf(
                    "userId" to userId,
                    "username" to username,
                    "displayName" to displayName,
                    "bio" to userProfile?.bio,
                    "fitnessLevel" to userProfile?.fitnessLevel,
                    "totalWorkouts" to totalWorkouts,
                    "memberSince" to publicUserData["memberSince"],
                    "lastActiveAt" to publicUserData["lastActiveAt"],
                    "profileImageUrl" to userProfile?.profileImageUrl,
                    "isPublic" to isPublicProfile,
                    "isSearchable" to isPublicProfile,
                    "searchTokens" to searchTokens,
                    "keywords" to searchTokens,
                    "lastModified" to localLastModified,
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            ).await()

            val syncVersion = System.currentTimeMillis()
            userAccountDao.markAsClean(listOf(userId), userId, syncVersion)
            userProfile?.let { profile ->
                userProfileDao.markAsClean(listOf(profile.id), userId, syncVersion)
            }
            Timber.i("[PUBLIC-LOG] Unified public profile synced user=$userId username=$username searchable=$isPublicProfile")
            listOf(SyncOperationResult(operation, success = true))
        } catch (e: Exception) {
            Timber.e(e, "SyncOperationManager: User public sync failed for user $userId")
            listOf(SyncOperationResult(operation, success = false, error = e.message))
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
        val results = mutableListOf<SyncOperationResult>()
        cancellationCheck?.invoke()
        Timber.d("SyncOperationManager: Syncing social data for user $userId")
        results.addAll(syncSocialProfiles(userId, cancellationCheck))
        results.addAll(syncFollowRelationships(userId, cancellationCheck))
        results.addAll(syncWorkoutPosts(userId, cancellationCheck))
        results.addAll(syncGymBuddies(userId, cancellationCheck))
        Timber.d("SyncOperationManager: Social sync completed for user $userId results=${results.size}")
        return results
    }

    private suspend fun syncSocialProfiles(
        userId: String,
        cancellationCheck: (suspend () -> Unit)?
    ): List<SyncOperationResult> = runCatching {
        val useDirtyFlagGating = OfflineArchitectureFlags.ROOM_FIRST_ENABLED &&
            OfflineArchitectureFlags.USE_DIRTY_FLAG_GATING
        val profiles = if (useDirtyFlagGating) {
            socialProfileDao.getDirtySocialProfiles(userId)
        } else {
            socialProfileDao.getUnsyncedProfiles(userId)
        }
        if (profiles.isEmpty()) return@runCatching emptyList()

        val results = mutableListOf<SyncOperationResult>()
        for (batch in profiles.chunked(SOCIAL_BATCH_SIZE)) {
            cancellationCheck?.invoke()
            val writeBatch = firestore.batch()
            batch.forEach { profile ->
                writeBatch.set(
                    firestore.collection("social_profiles").document(profile.userId),
                    mapOf(
                        "userId" to profile.userId,
                        "username" to profile.username,
                        "displayName" to (profile.displayName ?: ""),
                        "bio" to (profile.bio ?: ""),
                        "profilePhotoUrl" to profile.profilePhotoUrl,
                        "coverPhotoUrl" to profile.coverPhotoUrl,
                        "isPrivate" to profile.isPrivate,
                        "hideFromSuggestions" to profile.hideFromSuggestions,
                        "allowFriendRequests" to profile.allowFriendRequests,
                        "followerCount" to profile.followerCount,
                        "followingCount" to profile.followingCount,
                        "workoutCount" to profile.workoutCount,
                        "memberSince" to profile.memberSince,
                        "lastActive" to profile.lastActive,
                        "syncVersion" to System.currentTimeMillis(),
                        "lastModified" to profile.lastModified,
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                )
            }
            writeBatch.commit().await()
            socialProfileDao.markAsClean(batch.map { it.userId }, userId, System.currentTimeMillis())
            results.addAll(batch.map {
                SyncOperationResult(
                    operation = SyncOperation(ENTITY_SOCIAL_PROFILE, it.userId, "UPDATE", PRIORITY_MEDIUM, userId),
                    success = true
                )
            })
        }
        results
    }.getOrElse { error ->
        Timber.e(error, "SyncOperationManager: Social profile sync failed for user $userId")
        listOf(SyncOperationResult(SyncOperation(ENTITY_SOCIAL_PROFILE, userId, "UPDATE", PRIORITY_MEDIUM, userId), false, error.message))
    }

    private suspend fun syncFollowRelationships(
        userId: String,
        cancellationCheck: (suspend () -> Unit)?
    ): List<SyncOperationResult> = runCatching {
        val useDirtyFlagGating = OfflineArchitectureFlags.ROOM_FIRST_ENABLED &&
            OfflineArchitectureFlags.USE_DIRTY_FLAG_GATING
        val relationships = if (useDirtyFlagGating) {
            followRelationshipDao.getDirtyFollowRelationships(userId)
        } else {
            followRelationshipDao.getUnsyncedRelationships(userId)
        }
        if (relationships.isEmpty()) return@runCatching emptyList()

        val results = mutableListOf<SyncOperationResult>()
        for (batch in relationships.chunked(SOCIAL_BATCH_SIZE)) {
            cancellationCheck?.invoke()
            val writeBatch = firestore.batch()
            batch.forEach { relationship ->
                val remoteRelationshipId = relationshipId(relationship.followerId, relationship.followingId)
                writeBatch.set(
                    firestore.collection("follow_relationships").document(remoteRelationshipId),
                    relationship.toFirestoreMap(remoteRelationshipId),
                    SetOptions.merge()
                )
            }
            writeBatch.commit().await()
            followRelationshipDao.markAsClean(batch.map { it.id }, userId, System.currentTimeMillis())
            results.addAll(batch.map {
                SyncOperationResult(
                    operation = SyncOperation(ENTITY_FOLLOW_RELATIONSHIP, it.id, "UPDATE", PRIORITY_MEDIUM, userId),
                    success = true
                )
            })
        }
        results
    }.getOrElse { error ->
        Timber.e(error, "SyncOperationManager: Follow relationship sync failed for user $userId")
        listOf(SyncOperationResult(SyncOperation(ENTITY_FOLLOW_RELATIONSHIP, userId, "UPDATE", PRIORITY_MEDIUM, userId), false, error.message))
    }

    private suspend fun syncWorkoutPosts(
        userId: String,
        cancellationCheck: (suspend () -> Unit)?
    ): List<SyncOperationResult> = runCatching {
        val useDirtyFlagGating = OfflineArchitectureFlags.ROOM_FIRST_ENABLED &&
            OfflineArchitectureFlags.USE_DIRTY_FLAG_GATING
        val posts = if (useDirtyFlagGating) {
            (workoutPostDao.getDirtyPosts(userId) + workoutPostDao.getUnsyncedPosts(userId)).distinctBy { it.id }
        } else {
            workoutPostDao.getUnsyncedPosts(userId)
        }
        if (posts.isEmpty()) return@runCatching emptyList()

        val results = mutableListOf<SyncOperationResult>()
        for (batch in posts.chunked(10)) {
            cancellationCheck?.invoke()
            val writeBatch = firestore.batch()
            batch.forEach { post ->
                writeBatch.set(
                    firestore.collection("workout_posts").document(post.id),
                    post.toFirestoreMap(),
                    SetOptions.merge()
                )
            }
            writeBatch.commit().await()
            workoutPostDao.markAsClean(batch.map { it.id }, userId, System.currentTimeMillis())
            results.addAll(batch.map {
                SyncOperationResult(
                    operation = SyncOperation(ENTITY_WORKOUT_POST, it.id, "UPDATE", PRIORITY_MEDIUM, userId),
                    success = true
                )
            })
        }
        results
    }.getOrElse { error ->
        Timber.e(error, "SyncOperationManager: Workout post sync failed for user $userId")
        listOf(SyncOperationResult(SyncOperation(ENTITY_WORKOUT_POST, userId, "UPDATE", PRIORITY_MEDIUM, userId), false, error.message))
    }

    private suspend fun syncGymBuddies(
        userId: String,
        cancellationCheck: (suspend () -> Unit)?
    ): List<SyncOperationResult> = runCatching {
        val useDirtyFlagGating = OfflineArchitectureFlags.ROOM_FIRST_ENABLED &&
            OfflineArchitectureFlags.USE_DIRTY_FLAG_GATING
        val buddies = if (useDirtyFlagGating) {
            gymBuddyDao.getDirtyGymBuddies(userId)
        } else {
            gymBuddyDao.getUnsyncedGymBuddies(userId)
        }.take(5)
        if (buddies.isEmpty()) return@runCatching emptyList()

        val results = mutableListOf<SyncOperationResult>()
        val writeBatch = firestore.batch()
        buddies.forEach { buddy ->
            cancellationCheck?.invoke()
            val userBuddyRef = firestore.collection("users").document(userId)
                .collection("gym_buddies").document(buddy.buddyId)
            val buddyUserRef = firestore.collection("users").document(buddy.buddyId)
                .collection("gym_buddies").document(userId)
            writeBatch.set(userBuddyRef, buddy.toFirestoreMap(userId), SetOptions.merge())
            writeBatch.set(
                buddyUserRef,
                buddy.toFirestoreMap(userId).toMutableMap().apply {
                    put("buddyId", userId)
                    remove("nickname")
                },
                SetOptions.merge()
            )
        }
        writeBatch.commit().await()
        gymBuddyDao.markAsClean(buddies.map { it.id }, userId, System.currentTimeMillis())
        results.addAll(buddies.map {
            SyncOperationResult(
                operation = SyncOperation(ENTITY_GYM_BUDDY, it.id, "UPDATE", PRIORITY_MEDIUM, userId),
                success = true
            )
        })
        results
    }.getOrElse { error ->
        Timber.e(error, "SyncOperationManager: Gym buddy sync failed for user $userId")
        listOf(SyncOperationResult(SyncOperation(ENTITY_GYM_BUDDY, userId, "UPDATE", PRIORITY_MEDIUM, userId), false, error.message))
    }

    private suspend fun syncSettings(
        userId: String,
        cancellationCheck: (suspend () -> Unit)?
    ): List<SyncOperationResult> = runCatching {
        cancellationCheck?.invoke()
        val useDirtyFlagGating = OfflineArchitectureFlags.ROOM_FIRST_ENABLED &&
            OfflineArchitectureFlags.USE_DIRTY_FLAG_GATING
        val settingsToUpload = if (useDirtyFlagGating) {
            settingsDao.getDirtySettings(userId)
        } else {
            settingsDao.getSettingsForUser(userId)?.let { settings ->
                if (!settings.isSynced) listOf(settings) else emptyList()
            } ?: emptyList()
        }
        if (settingsToUpload.isEmpty()) return@runCatching emptyList()

        val docRef = firestore.collection("users").document(userId)
            .collection("settings").document("preferences")
        val remoteLastModified = docRef.get().await().let { doc ->
            if (doc.exists()) millisFromFirestoreValue(doc.get("lastModified")) ?: 0L else 0L
        }

        val results = mutableListOf<SyncOperationResult>()
        settingsToUpload.forEach { settings ->
            if (remoteLastModified <= settings.lastModified) {
                docRef.set(settings.toFirestoreMap(userId), SetOptions.merge()).await()
                settingsDao.markAsClean(listOf(userId), userId, System.currentTimeMillis())
                results.add(SyncOperationResult(SyncOperation(ENTITY_SETTINGS, userId, "UPDATE", PRIORITY_MEDIUM, userId), true))
            } else {
                results.add(SyncOperationResult(SyncOperation(ENTITY_SETTINGS, userId, "UPDATE", PRIORITY_MEDIUM, userId), true, itemsProcessed = 0))
            }
        }
        results
    }.getOrElse { error ->
        Timber.e(error, "SyncOperationManager: Settings sync failed for user $userId")
        listOf(SyncOperationResult(SyncOperation(ENTITY_SETTINGS, userId, "UPDATE", PRIORITY_MEDIUM, userId), false, error.message))
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

    private suspend fun restoreRemoteStateForStartup(
        userId: String,
        cancellationCheck: (suspend () -> Unit)?
    ): List<SyncOperationResult> {
        val results = mutableListOf<SyncOperationResult>()
        results.add(restoreRemoteWorkoutsForStartup(userId, cancellationCheck))
        results.add(restoreRemoteTemplatesForStartup(userId, cancellationCheck))
        results.add(restoreRemoteAchievementsForStartup(userId, cancellationCheck))
        return results
    }

    private suspend fun restoreRemoteWorkoutsForStartup(
        userId: String,
        cancellationCheck: (suspend () -> Unit)?
    ): SyncOperationResult {
        val operation = SyncOperation(
            entityType = ENTITY_WORKOUT,
            entityId = "startup_restore_workouts",
            operation = "FETCH",
            priority = PRIORITY_HIGH,
            userId = userId
        )
        return try {
            cancellationCheck?.invoke()
            val localBefore = workoutDao.getWorkoutCountForUser(userId)
            val remoteDocs = firestore
                .collection("users")
                .document(userId)
                .collection("workouts")
                .get()
                .await()

            var restored = 0
            var skipped = 0
            remoteDocs.documents.forEach { doc ->
                cancellationCheck?.invoke()
                try {
                    val remoteDto = doc.toObject(WorkoutDto::class.java)
                        ?.copy(
                            id = doc.id,
                            userId = userId
                        )
                    if (remoteDto == null || remoteDto.status.isBlank()) {
                        skipped++
                        return@forEach
                    }

                    val remoteLastModified = millisFromFirestoreValue(remoteDto.lastModified)
                        ?: millisFromFirestoreValue(remoteDto.updatedAt)
                        ?: System.currentTimeMillis()
                    val remoteEntity = workoutMapper
                        .firestoreDtoToEntity(remoteDto, isSynced = true)
                        .copy(
                            userId = userId,
                            isDirty = false,
                            isSynced = true,
                            syncVersion = remoteDto.syncVersion.coerceAtLeast(remoteDto.version),
                            lastModified = remoteLastModified
                        )

                    upsertRemoteWorkoutAndRefreshReadModels(remoteEntity)
                    restored++
                } catch (e: Exception) {
                    skipped++
                    Timber.w(e, "SyncOperationManager: Failed to restore remote workout ${doc.id}")
                }
            }

            if (remoteDocs.isEmpty && localBefore > 0) {
                val markedDirty = workoutDao.markAllDirtyForUser(userId)
                Timber.tag("StartupRestoreFix").i(
                    "operation=UNIFIED_STARTUP_EMPTY_REMOTE_MARK_LOCAL_DIRTY userId=$userId entityType=WORKOUT localBefore=$localBefore markedDirty=$markedDirty timestamp=${System.currentTimeMillis()}"
                )
            }

            Timber.tag("StartupRestoreFix").i(
                "operation=UNIFIED_STARTUP_RESTORE_ENTITY_FINISH userId=$userId entityType=WORKOUT remoteCount=${remoteDocs.size()} restored=$restored skipped=$skipped localBefore=$localBefore localAfter=${workoutDao.getWorkoutCountForUser(userId)} timestamp=${System.currentTimeMillis()}"
            )
            SyncOperationResult(operation, success = true, itemsProcessed = restored)
        } catch (e: Exception) {
            Timber.e(e, "SyncOperationManager: Startup workout restore failed for user $userId")
            SyncOperationResult(operation, success = false, error = e.message)
        }
    }

    private suspend fun restoreRemoteTemplatesForStartup(
        userId: String,
        cancellationCheck: (suspend () -> Unit)?
    ): SyncOperationResult {
        val operation = SyncOperation(
            entityType = ENTITY_TEMPLATE,
            entityId = "startup_restore_templates",
            operation = "FETCH",
            priority = PRIORITY_HIGH,
            userId = userId
        )
        return try {
            cancellationCheck?.invoke()
            val localBefore = workoutTemplateDao.getTemplateCount(userId)
            val remoteDocs = firestore
                .collection("users")
                .document(userId)
                .collection("templates")
                .get()
                .await()

            var restored = 0
            var skipped = 0
            remoteDocs.documents.forEach { doc ->
                cancellationCheck?.invoke()
                try {
                    if (doc.getBooleanFlag("deleted") || doc.getBooleanFlag("isDeleted") || doc.getBooleanFlag("archived") || doc.getBooleanFlag("isArchived")) {
                        skipped++
                        return@forEach
                    }
                    val templateId = doc.getString("id")?.takeIf { it.isNotBlank() } ?: doc.id
                    val exercisesJson = when (val remoteExercises = doc.get("exercises") ?: doc.get("templateExercisesJson")) {
                        is List<*> -> gson.toJson(remoteExercises)
                        is String -> remoteExercises
                        else -> ""
                    }
                    val createdAt = instantFromFirestoreValue(doc.get("createdAt")) ?: Instant.now()
                    val updatedAt = instantFromFirestoreValue(doc.get("updatedAt")) ?: createdAt
                    val lastModified = millisFromFirestoreValue(doc.get("lastModified"))
                        ?: updatedAt.toEpochMilli()
                    val remoteEntity = WorkoutTemplateEntity(
                        id = templateId,
                        userId = userId,
                        name = doc.getString("name")?.takeIf { it.isNotBlank() } ?: "Untitled Template",
                        description = doc.getString("description"),
                        templateExercisesJson = exercisesJson,
                        estimatedDurationMinutes = doc.getLong("estimatedDurationMinutes")?.toInt(),
                        difficultyLevel = doc.getLong("difficultyLevel")?.toInt(),
                        folderId = doc.getString("folderId"),
                        usageCount = doc.getLong("usageCount")?.toInt() ?: 0,
                        lastUsedAt = instantFromFirestoreValue(doc.get("lastUsedAt")),
                        createdAt = createdAt,
                        updatedAt = updatedAt,
                        isSynced = true,
                        syncVersion = doc.getLong("syncVersion") ?: System.currentTimeMillis(),
                        isDirty = false,
                        lastModified = lastModified
                    )

                    workoutTemplateDao.upsertFromRemote(remoteEntity)
                    restored++
                } catch (e: Exception) {
                    skipped++
                    Timber.w(e, "SyncOperationManager: Failed to restore remote template ${doc.id}")
                }
            }

            Timber.tag("StartupRestoreFix").i(
                "operation=UNIFIED_STARTUP_RESTORE_ENTITY_FINISH userId=$userId entityType=TEMPLATE remoteCount=${remoteDocs.size()} restored=$restored skipped=$skipped localBefore=$localBefore localAfter=${workoutTemplateDao.getTemplateCount(userId)} timestamp=${System.currentTimeMillis()}"
            )
            SyncOperationResult(operation, success = true, itemsProcessed = restored)
        } catch (e: Exception) {
            Timber.e(e, "SyncOperationManager: Startup template restore failed for user $userId")
            SyncOperationResult(operation, success = false, error = e.message)
        }
    }

    private suspend fun restoreRemoteAchievementsForStartup(
        userId: String,
        cancellationCheck: (suspend () -> Unit)?
    ): SyncOperationResult {
        val operation = SyncOperation(
            entityType = ENTITY_ACHIEVEMENT,
            entityId = "startup_restore_achievements",
            operation = "FETCH",
            priority = PRIORITY_LOW,
            userId = userId
        )
        return try {
            cancellationCheck?.invoke()
            val remoteDocs = firestore
                .collection("users")
                .document(userId)
                .collection("achievements")
                .get()
                .await()

            Timber.tag("StartupRestoreFix").i(
                "operation=UNIFIED_STARTUP_RESTORE_ENTITY_FETCHED userId=$userId entityType=ACHIEVEMENT remoteCount=${remoteDocs.size()} timestamp=${System.currentTimeMillis()}"
            )
            SyncOperationResult(operation, success = true, itemsProcessed = remoteDocs.size())
        } catch (e: Exception) {
            Timber.e(e, "SyncOperationManager: Startup achievement restore failed for user $userId")
            SyncOperationResult(operation, success = false, error = e.message)
        }
    }

    private fun DocumentSnapshot.getBooleanFlag(field: String): Boolean {
        return when (val value = get(field)) {
            is Boolean -> value
            is String -> value.equals("true", ignoreCase = true)
            is Number -> value.toInt() != 0
            else -> false
        }
    }

    private fun instantFromFirestoreValue(value: Any?): Instant? {
        return when (value) {
            is Timestamp -> value.toDate().toInstant()
            is Long -> Instant.ofEpochMilli(value)
            is Int -> Instant.ofEpochMilli(value.toLong())
            is Number -> Instant.ofEpochMilli(value.toLong())
            else -> null
        }
    }

    private fun millisFromFirestoreValue(value: Any?): Long? {
        return when (value) {
            is Timestamp -> value.toDate().time
            is Long -> value
            is Int -> value.toLong()
            is Number -> value.toLong()
            else -> null
        }
    }

    private fun relationshipId(followerId: String, followingId: String): String =
        "${followerId}_follows_$followingId"

    private fun buildSearchTokens(vararg values: String?): List<String> {
        return values
            .filterNotNull()
            .flatMap { value ->
                value.lowercase()
                    .split(Regex("[^a-z0-9]+"))
                    .filter { it.isNotBlank() }
            }
            .distinct()
    }

    private fun parseStoredJsonArray(value: String?): List<String> {
        if (value.isNullOrBlank()) return emptyList()
        return runCatching {
            gson.fromJson(value, Array<String>::class.java).filterNotNull()
        }.getOrElse {
            value.split(",").map { item -> item.trim() }.filter { it.isNotBlank() }
        }
    }

    private fun FollowRelationshipEntity.toFirestoreMap(remoteRelationshipId: String): Map<String, Any?> = mapOf(
        "id" to remoteRelationshipId,
        "followerId" to followerId,
        "followingId" to followingId,
        "status" to status,
        "createdAt" to createdAt,
        "acceptedAt" to acceptedAt,
        "blockedAt" to blockedAt,
        "syncVersion" to System.currentTimeMillis(),
        "lastModified" to lastModified,
        "updatedAt" to FieldValue.serverTimestamp()
    )

    private fun WorkoutPostEntity.toFirestoreMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "userId" to userId,
        "workoutId" to workoutId,
        "caption" to caption,
        "mediaUrls" to parseStoredJsonArray(mediaUrls),
        "mediaThumbnails" to parseStoredJsonArray(mediaThumbnails),
        "visibility" to visibility,
        "likeCount" to likeCount,
        "commentCount" to commentCount,
        "shareCount" to shareCount,
        "saveCount" to saveCount,
        "workoutDuration" to workoutDuration,
        "totalVolume" to totalVolume,
        "exercisesCount" to exercisesCount,
        "prsCount" to prsCount,
        "createdAt" to createdAt,
        "updatedAt" to FieldValue.serverTimestamp(),
        "syncVersion" to System.currentTimeMillis(),
        "lastModified" to lastModified,
        "isHidden" to isHidden,
        "hiddenReason" to hiddenReason,
        "hiddenAt" to hiddenAt,
        "hiddenByUserId" to hiddenByUserId,
        "isDeleted" to false
    )

    private fun GymBuddyEntity.toFirestoreMap(ownerUserId: String): MutableMap<String, Any?> = mutableMapOf(
        "userId" to ownerUserId,
        "buddyId" to buddyId,
        "nickname" to buddyNickname,
        "pairedViaQr" to pairedViaQr,
        "pairingLocation" to pairingLocation,
        "prNotificationsEnabled" to true,
        "createdAt" to createdAt,
        "lastPrNotificationSent" to lastPrNotificationSent,
        "notificationCooldownHours" to notificationCooldownHours,
        "syncVersion" to System.currentTimeMillis(),
        "lastModified" to lastModified,
        "updatedAt" to FieldValue.serverTimestamp()
    )

    private fun SettingsEntity.toFirestoreMap(userId: String): Map<String, Any?> = mapOf(
        "userId" to userId,
        "weightUnit" to weightUnit.toString(),
        "distanceUnit" to distanceUnit,
        "darkMode" to darkMode,
        "notifications" to notificationsEnabled,
        "terminologyPreference" to terminologyPreference,
        "migrationCompleted" to migrationCompleted,
        "migrationExplanationSeen" to migrationExplanationSeen,
        "settingsVersion" to settingsVersion,
        "privateProfile" to privateProfile,
        "hideStats" to hideStats,
        "allowMessages" to allowMessages,
        "autoPlayVideos" to autoPlayVideos,
        "lastSyncTimestamp" to lastSyncTimestamp,
        "lastModified" to lastModified,
        "syncVersion" to System.currentTimeMillis(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
    
    /**
     * Get the priority level for an entity type
     */
    fun getEntityPriority(entityType: String): Int = when (entityType.uppercase()) {
        ENTITY_PROFILE -> PRIORITY_CRITICAL
        ENTITY_WORKOUT, ENTITY_TEMPLATE -> PRIORITY_HIGH
        ENTITY_USER_PUBLIC -> PRIORITY_CRITICAL
        ENTITY_SOCIAL_PROFILE, ENTITY_FOLLOW_RELATIONSHIP, ENTITY_WORKOUT_POST, ENTITY_GYM_BUDDY, ENTITY_SETTINGS -> PRIORITY_MEDIUM
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
