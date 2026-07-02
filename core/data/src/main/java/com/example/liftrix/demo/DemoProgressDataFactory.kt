package com.example.liftrix.demo

import com.example.liftrix.domain.model.MuscleGroup
import com.example.liftrix.domain.model.Volume
import com.example.liftrix.domain.model.analytics.Achievement
import com.example.liftrix.domain.model.analytics.AnalyticsWidget
import com.example.liftrix.domain.model.analytics.AnalyticsWidgetData
import com.example.liftrix.domain.model.analytics.ChartSummary
import com.example.liftrix.domain.model.analytics.ChartType
import com.example.liftrix.domain.model.analytics.ChartWidgetData
import com.example.liftrix.domain.model.analytics.DataPoint
import com.example.liftrix.domain.model.analytics.Insight
import com.example.liftrix.domain.model.analytics.InsightCategory
import com.example.liftrix.domain.model.analytics.MetricWidgetData
import com.example.liftrix.domain.model.analytics.Milestone
import com.example.liftrix.domain.model.analytics.MuscleHeatmapColorMode
import com.example.liftrix.domain.model.analytics.MuscleHeatmapGender
import com.example.liftrix.domain.model.analytics.MuscleHeatmapMetric
import com.example.liftrix.domain.model.analytics.MuscleHeatmapMuscleValue
import com.example.liftrix.domain.model.analytics.MuscleHeatmapViewSide
import com.example.liftrix.domain.model.analytics.MuscleHeatmapWidgetData
import com.example.liftrix.domain.model.analytics.ProgressWidgetData
import com.example.liftrix.domain.model.analytics.Recommendation
import com.example.liftrix.domain.model.analytics.RecommendationPriority
import com.example.liftrix.domain.model.analytics.StrengthExerciseForecast
import com.example.liftrix.domain.model.analytics.StrengthForecastPoint
import com.example.liftrix.domain.model.analytics.StrengthForecastPointType
import com.example.liftrix.domain.model.analytics.StrengthForecastRegression
import com.example.liftrix.domain.model.analytics.StrengthForecastResult
import com.example.liftrix.domain.model.analytics.StrengthForecastStatus
import com.example.liftrix.domain.model.analytics.StrengthForecastSummary
import com.example.liftrix.domain.model.analytics.StrengthForecastTrend
import com.example.liftrix.domain.model.analytics.TimeRange
import com.example.liftrix.domain.model.analytics.TimeRangeType
import com.example.liftrix.domain.model.analytics.TrendDirection
import com.example.liftrix.domain.model.analytics.VolumeCalendarData
import com.example.liftrix.domain.model.export.ProgressReportAiSummary
import com.example.liftrix.domain.model.export.ProgressReportConsistencyRow
import com.example.liftrix.domain.model.export.ProgressReportData
import com.example.liftrix.domain.model.export.ProgressReportDateRange
import com.example.liftrix.domain.model.export.ProgressReportMuscleGroupRow
import com.example.liftrix.domain.model.export.ProgressReportPersonalRecordRow
import com.example.liftrix.domain.model.export.ProgressReportRequest
import com.example.liftrix.domain.model.export.ProgressReportResolvedDateRange
import com.example.liftrix.domain.model.export.ProgressReportStrengthForecastSection
import com.example.liftrix.domain.model.export.ProgressReportStrengthRow
import com.example.liftrix.domain.model.export.ProgressReportSummaryMetrics
import com.example.liftrix.domain.model.export.ProgressReportSyncStatus
import com.example.liftrix.domain.model.export.ProgressReportWeeklyVolumeRow
import com.example.liftrix.domain.model.export.ProgressReportWorkoutRow
import com.example.liftrix.domain.progress.ExerciseProgression
import com.example.liftrix.domain.progress.ExerciseRankingData
import com.example.liftrix.domain.progress.FrequencyPoint
import com.example.liftrix.domain.progress.MuscleGroupAnalyticsData
import com.example.liftrix.domain.progress.MuscleGroupDistribution
import com.example.liftrix.domain.progress.OneRmDataPoint
import com.example.liftrix.domain.progress.OneRmProgressionData
import com.example.liftrix.domain.progress.PlateauStatus
import com.example.liftrix.domain.progress.PerformanceTrend
import com.example.liftrix.domain.progress.ProgressDashboardWidgetData
import com.example.liftrix.domain.progress.RankedExercise
import com.example.liftrix.domain.progress.VolumeAnalysisData
import com.example.liftrix.domain.progress.VolumeAnalysisDataPoint
import com.example.liftrix.domain.progress.WorkoutFrequencyData
import com.example.liftrix.domain.repository.DurationDataPoint
import com.example.liftrix.domain.repository.FrequencyDataPoint
import com.example.liftrix.domain.repository.ProgressSummary
import com.example.liftrix.domain.repository.VolumeDataPoint
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.todayIn
import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

@Singleton
class DemoProgressDataFactory @Inject constructor() {
    private companion object {
        const val MAX_MUSCLE_GROUP_REPORT_ROWS = 8
    }

    fun progressReportData(timeline: DemoTimeline, request: ProgressReportRequest): ProgressReportData {
        val range = resolveReportRange(timeline, request.dateRange)
        val workouts = timeline.workouts.filter { workout ->
            val date = workout.date.toJavaLocalDate()
            !date.isBefore(range.start) && !date.isAfter(range.end)
        }
        val activeDays = workouts.map { it.date }.distinct().size
        val totalDays = (ChronoUnit.DAYS.between(range.start, range.end) + 1).toInt().coerceAtLeast(activeDays)
        val restDays = (totalDays - activeDays).coerceAtLeast(0)
        val averageWorkoutsPerWeek = workouts.size / (totalDays.coerceAtLeast(1) / 7.0)
        val strengthRows = buildReportStrengthRows(workouts)
        val positiveStrengthGain = strengthRows.sumOf { it.improvementKg }.takeIf { it > 0.0 }
        val baselineOneRm = workouts
            .flatMap { it.exercises }
            .flatMap { it.sets }
            .maxOfOrNull { estimateOneRmKg(it) }
        val personalRecords = timeline.personalRecords.filter { record ->
            val date = record.date.toJavaLocalDate()
            !date.isBefore(range.start) && !date.isAfter(range.end)
        }
        val weeklyRows = buildReportWeeklyRows(workouts, range)
        val muscleRows = buildReportMuscleRows(workouts)
        val consistencyRows = buildReportConsistencyRows(workouts, range)
        val totalVolume = workouts.sumOf { it.totalVolumeKg }
        val totalReps = workouts.sumOf { it.totalReps }
        val totalSets = workouts.sumOf { it.totalSets }
        val averageDuration = workouts.takeIf { it.isNotEmpty() }?.map { it.durationMinutes }?.average()?.toLong()
        val forecast = strengthForecast(timeline, selectedExerciseId = null, historyDays = 30, forecastDays = 14)
            .exercises
            .firstOrNull { it.status == StrengthForecastStatus.READY }
        val summary = ProgressReportSummaryMetrics(
            workoutsCompleted = workouts.size,
            rawWorkoutEntries = workouts.size,
            totalVolumeKg = totalVolume,
            totalReps = totalReps,
            totalSets = totalSets,
            activeTrainingDays = activeDays,
            restDays = restDays,
            newPersonalRecords = personalRecords.size,
            bestStreakDays = longestStreak(timeline),
            consistencyScore = ((activeDays.toDouble() / totalDays.coerceAtLeast(1) * 40.0) +
                (averageWorkoutsPerWeek / 3.0 * 60.0)).toInt().coerceIn(0, 100),
            consistencyWindowLabel = "Based on ${range.label.lowercase(Locale.ENGLISH)} (${range.start} to ${range.end})",
            mostActiveDay = workouts
                .groupingBy { it.date.toJavaLocalDate().dayOfWeek }
                .eachCount()
                .maxWithOrNull(compareBy<Map.Entry<java.time.DayOfWeek, Int>> { it.value }.thenBy { it.key.value })
                ?.key
                ?.getDisplayName(TextStyle.FULL, Locale.ENGLISH),
            estimatedOneRmImprovementKg = positiveStrengthGain,
            estimatedOneRmBaselineKg = if (positiveStrengthGain == null) baselineOneRm else null,
            oneRmStatusLabel = positiveStrengthGain?.let { "Gain: +${formatDemoDecimal(it, 1)} kg" }
                ?: baselineOneRm?.let { "Baseline: ${formatDemoDecimal(it, 1)} kg" }
                ?: "Log weighted lifts to calculate 1RM progress",
            averageWorkoutsPerWeek = averageWorkoutsPerWeek,
            averageDurationMinutes = averageDuration,
            validDurationWorkoutCount = workouts.size,
            activeWeekAverageWorkouts = workouts
                .groupingBy { it.date.toJavaLocalDate().weekStart() }
                .eachCount()
                .values
                .takeIf { it.isNotEmpty() }
                ?.average(),
            hasUnusuallyHighWorkoutFrequency = averageWorkoutsPerWeek > 7.0
        )

        return ProgressReportData(
            generatedAt = request.generatedAt,
            range = range,
            title = "${range.label} Demo Training Summary",
            summary = summary,
            privacyOptions = request.privacyOptions,
            privacyApplied = buildList {
                add(if (request.privacyOptions.hideBodyweight) "Bodyweight hidden" else "Bodyweight not collected in this report")
                add(if (request.privacyOptions.hidePersonalNotes) "Personal notes hidden" else "Personal notes not collected in this report")
                add(if (request.privacyOptions.hideEmailAccountInfo) "Email and account info hidden" else "Email and account info not collected in this report")
            },
            strengthRows = if (request.includeOptions.strengthProgress) strengthRows else emptyList(),
            weeklyVolumeRows = if (request.includeOptions.volumeAnalysis) weeklyRows else emptyList(),
            muscleGroupRows = if (request.includeOptions.volumeAnalysis) muscleRows else emptyList(),
            consistencyRows = if (request.includeOptions.consistencySummary) consistencyRows else emptyList(),
            personalRecordRows = if (request.includeOptions.personalRecords) {
                personalRecords.sortedByDescending { it.date }.take(18).map { record ->
                    ProgressReportPersonalRecordRow(
                        date = record.date.toJavaLocalDate(),
                        exerciseName = record.exerciseName,
                        recordType = "PR event",
                        newValue = "MAX WEIGHT: ${formatDemoDecimal(record.weightKg, 1)} kg x ${record.reps}",
                        previousValue = "Previous demo best"
                    )
                }
            } else {
                emptyList()
            },
            workoutRows = if (request.includeOptions.detailedWorkoutList) {
                workouts.takeLast(24).map { workout ->
                    ProgressReportWorkoutRow(
                        date = workout.date.toJavaLocalDate(),
                        name = workout.name,
                        durationMinutes = workout.durationMinutes.toLong(),
                        synced = false,
                        workoutId = workout.id,
                        volumeKg = workout.totalVolumeKg,
                        repCount = workout.totalReps,
                        setCount = workout.totalSets
                    )
                }
            } else {
                emptyList()
            },
            aiSummary = if (request.includeOptions.aiCoachInsights) {
                ProgressReportAiSummary(
                    summary = "Demo training data shows ${workouts.size} completed sessions, ${formatDemoDecimal(totalVolume, 0)} kg of weighted volume, and ${personalRecords.size} personal record events in this period.",
                    recommendations = listOf(
                        "Keep progressive overload focused on ${strengthRows.firstOrNull()?.exerciseName ?: "your top compound lifts"} while recovery stays stable.",
                        "Use the latest weekly volume as next week's baseline and add small increases only when form stays strong.",
                        "Review rest days around the heaviest demo sessions before adding more volume."
                    ),
                    contextUsed = listOf("Demo timeline", "Completed workouts", "Set activity", "Personal records")
                )
            } else {
                ProgressReportAiSummary("", emptyList(), emptyList())
            },
            syncStatus = ProgressReportSyncStatus(
                pendingSyncItems = 0,
                lastSyncTimestampMillis = null,
                generatedOffline = true,
                syncedWorkoutCount = 0
            ),
            strengthForecast = if (request.includeOptions.strengthProgress) {
                ProgressReportStrengthForecastSection(
                    generatedForExerciseId = forecast?.exerciseId,
                    generatedForExerciseName = forecast?.exerciseName,
                    forecast = forecast,
                    message = forecast?.summary?.message ?: "Not enough data to generate forecast"
                )
            } else {
                null
            },
            isMinimalReport = workouts.size < 4
        )
    }

    fun volumeData(timeline: DemoTimeline, timeRange: TimeRange): List<VolumeDataPoint> =
        workoutsInRange(timeline, timeRange)
            .groupBy { it.date }
            .toSortedMap()
            .map { (date, workouts) ->
                VolumeDataPoint(
                    date = date,
                    totalVolume = workouts.sumOf { it.totalVolumeKg }.toFloat(),
                    exerciseCount = workouts.sumOf { it.exercises.size }
                )
            }

    fun durationData(timeline: DemoTimeline, timeRange: TimeRange): List<DurationDataPoint> =
        workoutsInRange(timeline, timeRange)
            .groupBy { it.date }
            .toSortedMap()
            .map { (date, workouts) ->
                DurationDataPoint(
                    date = date,
                    durationMinutes = workouts.sumOf { it.durationMinutes },
                    workoutCount = workouts.size
                )
            }

    fun frequencyData(timeline: DemoTimeline, timeRange: TimeRange): List<FrequencyDataPoint> {
        val workoutsByDate = workoutsInRange(timeline, timeRange).groupBy { it.date }
        return workoutsByDate.keys.sorted().map { date ->
            val count = workoutsByDate.getValue(date).size
            FrequencyDataPoint(date = date, workoutCount = count, intensity = count.coerceAtMost(2) / 2f)
        }
    }

    fun volumeCalendarData(timeline: DemoTimeline): VolumeCalendarData {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val monthWorkouts = timeline.workouts.filter { it.date.year == today.year && it.date.month == today.month }
        val rawDailyVolumes = monthWorkouts
            .groupBy { it.date }
            .mapValues { (_, workouts) -> workouts.sumOf { it.totalVolumeKg } }
        val scale = calendarScaleFactor(rawDailyVolumes.values.sum())
        val dailyVolumes = rawDailyVolumes
            .mapValues { (_, volumeKg) -> Volume.fromKilograms(volumeKg * scale) }
        val max = dailyVolumes.values.maxWithOrNull(compareBy { it.kilograms }) ?: Volume.ZERO
        val average = if (dailyVolumes.isNotEmpty()) {
            Volume.fromKilograms(dailyVolumes.values.sumOf { it.kilograms } / dailyVolumes.size)
        } else {
            Volume.ZERO
        }
        return VolumeCalendarData(today.year, today.month, dailyVolumes, max, average)
    }

    fun progressSummary(timeline: DemoTimeline, timeRange: TimeRange): ProgressSummary {
        val workouts = workoutsInRange(timeline, timeRange)
        val totalMinutes = workouts.sumOf { it.durationMinutes }
        return ProgressSummary(
            totalWorkouts = workouts.size,
            totalVolume = workouts.sumOf { it.totalVolumeKg }.toFloat(),
            averageDuration = if (workouts.isNotEmpty()) totalMinutes / workouts.size else 0,
            currentStreak = currentStreak(timeline),
            longestStreak = longestStreak(timeline),
            averageWorkoutsPerWeek = workouts.size / (timeRange.getDurationInWeeks().coerceAtLeast(1)).toFloat(),
            totalActiveTime = totalMinutes
        )
    }

    fun widgetData(timeline: DemoTimeline, widget: AnalyticsWidget): com.example.liftrix.domain.model.analytics.WidgetData {
        val now = kotlinx.datetime.Clock.System.now()
        val recent = timeline.workouts.takeLast(30)
        val totalVolume = recent.sumOf { it.totalVolumeKg }.toFloat()
        val oneRmChartValues = oneRmChartValues(timeline, 14)
        val trend = TrendDirection.UP
        return when (widget) {
            AnalyticsWidget.OneRMProgression -> ChartWidgetData(
                widgetType = widget,
                lastUpdated = now,
                chartType = ChartType.LINE,
                dataPoints = oneRmChartValues.mapIndexed { index, value ->
                    DataPoint(
                        x = index.toFloat(),
                        y = value,
                        label = "PR ${index + 1}",
                        timestamp = kotlinx.datetime.Instant.fromEpochMilliseconds(
                            recent.getOrNull(index)?.endTime?.toEpochMilli() ?: timeline.anchorDate.atStartMillis()
                        )
                    )
                },
                xAxisLabel = "Record",
                yAxisLabel = "1RM",
                timeRange = "Recent",
                summary = ChartSummary(
                    trend,
                    8.5f,
                    oneRmChartValues.lastOrNull() ?: 0f,
                    oneRmChartValues.maxOrNull() ?: 0f,
                    "kg"
                )
            )

            AnalyticsWidget.FrequencyChart,
            AnalyticsWidget.ProgressChart,
            AnalyticsWidget.WorkoutDuration,
            AnalyticsWidget.VolumeAnalytics,
            AnalyticsWidget.StrengthForecast -> ChartWidgetData(
                widgetType = widget,
                lastUpdated = now,
                chartType = ChartType.LINE,
                dataPoints = recent.takeLast(14).mapIndexed { index, workout ->
                    DataPoint(
                        x = index.toFloat(),
                        y = when (widget) {
                            AnalyticsWidget.WorkoutDuration -> workout.durationMinutes.toFloat()
                            AnalyticsWidget.FrequencyChart -> 1f
                            else -> workout.totalVolumeKg.toFloat()
                        },
                        label = workout.date.toString(),
                        timestamp = kotlinx.datetime.Instant.fromEpochMilliseconds(workout.endTime.toEpochMilli())
                    )
                },
                xAxisLabel = "Date",
                yAxisLabel = if (widget == AnalyticsWidget.WorkoutDuration) "Minutes" else "Volume",
                timeRange = "Recent",
                summary = ChartSummary(trend, 12.4f, totalVolume / recent.size.coerceAtLeast(1), recent.maxOfOrNull { it.totalVolumeKg.toFloat() } ?: 0f, "kg")
            )

            AnalyticsWidget.StrengthAnalytics,
            AnalyticsWidget.MonthlySummary,
            AnalyticsWidget.RecentAchievements -> ProgressWidgetData(
                widgetType = widget,
                lastUpdated = now,
                currentValue = totalVolume,
                targetValue = (totalVolume * 1.12f).coerceAtLeast(1f),
                progressPercentage = 82f,
                unit = "kg",
                milestones = timeline.personalRecords.takeLast(4).map {
                    Milestone(it.weightKg.toFloat(), it.exerciseName, true, kotlinx.datetime.Instant.fromEpochMilliseconds(it.date.atStartMillis()))
                },
                recentAchievements = timeline.achievements.takeLast(4).map {
                    Achievement(it.title, it.description, it.value.toInt().toString(), kotlinx.datetime.Instant.fromEpochMilliseconds(it.date.atStartMillis()))
                }
            )

            AnalyticsWidget.MuscleHeatmap -> muscleHeatmap(timeline)

            AnalyticsWidget.ConsistencyScore,
            AnalyticsWidget.WorkoutFrequency,
            AnalyticsWidget.TotalVolume,
            AnalyticsWidget.WorkoutStreak,
            AnalyticsWidget.AverageDuration,
            AnalyticsWidget.VolumeCalendar -> MetricWidgetData(
                widgetType = widget,
                lastUpdated = now,
                primaryValue = when (widget) {
                    AnalyticsWidget.TotalVolume -> totalVolume.toInt().toString()
                    AnalyticsWidget.WorkoutStreak -> currentStreak(timeline).toString()
                    AnalyticsWidget.AverageDuration -> recent.map { it.durationMinutes }.average().toInt().toString()
                    AnalyticsWidget.ConsistencyScore -> "86"
                    else -> recent.size.toString()
                },
                secondaryValue = widget.displayName,
                unit = when (widget) {
                    AnalyticsWidget.TotalVolume -> "kg"
                    AnalyticsWidget.AverageDuration -> "min"
                    AnalyticsWidget.ConsistencyScore -> "%"
                    else -> ""
                },
                trend = trend,
                trendPercentage = 9.5f
            )

            else -> AnalyticsWidgetData(
                widgetType = widget,
                lastUpdated = now,
                insights = listOf(
                    Insight("Training momentum", "Volume and frequency are trending upward", 0.91f, InsightCategory.PERFORMANCE, true)
                ),
                recommendations = listOf(
                    Recommendation("Hold recovery rhythm", "Keep one lower-intensity day after heavy lower sessions", RecommendationPriority.MEDIUM, "Sustained progress")
                ),
                metrics = mapOf(
                    "totalVolume" to totalVolume.toInt().toString(),
                    "workouts" to recent.size.toString(),
                    "prs" to timeline.personalRecords.takeLast(30).size.toString()
                ),
                confidence = 0.93f,
                timeRange = "Recent"
            )
        }
    }

    fun dashboardWidgetData(timeline: DemoTimeline, widget: AnalyticsWidget): ProgressDashboardWidgetData =
        ProgressDashboardWidgetData(
            widgetType = widget,
            data = dashboardDataMap(timeline, widget),
            lastUpdated = System.currentTimeMillis(),
            isStale = false
        )

    fun strengthForecast(
        timeline: DemoTimeline,
        selectedExerciseId: String?,
        historyDays: Int,
        forecastDays: Int
    ): StrengthForecastResult {
        val candidateExercises = timeline.workouts
            .flatMap { it.exercises }
            .groupBy { it.exerciseId }
            .entries
            .sortedByDescending { it.value.size }
            .take(4)

        val forecasts = candidateExercises.map { (exerciseId, blocks) ->
            val exerciseName = blocks.first().name
            val history = timeline.workouts
                .filter { workout -> workout.exercises.any { it.exerciseId == exerciseId } }
                .takeLast(historyDays.coerceAtLeast(14))
                .mapIndexed { index, workout ->
                    val block = workout.exercises.first { it.exerciseId == exerciseId }
                    val topSet = block.sets.maxBy { estimateOneRmKg(it) }
                    StrengthForecastPoint(
                        date = workout.date,
                        estimatedOneRmKg = estimateOneRmKg(topSet),
                        type = StrengthForecastPointType.HISTORICAL,
                        timelineDay = index.toDouble()
                    )
                }
                .recordHighForecastPoints()
            val latestPoint = history.lastOrNull()
            val latest = latestPoint?.estimatedOneRmKg ?: 0.0
            val latestTimelineDay = latestPoint?.timelineDay ?: history.lastIndex.coerceAtLeast(0).toDouble()
            val forecast = (1..forecastDays).map { day ->
                StrengthForecastPoint(
                    date = timeline.anchorDate.plus(kotlinx.datetime.DatePeriod(days = day)),
                    estimatedOneRmKg = latest + day * 0.18,
                    type = StrengthForecastPointType.FORECAST,
                    timelineDay = latestTimelineDay + day
                )
            }
            StrengthExerciseForecast(
                exerciseId = exerciseId,
                exerciseName = exerciseName,
                historicalPoints = history,
                forecastPoints = forecast,
                regression = StrengthForecastRegression(
                    slopeKgPerDay = 0.18,
                    interceptKg = history.firstOrNull()?.estimatedOneRmKg ?: latest,
                    rSquared = 0.82,
                    residualStandardErrorKg = 2.4
                ),
                summary = StrengthForecastSummary(
                    trend = StrengthForecastTrend.IMPROVING,
                    message = "Projected steady strength increase from recent training.",
                    latestEstimatedOneRmKg = latest,
                    projectedEstimatedOneRmKg = forecast.lastOrNull()?.estimatedOneRmKg,
                    projectedChangeKg = forecast.lastOrNull()?.estimatedOneRmKg?.minus(latest),
                    historyDays = historyDays,
                    forecastDays = forecastDays
                ),
                status = if (history.isNotEmpty()) StrengthForecastStatus.READY else StrengthForecastStatus.NO_DATA
            )
        }

        return StrengthForecastResult(
            exercises = forecasts,
            selectedExerciseId = selectedExerciseId ?: forecasts.firstOrNull { it.status == StrengthForecastStatus.READY }?.exerciseId
        )
    }

    fun volumeAnalysisData(
        timeline: DemoTimeline,
        timeRange: TimeRangeType,
        muscleGroupFilter: String?,
        grouping: com.example.liftrix.domain.model.analytics.VolumeGrouping
    ): VolumeAnalysisData {
        val workouts = workoutsInDetailRange(timeline, timeRange)
        val filteredWorkouts = if (muscleGroupFilter.isNullOrBlank()) {
            workouts
        } else {
            workouts.map { workout ->
                workout.copy(exercises = workout.exercises.filter { exercise ->
                    exercise.muscleGroup.equals(muscleGroupFilter, ignoreCase = true)
                })
            }.filter { it.exercises.isNotEmpty() }
        }

        val points = when (grouping) {
            com.example.liftrix.domain.model.analytics.VolumeGrouping.BY_EXERCISE ->
                filteredWorkouts.flatMap { it.exercises }
                    .groupBy { it.name }
                    .map { (name, exercises) ->
                        VolumeAnalysisDataPoint(
                            date = null,
                            volume = exercises.sumOf { it.totalVolumeKg },
                            sets = exercises.sumOf { it.sets.size },
                            exercises = 1,
                            label = name
                        )
                    }
                    .sortedByDescending { it.volume }

            com.example.liftrix.domain.model.analytics.VolumeGrouping.BY_MUSCLE_GROUP ->
                filteredWorkouts.flatMap { it.exercises }
                    .groupBy { it.muscleGroup }
                    .map { (muscleGroup, exercises) ->
                        VolumeAnalysisDataPoint(
                            date = null,
                            volume = exercises.sumOf { it.totalVolumeKg },
                            sets = exercises.sumOf { it.sets.size },
                            exercises = exercises.map { it.exerciseId }.distinct().size,
                            label = muscleGroup
                        )
                    }
                    .sortedByDescending { it.volume }

            com.example.liftrix.domain.model.analytics.VolumeGrouping.BY_SESSION ->
                filteredWorkouts.map { workout ->
                    VolumeAnalysisDataPoint(
                        date = workout.date.toString(),
                        volume = workout.totalVolumeKg,
                        sets = workout.totalSets,
                        exercises = workout.exercises.size,
                        label = workout.name
                    )
                }

            com.example.liftrix.domain.model.analytics.VolumeGrouping.BY_WEEK ->
                filteredWorkouts.groupBy { it.date.weekStart() }
                    .toSortedMap()
                    .map { (date, weekWorkouts) ->
                        VolumeAnalysisDataPoint(
                            date = date.toString(),
                            volume = weekWorkouts.sumOf { it.totalVolumeKg },
                            sets = weekWorkouts.sumOf { it.totalSets },
                            exercises = weekWorkouts.flatMap { it.exercises }.map { it.exerciseId }.distinct().size,
                            label = "Week of $date"
                        )
                    }

            com.example.liftrix.domain.model.analytics.VolumeGrouping.BY_MONTH ->
                filteredWorkouts.groupBy { LocalDate(it.date.year, it.date.monthNumber, 1) }
                    .toSortedMap()
                    .map { (date, monthWorkouts) ->
                        VolumeAnalysisDataPoint(
                            date = date.toString(),
                            volume = monthWorkouts.sumOf { it.totalVolumeKg },
                            sets = monthWorkouts.sumOf { it.totalSets },
                            exercises = monthWorkouts.flatMap { it.exercises }.map { it.exerciseId }.distinct().size,
                            label = "${date.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${date.year}"
                        )
                    }

            com.example.liftrix.domain.model.analytics.VolumeGrouping.TOTAL ->
                filteredWorkouts.groupBy { it.date }
                    .toSortedMap()
                    .map { (date, dayWorkouts) ->
                        VolumeAnalysisDataPoint(
                            date = date.toString(),
                            volume = dayWorkouts.sumOf { it.totalVolumeKg },
                            sets = dayWorkouts.sumOf { it.totalSets },
                            exercises = dayWorkouts.flatMap { it.exercises }.size,
                            label = date.toString()
                        )
                    }
        }

        val total = points.sumOf { it.volume }.toFloat()
        val midpoint = points.size / 2
        val earlier = points.take(midpoint).sumOf { it.volume }.coerceAtLeast(1.0)
        val recent = points.drop(midpoint).sumOf { it.volume }
        val growth = if (points.size > 1) (((recent - earlier) / earlier) * 100.0).toFloat() else 0f

        return VolumeAnalysisData(
            volumeData = points,
            totalVolume = total,
            volumeGrowth = growth,
            averageVolume = if (points.isNotEmpty()) total / points.size else 0f
        )
    }

    fun oneRmProgressionData(
        timeline: DemoTimeline,
        exerciseIds: List<String>?,
        timeRange: TimeRangeType,
        includeEstimated: Boolean
    ): OneRmProgressionData {
        val selectedIds = exerciseIds?.toSet().orEmpty()
        val progressions = workoutsInDetailRange(timeline, timeRange)
            .flatMap { workout ->
                workout.exercises.map { exercise ->
                    workout to exercise
                }
            }
            .filter { (_, exercise) -> selectedIds.isEmpty() || exercise.exerciseId in selectedIds }
            .groupBy { (_, exercise) -> exercise.exerciseId }
            .entries
            .sortedByDescending { it.value.size }
            .take(if (selectedIds.isEmpty()) 6 else selectedIds.size.coerceAtLeast(1))
            .map { (exerciseId, entries) ->
                val exerciseName = entries.first().second.name
                val points = entries.map { (workout, exercise) ->
                    val topSet = exercise.sets.maxBy { estimateOneRmKg(it) }
                    val estimated = estimateOneRmKg(topSet).toFloat()
                    OneRmDataPoint(
                        date = workout.date,
                        exerciseId = exerciseId,
                        exerciseName = exerciseName,
                        actualOneRm = null,
                        estimatedOneRm = if (includeEstimated) estimated else null,
                        weight = topSet.weightKg.toFloat(),
                        reps = topSet.reps,
                        isEstimated = true,
                        oneRmValue = estimated
                    )
                }.recordHighOneRmPoints()
                ExerciseProgression(exerciseId, exerciseName, points)
            }

        return OneRmProgressionData(progressions)
    }

    private fun estimateOneRmKg(set: DemoSet): Double = set.weightKg * (1.0 + set.reps / 30.0)

    private fun resolveReportRange(
        timeline: DemoTimeline,
        requestedRange: ProgressReportDateRange
    ): ProgressReportResolvedDateRange {
        val today = timeline.anchorDate.toJavaLocalDate()
        return when (requestedRange) {
            ProgressReportDateRange.Last30Days -> ProgressReportResolvedDateRange(today.minusDays(29), today, "Last 30 days")
            ProgressReportDateRange.Last6Months -> ProgressReportResolvedDateRange(today.minusMonths(6).plusDays(1), today, "Last 6 months")
            ProgressReportDateRange.AllTime -> ProgressReportResolvedDateRange(
                timeline.workouts.firstOrNull()?.date?.toJavaLocalDate() ?: today.minusDays(29),
                today,
                "All time"
            )
            is ProgressReportDateRange.Custom -> ProgressReportResolvedDateRange(
                requestedRange.start,
                requestedRange.end.coerceAtMost(today),
                "Custom range"
            )
        }
    }

    private fun buildReportStrengthRows(workouts: List<DemoWorkout>): List<ProgressReportStrengthRow> =
        workouts
            .flatMap { workout -> workout.exercises.map { exercise -> workout to exercise } }
            .groupBy { (_, exercise) -> exercise.exerciseId }
            .mapNotNull { (exerciseId, entries) ->
                val estimates = entries.map { (workout, exercise) ->
                    workout.date to exercise.sets.maxOf { estimateOneRmKg(it) }
                }.sortedBy { it.first }
                val start = estimates.firstOrNull()?.second ?: return@mapNotNull null
                val best = estimates.maxOf { it.second }
                val improvement = if (estimates.map { it.first }.distinct().size >= 2) best - start else 0.0
                ProgressReportStrengthRow(
                    exerciseId = exerciseId,
                    exerciseName = entries.first().second.name,
                    startEstimatedOneRmKg = start,
                    bestEstimatedOneRmKg = best,
                    improvementKg = max(0.0, improvement)
                )
            }
            .sortedByDescending { it.improvementKg }
            .take(8)

    private fun buildReportWeeklyRows(
        workouts: List<DemoWorkout>,
        range: ProgressReportResolvedDateRange
    ): List<ProgressReportWeeklyVolumeRow> =
        workouts
            .groupBy { maxOf(it.date.toJavaLocalDate().weekStart(), range.start) }
            .toSortedMap()
            .map { (weekStart, weekWorkouts) ->
                ProgressReportWeeklyVolumeRow(
                    weekLabel = formatDemoWeekLabel(weekStart, range),
                    workoutCount = weekWorkouts.size,
                    activeDays = weekWorkouts.map { it.date }.distinct().size,
                    totalVolumeKg = weekWorkouts.sumOf { it.totalVolumeKg },
                    setCount = weekWorkouts.sumOf { it.totalSets },
                    repCount = weekWorkouts.sumOf { it.totalReps }
                )
            }
            .takeLast(12)

    private fun buildReportMuscleRows(workouts: List<DemoWorkout>): List<ProgressReportMuscleGroupRow> =
        collapseReportMuscleRows(
            workouts
                .flatMap { it.exercises }
                .groupBy { it.muscleGroup }
                .map { (muscleGroup, exercises) ->
                    ProgressReportMuscleGroupRow(
                        muscleGroup = muscleGroup,
                        totalVolumeKg = exercises.sumOf { it.totalVolumeKg },
                        exerciseCount = exercises.map { it.exerciseId }.distinct().size,
                        setCount = exercises.sumOf { it.sets.size },
                        repCount = exercises.sumOf { block -> block.sets.sumOf { it.reps } }
                    )
                }
                .sortedByDescending { it.totalVolumeKg }
        )

    private fun collapseReportMuscleRows(rows: List<ProgressReportMuscleGroupRow>): List<ProgressReportMuscleGroupRow> {
        if (rows.size <= MAX_MUSCLE_GROUP_REPORT_ROWS) return rows

        val visibleRows = rows.take(MAX_MUSCLE_GROUP_REPORT_ROWS - 1)
        val hiddenRows = rows.drop(MAX_MUSCLE_GROUP_REPORT_ROWS - 1)
        return visibleRows + ProgressReportMuscleGroupRow(
            muscleGroup = "Other muscle groups",
            totalVolumeKg = hiddenRows.sumOf { it.totalVolumeKg },
            exerciseCount = hiddenRows.sumOf { it.exerciseCount },
            setCount = hiddenRows.sumOf { it.setCount },
            repCount = hiddenRows.sumOf { it.repCount }
        )
    }

    private fun buildReportConsistencyRows(
        workouts: List<DemoWorkout>,
        range: ProgressReportResolvedDateRange
    ): List<ProgressReportConsistencyRow> {
        val byWeek = workouts.groupBy { maxOf(it.date.toJavaLocalDate().weekStart(), range.start) }
        return generateSequence(range.start) { current ->
            val nextMonday = current.with(TemporalAdjusters.nextOrSame(java.time.DayOfWeek.MONDAY))
            if (nextMonday == current) current.plusWeeks(1) else nextMonday
        }
            .takeWhile { !it.isAfter(range.end) }
            .map { weekStart ->
                val weekWorkouts = byWeek[weekStart].orEmpty()
                ProgressReportConsistencyRow(
                    weekLabel = formatDemoWeekLabel(weekStart, range),
                    workoutCount = weekWorkouts.size,
                    activeDays = weekWorkouts.map { it.date }.distinct().size
                )
            }
            .toList()
            .takeLast(12)
    }

    private fun java.time.LocalDate.weekStart(): java.time.LocalDate =
        with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))

    private fun formatDemoWeekLabel(
        bucketStart: java.time.LocalDate,
        range: ProgressReportResolvedDateRange
    ): String {
        val partialPrefix = if (bucketStart == range.start && bucketStart.weekStart() != bucketStart) "partial " else ""
        return "$partialPrefix${bucketStart.monthValue.toString().padStart(2, '0')}/${bucketStart.dayOfMonth.toString().padStart(2, '0')}"
    }

    private fun formatDemoDecimal(value: Double, digits: Int): String =
        String.format(Locale.ENGLISH, "%.${digits}f", value)

    private fun List<StrengthForecastPoint>.recordHighForecastPoints(): List<StrengthForecastPoint> {
        var best = 0.0
        return mapNotNull { point ->
            if (point.estimatedOneRmKg < best) {
                null
            } else {
                best = point.estimatedOneRmKg
                point
            }
        }
    }

    private fun List<OneRmDataPoint>.recordHighOneRmPoints(): List<OneRmDataPoint> {
        var best = 0f
        return mapNotNull { point ->
            val value = point.oneRmValue ?: point.estimatedOneRm ?: point.actualOneRm ?: return@mapNotNull null
            if (value < best) {
                null
            } else {
                best = value
                point
            }
        }
    }

    fun workoutFrequencyData(timeline: DemoTimeline, timeRange: TimeRangeType): WorkoutFrequencyData {
        val workouts = workoutsInDetailRange(timeline, timeRange)
        val byDate = workouts.groupBy { it.date }.toSortedMap()
        val points = byDate.map { (date, dayWorkouts) ->
            FrequencyPoint(
                date = date,
                workoutCount = dayWorkouts.size,
                dayOfWeek = date.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }
            )
        }
        val daySpan = detailStartDate(timeline, timeRange).daysUntil(timeline.anchorDate).coerceAtLeast(1)
        val dailyAverage = byDate.size / daySpan.toFloat()

        return WorkoutFrequencyData(
            totalWorkouts = workouts.size,
            consistencyScore = 0.86f,
            frequencyPoints = points,
            totalWorkoutDays = byDate.size,
            dailyAverage = dailyAverage,
            currentStreak = currentStreak(timeline),
            longestStreak = longestStreak(timeline),
            weeklyDistribution = workouts.groupingBy {
                it.date.dayOfWeek.name.lowercase().replaceFirstChar { day -> day.uppercase() }
            }.eachCount()
        )
    }

    fun muscleGroupAnalyticsData(
        timeline: DemoTimeline,
        timeRange: TimeRangeType,
        muscleGroup: com.example.liftrix.domain.progress.MuscleGroup?
    ): MuscleGroupAnalyticsData {
        val exercises = workoutsInDetailRange(timeline, timeRange).flatMap { it.exercises }
        val grouped = exercises.groupBy { it.muscleGroup.toProgressMuscleGroupName() }
        val total = grouped.values.flatten().sumOf { it.totalVolumeKg }.coerceAtLeast(1.0)
        val distribution = grouped.entries
            .filter { (name, _) -> muscleGroup == null || name == muscleGroup.name }
            .map { (name, groupExercises) ->
                val volume = groupExercises.sumOf { it.totalVolumeKg }.toFloat()
                MuscleGroupDistribution(
                    muscleGroup = name,
                    volumePercentage = (volume / total.toFloat() * 100f).coerceAtLeast(0f),
                    totalVolume = volume,
                    exerciseCount = groupExercises.map { it.exerciseId }.distinct().size
                )
            }
            .sortedByDescending { it.totalVolume }

        return MuscleGroupAnalyticsData(distribution)
    }

    fun exerciseRankingData(
        timeline: DemoTimeline,
        timeRange: TimeRangeType,
        metric: com.example.liftrix.domain.model.analytics.RankingMetric
    ): ExerciseRankingData {
        val ranked = workoutsInDetailRange(timeline, timeRange)
            .flatMap { workout -> workout.exercises.map { workout to it } }
            .groupBy { (_, exercise) -> exercise.exerciseId }
            .map { (exerciseId, entries) ->
                val exercise = entries.first().second
                val volumes = entries.map { (_, block) -> block.totalVolumeKg }
                val first = volumes.take(volumes.size / 2).sum().coerceAtLeast(1.0)
                val last = volumes.drop(volumes.size / 2).sum()
                val growth = ((last - first) / first) * 100.0
                val maxOneRm = entries.maxOf { (_, block) ->
                    val topSet = block.sets.maxBy { it.weightKg }
                    topSet.weightKg * (1.0 + topSet.reps / 30.0)
                }
                val score = when (metric) {
                    com.example.liftrix.domain.model.analytics.RankingMetric.VOLUME_GROWTH -> growth
                    com.example.liftrix.domain.model.analytics.RankingMetric.STRENGTH_GROWTH -> maxOneRm
                    com.example.liftrix.domain.model.analytics.RankingMetric.FREQUENCY -> entries.map { it.first.date }.distinct().size.toDouble()
                    com.example.liftrix.domain.model.analytics.RankingMetric.CONSISTENCY -> 100.0 - volumes.variancePercent()
                    com.example.liftrix.domain.model.analytics.RankingMetric.RECENT_TREND -> growth
                    com.example.liftrix.domain.model.analytics.RankingMetric.PERFORMANCE_SCORE -> growth + maxOneRm / 10.0
                }

                RankedExercise(
                    rank = 0,
                    exerciseId = exerciseId,
                    exerciseName = exercise.name,
                    performanceScore = score,
                    totalVolume = volumes.sum(),
                    workoutDays = entries.map { it.first.date }.distinct().size,
                    totalSets = entries.sumOf { it.second.sets.size },
                    maxEstimated1RM = maxOneRm,
                    plateauStatus = if (growth > 8.0) PlateauStatus.PROGRESSING else PlateauStatus.STABLE,
                    trend = if (growth > 0.0) PerformanceTrend.IMPROVING else PerformanceTrend.STABLE,
                    recommendations = listOf("Keep progressing ${exercise.name} with controlled weekly volume increases"),
                    muscleGroup = exercise.muscleGroup.toProgressMuscleGroupName()
                )
            }
            .sortedByDescending { it.performanceScore }
            .mapIndexed { index, exercise -> exercise.copy(rank = index + 1) }

        return ExerciseRankingData(
            rankedExercises = ranked,
            topPerformer = ranked.firstOrNull(),
            mostImproved = ranked.maxByOrNull { it.performanceScore },
            needsAttention = ranked.filter { it.plateauStatus == PlateauStatus.STABLE }.take(2),
            overallScore = if (ranked.isNotEmpty()) ranked.map { it.performanceScore }.average() else 0.0,
            timeRange = timeRange,
            isEmpty = ranked.isEmpty()
        )
    }

    fun muscleHeatmapData(
        timeline: DemoTimeline,
        configuration: Map<String, String>
    ): MuscleHeatmapWidgetData = muscleHeatmap(timeline, configuration)

    private fun muscleHeatmap(
        timeline: DemoTimeline,
        configuration: Map<String, String> = emptyMap()
    ): MuscleHeatmapWidgetData {
        val metric = MuscleHeatmapMetric.fromConfig(configuration["metric"])
        val timeRange = TimeRangeType.fromConfig(configuration["timeRange"])
        val byMuscle = workoutsInDetailRange(timeline,  timeRange)
            .flatMap { it.exercises }
            .groupBy { it.muscleGroup }
            .mapValues { (_, blocks) ->
                when (metric) {
                    MuscleHeatmapMetric.SETS -> blocks.sumOf { it.sets.size }.toFloat()
                    MuscleHeatmapMetric.REPS -> blocks.sumOf { block -> block.sets.sumOf { it.reps } }.toFloat()
                    MuscleHeatmapMetric.SESSIONS -> blocks.map { it.exerciseId }.distinct().size.toFloat()
                    MuscleHeatmapMetric.VOLUME -> blocks.sumOf { it.totalVolumeKg }.toFloat()
                }
            }
        val max = byMuscle.values.maxOrNull()?.coerceAtLeast(1f) ?: 1f
        val values = MuscleGroup.getPrimaryMuscleGroups().map { muscleGroup ->
            val raw = byMuscle.entries.firstOrNull { it.key.contains(muscleGroup.displayName, ignoreCase = true) }?.value ?: 0f
            MuscleHeatmapMuscleValue(
                muscleGroup = muscleGroup,
                rawValue = raw,
                normalizedIntensity = (raw / max).coerceIn(0f, 1f),
                displayLabel = muscleGroup.displayName,
                formattedValue = metric.formatValue(raw)
            )
        }
        return MuscleHeatmapWidgetData(
            gender = MuscleHeatmapGender.fromConfig(configuration["gender"]),
            viewSide = MuscleHeatmapViewSide.fromConfig(configuration["viewSide"]),
            timeRange = timeRange,
            metric = metric,
            colorMode = MuscleHeatmapColorMode.fromConfig(configuration["colorMode"]),
            muscleValues = values,
            topTrained = values.sortedByDescending { it.rawValue }.take(3),
            recoveryCandidates = values.sortedBy { it.rawValue }.take(3),
            totalValue = values.sumOf { it.rawValue.toDouble() }.toFloat()
        )
    }

    private fun dashboardDataMap(timeline: DemoTimeline, widget: AnalyticsWidget): Map<String, Any> {
        val recent = timeline.workouts.takeLast(30)
        val totalVolume = recent.sumOf { it.totalVolumeKg }.toFloat()
        val chartVolumes = recent.map { it.totalVolumeKg.toInt() }
        return when (widget) {
            AnalyticsWidget.OneRMProgression -> {
                val chartOneRm = oneRmChartValues(timeline, 14).map { it.toInt() }
                mapOf(
                    "currentOneRm" to (chartOneRm.lastOrNull() ?: 0),
                    "trend" to "up",
                    "chartData" to chartOneRm
                )
            }

            AnalyticsWidget.VolumeAnalytics,
            AnalyticsWidget.VolumeChart -> mapOf(
                "totalVolume" to totalVolume.toInt(),
                "weeklyAverage" to (totalVolume / 4f).toInt(),
                "trend" to "up",
                "chartData" to chartVolumes
            )

            AnalyticsWidget.FrequencyChart -> {
                mapOf(
                    "weeklyFrequency" to (recent.size / 4f).toInt(),
                    "consistency" to "0.86",
                    "totalWorkouts" to recent.size,
                    "chartData" to recent.map { 1 }
                )
            }

            AnalyticsWidget.StrengthAnalytics,
            AnalyticsWidget.StrengthProgress -> mapOf(
                "totalWorkouts" to recent.size,
                "totalVolume" to totalVolume.toInt(),
                "currentStreak" to currentStreak(timeline),
                "strengthScore" to (totalVolume * 0.1f).toInt(),
                "chartData" to chartVolumes
            )

            AnalyticsWidget.ProgressChart,
            AnalyticsWidget.WorkoutDuration -> mapOf(
                "averageDuration" to (recent.sumOf { it.durationMinutes } / recent.size.coerceAtLeast(1)),
                "totalTime" to recent.sumOf { it.durationMinutes },
                "efficiency" to 0.78f,
                "chartData" to recent.map { it.durationMinutes }
            )

            AnalyticsWidget.PersonalRecords,
            AnalyticsWidget.RecentAchievements -> mapOf(
                "totalPRs" to timeline.personalRecords.size,
                "recentPRs" to timeline.personalRecords.takeLast(30).size,
                "thisMonth" to timeline.personalRecords.count { it.date.month == timeline.anchorDate.month }
            )

            AnalyticsWidget.MuscleGroupDistribution -> muscleDistributionMap(timeline)

            AnalyticsWidget.MuscleHeatmap -> {
                val heatmap = muscleHeatmap(timeline)
                mapOf(
                    "muscleValues" to heatmap.muscleValues.map {
                        mapOf(
                            "muscleGroup" to it.muscleGroup.name,
                            "rawValue" to it.rawValue,
                            "normalizedIntensity" to it.normalizedIntensity,
                            "displayLabel" to it.displayLabel,
                            "formattedValue" to it.formattedValue
                        )
                    },
                    "topTrained" to heatmap.topTrained.map { mapOf("muscleGroup" to it.muscleGroup.name, "rawValue" to it.rawValue) },
                    "recoveryCandidates" to heatmap.recoveryCandidates.map { mapOf("muscleGroup" to it.muscleGroup.name, "rawValue" to it.rawValue) },
                    "totalValue" to heatmap.totalValue,
                    "timeRange" to "MONTH"
                )
            }

            AnalyticsWidget.ConsistencyScore -> mapOf(
                "consistencyScore" to 86,
                "totalWorkouts" to recent.size,
                "activeDays" to recent.map { it.date }.distinct().size,
                "chartData" to recent.map { 1 }
            )

            AnalyticsWidget.ProgressiveOverload -> mapOf(
                "volumeGrowth" to 12.4f,
                "progressionRate" to "good",
                "totalVolume" to totalVolume.toInt(),
                "chartData" to chartVolumes
            )

            else -> mapOf(
                "value" to recent.size.toString(),
                "subtitle" to "${widget.displayName} data",
                "trend" to "up",
                "lastUpdated" to System.currentTimeMillis(),
                "chartData" to chartVolumes
            )
        }
    }

    private fun oneRmChartValues(timeline: DemoTimeline, count: Int): List<Float> {
        val workoutBestEstimates = timeline.workouts
            .mapNotNull { workout ->
                workout.exercises
                    .flatMap { it.sets }
                    .maxOfOrNull { estimateOneRmKg(it) }
                    ?.toFloat()
            }
            .takeLast(count)
        var best = 0f
        return workoutBestEstimates.mapNotNull { estimate ->
            if (estimate < best) {
                null
            } else {
                best = estimate
                estimate
            }
        }
    }

    private fun muscleDistributionMap(timeline: DemoTimeline): Map<String, Any> {
        val byMuscle = timeline.workouts.takeLast(30)
            .flatMap { it.exercises }
            .groupBy { it.muscleGroup.lowercase() }
            .mapValues { (_, blocks) -> blocks.sumOf { it.totalVolumeKg } }
        val total = byMuscle.values.sum().coerceAtLeast(1.0)
        return mapOf(
            "chest" to percentage(byMuscle, total, "chest"),
            "back" to percentage(byMuscle, total, "back"),
            "legs" to percentage(byMuscle, total, "quadriceps", "hamstrings", "glutes", "calves"),
            "shoulders" to percentage(byMuscle, total, "shoulders"),
            "arms" to percentage(byMuscle, total, "biceps", "triceps"),
            "core" to "6.0"
        )
    }

    private fun workoutsInRange(timeline: DemoTimeline, timeRange: TimeRange): List<DemoWorkout> {
        val start = timeRange.startDate.toLocalDate()
        val end = timeRange.endDate.toLocalDate()
        return timeline.workouts.filter { it.date in start..end }
    }

    private fun currentStreak(timeline: DemoTimeline): Int {
        val workoutDates = timeline.workouts.map { it.date }.toSet()
        var date = timeline.anchorDate
        var streak = 0
        while (date in workoutDates) {
            streak += 1
            date = date.minus(kotlinx.datetime.DatePeriod(days = 1))
        }
        return streak
    }

    private fun longestStreak(timeline: DemoTimeline): Int {
        val dates = timeline.workouts.map { it.date }.distinct().sorted()
        var longest = 0
        var current = 0
        var previous: LocalDate? = null
        dates.forEach { date ->
            current = if (previous != null && previous!!.plus(kotlinx.datetime.DatePeriod(days = 1)) == date) current + 1 else 1
            longest = maxOf(longest, current)
            previous = date
        }
        return longest
    }
}

private fun percentage(values: Map<String, Double>, total: Double, vararg keys: String): String {
    val value = values.filterKeys { key -> keys.any { key.contains(it) } }.values.sum()
    return String.format("%.1f", (value / total * 100.0).coerceAtLeast(0.0))
}

private fun workoutsInDetailRange(timeline: DemoTimeline, timeRange: TimeRangeType): List<DemoWorkout> {
    val startDate = detailStartDate(timeline, timeRange)
    return timeline.workouts.filter { it.date in startDate..timeline.anchorDate }
}

private fun detailStartDate(timeline: DemoTimeline, timeRange: TimeRangeType): LocalDate =
    when (timeRange) {
        TimeRangeType.MONTH -> timeline.anchorDate.minus(kotlinx.datetime.DatePeriod(days = 29))
        TimeRangeType.SIX_MONTHS -> timeline.anchorDate.minus(kotlinx.datetime.DatePeriod(days = 179))
        TimeRangeType.ALL_TIME -> timeline.workouts.firstOrNull()?.date ?: timeline.anchorDate
    }

private fun LocalDate.weekStart(): LocalDate =
    minus(kotlinx.datetime.DatePeriod(days = dayOfWeek.isoDayNumber - 1))

private fun String.toProgressMuscleGroupName(): String =
    when {
        equals("Chest", ignoreCase = true) -> "CHEST"
        equals("Back", ignoreCase = true) -> "BACK"
        equals("Shoulders", ignoreCase = true) -> "SHOULDERS"
        equals("Biceps", ignoreCase = true) || equals("Triceps", ignoreCase = true) -> "ARMS"
        equals("Quadriceps", ignoreCase = true) || equals("Hamstrings", ignoreCase = true) || equals("Calves", ignoreCase = true) -> "LEGS"
        equals("Glutes", ignoreCase = true) -> "GLUTES"
        equals("Core", ignoreCase = true) -> "CORE"
        else -> "OTHER"
    }

private fun List<Double>.variancePercent(): Double {
    if (isEmpty()) return 0.0
    val mean = average().coerceAtLeast(1.0)
    return map { kotlin.math.abs(it - mean) }.average() / mean * 100.0
}

private fun calendarScaleFactor(totalVolumeKg: Double): Double =
    if (totalVolumeKg > DEMO_CALENDAR_TOTAL_VOLUME_CAP_KG) {
        DEMO_CALENDAR_TOTAL_VOLUME_CAP_KG / totalVolumeKg
    } else {
        1.0
    }

private const val DEMO_CALENDAR_TOTAL_VOLUME_CAP_KG = Volume.MAX_VOLUME_KG * 0.92

private fun java.util.Date.toLocalDate(): LocalDate =
    Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).toLocalDate().let {
        LocalDate(it.year, it.monthValue, it.dayOfMonth)
    }

private fun LocalDate.atStartMillis(): Long =
    toJavaLocalDate().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
