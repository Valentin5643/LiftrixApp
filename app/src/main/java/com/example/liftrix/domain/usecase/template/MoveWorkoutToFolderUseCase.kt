package com.example.liftrix.domain.usecase.template

import com.example.liftrix.domain.model.FolderId
import com.example.liftrix.domain.model.WorkoutTemplate
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.repository.FolderRepository
import com.example.liftrix.domain.repository.WorkoutTemplateRepository
import java.time.Instant
import javax.inject.Inject

class MoveWorkoutToFolderUseCase @Inject constructor(
    private val workoutTemplateRepository: WorkoutTemplateRepository,
    private val folderRepository: FolderRepository
) {
    suspend operator fun invoke(
        workoutTemplate: WorkoutTemplate,
        targetFolderId: String
    ): LiftrixResult<WorkoutTemplate> {
        return try {
            if (workoutTemplate.folderId == targetFolderId) {
                return LiftrixResult.success(workoutTemplate)
            }
            val folder = folderRepository.getFolderById(FolderId(targetFolderId))
                ?: return LiftrixResult.failure(IllegalArgumentException("Target folder does not exist"))
            if (folder.userId != workoutTemplate.userId) {
                return LiftrixResult.failure(SecurityException("Cannot move workout to folder belonging to different user"))
            }

            val movedTemplate = workoutTemplate.copy(
                folderId = targetFolderId,
                updatedAt = Instant.now()
            )
            workoutTemplateRepository.updateTemplate(movedTemplate).fold(
                onSuccess = { LiftrixResult.success(it) },
                onFailure = { error ->
                    LiftrixResult.failure(RuntimeException("Failed to update workout template: ${error.message}", error))
                }
            )
        } catch (throwable: Throwable) {
            LiftrixResult.failure(throwable)
        }
    }
}
