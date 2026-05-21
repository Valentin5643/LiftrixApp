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
    fun `daily grouping keeps heaviest set and tie breaks by estimate`() {
        val result = service.buildForecast(
            samples = listOf(
                sample("2026-05-01", 80f, 3),
                sample("2026-05-01", 85f, 2),
                sample("2026-05-18", 90f, 5)
            ),
            today = LocalDate(2026, 5, 18)
        )

        assertEquals(2, result.exercises.single().historicalPoints.size)
        assertTrue(result.exercises.single().historicalPoints.first().estimatedOneRmKg > 89.0)
    }

    private fun sample(date: String, weightKg: Float, reps: Int) = StrengthForecastSetSampleResult(
        exercise_library_id = "bench",
        exercise_name = "Bench Press",
        activity_date = date,
        weight_kg = weightKg,
        reps = reps,
        completed_at = Instant.parse("${date}T10:00:00Z").toEpochMilli()
    )
}
