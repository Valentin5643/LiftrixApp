package com.example.liftrix.domain.service

import com.example.liftrix.domain.model.common.LiftrixResult

interface WorkoutProgramGenerationService {
    suspend fun generateProgramJson(
        userId: String,
        userPrompt: String,
        systemPrompt: String,
        inputPayload: String,
        language: Language = Language.ENGLISH
    ): LiftrixResult<WorkoutProgramJsonResponse>

    suspend fun repairProgramJson(
        userId: String,
        userPrompt: String,
        systemPrompt: String,
        inputPayload: String,
        invalidJson: String,
        repairInstruction: String,
        language: Language = Language.ENGLISH
    ): LiftrixResult<WorkoutProgramJsonResponse>

    suspend fun modifyProgramJson(
        userId: String,
        userPrompt: String,
        systemPrompt: String,
        inputPayload: String,
        language: Language = Language.ENGLISH
    ): LiftrixResult<WorkoutProgramJsonResponse>
}

data class WorkoutProgramJsonResponse(
    val json: String,
    val tokensUsed: Int,
    val processingTimeMs: Long,
    val modelVersion: String
)
