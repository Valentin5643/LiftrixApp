package com.example.liftrix.domain.interactor.workout

import com.example.liftrix.domain.model.ExerciseCategory
import com.example.liftrix.domain.model.ExerciseId
import com.example.liftrix.domain.model.UnifiedWorkoutSession
import com.example.liftrix.domain.model.Weight
import com.example.liftrix.domain.model.WorkoutTemplate
import com.example.liftrix.domain.usecase.session.SessionOperationsUseCase
import javax.inject.Inject

class SessionInteractor @Inject constructor(
    private val sessionOperationsUseCase: SessionOperationsUseCase
) {
    suspend fun addExercise(exerciseId: ExerciseId): Result<Unit> =
        sessionOperationsUseCase.addExercise(exerciseId)

    suspend fun addCustomExercise(
        name: String,
        category: ExerciseCategory,
        primaryMuscle: ExerciseCategory,
        initialSets: Int = 3
    ): Result<Unit> =
        sessionOperationsUseCase.addCustomExercise(name, category, primaryMuscle, initialSets)

    suspend fun addMultipleExercises(exerciseIds: List<ExerciseId>): Result<Unit> =
        sessionOperationsUseCase.addMultipleExercises(exerciseIds)

    suspend fun toggleSetCompletion(exerciseId: ExerciseId, setNumber: Int): Result<Unit> =
        sessionOperationsUseCase.toggleSetCompletion(exerciseId, setNumber)

    suspend fun markSetCompleted(
        exerciseId: ExerciseId,
        setNumber: Int,
        actualReps: Int? = null,
        actualWeight: Weight? = null,
        actualTime: Long? = null,
        actualRpe: Int? = null
    ): Result<Unit> =
        sessionOperationsUseCase.markSetCompleted(exerciseId, setNumber, actualReps, actualWeight, actualTime, actualRpe)

    suspend fun createTemplateFromCurrentSession(
        templateName: String,
        description: String? = null,
        isPublic: Boolean = false
    ): Result<WorkoutTemplate> =
        sessionOperationsUseCase.createTemplateFromCurrentSession(templateName, description, isPublic)

    suspend fun createTemplateFromSession(
        session: UnifiedWorkoutSession,
        templateName: String,
        description: String? = null,
        isPublic: Boolean = false
    ): Result<WorkoutTemplate> =
        sessionOperationsUseCase.createTemplateFromSession(session, templateName, description, isPublic)

    suspend fun createTemplateWithSmartDefaults(
        session: UnifiedWorkoutSession,
        templateName: String? = null
    ): Result<WorkoutTemplate> =
        sessionOperationsUseCase.createTemplateWithSmartDefaults(session, templateName)

    fun validateTemplateCreation(session: UnifiedWorkoutSession): Result<Unit> =
        sessionOperationsUseCase.validateTemplateCreation(session)
}
