package com.example.liftrix.ui.chat.workoutbuilder

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.liftrix.domain.interactor.auth.AuthInteractor
import com.example.liftrix.domain.interactor.chat.ChatInteractor
import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.ai.GeneratedWorkoutProgram
import com.example.liftrix.domain.model.ai.SavedGeneratedWorkoutDay
import com.example.liftrix.domain.model.ai.WorkoutGenerationPreferences
import com.example.liftrix.domain.model.ai.WorkoutGenerationResult
import com.example.liftrix.domain.model.ai.WorkoutGenerationStage
import com.example.liftrix.domain.model.ai.WorkoutModificationSignificance
import com.example.liftrix.domain.model.ai.WorkoutModificationSaveMode
import com.example.liftrix.domain.model.ai.WorkoutProgramGoal
import com.example.liftrix.domain.model.ai.WorkoutProgramLevel
import com.example.liftrix.domain.model.ai.WorkoutProgramSaveOutcome
import com.example.liftrix.domain.model.ai.WorkoutProgramChangeSummary
import com.example.liftrix.domain.model.ai.WorkoutProgramSourceReference
import com.example.liftrix.domain.model.ai.WorkoutTrainingDay
import com.example.liftrix.domain.model.chat.MessageType
import com.example.liftrix.domain.service.Language
import com.example.liftrix.domain.service.NetworkConnectivityMonitor
import com.example.liftrix.domain.usecase.ai.ModifyWorkoutProgramRequest
import com.example.liftrix.domain.usecase.ai.WorkoutModificationScope
import com.example.liftrix.domain.usecase.ai.WorkoutProgramGateway
import com.example.liftrix.ui.common.viewmodel.ModernBaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val BUILDER_SNAPSHOT_KEY = "ai_workout_builder_snapshot"
private const val BUILDER_SNAPSHOT_VERSION = 1
private const val BUILDER_SNAPSHOT_MAX_BYTES = 200 * 1024

private val builderSnapshotJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

@HiltViewModel
class AIWorkoutBuilderViewModel @Inject constructor(
    private val authInteractor: AuthInteractor,
    private val chatInteractor: ChatInteractor,
    private val gateway: WorkoutProgramGateway,
    private val connectivity: NetworkConnectivityMonitor,
    private val savedStateHandle: SavedStateHandle
) : ModernBaseViewModel<AIWorkoutBuilderState>(
    AIWorkoutBuilderState(
        draft = WorkoutGenerationPreferences(
            goal = WorkoutProgramGoal.GENERAL_FITNESS,
            level = WorkoutProgramLevel.BEGINNER,
            availableEquipment = setOf(Equipment.BODYWEIGHT_ONLY),
            trainingDays = listOf(WorkoutTrainingDay.MONDAY, WorkoutTrainingDay.WEDNESDAY, WorkoutTrainingDay.FRIDAY),
            sessionDurationMinutes = 45,
            additionalPreferences = savedStateHandle["seedPrompt"] ?: ""
        ),
        conversationId = savedStateHandle["conversationId"]
    )
) {
    private var userId: String? = null
    private var inFlight = false
    private var lastSnapshot: String? = null

    init {
        restoreSnapshot()
        viewModelScope.launch {
            val id = authInteractor.currentUser(true).getOrNull()?.value
            userId = id
            if (id != null) {
                chatInteractor.observePreferences(id).collectLatest { preferences ->
                    val language = preferences?.preferredLanguage.toBuilderLanguage()
                    updateState { state ->
                        if (state.language == language) state else state.copy(language = language)
                    }
                }
            }
        }
        viewModelScope.launch {
            connectivity.isConnected.collectLatest { online -> updateState { it.copy(isOnline = online) } }
        }
        viewModelScope.launch {
            uiState.collectLatest { state -> persistSnapshot(state) }
        }
    }

    fun updateDraft(draft: WorkoutGenerationPreferences) {
        if (_uiState.value.step == BuilderStep.GENERATING || inFlight) return
        savedStateHandle["builder_step"] = BuilderStep.FORM.name
        updateState { it.copy(draft = draft.sanitized(), step = BuilderStep.FORM, error = null) }
    }

    fun review() {
        if (_uiState.value.draft.validationErrors().isEmpty()) {
            savedStateHandle["builder_step"] = BuilderStep.REVIEW.name
            updateState { it.copy(step = BuilderStep.REVIEW, error = null) }
        }
    }

    fun editPreferences() = updateState { it.copy(step = BuilderStep.FORM) }

    fun generate(forceRefresh: Boolean = false) {
        val id = userId ?: return fail("Sign in is required to create a plan.")
        val state = _uiState.value
        if (inFlight || !state.isOnline || state.draft.validationErrors().isNotEmpty() || state.step != BuilderStep.REVIEW && !forceRefresh) return
        inFlight = true
        updateState { it.copy(step = BuilderStep.GENERATING, error = null, activeAction = BuilderAction.GENERATE) }
        viewModelScope.launch {
            gateway.generate(id, state.draft, state.language, forceRefresh) { stage ->
                updateState { current -> current.copy(generationStage = stage) }
            }.fold(
                onSuccess = { result ->
                    updateState { it.copy(step = BuilderStep.PREVIEW, result = result, activeAction = null, dirty = true) }
                    recordResult("Generated ${result.program.workoutName}")
                },
                onFailure = { fail(it.message ?: "Workout generation failed.") }
            )
            inFlight = false
        }
    }

    fun replaceExercise(dayIndex: Int, exerciseId: String) = modify(
        WorkoutModificationScope.ReplaceExercise(dayIndex, exerciseId),
        "Replace the selected exercise with one compatible alternative."
    )

    fun regenerateDay(dayIndex: Int) = modify(
        WorkoutModificationScope.RegenerateDay(dayIndex),
        "Regenerate only the selected day."
    )

    private fun modify(scope: WorkoutModificationScope, message: String) {
        val id = userId ?: return
        val state = _uiState.value
        val current = state.result ?: return
        if (inFlight || !state.isOnline) return
        inFlight = true
        updateState { it.copy(activeAction = BuilderAction.MODIFY, error = null) }
        viewModelScope.launch {
            gateway.previewModification(
                ModifyWorkoutProgramRequest(
                    userId = id,
                    message = message,
                    pendingGeneratedProgram = current,
                    scope = scope,
                    preferences = state.draft,
                    language = state.language
                )
            ).fold(
                onSuccess = { updateState { state -> state.copy(result = it, activeAction = null, dirty = true) } },
                onFailure = { fail(it.message ?: "The requested change could not be applied.") }
            )
            inFlight = false
        }
    }

    fun applyLocalEdit(program: GeneratedWorkoutProgram) {
        val result = _uiState.value.result ?: return
        updateState { it.copy(result = result.copy(program = program), dirty = true, error = null) }
    }

    fun save() {
        val id = userId ?: return
        val current = _uiState.value.result ?: return
        if (inFlight) return
        inFlight = true
        updateState { it.copy(step = BuilderStep.SAVING, activeAction = BuilderAction.SAVE, error = null) }
        viewModelScope.launch {
            gateway.saveGeneratedProgram(id, current.program, _uiState.value.draft, _uiState.value.savedDays).fold(
                onSuccess = { outcome ->
                    when (outcome) {
                        is WorkoutProgramSaveOutcome.Complete -> {
                            updateState {
                                it.copy(step = BuilderStep.SAVED, savedDays = outcome.savedDays, dirty = false, activeAction = null)
                            }
                            clearSnapshot()
                        }
                        is WorkoutProgramSaveOutcome.Partial -> updateState {
                            it.copy(step = BuilderStep.PARTIAL, savedDays = outcome.savedDays, error = outcome.error.message, activeAction = null)
                        }
                    }
                },
                onFailure = { fail(it.message ?: "The plan could not be saved.") }
            )
            inFlight = false
        }
    }

    private fun restoreSnapshot() {
        val encoded = savedStateHandle.get<String>(BUILDER_SNAPSHOT_KEY) ?: return
        if (encoded.toByteArray(Charsets.UTF_8).size > BUILDER_SNAPSHOT_MAX_BYTES) {
            clearSnapshot()
            return
        }

        val snapshot = runCatching {
            builderSnapshotJson.decodeFromString<BuilderSnapshot>(encoded)
        }.getOrNull()
        if (snapshot == null || snapshot.version != BUILDER_SNAPSHOT_VERSION) {
            clearSnapshot()
            return
        }

        val persistedStep = runCatching { BuilderStep.valueOf(snapshot.step) }.getOrNull()
        val decodedResult = snapshot.result?.toWorkoutGenerationResult()
        if (persistedStep == null || (snapshot.result != null && decodedResult == null)) {
            clearSnapshot()
            return
        }

        val result = if (persistedStep == BuilderStep.GENERATING) null else decodedResult
        val restoredStep = when {
            persistedStep == BuilderStep.GENERATING -> BuilderStep.REVIEW
            persistedStep == BuilderStep.SAVING || persistedStep == BuilderStep.PARTIAL || persistedStep == BuilderStep.SAVED ->
                if (result != null) BuilderStep.PREVIEW else BuilderStep.REVIEW
            result == null && persistedStep in setOf(BuilderStep.PREVIEW, BuilderStep.SAVING, BuilderStep.PARTIAL, BuilderStep.SAVED) ->
                BuilderStep.REVIEW
            else -> persistedStep
        }.let { step ->
            if (snapshot.draft.sanitized().validationErrors().isEmpty() || step == BuilderStep.FORM) step else BuilderStep.FORM
        }
        val warning = when {
            persistedStep == BuilderStep.GENERATING ->
                "Generation was interrupted. Review your preferences before generating again."
            persistedStep == BuilderStep.SAVING || persistedStep == BuilderStep.PARTIAL ||
                persistedStep == BuilderStep.SAVED || snapshot.saveMayHaveCompleted ->
                "Saving may have completed before the app closed. Verify your existing workouts before saving again."
            else -> null
        }

        setState(
            _uiState.value.copy(
                draft = snapshot.draft.sanitized(),
                conversationId = snapshot.conversationId ?: _uiState.value.conversationId,
                step = restoredStep,
                result = result,
                expandedDays = snapshot.expandedDays.toSet().ifEmpty { setOf(0) },
                language = snapshot.languageCode.toBuilderLanguage(),
                dirty = snapshot.dirty || result != null || warning != null,
                error = warning,
                generationStage = null,
                savedDays = emptyList(),
                activeAction = null,
                showDiscardDialog = false
            )
        )
    }

    private fun persistSnapshot(state: AIWorkoutBuilderState) {
        if (state.step == BuilderStep.SAVED) {
            clearSnapshot()
            return
        }

        val encoded = runCatching {
            builderSnapshotJson.encodeToString(
                BuilderSnapshot(
                    version = BUILDER_SNAPSHOT_VERSION,
                    draft = state.draft,
                    step = state.step.name,
                    languageCode = state.language.code,
                    conversationId = state.conversationId,
                    result = state.result?.toBuilderResultSnapshot(),
                    expandedDays = state.expandedDays.toList().sorted(),
                    dirty = state.dirty,
                    saveMayHaveCompleted = state.step == BuilderStep.SAVING || state.step == BuilderStep.PARTIAL
                )
            )
        }.getOrNull()

        if (encoded == null || encoded.toByteArray(Charsets.UTF_8).size > BUILDER_SNAPSHOT_MAX_BYTES) {
            clearSnapshot()
            return
        }
        if (encoded != lastSnapshot) {
            savedStateHandle[BUILDER_SNAPSHOT_KEY] = encoded
            lastSnapshot = encoded
        }
    }

    private fun clearSnapshot() {
        savedStateHandle.remove<String>(BUILDER_SNAPSHOT_KEY)
        lastSnapshot = null
    }

    fun toggleDay(index: Int) = updateState {
        val next = it.expandedDays.toMutableSet().apply { if (!add(index)) remove(index) }
        it.copy(expandedDays = next)
    }

    fun requestDiscard() = updateState { it.copy(showDiscardDialog = it.dirty || it.step == BuilderStep.GENERATING) }
    fun dismissDiscard() = updateState { it.copy(showDiscardDialog = false) }
    fun dismissError() = updateState { it.copy(error = null) }

    private fun fail(message: String) {
        updateState { current ->
            current.copy(
                step = if (current.result != null) BuilderStep.PREVIEW else BuilderStep.REVIEW,
                error = message,
                activeAction = null
            )
        }
        inFlight = false
    }

    private fun recordResult(summary: String) {
        val id = userId ?: return
        val conversation = _uiState.value.conversationId ?: return
        viewModelScope.launch {
            chatInteractor.recordMessage(
                messageId = "builder-${_uiState.value.result?.previewId}-assistant",
                userId = id,
                conversationId = conversation,
                content = summary,
                type = MessageType.AI_RESPONSE,
                language = _uiState.value.language.code
            )
        }
    }
}

enum class BuilderStep { FORM, REVIEW, GENERATING, PREVIEW, SAVING, PARTIAL, SAVED }
enum class BuilderAction { GENERATE, MODIFY, SAVE }

data class AIWorkoutBuilderState(
    val draft: WorkoutGenerationPreferences,
    val conversationId: String? = null,
    val step: BuilderStep = BuilderStep.FORM,
    val result: WorkoutGenerationResult? = null,
    val generationStage: WorkoutGenerationStage? = null,
    val savedDays: List<SavedGeneratedWorkoutDay> = emptyList(),
    val expandedDays: Set<Int> = setOf(0),
    val activeAction: BuilderAction? = null,
    val isOnline: Boolean = true,
    val language: Language = Language.ENGLISH,
    val dirty: Boolean = false,
    val error: String? = null,
    val showDiscardDialog: Boolean = false
) {
    val canReview: Boolean get() = draft.validationErrors().isEmpty()
}

fun WorkoutGenerationPreferences.validationErrors(): List<String> = buildList {
    if (availableEquipment.isEmpty()) add("Select at least one equipment option.")
    if (trainingDays.isEmpty() || trainingDays.size > 6 || trainingDays.distinct().size != trainingDays.size) add("Select 1-6 distinct training days.")
    if (sessionDurationMinutes !in 5..90) add("Session duration must be 5-90 minutes.")
    if (limitations.length > WorkoutGenerationPreferences.MAX_FREE_TEXT_LENGTH) add("Limitations are too long.")
    if (additionalPreferences.length > WorkoutGenerationPreferences.MAX_FREE_TEXT_LENGTH) add("Additional preferences are too long.")
}

private fun WorkoutGenerationPreferences.sanitized() = copy(
    limitations = limitations.take(WorkoutGenerationPreferences.MAX_FREE_TEXT_LENGTH),
    additionalPreferences = additionalPreferences.take(WorkoutGenerationPreferences.MAX_FREE_TEXT_LENGTH),
    trainingDays = trainingDays.distinct().take(6)
)

@Serializable
private data class BuilderSnapshot(
    val version: Int,
    val draft: WorkoutGenerationPreferences,
    val step: String,
    val languageCode: String,
    val conversationId: String? = null,
    val result: BuilderResultSnapshot? = null,
    val expandedDays: List<Int> = emptyList(),
    val dirty: Boolean = false,
    val saveMayHaveCompleted: Boolean = false
)

@Serializable
private data class BuilderResultSnapshot(
    val previewId: String,
    val program: GeneratedWorkoutProgram,
    val validationWarnings: List<String>,
    val cacheHit: Boolean,
    val repairAttempts: Int,
    val tokensUsed: Int,
    val processingTimeMs: Long,
    val modelVersion: String?,
    val sourceReference: WorkoutProgramSourceReference?,
    val changeSummaries: List<WorkoutProgramChangeSummary>,
    val significance: String,
    val requiresConfirmation: Boolean,
    val defaultSaveMode: String,
    val optionalQuestion: String?,
    val saveTargetTemplateId: String?
)

private fun WorkoutGenerationResult.toBuilderResultSnapshot() = BuilderResultSnapshot(
    previewId = previewId,
    program = program,
    validationWarnings = validationWarnings,
    cacheHit = cacheHit,
    repairAttempts = repairAttempts,
    tokensUsed = tokensUsed,
    processingTimeMs = processingTimeMs,
    modelVersion = modelVersion,
    sourceReference = sourceReference,
    changeSummaries = changeSummaries,
    significance = significance.name,
    requiresConfirmation = requiresConfirmation,
    defaultSaveMode = defaultSaveMode.name,
    optionalQuestion = optionalQuestion,
    saveTargetTemplateId = saveTargetTemplateId
)

private fun BuilderResultSnapshot.toWorkoutGenerationResult(): WorkoutGenerationResult? = runCatching {
    WorkoutGenerationResult(
        previewId = previewId,
        program = program,
        validationWarnings = validationWarnings,
        cacheHit = cacheHit,
        repairAttempts = repairAttempts,
        tokensUsed = tokensUsed,
        processingTimeMs = processingTimeMs,
        modelVersion = modelVersion,
        sourceReference = sourceReference,
        changeSummaries = changeSummaries,
        significance = WorkoutModificationSignificance.valueOf(significance),
        requiresConfirmation = requiresConfirmation,
        defaultSaveMode = WorkoutModificationSaveMode.valueOf(defaultSaveMode),
        optionalQuestion = optionalQuestion,
        saveTargetTemplateId = saveTargetTemplateId
    )
}.getOrNull()

private fun String?.toBuilderLanguage(): Language =
    if (this == Language.ROMANIAN.code) Language.ROMANIAN else Language.ENGLISH
