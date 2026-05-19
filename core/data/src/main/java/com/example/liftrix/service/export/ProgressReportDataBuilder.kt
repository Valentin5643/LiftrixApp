package com.example.liftrix.service.export

import com.example.liftrix.data.local.dao.ExerciseSetDao
import com.example.liftrix.data.local.dao.ExerciseLibraryDao
import com.example.liftrix.data.local.dao.PersonalRecordDao
import com.example.liftrix.data.local.dao.SyncPreferencesDao
import com.example.liftrix.data.local.dao.SyncQueueDao
import com.example.liftrix.data.local.dao.WorkoutDao
import com.example.liftrix.data.local.entity.PersonalRecordEntity
import com.example.liftrix.data.local.entity.WorkoutEntity
import com.example.liftrix.domain.model.Equipment
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
    private val exerciseLibraryDao: ExerciseLibraryDao,
    private val personalRecordDao: PersonalRecordDao,
    private val syncQueueDao: SyncQueueDao,
    private val syncPreferencesDao: SyncPreferencesDao
) {
    private companion object {
        const val MAX_REPS_FOR_RELIABLE_ONE_RM = 10
        const val UNUSUALLY_HIGH_WORKOUTS_PER_WEEK = 7.0
        val REPORT_LOCALE: Locale = Locale.ENGLISH
    }

    suspend fun build(userId: String, request: ProgressReportRequest): ProgressReportData {
        val today = request.generatedAt.toLocalDate()
        val range = resolveRange(userId, request.dateRange, today)
        val start = range.start.toString()
        val end = range.end.toString()
        val workouts = workoutDao.getCompletedWorkoutsInDateRangeForUser(userId, start, end)
        val reportSessions = groupReportSessions(workouts)
        val dailyVolume = exerciseSetDao.getDailyVolumeData(userId, start, end)
        val dailyRepActivity = exerciseSetDao.getDailyRepActivityData(userId, start, end)
        val muscleGroups = exerciseSetDao.getVolumeDataByMuscleGroup(userId, start, end)
        val muscleGroupRepActivity = exerciseSetDao.getRepActivityByMuscleGroup(userId, start, end)
        val oneRmData = exerciseSetDao.getAllOneRmData(userId, start, end)
        val performanceHistory = exerciseSetDao.getExercisePerformanceHistory(userId, start, end)
        val prs = personalRecordDao.getPRsInDateRange(
            userId = userId,
            startDate = range.start.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            endDate = range.end.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
        )
        val pendingSync = syncQueueDao.getPendingItemsCount(userId)
        val lastSync = syncPreferencesDao.getLastSyncTimestamp(userId)
        val syncedWorkoutCount = workouts.count { it.isSynced }
        val effectiveLastSync = lastSync ?: workouts
            .filter { it.isSynced }
            .maxOfOrNull { workout ->
                workout.syncVersion.takeIf { it > 0L } ?: workout.updatedAt.toEpochMilli()
            }

        val totalVolume = dailyVolume.sumOf { it.total_volume }
        val totalReps = dailyRepActivity.sumOf { it.total_reps }
        val totalSets = dailyRepActivity.sumOf { it.total_sets }
        val activeDays = reportSessions.map { it.date }.distinct().size
        val bestStreak = calculateBestStreak(reportSessions)
        val avgWorkoutsPerWeek = calculateAverageWorkoutsPerWeek(reportSessions.size, range)
        val activeWeekAverageWorkouts = calculateActiveWeekAverageWorkouts(reportSessions)
        val validDurations = workouts.mapNotNull { durationMinutes(it) }
        val averageDuration = validDurations.takeIf { it.isNotEmpty() }?.average()?.roundToInt()?.toLong()
        val totalDays = (ChronoUnit.DAYS.between(range.start, range.end) + 1).toInt().coerceAtLeast(activeDays)
        val restDays = (totalDays - activeDays).coerceAtLeast(0)
        val consistencyScore = calculateConsistencyScore(activeDays, totalDays, avgWorkoutsPerWeek)
        val mostActiveDay = findMostActiveDay(reportSessions)
        val strengthRows = buildStrengthRows(oneRmData, performanceHistory)
        val oneRmStatus = buildOneRmStatus(strengthRows, oneRmData)
        val weeklyVolumeRows = buildWeeklyVolumeRows(dailyVolume, dailyRepActivity, reportSessions, range)
        val consistencyRows = buildConsistencyRows(reportSessions, range)
        val privacyApplied = buildPrivacyApplied(request.privacyOptions)
        val prExerciseNames = prs.map { it.exerciseName }.distinct()
        val bodyweightExerciseNames = if (prExerciseNames.isEmpty()) {
            emptySet()
        } else {
            exerciseLibraryDao
                .getExercisesByNames(prExerciseNames)
                .filter { it.equipment == Equipment.BODYWEIGHT_ONLY }
                .mapTo(mutableSetOf()) { it.name.normalizedNameKey() }
        }
        val filteredPrs = filterReportPersonalRecords(prs, bodyweightExerciseNames)
        val prRows = buildPersonalRecordRows(filteredPrs)
        val workoutRows = workouts.takeLast(24).map { workout ->
            ProgressReportWorkoutRow(
                date = workout.date,
                name = workout.name,
                durationMinutes = durationMinutes(workout),
                synced = workout.isSynced
            )
        }
        val summary = ProgressReportSummaryMetrics(
            workoutsCompleted = reportSessions.size,
            rawWorkoutEntries = workouts.size,
            totalVolumeKg = totalVolume,
            totalReps = totalReps,
            totalSets = totalSets,
            activeTrainingDays = activeDays,
            restDays = restDays,
            newPersonalRecords = prRows.size,
            bestStreakDays = bestStreak,
            consistencyScore = consistencyScore,
            consistencyWindowLabel = "Based on ${range.label.lowercase(REPORT_LOCALE)} (${range.start} to ${range.end})",
            mostActiveDay = mostActiveDay,
            estimatedOneRmImprovementKg = oneRmStatus.improvementKg,
            estimatedOneRmBaselineKg = oneRmStatus.baselineKg,
            oneRmStatusLabel = oneRmStatus.label,
            averageWorkoutsPerWeek = avgWorkoutsPerWeek,
            averageDurationMinutes = averageDuration,
            validDurationWorkoutCount = validDurations.size,
            activeWeekAverageWorkouts = activeWeekAverageWorkouts,
            hasUnusuallyHighWorkoutFrequency = avgWorkoutsPerWeek > UNUSUALLY_HIGH_WORKOUTS_PER_WEEK ||
                (activeWeekAverageWorkouts?.let { it > UNUSUALLY_HIGH_WORKOUTS_PER_WEEK } == true)
        )
        val muscleGroupRows = buildMuscleGroupRows(muscleGroups, muscleGroupRepActivity)

        return ProgressReportData(
            generatedAt = request.generatedAt,
            range = range,
            title = "${range.label} Training Summary",
            summary = summary,
            privacyOptions = request.privacyOptions,
            privacyApplied = privacyApplied,
            strengthRows = if (request.includeOptions.strengthProgress) strengthRows else emptyList(),
            weeklyVolumeRows = if (request.includeOptions.volumeAnalysis) weeklyVolumeRows else emptyList(),
            muscleGroupRows = if (request.includeOptions.volumeAnalysis) muscleGroupRows else emptyList(),
            consistencyRows = if (request.includeOptions.consistencySummary) consistencyRows else emptyList(),
            personalRecordRows = if (request.includeOptions.personalRecords) prRows else emptyList(),
            workoutRows = if (request.includeOptions.detailedWorkoutList) workoutRows else emptyList(),
            aiSummary = if (request.includeOptions.aiCoachInsights) buildAiSummary(summary, strengthRows, weeklyVolumeRows, prRows.size) else ProgressReportAiSummary("", emptyList(), emptyList()),
            syncStatus = ProgressReportSyncStatus(
                pendingSyncItems = pendingSync,
                lastSyncTimestampMillis = effectiveLastSync,
                generatedOffline = true,
                syncedWorkoutCount = syncedWorkoutCount
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
                val historyRows = firstByExercise[exerciseId].orEmpty()
                val distinctSessions = (historyRows.map { it.date } + rows.map { epochDay(it.completed_at).toString() }).distinct().size
                val best = rows.maxOfOrNull { it.estimated_one_rm } ?: return@mapNotNull null
                val start = historyRows.minByOrNull { it.date }?.max_estimated_one_rm
                    ?: rows.minByOrNull { it.completed_at }?.estimated_one_rm
                    ?: return@mapNotNull null
                val improvement = best - start
                if (distinctSessions >= 2 && improvement <= 0.0) return@mapNotNull null
                ProgressReportStrengthRow(
                    exerciseId = exerciseId,
                    exerciseName = rows.firstOrNull()?.exercise_name
                        ?: historyRows.firstOrNull()?.exercise_name
                        ?: exerciseId,
                    startEstimatedOneRmKg = start,
                    bestEstimatedOneRmKg = best,
                    improvementKg = if (distinctSessions < 2) 0.0 else improvement
                )
            }
            .sortedByDescending { it.improvementKg }
            .take(8)
    }

    private fun buildOneRmStatus(
        strengthRows: List<ProgressReportStrengthRow>,
        oneRmData: List<com.example.liftrix.data.local.dao.OneRmResult>
    ): OneRmStatus {
        val positiveImprovement = strengthRows.sumOf { it.improvementKg }.takeIf { it > 0.0 }
        if (positiveImprovement != null) {
            return OneRmStatus(
                label = "Gain: +${formatDecimal(positiveImprovement, 1)} kg",
                improvementKg = positiveImprovement,
                baselineKg = null
            )
        }

        val baseline = oneRmData.maxOfOrNull { it.estimated_one_rm }
        return if (baseline != null) {
            OneRmStatus(
                label = "Baseline: ${formatDecimal(baseline, 1)} kg",
                improvementKg = null,
                baselineKg = baseline
            )
        } else {
            OneRmStatus(
                label = "Log weighted lifts to calculate 1RM progress",
                improvementKg = null,
                baselineKg = null
            )
        }
    }

    private fun buildWeeklyVolumeRows(
        dailyVolume: List<com.example.liftrix.data.local.dao.DailyVolumeResult>,
        dailyRepActivity: List<com.example.liftrix.data.local.dao.DailyRepActivityResult>,
        sessions: List<ReportSession>,
        range: ProgressReportResolvedDateRange
    ): List<ProgressReportWeeklyVolumeRow> {
        val sessionsByWeek = sessions.groupBy { reportBucketStart(it.date, range) }
        val volumeByWeek = dailyVolume.groupBy { reportBucketStart(LocalDate.parse(it.date), range) }
        val repsByWeek = dailyRepActivity.groupBy { reportBucketStart(LocalDate.parse(it.date), range) }
        return (volumeByWeek.keys + repsByWeek.keys + sessionsByWeek.keys)
            .filter { !it.isBefore(range.start) && !it.isAfter(range.end) }
            .sorted()
            .map { weekStart ->
                val volumeRows = volumeByWeek[weekStart].orEmpty()
                val repRows = repsByWeek[weekStart].orEmpty()
                val weekSessions = sessionsByWeek[weekStart].orEmpty()
                val totalVolume = volumeRows.sumOf { it.total_volume }
                ProgressReportWeeklyVolumeRow(
                    weekLabel = formatWeekLabel(weekStart, range),
                    workoutCount = weekSessions.size,
                    activeDays = weekSessions.map { it.date }.distinct().size,
                    totalVolumeKg = totalVolume,
                    setCount = if (totalVolume > 0.0) volumeRows.sumOf { it.total_sets } else repRows.sumOf { it.total_sets },
                    repCount = repRows.sumOf { it.total_reps }
                )
            }
            .takeLast(12)
    }

    private fun buildMuscleGroupRows(
        volumeRows: List<com.example.liftrix.data.local.dao.MuscleGroupVolumeResult>,
        repRows: List<com.example.liftrix.data.local.dao.MuscleGroupRepActivityResult>
    ): List<ProgressReportMuscleGroupRow> {
        if (volumeRows.isNotEmpty()) {
            val repsByMuscle = repRows.associateBy { it.primary_muscle_group }
            return volumeRows.take(8).map {
                ProgressReportMuscleGroupRow(
                    muscleGroup = it.primary_muscle_group,
                    totalVolumeKg = it.total_volume,
                    exerciseCount = it.exercise_count,
                    setCount = it.total_sets,
                    repCount = repsByMuscle[it.primary_muscle_group]?.total_reps ?: 0
                )
            }
        }
        return repRows.take(8).map {
            ProgressReportMuscleGroupRow(
                muscleGroup = it.primary_muscle_group,
                totalVolumeKg = 0.0,
                exerciseCount = it.exercise_count,
                setCount = it.total_sets,
                repCount = it.total_reps
            )
        }
    }

    private fun buildConsistencyRows(
        sessions: List<ReportSession>,
        range: ProgressReportResolvedDateRange
    ): List<ProgressReportConsistencyRow> {
        val sessionsByWeek = sessions.groupBy { reportBucketStart(it.date, range) }
        return generateSequence(range.start) { current ->
                val nextMonday = current.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))
                if (nextMonday == current) current.plusWeeks(1) else nextMonday
            }
            .takeWhile { !it.isAfter(range.end) }
            .map { week ->
                val weekSessions = sessionsByWeek[week].orEmpty()
                ProgressReportConsistencyRow(
                    weekLabel = formatWeekLabel(week, range),
                    workoutCount = weekSessions.size,
                    activeDays = weekSessions.map { it.date }.distinct().size
                )
            }
            .toList()
            .takeLast(12)
    }

    private fun calculateBestStreak(sessions: List<ReportSession>): Int {
        val dates = sessions.map { it.date }.distinct().sorted()
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

    private fun calculateActiveWeekAverageWorkouts(sessions: List<ReportSession>): Double? {
        val activeWeeks = sessions.groupingBy { weekStart(it.date) }.eachCount().values
        return activeWeeks.takeIf { it.isNotEmpty() }?.average()
    }

    private fun calculateConsistencyScore(activeDays: Int, totalDays: Int, averageWorkoutsPerWeek: Double): Int {
        if (totalDays <= 0 || activeDays <= 0) return 0
        val activeDayScore = activeDays.toDouble() / totalDays * 100.0
        val frequencyScore = (averageWorkoutsPerWeek / 3.0 * 100.0).coerceAtMost(100.0)
        return ((activeDayScore * 0.4) + (frequencyScore * 0.6)).roundToInt().coerceIn(0, 100)
    }

    private fun findMostActiveDay(sessions: List<ReportSession>): String? {
        val day = sessions
            .groupingBy { it.date.dayOfWeek }
            .eachCount()
            .maxWithOrNull(compareBy<Map.Entry<DayOfWeek, Int>> { it.value }.thenBy { it.key.value })
            ?.key
            ?: return null
        return day.getDisplayName(TextStyle.FULL, REPORT_LOCALE)
    }

    private fun buildPrivacyApplied(privacyOptions: ProgressReportPrivacyOptions): List<String> {
        return buildList {
            add(if (privacyOptions.hideBodyweight) "Bodyweight hidden" else "Bodyweight not collected in this report")
            add(if (privacyOptions.hidePersonalNotes) "Personal notes hidden" else "Personal notes not collected in this report")
            add(if (privacyOptions.hideEmailAccountInfo) "Email and account info hidden" else "Email and account info not collected in this report")
        }
    }

    private fun filterReportPersonalRecords(
        records: List<PersonalRecordEntity>,
        bodyweightExerciseNames: Set<String>
    ): List<PersonalRecordEntity> {
        return records
            .asSequence()
            .filterNot { it.prType == "ONE_RM" && it.reps > MAX_REPS_FOR_RELIABLE_ONE_RM }
            .filterNot { record ->
                record.exerciseName.normalizedNameKey() in bodyweightExerciseNames &&
                    record.prType in setOf("ONE_RM", "MAX_WEIGHT", "VOLUME")
            }
            .groupBy { record ->
                listOf(
                    record.exerciseName.normalizedNameKey(),
                    record.prType,
                    record.workoutId.ifBlank { Instant.ofEpochMilli(record.achievedAt).atZone(ZoneId.systemDefault()).toLocalDate().toString() }
                ).joinToString("|")
            }
            .values
            .mapNotNull { duplicates -> duplicates.maxByOrNull { recordNumericValue(it) } }
            .sortedByDescending { it.achievedAt }
    }

    private fun buildPersonalRecordRows(records: List<PersonalRecordEntity>): List<ProgressReportPersonalRecordRow> {
        return records
            .groupBy { record ->
                listOf(
                    record.exerciseName.normalizedNameKey(),
                    record.workoutId.ifBlank { Instant.ofEpochMilli(record.achievedAt).atZone(ZoneId.systemDefault()).toLocalDate().toString() }
                ).joinToString("|")
            }
            .values
            .map { group ->
                val newest = group.maxByOrNull { it.achievedAt } ?: group.first()
                val date = Instant.ofEpochMilli(newest.achievedAt).atZone(ZoneId.systemDefault()).toLocalDate()
                val metrics = group
                    .sortedBy { it.prType }
                    .joinToString(", ") { it.prType.replace('_', ' ') }
                val hasPrevious = group.any { it.previousBest != null }
                val bestDisplay = group.maxByOrNull { recordNumericValue(it) }?.let {
                    formatRecordValue(it.prType, it.estimatedOneRM, it.volume, it.weightKg, it.reps)
                } ?: "-"
                ProgressReportPersonalRecordRow(
                    date = date,
                    exerciseName = newest.exerciseName,
                    recordType = if (hasPrevious) "PR event" else "Baseline set",
                    newValue = if (group.size == 1) {
                        "${newest.prType.replace('_', ' ')}: $bestDisplay"
                    } else {
                        "${if (hasPrevious) "improved" else "first recorded"} across ${group.size} metrics ($metrics)"
                    },
                    previousValue = if (hasPrevious) {
                        group.mapNotNull { it.previousBest }.maxOrNull()?.let { formatDecimal(it, 1) }
                    } else {
                        "First recorded"
                    }
                )
            }
            .sortedByDescending { it.date }
            .take(18)
    }

    private fun recordNumericValue(record: PersonalRecordEntity): Double {
        return when (record.prType) {
            "ONE_RM" -> record.estimatedOneRM ?: record.weightKg ?: 0.0
            "VOLUME" -> record.volume ?: 0.0
            "MAX_WEIGHT" -> record.weightKg ?: 0.0
            else -> record.reps.toDouble()
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
                contextUsed = listOf("Completed workouts", "Training activity", "Sync status")
            )
        }

        val recommendation = when {
            summary.averageWorkoutsPerWeek < 2.0 -> "Plan two or three repeatable training days next week to improve consistency."
            strengthRows.isNotEmpty() -> "Keep progressive overload focused on ${strengthRows.first().exerciseName} while recovery is stable."
            summary.totalVolumeKg > 0.0 -> "Use the latest weekly volume as next week's baseline and add small increases only when form stays strong."
            weeklyVolumeRows.any { it.repCount > 0 } -> "No external load was logged, so use total reps and completed sets to progress gradually."
            else -> "Keep logging complete workouts so the report can identify clearer training patterns."
        }
        val volumePhrase = when {
            summary.totalVolumeKg > 0.0 && summary.totalReps > 0 ->
                "${formatDecimal(summary.totalVolumeKg, 0)} kg of recorded weighted volume and ${summary.totalReps} logged reps"
            summary.totalVolumeKg > 0.0 ->
                "${formatDecimal(summary.totalVolumeKg, 0)} kg of recorded weighted volume"
            summary.totalReps > 0 ->
                "${summary.totalReps} logged reps with no external load recorded"
            else ->
                "limited set detail"
        }
        val entryPhrase = if (summary.rawWorkoutEntries > summary.workoutsCompleted) {
            " (${summary.rawWorkoutEntries} logged entries grouped into report sessions)"
        } else {
            ""
        }

        return ProgressReportAiSummary(
            summary = "Your local training data shows ${summary.workoutsCompleted} completed sessions$entryPhrase, $volumePhrase, and ${summary.newPersonalRecords} grouped personal record events in this period.",
            recommendations = listOf(
                recommendation,
                "Review rest days around your hardest sessions before adding more volume.",
                "Prioritize consistent logging for sets, reps, and weights to improve future report accuracy."
            ).take(3),
            contextUsed = listOf("Completed workouts", "Set activity", "Personal records", "Sync status")
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
            "ONE_RM" -> "${formatDecimal(estimatedOneRm ?: weight ?: 0.0, 1)} kg"
            "VOLUME" -> "${formatDecimal(volume ?: 0.0, 0)} kg"
            "MAX_WEIGHT" -> "${formatDecimal(weight ?: 0.0, 1)} kg"
            else -> "$reps reps"
        }
    }

    private fun formatDecimal(value: Double, digits: Int): String =
        String.format(REPORT_LOCALE, "%.${digits}f", value)

    private fun epochDay(epochMillis: Long): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()

    private fun weekStart(date: LocalDate): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    private fun reportBucketStart(date: LocalDate, range: ProgressReportResolvedDateRange): LocalDate =
        maxOf(weekStart(date), range.start)

    private fun formatWeekLabel(bucketStart: LocalDate, range: ProgressReportResolvedDateRange): String {
        val partialPrefix = if (bucketStart == range.start && weekStart(range.start) != range.start) "partial " else ""
        return "$partialPrefix${bucketStart.monthValue.toString().padStart(2, '0')}/${bucketStart.dayOfMonth.toString().padStart(2, '0')}"
    }

    private fun String.normalizedNameKey(): String = trim().lowercase(Locale.ROOT)

    private fun groupReportSessions(workouts: List<WorkoutEntity>): List<ReportSession> {
        return workouts
            .filterNot { isPlaceholderWorkout(it) }
            .groupBy { workout ->
                listOf(
                    workout.date.toString(),
                    workout.templateId?.takeIf { it.isNotBlank() } ?: workout.name.normalizedNameKey(),
                    durationMinutes(workout)?.div(15L) ?: "missing-duration"
                ).joinToString("|")
            }
            .values
            .map { entries ->
                val representative = entries.minByOrNull { it.createdAt } ?: entries.first()
                ReportSession(
                    date = representative.date,
                    name = representative.name,
                    entryCount = entries.size
                )
            }
            .sortedBy { it.date }
    }

    private fun isPlaceholderWorkout(workout: WorkoutEntity): Boolean {
        val hasName = workout.name.isNotBlank() && workout.name.normalizedNameKey() !in setOf("workout", "untitled", "untitled workout")
        val hasDuration = durationMinutes(workout) != null
        val hasExercises = workout.exercisesJson.trim() !in setOf("", "[]", "null")
        return !hasName && !hasDuration && !hasExercises
    }

    private data class ReportSession(
        val date: LocalDate,
        val name: String,
        val entryCount: Int
    )

    private data class OneRmStatus(
        val label: String,
        val improvementKg: Double?,
        val baselineKg: Double?
    )
}
