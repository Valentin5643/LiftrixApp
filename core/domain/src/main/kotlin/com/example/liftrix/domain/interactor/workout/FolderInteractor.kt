package com.example.liftrix.domain.interactor.workout

import com.example.liftrix.domain.model.Folder
import com.example.liftrix.domain.model.FolderId
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.usecase.folder.FolderOperationsUseCase
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class FolderInteractor @Inject constructor(
    private val folderOperationsUseCase: FolderOperationsUseCase
) {
    operator fun invoke(userId: String): Flow<Result<List<Folder>>> =
        folderOperationsUseCase(userId)

    suspend fun create(userId: String, name: String): LiftrixResult<Folder> =
        folderOperationsUseCase.create(userId, name)

    suspend fun delete(userId: String, folderId: FolderId): LiftrixResult<Unit> =
        folderOperationsUseCase.delete(userId, folderId)

    suspend fun move(userId: String, templateId: String, targetFolderId: FolderId): LiftrixResult<Unit> =
        folderOperationsUseCase.move(userId, templateId, targetFolderId)

    suspend fun reorder(
        userId: String,
        folders: List<Folder>,
        orderedFolderIds: List<FolderId>
    ): Result<List<Folder>> =
        folderOperationsUseCase.reorder(userId, folders, orderedFolderIds)

    fun getFolderOrder(userId: String): List<String>? =
        folderOperationsUseCase.getFolderOrder(userId)

    fun applyStoredOrder(userId: String, folders: List<Folder>): List<Folder> =
        folderOperationsUseCase.applyStoredOrder(userId, folders)

    fun clearStoredOrder(userId: String) {
        folderOperationsUseCase.clearStoredOrder(userId)
    }
}
