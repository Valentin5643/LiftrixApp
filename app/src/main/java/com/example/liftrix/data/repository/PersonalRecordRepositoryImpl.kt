package com.example.liftrix.data.repository

import com.example.liftrix.data.local.dao.PersonalRecordDao
import com.example.liftrix.data.local.entity.PersonalRecordEntity
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.PersonalRecordRepository
import com.example.liftrix.domain.repository.PRSyncStatus
import com.example.liftrix.domain.service.PersonalRecord
import com.example.liftrix.domain.service.PRType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of PersonalRecordRepository using Room database
 * 
 * Provides user-scoped personal record operations with proper error handling
 * Uses LiftrixResult pattern for consistent error management
 * Includes comprehensive logging for debugging
 * 
 * CRITICAL: All operations filter by userId to prevent data leakage
 */
@Singleton
class PersonalRecordRepositoryImpl @Inject constructor(
    private val personalRecordDao: PersonalRecordDao
) : PersonalRecordRepository {

    override suspend fun savePR(
        personalRecord: PersonalRecord,
        userId: String,
        workoutId: String
    ): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.DatabaseError(
                errorMessage = "Failed to save personal record: ${throwable.message}",
                analyticsContext = mapOf(
                    "operation" to "SAVE_PERSONAL_RECORD",
                    "user_id" to userId,
                    "exercise_name" to personalRecord.exerciseName,
                    "pr_type" to personalRecord.prType.name
                )
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            Timber.d("Saving PR for user $userId: ${personalRecord.exerciseName} - ${personalRecord.prType}")
            
            val entity = PersonalRecordEntity.fromDomain(personalRecord, userId, workoutId)
            val result = personalRecordDao.insertPR(entity)
            
            Timber.d("PR saved successfully with ID: ${entity.id}, row ID: $result")
        }
    }

    override suspend fun savePRs(
        personalRecords: List<PersonalRecord>,
        userId: String,
        workoutId: String
    ): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.DatabaseError(
                errorMessage = "Failed to save personal records: ${throwable.message}",
                analyticsContext = mapOf(
                    "operation" to "SAVE_PERSONAL_RECORDS",
                    "user_id" to userId,
                    "workout_id" to workoutId,
                    "pr_count" to personalRecords.size.toString()
                )
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            Timber.d("Saving ${personalRecords.size} PRs for user $userId in workout $workoutId")
            
            val entities = personalRecords.map { pr ->
                PersonalRecordEntity.fromDomain(pr, userId, workoutId)
            }
            
            val results = personalRecordDao.insertPRs(entities)
            
            Timber.d("${results.size} PRs saved successfully")
        }
    }

    override suspend fun getBestPR(
        userId: String,
        exerciseName: String,
        prType: PRType
    ): LiftrixResult<PersonalRecord?> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.DataRetrievalError(
                errorMessage = "Failed to get best PR: ${throwable.message}",
                analyticsContext = mapOf(
                    "operation" to "GET_BEST_PR",
                    "user_id" to userId,
                    "exercise_name" to exerciseName,
                    "pr_type" to prType.name
                )
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            val entity = personalRecordDao.getBestPRByType(userId, exerciseName, prType.name)
            entity?.toDomain()
        }
    }

    override suspend fun getBest1RM(
        userId: String,
        exerciseName: String
    ): LiftrixResult<PersonalRecord?> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.DataRetrievalError(
                errorMessage = "Failed to get best 1RM: ${throwable.message}",
                analyticsContext = mapOf(
                    "operation" to "GET_BEST_1RM",
                    "user_id" to userId,
                    "exercise_name" to exerciseName
                )
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            val entity = personalRecordDao.getBest1RM(userId, exerciseName)
            entity?.toDomain()
        }
    }

    override suspend fun getBestVolume(
        userId: String,
        exerciseName: String
    ): LiftrixResult<PersonalRecord?> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.DataRetrievalError(
                errorMessage = "Failed to get best volume: ${throwable.message}",
                analyticsContext = mapOf(
                    "operation" to "GET_BEST_VOLUME",
                    "user_id" to userId,
                    "exercise_name" to exerciseName
                )
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            val entity = personalRecordDao.getBestVolume(userId, exerciseName)
            entity?.toDomain()
        }
    }

    override suspend fun getBestReps(
        userId: String,
        exerciseName: String
    ): LiftrixResult<PersonalRecord?> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.DataRetrievalError(
                errorMessage = "Failed to get best reps: ${throwable.message}",
                analyticsContext = mapOf(
                    "operation" to "GET_BEST_REPS",
                    "user_id" to userId,
                    "exercise_name" to exerciseName
                )
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            val entity = personalRecordDao.getBestReps(userId, exerciseName)
            entity?.toDomain()
        }
    }

    override suspend fun getBestWeight(
        userId: String,
        exerciseName: String
    ): LiftrixResult<PersonalRecord?> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.DataRetrievalError(
                errorMessage = "Failed to get best weight: ${throwable.message}",
                analyticsContext = mapOf(
                    "operation" to "GET_BEST_WEIGHT",
                    "user_id" to userId,
                    "exercise_name" to exerciseName
                )
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            val entity = personalRecordDao.getBestWeight(userId, exerciseName)
            entity?.toDomain()
        }
    }

    override suspend fun getPRsForExercise(
        userId: String,
        exerciseName: String
    ): LiftrixResult<List<PersonalRecord>> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.DataRetrievalError(
                errorMessage = "Failed to get PRs for exercise: ${throwable.message}",
                analyticsContext = mapOf(
                    "operation" to "GET_PRS_FOR_EXERCISE",
                    "user_id" to userId,
                    "exercise_name" to exerciseName
                )
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            val entities = personalRecordDao.getPRsForExercise(userId, exerciseName)
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getRecentPRs(
        userId: String,
        limit: Int
    ): LiftrixResult<List<PersonalRecord>> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.DataRetrievalError(
                errorMessage = "Failed to get recent PRs: ${throwable.message}",
                analyticsContext = mapOf(
                    "operation" to "GET_RECENT_PRS",
                    "user_id" to userId,
                    "limit" to limit.toString()
                )
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            val entities = personalRecordDao.getRecentPRs(userId, limit = limit)
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getPRsInDateRange(
        userId: String,
        startDate: Long,
        endDate: Long
    ): LiftrixResult<List<PersonalRecord>> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.DataRetrievalError(
                errorMessage = "Failed to get PRs in date range: ${throwable.message}",
                analyticsContext = mapOf(
                    "operation" to "GET_PRS_IN_DATE_RANGE",
                    "user_id" to userId,
                    "start_date" to startDate.toString(),
                    "end_date" to endDate.toString()
                )
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            val entities = personalRecordDao.getPRsInDateRange(userId, startDate, endDate)
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getPRsForWorkout(
        userId: String,
        workoutId: String
    ): LiftrixResult<List<PersonalRecord>> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.DataRetrievalError(
                errorMessage = "Failed to get PRs for workout: ${throwable.message}",
                analyticsContext = mapOf(
                    "operation" to "GET_PRS_FOR_WORKOUT",
                    "user_id" to userId,
                    "workout_id" to workoutId
                )
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            val entities = personalRecordDao.getPRsForWorkout(userId, workoutId)
            entities.map { it.toDomain() }
        }
    }

    override fun observePRsForUser(userId: String): Flow<List<PersonalRecord>> {
        return personalRecordDao.observePRsForUser(userId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getPRCountsByExercise(userId: String): LiftrixResult<Map<String, Int>> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.DataRetrievalError(
                errorMessage = "Failed to get PR counts by exercise: ${throwable.message}",
                analyticsContext = mapOf(
                    "operation" to "GET_PR_COUNTS_BY_EXERCISE",
                    "user_id" to userId
                )
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            val results = personalRecordDao.getPRCountsByExercise(userId)
            results.associate { it.exercise_name to it.pr_count }
        }
    }

    override suspend fun deletePR(prId: String, userId: String): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.DatabaseError(
                errorMessage = "Failed to delete personal record: ${throwable.message}",
                analyticsContext = mapOf(
                    "operation" to "DELETE_PERSONAL_RECORD",
                    "user_id" to userId,
                    "pr_id" to prId
                )
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            val deletedRows = personalRecordDao.deletePR(prId, userId)
            if (deletedRows == 0) {
                throw IllegalArgumentException("Personal record not found or access denied")
            }
            Timber.d("Deleted PR $prId for user $userId")
        }
    }

    override suspend fun deleteAllPRsForUser(userId: String): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.DatabaseError(
                errorMessage = "Failed to delete all personal records: ${throwable.message}",
                analyticsContext = mapOf(
                    "operation" to "DELETE_ALL_PERSONAL_RECORDS",
                    "user_id" to userId
                )
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            val deletedRows = personalRecordDao.deleteAllPRsForUser(userId)
            Timber.d("Deleted $deletedRows PRs for user $userId")
        }
    }

    override suspend fun getSyncStatus(userId: String): LiftrixResult<com.example.liftrix.domain.repository.PRSyncStatus> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.DataRetrievalError(
                errorMessage = "Failed to get sync status: ${throwable.message}",
                analyticsContext = mapOf(
                    "operation" to "GET_SYNC_STATUS",
                    "user_id" to userId
                )
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            val status = personalRecordDao.getSyncStatus(userId)
            com.example.liftrix.domain.repository.PRSyncStatus(
                syncedCount = status.synced_count,
                unsyncedCount = status.unsynced_count,
                totalCount = status.total_count
            )
        }
    }
}