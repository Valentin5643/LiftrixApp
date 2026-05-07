package com.example.liftrix.domain.usecase.ai

import com.example.liftrix.domain.model.ai.GeneratedWorkoutProgram
import com.example.liftrix.domain.model.ai.WorkoutGenerationResult
import com.example.liftrix.domain.model.ai.WorkoutModificationSaveMode
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.service.Language

interface WorkoutProgramGateway {
    suspend fun generate(
        userId: String,
        prompt: String,
        language: Language
    ): LiftrixResult<WorkoutGenerationResult>

    suspend fun saveGeneratedProgram(
        userId: String,
        program: GeneratedWorkoutProgram
    ): LiftrixResult<WorkoutGenerationResult>

    suspend fun previewModification(
        request: ModifyWorkoutProgramRequest
    ): LiftrixResult<WorkoutGenerationResult>

    suspend fun saveConfirmedModification(
        userId: String,
        result: WorkoutGenerationResult,
        saveMode: WorkoutModificationSaveMode
    ): LiftrixResult<WorkoutGenerationResult>
}

data class ModifyWorkoutProgramRequest(
    val userId: String,
    val message: String,
    val language: Language = Language.ENGLISH,
    val pendingTemplateId: String? = null,
    val pendingGeneratedProgram: WorkoutGenerationResult? = null,
    val updateFromProgress: Boolean = false
)
