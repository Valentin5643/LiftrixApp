package com.example.liftrix.data.repository

import com.example.liftrix.data.local.dao.WorkoutTemplateDao
import com.example.liftrix.data.mapper.WorkoutTemplateMapper
import com.example.liftrix.domain.model.WorkoutTemplate
import com.example.liftrix.domain.model.WorkoutTemplateId
import com.example.liftrix.domain.repository.WorkoutTemplateRepository
import kotlinx.coroutines.flow.Flow
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

    override fun getAllTemplatesForUser(userId: String): Flow<List<WorkoutTemplate>> {
        return workoutTemplateDao.getAllTemplatesForUser(userId).map { entities ->
            workoutTemplateMapper.toDomainList(entities)
        }
    }

    override suspend fun getTemplateById(templateId: WorkoutTemplateId, userId: String): WorkoutTemplate? {
        return try {
            workoutTemplateDao.getTemplateById(templateId.value, userId)?.let { entity ->
                workoutTemplateMapper.toDomain(entity)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to get template by ID: ${templateId.value}")
            null
        }
    }

    override fun searchTemplates(userId: String, searchQuery: String): Flow<List<WorkoutTemplate>> {
        return workoutTemplateDao.searchTemplates(userId, searchQuery).map { entities ->
            workoutTemplateMapper.toDomainList(entities)
        }
    }

    override fun getTemplatesByTag(userId: String, tag: String): Flow<List<WorkoutTemplate>> {
        return workoutTemplateDao.getTemplatesByTag(userId, tag).map { entities ->
            workoutTemplateMapper.toDomainList(entities)
        }
    }

    override fun getTemplatesByDifficulty(userId: String, difficultyLevel: Int): Flow<List<WorkoutTemplate>> {
        return workoutTemplateDao.getTemplatesByDifficulty(userId, difficultyLevel).map { entities ->
            workoutTemplateMapper.toDomainList(entities)
        }
    }

    override fun getMostUsedTemplates(userId: String, limit: Int): Flow<List<WorkoutTemplate>> {
        return workoutTemplateDao.getMostUsedTemplates(userId, limit).map { entities ->
            workoutTemplateMapper.toDomainList(entities)
        }
    }

    override fun getRecentlyUsedTemplates(userId: String, limit: Int): Flow<List<WorkoutTemplate>> {
        return workoutTemplateDao.getRecentlyUsedTemplates(userId, limit).map { entities ->
            workoutTemplateMapper.toDomainList(entities)
        }
    }

    override suspend fun createTemplate(template: WorkoutTemplate): Result<WorkoutTemplate> {
        return try {
            // Check if template name already exists
            val nameExists = workoutTemplateDao.doesTemplateNameExist(template.userId, template.name)
            if (nameExists) {
                return Result.failure(IllegalArgumentException("Template name '${template.name}' already exists"))
            }

            val entity = workoutTemplateMapper.toEntity(template, isSynced = false)
            val insertResult = workoutTemplateDao.insertTemplate(entity)
            
            if (insertResult > 0) {
                Timber.d("Created template: ${template.name} for user ${template.userId}")
                Result.success(template)
            } else {
                Result.failure(RuntimeException("Failed to insert template"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to create template: ${template.name}")
            Result.failure(e)
        }
    }

    override suspend fun updateTemplate(template: WorkoutTemplate): Result<WorkoutTemplate> {
        return try {
            // Get existing entity to preserve sync information
            val existingEntity = workoutTemplateDao.getTemplateById(template.id.value, template.userId)
                ?: return Result.failure(IllegalArgumentException("Template not found"))

            val updatedEntity = workoutTemplateMapper.updateEntity(existingEntity, template)
            val updateResult = workoutTemplateDao.updateTemplate(updatedEntity)
            
            if (updateResult > 0) {
                Timber.d("Updated template: ${template.name} for user ${template.userId}")
                Result.success(template)
            } else {
                Result.failure(RuntimeException("Failed to update template"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to update template: ${template.id}")
            Result.failure(e)
        }
    }

    override suspend fun deleteTemplate(templateId: WorkoutTemplateId, userId: String): Result<Unit> {
        return try {
            val deleteResult = workoutTemplateDao.deleteTemplate(templateId.value, userId)
            
            if (deleteResult > 0) {
                Timber.d("Deleted template: ${templateId.value} for user $userId")
                Result.success(Unit)
            } else {
                Result.failure(IllegalArgumentException("Template not found or not owned by user"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete template: ${templateId.value}")
            Result.failure(e)
        }
    }

    override suspend fun recordTemplateUsage(templateId: WorkoutTemplateId, userId: String): Result<Unit> {
        return try {
            val now = Instant.now()
            val updateResult = workoutTemplateDao.incrementUsageCount(templateId.value, userId, now)
            
            if (updateResult > 0) {
                Timber.d("Recorded usage for template: ${templateId.value}")
                Result.success(Unit)
            } else {
                Result.failure(IllegalArgumentException("Template not found or not owned by user"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to record template usage: ${templateId.value}")
            Result.failure(e)
        }
    }

    override suspend fun doesTemplateNameExist(userId: String, name: String): Boolean {
        return try {
            workoutTemplateDao.doesTemplateNameExist(userId, name)
        } catch (e: Exception) {
            Timber.e(e, "Failed to check template name existence: $name")
            false
        }
    }

    override suspend fun getTemplateCount(userId: String): Int {
        return try {
            workoutTemplateDao.getTemplateCount(userId)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get template count for user: $userId")
            0
        }
    }
}