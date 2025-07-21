package com.example.liftrix.data.repository

import com.example.liftrix.data.local.dao.WorkoutTemplateDao
import com.example.liftrix.data.mapper.WorkoutTemplateMapper
import com.example.liftrix.domain.model.WorkoutTemplate
import com.example.liftrix.domain.model.WorkoutTemplateId
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.WorkoutTemplateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkoutTemplateRepositoryImpl @Inject constructor(
    private val workoutTemplateDao: WorkoutTemplateDao,
    private val workoutTemplateMapper: WorkoutTemplateMapper
) : WorkoutTemplateRepository {

    override fun getAllTemplatesForUser(userId: String): Flow<LiftrixResult<List<WorkoutTemplate>>> {
        // Validate user ID early to prevent queries with blank userId
        if (userId.isBlank()) {
            return flowOf(
                LiftrixResult.failure(
                    LiftrixError.ValidationError(
                        field = "userId",
                        violations = listOf("User ID cannot be blank when retrieving templates"),
                        errorMessage = "User ID is required"
                    )
                )
            )
        }
        
        return workoutTemplateDao.getAllTemplatesForUser(userId)
            .map { entities ->
                try {
                    val templates = workoutTemplateMapper.toDomainList(entities)
                    LiftrixResult.success(templates)
                } catch (throwable: Throwable) {
                    Timber.e(throwable, "Failed to map template entities to domain models for user: $userId")
                    LiftrixResult.failure(
                        LiftrixError.DatabaseError(
                            errorMessage = "Failed to retrieve templates for user",
                            operation = "READ",
                            table = "workout_templates",
                            analyticsContext = mapOf("user_id" to userId)
                        )
                    )
                }
            }
            .catch { throwable ->
                Timber.e(throwable, "Database flow error for user templates: $userId")
                emit(
                    LiftrixResult.failure(
                        LiftrixError.DatabaseError(
                            errorMessage = "Database connection error while retrieving templates",
                            operation = "READ",
                            table = "workout_templates",
                            analyticsContext = mapOf("user_id" to userId)
                        )
                    )
                )
            }
    }

    override suspend fun getTemplateById(templateId: WorkoutTemplateId, userId: String): LiftrixResult<WorkoutTemplate?> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to retrieve template by ID",
                    operation = "READ",
                    table = "workout_templates",
                    analyticsContext = mapOf(
                        "template_id" to templateId.value,
                        "user_id" to userId
                    )
                )
            }
        ) {
            workoutTemplateDao.getTemplateById(templateId.value, userId)?.let { entity ->
                workoutTemplateMapper.toDomain(entity)
            }
        }
    }

    override fun searchTemplates(userId: String, searchQuery: String): Flow<LiftrixResult<List<WorkoutTemplate>>> {
        return workoutTemplateDao.searchTemplates(userId, searchQuery)
            .map { entities ->
                try {
                    val templates = workoutTemplateMapper.toDomainList(entities)
                    LiftrixResult.success(templates)
                } catch (throwable: Throwable) {
                    Timber.e(throwable, "Failed to map template search results for user: $userId")
                    LiftrixResult.failure(
                        LiftrixError.DatabaseError(
                            errorMessage = "Failed to search templates",
                            operation = "READ",
                            table = "workout_templates",
                            analyticsContext = mapOf(
                                "user_id" to userId,
                                "search_query" to searchQuery
                            )
                        )
                    )
                }
            }
            .catch { throwable ->
                Timber.e(throwable, "Database flow error for template search: $userId")
                emit(
                    LiftrixResult.failure(
                        LiftrixError.DatabaseError(
                            errorMessage = "Database connection error while searching templates",
                            operation = "READ",
                            table = "workout_templates",
                            analyticsContext = mapOf(
                                "user_id" to userId,
                                "search_query" to searchQuery
                            )
                        )
                    )
                )
            }
    }

    override fun getTemplatesByFolder(userId: String, folderId: String): Flow<LiftrixResult<List<WorkoutTemplate>>> {
        return workoutTemplateDao.getTemplatesByFolder(userId, folderId)
            .map { entities ->
                try {
                    val templates = workoutTemplateMapper.toDomainList(entities)
                    LiftrixResult.success(templates)
                } catch (throwable: Throwable) {
                    Timber.e(throwable, "Failed to map templates by folder for user: $userId")
                    LiftrixResult.failure(
                        LiftrixError.DatabaseError(
                            errorMessage = "Failed to retrieve templates by folder",
                            operation = "READ",
                            table = "workout_templates",
                            analyticsContext = mapOf(
                                "user_id" to userId,
                                "folder_id" to folderId
                            )
                        )
                    )
                }
            }
            .catch { throwable ->
                Timber.e(throwable, "Database flow error for templates by folder: $userId")
                emit(
                    LiftrixResult.failure(
                        LiftrixError.DatabaseError(
                            errorMessage = "Database connection error while retrieving templates by folder",
                            operation = "READ",
                            table = "workout_templates",
                            analyticsContext = mapOf(
                                "user_id" to userId,
                                "folder_id" to folderId
                            )
                        )
                    )
                )
            }
    }

    override fun getTemplatesByDifficulty(userId: String, difficultyLevel: Int): Flow<LiftrixResult<List<WorkoutTemplate>>> {
        return workoutTemplateDao.getTemplatesByDifficulty(userId, difficultyLevel)
            .map { entities ->
                try {
                    val templates = workoutTemplateMapper.toDomainList(entities)
                    LiftrixResult.success(templates)
                } catch (throwable: Throwable) {
                    Timber.e(throwable, "Failed to map templates by difficulty for user: $userId")
                    LiftrixResult.failure(
                        LiftrixError.DatabaseError(
                            errorMessage = "Failed to retrieve templates by difficulty",
                            operation = "READ",
                            table = "workout_templates",
                            analyticsContext = mapOf(
                                "user_id" to userId,
                                "difficulty_level" to difficultyLevel.toString()
                            )
                        )
                    )
                }
            }
            .catch { throwable ->
                Timber.e(throwable, "Database flow error for templates by difficulty: $userId")
                emit(
                    LiftrixResult.failure(
                        LiftrixError.DatabaseError(
                            errorMessage = "Database connection error while retrieving templates by difficulty",
                            operation = "READ",
                            table = "workout_templates",
                            analyticsContext = mapOf(
                                "user_id" to userId,
                                "difficulty_level" to difficultyLevel.toString()
                            )
                        )
                    )
                )
            }
    }

    override fun getMostUsedTemplates(userId: String, limit: Int): Flow<LiftrixResult<List<WorkoutTemplate>>> {
        return workoutTemplateDao.getMostUsedTemplates(userId, limit)
            .map { entities ->
                try {
                    val templates = workoutTemplateMapper.toDomainList(entities)
                    LiftrixResult.success(templates)
                } catch (throwable: Throwable) {
                    Timber.e(throwable, "Failed to map most used templates for user: $userId")
                    LiftrixResult.failure(
                        LiftrixError.DatabaseError(
                            errorMessage = "Failed to retrieve most used templates",
                            operation = "READ",
                            table = "workout_templates",
                            analyticsContext = mapOf(
                                "user_id" to userId,
                                "limit" to limit.toString()
                            )
                        )
                    )
                }
            }
            .catch { throwable ->
                Timber.e(throwable, "Database flow error for most used templates: $userId")
                emit(
                    LiftrixResult.failure(
                        LiftrixError.DatabaseError(
                            errorMessage = "Database connection error while retrieving most used templates",
                            operation = "READ",
                            table = "workout_templates",
                            analyticsContext = mapOf(
                                "user_id" to userId,
                                "limit" to limit.toString()
                            )
                        )
                    )
                )
            }
    }

    override fun getRecentlyUsedTemplates(userId: String, limit: Int): Flow<LiftrixResult<List<WorkoutTemplate>>> {
        return workoutTemplateDao.getRecentlyUsedTemplates(userId, limit)
            .map { entities ->
                try {
                    val templates = workoutTemplateMapper.toDomainList(entities)
                    LiftrixResult.success(templates)
                } catch (throwable: Throwable) {
                    Timber.e(throwable, "Failed to map recently used templates for user: $userId")
                    LiftrixResult.failure(
                        LiftrixError.DatabaseError(
                            errorMessage = "Failed to retrieve recently used templates",
                            operation = "READ",
                            table = "workout_templates",
                            analyticsContext = mapOf(
                                "user_id" to userId,
                                "limit" to limit.toString()
                            )
                        )
                    )
                }
            }
            .catch { throwable ->
                Timber.e(throwable, "Database flow error for recently used templates: $userId")
                emit(
                    LiftrixResult.failure(
                        LiftrixError.DatabaseError(
                            errorMessage = "Database connection error while retrieving recently used templates",
                            operation = "READ",
                            table = "workout_templates",
                            analyticsContext = mapOf(
                                "user_id" to userId,
                                "limit" to limit.toString()
                            )
                        )
                    )
                )
            }
    }

    override suspend fun createTemplate(template: WorkoutTemplate): LiftrixResult<WorkoutTemplate> {
        // Validate user ID
        if (template.userId.isBlank()) {
            return LiftrixResult.failure(
                LiftrixError.ValidationError(
                    field = "userId",
                    violations = listOf("User ID cannot be blank when creating workout template"),
                    errorMessage = "User ID is required"
                )
            )
        }
        
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to create workout template: ${template.name}",
                    operation = "CREATE",
                    table = "workout_templates",
                    analyticsContext = mapOf(
                        "template_name" to template.name,
                        "user_id" to template.userId,
                        "exercise_count" to template.exercises.size.toString()
                    )
                )
            }
        ) {
            // Check if template name already exists
            val nameExists = workoutTemplateDao.doesTemplateNameExist(template.userId, template.name)
            if (nameExists) {
                throw IllegalArgumentException("Template name '${template.name}' already exists")
            }

            val entity = workoutTemplateMapper.toEntity(template, isSynced = false)
            val insertResult = workoutTemplateDao.insertTemplate(entity)
            
            if (insertResult > 0) {
                Timber.d("Created template: ${template.name} for user ${template.userId}")
                template.copy(id = WorkoutTemplateId(insertResult.toString()))
            } else {
                throw RuntimeException("Template insert operation returned invalid ID: $insertResult")
            }
        }
    }

    override suspend fun updateTemplate(template: WorkoutTemplate): LiftrixResult<WorkoutTemplate> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to update workout template: ${template.name}",
                    operation = "UPDATE",
                    table = "workout_templates",
                    analyticsContext = mapOf(
                        "template_id" to template.id.value,
                        "template_name" to template.name,
                        "user_id" to template.userId
                    )
                )
            }
        ) {
            // Get existing entity to preserve sync information
            val existingEntity = workoutTemplateDao.getTemplateById(template.id.value, template.userId)
                ?: throw RuntimeException("Template not found for ID: ${template.id.value} and user: ${template.userId}")

            val updatedEntity = workoutTemplateMapper.updateEntity(existingEntity, template)
            val updateResult = workoutTemplateDao.updateTemplate(updatedEntity)
            
            if (updateResult > 0) {
                Timber.d("Updated template: ${template.name} for user ${template.userId}")
                template
            } else {
                throw RuntimeException("Template update operation affected 0 rows for ID: ${template.id.value}")
            }
        }
    }

    override suspend fun deleteTemplate(templateId: WorkoutTemplateId, userId: String): LiftrixResult<Unit> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to delete template",
                    operation = "DELETE",
                    table = "workout_templates",
                    analyticsContext = mapOf(
                        "template_id" to templateId.value,
                        "user_id" to userId
                    )
                )
            }
        ) {
            val deleteResult = workoutTemplateDao.deleteTemplate(templateId.value, userId)
            
            if (deleteResult > 0) {
                Timber.d("Deleted template: ${templateId.value} for user $userId")
            } else {
                throw RuntimeException("No template found to delete with ID: ${templateId.value} for user: $userId")
            }
        }
    }

    override suspend fun recordTemplateUsage(templateId: WorkoutTemplateId, userId: String): LiftrixResult<Unit> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to record template usage",
                    operation = "UPDATE",
                    table = "workout_templates",
                    analyticsContext = mapOf(
                        "template_id" to templateId.value,
                        "user_id" to userId
                    )
                )
            }
        ) {
            val now = Instant.now()
            val updateResult = workoutTemplateDao.incrementUsageCount(templateId.value, userId, now)
            
            if (updateResult > 0) {
                Timber.d("Recorded usage for template: ${templateId.value}")
            } else {
                throw RuntimeException("No template found to update usage for ID: ${templateId.value} and user: $userId")
            }
        }
    }

    override suspend fun doesTemplateNameExist(userId: String, name: String): LiftrixResult<Boolean> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to check template name existence",
                    operation = "READ",
                    table = "workout_templates",
                    analyticsContext = mapOf(
                        "user_id" to userId,
                        "template_name" to name
                    )
                )
            }
        ) {
            workoutTemplateDao.doesTemplateNameExist(userId, name)
        }
    }

    override suspend fun getTemplateCount(userId: String): LiftrixResult<Int> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to get template count",
                    operation = "READ",
                    table = "workout_templates",
                    analyticsContext = mapOf("user_id" to userId)
                )
            }
        ) {
            workoutTemplateDao.getTemplateCount(userId)
        }
    }
}