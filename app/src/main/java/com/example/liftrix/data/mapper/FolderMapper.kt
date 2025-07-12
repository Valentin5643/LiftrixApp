package com.example.liftrix.data.mapper

import com.example.liftrix.data.local.entity.FolderEntity
import com.example.liftrix.domain.model.Folder
import com.example.liftrix.domain.model.FolderId
import com.example.liftrix.domain.model.FolderName
import timber.log.Timber
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mapper for converting between FolderEntity and Folder domain model
 * 
 * Handles the transformation between database entities and domain models,
 * including validation and error handling for data integrity.
 */
@Singleton
class FolderMapper @Inject constructor() {
    
    /**
     * Convert FolderEntity to Folder domain model
     * 
     * @param entity The folder entity from the database
     * @return Folder domain model
     * @throws IllegalArgumentException if entity data is invalid
     */
    fun toDomain(entity: FolderEntity): Folder {
        return try {
            Folder(
                id = FolderId(entity.id),
                userId = entity.userId,
                name = FolderName(entity.name),
                createdAt = entity.createdAt,
                updatedAt = entity.updatedAt,
                templateCount = entity.templateCount
            )
        } catch (e: IllegalArgumentException) {
            Timber.e(e, "Failed to convert FolderEntity to domain: ${entity.id}")
            throw IllegalArgumentException("Invalid folder data: ${e.message}", e)
        }
    }
    
    /**
     * Convert Folder domain model to FolderEntity
     * 
     * @param domain The folder domain model
     * @param isSynced Whether the folder has been synced to Firebase
     * @param syncVersion The sync version for Firebase synchronization
     * @return FolderEntity for database storage
     */
    fun toEntity(
        domain: Folder, 
        isSynced: Boolean = false, 
        syncVersion: Long = 1L
    ): FolderEntity {
        return FolderEntity(
            id = domain.id.value,
            userId = domain.userId,
            name = domain.name.value,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt,
            templateCount = domain.templateCount,
            isSynced = isSynced,
            syncVersion = syncVersion
        )
    }
    
    /**
     * Convert list of FolderEntity to list of Folder domain models
     * 
     * @param entities List of folder entities from the database
     * @return List of Folder domain models
     */
    fun toDomainList(entities: List<FolderEntity>): List<Folder> {
        return entities.mapNotNull { entity ->
            try {
                toDomain(entity)
            } catch (e: Exception) {
                Timber.w(e, "Skipping invalid folder entity: ${entity.id}")
                null
            }
        }
    }
    
    /**
     * Convert list of Folder domain models to list of FolderEntity
     * 
     * @param domainList List of folder domain models
     * @param isSynced Whether the folders have been synced to Firebase
     * @param syncVersion The sync version for Firebase synchronization
     * @return List of FolderEntity for database storage
     */
    fun toEntityList(
        domainList: List<Folder>, 
        isSynced: Boolean = false, 
        syncVersion: Long = 1L
    ): List<FolderEntity> {
        return domainList.map { domain ->
            toEntity(domain, isSynced, syncVersion)
        }
    }
    
    /**
     * Update existing FolderEntity with new domain data while preserving sync information
     * 
     * This method is useful for updates where we want to preserve the existing
     * sync status and version information from the database.
     * 
     * @param existingEntity The existing folder entity from the database
     * @param updatedDomain The updated folder domain model
     * @return Updated FolderEntity with preserved sync information
     */
    fun updateEntity(existingEntity: FolderEntity, updatedDomain: Folder): FolderEntity {
        return existingEntity.copy(
            name = updatedDomain.name.value,
            updatedAt = updatedDomain.updatedAt,
            templateCount = updatedDomain.templateCount,
            // Preserve sync information from existing entity
            isSynced = false, // Mark as unsynced due to update
            syncVersion = existingEntity.syncVersion + 1 // Increment sync version
        )
    }
    
    /**
     * Create a new FolderEntity for insert operations
     * 
     * @param domain The folder domain model
     * @return FolderEntity ready for database insertion
     */
    fun toNewEntity(domain: Folder): FolderEntity {
        return toEntity(
            domain = domain,
            isSynced = false, // New entities are not synced initially
            syncVersion = 1L // Start with version 1
        )
    }
    
    /**
     * Mark a folder entity as synced to Firebase
     * 
     * @param entity The folder entity to mark as synced
     * @return Updated FolderEntity marked as synced
     */
    fun markAsSynced(entity: FolderEntity): FolderEntity {
        return entity.copy(
            isSynced = true,
            updatedAt = Instant.now() // Update the timestamp when synced
        )
    }
}