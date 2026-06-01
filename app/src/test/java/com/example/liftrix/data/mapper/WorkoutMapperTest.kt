package com.example.liftrix.data.mapper

import com.example.liftrix.data.local.entity.WorkoutEntity
import com.example.liftrix.domain.model.WorkoutStatus
import com.google.common.truth.Truth.assertThat
import com.google.gson.Gson
import io.mockk.mockk
import java.time.Instant
import java.time.LocalDate
import org.junit.Test

class WorkoutMapperTest {

    private val mapper = WorkoutMapper(
        exerciseMapper = mockk(relaxed = true),
        exerciseConversionService = mockk(relaxed = true),
        kotlinxSerializer = mockk(relaxed = true),
        canonicalJsonAdapter = mockk(relaxed = true),
        gson = Gson(),
        jsonValidator = mockk(relaxed = true)
    )

    @Test
    fun `entityToFirestoreDto parses exercises from array JSON`() {
        val exercisesJson = """
            [
              {
                "id": "e1",
                "name": "Bench Press",
                "category": "CHEST",
                "sets": []
              }
            ]
        """.trimIndent()

        val dto = mapper.entityToFirestoreDto(createWorkoutEntity(exercisesJson), userId = "u1")

        assertThat(dto.exercises).hasSize(1)
        assertThat(dto.exercises.first().name).isEqualTo("Bench Press")
    }

    @Test
    fun `entityToFirestoreDto parses exercises from wrapper JSON`() {
        val exercisesJson = """
            {
              "exercises": [
                {
                  "id": "e1",
                  "name": "Squat",
                  "category": "LEGS",
                  "sets": []
                }
              ]
            }
        """.trimIndent()

        val dto = mapper.entityToFirestoreDto(createWorkoutEntity(exercisesJson), userId = "u1")

        assertThat(dto.exercises).hasSize(1)
        assertThat(dto.exercises.first().name).isEqualTo("Squat")
    }

    private fun createWorkoutEntity(exercisesJson: String): WorkoutEntity {
        return WorkoutEntity(
            id = "w1",
            userId = "u1",
            name = "Test Workout",
            date = LocalDate.of(2025, 1, 1),
            exercisesJson = exercisesJson,
            status = WorkoutStatus.COMPLETED,
            startTime = null,
            endTime = null,
            notes = null,
            templateId = null,
            createdAt = Instant.ofEpochSecond(1),
            updatedAt = Instant.ofEpochSecond(2),
            isSynced = false,
            syncVersion = 1L,
            isDirty = true,
            lastModified = 123L
        )
    }
}
