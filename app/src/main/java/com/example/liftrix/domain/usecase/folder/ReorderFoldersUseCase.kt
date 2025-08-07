package com.example.liftrix.domain.usecase.folder

import com.example.liftrix.domain.model.Folder
import com.example.liftrix.domain.model.FolderId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use case for reordering folders in memory (without database migration)
 * 
 * This use case provides folder reordering functionality that maintains
 * order during the current session but resets on app restart.
 * 
 * Features:
 * - In-memory folder reordering
 * - Session-based ordering persistence
 * - Validation of folder ownership
 */
@Singleton
class ReorderFoldersUseCase @Inject constructor() {
    
    // In-memory storage for folder order (per user)
    private val folderOrderMap = mutableMapOf<String, List<String>>()
    
    /**
     * Reorders folders for a user
     * 
     * @param input The reorder operation input
     * @return Result indicating success or failure
     */
    suspend operator fun invoke(input: ReorderFoldersInput): Result<List<Folder>> {
        return try {
            // Separate system folders from user folders
            val systemFolders = input.folders.filter { it.isDefault() }
            val userFolders = input.folders.filter { !it.isDefault() }
            
            // Only validate and reorder user folders
            val userFolderIds = input.orderedFolderIds.filter { orderId ->
                userFolders.any { it.id == orderId }
            }
            
            // Validate only user folders
            validateUserFolders(input.userId, userFolders, userFolderIds)
            
            // Store the new order for user folders only
            val orderToStore = userFolderIds.map { it.value }
            folderOrderMap[input.userId] = orderToStore
            
            // Reorder user folders
            val reorderedUserFolders = userFolderIds.mapNotNull { folderId ->
                userFolders.find { it.id == folderId }
            }
            
            // Combine system folders (maintain original position) + reordered user folders
            val finalFolderOrder = systemFolders + reorderedUserFolders
            
            Result.success(finalFolderOrder)
        } catch (e: Exception) {
            timber.log.Timber.e("Folder reorder failed: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Gets the stored folder order for a user, or null if no custom order exists
     */
    fun getFolderOrder(userId: String): List<String>? {
        return folderOrderMap[userId]
    }
    
    /**
     * Applies stored folder order to a list of folders
     */
    fun applyStoredOrder(userId: String, folders: List<Folder>): List<Folder> {
        val storedOrder = getFolderOrder(userId) ?: return folders.sortedBy { it.createdAt }
        
        // Apply stored order
        val orderedFolders = storedOrder.mapNotNull { folderId ->
            folders.find { it.id.value == folderId }
        }
        
        // Add any folders not in the stored order
        val missingFolders = folders.filter { folder ->
            !storedOrder.contains(folder.id.value)
        }.sortedBy { it.createdAt }
        
        return orderedFolders + missingFolders
    }
    
    /**
     * Clears stored order for a user (useful for testing or reset functionality)
     */
    fun clearStoredOrder(userId: String) {
        folderOrderMap.remove(userId)
    }
    
    /**
     * Validates user folders only (excludes system folders)
     */
    private fun validateUserFolders(userId: String, userFolders: List<Folder>, userFolderIds: List<FolderId>) {
        require(userId.isNotBlank()) { "User ID cannot be blank" }
        
        // Skip validation if no user folders to reorder
        if (userFolders.isEmpty() || userFolderIds.isEmpty()) {
            return
        }
        
        // Enhanced validation with detailed error information for user folders only
        if (userFolderIds.size != userFolders.size) {
            val orderedIds = userFolderIds.map { it.value }
            val folderIds = userFolders.map { it.id.value }
            val missingInOrder = folderIds.filter { !orderedIds.contains(it) }
            val extraInOrder = orderedIds.filter { !folderIds.contains(it) }
            
            val errorDetails = buildString {
                append("User folder reorder validation failed: ")
                append("Expected ${userFolders.size} user folder IDs, got ${userFolderIds.size}. ")
                if (missingInOrder.isNotEmpty()) {
                    append("Missing user folders from reorder list: $missingInOrder. ")
                }
                if (extraInOrder.isNotEmpty()) {
                    append("Extra user folder IDs in reorder list: $extraInOrder. ")
                }
                append("Current user folders: $folderIds, ")
                append("Ordered user folder list: $orderedIds")
            }
            
            throw IllegalArgumentException(errorDetails)
        }
    }
    
    /**
     * Original validation method - kept for backward compatibility
     */
    private fun validateInput(input: ReorderFoldersInput) {
        require(input.userId.isNotBlank()) { "User ID cannot be blank" }
        require(input.orderedFolderIds.isNotEmpty()) { "Ordered folder IDs cannot be empty" }
        require(input.folders.isNotEmpty()) { "Folders list cannot be empty" }
        
        // Enhanced validation with detailed error information
        if (input.orderedFolderIds.size != input.folders.size) {
            val orderedIds = input.orderedFolderIds.map { it.value }
            val folderIds = input.folders.map { it.id.value }
            val missingInOrder = folderIds.filter { !orderedIds.contains(it) }
            val extraInOrder = orderedIds.filter { !folderIds.contains(it) }
            
            val errorDetails = buildString {
                append("Folder reorder validation failed: ")
                append("Expected ${input.folders.size} folder IDs, got ${input.orderedFolderIds.size}. ")
                if (missingInOrder.isNotEmpty()) {
                    append("Missing from reorder list: $missingInOrder. ")
                }
                if (extraInOrder.isNotEmpty()) {
                    append("Extra in reorder list: $extraInOrder. ")
                }
                append("Current folders: $folderIds, ")
                append("Ordered list: $orderedIds")
            }
            
            throw IllegalArgumentException(errorDetails)
        }
    }
    
    /**
     * Input data class for reordering folders
     */
    data class ReorderFoldersInput(
        val userId: String,
        val folders: List<Folder>, // Current folders
        val orderedFolderIds: List<FolderId> // Desired order
    )
}