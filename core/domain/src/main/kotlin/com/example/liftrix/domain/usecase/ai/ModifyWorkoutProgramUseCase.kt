package com.example.liftrix.domain.usecase.ai

import com.example.liftrix.domain.model.ExerciseId
import com.example.liftrix.domain.model.Reps
import com.example.liftrix.domain.model.TemplateExercise
import com.example.liftrix.domain.model.WorkoutTemplate
import com.example.liftrix.domain.model.ai.GeneratedPrescriptionType
import com.example.liftrix.domain.model.ai.GeneratedWorkoutDay
import com.example.liftrix.domain.model.ai.GeneratedWorkoutExercise
import com.example.liftrix.domain.model.ai.GeneratedWorkoutProgram
import com.example.liftrix.domain.model.ai.ModifiedWorkoutProgramResponse
import com.example.liftrix.domain.model.ai.WorkoutGenerationConstraints
import com.example.liftrix.domain.model.ai.WorkoutGenerationPersonalization
import com.example.liftrix.domain.model.ai.WorkoutGenerationRequest
import com.example.liftrix.domain.model.ai.WorkoutGenerationResult
import com.example.liftrix.domain.model.ai.WorkoutModificationSaveMode
import com.example.liftrix.domain.model.ai.WorkoutModificationSignificance
import com.example.liftrix.domain.model.ai.WorkoutProgramGoal
import com.example.liftrix.domain.model.ai.WorkoutProgramLevel
import com.example.liftrix.domain.model.ai.WorkoutProgramSourceReference
import com.example.liftrix.domain.model.ai.WorkoutProgramSourceType
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.service.Language
import com.example.liftrix.domain.service.WorkoutProgramGenerationService
import com.example.liftrix.domain.usecase.template.TemplateCommandUseCase
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import javax.inject.Inject

class ModifyWorkoutProgramUseCase @Inject constructor(
    private val referenceResolver: WorkoutReferenceResolver,
    private val contextUseCase: BuildWorkoutAiContextUseCase,
    private val promptBuilder: WorkoutGenerationPromptBuilder,
    private val generationService: WorkoutProgramGenerationService,
    private val validationUseCase: ValidateGeneratedWorkoutProgramUseCase,
    private val cache: WorkoutGenerationCache,
    private val templateCommandUseCase: TemplateCommandUseCase
) {

    private val json = Json {
        ignoreUnknownKeys = false
        isLenient = false
        coerceInputValues = false
        explicitNulls = false
    }

    suspend fun preview(request: ModifyWorkoutProgramRequest): LiftrixResult<WorkoutGenerationResult> =
        liftrixCatching(
            errorMapper = { throwable ->
                when (throwable) {
                    is LiftrixError -> throwable
                    else -> LiftrixError.BusinessLogicError(
                        code = "MODIFY_WORKOUT_PROGRAM_FAILED",
                        errorMessage = throwable.message ?: "Failed to modify workout program",
                        analyticsContext = mapOf("user_id" to request.userId)
                    )
                }
            }
        ) {
            require(request.userId.isNotBlank()) { "User ID cannot be blank" }
            require(request.message.isNotBlank()) { "Message cannot be blank" }

            val resolution = referenceResolver(
                WorkoutReferenceRequest(
                    userId = request.userId,
                    message = request.message,
                    pendingTemplateId = request.pendingTemplateId,
                    pendingGeneratedProgramId = request.pendingGeneratedProgram?.previewId,
                    pendingGeneratedProgramName = request.pendingGeneratedProgram?.program?.workoutName,
                    allowGeneratedPreview = request.scope != null
                )
            ).getOrThrow()
            val source = resolveSource(resolution, request.pendingGeneratedProgram)
            val context = contextUseCase(request.userId).getOrThrow()
            val taskPrompt = if (request.updateFromProgress) {
                promptBuilder.buildProgressionUpdate(request.message, source.reference, source.program, context)
            } else {
                promptBuilder.buildModification(request.message, source.reference, source.program, context, request.scope)
            }
            val cacheKey = cache.keyForOperation(
                userId = request.userId,
                operation = if (request.updateFromProgress) "progression_preview" else "modification_preview",
                normalizedPrompt = request.message.lowercase().trim(),
                sourceId = source.reference.sourceId,
                contextHash = sha256(context.toString()),
                catalogHash = taskPrompt.catalogHash
            )
            cache.get(cacheKey)?.let { return@liftrixCatching it.copy(cacheHit = true) }

            val responseJson = generationService.modifyProgramJson(
                userId = request.userId,
                userPrompt = request.message,
                systemPrompt = taskPrompt.systemPrompt,
                inputPayload = taskPrompt.inputPayload,
                language = request.language
            ).getOrThrow()
            val modifiedResponse = json.decodeFromString<ModifiedWorkoutProgramResponse>(responseJson.json)
            val validationRequest = source.validationRequest(request.userId, request.message, context.experienceLevel)
            val validation = validationUseCase.validateModification(
                response = modifiedResponse,
                sourceProgram = source.program,
                sourceId = source.reference.sourceId,
                request = validationRequest,
                exerciseCatalog = (
                    context.exerciseCatalog.map { it.toExerciseLibrary() } +
                        source.program.days.flatMap { day -> day.exercises.map { it.toExerciseLibrary() } }
                    ).distinctBy { it.id },
                scope = request.scope
            ).getOrThrow()
            val result = WorkoutGenerationResult(
                program = validation.program,
                validationWarnings = validation.warnings,
                cacheHit = false,
                repairAttempts = 0,
                tokensUsed = responseJson.tokensUsed,
                processingTimeMs = responseJson.processingTimeMs,
                modelVersion = responseJson.modelVersion,
                sourceReference = modifiedResponse.source,
                changeSummaries = modifiedResponse.changes,
                significance = modifiedResponse.significance,
                requiresConfirmation = modifiedResponse.confirmationRequired,
                defaultSaveMode = WorkoutModificationSaveMode.COPY,
                optionalQuestion = modifiedResponse.optionalQuestion,
                saveTargetTemplateId = modifiedResponse.source.sourceId.takeIf {
                    modifiedResponse.source.sourceType == WorkoutProgramSourceType.TEMPLATE
                }
            )
            cache.put(cacheKey, result)
            result
        }

    suspend fun saveConfirmedModification(
        userId: String,
        result: WorkoutGenerationResult,
        saveMode: WorkoutModificationSaveMode
    ): LiftrixResult<WorkoutGenerationResult> = liftrixCatching(
        errorMapper = { throwable ->
            when (throwable) {
                is LiftrixError -> throwable
                else -> LiftrixError.BusinessLogicError(
                    code = "SAVE_AI_WORKOUT_MODIFICATION_FAILED",
                    errorMessage = throwable.message ?: "Failed to save workout modification",
                    analyticsContext = mapOf("user_id" to userId)
                )
            }
        }
    ) {
        require(userId.isNotBlank()) { "User ID cannot be blank" }
        val savedTemplate = when (saveMode) {
            WorkoutModificationSaveMode.COPY -> templateCommandUseCase.create(
                userId = userId,
                name = result.program.workoutName,
                exercises = result.program.toTemplateExercises(),
                estimatedDurationMinutes = result.program.days.sumOf { it.estimatedDurationMinutes }
                    .coerceIn(WorkoutTemplate.MIN_DURATION_MINUTES, WorkoutTemplate.MAX_DURATION_MINUTES),
                difficultyLevel = result.program.level.toDifficulty()
            ).getOrThrow()
            WorkoutModificationSaveMode.OVERWRITE -> {
                val templateId = result.saveTargetTemplateId
                    ?: throw IllegalArgumentException("Overwrite requires a source template")
                templateCommandUseCase.updateTemplateFromAiModification(
                    userId = userId,
                    templateId = templateId,
                    program = result.program
                ).getOrThrow()
            }
        }
        result.copy(
            savedTemplates = listOf(savedTemplate),
            defaultSaveMode = saveMode,
            requiresConfirmation = false
        )
    }

    private fun resolveSource(
        resolution: WorkoutReferenceResolution,
        pendingGeneratedProgram: WorkoutGenerationResult?
    ): ResolvedWorkoutProgramSource = when (resolution) {
        is WorkoutReferenceResolution.ResolvedTemplate -> ResolvedWorkoutProgramSource(
            reference = resolution.source,
            program = resolution.template.toGeneratedProgram()
        )
        is WorkoutReferenceResolution.ResolvedGeneratedPreview -> {
            val pending = pendingGeneratedProgram
                ?: throw IllegalArgumentException("Generated preview source is no longer available")
            ResolvedWorkoutProgramSource(reference = resolution.source, program = pending.program)
        }
        is WorkoutReferenceResolution.NeedsClarification -> throw LiftrixError.ValidationError(
            field = "workout_reference",
            violations = listOf(resolution.message)
        )
        is WorkoutReferenceResolution.NotFound -> throw LiftrixError.NotFoundError(
            errorMessage = resolution.message,
            resourceType = "WorkoutTemplate",
            resourceId = "unknown"
        )
    }

    private fun WorkoutTemplate.toGeneratedProgram(): GeneratedWorkoutProgram =
        GeneratedWorkoutProgram(
            workoutName = name,
            goal = WorkoutProgramGoal.GENERAL_FITNESS,
            level = difficultyLevel.toLevel(),
            days = listOf(
                GeneratedWorkoutDay(
                    dayName = name,
                    estimatedDurationMinutes = estimatedDurationMinutes ?: 45,
                    exercises = exercises.map { it.toGeneratedExercise() }
                )
            )
        )

    private fun TemplateExercise.toGeneratedExercise(): GeneratedWorkoutExercise =
        GeneratedWorkoutExercise(
            exerciseId = exerciseId.value,
            exerciseName = name,
            primaryMuscle = primaryMuscle,
            equipment = equipment,
            sets = targetSets ?: 3,
            type = GeneratedPrescriptionType.REPS,
            repsMin = targetReps?.count ?: 8,
            repsMax = targetReps?.count ?: 12,
            durationSeconds = null,
            isUnilateral = false,
            restSeconds = restTimeSeconds ?: 90,
            notes = notes
        )

    private fun GeneratedWorkoutProgram.toTemplateExercises(): List<TemplateExercise> =
        days.flatMap { it.exercises }.take(WorkoutTemplate.MAX_EXERCISES).mapIndexed { index, exercise ->
            TemplateExercise(
                exerciseId = ExerciseId.fromString(exercise.exerciseId),
                name = exercise.exerciseName,
                primaryMuscle = exercise.primaryMuscle,
                equipment = exercise.equipment,
                targetSets = exercise.sets,
                targetReps = when (exercise.type) {
                    GeneratedPrescriptionType.REPS -> Reps(exercise.repsMax ?: exercise.repsMin ?: 1)
                    GeneratedPrescriptionType.TIME -> null
                },
                restTimeSeconds = exercise.restSeconds,
                notes = exercise.notes,
                orderIndex = index,
                instanceId = exercise.exerciseId
            )
        }

    private fun ResolvedWorkoutProgramSource.validationRequest(
        userId: String,
        message: String,
        level: WorkoutProgramLevel
    ): WorkoutGenerationRequest =
        WorkoutGenerationRequest(
            userId = userId,
            userPrompt = message,
            normalizedConstraints = WorkoutGenerationConstraints(
                daysPerWeek = program.days.size,
                level = level,
                goal = program.goal,
                allowedEquipment = program.days.flatMap { it.exercises }.map { it.equipment }.toSet(),
                sessionDurationMinutes = program.days.firstOrNull()?.estimatedDurationMinutes ?: 45
            ),
            personalization = WorkoutGenerationPersonalization(experienceLevel = level)
        )

    private fun com.example.liftrix.domain.model.ai.WorkoutGenerationCatalogExercise.toExerciseLibrary() =
        com.example.liftrix.domain.model.ExerciseLibrary(
            id = exerciseId,
            name = exerciseName,
            primaryMuscleGroup = primaryMuscle,
            equipment = equipment,
            secondaryMuscleGroups = emptyList(),
            movementPattern = movementPattern,
            difficultyLevel = difficultyLevel,
            instructions = null,
            isCompound = isCompound,
            searchableTerms = listOf(exerciseId, exerciseName.lowercase())
        )

    private fun GeneratedWorkoutExercise.toExerciseLibrary() =
        com.example.liftrix.domain.model.ExerciseLibrary(
            id = exerciseId,
            name = exerciseName,
            primaryMuscleGroup = primaryMuscle,
            equipment = equipment,
            secondaryMuscleGroups = emptyList(),
            movementPattern = when (type) {
                GeneratedPrescriptionType.REPS -> "Strength"
                GeneratedPrescriptionType.TIME -> "Timed"
            },
            difficultyLevel = 2,
            instructions = null,
            isCompound = true,
            searchableTerms = listOf(exerciseId, exerciseName.lowercase())
        )

    private fun Int?.toLevel(): WorkoutProgramLevel = when {
        this == null || this <= 3 -> WorkoutProgramLevel.BEGINNER
        this <= 6 -> WorkoutProgramLevel.INTERMEDIATE
        else -> WorkoutProgramLevel.ADVANCED
    }

    private fun WorkoutProgramLevel.toDifficulty(): Int = when (this) {
        WorkoutProgramLevel.BEGINNER -> 2
        WorkoutProgramLevel.INTERMEDIATE -> 5
        WorkoutProgramLevel.ADVANCED -> 8
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private data class ResolvedWorkoutProgramSource(
        val reference: WorkoutProgramSourceReference,
        val program: GeneratedWorkoutProgram
    )
}
