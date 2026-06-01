package com.example.liftrix.ui.share

import com.example.liftrix.domain.model.Workout
import com.example.liftrix.domain.service.PRType
import com.example.liftrix.domain.service.PersonalRecord
import java.text.NumberFormat
import java.time.Duration
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import javax.inject.Inject

class WorkoutShareStatsFormatter @Inject constructor() {
    private val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    private val numberFormat = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
        maximumFractionDigits = 0
    }

    fun format(
        workout: Workout,
        personalRecords: List<PersonalRecord>
    ): WorkoutShareStoryStats {
        val prLabels = personalRecords
            .distinctBy { "${it.exerciseName}:${it.prType}" }
            .take(MaxPrLabels)
            .map(::formatPersonalRecord)

        return WorkoutShareStoryStats(
            workoutName = clampName(workout.name),
            displayDate = workout.date.format(dateFormatter),
            totalVolume = "${numberFormat.format(workout.calculateTotalVolumeKg())} kg",
            exerciseCount = "${workout.exercises.size}",
            duration = workout.getDuration()?.let(::formatDuration),
            prCount = personalRecords.size,
            prSummary = when (personalRecords.size) {
                0 -> "No PRs this session"
                1 -> "1 personal record"
                else -> "${personalRecords.size} personal records"
            },
            prLabels = prLabels
        )
    }

    private fun clampName(name: String): String {
        return if (name.length <= MaxWorkoutNameLength) {
            name
        } else {
            name.take(MaxWorkoutNameLength - 1).trimEnd() + "..."
        }
    }

    private fun formatDuration(duration: Duration): String {
        val minutes = duration.toMinutes().coerceAtLeast(1)
        val hours = minutes / 60
        val remainingMinutes = minutes % 60
        return when {
            hours > 0 && remainingMinutes > 0 -> "${hours}h ${remainingMinutes}m"
            hours > 0 -> "${hours}h"
            else -> "${minutes}m"
        }
    }

    private fun formatPersonalRecord(record: PersonalRecord): String {
        val value = when (record.prType) {
            PRType.ONE_RM -> record.estimatedOneRM?.let { "${numberFormat.format(it)} kg est. 1RM" }
            PRType.VOLUME -> record.volume?.let { "${numberFormat.format(it)} kg volume" }
            PRType.REPS -> "${record.reps} reps"
            PRType.MAX_WEIGHT -> record.weight?.let { "${numberFormat.format(it)} kg" }
        } ?: record.prType.displayName

        return "${record.exerciseName}: $value"
    }

    private companion object {
        const val MaxWorkoutNameLength = 48
        const val MaxPrLabels = 3
    }
}
