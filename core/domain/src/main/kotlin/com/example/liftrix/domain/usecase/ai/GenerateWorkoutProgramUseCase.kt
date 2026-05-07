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
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.jsonObject
import com.example.liftrix.domain.util.DomainLogger as Timber

class GenerateWorkoutProgramUseCase @Inject constructor(
    private val profileQueryUseCase: ProfileQueryUseCase,
    private val exerciseQueryUseCase: ExerciseQueryUseCase,
    private val templateCommandUseCase: TemplateCommandUseCase,
    private val generationService: WorkoutProgramGenerationService,
    private val promptBuilder: WorkoutGenerationPromptBuilder,
    private val validator: ValidateGeneratedWorkoutProgramUseCase,
    private val cache: WorkoutGenerationCache
) {

    private val workoutJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        coerceInputValues = true
    }

    private val fullGymEquipment = Equipment.entries.toSet() - Equipment.BODYWEIGHT_ONLY
    private val weeklyCountPatterns = listOf(
        Regex("""\b([1-6])\s*[- ]?\s*days?\b"""),
        Regex("""\b([1-6])\s*(?:x|times)\s*(?:/|per)?\s*(?:week|weekly)\b"""),
        Regex("""\b([1-6])\s*(?:workouts?|sessions?)\s*(?:a|per)?\s*week\b""")
    )
    private val wordNumbers = mapOf(
        "one" to 1,
        "two" to 2,
        "three" to 3,
        "four" to 4,
        "five" to 5,
        "six" to 6
    )
    private val noEquipmentTerms = listOf(
        "bodyweight only",
        "body weight only",
        "no equipment",
        "without equipment",
        "equipment free",
        "no weights",
        "without weights",
        "no gym equipment"
    )
    private val bodyweightTerms = listOf("bodyweight", "body weight", "calisthenics")
    private val fullGymTerms = listOf(
        "full gym",
        "commercial gym",
        "gym equipment",
        "at the gym",
        "in the gym",
        "gym workout",
        "gym program",
        "gym routine",
        "machine workout",
        "machines",
        "weight room"
    )
    private val homeBodyweightTerms = listOf("home workout", "home training", "at home", "train at home")
    private val dumbbellTerms = listOf("dumbbell", "dumbbells", "db only", "dbs")
    private val barbellTerms = listOf("barbell", "barbells", "squat rack", "rack", "plates", "bench press")
    private val bandTerms = listOf("resistance band", "resistance bands", "bands", "banded")
    private val kettlebellTerms = listOf("kettlebell", "kettlebells")
    private val pullUpTerms = listOf("pull-up", "pull up", "pullup", "chin-up", "chin up", "chinup")
    private val benchTerms = listOf("bench", "incline bench", "flat bench")
    private val cableTerms = listOf("cable", "cables", "cable machine", "pulley", "lat pulldown", "machine")
    private val treadmillTerms = listOf("treadmill", "running machine")
    private val bikeTerms = listOf("exercise bike", "stationary bike", "bike", "cycling")
    private val singleSessionTerms = listOf(
        "single workout",
        "one workout",
        "1 workout",
        "single session",
        "one session",
        "1 session",
        "today workout",
        "today's workout",
        "quick workout"
    )
    private val programShapeTerms = listOf("program", "routine", "plan", "split", "weekly", "per week", "/week")

    suspend operator fun invoke(
        userId: String,
        prompt: String,
        language: Language = Language.ENGLISH,
        saveAfterGeneration: Boolean = false
    ): LiftrixResult<WorkoutGenerationResult> {
        Timber.i("[AI] GenerateWorkoutProgramUseCase: request started user=$userId promptChars=${prompt.length} language=${language.code} saveAfterGeneration=$saveAfterGeneration")
        if (userId.isBlank() || prompt.isBlank()) {
            Timber.w("[AI] GenerateWorkoutProgramUseCase: validation failed for blank userId or prompt")
            return liftrixFailure(validationError("request", listOf("User ID and prompt are required")))
        }

        Timber.d("[AI] GenerateWorkoutProgramUseCase: loading profile")
        val profile = profileQueryUseCase.getById(userId).getOrElse { return Result.failure(it) }
        val request = buildRequest(userId, prompt, language, profile, saveAfterGeneration)
        Timber.i(
            "[AI] GenerateWorkoutProgramUseCase: constraints days=${request.normalizedConstraints.daysPerWeek} duration=${request.normalizedConstraints.sessionDurationMinutes} equipment=${request.normalizedConstraints.allowedEquipment} excluded=${request.normalizedConstraints.excludedEquipment} goal=${request.normalizedConstraints.goal} level=${request.normalizedConstraints.level}"
        )
        Timber.d("[AI] GenerateWorkoutProgramUseCase: loading exercise catalog")
        val rawCatalog = exerciseQueryUseCase.invoke().getOrElse { return Result.failure(it) }
        val catalog = rawCatalog.filter { it.equipment in request.normalizedConstraints.allowedEquipment }
        Timber.i(
            "[AI] GenerateWorkoutProgramUseCase: catalog raw=${rawCatalog.size} compatible=${catalog.size} compatibleEquipment=${catalog.equipmentBreakdown()}"
        )

        if (catalog.isEmpty()) {
            Timber.w("[AI] GenerateWorkoutProgramUseCase: no compatible exercises for equipment=${request.normalizedConstraints.allowedEquipment}")
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
        Timber.i("[AI] GenerateWorkoutProgramUseCase: prompt payload ready catalogHash=${generationPrompt.catalogHash} payloadChars=${generationPrompt.inputPayload.length}")
        val cacheKey = cache.keyFor(
            userId = userId,
            normalizedPrompt = prompt.trim().lowercase(),
            constraints = request.normalizedConstraints.toString(),
            language = language.code,
            catalogHash = generationPrompt.catalogHash
        )
        if (!saveAfterGeneration) {
            cache.get(cacheKey)?.let {
                Timber.i("[AI] GenerateWorkoutProgramUseCase: cache hit for preview")
                return Result.success(it.copy(cacheHit = true))
            }
        }

        Timber.i("[AI] GenerateWorkoutProgramUseCase: Firebase JSON generation started")
        val generatedJson = generationService.generateProgramJson(
            userId = userId,
            userPrompt = prompt,
            systemPrompt = generationPrompt.systemPrompt,
            inputPayload = generationPrompt.inputPayload,
            language = language
        ).getOrElse { return Result.failure(it) }
        Timber.i("[AI] GenerateWorkoutProgramUseCase: Firebase JSON generation succeeded chars=${generatedJson.json.length} tokens=${generatedJson.tokensUsed}")

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
        Timber.i("[AI] GenerateWorkoutProgramUseCase: parsing and validation succeeded days=${parsed.program.days.size} repairAttempts=${parsed.repairAttempts}")

        val savedTemplates = if (saveAfterGeneration) {
            Timber.i("[AI] GenerateWorkoutProgramUseCase: repository save started")
            saveProgram(request, parsed.program).getOrElse { return Result.failure(it) }
        } else {
            emptyList()
        }
        if (saveAfterGeneration) {
            Timber.i("[AI] GenerateWorkoutProgramUseCase: repository save succeeded templates=${savedTemplates.size}")
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
            Timber.d("[AI] GenerateWorkoutProgramUseCase: preview cached")
        }
        Timber.i("[AI] GenerateWorkoutProgramUseCase: final Result returned success savedTemplates=${result.savedTemplates.size}")
        return Result.success(result)
    }

    suspend fun saveGeneratedProgram(
        userId: String,
        program: GeneratedWorkoutProgram
    ): LiftrixResult<WorkoutGenerationResult> {
        Timber.i("[AI] GenerateWorkoutProgramUseCase: save generated program started user=$userId days=${program.days.size}")
        val request = WorkoutGenerationRequest(
            userId = userId,
            userPrompt = "save generated program",
            normalizedConstraints = WorkoutGenerationConstraints(daysPerWeek = program.days.size)
        )
        val templates = saveProgram(request, program).getOrElse { return Result.failure(it) }
        Timber.i("[AI] GenerateWorkoutProgramUseCase: save generated program succeeded templates=${templates.size}")
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
        Timber.i("[AI] GenerateWorkoutProgramUseCase: parsing started rawChars=${rawJson.length}")
        parseProgram(rawJson, catalog).fold(
            onSuccess = { program ->
                Timber.i("[AI] GenerateWorkoutProgramUseCase: parsing succeeded days=${program.days.size}")
                Timber.i("[AI] GenerateWorkoutProgramUseCase: validation started")
                val validation = validator(program, request, catalog)
                if (validation.isSuccess) {
                    val valid = validation.getOrThrow()
                    Timber.i("[AI] GenerateWorkoutProgramUseCase: validation succeeded warnings=${valid.warnings.size}")
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
                Timber.w("[AI] GenerateWorkoutProgramUseCase: validation failed, repair started: ${validation.exceptionOrNull()?.message}")
                return repairInvalidProgram(
                    userId, request, catalog, rawJson, generationPrompt,
                    tokensUsed, processingTimeMs, modelVersion,
                    validation.exceptionOrNull()?.message
                        ?: "Generated workout program failed validation",
                    language
                )
            },
            onFailure = { parseError ->
                Timber.w("[AI] GenerateWorkoutProgramUseCase: parsing failed, repair started: ${parseError.message}")
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
        Timber.i("[AI] GenerateWorkoutProgramUseCase: repair request started instruction=${repairInstruction.take(240)}")
        val repairedJson = generationService.repairProgramJson(
            userId = userId,
            userPrompt = request.userPrompt,
            systemPrompt = generationPrompt.systemPrompt,
            inputPayload = generationPrompt.inputPayload,
            invalidJson = invalidJson,
            repairInstruction = repairInstruction,
            language = language
        ).getOrElse { error ->
            Timber.w(error, "[AI] GenerateWorkoutProgramUseCase: AI repair failed; refusing fallback workout")
            return invalidGenerationFailure(
                stage = "repair",
                details = "The AI generated a workout that did not pass validation: ${repairInstruction.take(500)}. Repair failed: ${error.message ?: error.javaClass.simpleName}."
            )
        }
        Timber.i("[AI] GenerateWorkoutProgramUseCase: repair JSON received chars=${repairedJson.json.length}")

        val repairedProgram = parseProgram(repairedJson.json, catalog).getOrElse {
            Timber.w(it, "[AI] GenerateWorkoutProgramUseCase: repair parsing failed; refusing fallback workout")
            return invalidGenerationFailure(
                stage = "repair_parse",
                details = "The AI repair response was not valid workout JSON: ${it.message ?: it.javaClass.simpleName}."
            )
        }
        val validation = validator(repairedProgram, request, catalog).getOrElse {
            Timber.w(it, "[AI] GenerateWorkoutProgramUseCase: repair validation failed; refusing fallback workout")
            return invalidGenerationFailure(
                stage = "repair_validation",
                details = "The AI repair response still did not satisfy the request: ${it.message ?: it.javaClass.simpleName}."
            )
        }
        Timber.i("[AI] GenerateWorkoutProgramUseCase: repair parsing and validation succeeded")
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

    private fun parseProgram(
        rawJson: String,
        catalog: List<ExerciseLibrary>
    ): LiftrixResult<GeneratedWorkoutProgram> {
        val normalizedJson = normalizeJsonPayload(rawJson)
        Timber.d(
            "[AI] GenerateWorkoutProgramUseCase: normalized JSON ready rawChars=${rawJson.length} normalizedChars=${normalizedJson.length}"
        )
        return try {
            if (normalizedJson.contains("\"error\"")) {
                val error = workoutJson.decodeFromString<GeneratedWorkoutProgramError>(normalizedJson)
                Timber.w("[AI] GenerateWorkoutProgramUseCase: AI returned structured error code=${error.error.code} message=${error.error.message}")
                liftrixFailure(
                    LiftrixError.BusinessLogicError(
                        code = error.error.code.name,
                        errorMessage = error.error.message,
                        isRecoverable = true
                    )
                )
            } else {
                val root = workoutJson.parseToJsonElement(normalizedJson).jsonObject
                val program = if ("program" in root) {
                    val response = workoutJson.decodeFromString<AiWorkoutResponseDto>(normalizedJson)
                    Timber.i("[AI] GenerateWorkoutProgramUseCase: wrapped AI program parsed successfully")
                    response.program
                        ?.toGeneratedProgram(
                            schemaVersion = response.schemaVersion ?: GeneratedWorkoutProgram.SCHEMA_VERSION,
                            catalog = catalog
                        )
                        ?: throw IllegalArgumentException("Generated workout response is missing program")
                } else {
                    Timber.i("[AI] GenerateWorkoutProgramUseCase: direct AI program parsed successfully")
                    workoutJson.decodeFromString<AiWorkoutProgramDto>(normalizedJson)
                        .toGeneratedProgram(
                            schemaVersion = GeneratedWorkoutProgram.SCHEMA_VERSION,
                            catalog = catalog
                        )
                }
                Result.success(program)
            }
        } catch (exception: SerializationException) {
            Timber.w(exception, "[AI] GenerateWorkoutProgramUseCase: JSON parse failed")
            liftrixFailure(
                LiftrixError.BusinessLogicError(
                    code = "AI_WORKOUT_GENERATION_PARSE_FAILED",
                    errorMessage = exception.message ?: "Failed to parse generated workout program",
                    isRecoverable = true
                )
            )
        } catch (exception: IllegalArgumentException) {
            Timber.w(exception, "[AI] GenerateWorkoutProgramUseCase: AI program DTO mapping failed")
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
            Timber.i("[AI] GenerateWorkoutProgramUseCase: saving template day=${day.dayName} exercises=${day.exercises.size}")
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
                Timber.e(it, "[AI] GenerateWorkoutProgramUseCase: template save failed day=${day.dayName}")
                return Result.failure(it)
            })
            Timber.i("[AI] GenerateWorkoutProgramUseCase: template save succeeded day=${day.dayName} id=${saved.last().id.value}")
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
        val profileEquipment = profile?.availableEquipment?.toSet().orEmpty()
        val allowedEquipment = resolveAllowedEquipment(
            requestedEquipment = extractEquipment(prompt),
            profileEquipment = profileEquipment
        )
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
                profileEquipment = profileEquipment,
                experienceLevel = level
            ),
            saveAfterGeneration = saveAfterGeneration
        )
    }

    private fun resolveAllowedEquipment(
        requestedEquipment: Set<Equipment>,
        profileEquipment: Set<Equipment>
    ): Set<Equipment> = when {
        requestedEquipment.isNotEmpty() -> requestedEquipment
        profileEquipment.isNotEmpty() -> profileEquipment
        else -> Equipment.entries.toSet()
    }

    private fun extractDays(prompt: String): Int {
        val lower = prompt.normalizedForParsing()
        extractExplicitWeeklyCount(lower)?.let { return it }

        return when {
            hasAny(lower, listOf("arnold split")) -> 6
            hasAny(lower, listOf("bro split")) -> 5
            hasAny(lower, listOf("upper lower", "upper/lower")) -> 4
            hasPplSplit(lower) -> 3
            asksForSingleSession(lower) -> 1
            hasAny(lower, listOf("full body", "full-body")) -> 3
            else -> 3
        }
    }

    private fun extractDuration(prompt: String): Int {
        val lower = prompt.lowercase()
        val numericMatch = Regex("""\b(\d{1,3})\s*(?:-| )?\s*(?:minute|minutes|min|mins|m)\b""")
            .find(lower)
        if (numericMatch != null) {
            return numericMatch.groupValues[1].toIntOrNull()?.coerceIn(5, 90) ?: 45
        }

        return when {
            Regex("""\b(one|1)\s+hour\b""").containsMatchIn(lower) -> 60
            Regex("""\bhalf\s+hour\b""").containsMatchIn(lower) -> 30
            else -> 45
        }
    }

    private fun extractLevel(prompt: String): WorkoutProgramLevel {
        val lower = prompt.lowercase()
        return when {
            "advanced" in lower -> WorkoutProgramLevel.ADVANCED
            "intermediate" in lower || "experienced" in lower -> WorkoutProgramLevel.INTERMEDIATE
            else -> WorkoutProgramLevel.BEGINNER
        }
    }

    private fun extractGoal(prompt: String): WorkoutProgramGoal? {
        val lower = prompt.lowercase()
        return when {
            "fat loss" in lower || "lose weight" in lower || "weight loss" in lower || "cutting" in lower -> WorkoutProgramGoal.FAT_LOSS
            "hypertrophy" in lower || "muscle" in lower || "build size" in lower || "bulk" in lower -> WorkoutProgramGoal.HYPERTROPHY
            "strength" in lower || "get strong" in lower || "powerlifting" in lower -> WorkoutProgramGoal.STRENGTH
            "endurance" in lower || "cardio" in lower || "conditioning" in lower || "stamina" in lower -> WorkoutProgramGoal.ENDURANCE
            else -> null
        }
    }

    private fun extractEquipment(prompt: String): Set<Equipment> {
        val lower = prompt.normalizedForParsing()
        if (hasAny(lower, noEquipmentTerms)) return setOf(Equipment.BODYWEIGHT_ONLY)
        if (hasAny(lower, fullGymTerms)) return fullGymEquipment

        val includeBodyweight = hasAny(lower, bodyweightTerms)
        val explicitEquipment = buildSet {
            if (includeBodyweight) add(Equipment.BODYWEIGHT_ONLY)
            if (hasAny(lower, dumbbellTerms)) add(Equipment.DUMBBELLS)
            if (hasAny(lower, barbellTerms)) add(Equipment.BARBELL)
            if (hasAny(lower, bandTerms)) add(Equipment.RESISTANCE_BANDS)
            if (hasAny(lower, kettlebellTerms)) add(Equipment.KETTLEBELLS)
            if (hasAny(lower, pullUpTerms)) add(Equipment.PULL_UP_BAR)
            if (hasAny(lower, benchTerms)) add(Equipment.BENCH)
            if (hasAny(lower, cableTerms)) add(Equipment.CABLE_MACHINE)
            if (hasAny(lower, treadmillTerms)) add(Equipment.TREADMILL)
            if (hasAny(lower, bikeTerms)) add(Equipment.EXERCISE_BIKE)
        }
        if (explicitEquipment.isNotEmpty()) return explicitEquipment
        if (hasAny(lower, homeBodyweightTerms)) return setOf(Equipment.BODYWEIGHT_ONLY)
        return emptySet()
    }

    private fun extractExplicitWeeklyCount(lowerPrompt: String): Int? {
        weeklyCountPatterns.forEach { pattern ->
            pattern.find(lowerPrompt)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let {
                return it.coerceIn(1, 6)
            }
        }

        wordNumbers.forEach { (word, value) ->
            if (Regex("""\b$word\s*[- ]?\s*days?\b""").containsMatchIn(lowerPrompt)) return value
            if (Regex("""\b$word\s*(?:workouts?|sessions?)\s*(?:a|per)?\s*week\b""").containsMatchIn(lowerPrompt)) {
                return value
            }
        }

        return null
    }

    private fun hasPplSplit(lowerPrompt: String): Boolean =
        hasAny(lowerPrompt, listOf("push pull legs", "push/pull/legs", "push-pull-legs", "ppl split", "ppl program", "ppl routine"))

    private fun asksForSingleSession(lowerPrompt: String): Boolean {
        if (hasAny(lowerPrompt, singleSessionTerms)) return true
        val hasProgramShape = hasAny(lowerPrompt, programShapeTerms) || hasPplSplit(lowerPrompt)
        val singularWorkout = Regex("""\b(?:a|one|1)\s+(?:[a-z]+\s+){0,3}workout\b""").containsMatchIn(lowerPrompt)
        return singularWorkout && !hasProgramShape
    }

    private fun String.normalizedForParsing(): String =
        lowercase()
            .replace(Regex("""[^\w\s/-]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

    private fun hasAny(text: String, terms: List<String>): Boolean =
        terms.any { term -> text.contains(term) }

    private fun List<ExerciseLibrary>.equipmentBreakdown(): String =
        groupingBy { it.equipment }
            .eachCount()
            .entries
            .sortedBy { it.key.name }
            .joinToString(prefix = "{", postfix = "}") { "${it.key.name}=${it.value}" }

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

    private fun invalidGenerationFailure(
        stage: String,
        details: String? = null
    ): LiftrixResult<ParsedProgram> {
        val message = details ?: "Generated workout program failed $stage after repair."
        Timber.w("[AI] GenerateWorkoutProgramUseCase: generation failed stage=$stage details=${message.take(500)}")
        return liftrixFailure(
            LiftrixError.BusinessLogicError(
                code = "AI_WORKOUT_GENERATION_INVALID",
                errorMessage = message,
                isRecoverable = true,
                analyticsContext = mapOf(
                    "stage" to stage,
                    "details" to message.take(500)
                )
            )
        )
    }

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

    @OptIn(ExperimentalSerializationApi::class)
    @Serializable
    private data class AiWorkoutDayDto(
        @SerialName("day_name")
        val dayName: String? = null,
        @SerialName("day_number")
        @JsonNames("day")
        val dayNumber: Int? = null,
        val focus: String? = null,
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
