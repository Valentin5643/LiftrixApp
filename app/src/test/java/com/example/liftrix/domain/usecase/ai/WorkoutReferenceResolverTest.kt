package com.example.liftrix.domain.usecase.ai

import com.example.liftrix.domain.model.WorkoutTemplate
import com.example.liftrix.domain.model.WorkoutTemplateId
import com.example.liftrix.domain.model.ai.WorkoutProgramSourceType
import com.example.liftrix.domain.usecase.template.TemplateQueryUseCase
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

class WorkoutReferenceResolverTest {

    private val templateQueryUseCase = mockk<TemplateQueryUseCase>()
    private val resolver = WorkoutReferenceResolver(templateQueryUseCase)

    @Test
    fun `resolves explicit template name from user-scoped templates`() = runTest {
        every { templateQueryUseCase("user-1") } returns flowOf(
            listOf(
                template("template-a", "Push Day"),
                template("template-b", "Leg Day")
            )
        )

        val result = resolver(
            WorkoutReferenceRequest(
                userId = "user-1",
                message = "Make Push Day easier"
            )
        ).getOrThrow()

        val resolved = assertIs<WorkoutReferenceResolution.ResolvedTemplate>(result)
        assertEquals("template-a", resolved.template.id.value)
        assertEquals(WorkoutReferenceMatchType.EXPLICIT_TEMPLATE_NAME, resolved.matchedBy)
        assertEquals(WorkoutProgramSourceType.TEMPLATE, resolved.source.sourceType)
        verify(exactly = 1) { templateQueryUseCase("user-1") }
    }

    @Test
    fun `resolves pending template id before message text`() = runTest {
        every { templateQueryUseCase("user-1") } returns flowOf(
            listOf(
                template("template-a", "Push Day"),
                template("template-b", "Leg Day")
            )
        )

        val result = resolver(
            WorkoutReferenceRequest(
                userId = "user-1",
                message = "Make this workout easier",
                pendingTemplateId = "template-b"
            )
        ).getOrThrow()

        val resolved = assertIs<WorkoutReferenceResolution.ResolvedTemplate>(result)
        assertEquals("template-b", resolved.template.id.value)
        assertEquals(WorkoutReferenceMatchType.PENDING_TEMPLATE, resolved.matchedBy)
    }

    @Test
    fun `returns clarification when multiple templates match`() = runTest {
        every { templateQueryUseCase("user-1") } returns flowOf(
            listOf(
                template("template-a", "Upper Body A"),
                template("template-b", "Upper Body B")
            )
        )

        val result = resolver(
            WorkoutReferenceRequest(
                userId = "user-1",
                message = "Adjust Upper Body for hypertrophy"
            )
        ).getOrThrow()

        val clarification = assertIs<WorkoutReferenceResolution.NeedsClarification>(result)
        assertEquals(listOf("template-a", "template-b"), clarification.options.map { it.sourceId })
    }

    @Test
    fun `resolves this workout to pending generated preview when present`() = runTest {
        every { templateQueryUseCase("user-1") } returns flowOf(emptyList())

        val result = resolver(
            WorkoutReferenceRequest(
                userId = "user-1",
                message = "Make this workout harder",
                pendingGeneratedProgramId = "preview-1",
                pendingGeneratedProgramName = "AI Push Plan"
            )
        ).getOrThrow()

        val resolved = assertIs<WorkoutReferenceResolution.ResolvedGeneratedPreview>(result)
        assertEquals(WorkoutProgramSourceType.GENERATED_PREVIEW, resolved.source.sourceType)
        assertEquals("preview-1", resolved.source.sourceId)
    }

    @Test
    fun `resolves last workout phrase to most recently used template`() = runTest {
        every { templateQueryUseCase("user-1") } returns flowOf(
            listOf(
                template(
                    id = "template-a",
                    name = "Older Push",
                    updatedAt = Instant.parse("2026-04-01T00:00:00Z")
                ),
                template(
                    id = "template-b",
                    name = "Latest Legs",
                    updatedAt = Instant.parse("2026-05-01T00:00:00Z")
                )
            )
        )

        val result = resolver(
            WorkoutReferenceRequest(
                userId = "user-1",
                message = "Make my last workout easier"
            )
        ).getOrThrow()

        val resolved = assertIs<WorkoutReferenceResolution.ResolvedTemplate>(result)
        assertEquals("template-b", resolved.template.id.value)
        assertEquals(WorkoutReferenceMatchType.RECENT_TEMPLATE, resolved.matchedBy)
    }

    private fun template(
        id: String,
        name: String,
        updatedAt: Instant = Instant.parse("2026-04-01T00:00:00Z")
    ): WorkoutTemplate =
        WorkoutTemplate(
            id = WorkoutTemplateId(id),
            userId = "user-1",
            name = name,
            description = null,
            exercises = emptyList(),
            estimatedDurationMinutes = 45,
            difficultyLevel = 5,
            folderId = null,
            usageCount = 0,
            lastUsedAt = null,
            createdAt = updatedAt,
            updatedAt = updatedAt
        )
}
