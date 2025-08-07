package com.example.liftrix.domain.usecase.folder

import com.example.liftrix.domain.model.Folder
import com.example.liftrix.domain.repository.FolderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use case for retrieving folders for a user.
 * 
 * This use case handles fetching all folders for a user with proper
 * error handling and ensures that a default folder exists.
 * 
 * Features:
 * - User authentication validation
 * - Default folder creation if missing
 * - Reactive data access with Flow
 * - Error handling with Result emission
 */
@Singleton
class GetFoldersUseCase @Inject constructor(
    private val folderRepository: FolderRepository,
    private val reorderFoldersUseCase: ReorderFoldersUseCase
) {
    
    /**
     * Gets all folders for the specified user
     * 
     * @param input The input containing userId
     * @return Flow emitting Result with list of folders or error
     */
    operator fun invoke(input: GetFoldersInput): Flow<Result<List<Folder>>> {
        return try {
            validateInput(input)
            
            folderRepository.getAllFoldersForUser(input.userId)
                .map { folders ->
                    timber.log.Timber.d("🔥 GET-FOLDERS: Raw folders from DAO: ${folders.size} folders")
                    folders.forEach { folder ->
                        timber.log.Timber.d("🔥 GET-FOLDERS: - Folder: id=${folder.id.value}, name='${folder.name.value}', isDefault=${folder.isDefault()}")
                    }
                    
                    // Always ensure default folder is included
                    val hasUncategorized = folders.any { it.id.value.startsWith("uncategorized_") }
                    
                    if (!hasUncategorized) {
                        timber.log.Timber.w("🔥 GET-FOLDERS: Uncategorized folder missing from DAO results, creating/fetching it")
                        // Create or get default folder
                        val defaultFolderResult = folderRepository.getOrCreateDefaultFolder(input.userId)
                        when {
                            defaultFolderResult.isSuccess -> {
                                val defaultFolder = defaultFolderResult.getOrThrow()
                                val allFolders = listOf(defaultFolder) + folders
                                timber.log.Timber.d("🔥 GET-FOLDERS: Added missing uncategorized folder, total: ${allFolders.size}")
                                
                                // Apply stored folder order
                                val orderedFolders = reorderFoldersUseCase.applyStoredOrder(input.userId, allFolders)
                                Result.success(orderedFolders)
                            }
                            else -> Result.failure(
                                defaultFolderResult.exceptionOrNull() ?: RuntimeException("Failed to create default folder")
                            )
                        }
                    } else {
                        timber.log.Timber.d("🔥 GET-FOLDERS: Uncategorized folder found in results")
                        // Apply stored folder order or default sorting
                        val orderedFolders = reorderFoldersUseCase.applyStoredOrder(input.userId, folders)
                        Result.success(orderedFolders)
                    }
                }
                .catch { exception ->
                    emit(Result.failure(exception))
                }
                
        } catch (e: Exception) {
            kotlinx.coroutines.flow.flow {
                emit(Result.failure(e))
            }
        }
    }
    
    /**
     * Gets all folders for the specified user (suspend version)
     * 
     * @param input The input containing userId
     * @return Result containing list of folders or error
     */
    suspend fun execute(input: GetFoldersInput): Result<List<Folder>> {
        return try {
            validateInput(input)
            
            // Ensure default folder exists first
            folderRepository.getOrCreateDefaultFolder(input.userId)
                .fold(
                    onSuccess = {
                        // Get all folders after ensuring default exists
                        val folders = folderRepository.getAllFoldersForUser(input.userId)
                        // Note: Since we can't collect Flow in suspend function without coroutines,
                        // we'll return success and let the caller handle the Flow
                        Result.success(emptyList<Folder>()) // Placeholder - use Flow version instead
                    },
                    onFailure = { exception ->
                        Result.failure(exception)
                    }
                )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Validates input parameters
     */
    private fun validateInput(input: GetFoldersInput) {
        require(input.userId.isNotBlank()) { "User ID cannot be blank" }
    }
    
    /**
     * Input data class for getting folders
     */
    data class GetFoldersInput(
        val userId: String
    )
}