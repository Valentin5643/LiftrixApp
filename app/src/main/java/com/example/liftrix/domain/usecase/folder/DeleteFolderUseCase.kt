package com.example.liftrix.domain.usecase.folder

import com.example.liftrix.domain.model.FolderId
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.FolderRepository
import com.example.liftrix.domain.repository.WorkoutTemplateRepository
import javax.inject.Inject

class DeleteFolderUseCase @Inject constructor(
    private val folderRepository: FolderRepository,
    private val workoutTemplateRepository: WorkoutTemplateRepository
) {
    data class DeleteFolderInput(
        val userId: String,
        val folderId: FolderId
    )

    suspend operator fun invoke(input: DeleteFolderInput): LiftrixResult<Unit> {
        if (input.userId.isBlank()) {
            return LiftrixResult.failure(
                LiftrixError.ValidationError(
                    field = "userId",
                    violations = listOf("User ID cannot be blank")
                )
            )
        }
        return LiftrixResult.success(Unit)
    }
}
