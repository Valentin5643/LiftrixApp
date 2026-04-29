package com.example.liftrix.domain.usecase.ai

import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.ExerciseCategory
import com.example.liftrix.domain.model.ExerciseId
import com.example.liftrix.domain.model.ExerciseLibrary
import com.example.liftrix.domain.model.FitnessGoal
import com.example.liftrix.domain.model.Reps
import com.example.liftrix.domain.model.TemplateExercise
import com.example.liftrix.domain.model.UserProfile
import com.example.liftrix.domain.model.ai.GeneratedPrescriptionType
import com.example.liftrix.domain.model.ai.GeneratedWorkoutDay
import com.example.liftrix.domain.model.ai.GeneratedWorkoutExercise
import com.example.liftrix.domain.model.ai.GeneratedWorkoutProgram
import com.example.liftrix.domain.model.ai.GeneratedWorkoutProgramError
import com.example.liftrix.domain.model.ai.WorkoutGenerationConstraints
import com.example.liftrix.domain.model.ai.WorkoutGenerationPersonalization
import com.example.liftrix.domain.model.ai.WorkoutGenerationRequest
import com.example.liftrix.domain.model.ai.WorkoutGenerationResult
import com.example.liftrix.domain.model.ai.WorkoutProgramGoal
import com.example.liftrix.domain.model.ai.WorkoutProgramLevel
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixFailure
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.service.Language
import com.example.liftrix.domain.service.WorkoutProgramGenerationService
import com.example.liftrix.domain.usecase.exercise.ExerciseQueryUseCase
import com.example.liftrix.domain.usecase.profile.ProfileQueryUseCase
import com.example.liftrix.domain.usecase.template.TemplateCommandUseCase
import javax.inject.Inject
import kotlinx.serialization.SerialName
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber

class GenerateWorkoutProgramUseCase @Inject constructor(
    private val profileQueryUseCase: ProfileQueryUseCase,
    private val exerciseQueryUseCase: ExerciseQueryUseCase,
    private val templateCommandUseCase: TemplateCommandUseCase,
    private val generationService: WorkoutProgramGenerationService,
    private val promptBuilder: WorkoutGenerationPromptBuilder,
    private val validator: ValidateGeneratedWorkoutProgramUseCase,
    private val cache: WorkoutGenerationCache
) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    suspend operator fun invoke(
        userId: String,
        prompt: String,
        language: Language = Language.ENGLISH,
        saveAfterGeneration: Boolean = false
    ): LiftrixResult<WorkoutGenerationResult> {
        Timber.i("GenerateWorkoutProgramUseCase: request started user=$userId promptChars=${prompt.length} saveAfterGeneration=$saveAfterGeneration")
        if (userId.isBlank() || prompt.isBlank()) {
            Timber.w("GenerateWorkoutProgramUseCase: validation failed for blank userId or prompt")
            return liftrixFailure(validationError("request", listOf("User ID and prompt are required")))
        }

        Timber.d("GenerateWorkoutProgramUseCase: loading profile")
        val profile = profileQueryUseCase.getById(userId).getOrElse { return Result.failure(it) }
        val request = buildRequest(userId, prompt, language, profile, saveAfterGeneration)
        Timber.i(
            "GenerateWorkoutProgramUseCase: prompt built days=${request.normalizedConstraints.daysPerWeek} equipment=${request.normalizedConstraints.allowedEquipment} goal=${request.normalizedConstraints.goal} level=${request.normalizedConstraints.level}"
        )
        Timber.d("GenerateWorkoutProgramUseCase: loading exercise catalog")
        val catalog = exerciseQueryUseCase.invoke().getOrElse { return Result.failure(it) }
            .filter { it.equipment in request.normalizedConstraints.allowedEquipment }
        Timber.i("GenerateWorkoutProgramUseCase: compatible catalog size=${catalog.size}")

        if (catalog.isEmpty()) {
            Timber.w("GenerateWorkoutProgramUseCase: no compatible exercises for equipment=${request.normalizedConstraints.allowedEquipment}")
            return liftrixFailure(
                LiftrixError.BusinessLogicError(
                    code = "AI_WORKOUT_GENERATION_UNSATISFIABLE",
                    errorMessage = "No compatible exercises found for the requested equipment",
                    isRecoverable = true,
                    analyticsContext = mapOf("operation" to "GENERATE_WORKOUT_PROGRAM")
                )
            )
        }

        val generationPrompt = promptBuilder.build(request, catalog)
        Timber.i("GenerateWorkoutProgramUseCase: prompt payload ready catalogHash=${generationPrompt.catalogHash} payloadChars=${generationPrompt.inputPayload.length}")
        val cacheKey = cache.keyFor(
            userId = userId,
            normalizedPrompt = prompt.trim().lowercase(),
            constraints = request.normalizedConstraints.toString(),
            language = language.code,
            catalogHash = generationPrompt.catalogHash
        )
        if (!saveAfterGeneration) {
            cache.get(cacheKey)?.let {
                Timber.i("GenerateWorkoutProgramUseCase: cache hit for preview")
                return Result.success(it.copy(cacheHit = true))
            }
        }

        Timber.i("GenerateWorkoutProgramUseCase: Firebase JSON generation started")
        val generatedJson = generationService.generateProgramJson(
            userId = userId,
            userPrompt = prompt,
            systemPrompt = generationPrompt.systemPrompt,
            inputPayload = generationPrompt.inputPayload,
            language = language
        ).getOrElse { return Result.failure(it) }
        Timber.i("GenerateWorkoutProgramUseCase: Firebase JSON generation succeeded chars=${generatedJson.json.length} tokens=${generatedJson.tokensUsed}")

        val parsed = parseOrRepair(
            userId = userId,
            request = request,
            catalog = catalog,
            rawJson = generatedJson.json,
            tokensUsed = generatedJson.tokensUsed,
            processingTimeMs = generatedJson.processingTimeMs,
            modelVersion = generatedJson.modelVersion,
            generationPrompt = generationPrompt,
            language = language
        ).getOrElse { return Result.failure(it) }
        Timber.i("GenerateWorkoutProgramUseCase: parsing and validation succeeded days=${parsed.program.days.size} repairAttempts=${parsed.repairAttempts}")

        val savedTemplates = if (saveAfterGeneration) {
            Timber.i("GenerateWorkoutProgramUseCase: repository save started")
            saveProgram(request, parsed.program).getOrElse { return Result.failure(it) }
        } else {
            emptyList()
        }
        if (saveAfterGeneration) {
            Timber.i("GenerateWorkoutProgramUseCase: repository save succeeded templates=${savedTemplates.size}")
        }

        val result = WorkoutGenerationResult(
            program = parsed.program,
            validationWarnings = parsed.warnings,
            savedTemplates = savedTemplates,
            repairAttempts = parsed.repairAttempts,
            tokensUsed = parsed.tokensUsed,
            processingTimeMs = parsed.processingTimeMs,
            modelVersion = parsed.modelVersion
        )
        if (!saveAfterGeneration) {
            cache.put(cacheKey, result)
            Timber.d("GenerateWorkoutProgramUseCase: preview cached")
        }
        Timber.i("GenerateWorkoutProgramUseCase: final Result returned success savedTemplates=${result.savedTemplates.size}")
        return Result.success(result)
    }

    suspend fun saveGeneratedProgram(
        userId: String,
        program: GeneratedWorkoutProgram
    ): LiftrixResult<WorkoutGenerationResult> {
        Timber.i("GenerateWorkoutProgramUseCase: save generated program started user=$userId days=${program.days.size}")
        val request = WorkoutGenerationRequest(
            userId = userId,
            userPrompt = "save generated program",
            normalizedConstraints = WorkoutGenerationConstraints(daysPerWeek = program.days.size)
        )
        val templates = saveProgram(request, program).getOrElse { return Result.failure(it) }
        Timber.i("GenerateWorkoutProgramUseCase: save generated program succeeded templates=${templates.size}")
        return Result.success(WorkoutGenerationResult(program = program, savedTemplates = templates))
    }

    private suspend fun parseOrRepair(
        userId: String,
        request: WorkoutGenerationRequest,
        catalog: List<ExerciseLibrary>,
        rawJson: String,
        tokensUsed: Int,
        processingTimeMs: Long,
        modelVersion: String,
        generationPrompt: com.example.liftrix.domain.model.ai.WorkoutGenerationPrompt,
        language: Language
    ): LiftrixResult<ParsedProgram> {
        Timber.i("GenerateWorkoutProgramUseCase: parsing started rawChars=${rawJson.length}")
        parseProgram(rawJson, catalog).fold(
            onSuccess = { program ->
                Timber.i("GenerateWorkoutProgramUseCase: parsing succeeded days=${program.days.size}")
                Timber.i("GenerateWorkoutProgramUseCase: validation started")
                val validation = validator(program, request, catalog)
                if (validation.isSuccess) {
                    val valid = validation.getOrThrow()
                    Timber.i("GenerateWorkoutProgramUseCase: validation succeeded warnings=${valid.warnings.size}")
                    return Result.success(
                        ParsedProgram(
                            program = valid.program,
                            warnings = valid.warnings,
                            repairAttempts = 0,
                            tokensUsed = tokensUsed,
                            processingTimeMs = processingTimeMs,
                            modelVersion = modelVersion
                        )
                    )
                }
                Timber.w("GenerateWorkoutProgramUseCase: validation failed, repair started: ${validation.exceptionOrNull()?.message}")
                return repairInvalidProgram(
                    userId, request, catalog, rawJson, generationPrompt,
                    tokensUsed, processingTimeMs, modelVersion,
                    validation.exceptionOrNull()?.message
                        ?: "Generated workout program failed validation",
                    language
                )
            },
            onFailure = { parseError ->
                Timber.w("GenerateWorkoutProgramUseCase: parsing failed, repair started: ${parseError.message}")
                return repairInvalidProgram(
                    userId, request, catalog, rawJson, generationPrompt,
                    tokensUsed, processingTimeMs, modelVersion,
                    "Fix schema and JSON syntax. Parser error: ${parseError.message}", language
                )
            }
        )
    }

    private suspend fun repairInvalidProgram(
        userId: String,
        request: WorkoutGenerationRequest,
        catalog: List<ExerciseLibrary>,
        invalidJson: String,
        generationPrompt: com.example.liftrix.domain.model.ai.WorkoutGenerationPrompt,
        originalTokensUsed: Int,
        originalProcessingTimeMs: Long,
        originalModelVersion: String,
        repairInstruction: String,
        language: Language
    ): LiftrixResult<ParsedProgram> {
        Timber.i("GenerateWorkoutProgramUseCase: repair request started instruction=${repairInstruction.take(160)}")
        val repairedJson = generationService.repairProgramJson(
            userId = userId,
            userPrompt = request.userPrompt,
            systemPrompt = generationPrompt.systemPrompt,
            inputPayload = generationPrompt.inputPayload,
            invalidJson = invalidJson,
            repairInstruction = repairInstruction,
            language = language
        ).getOrElse { error ->
            Timber.w(error, "GenerateWorkoutProgramUseCase: AI repair failed, local repair started")
            return repairLocally(
                request = request,
                catalog = catalog,
                sourceJson = invalidJson,
                originalTokensUsed = originalTokensUsed,
                originalProcessingTimeMs = originalProcessingTimeMs,
                modelVersion = originalModelVersion,
                repairAttempts = 1
            )
        }
        Timber.i("GenerateWorkoutProgramUseCase: repair JSON received chars=${repairedJson.json.length}")

        val repairedProgram = parseProgram(repairedJson.json, catalog).getOrElse {
            Timber.w(it, "GenerateWorkoutProgramUseCase: repair parsing failed, local repair started")
            return repairLocally(
                request = request,
                catalog = catalog,
                sourceJson = invalidJson,
                originalTokensUsed = originalTokensUsed + repairedJson.tokensUsed,
                originalProcessingTimeMs = originalProcessingTimeMs + repairedJson.processingTimeMs,
                modelVersion = repairedJson.modelVersion.ifBlank { originalModelVersion },
                repairAttempts = 2
            )
        }
        val validation = validator(repairedProgram, request, catalog).getOrElse {
            Timber.w(it, "GenerateWorkoutProgramUseCase: repair validation failed errors=${it.message}")
            return repairLocally(
                request = request,
                catalog = catalog,
                sourceProgram = repairedProgram,
                sourceJson = invalidJson,
                originalTokensUsed = originalTokensUsed + repairedJson.tokensUsed,
                originalProcessingTimeMs = originalProcessingTimeMs + repairedJson.processingTimeMs,
                modelVersion = repairedJson.modelVersion.ifBlank { originalModelVersion },
                repairAttempts = 2
            )
        }
        Timber.i("GenerateWorkoutProgramUseCase: repair parsing and validation succeeded")
        return Result.success(
            ParsedProgram(
                program = validation.program,
                warnings = validation.warnings,
                repairAttempts = 1,
                tokensUsed = originalTokensUsed + repairedJson.tokensUsed,
                processingTimeMs = originalProcessingTimeMs + repairedJson.processingTimeMs,
                modelVersion = repairedJson.modelVersion.ifBlank { originalModelVersion }
            )
        )
    }

    private fun repairLocally(
        request: WorkoutGenerationRequest,
        catalog: List<ExerciseLibrary>,
        sourceProgram: GeneratedWorkoutProgram? = null,
        sourceJson: String,
        originalTokensUsed: Int,
        originalProcessingTimeMs: Long,
        modelVersion: String,
        repairAttempts: Int
    ): LiftrixResult<ParsedProgram> {
        val program = sourceProgram ?: parseProgram(sourceJson, catalog).getOrElse {
            Timber.w(it, "GenerateWorkoutProgramUseCase: local repair cannot parse source JSON")
            return invalidGenerationFailure("parse")
        }
        val locallyRepaired = locallyRepairProgram(program, request, catalog)
        val validation = validator(locallyRepaired, request, catalog).getOrElse {
            Timber.w(it, "GenerateWorkoutProgramUseCase: local repair validation failed errors=${it.message}")
            return invalidGenerationFailure("validation")
        }
        Timber.i("GenerateWorkoutProgramUseCase: local repair validation succeeded warnings=${validation.warnings.size}")
        return Result.success(
            ParsedProgram(
                program = validation.program,
                warnings = validation.warnings,
                repairAttempts = repairAttempts,
                tokensUsed = originalTokensUsed,
                processingTimeMs = originalProcessingTimeMs,
                modelVersion = modelVersion
            )
        )
    }

    private fun locallyRepairProgram(
        program: GeneratedWorkoutProgram,
        request: WorkoutGenerationRequest,
        catalog: List<ExerciseLibrary>
    ): GeneratedWorkoutProgram {
        val catalogById = catalog.associateBy { it.id }
        val compatibleCatalog = catalog
            .filter { it.equipment in request.normalizedConstraints.allowedEquipment }
            .filter { it.equipment !in request.normalizedConstraints.excludedEquipment }
            .filter { it.difficultyLevel <= maxDifficultyFor(request.normalizedConstraints.level) }

        return program.copy(
            level = request.normalizedConstraints.level,
            goal = request.normalizedConstraints.goal,
            days = program.days.map { day ->
                day.copy(
                    exercises = day.exercises.map { exercise ->
                        val catalogExercise = catalogById[exercise.exerciseId]
                        val shouldReplace = catalogExercise == null ||
                            catalogExercise.difficultyLevel > maxDifficultyFor(request.normalizedConstraints.level) ||
                            catalogExercise.equipment !in request.normalizedConstraints.allowedEquipment ||
                            catalogExercise.equipment in request.normalizedConstraints.excludedEquipment

                        val replacement = if (shouldReplace) {
                            chooseReplacementExercise(exercise, compatibleCatalog)
                        } else {
                            catalogExercise
                        }
                        repairExercise(exercise, replacement, request)
                    }
                )
            }
        )
    }

    private fun repairExercise(
        exercise: GeneratedWorkoutExercise,
        catalogExercise: ExerciseLibrary?,
        request: WorkoutGenerationRequest
    ): GeneratedWorkoutExercise {
        val beginner = request.normalizedConstraints.level == WorkoutProgramLevel.BEGINNER
        val repairedPrescription = if (beginner && exercise.type == GeneratedPrescriptionType.REPS) {
            clampBeginnerReps(exercise.repsMin, exercise.repsMax)
        } else {
            exercise.repsMin to exercise.repsMax
        }

        return exercise.copy(
            exerciseId = catalogExercise?.id ?: exercise.exerciseId,
            exerciseName = catalogExercise?.name ?: exercise.exerciseName,
            primaryMuscle = catalogExercise?.primaryMuscleGroup ?: exercise.primaryMuscle,
            equipment = catalogExercise?.equipment ?: exercise.equipment,
            sets = if (beginner) exercise.sets.coerceIn(1, 3) else exercise.sets.coerceIn(1, 5),
            repsMin = repairedPrescription.first,
            repsMax = repairedPrescription.second,
            restSeconds = exercise.restSeconds.coerceIn(30, 180)
        )
    }

    private fun clampBeginnerReps(repsMin: Int?, repsMax: Int?): Pair<Int?, Int?> {
        val min = repsMin ?: repsMax ?: 8
        val max = repsMax ?: min
        val clampedMin = min.coerceIn(8, 15)
        val clampedMax = max.coerceIn(8, 15).coerceAtLeast(clampedMin)
        return clampedMin to clampedMax
    }

    private fun chooseReplacementExercise(
        exercise: GeneratedWorkoutExercise,
        compatibleCatalog: List<ExerciseLibrary>
    ): ExerciseLibrary? {
        if (compatibleCatalog.isEmpty()) return null
        return compatibleCatalog
            .sortedWith(
                compareBy<ExerciseLibrary> { if (it.primaryMuscleGroup == exercise.primaryMuscle) 0 else 1 }
                    .thenBy { it.difficultyLevel }
                    .thenByDescending { it.isCompound }
                    .thenBy { it.name }
            )
            .firstOrNull()
    }

    private fun maxDifficultyFor(level: WorkoutProgramLevel): Int = when (level) {
        WorkoutProgramLevel.BEGINNER -> 3
        WorkoutProgramLevel.INTERMEDIATE -> 6
        WorkoutProgramLevel.ADVANCED -> 10
    }

    private fun parseProgram(
        rawJson: String,
        catalog: List<ExerciseLibrary>
    ): LiftrixResult<GeneratedWorkoutProgram> {
        val normalizedJson = normalizeJsonPayload(rawJson)
        Timber.d(
            "GenerateWorkoutProgramUseCase: normalized JSON ready rawChars=${rawJson.length} normalizedChars=${normalizedJson.length}"
        )
        return try {
            if (normalizedJson.contains("\"error\"")) {
                val error = json.decodeFromString<GeneratedWorkoutProgramError>(normalizedJson)
                liftrixFailure(
                    LiftrixError.BusinessLogicError(
                        code = error.error.code.name,
                        errorMessage = error.error.message,
                        isRecoverable = true
                    )
                )
            } else {
                val response = json.decodeFromString<AiWorkoutResponseDto>(normalizedJson)
                val programDto = response.program ?: json.decodeFromString<AiWorkoutProgramDto>(normalizedJson)
                val program = programDto.toGeneratedProgram(
                    schemaVersion = response.schemaVersion ?: GeneratedWorkoutProgram.SCHEMA_VERSION,
                    catalog = catalog
                )
                if (response.program != null) {
                    Timber.i("GenerateWorkoutProgramUseCase: wrapped AI program DTO parsed successfully")
                } else {
                    Timber.i("GenerateWorkoutProgramUseCase: direct AI program DTO parsed successfully")
                }
                Result.success(program)
            }
        } catch (exception: SerializationException) {
            Timber.w(exception, "GenerateWorkoutProgramUseCase: JSON parse failed")
            liftrixFailure(
                LiftrixError.BusinessLogicError(
                    code = "AI_WORKOUT_GENERATION_PARSE_FAILED",
                    errorMessage = exception.message ?: "Failed to parse generated workout program",
                    isRecoverable = true
                )
            )
        } catch (exception: IllegalArgumentException) {
            Timber.w(exception, "GenerateWorkoutProgramUseCase: AI program DTO mapping failed")
            liftrixFailure(
                LiftrixError.BusinessLogicError(
                    code = "AI_WORKOUT_GENERATION_PARSE_FAILED",
                    errorMessage = exception.message ?: "Failed to map generated workout program",
                    isRecoverable = true
                )
            )
        }
    }

    private fun AiWorkoutProgramDto.toGeneratedProgram(
        schemaVersion: String,
        catalog: List<ExerciseLibrary>
    ): GeneratedWorkoutProgram {
        val catalogById = catalog.associateBy { it.id }
        val resolvedName = workoutName?.takeIf { it.isNotBlank() }
            ?: programName?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Generated workout program is missing workout_name/program_name")
        if (days.isEmpty()) throw IllegalArgumentException("Generated workout program is missing days")

        return GeneratedWorkoutProgram(
            schemaVersion = schemaVersion,
            workoutName = resolvedName,
            goal = goal.toProgramGoalOrDefault(),
            level = level.toProgramLevelOrDefault(),
            days = days.mapIndexed { dayIndex, day ->
                day.toGeneratedDay(dayIndex, catalogById)
            }
        )
    }

    private fun AiWorkoutDayDto.toGeneratedDay(
        index: Int,
        catalogById: Map<String, ExerciseLibrary>
    ): GeneratedWorkoutDay {
        val name = dayName?.takeIf { it.isNotBlank() }
            ?: dayNumber?.let { "Day $it" }
            ?: "Day ${index + 1}"
        if (exercises.isEmpty()) throw IllegalArgumentException("$name is missing exercises")

        return GeneratedWorkoutDay(
            dayName = name,
            estimatedDurationMinutes = estimatedDurationMinutes ?: 45,
            exercises = exercises.mapIndexed { exerciseIndex, exercise ->
                exercise.toGeneratedExercise(
                    path = "days[$index].exercises[$exerciseIndex]",
                    catalogById = catalogById
                )
            }
        )
    }

    private fun AiWorkoutExerciseDto.toGeneratedExercise(
        path: String,
        catalogById: Map<String, ExerciseLibrary>
    ): GeneratedWorkoutExercise {
        val id = exerciseId?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("$path.exercise_id is required")
        val catalogExercise = catalogById[id]
        val resolvedType = inferPrescriptionType()

        return GeneratedWorkoutExercise(
            exerciseId = id,
            exerciseName = exerciseName?.takeIf { it.isNotBlank() }
                ?: catalogExercise?.name
                ?: throw IllegalArgumentException("$path.exercise_name is required"),
            primaryMuscle = primaryMuscle.toExerciseCategoryOrNull()
                ?: catalogExercise?.primaryMuscleGroup
                ?: ExerciseCategory.OTHER,
            equipment = equipment.toEquipmentOrNull()
                ?: catalogExercise?.equipment
                ?: Equipment.BODYWEIGHT_ONLY,
            sets = sets?.coerceIn(1, 5) ?: 1,
            type = resolvedType,
            repsMin = if (resolvedType == GeneratedPrescriptionType.REPS) repsMin else null,
            repsMax = if (resolvedType == GeneratedPrescriptionType.REPS) repsMax ?: repsMin else null,
            durationSeconds = if (resolvedType == GeneratedPrescriptionType.TIME) durationSeconds else null,
            isUnilateral = isUnilateral ?: false,
            restSeconds = restSeconds?.coerceIn(30, 180) ?: 60,
            notes = notes
        )
    }

    private fun AiWorkoutExerciseDto.inferPrescriptionType(): GeneratedPrescriptionType =
        when {
            durationSeconds != null -> GeneratedPrescriptionType.TIME
            repsMin != null || repsMax != null -> GeneratedPrescriptionType.REPS
            exerciseType.equals("cardio", ignoreCase = true) -> GeneratedPrescriptionType.TIME
            type.equals("cardio", ignoreCase = true) -> GeneratedPrescriptionType.TIME
            else -> GeneratedPrescriptionType.REPS
        }

    private fun String?.toProgramGoalOrDefault(): WorkoutProgramGoal =
        WorkoutProgramGoal.entries.firstOrNull { enum ->
            equals(enum.name, ignoreCase = true) ||
                equals(enum.name.lowercase(), ignoreCase = true) ||
                equals(enum.name.lowercase().replace('_', '-'), ignoreCase = true)
        } ?: WorkoutProgramGoal.GENERAL_FITNESS

    private fun String?.toProgramLevelOrDefault(): WorkoutProgramLevel =
        WorkoutProgramLevel.entries.firstOrNull { enum ->
            equals(enum.name, ignoreCase = true) ||
                equals(enum.name.lowercase(), ignoreCase = true)
        } ?: WorkoutProgramLevel.BEGINNER

    private fun String?.toExerciseCategoryOrNull(): ExerciseCategory? =
        ExerciseCategory.entries.firstOrNull { enum ->
            equals(enum.name, ignoreCase = true) ||
                equals(enum.name.lowercase(), ignoreCase = true) ||
                equals(enum.displayName, ignoreCase = true)
        }

    private fun String?.toEquipmentOrNull(): Equipment? =
        Equipment.entries.firstOrNull { enum ->
            val normalized = this?.replace('-', '_')?.replace(' ', '_')
            normalized.equals(enum.name, ignoreCase = true) ||
                equals(enum.displayName, ignoreCase = true)
        }

    private fun normalizeJsonPayload(rawJson: String): String {
        val trimmed = rawJson.trim()
        val fenced = Regex("""(?s)```\s*(?:json|JSON)?\s*(.*?)\s*```""")
            .find(trimmed)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
        if (fenced != null) return fenced
        val first = trimmed.indexOf('{')
        val last = trimmed.lastIndexOf('}')
        return if (first >= 0 && last > first) trimmed.substring(first, last + 1).trim() else trimmed
    }

    private suspend fun saveProgram(
        request: WorkoutGenerationRequest,
        program: GeneratedWorkoutProgram
    ): LiftrixResult<List<com.example.liftrix.domain.model.WorkoutTemplate>> {
        val saved = mutableListOf<com.example.liftrix.domain.model.WorkoutTemplate>()
        program.days.forEach { day ->
            Timber.i("GenerateWorkoutProgramUseCase: saving template day=${day.dayName} exercises=${day.exercises.size}")
            val result = templateCommandUseCase.create(
                userId = request.userId,
                name = "${program.workoutName} - ${day.dayName}".take(100),
                folderId = null,
                description = "Generated by Liftrix AI for ${program.goal.name.lowercase().replace('_', ' ')}.",
                exercises = day.exercises.mapIndexed { index, exercise ->
                    TemplateExercise(
                        exerciseId = ExerciseId.fromString(exercise.exerciseId),
                        name = exercise.exerciseName,
                        primaryMuscle = exercise.primaryMuscle,
                        equipment = exercise.equipment,
                        targetSets = exercise.sets,
                        targetReps = if (exercise.type == GeneratedPrescriptionType.REPS) {
                            Reps((exercise.repsMax ?: exercise.repsMin ?: 1).coerceAtLeast(1))
                        } else {
                            Reps(1)
                        },
                        restTimeSeconds = exercise.restSeconds,
                        notes = buildTemplateNotes(exercise),
                        orderIndex = index
                    )
                },
                estimatedDurationMinutes = day.estimatedDurationMinutes,
                difficultyLevel = when (program.level) {
                    WorkoutProgramLevel.BEGINNER -> 2
                    WorkoutProgramLevel.INTERMEDIATE -> 5
                    WorkoutProgramLevel.ADVANCED -> 8
                }
            )
            saved.add(result.getOrElse {
                Timber.e(it, "GenerateWorkoutProgramUseCase: template save failed day=${day.dayName}")
                return Result.failure(it)
            })
            Timber.i("GenerateWorkoutProgramUseCase: template save succeeded day=${day.dayName} id=${saved.last().id.value}")
        }
        return Result.success(saved)
    }

    private fun buildTemplateNotes(exercise: com.example.liftrix.domain.model.ai.GeneratedWorkoutExercise): String? {
        val prescription = when (exercise.type) {
            GeneratedPrescriptionType.REPS -> "${exercise.repsMin}-${exercise.repsMax} reps"
            GeneratedPrescriptionType.TIME -> "${exercise.durationSeconds} seconds"
        }
        val unilateral = if (exercise.isUnilateral) " each side" else ""
        return listOfNotNull("$prescription$unilateral", exercise.notes)
            .joinToString(" - ")
            .take(TemplateExercise.MAX_NOTES_LENGTH)
    }

    private fun buildRequest(
        userId: String,
        prompt: String,
        language: Language,
        profile: UserProfile?,
        saveAfterGeneration: Boolean
    ): WorkoutGenerationRequest {
        val allowedEquipment = extractEquipment(prompt)
            .ifEmpty { profile?.availableEquipment?.toSet().orEmpty() }
            .ifEmpty { setOf(Equipment.BODYWEIGHT_ONLY) }
        val goal = extractGoal(prompt) ?: profile?.fitnessGoals?.firstOrNull()?.toProgramGoal() ?: WorkoutProgramGoal.GENERAL_FITNESS
        val level = extractLevel(prompt)

        return WorkoutGenerationRequest(
            userId = userId,
            userPrompt = prompt,
            normalizedConstraints = WorkoutGenerationConstraints(
                daysPerWeek = extractDays(prompt),
                level = level,
                goal = goal,
                allowedEquipment = allowedEquipment,
                excludedEquipment = Equipment.entries.toSet() - allowedEquipment,
                sessionDurationMinutes = extractDuration(prompt),
                language = language
            ),
            personalization = WorkoutGenerationPersonalization(
                ageBand = ageBand(profile?.age),
                profileGoals = profile?.fitnessGoals.orEmpty(),
                profileEquipment = profile?.availableEquipment?.toSet().orEmpty(),
                experienceLevel = level
            ),
            saveAfterGeneration = saveAfterGeneration
        )
    }

    private fun extractDays(prompt: String): Int {
        val match = Regex("""(\d+)\s*[- ]?\s*day""").find(prompt.lowercase())
        return match?.groupValues?.getOrNull(1)?.toIntOrNull()?.coerceIn(1, 6) ?: 3
    }

    private fun extractDuration(prompt: String): Int {
        val match = Regex("""(\d+)\s*(minute|min)""").find(prompt.lowercase())
        return match?.groupValues?.getOrNull(1)?.toIntOrNull()?.coerceIn(5, 90) ?: 45
    }

    private fun extractLevel(prompt: String): WorkoutProgramLevel {
        val lower = prompt.lowercase()
        return when {
            "advanced" in lower -> WorkoutProgramLevel.ADVANCED
            "intermediate" in lower -> WorkoutProgramLevel.INTERMEDIATE
            else -> WorkoutProgramLevel.BEGINNER
        }
    }

    private fun extractGoal(prompt: String): WorkoutProgramGoal? {
        val lower = prompt.lowercase()
        return when {
            "fat loss" in lower || "lose weight" in lower -> WorkoutProgramGoal.FAT_LOSS
            "hypertrophy" in lower || "muscle" in lower -> WorkoutProgramGoal.HYPERTROPHY
            "strength" in lower -> WorkoutProgramGoal.STRENGTH
            "endurance" in lower || "cardio" in lower -> WorkoutProgramGoal.ENDURANCE
            else -> null
        }
    }

    private fun extractEquipment(prompt: String): Set<Equipment> {
        val lower = prompt.lowercase()
        return buildSet {
            if ("dumbbell" in lower) add(Equipment.DUMBBELLS)
            if ("barbell" in lower) add(Equipment.BARBELL)
            if ("band" in lower) add(Equipment.RESISTANCE_BANDS)
            if ("kettlebell" in lower) add(Equipment.KETTLEBELLS)
            if ("pull-up" in lower || "pull up" in lower) add(Equipment.PULL_UP_BAR)
            if ("bench" in lower) add(Equipment.BENCH)
            if ("cable" in lower) add(Equipment.CABLE_MACHINE)
            if ("treadmill" in lower) add(Equipment.TREADMILL)
            if ("bike" in lower) add(Equipment.EXERCISE_BIKE)
            if ("bodyweight" in lower || "home" in lower) add(Equipment.BODYWEIGHT_ONLY)
        }
    }

    private fun FitnessGoal.toProgramGoal(): WorkoutProgramGoal = when (this) {
        FitnessGoal.LOSE_WEIGHT -> WorkoutProgramGoal.FAT_LOSS
        FitnessGoal.BUILD_MUSCLE -> WorkoutProgramGoal.HYPERTROPHY
        FitnessGoal.IMPROVE_ENDURANCE -> WorkoutProgramGoal.ENDURANCE
        FitnessGoal.INCREASE_STRENGTH -> WorkoutProgramGoal.STRENGTH
        else -> WorkoutProgramGoal.GENERAL_FITNESS
    }

    private fun ageBand(age: Int?): String = when (age) {
        null -> "adult"
        in 13..17 -> "teen"
        in 18..64 -> "adult"
        else -> "older_adult"
    }

    private fun validationError(field: String, violations: List<String>) =
        LiftrixError.ValidationError(field = field, violations = violations)

    private fun invalidGenerationFailure(stage: String): LiftrixResult<ParsedProgram> =
        liftrixFailure(
            LiftrixError.BusinessLogicError(
                code = "AI_WORKOUT_GENERATION_INVALID",
                errorMessage = "Generated workout program failed $stage after repair.",
                isRecoverable = true,
                analyticsContext = mapOf("stage" to stage)
            )
        )

    @Serializable
    private data class AiWorkoutResponseDto(
        @SerialName("schema_version")
        val schemaVersion: String? = null,
        val program: AiWorkoutProgramDto? = null
    )

    @Serializable
    private data class AiWorkoutProgramDto(
        @SerialName("workout_name")
        val workoutName: String? = null,
        @SerialName("program_name")
        val programName: String? = null,
        val goal: String? = null,
        val level: String? = null,
        val days: List<AiWorkoutDayDto> = emptyList()
    )

    @Serializable
    private data class AiWorkoutDayDto(
        @SerialName("day_name")
        val dayName: String? = null,
        @SerialName("day_number")
        val dayNumber: Int? = null,
        @SerialName("estimated_duration_minutes")
        val estimatedDurationMinutes: Int? = null,
        val exercises: List<AiWorkoutExerciseDto> = emptyList()
    )

    @Serializable
    private data class AiWorkoutExerciseDto(
        @SerialName("exercise_id")
        val exerciseId: String? = null,
        @SerialName("exercise_name")
        val exerciseName: String? = null,
        @SerialName("primary_muscle")
        val primaryMuscle: String? = null,
        val equipment: String? = null,
        val sets: Int? = null,
        val type: String? = null,
        @SerialName("exercise_type")
        val exerciseType: String? = null,
        @SerialName("reps_min")
        val repsMin: Int? = null,
        @SerialName("reps_max")
        val repsMax: Int? = null,
        @SerialName("duration_seconds")
        val durationSeconds: Int? = null,
        @SerialName("is_unilateral")
        val isUnilateral: Boolean? = null,
        @SerialName("rest_seconds")
        val restSeconds: Int? = null,
        val notes: String? = null
    )

    private data class ParsedProgram(
        val program: GeneratedWorkoutProgram,
        val warnings: List<String>,
        val repairAttempts: Int,
        val tokensUsed: Int,
        val processingTimeMs: Long,
        val modelVersion: String
    )
}
