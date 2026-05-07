package com.example.liftrix.domain.usecase.folder

import com.example.liftrix.domain.model.Folder
import com.example.liftrix.domain.model.FolderId
import com.example.liftrix.domain.model.FolderName
import com.example.liftrix.domain.model.UserProfile
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.FolderRepository
import com.example.liftrix.domain.repository.ProfileRepository
import com.example.liftrix.domain.repository.template.TemplateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import com.example.liftrix.domain.util.DomainLogger as Timber
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Consolidated use case for all folder CRUD operations.
 *
 * This use case consolidates:
 * - GetFoldersUseCase (query folders)
 * - CreateFolderUseCase (create new folder)
 * - DeleteFolderUseCase (delete folder with template reallocation)
 * - MoveFolderUseCase (move template to folder)
 * - ReorderFoldersUseCase (reorder folders in memory)
 *
 * **Consolidation Rationale**:
 * - All use cases operate on Folder entities
 * - Share common validation logic
 * - Share folder ownership and authentication checks
 * - Simple CRUD operations benefit from consolidation (80% reduction from 5→1)
 *
 * **Operations**:
 * - invoke(userId): Get all folders for user (Flow)
 * - create(userId, name): Create new folder
 * - delete(userId, folderId): Delete folder (moves templates to default)
 * - move(userId, templateId, targetFolderId): Move template between folders
 * - reorder(userId, folders, orderedFolderIds): Reorder folders
 */
@Singleton
class FolderOperationsUseCase @Inject constructor(
    private val folderRepository: FolderRepository,
    private val profileRepository: ProfileRepository,
    private val workoutTemplateRepository: TemplateRepository
) {

    // In-memory storage for folder order (per user)
    private val folderOrderMap = mutableMapOf<String, List<String>>()

    // ========== QUERY OPERATIONS ==========

    /**
     * Gets all folders for the specified user (Flow).
     *
     * **Replaces**: GetFoldersUseCase.invoke()
     *
     * @param userId The user ID
     * @return Flow emitting Result with list of folders or error
     */
    operator fun invoke(userId: String): Flow<Result<List<Folder>>> {
        return try {
            require(userId.isNotBlank()) { "User ID cannot be blank" }

            folderRepository.getAllFoldersForUser(userId)
                .map { folders ->
                    Timber.d("FOLDER-OPERATIONS: Raw folders from DAO: ${folders.size} folders")

                    // Always ensure default folder is included
                    val hasUncategorized = folders.any { it.id.value.startsWith("uncategorized_") }

                    if (!hasUncategorized) {
                        Timber.w("FOLDER-OPERATIONS: Uncategorized folder missing, creating/fetching it")
                        // Create or get default folder
                        val defaultFolderResult = folderRepository.getOrCreateDefaultFolder(userId)
                        when {
                            defaultFolderResult.isSuccess -> {
                                val defaultFolder = defaultFolderResult.getOrThrow()
                                val allFolders = listOf(defaultFolder) + folders
                                Timber.d("FOLDER-OPERATIONS: Added missing uncategorized folder, total: ${allFolders.size}")

                                // Apply stored folder order
                                val orderedFolders = applyStoredOrder(userId, allFolders)
                                Result.success(orderedFolders)
                            }
                            else -> Result.failure(
                                defaultFolderResult.exceptionOrNull() ?: RuntimeException("Failed to create default folder")
                            )
                        }
                    } else {
                        Timber.d("FOLDER-OPERATIONS: Uncategorized folder found in results")
                        // Apply stored folder order or default sorting
                        val orderedFolders = applyStoredOrder(userId, folders)
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

    // ========== CREATE OPERATIONS ==========

    /**
     * Creates a new folder for the specified user.
     *
     * **Replaces**: CreateFolderUseCase.invoke()
     *
     * @param userId The user ID
     * @param name The folder name
     * @return LiftrixResult containing the created folder or error
     */
    suspend fun create(userId: String, name: String): LiftrixResult<Folder> {
        return liftrixCatching(
            errorMapper = { throwable ->
                Timber.e(throwable, "FOLDER-OPERATIONS: Exception during folder creation")
                when (throwable) {
                    is IllegalArgumentException -> LiftrixError.ValidationError(
                        field = "folderName",
                        violations = listOf(throwable.message ?: "Invalid input"),
                        analyticsContext = mapOf("operation" to "CREATE_FOLDER")
                    )
                    else -> LiftrixError.BusinessLogicError(
                        code = "FOLDER_CREATION_FAILED",
                        errorMessage = "Failed to create folder: ${throwable.message}",
                        analyticsContext = mapOf("operation" to "CREATE_FOLDER", "userId" to userId)
                    )
                }
            }
        ) {
            Timber.d("FOLDER-OPERATIONS: Starting folder creation - userId: '$userId', name: '$name'")

            // Validate inputs
            require(userId.isNotBlank()) { "User ID cannot be blank" }
            require(name.isNotBlank()) { "Folder name cannot be blank" }
            require(name.trim().length in FolderName.MIN_LENGTH..FolderName.MAX_LENGTH) {
                "Folder name must be between ${FolderName.MIN_LENGTH} and ${FolderName.MAX_LENGTH} characters"
            }

            // Ensure user profile exists (required for foreign key constraint)
            val hasProfileBefore = profileRepository.hasProfile(userId)
            Timber.d("FOLDER-OPERATIONS: Profile existence check - hasProfile: $hasProfileBefore")

            if (!hasProfileBefore) {
                Timber.d("FOLDER-OPERATIONS: User profile doesn't exist, creating minimal profile")
                val minimalProfile = UserProfile.createMinimal(userId)
                profileRepository.saveProfile(minimalProfile).getOrThrow()
                Timber.d("FOLDER-OPERATIONS: Minimal user profile created successfully")
            }

            // Check if folder name already exists for this user
            val trimmedName = name.trim()
            val nameExists = folderRepository.doesFolderNameExist(userId, trimmedName)
            Timber.d("FOLDER-OPERATIONS: Name existence check - exists: $nameExists")

            if (nameExists) {
                Timber.w("FOLDER-OPERATIONS: Folder name '$trimmedName' already exists for user '$userId'")
                throw IllegalArgumentException("Folder name '$trimmedName' already exists")
            }

            val folder = Folder(
                id = FolderId.generate(),
                userId = userId,
                name = FolderName(trimmedName),
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
                templateCount = 0
            )
            Timber.d("FOLDER-OPERATIONS: Created folder object - id: '${folder.id.value}', name: '${folder.name.value}'")

            folderRepository.createFolder(folder).getOrThrow()
        }
    }

    // ========== DELETE OPERATIONS ==========

    /**
     * Deletes a folder and moves all its templates to the default folder.
     *
     * **Replaces**: DeleteFolderUseCase.invoke()
     *
     * @param userId The user ID
     * @param folderId The folder ID to delete
     * @return LiftrixResult indicating success or failure
     */
    suspend fun delete(userId: String, folderId: FolderId): LiftrixResult<Unit> {
        return liftrixCatching(
            errorMapper = { throwable ->
                when (throwable) {
                    is IllegalArgumentException -> LiftrixError.ValidationError(
                        field = "folderId",
                        violations = listOf(throwable.message ?: "Invalid folder ID"),
                        analyticsContext = mapOf("operation" to "DELETE_FOLDER")
                    )
                    else -> LiftrixError.BusinessLogicError(
                        code = "FOLDER_DELETION_FAILED",
                        errorMessage = "Failed to delete folder: ${throwable.message}",
                        analyticsContext = mapOf("operation" to "DELETE_FOLDER", "userId" to userId)
                    )
                }
            }
        ) {
            // Validate inputs
            require(userId.isNotBlank()) { "User ID cannot be blank" }
            require(folderId.value.isNotBlank()) { "Folder ID cannot be blank" }

            // Step 1: Verify user owns the folder
            val folderResult = folderRepository.getFolderByIdDirect(folderId, userId)
            val folder = folderResult.getOrNull()
                ?: throw IllegalArgumentException("Folder not found or not owned by user")

            // Step 2: Prevent deletion of default "Uncategorized" folder
            if (folder.id.value.startsWith("uncategorized_")) {
                throw IllegalArgumentException("Cannot delete the default 'Uncategorized' folder")
            }

            // Step 3: Get or create default folder for template reallocation
            val defaultFolder = folderRepository.getOrCreateDefaultFolder(userId).getOrThrow()

            // Step 4: Move templates to default folder if folder has templates
            if (folder.templateCount > 0) {
                val templatesResult = workoutTemplateRepository.getTemplatesByFolder(userId, folder.id.value).first()
                val templates = templatesResult.getOrThrow()

                // Move each template to default folder
                for (template in templates) {
                    // Skip templates that are already in default folder or have null folder ID
                    if (template.folderId == null || template.folderId == defaultFolder.id.value) {
                        continue
                    }

                    folderRepository.moveTemplateToFolder(
                        templateId = template.id.value,
                        targetFolderId = defaultFolder.id,
                        userId = userId
                    ).getOrThrow()
                }
            }

            // Step 5: Delete the folder (templates have been moved to default folder)
            folderRepository.deleteFolder(folderId, userId).getOrThrow()
        }
    }

    // ========== MOVE OPERATIONS ==========

    /**
     * Moves a workout template to a different folder.
     *
     * **Replaces**: MoveFolderUseCase.invoke()
     *
     * @param userId The user ID
     * @param templateId The template ID to move
     * @param targetFolderId The target folder ID
     * @return LiftrixResult indicating success or failure
     */
    suspend fun move(userId: String, templateId: String, targetFolderId: FolderId): LiftrixResult<Unit> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.BusinessLogicError(
                    code = "MOVE_TEMPLATE_FAILED",
                    errorMessage = "Failed to move template: ${throwable.message}",
                    analyticsContext = mapOf(
                        "operation" to "MOVE_TEMPLATE",
                        "userId" to userId,
                        "templateId" to templateId
                    )
                )
            }
        ) {
            // Validate inputs
            require(userId.isNotBlank()) { "User ID cannot be blank" }
            require(templateId.isNotBlank()) { "Template ID cannot be blank" }
            require(targetFolderId.value.isNotBlank()) { "Target folder ID cannot be blank" }

            // Verify user owns the target folder
            val targetFolder = folderRepository.getFolderById(targetFolderId, userId).first()
            if (targetFolder == null) {
                throw IllegalArgumentException("Target folder not found or not owned by user")
            }

            // Verify user owns the template
            val templateIdObj = com.example.liftrix.domain.model.WorkoutTemplateId.fromString(templateId)
            val templateResult = workoutTemplateRepository.getTemplateById(templateIdObj, userId)
            val template = templateResult.getOrNull()
                ?: throw IllegalArgumentException("Template not found or not owned by user")

            // Don't move if already in target folder
            if (template.folderId == targetFolderId.value) {
                return@liftrixCatching
            }

            // Move template to target folder (this will handle template count updates automatically)
            folderRepository.moveTemplateToFolder(
                templateId = templateId,
                targetFolderId = targetFolderId,
                userId = userId
            ).getOrThrow()
        }
    }

    // ========== REORDER OPERATIONS ==========

    /**
     * Reorders folders for a user (in-memory, session-based).
     *
     * **Replaces**: ReorderFoldersUseCase.invoke()
     *
     * @param userId The user ID
     * @param folders Current folders
     * @param orderedFolderIds Desired order
     * @return Result containing the reordered folders or error
     */
    suspend fun reorder(userId: String, folders: List<Folder>, orderedFolderIds: List<FolderId>): Result<List<Folder>> {
        return try {
            // Separate system folders from user folders
            val systemFolders = folders.filter { it.isDefault() }
            val userFolders = folders.filter { !it.isDefault() }

            // Only validate and reorder user folders
            val userFolderIds = orderedFolderIds.filter { orderId ->
                userFolders.any { it.id == orderId }
            }

            // Validate only user folders
            require(userId.isNotBlank()) { "User ID cannot be blank" }

            // Skip validation if no user folders to reorder
            if (userFolders.isNotEmpty() && userFolderIds.isNotEmpty()) {
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

            // Store the new order for user folders only
            val orderToStore = userFolderIds.map { it.value }
            folderOrderMap[userId] = orderToStore

            // Reorder user folders
            val reorderedUserFolders = userFolderIds.mapNotNull { folderId ->
                userFolders.find { it.id == folderId }
            }

            // Combine system folders (maintain original position) + reordered user folders
            val finalFolderOrder = systemFolders + reorderedUserFolders

            Result.success(finalFolderOrder)
        } catch (e: Exception) {
            Timber.e("Folder reorder failed: ${e.message}")
            Result.failure(e)
        }
    }

    // ========== HELPER METHODS ==========

    /**
     * Gets the stored folder order for a user, or null if no custom order exists.
     */
    fun getFolderOrder(userId: String): List<String>? {
        return folderOrderMap[userId]
    }

    /**
     * Applies stored folder order to a list of folders.
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
     * Clears stored order for a user (useful for testing or reset functionality).
     */
    fun clearStoredOrder(userId: String) {
        folderOrderMap.remove(userId)
    }
}
