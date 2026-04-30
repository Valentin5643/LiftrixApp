package com.example.liftrix.data.repository.template

import com.example.liftrix.data.local.dao.WorkoutTemplateDao
import com.example.liftrix.data.mapper.WorkoutTemplateMapper
import com.example.liftrix.domain.model.WorkoutTemplate
import com.example.liftrix.domain.model.WorkoutTemplateId
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.template.TemplateRepository
import com.example.liftrix.sync.SyncCoordinator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of TemplateRepository focused on workout template data operations.
 * 
 * Responsibilities:
 * - Template CRUD operations with user scoping
 * - Data mapping between domain models and entities
 * - Database operations through WorkoutTemplateDao
 * - Error handling with LiftrixError hierarchy
 * - Template usage tracking for analytics
 * 
 * Does NOT contain:
 * - Business logic (delegated to use cases)
 * - Template validation logic (handled in domain layer)
 * - Template sharing features (separate repository)
 * - Analytics computations (separate analytics repository)
 */
@Singleton
class TemplateRepositoryImpl @Inject constructor(
    private val workoutTemplateDao: WorkoutTemplateDao,
    private val workoutTemplateMapper: WorkoutTemplateMapper,
    private val syncCoordinator: SyncCoordinator
) : TemplateRepository {

    override suspend fun createTemplate(template: WorkoutTemplate): LiftrixResult<WorkoutTemplate> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to create workout template: ${template.name}",
                    operation = "CREATE",
                    table = "workout_templates",
                    analyticsContext = mapOf(
                        "template_name" to template.name,
                        "user_id" to template.userId,
                        "exercise_count" to template.exercises.size.toString(),
                        "difficulty_level" to template.difficultyLevel.toString()
                    )
                )
            }
        ) {
            // Check if template name already exists
            val nameExists = workoutTemplateDao.doesTemplateNameExist(template.userId, template.name)
            if (nameExists) {
                throw IllegalArgumentException("Template name '${template.name}' already exists")
            }

            val entity = workoutTemplateMapper.toEntity(template, isSynced = false).copy(
                isDirty = true,
                lastModified = System.currentTimeMillis()
            )
            Timber.tag("StartupRestoreFix").i(
                "operation=TEMPLATE_CREATE_BEFORE_INSERT repo=TemplateRepositoryImpl userId=${template.userId} stableTemplateId=${template.id.value} localRowId=not_assigned isDirty=${entity.isDirty} isSynced=${entity.isSynced} timestamp=${System.currentTimeMillis()}"
            )
            val insertResult = workoutTemplateDao.insertTemplate(entity)
            
            if (insertResult > 0) {
                Timber.tag("StartupRestoreFix").i(
                    "operation=TEMPLATE_CREATE_AFTER_INSERT repo=TemplateRepositoryImpl userId=${template.userId} stableTemplateId=${template.id.value} localRowId=$insertResult returnedTemplateId=${template.id.value} isDirty=true isSynced=false timestamp=${System.currentTimeMillis()}"
                )
                Timber.d("Created template: ${template.name} for user ${template.userId}")
                syncCoordinator.triggerEntitySync(template.userId, "template")
                template
            } else {
                throw RuntimeException("Template insert operation returned invalid ID: $insertResult")
            }
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
            val entity = workoutTemplateDao.getTemplateById(templateId.value, userId)
            entity?.let { workoutTemplateMapper.toDomain(it) }
        }
    }

    override fun getAllTemplatesForUser(userId: String): Flow<LiftrixResult<List<WorkoutTemplate>>> {
        return workoutTemplateDao.getAllTemplatesForUser(userId)
            .map { entities ->
                try {
                    val templates = entities.map { workoutTemplateMapper.toDomain(it) }
                    Timber.tag("StartupRestoreFix").d(
                        "[TEMPLATE-LOAD] operation=TEMPLATE_REPOSITORY_FLOW_EMIT repo=TemplateRepositoryImpl userId=$userId entityCount=${entities.size} templateCount=${templates.size} debounceApplied=false distinctApplied=false cacheApplied=false timestamp=${System.currentTimeMillis()}"
                    )
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
            val deletedRows = workoutTemplateDao.deleteTemplate(templateId.value, userId)
            
            if (deletedRows == 0) {
                throw RuntimeException("No template found to delete with ID: ${templateId.value} for user: $userId")
            }
        }
    }

    override fun searchTemplates(userId: String, searchQuery: String): Flow<LiftrixResult<List<WorkoutTemplate>>> {
        return workoutTemplateDao.searchTemplates(userId, searchQuery)
            .map { entities ->
                try {
                    val templates = entities.map { workoutTemplateMapper.toDomain(it) }
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
                    val templates = entities.map { workoutTemplateMapper.toDomain(it) }
                    LiftrixResult.success(templates)
                } catch (throwable: Throwable) {
                    Timber.e(throwable, "Failed to map folder template entities for user: $userId, folder: $folderId")
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
                Timber.e(throwable, "Database flow error for folder templates: user: $userId, folder: $folderId")
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
                    val templates = entities.map { workoutTemplateMapper.toDomain(it) }
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

    override fun getRecentlyUsedTemplates(userId: String, limit: Int): Flow<LiftrixResult<List<WorkoutTemplate>>> {
        return workoutTemplateDao.getRecentlyUsedTemplates(userId, limit)
            .map { entities ->
                try {
                    val templates = entities.map { workoutTemplateMapper.toDomain(it) }
                    LiftrixResult.success(templates)
                } catch (throwable: Throwable) {
                    Timber.e(throwable, "Failed to map recently used template entities for user: $userId, limit: $limit")
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
                Timber.e(throwable, "Database flow error for recently used templates: user: $userId, limit: $limit")
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

    override fun getMostUsedTemplates(userId: String, limit: Int): Flow<LiftrixResult<List<WorkoutTemplate>>> {
        return workoutTemplateDao.getMostUsedTemplates(userId, limit)
            .map { entities ->
                try {
                    val templates = entities.map { workoutTemplateMapper.toDomain(it) }
                    LiftrixResult.success(templates)
                } catch (throwable: Throwable) {
                    Timber.e(throwable, "Failed to map most used template entities for user: $userId, limit: $limit")
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
                Timber.e(throwable, "Database flow error for most used templates: user: $userId, limit: $limit")
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
            if (name.isBlank()) {
                throw IllegalArgumentException("Template name cannot be blank")
            }
            
            workoutTemplateDao.doesTemplateNameExist(userId, name)
        }
    }

    override suspend fun templateExists(templateId: WorkoutTemplateId, userId: String): LiftrixResult<Boolean> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to check template existence",
                    operation = "READ",
                    table = "workout_templates",
                    analyticsContext = mapOf(
                        "template_id" to templateId.value,
                        "user_id" to userId
                    )
                )
            }
        ) {
            workoutTemplateDao.getTemplateById(templateId.value, userId) != null
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

    override suspend fun duplicateTemplate(
        templateId: WorkoutTemplateId,
        userId: String,
        newName: String?
    ): LiftrixResult<WorkoutTemplate> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to duplicate template",
                    operation = "CREATE",
                    table = "workout_templates",
                    analyticsContext = mapOf(
                        "source_template_id" to templateId.value,
                        "user_id" to userId,
                        "new_name" to (newName ?: "null")
                    )
                )
            }
        ) {
            // First, get the original template
            val originalEntity = workoutTemplateDao.getTemplateById(templateId.value, userId)
                ?: throw RuntimeException("Template not found for duplication: ${templateId.value}")
            
            val originalTemplate = workoutTemplateMapper.toDomain(originalEntity)
            
            // Create the duplicate with new name and reset metadata
            val duplicateName = newName ?: "${originalTemplate.name} (Copy)"
            val duplicateId = WorkoutTemplateId.generate()
            val duplicateTemplate = originalTemplate.copy(
                id = duplicateId,
                name = duplicateName,
                usageCount = 0,
                lastUsedAt = null,
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )
            
            // Insert the duplicate
            val duplicateEntity = workoutTemplateMapper.toEntity(duplicateTemplate, isSynced = false).copy(
                isDirty = true,
                lastModified = System.currentTimeMillis()
            )
            val insertedId = workoutTemplateDao.insertTemplate(duplicateEntity)
            
            if (insertedId > 0) {
                syncCoordinator.triggerEntitySync(userId, "template")
                duplicateTemplate
            } else {
                throw RuntimeException("Template duplication insert operation returned invalid ID: $insertedId")
            }
        }
    }
}
