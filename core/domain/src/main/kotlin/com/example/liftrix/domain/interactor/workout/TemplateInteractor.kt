package com.example.liftrix.domain.interactor.workout

import com.example.liftrix.domain.model.TemplateExercise
import com.example.liftrix.domain.model.UnifiedWorkoutSession
import com.example.liftrix.domain.model.Workout
import com.example.liftrix.domain.model.WorkoutTemplate
import com.example.liftrix.domain.model.WorkoutTemplateId
import com.example.liftrix.domain.model.ai.GeneratedWorkoutProgram
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.sharing.SharedTemplatePreview
import com.example.liftrix.domain.model.sharing.TemplateShareEvent
import com.example.liftrix.domain.usecase.template.GetTemplatesRequest
import com.example.liftrix.domain.usecase.template.GetTemplatesResult
import com.example.liftrix.domain.usecase.template.TemplateCommandUseCase
import com.example.liftrix.domain.usecase.template.TemplateQueryUseCase
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class TemplateInteractor @Inject constructor(
    private val templateQueryUseCase: TemplateQueryUseCase,
    private val templateCommandUseCase: TemplateCommandUseCase
) {
    operator fun invoke(userId: String): Flow<List<WorkoutTemplate>> =
        templateQueryUseCase(userId)

    fun getTemplates(request: GetTemplatesRequest): Flow<LiftrixResult<GetTemplatesResult>> =
        templateQueryUseCase.invoke(request)

    suspend fun getById(templateId: String, userId: String): LiftrixResult<Workout?> =
        templateQueryUseCase.getById(templateId, userId)

    suspend fun getPendingSharesFromBuddy(
        senderId: String,
        receiverId: String
    ): LiftrixResult<List<TemplateShareEvent>> =
        templateQueryUseCase.getPendingSharesFromBuddy(senderId, receiverId)

    suspend fun getSharedTemplatePreview(
        shareId: String,
        receiverId: String
    ): LiftrixResult<SharedTemplatePreview> =
        templateQueryUseCase.getSharedTemplatePreview(shareId, receiverId)

    suspend fun create(
        userId: String,
        name: String,
        folderId: String? = null,
        description: String? = null,
        exercises: List<TemplateExercise> = emptyList(),
        estimatedDurationMinutes: Int? = null,
        difficultyLevel: Int? = null
    ): LiftrixResult<WorkoutTemplate> =
        templateCommandUseCase.create(
            userId,
            name,
            folderId,
            description,
            exercises,
            estimatedDurationMinutes,
            difficultyLevel
        )

    suspend fun createFromSession(
        session: UnifiedWorkoutSession,
        templateName: String,
        templateDescription: String? = null
    ): LiftrixResult<WorkoutTemplate> =
        templateCommandUseCase.createFromSession(session, templateName, templateDescription)

    suspend fun duplicate(originalTemplate: WorkoutTemplate, newName: String): LiftrixResult<WorkoutTemplate> =
        templateCommandUseCase.duplicate(originalTemplate, newName)

    suspend fun delete(templateId: WorkoutTemplateId): LiftrixResult<Unit> =
        templateCommandUseCase.delete(templateId)

    suspend fun moveToFolder(
        workoutTemplate: WorkoutTemplate,
        targetFolderId: String
    ): LiftrixResult<WorkoutTemplate> =
        templateCommandUseCase.moveToFolder(workoutTemplate, targetFolderId)

    suspend fun updateFromEditedWorkout(workout: Workout): LiftrixResult<Workout> =
        templateCommandUseCase.updateFromEditedWorkout(workout)

    suspend fun updateTemplateFromAiModification(
        userId: String,
        templateId: String,
        program: GeneratedWorkoutProgram
    ): LiftrixResult<WorkoutTemplate> =
        templateCommandUseCase.updateTemplateFromAiModification(userId, templateId, program)

    suspend fun shareTemplateToBuddy(templateId: String, buddyId: String): LiftrixResult<TemplateShareEvent> =
        templateCommandUseCase.shareTemplateToBuddy(templateId, buddyId)

    suspend fun createQrTemplateShare(templateId: String): LiftrixResult<TemplateShareEvent> =
        templateCommandUseCase.createQrTemplateShare(templateId)

    suspend fun acceptSharedTemplate(shareId: String): LiftrixResult<WorkoutTemplate> =
        templateCommandUseCase.acceptSharedTemplate(shareId)
}
