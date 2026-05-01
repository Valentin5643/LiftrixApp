package com.example.liftrix.domain.model

import java.time.Instant
import java.util.UUID

/**
 * Value class representing a unique identifier for a folder
 */
@JvmInline
value class FolderId(val value: String) {
    init {
        require(value.isNotBlank()) { "Folder ID cannot be blank" }
    }
    
    companion object {
        /**
         * Generates a new unique FolderId
         */
        fun generate(): FolderId = FolderId("folder-${UUID.randomUUID()}")
        
        /**
         * Creates a FolderId from an existing string value
         */
        fun fromString(value: String): FolderId = FolderId(value)
    }
    
    override fun toString(): String = value
}

/**
 * Value class representing a folder name with validation
 */
@JvmInline
value class FolderName(val value: String) {
    init {
        require(value.length in 3..30) { "Folder name must be 3-30 characters: ${value.length}" }
        require(value.isNotBlank()) { "Folder name cannot be blank" }
        require(value.trim() == value) { "Folder name cannot have leading or trailing whitespace" }
    }
    
    companion object {
        const val MIN_LENGTH = 3
        const val MAX_LENGTH = 30
        
        /**
         * Creates a FolderName from a string value
         */
        fun fromString(value: String): FolderName = FolderName(value)
    }
    
    override fun toString(): String = value
}

/**
 * Domain model representing a folder for organizing workout templates
 */
data class Folder(
    val id: FolderId,
    val userId: String,
    val name: FolderName,
    val createdAt: Instant,
    val updatedAt: Instant,
    val templateCount: Int = 0
) {
    init {
        require(userId.isNotBlank()) { "User ID cannot be blank" }
        require(templateCount >= 0) { "Template count cannot be negative: $templateCount" }
        require(!updatedAt.isBefore(createdAt)) { "Updated timestamp cannot be before created timestamp" }
    }
    
    companion object {
        const val DEFAULT_FOLDER_NAME = "Uncategorized"
        
        /**
         * Creates a new folder with current timestamp
         */
        fun create(
            userId: String,
            name: String,
            id: FolderId = FolderId.generate()
        ): Folder {
            val now = Instant.now()
            return Folder(
                id = id,
                userId = userId,
                name = FolderName.fromString(name),
                createdAt = now,
                updatedAt = now,
                templateCount = 0
            )
        }
        
        /**
         * Creates the default "Uncategorized" folder for a user
         */
        fun createDefault(userId: String): Folder {
            return create(
                userId = userId,
                name = DEFAULT_FOLDER_NAME,
                id = FolderId.fromString("uncategorized_$userId")
            )
        }
    }
    
    /**
     * Updates the template count
     */
    fun updateTemplateCount(newCount: Int): Folder {
        require(newCount >= 0) { "Template count cannot be negative: $newCount" }
        return copy(
            templateCount = newCount,
            updatedAt = Instant.now()
        )
    }
    
    /**
     * Renames the folder
     */
    fun rename(newName: String): Folder {
        return copy(
            name = FolderName.fromString(newName),
            updatedAt = Instant.now()
        )
    }
    
    /**
     * Checks if this is the default "Uncategorized" folder
     */
    fun isDefault(): Boolean = name.value == DEFAULT_FOLDER_NAME
}