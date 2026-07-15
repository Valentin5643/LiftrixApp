package com.example.liftrix.domain.usecase.ai

import com.example.liftrix.domain.model.WorkoutTemplate
import com.example.liftrix.domain.model.ai.WorkoutProgramSourceReference
import com.example.liftrix.domain.model.ai.WorkoutProgramSourceType
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.usecase.template.TemplateQueryUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

class WorkoutReferenceResolver @Inject constructor(
    private val templateQueryUseCase: TemplateQueryUseCase
) {

    suspend operator fun invoke(request: WorkoutReferenceRequest): LiftrixResult<WorkoutReferenceResolution> =
        withContext(Dispatchers.IO) {
            liftrixCatching(
                errorMapper = { throwable ->
                    LiftrixError.BusinessLogicError(
                        code = "WORKOUT_REFERENCE_RESOLUTION_FAILED",
                        errorMessage = "Failed to resolve workout reference",
                        analyticsContext = mapOf(
                            "user_id" to request.userId,
                            "error" to throwable.message.orEmpty()
                        )
                    )
                }
            ) {
                require(request.userId.isNotBlank()) { "User ID cannot be blank" }

                val templates = templateQueryUseCase(request.userId).first()
                resolveFromPendingTemplate(request, templates)
                    ?: resolveGeneratedPreview(request)
                    ?: resolveNamedTemplate(request, templates)
                    ?: resolveCurrentTemplate(request, templates)
                    ?: WorkoutReferenceResolution.NotFound(
                        message = "I could not find a saved workout template to edit."
                    )
            }
        }

    private fun resolveFromPendingTemplate(
        request: WorkoutReferenceRequest,
        templates: List<WorkoutTemplate>
    ): WorkoutReferenceResolution? {
        val pendingTemplateId = request.pendingTemplateId?.takeIf { it.isNotBlank() } ?: return null
        val template = templates.firstOrNull { it.id.value.equals(pendingTemplateId, ignoreCase = true) }
            ?: return WorkoutReferenceResolution.NotFound(
                message = "The selected workout template is no longer available."
            )

        return WorkoutReferenceResolution.ResolvedTemplate(
            template = template,
            source = template.toSourceReference(),
            matchedBy = WorkoutReferenceMatchType.PENDING_TEMPLATE
        )
    }

    private fun resolveGeneratedPreview(request: WorkoutReferenceRequest): WorkoutReferenceResolution? {
        val previewId = request.pendingGeneratedProgramId?.takeIf { it.isNotBlank() } ?: return null
        val previewName = request.pendingGeneratedProgramName?.takeIf { it.isNotBlank() } ?: "Generated workout"
        if (!request.allowGeneratedPreview && !request.message.hasCurrentWorkoutPhrase()) return null

        return WorkoutReferenceResolution.ResolvedGeneratedPreview(
            source = WorkoutProgramSourceReference(
                sourceType = WorkoutProgramSourceType.GENERATED_PREVIEW,
                sourceId = previewId,
                sourceName = previewName
            )
        )
    }

    private fun resolveNamedTemplate(
        request: WorkoutReferenceRequest,
        templates: List<WorkoutTemplate>
    ): WorkoutReferenceResolution? {
        val message = request.message.trim()
        if (message.isBlank()) return null

        val idMatch = templates.firstOrNull { template ->
            message.contains(template.id.value, ignoreCase = true)
        }
        if (idMatch != null) {
            return WorkoutReferenceResolution.ResolvedTemplate(
                template = idMatch,
                source = idMatch.toSourceReference(),
                matchedBy = WorkoutReferenceMatchType.EXPLICIT_TEMPLATE_ID
            )
        }

        val candidates = templates.filter { template ->
            message.contains(template.name, ignoreCase = true) ||
                message.normalizedWords().contains(template.significantNamePrefix()) ||
                message.quotedFragments().any { fragment ->
                    fragment.equals(template.name, ignoreCase = true) ||
                        template.name.contains(fragment, ignoreCase = true)
                }
        }
        if (candidates.isEmpty()) return null

        val exactMatches = candidates.filter { template ->
            message.quotedFragments().any { it.equals(template.name, ignoreCase = true) } ||
                message.normalizedWords().contains(template.name.normalized())
        }
        val matches = exactMatches.ifEmpty { candidates }

        return when (matches.size) {
            1 -> WorkoutReferenceResolution.ResolvedTemplate(
                template = matches.single(),
                source = matches.single().toSourceReference(),
                matchedBy = WorkoutReferenceMatchType.EXPLICIT_TEMPLATE_NAME
            )
            else -> WorkoutReferenceResolution.NeedsClarification(
                message = "Multiple saved workout templates match that request.",
                options = matches.map { it.toSourceReference() }
            )
        }
    }

    private fun resolveCurrentTemplate(
        request: WorkoutReferenceRequest,
        templates: List<WorkoutTemplate>
    ): WorkoutReferenceResolution? {
        if (!request.message.hasCurrentWorkoutPhrase()) return null
        val recentTemplate = templates.maxWithOrNull(
            compareBy<WorkoutTemplate> { it.lastUsedAt ?: it.updatedAt }
                .thenBy { it.updatedAt }
        ) ?: return null

        return WorkoutReferenceResolution.ResolvedTemplate(
            template = recentTemplate,
            source = recentTemplate.toSourceReference(),
            matchedBy = WorkoutReferenceMatchType.RECENT_TEMPLATE
        )
    }

    private fun WorkoutTemplate.toSourceReference(): WorkoutProgramSourceReference =
        WorkoutProgramSourceReference(
            sourceType = WorkoutProgramSourceType.TEMPLATE,
            sourceId = id.value,
            sourceName = name
        )

    private fun String.hasCurrentWorkoutPhrase(): Boolean {
        val normalized = normalized()
        return CURRENT_WORKOUT_PHRASES.any { phrase -> normalized.contains(phrase) }
    }

    private fun String.quotedFragments(): List<String> =
        QUOTED_TEXT.findAll(this)
            .map { match -> (match.groupValues[1].ifBlank { match.groupValues[2] }).trim() }
            .filter { it.isNotBlank() }
            .toList()

    private fun WorkoutTemplate.significantNamePrefix(): String =
        name.normalized()
            .split(" ")
            .take(2)
            .joinToString(" ")

    private fun String.normalizedWords(): String = normalized()

    private fun String.normalized(): String =
        lowercase()
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")

    companion object {
        private val QUOTED_TEXT = Regex("\"([^\"]+)\"|'([^']+)'")
        private val CURRENT_WORKOUT_PHRASES = setOf(
            "this workout",
            "that workout",
            "current workout",
            "selected workout",
            "recent workout",
            "last workout",
            "latest workout",
            "previous workout",
            "this plan",
            "current plan",
            "recent plan",
            "last plan",
            "latest plan",
            "previous plan",
            "this routine",
            "current routine",
            "recent routine",
            "last routine",
            "latest routine",
            "previous routine"
        )
    }
}

data class WorkoutReferenceRequest(
    val userId: String,
    val message: String,
    val pendingTemplateId: String? = null,
    val pendingGeneratedProgramId: String? = null,
    val pendingGeneratedProgramName: String? = null,
    val allowGeneratedPreview: Boolean = false
)

sealed interface WorkoutReferenceResolution {
    data class ResolvedTemplate(
        val template: WorkoutTemplate,
        val source: WorkoutProgramSourceReference,
        val matchedBy: WorkoutReferenceMatchType
    ) : WorkoutReferenceResolution

    data class ResolvedGeneratedPreview(
        val source: WorkoutProgramSourceReference
    ) : WorkoutReferenceResolution

    data class NeedsClarification(
        val message: String,
        val options: List<WorkoutProgramSourceReference>
    ) : WorkoutReferenceResolution

    data class NotFound(
        val message: String
    ) : WorkoutReferenceResolution
}

enum class WorkoutReferenceMatchType {
    PENDING_TEMPLATE,
    EXPLICIT_TEMPLATE_ID,
    EXPLICIT_TEMPLATE_NAME,
    RECENT_TEMPLATE
}
