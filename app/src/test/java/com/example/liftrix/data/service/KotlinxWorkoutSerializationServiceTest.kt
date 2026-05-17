package com.example.liftrix.data.service

import com.example.liftrix.core.performance.SerializationCacheManager
import com.example.liftrix.core.performance.SerializationPerformanceMonitor
import com.example.liftrix.core.security.JsonInputValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class KotlinxWorkoutSerializationServiceTest {

    private val performanceMonitor = SerializationPerformanceMonitor()
    private val service = KotlinxWorkoutSerializationService(
        jsonValidator = JsonInputValidator(),
        performanceMonitor = performanceMonitor,
        cacheManager = SerializationCacheManager(performanceMonitor)
    )

    @Test
    fun `deserializeExercises reads legacy named weight and reps fields`() {
        val json = """
            {
              "schemaVersion": 1,
              "exercises": [
                {
                  "id": "exercise-1",
                  "workoutId": "workout-1",
                  "libraryExerciseName": "Barbell Squat",
                  "orderIndex": 0,
                  "targetSets": 1,
                  "targetReps": 8,
                  "targetWeightKg": 60.0,
                  "sets": [
                    {
                      "id": "set-1",
                      "setNumber": 1,
                      "weight": 60.0,
                      "reps": 8,
                      "completedAt": "2026-05-01T10:00:00Z"
                    },
                    {
                      "id": "set-2",
                      "setNumber": 2,
                      "reps": { "count": 6 },
                      "weight": { "kilograms": 62.5 },
                      "completedAt": 1777630200000
                    }
                  ],
                  "createdAtEpochMilli": 1777629600000
                }
              ],
              "metadata": {
                "totalVolumeKg": 855.0,
                "totalSets": 2,
                "exerciseCount": 1,
                "createdAt": 1777629600000,
                "format": "legacy_alias_test"
              },
              "serializationFormat": "kotlinx.serialization"
            }
        """.trimIndent()

        val exercises = service.deserializeExercises(json)

        assertEquals(1, exercises.size)
        assertEquals(2, exercises.first().sets.size)
        assertEquals(60.0, exercises.first().sets[0].weight?.kilograms ?: 0.0, 0.0)
        assertEquals(8, exercises.first().sets[0].reps?.count)
        assertNotNull(exercises.first().sets[0].completedAt)
        assertEquals(62.5, exercises.first().sets[1].weight?.kilograms ?: 0.0, 0.0)
        assertEquals(6, exercises.first().sets[1].reps?.count)
        assertNotNull(exercises.first().sets[1].completedAt)
    }
}
