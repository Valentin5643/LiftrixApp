package com.example.liftrix.domain.model.ai

import com.example.liftrix.domain.model.WorkoutTemplate

data class WorkoutGenerationResult(
    val previewId: String = java.util.UUID.randomUUID().toString(),
    val program: GeneratedWorkoutProgram,
    val validationWarnings: List<String> = emptyList(),
    val savedTemplates: List<WorkoutTemplate> = emptyList(),
    val cacheHit: Boolean = false,
    val repairAttempts: Int = 0,
    val tokensUsed: Int = 0,
    val processingTimeMs: Long = 0L,
    val modelVersion: String? = null,
    val sourceReference: WorkoutProgramSourceReference? = null,
    val changeSummaries: List<WorkoutProgramChangeSummary> = emptyList(),
    val significance: WorkoutModificationSignificance = WorkoutModificationSignificance.MINOR,
    val requiresConfirmation: Boolean = false,
    val defaultSaveMode: WorkoutModificationSaveMode = WorkoutModificationSaveMode.COPY,
    val optionalQuestion: String? = null,
    val saveTargetTemplateId: String? = null
) {
    val savedTemplateIds: List<String>
        get() = savedTemplates.map { it.id.value }
}

data class SavedGeneratedWorkoutDay(
    val dayIndex: Int,
    val template: WorkoutTemplate
)

sealed interface WorkoutProgramSaveOutcome {
    val savedDays: List<SavedGeneratedWorkoutDay>

    data class Complete(
        override val savedDays: List<SavedGeneratedWorkoutDay>
    ) : WorkoutProgramSaveOutcome

    data class Partial(
        override val savedDays: List<SavedGeneratedWorkoutDay>,
        val failedDayIndex: Int,
        val error: Throwable
    ) : WorkoutProgramSaveOutcome
}

data class WorkoutGenerationValidationResult(
    val program: GeneratedWorkoutProgram,
    val warnings: List<String> = emptyList()
)
