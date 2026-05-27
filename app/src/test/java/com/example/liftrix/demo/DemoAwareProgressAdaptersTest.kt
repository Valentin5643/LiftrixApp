package com.example.liftrix.demo

import com.example.liftrix.domain.model.analytics.AnalyticsWidget
import com.example.liftrix.domain.model.analytics.MuscleHeatmapColorMode
import com.example.liftrix.domain.model.analytics.MuscleHeatmapGender
import com.example.liftrix.domain.model.analytics.MuscleHeatmapMetric
import com.example.liftrix.domain.model.analytics.MuscleHeatmapViewSide
import com.example.liftrix.domain.model.analytics.RankingMetric
import com.example.liftrix.domain.model.analytics.TimeRange
import com.example.liftrix.domain.model.analytics.TimeRangeType
import com.example.liftrix.domain.model.analytics.VolumeGrouping
import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.LocalDate
import org.junit.Test
import java.util.Date

class DemoAwareProgressAdaptersTest {
    private val timeline = DemoTimelineGenerator().generate(20260525L, LocalDate(2026, 5, 25))
    private val factory = DemoProgressDataFactory()
    private val timeRange = TimeRange(Date(0), Date(1_800_000_000_000L))

    @Test
    fun `progress projections are non empty and internally consistent`() {
        val volume = factory.volumeData(timeline, timeRange)
        val duration = factory.durationData(timeline, timeRange)
        val frequency = factory.frequencyData(timeline, timeRange)
        val summary = factory.progressSummary(timeline, timeRange)

        assertThat(volume).isNotEmpty()
        assertThat(duration).isNotEmpty()
        assertThat(frequency).isNotEmpty()
        assertThat(summary.totalWorkouts).isEqualTo(timeline.workouts.size)
        assertThat(summary.totalVolume).isWithin(0.1f).of(timeline.totalVolumeKg.toFloat())
    }

    @Test
    fun `active widgets return valid demo data`() {
        AnalyticsWidget.getActiveWidgets().forEach { widget ->
            val data = factory.widgetData(timeline, widget)

            assertThat(data.widgetType).isEqualTo(widget)
            assertThat(data.error).isNull()
            assertThat(data.isValid).isTrue()
        }
    }

    @Test
    fun `volume calendar monthly total stays within volume invariant`() {
        val calendar = factory.volumeCalendarData(timeline)

        assertThat(calendar.getTotalMonthVolume().kilograms)
            .isAtMost(com.example.liftrix.domain.model.Volume.MAX_VOLUME_KG)
    }

    @Test
    fun `dashboard gateway projections include graph data`() {
        val graphWidgets = listOf(
            AnalyticsWidget.VolumeAnalytics,
            AnalyticsWidget.FrequencyChart,
            AnalyticsWidget.WorkoutDuration,
            AnalyticsWidget.StrengthAnalytics
        )

        graphWidgets.forEach { widget ->
            val data = factory.dashboardWidgetData(timeline, widget)

            assertThat(data.data["chartData"] as? List<*>).isNotEmpty()
        }
    }

    @Test
    fun `strength forecast demo data has historical and forecast points`() {
        val forecast = factory.strengthForecast(timeline, selectedExerciseId = null, historyDays = 30, forecastDays = 14)
        val readyExercise = forecast.exercises.first()

        assertThat(readyExercise.historicalPoints).isNotEmpty()
        assertThat(readyExercise.forecastPoints).hasSize(14)
        assertThat(readyExercise.historicalPoints.zipWithNext().all { (left, right) ->
            right.estimatedOneRmKg >= left.estimatedOneRmKg
        }).isTrue()
        assertThat(readyExercise.forecastPoints.zipWithNext().all { (left, right) ->
            right.estimatedOneRmKg >= left.estimatedOneRmKg
        }).isTrue()
    }

    @Test
    fun `detail graph projections are populated in demo mode`() {
        val volume = factory.volumeAnalysisData(timeline, TimeRangeType.SIX_MONTHS, null, VolumeGrouping.BY_WEEK)
        val oneRm = factory.oneRmProgressionData(timeline, null, TimeRangeType.SIX_MONTHS, includeEstimated = true)
        val frequency = factory.workoutFrequencyData(timeline, TimeRangeType.SIX_MONTHS)
        val muscleGroups = factory.muscleGroupAnalyticsData(timeline, TimeRangeType.SIX_MONTHS, null)
        val rankings = factory.exerciseRankingData(timeline, TimeRangeType.SIX_MONTHS, RankingMetric.PERFORMANCE_SCORE)

        assertThat(volume.volumeData).isNotEmpty()
        assertThat(oneRm.exerciseProgressions).isNotEmpty()
        assertThat(oneRm.exerciseProgressions.first().progressionPoints).isNotEmpty()
        oneRm.exerciseProgressions.forEach { progression ->
            assertThat(progression.progressionPoints.zipWithNext().all { (left, right) ->
                (right.oneRmValue ?: 0f) >= (left.oneRmValue ?: 0f)
            }).isTrue()
        }
        assertThat(frequency.frequencyPoints).isNotEmpty()
        assertThat(muscleGroups.muscleGroupDistribution).isNotEmpty()
        assertThat(rankings.rankedExercises).isNotEmpty()
    }

    @Test
    fun `demo one rm widget chart data is record high only`() {
        val widgetData = factory.widgetData(timeline, AnalyticsWidget.OneRMProgression)
        val chartData = widgetData as com.example.liftrix.domain.model.analytics.ChartWidgetData
        val dashboardData = factory.dashboardWidgetData(timeline, AnalyticsWidget.OneRMProgression)
        val dashboardChart = dashboardData.data["chartData"] as List<*>

        assertThat(chartData.dataPoints.zipWithNext().all { (left, right) -> right.y >= left.y }).isTrue()
        assertThat(dashboardChart.zipWithNext().all { (left, right) ->
            (right as Int) >= (left as Int)
        }).isTrue()
    }

    @Test
    fun `demo muscle heatmap applies selected configuration`() {
        val heatmap = factory.muscleHeatmapData(
            timeline = timeline,
            configuration = mapOf(
                "gender" to "female",
                "viewSide" to "back",
                "timeRange" to "SIX_MONTHS",
                "metric" to "sets",
                "colorMode" to "monochrome_intensity"
            )
        )

        assertThat(heatmap.gender).isEqualTo(MuscleHeatmapGender.FEMALE)
        assertThat(heatmap.viewSide).isEqualTo(MuscleHeatmapViewSide.BACK)
        assertThat(heatmap.timeRange).isEqualTo(TimeRangeType.SIX_MONTHS)
        assertThat(heatmap.metric).isEqualTo(MuscleHeatmapMetric.SETS)
        assertThat(heatmap.colorMode).isEqualTo(MuscleHeatmapColorMode.MONOCHROME_INTENSITY)
        assertThat(heatmap.muscleValues).isNotEmpty()
    }
}
