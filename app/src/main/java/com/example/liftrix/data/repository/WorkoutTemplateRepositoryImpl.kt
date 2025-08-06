package com.example.liftrix.data.repository

import com.example.liftrix.data.local.dao.WorkoutTemplateDao
import com.example.liftrix.data.local.LiftrixDatabase
import com.example.liftrix.data.mapper.WorkoutTemplateMapper
import com.example.liftrix.domain.model.WorkoutTemplate
import com.example.liftrix.domain.model.WorkoutTemplateId
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.WorkoutTemplateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.delay
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
        Timber.d("🔥 REPO-ALL-TEMPLATES: Getting all templates for user: $userId")
        
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
        
        return flow {
            var retryCount = 0
            val maxRetries = 3
            var delayMs = 500L
            
            while (retryCount <= maxRetries) {
                try {
                    Timber.d("Attempting to load templates for user $userId (attempt ${retryCount + 1})")
                    
                    // Collect templates from DAO flow
                    workoutTemplateDao.getAllTemplatesForUser(userId).collect { entities ->
                        try {
                            Timber.d("🔥 REPO-ALL-TEMPLATES: DAO returned ${entities.size} entities for user $userId")
                            entities.forEach { entity ->
                                Timber.d("🔥 REPO-ALL-TEMPLATES: - Entity: id=${entity.id}, name='${entity.name}', folderId='${entity.folderId}', userId='${entity.userId}'")
                            }
                            
                            val templates = workoutTemplateMapper.toDomainList(entities)
                            Timber.d("🔥 REPO-ALL-TEMPLATES: Mapped to ${templates.size} domain templates")
                            emit(LiftrixResult.success(templates))
                        } catch (mappingThrowable: Throwable) {
                            Timber.e(mappingThrowable, "Failed to map template entities for user: $userId")
                            emit(
                                LiftrixResult.failure(
                                    LiftrixError.DatabaseError(
                                        errorMessage = "Failed to retrieve templates for user",
                                        operation = "READ",
                                        table = "workout_templates",
                                        analyticsContext = mapOf("user_id" to userId)
                                    )
                                )
                            )
                        }
                    }
                    
                    // If we get here, the operation succeeded
                    break
                    
                } catch (throwable: Throwable) {
                    Timber.w(throwable, "Database access failed for templates (attempt ${retryCount + 1}/$maxRetries): ${throwable.message}")
                    
                    if (retryCount >= maxRetries) {
                        // Final attempt failed - emit error
                        Timber.e(throwable, "All retry attempts failed for user templates: $userId")
                        emit(
                            LiftrixResult.failure(
                                LiftrixError.DatabaseError(
                                    errorMessage = "Database connection error while retrieving templates",
                                    operation = "READ",
                                    table = "workout_templates",
                                    analyticsContext = mapOf(
                                        "user_id" to userId,
                                        "retry_count" to retryCount.toString(),
                                        "error_message" to (throwable.message ?: "Unknown error")
                                    )
                                )
                            )
                        )
                        break
                    } else {
                        // Wait before retrying
                        Timber.d("Waiting ${delayMs}ms before retry...")
                        delay(delayMs)
                        retryCount++
                        delayMs = (delayMs * 1.5).toLong().coerceAtMost(2000L) // Exponential backoff
                    }
                }
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
        Timber.d("🔥 REPO-CREATE: Starting template creation in repository")
        Timber.d("🔥 REPO-CREATE: Template ID: ${template.id.value}")
        Timber.d("🔥 REPO-CREATE: Template name: '${template.name}'")
        Timber.d("🔥 REPO-CREATE: User ID: '${template.userId}'")
        Timber.d("🔥 REPO-CREATE: Folder ID: '${template.folderId}'")
        Timber.d("🔥 REPO-CREATE: Exercises count: ${template.exercises.size}")
        
        // Validate user ID
        if (template.userId.isBlank()) {
            Timber.e("🔥 REPO-CREATE: User ID validation failed - blank userId")
            return LiftrixResult.failure(
                LiftrixError.ValidationError(
                    field = "userId",
                    violations = listOf("User ID cannot be blank when creating workout template"),
                    errorMessage = "User ID is required"
                )
            )
        }
        
        Timber.d("🔥 REPO-CREATE: User ID validation passed")
        
        return liftrixCatching(
            errorMapper = { throwable ->
                Timber.e(throwable, "🔥 REPO-CREATE: Error in repository createTemplate")
                Timber.e("🔥 REPO-CREATE: Error type: ${throwable::class.simpleName}")
                Timber.e("🔥 REPO-CREATE: Error message: ${throwable.message}")
                
                when {
                    throwable.message?.contains("FOREIGN KEY constraint failed") == true -> {
                        Timber.e("🔥 REPO-CREATE: Foreign key constraint failed - folder doesn't exist")
                        LiftrixError.DatabaseError(
                            errorMessage = "Invalid folder reference - ensure the folder exists before creating template",
                            operation = "CREATE",
                            table = "workout_templates",
                            analyticsContext = mapOf(
                                "template_name" to template.name,
                                "user_id" to template.userId,
                                "folder_id" to template.folderId,
                                "error_type" to "foreign_key_violation"
                            )
                        )
                    }
                    throwable.message?.contains("UNIQUE constraint failed") == true ||
                    throwable is IllegalArgumentException && throwable.message?.contains("already exists") == true -> {
                        Timber.e("🔥 REPO-CREATE: Duplicate template name")
                        LiftrixError.ValidationError(
                            field = "name",
                            violations = listOf("Template name '${template.name}' already exists for this user"),
                            errorMessage = "Duplicate template name"
                        )
                    }
                    else -> {
                        Timber.e("🔥 REPO-CREATE: Generic database error")
                        LiftrixError.DatabaseError(
                            errorMessage = "Failed to create workout template: ${template.name}",
                            operation = "CREATE",
                            table = "workout_templates",
                            analyticsContext = mapOf(
                                "template_name" to template.name,
                                "user_id" to template.userId,
                                "exercise_count" to template.exercises.size.toString(),
                                "error_message" to (throwable.message ?: "Unknown error")
                            )
                        )
                    }
                }
            }
        ) {
            // Check if template name already exists
            Timber.d("🔥 REPO-CREATE: Checking if template name exists")
            val nameExists = workoutTemplateDao.doesTemplateNameExist(template.userId, template.name)
            if (nameExists) {
                Timber.e("🔥 REPO-CREATE: Template name '${template.name}' already exists for user ${template.userId}")
                throw IllegalArgumentException("Template name '${template.name}' already exists")
            }
            Timber.d("🔥 REPO-CREATE: Template name is unique, proceeding")

            Timber.d("🔥 REPO-CREATE: Converting template to entity")
            val entity = workoutTemplateMapper.toEntity(template, isSynced = false)
            Timber.d("🔥 REPO-CREATE: Entity created, calling DAO insertTemplate")
            
            val insertResult = workoutTemplateDao.insertTemplate(entity)
            Timber.d("🔥 REPO-CREATE: DAO insertTemplate returned: $insertResult")
            
            if (insertResult > 0) {
                Timber.d("🔥 REPO-CREATE: Template inserted successfully with ID: $insertResult")
                Timber.d("🔥 REPO-CREATE: Created template: ${template.name} for user ${template.userId}")
                val finalTemplate = template.copy(id = WorkoutTemplateId(insertResult.toString()))
                Timber.d("🔥 REPO-CREATE: Returning final template with ID: ${finalTemplate.id.value}")
                finalTemplate
            } else {
                Timber.e("🔥 REPO-CREATE: Insert operation returned invalid ID: $insertResult")
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