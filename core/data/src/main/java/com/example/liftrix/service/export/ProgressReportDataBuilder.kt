package com.example.liftrix.service.export

import com.example.liftrix.data.local.dao.ExerciseSetDao
import com.example.liftrix.data.local.dao.PersonalRecordDao
import com.example.liftrix.data.local.dao.SyncPreferencesDao
import com.example.liftrix.data.local.dao.SyncQueueDao
import com.example.liftrix.data.local.dao.WorkoutDao
import com.example.liftrix.data.local.entity.WorkoutEntity
import com.example.liftrix.domain.model.export.ProgressReportAiSummary
import com.example.liftrix.domain.model.export.ProgressReportConsistencyRow
import com.example.liftrix.domain.model.export.ProgressReportData
import com.example.liftrix.domain.model.export.ProgressReportDateRange
import com.example.liftrix.domain.model.export.ProgressReportMuscleGroupRow
import com.example.liftrix.domain.model.export.ProgressReportPersonalRecordRow
import com.example.liftrix.domain.model.export.ProgressReportPrivacyOptions
import com.example.liftrix.domain.model.export.ProgressReportRequest
import com.example.liftrix.domain.model.export.ProgressReportResolvedDateRange
import com.example.liftrix.domain.model.export.ProgressReportStrengthRow
import com.example.liftrix.domain.model.export.ProgressReportSummaryMetrics
import com.example.liftrix.domain.model.export.ProgressReportSyncStatus
import com.example.liftrix.domain.model.export.ProgressReportWeeklyVolumeRow
import com.example.liftrix.domain.model.export.ProgressReportWorkoutRow
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.roundToInt

@Singleton
class ProgressReportDataBuilder @Inject constructor(
    private val workoutDao: WorkoutDao,
    private val exerciseSetDao: ExerciseSetDao,
    private val personalRecordDao: PersonalRecordDao,
    private val syncQueueDao: SyncQueueDao,
    private val syncPreferencesDao: SyncPreferencesDao
) {
    suspend fun build(userId: String, request: ProgressReportRequest): ProgressReportData {
        val today = request.generatedAt.toLocalDate()
        val range = resolveRange(userId, request.dateRange, today)
        val start = range.start.toString()
        val end = range.end.toString()
        val workouts = workoutDao.getCompletedWorkoutsInDateRangeForUser(userId, start, end)
        val dailyVolume = exerciseSetDao.getDailyVolumeData(userId, start, end)
        val muscleGroups = exerciseSetDao.getVolumeDataByMuscleGroup(userId, start, end)
        val oneRmData = exerciseSetDao.getAllOneRmData(userId, start, end)
        val performanceHistory = exerciseSetDao.getExercisePerformanceHistory(userId, start, end)
        val prs = personalRecordDao.getPRsInDateRange(
            userId = userId,
            startDate = range.start.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            endDate = range.end.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
        )
        val pendingSync = syncQueueDao.getPendingItemsCount(userId)
        val lastSync = syncPreferencesDao.getLastSyncTimestamp(userId)

        val totalVolume = dailyVolume.sumOf { it.total_volume }
        val activeDays = workouts.map { it.date }.distinct().size
        val bestStreak = calculateBestStreak(workouts)
        val avgWorkoutsPerWeek = calculateAverageWorkoutsPerWeek(workouts.size, range)
        val averageDuration = workouts.mapNotNull { durationMinutes(it) }.takeIf { it.isNotEmpty() }?.average()?.roundToInt()?.toLong()
        val totalDays = (ChronoUnit.DAYS.between(range.start, range.end) + 1).toInt().coerceAtLeast(activeDays)
        val restDays = (totalDays - activeDays).coerceAtLeast(0)
        val consistencyScore = calculateConsistencyScore(activeDays, totalDays, avgWorkoutsPerWeek)
        val mostActiveDay = findMostActiveDay(workouts)
        val strengthRows = buildStrengthRows(oneRmData, performanceHistory)
        val weeklyVolumeRows = buildWeeklyVolumeRows(dailyVolume, workouts)
        val consistencyRows = buildConsistencyRows(workouts, range)
        val privacyApplied = buildPrivacyApplied(request.privacyOptions)
        val prRows = prs.take(18).map { record ->
            ProgressReportPersonalRecordRow(
                date = Instant.ofEpochMilli(record.achievedAt).atZone(ZoneId.systemDefault()).toLocalDate(),
                exerciseName = record.exerciseName,
                recordType = record.prType.replace('_', ' '),
                newValue = formatRecordValue(record.prType, record.estimatedOneRM, record.volume, record.weightKg, record.reps),
                previousValue = record.previousBest?.let { "%.1f".format(it) }
            )
        }
        val workoutRows = workouts.takeLast(24).map { workout ->
            ProgressReportWorkoutRow(
                date = workout.date,
                name = workout.name,
                durationMinutes = durationMinutes(workout),
                synced = workout.isSynced
            )
        }
        val summary = ProgressReportSummaryMetrics(
            workoutsCompleted = workouts.size,
            totalVolumeKg = totalVolume,
            activeTrainingDays = activeDays,
            restDays = restDays,
            newPersonalRecords = prs.size,
            bestStreakDays = bestStreak,
            consistencyScore = consistencyScore,
            mostActiveDay = mostActiveDay,
            estimatedOneRmImprovementKg = strengthRows.sumOf { it.improvementKg }.takeIf { it > 0.0 },
            averageWorkoutsPerWeek = avgWorkoutsPerWeek,
            averageDurationMinutes = averageDuration
        )

        return ProgressReportData(
            generatedAt = request.generatedAt,
            range = range,
            title = "${range.label} Training Summary",
            summary = summary,
            privacyOptions = request.privacyOptions,
            privacyApplied = privacyApplied,
            strengthRows = if (request.includeOptions.strengthProgress) strengthRows else emptyList(),
            weeklyVolumeRows = if (request.includeOptions.volumeAnalysis) weeklyVolumeRows else emptyList(),
            muscleGroupRows = if (request.includeOptions.volumeAnalysis) muscleGroups.take(8).map {
                ProgressReportMuscleGroupRow(
                    muscleGroup = it.primary_muscle_group,
                    totalVolumeKg = it.total_volume,
                    exerciseCount = it.exercise_count,
                    setCount = it.total_sets
                )
            } else emptyList(),
            consistencyRows = if (request.includeOptions.consistencySummary) consistencyRows else emptyList(),
            personalRecordRows = if (request.includeOptions.personalRecords) prRows else emptyList(),
            workoutRows = if (request.includeOptions.detailedWorkoutList) workoutRows else emptyList(),
            aiSummary = if (request.includeOptions.aiCoachInsights) buildAiSummary(summary, strengthRows, weeklyVolumeRows, prs.size) else ProgressReportAiSummary("", emptyList(), emptyList()),
            syncStatus = ProgressReportSyncStatus(
                pendingSyncItems = pendingSync,
                lastSyncTimestampMillis = lastSync,
                generatedOffline = true
            ),
            isMinimalReport = workouts.size < 4
        )
    }

    private suspend fun resolveRange(
        userId: String,
        requestedRange: ProgressReportDateRange,
        today: LocalDate
    ): ProgressReportResolvedDateRange {
        return when (requestedRange) {
            ProgressReportDateRange.Last30Days -> ProgressReportResolvedDateRange(
                start = today.minusDays(29),
                end = today,
                label = "Last 30 days"
            )
            ProgressReportDateRange.Last6Months -> ProgressReportResolvedDateRange(
                start = today.minusMonths(6).plusDays(1),
                end = today,
                label = "Last 6 months"
            )
            ProgressReportDateRange.AllTime -> {
                val allWorkouts = workoutDao.getCompletedWorkoutsForStats(userId, limit = 10000)
                val start = allWorkouts.minOfOrNull { it.date } ?: today.minusDays(29)
                ProgressReportResolvedDateRange(start = start, end = today, label = "All time")
            }
            is ProgressReportDateRange.Custom -> ProgressReportResolvedDateRange(
                start = requestedRange.start,
                end = requestedRange.end.coerceAtMost(today),
                label = "Custom range"
            )
        }
    }

    private fun buildStrengthRows(
        oneRmData: List<com.example.liftrix.data.local.dao.OneRmResult>,
        performanceHistory: List<com.example.liftrix.data.local.dao.ExercisePerformanceHistoryResult>
    ): List<ProgressReportStrengthRow> {
        val firstByExercise = performanceHistory.groupBy { it.exercise_library_id }
        return oneRmData
            .groupBy { it.exercise_library_id }
            .mapNotNull { (exerciseId, rows) ->
                val best = rows.maxOfOrNull { it.estimated_one_rm } ?: return@mapNotNull null
                val start = firstByExercise[exerciseId]?.minByOrNull { it.date }?.max_estimated_one_rm
                    ?: rows.minByOrNull { it.completed_at }?.estimated_one_rm
                    ?: return@mapNotNull null
                val improvement = best - start
                if (improvement <= 0.0) return@mapNotNull null
                ProgressReportStrengthRow(
                    exerciseId = exerciseId,
                    exerciseName = exerciseId,
                    startEstimatedOneRmKg = start,
                    bestEstimatedOneRmKg = best,
                    improvementKg = improvement
                )
            }
            .sortedByDescending { it.improvementKg }
            .take(8)
    }

    private fun buildWeeklyVolumeRows(
        dailyVolume: List<com.example.liftrix.data.local.dao.DailyVolumeResult>,
        workouts: List<WorkoutEntity>
    ): List<ProgressReportWeeklyVolumeRow> {
        val workoutsByWeek = workouts.groupBy { weekStart(it.date) }
        return dailyVolume
            .groupBy { weekStart(LocalDate.parse(it.date)) }
            .map { (weekStart, rows) ->
                ProgressReportWeeklyVolumeRow(
                    weekLabel = formatWeekLabel(weekStart),
                    workoutCount = workoutsByWeek[weekStart]?.size ?: 0,
                    totalVolumeKg = rows.sumOf { it.total_volume },
                    setCount = rows.sumOf { it.total_sets }
                )
            }
            .sortedBy { it.weekLabel }
            .takeLast(12)
    }

    private fun buildConsistencyRows(
        workouts: List<WorkoutEntity>,
        range: ProgressReportResolvedDateRange
    ): List<ProgressReportConsistencyRow> {
        val firstWeek = weekStart(range.start)
        val lastWeek = weekStart(range.end)
        val workoutsByWeek = workouts.groupBy { weekStart(it.date) }
        return generateSequence(firstWeek) { it.plusWeeks(1) }
            .takeWhile { !it.isAfter(lastWeek) }
            .map { week ->
                val weekWorkouts = workoutsByWeek[week].orEmpty()
                ProgressReportConsistencyRow(
                    weekLabel = formatWeekLabel(week),
                    workoutCount = weekWorkouts.size,
                    activeDays = weekWorkouts.map { it.date }.distinct().size
                )
            }
            .toList()
            .takeLast(12)
    }

    private fun calculateBestStreak(workouts: List<WorkoutEntity>): Int {
        val dates = workouts.map { it.date }.distinct().sorted()
        if (dates.isEmpty()) return 0
        var best = 1
        var current = 1
        dates.zipWithNext().forEach { (previous, next) ->
            current = if (ChronoUnit.DAYS.between(previous, next) == 1L) current + 1 else 1
            best = max(best, current)
        }
        return best
    }

    private fun calculateAverageWorkoutsPerWeek(workoutCount: Int, range: ProgressReportResolvedDateRange): Double {
        val days = ChronoUnit.DAYS.between(range.start, range.end).coerceAtLeast(1) + 1
        return workoutCount / (days / 7.0)
    }

    private fun calculateConsistencyScore(activeDays: Int, totalDays: Int, averageWorkoutsPerWeek: Double): Int {
        if (totalDays <= 0 || activeDays <= 0) return 0
        val activeDayScore = activeDays.toDouble() / totalDays * 100.0
        val frequencyScore = (averageWorkoutsPerWeek / 3.0 * 100.0).coerceAtMost(100.0)
        return ((activeDayScore * 0.4) + (frequencyScore * 0.6)).roundToInt().coerceIn(0, 100)
    }

    private fun findMostActiveDay(workouts: List<WorkoutEntity>): String? {
        val day = workouts
            .groupingBy { it.date.dayOfWeek }
            .eachCount()
            .maxWithOrNull(compareBy<Map.Entry<DayOfWeek, Int>> { it.value }.thenBy { it.key.value })
            ?.key
            ?: return null
        return day.getDisplayName(TextStyle.FULL, Locale.getDefault())
    }

    private fun buildPrivacyApplied(privacyOptions: ProgressReportPrivacyOptions): List<String> {
        return buildList {
            add(if (privacyOptions.hideBodyweight) "Bodyweight hidden" else "Bodyweight not collected in this report")
            add(if (privacyOptions.hidePersonalNotes) "Personal notes hidden" else "Personal notes not collected in this report")
            add(if (privacyOptions.hideEmailAccountInfo) "Email and account info hidden" else "Email and account info not collected in this report")
        }
    }

    private fun durationMinutes(workout: WorkoutEntity): Long? {
        val start = workout.startTime ?: return null
        val end = workout.endTime ?: return null
        return ChronoUnit.MINUTES.between(start, end).takeIf { it > 0 }
    }

    private fun buildAiSummary(
        summary: ProgressReportSummaryMetrics,
        strengthRows: List<ProgressReportStrengthRow>,
        weeklyVolumeRows: List<ProgressReportWeeklyVolumeRow>,
        personalRecordCount: Int
    ): ProgressReportAiSummary {
        val hasDetailedContext = summary.workoutsCompleted >= 4 ||
            (summary.workoutsCompleted >= 2 && (strengthRows.isNotEmpty() || weeklyVolumeRows.isNotEmpty() || personalRecordCount > 0))
        if (!hasDetailedContext) {
            return ProgressReportAiSummary(
                summary = ProgressReportAiSummary.INSUFFICIENT_HISTORY_MESSAGE,
                recommendations = emptyList(),
                contextUsed = listOf("Completed workouts", "Local training volume")
            )
        }

        val recommendation = when {
            summary.averageWorkoutsPerWeek < 2.0 -> "Plan two or three repeatable training days next week to improve consistency."
            strengthRows.isNotEmpty() -> "Keep progressive overload focused on ${strengthRows.first().exerciseName} while recovery is stable."
            summary.totalVolumeKg > 0.0 -> "Use the latest weekly volume as next week's baseline and add small increases only when form stays strong."
            else -> "Keep logging complete workouts so the report can identify clearer training patterns."
        }

        return ProgressReportAiSummary(
            summary = "Your local training data shows ${summary.workoutsCompleted} completed workouts, ${"%.0f".format(summary.totalVolumeKg)} kg of recorded volume, and ${summary.newPersonalRecords} new personal records in this period.",
            recommendations = listOf(
                recommendation,
                "Review rest days around your hardest sessions before adding more volume.",
                "Prioritize consistent logging for sets, reps, and weights to improve future report accuracy."
            ).take(3),
            contextUsed = listOf("Completed workouts", "Set volume", "Personal records", "Sync status")
        )
    }

    private fun formatRecordValue(
        type: String,
        estimatedOneRm: Double?,
        volume: Double?,
        weight: Double?,
        reps: Int
    ): String {
        return when (type) {
            "ONE_RM" -> "${"%.1f".format(estimatedOneRm ?: weight ?: 0.0)} kg"
            "VOLUME" -> "${"%.0f".format(volume ?: 0.0)} kg"
            "MAX_WEIGHT" -> "${"%.1f".format(weight ?: 0.0)} kg"
            else -> "$reps reps"
        }
    }

    private fun weekStart(date: LocalDate): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    private fun formatWeekLabel(weekStart: LocalDate): String =
        "${weekStart.monthValue.toString().padStart(2, '0')}/${weekStart.dayOfMonth.toString().padStart(2, '0')}"
}
