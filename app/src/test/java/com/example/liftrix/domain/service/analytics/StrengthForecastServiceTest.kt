package com.example.liftrix.domain.service.analytics

import com.example.liftrix.data.local.dao.StrengthForecastSetSampleResult
import com.example.liftrix.domain.model.analytics.StrengthForecastStatus
import com.example.liftrix.domain.model.analytics.StrengthForecastTrend
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class StrengthForecastServiceTest {

    private val service = StrengthForecastService()

    @Test
    fun `brzycki estimate uses configured formula and rejects invalid reps`() {
        assertEquals(112.5, service.estimateBrzyckiOneRmKg(100.0, 5)!!, 0.01)
        assertEquals(null, service.estimateBrzyckiOneRmKg(100.0, 37))
        assertEquals(null, service.estimateBrzyckiOneRmKg(0.0, 5))
    }

    @Test
    fun `two daily points generate fourteen day forecast`() {
        val result = service.buildForecast(
            samples = listOf(
                sample("2026-05-01", 80f, 5),
                sample("2026-05-18", 90f, 5)
            ),
            today = LocalDate(2026, 5, 18)
        )

        val forecast = result.exercises.single()
        assertEquals(StrengthForecastStatus.READY, forecast.status)
        assertEquals(14, forecast.forecastPoints.size)
        assertTrue(forecast.summary.projectedChangeKg!! > 1.0)
        assertEquals(StrengthForecastTrend.IMPROVING, forecast.summary.trend)
        assertNotNull(forecast.regression)
    }

    @Test
    fun `one datapoint is insufficient and does not project`() {
        val result = service.buildForecast(
            samples = listOf(sample("2026-05-18", 90f, 5)),
            today = LocalDate(2026, 5, 18)
        )

        val forecast = result.exercises.single()
        assertEquals(StrengthForecastStatus.INSUFFICIENT_DATA, forecast.status)
        assertEquals("Not enough data to generate forecast", forecast.summary.message)
        assertTrue(forecast.forecastPoints.isEmpty())
    }

    @Test
    fun `workout grouping keeps best estimated set and skips lower later records`() {
        val result = service.buildForecast(
            samples = listOf(
                sample("2026-05-01", 80f, 3, workoutId = "workout-1", hour = 10),
                sample("2026-05-01", 85f, 2, workoutId = "workout-1", hour = 10),
                sample("2026-05-01", 60f, 12, workoutId = "workout-2", hour = 12),
                sample("2026-05-18", 90f, 5, workoutId = "workout-3", hour = 10)
            ),
            today = LocalDate(2026, 5, 18)
        )

        val forecast = result.exercises.single()
        assertEquals(2, forecast.historicalPoints.size)
        assertEquals(87.43, forecast.historicalPoints.first().estimatedOneRmKg, 0.01)
        assertTrue(forecast.historicalPoints.zipWithNext().all { (left, right) ->
            right.estimatedOneRmKg >= left.estimatedOneRmKg
        })
    }

    @Test
    fun `same day workouts are kept as separate history and can project by workout sequence`() {
        val result = service.buildForecast(
            samples = listOf(
                sample("2026-05-18", 50f, 12, workoutId = "workout-1", hour = 9),
                sample("2026-05-18", 60f, 12, workoutId = "workout-2", hour = 12),
                sample("2026-05-18", 70f, 12, workoutId = "workout-3", hour = 15)
            ),
            today = LocalDate(2026, 5, 18)
        )

        val forecast = result.exercises.single()
        assertEquals(StrengthForecastStatus.READY, forecast.status)
        assertEquals(3, forecast.historicalPoints.size)
        assertEquals(14, forecast.forecastPoints.size)
        assertTrue(forecast.historicalPoints.zipWithNext().all { (left, right) ->
            (left.timelineDay ?: 0.0) < (right.timelineDay ?: 0.0)
        })
        assertEquals(100.8, forecast.summary.latestEstimatedOneRmKg!!, 0.01)
    }

    @Test
    fun `missing completed timestamps do not collapse historical workouts`() {
        val result = service.buildForecast(
            samples = listOf(
                sample("2026-05-18", 50f, 12, workoutId = "workout-1", completedAt = 0L),
                sample("2026-05-18", 60f, 12, workoutId = "workout-2", completedAt = 0L),
                sample("2026-05-18", 70f, 12, workoutId = "workout-3", completedAt = 0L),
                sample("2026-05-18", 75f, 12, workoutId = "workout-4", completedAt = 0L),
                sample("2026-05-18", 80f, 12, workoutId = "workout-5", completedAt = 0L)
            ),
            today = LocalDate(2026, 5, 18)
        )

        val forecast = result.exercises.single()
        assertEquals(StrengthForecastStatus.READY, forecast.status)
        assertEquals(5, forecast.historicalPoints.size)
        assertEquals(listOf(0.0, 1.0, 2.0, 3.0, 4.0), forecast.historicalPoints.map { it.timelineDay })
        assertEquals(14, forecast.forecastPoints.size)
        assertEquals(115.2, forecast.summary.latestEstimatedOneRmKg!!, 0.01)
    }

    private fun sample(
        date: String,
        weightKg: Float,
        reps: Int,
        workoutId: String = "workout-$date",
        hour: Int = 10,
        completedAt: Long = Instant.parse("${date}T${hour.toString().padStart(2, '0')}:00:00Z").toEpochMilli()
    ) = StrengthForecastSetSampleResult(
        exercise_library_id = "bench",
        exercise_name = "Bench Press",
        workout_id = workoutId,
        activity_date = date,
        weight_kg = weightKg,
        reps = reps,
        completed_at = completedAt
    )
}
