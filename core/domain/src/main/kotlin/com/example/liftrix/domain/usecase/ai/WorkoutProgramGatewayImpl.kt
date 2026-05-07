package com.example.liftrix.domain.usecase.ai

import com.example.liftrix.domain.model.ai.GeneratedWorkoutProgram
import com.example.liftrix.domain.model.ai.WorkoutGenerationResult
import com.example.liftrix.domain.model.ai.WorkoutModificationSaveMode
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.service.Language
import javax.inject.Inject

class WorkoutProgramGatewayImpl @Inject constructor(
    private val generateWorkoutProgramUseCase: GenerateWorkoutProgramUseCase,
    private val modifyWorkoutProgramUseCase: ModifyWorkoutProgramUseCase
) : WorkoutProgramGateway {
    override suspend fun generate(
        userId: String,
        prompt: String,
        language: Language
    ): LiftrixResult<WorkoutGenerationResult> =
        generateWorkoutProgramUseCase(
            userId = userId,
            prompt = prompt,
            language = language
        )

    override suspend fun saveGeneratedProgram(
        userId: String,
        program: GeneratedWorkoutProgram
    ): LiftrixResult<WorkoutGenerationResult> =
        generateWorkoutProgramUseCase.saveGeneratedProgram(
            userId = userId,
            program = program
        )

    override suspend fun previewModification(
        request: ModifyWorkoutProgramRequest
    ): LiftrixResult<WorkoutGenerationResult> =
        modifyWorkoutProgramUseCase.preview(request)

    override suspend fun saveConfirmedModification(
        userId: String,
        result: WorkoutGenerationResult,
        saveMode: WorkoutModificationSaveMode
    ): LiftrixResult<WorkoutGenerationResult> =
        modifyWorkoutProgramUseCase.saveConfirmedModification(
            userId = userId,
            result = result,
            saveMode = saveMode
        )
}
