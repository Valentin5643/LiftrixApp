package com.example.liftrix.data.repository

import com.example.liftrix.data.local.dao.AnomalyDetectionSettingsDao
import com.example.liftrix.data.local.dao.ExerciseHistoryDao
import com.example.liftrix.data.local.dao.WorkoutAnomalyDao
import com.example.liftrix.data.mapper.AnomalyDetectionMapper.toDomain
import com.example.liftrix.data.mapper.AnomalyDetectionMapper.toEntity
import com.example.liftrix.data.mapper.AnomalyDetectionMapper.toWorkoutAnomalyDomain
import com.example.liftrix.data.mapper.AnomalyDetectionMapper.toExerciseHistoryDomain
import com.example.liftrix.domain.model.AnomalyDetectionSettings
import com.example.liftrix.domain.model.ExerciseHistory
import com.example.liftrix.domain.model.ExerciseId
import com.example.liftrix.domain.model.WorkoutAnomaly
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.AnomalyDetectionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of AnomalyDetectionRepository using Room database
 */
@Singleton
class AnomalyDetectionRepositoryImpl @Inject constructor(
    private val anomalyDao: WorkoutAnomalyDao,
    private val settingsDao: AnomalyDetectionSettingsDao,
    private val historyDao: ExerciseHistoryDao
) : AnomalyDetectionRepository {

    override suspend fun getDetectionSettings(userId: String): LiftrixResult<AnomalyDetectionSettings?> {
        return try {
            withContext(Dispatchers.IO) {
                val entity = settingsDao.getDetectionSettings(userId)
                LiftrixResult.success(entity?.toDomain())
            }
        } catch (e: Exception) {
            LiftrixResult.failure(
                LiftrixError.DatabaseError(
                    "Failed to get detection settings for user $userId: ${e.message}",
                    isRecoverable = true,
                    retryAfter = 1000L
                )
            )
        }
    }

    override suspend fun saveDetectionSettings(settings: AnomalyDetectionSettings): LiftrixResult<Unit> {
        return try {
            withContext(Dispatchers.IO) {
                settingsDao.insertOrUpdate(settings.toEntity())
                LiftrixResult.success(Unit)
            }
        } catch (e: Exception) {
            LiftrixResult.failure(
                LiftrixError.DatabaseError(
                    "Failed to save detection settings: ${e.message}",
                    isRecoverable = true,
                    retryAfter = 1000L
                )
            )
        }
    }

    override suspend fun getExerciseHistory(userId: String, exerciseId: ExerciseId): LiftrixResult<ExerciseHistory?> {
        return try {
            withContext(Dispatchers.IO) {
                val entity = historyDao.getExerciseHistory(userId, exerciseId.value)
                LiftrixResult.success(entity?.toDomain())
            }
        } catch (e: Exception) {
            LiftrixResult.failure(
                LiftrixError.DatabaseError(
                    "Failed to get exercise history for user $userId, exercise ${exerciseId.value}: ${e.message}",
                    isRecoverable = true,
                    retryAfter = 1000L
                )
            )
        }
    }

    override suspend fun saveExerciseHistory(history: ExerciseHistory): LiftrixResult<Unit> {
        return try {
            withContext(Dispatchers.IO) {
                historyDao.insertOrUpdate(history.toEntity())
                LiftrixResult.success(Unit)
            }
        } catch (e: Exception) {
            LiftrixResult.failure(
                LiftrixError.DatabaseError(
                    "Failed to save exercise history: ${e.message}",
                    isRecoverable = true,
                    retryAfter = 1000L
                )
            )
        }
    }

    override suspend fun saveAnomaly(anomaly: WorkoutAnomaly): LiftrixResult<Unit> {
        return try {
            withContext(Dispatchers.IO) {
                anomalyDao.insert(anomaly.toEntity())
                LiftrixResult.success(Unit)
            }
        } catch (e: Exception) {
            LiftrixResult.failure(
                LiftrixError.DatabaseError(
                    "Failed to save anomaly: ${e.message}",
                    isRecoverable = true,
                    retryAfter = 1000L
                )
            )
        }
    }

    override suspend fun getAnomaly(anomalyId: String): LiftrixResult<WorkoutAnomaly> {
        return try {
            withContext(Dispatchers.IO) {
                val entity = anomalyDao.getAnomaly(anomalyId)
                if (entity != null) {
                    LiftrixResult.success(entity.toDomain())
                } else {
                    LiftrixResult.failure(
                        LiftrixError.NotFoundError(
                            "Anomaly not found with ID: $anomalyId",
                            resourceType = "anomaly",
                            resourceId = anomalyId
                        )
                    )
                }
            }
        } catch (e: Exception) {
            LiftrixResult.failure(
                LiftrixError.DatabaseError(
                    "Failed to get anomaly $anomalyId: ${e.message}",
                    isRecoverable = true,
                    retryAfter = 1000L
                )
            )
        }
    }

    override suspend fun getUserAnomalies(userId: String, limit: Int): LiftrixResult<List<WorkoutAnomaly>> {
        return try {
            withContext(Dispatchers.IO) {
                val entities = anomalyDao.getUserAnomalies(userId, limit)
                LiftrixResult.success(entities.toWorkoutAnomalyDomain())
            }
        } catch (e: Exception) {
            LiftrixResult.failure(
                LiftrixError.DatabaseError(
                    "Failed to get user anomalies for $userId: ${e.message}",
                    isRecoverable = true,
                    retryAfter = 2000L
                )
            )
        }
    }

    override suspend fun getUnresolvedAnomalies(userId: String): LiftrixResult<List<WorkoutAnomaly>> {
        return try {
            withContext(Dispatchers.IO) {
                val entities = anomalyDao.getUnresolvedAnomalies(userId)
                LiftrixResult.success(entities.toWorkoutAnomalyDomain())
            }
        } catch (e: Exception) {
            LiftrixResult.failure(
                LiftrixError.DatabaseError(
                    "Failed to get unresolved anomalies for $userId: ${e.message}",
                    isRecoverable = true,
                    retryAfter = 2000L
                )
            )
        }
    }

    override suspend fun getUserAnomalyFeedback(userId: String): LiftrixResult<Pair<Int, Int>> {
        return try {
            withContext(Dispatchers.IO) {
                val confirmedCount = anomalyDao.getConfirmedAnomaliesCount(userId)
                val dismissedCount = anomalyDao.getDismissedAnomaliesCount(userId)
                LiftrixResult.success(Pair(confirmedCount, dismissedCount))
            }
        } catch (e: Exception) {
            LiftrixResult.failure(
                LiftrixError.DatabaseError(
                    "Failed to get anomaly feedback for $userId: ${e.message}",
                    isRecoverable = true,
                    retryAfter = 1000L
                )
            )
        }
    }

    override suspend fun getAllAnomalies(limit: Int, offset: Int): LiftrixResult<List<WorkoutAnomaly>> {
        return try {
            withContext(Dispatchers.IO) {
                val entities = anomalyDao.getAllAnomalies(limit, offset)
                LiftrixResult.success(entities.toWorkoutAnomalyDomain())
            }
        } catch (e: Exception) {
            LiftrixResult.failure(
                LiftrixError.DatabaseError(
                    "Failed to get all anomalies: ${e.message}",
                    isRecoverable = true,
                    retryAfter = 3000L
                )
            )
        }
    }

    override suspend fun cleanupOldAnomalies(daysToKeep: Int): LiftrixResult<Int> {
        return try {
            withContext(Dispatchers.IO) {
                val cutoffTime = Instant.now().minusSeconds(daysToKeep * 24L * 60L * 60L)
                val deletedCount = anomalyDao.deleteOldResolvedAnomalies(cutoffTime)
                LiftrixResult.success(deletedCount)
            }
        } catch (e: Exception) {
            LiftrixResult.failure(
                LiftrixError.DatabaseError(
                    "Failed to cleanup old anomalies: ${e.message}",
                    isRecoverable = true,
                    retryAfter = 5000L
                )
            )
        }
    }
}