package com.example.liftrix.sync

import android.content.Context
import androidx.room.Room
import com.example.liftrix.data.local.LiftrixDatabase
import com.example.liftrix.domain.repository.SyncStatusRepository
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import timber.log.Timber

/**
 * 🔧 HOTFIX: Service locator for worker dependencies when Hilt factory fails.
 * This provides fallback dependency resolution for workers that can't be instantiated via Hilt.
 * 
 * TEMPORARY: Remove this once Hilt assisted factories are confirmed working.
 */
object WorkerServiceLocator {
    
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WorkerDependencies {
        fun syncStatusRepository(): SyncStatusRepository
        fun database(): com.example.liftrix.data.local.LiftrixDatabase
        fun userProfileMapper(): com.example.liftrix.data.mapper.UserProfileMapper
        fun workoutMapper(): com.example.liftrix.data.mapper.WorkoutMapper
        fun exerciseMapper(): com.example.liftrix.data.mapper.ExerciseMapper
        fun conflictResolver(): com.example.liftrix.service.sync.ConflictResolver
        fun firestore(): com.google.firebase.firestore.FirebaseFirestore
        fun firebaseAuth(): com.google.firebase.auth.FirebaseAuth
        fun gson(): com.google.gson.Gson
        fun followRepository(): com.example.liftrix.data.repository.social.FollowRepositoryImpl
        fun profileCleanupService(): com.example.liftrix.data.service.ProfileCleanupService
        fun cleanupMetricsCollector(): com.example.liftrix.analytics.CleanupMetricsCollector
        fun achievementDao(): com.example.liftrix.data.local.dao.AchievementDao
        fun offlineQueueManager(): com.example.liftrix.data.sync.OfflineQueueManager
        fun syncOperationManager(): SyncOperationManager
    }
    
    private fun getEntryPoint(context: Context): WorkerDependencies {
        return EntryPointAccessors.fromApplication(
            context.applicationContext,
            WorkerDependencies::class.java
        )
    }
    
    /**
     * Get SyncStatusRepository via Hilt EntryPoint (safer than manual construction)
     */
    fun getSyncStatusRepository(context: Context): SyncStatusRepository {
        return try {
            getEntryPoint(context).syncStatusRepository()
        } catch (e: Exception) {
            Timber.e(e, "WorkerServiceLocator: Failed to get SyncStatusRepository via Hilt")
            throw IllegalStateException("Cannot provide SyncStatusRepository for worker fallback", e)
        }
    }
    
    /**
     * Get ProfileSyncWorker dependencies bundle
     */
    data class ProfileSyncDependencies(
        val userProfileDao: com.example.liftrix.data.local.dao.UserProfileDao,
        val workoutDao: com.example.liftrix.data.local.dao.WorkoutDao,
        val achievementDao: com.example.liftrix.data.local.dao.AchievementDao,
        val userProfileMapper: com.example.liftrix.data.mapper.UserProfileMapper,
        val firestore: com.google.firebase.firestore.FirebaseFirestore,
        val auth: com.google.firebase.auth.FirebaseAuth,
        val gson: com.google.gson.Gson,
        val followRepository: com.example.liftrix.data.repository.social.FollowRepositoryImpl,
        val profileCleanupService: com.example.liftrix.data.service.ProfileCleanupService
    )
    
    fun getProfileSyncDependencies(context: Context): ProfileSyncDependencies {
        return try {
            val entryPoint = getEntryPoint(context)
            val database = entryPoint.database()
            ProfileSyncDependencies(
                userProfileDao = database.userProfileDao(),
                workoutDao = database.workoutDao(),
                achievementDao = database.achievementDao(),
                userProfileMapper = entryPoint.userProfileMapper(),
                firestore = entryPoint.firestore(),
                auth = entryPoint.firebaseAuth(),
                gson = entryPoint.gson(),
                followRepository = entryPoint.followRepository(),
                profileCleanupService = entryPoint.profileCleanupService()
            )
        } catch (e: Exception) {
            Timber.e(e, "WorkerServiceLocator: Failed to get ProfileSync dependencies via Hilt")
            throw IllegalStateException("Cannot provide ProfileSync dependencies for worker fallback", e)
        }
    }
    
    /**
     * Get FollowRelationshipSyncWorker dependencies bundle
     */
    data class FollowRelationshipSyncDependencies(
        val followDao: com.example.liftrix.data.local.dao.FollowRelationshipDao,
        val socialProfileDao: com.example.liftrix.data.local.dao.SocialProfileDao,
        val safeFollowDao: com.example.liftrix.data.local.dao.SafeFollowRelationshipDaoImpl,
        val firestore: com.google.firebase.firestore.FirebaseFirestore
    )
    
    fun getFollowRelationshipSyncDependencies(context: Context): FollowRelationshipSyncDependencies {
        return try {
            val entryPoint = getEntryPoint(context)
            val database = entryPoint.database()
            val followDao = database.followRelationshipDao()
            val socialProfileDao = database.socialProfileDao()
            val safeFollowDao = com.example.liftrix.data.local.dao.SafeFollowRelationshipDaoImpl(
                database
            )
            FollowRelationshipSyncDependencies(
                followDao = followDao,
                socialProfileDao = socialProfileDao,
                safeFollowDao = safeFollowDao,
                firestore = entryPoint.firestore()
            )
        } catch (e: Exception) {
            Timber.e(e, "WorkerServiceLocator: Failed to get FollowRelationshipSync dependencies via Hilt")
            throw IllegalStateException("Cannot provide FollowRelationshipSync dependencies for worker fallback", e)
        }
    }

    /**
     * Get WorkoutSyncWorker dependencies bundle
     */
    data class WorkoutSyncDependencies(
        val workoutDao: com.example.liftrix.data.local.dao.WorkoutDao,
        val workoutMapper: com.example.liftrix.data.mapper.WorkoutMapper,
        val firestore: com.google.firebase.firestore.FirebaseFirestore,
        val auth: com.google.firebase.auth.FirebaseAuth,
        val conflictResolver: com.example.liftrix.service.sync.ConflictResolver
    )
    
    fun getWorkoutSyncDependencies(context: Context): WorkoutSyncDependencies {
        return try {
            val entryPoint = getEntryPoint(context)
            val database = entryPoint.database()
            WorkoutSyncDependencies(
                workoutDao = database.workoutDao(),
                workoutMapper = entryPoint.workoutMapper(),
                firestore = entryPoint.firestore(),
                auth = entryPoint.firebaseAuth(),
                conflictResolver = entryPoint.conflictResolver()
            )
        } catch (e: Exception) {
            Timber.e(e, "WorkerServiceLocator: Failed to get WorkoutSync dependencies via Hilt")
            throw IllegalStateException("Cannot provide WorkoutSync dependencies for worker fallback", e)
        }
    }

    /**
     * Get WorkoutPostSyncWorker dependencies bundle
     */
    data class WorkoutPostSyncDependencies(
        val postDao: com.example.liftrix.data.local.dao.WorkoutPostDao,
        val firestore: com.google.firebase.firestore.FirebaseFirestore,
        val gson: com.google.gson.Gson
    )
    
    fun getWorkoutPostSyncDependencies(context: Context): WorkoutPostSyncDependencies {
        return try {
            val entryPoint = getEntryPoint(context)
            val database = entryPoint.database()
            WorkoutPostSyncDependencies(
                postDao = database.workoutPostDao(),
                firestore = entryPoint.firestore(),
                gson = entryPoint.gson()
            )
        } catch (e: Exception) {
            Timber.e(e, "WorkerServiceLocator: Failed to get WorkoutPostSync dependencies via Hilt")
            throw IllegalStateException("Cannot provide WorkoutPostSync dependencies for worker fallback", e)
        }
    }

    /**
     * Get TemplateSyncWorker dependencies bundle
     */
    data class TemplateSyncDependencies(
        val templateDao: com.example.liftrix.data.local.dao.WorkoutTemplateDao,
        val firestore: com.google.firebase.firestore.FirebaseFirestore,
        val conflictResolver: com.example.liftrix.service.sync.ConflictResolver,
        val auth: com.google.firebase.auth.FirebaseAuth
    )
    
    fun getTemplateSyncDependencies(context: Context): TemplateSyncDependencies {
        return try {
            val entryPoint = getEntryPoint(context)
            val database = entryPoint.database()
            TemplateSyncDependencies(
                templateDao = database.workoutTemplateDao(),
                firestore = entryPoint.firestore(),
                conflictResolver = entryPoint.conflictResolver(),
                auth = entryPoint.firebaseAuth()
            )
        } catch (e: Exception) {
            Timber.e(e, "WorkerServiceLocator: Failed to get TemplateSync dependencies via Hilt")
            throw IllegalStateException("Cannot provide TemplateSync dependencies for worker fallback", e)
        }
    }

    /**
     * Get UserPublicSyncWorker dependencies bundle
     */
    data class UserPublicSyncDependencies(
        val userAccountDao: com.example.liftrix.data.local.dao.UserAccountDao,
        val userProfileDao: com.example.liftrix.data.local.dao.UserProfileDao,
        val workoutDao: com.example.liftrix.data.local.dao.WorkoutDao,
        val firestore: com.google.firebase.firestore.FirebaseFirestore,
        val auth: com.google.firebase.auth.FirebaseAuth,
        val gson: com.google.gson.Gson
    )
    
    fun getUserPublicSyncDependencies(context: Context): UserPublicSyncDependencies {
        return try {
            val entryPoint = getEntryPoint(context)
            val database = entryPoint.database()
            UserPublicSyncDependencies(
                userAccountDao = database.userAccountDao(),
                userProfileDao = database.userProfileDao(),
                workoutDao = database.workoutDao(),
                firestore = entryPoint.firestore(),
                auth = entryPoint.firebaseAuth(),
                gson = entryPoint.gson()
            )
        } catch (e: Exception) {
            Timber.e(e, "WorkerServiceLocator: Failed to get UserPublicSync dependencies via Hilt")
            throw IllegalStateException("Cannot provide UserPublicSync dependencies for worker fallback", e)
        }
    }

    /**
     * Get AchievementSyncWorker dependencies bundle
     */
    data class AchievementSyncDependencies(
        val achievementDao: com.example.liftrix.data.local.dao.AchievementDao,
        val firestore: com.google.firebase.firestore.FirebaseFirestore,
        val firebaseAuth: com.google.firebase.auth.FirebaseAuth
    )
    
    fun getAchievementSyncDependencies(context: Context): AchievementSyncDependencies {
        return try {
            val entryPoint = getEntryPoint(context)
            val database = entryPoint.database()
            AchievementSyncDependencies(
                achievementDao = database.achievementDao(),
                firestore = entryPoint.firestore(),
                firebaseAuth = entryPoint.firebaseAuth()
            )
        } catch (e: Exception) {
            Timber.e(e, "WorkerServiceLocator: Failed to get AchievementSync dependencies via Hilt")
            throw IllegalStateException("Cannot provide AchievementSync dependencies for worker fallback", e)
        }
    }
    
    /**
     * Get UnifiedSyncWorker dependencies bundle
     */
    data class UnifiedSyncDependencies(
        val syncOperationManager: SyncOperationManager,
        val offlineQueueManager: com.example.liftrix.data.sync.OfflineQueueManager
    )
    
    fun getUnifiedSyncDependencies(context: Context): UnifiedSyncDependencies {
        return try {
            val entryPoint = getEntryPoint(context)
            UnifiedSyncDependencies(
                syncOperationManager = entryPoint.syncOperationManager(),
                offlineQueueManager = entryPoint.offlineQueueManager()
            )
        } catch (e: Exception) {
            Timber.e(e, "WorkerServiceLocator: Failed to get UnifiedSync dependencies via Hilt")
            throw IllegalStateException("Cannot provide UnifiedSync dependencies for worker fallback", e)
        }
    }
}