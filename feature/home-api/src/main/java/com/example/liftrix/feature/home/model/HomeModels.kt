package com.example.liftrix.feature.home.model

import android.net.Uri
import com.example.liftrix.domain.model.WeightUnit
import com.example.liftrix.domain.model.social.MediaType
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

data class HomeUser(
    val uid: String,
    val displayName: String?,
    val photoUrl: String?
)

enum class HomeWorkoutStatus {
    PLANNED,
    IN_PROGRESS,
    PAUSED,
    COMPLETED,
    CANCELLED
}

data class HomeWeight(
    val kilograms: Double
) {
    fun getValue(unit: WeightUnit): Double = when (unit) {
        WeightUnit.KILOGRAMS -> kilograms
        WeightUnit.POUNDS -> kilograms * 2.2046226218
    }
}

data class HomeWorkout(
    val id: String,
    val userId: String,
    val name: String,
    val date: LocalDate,
    val exerciseCount: Int,
    val totalSets: Int,
    val completedSetCount: Int,
    val totalVolumeKg: Double,
    val status: HomeWorkoutStatus,
    val startTime: Instant? = null,
    val endTime: Instant? = null,
    val notes: String? = null
) {
    fun getDuration(): Duration? {
        return if (startTime != null && endTime != null) {
            Duration.between(startTime, endTime)
        } else null
    }

    fun getCompletedSets(): Int = completedSetCount

    fun getCompletionPercentage(): Double {
        return if (totalSets > 0) {
            (completedSetCount.toDouble() / totalSets) * 100.0
        } else 0.0
    }

    fun calculateTotalVolume(): HomeWeight? {
        return if (totalVolumeKg > 0.0) HomeWeight(totalVolumeKg) else null
    }
}

data class HomeFeedWorkout(
    val workout: HomeWorkout,
    val isPersonal: Boolean,
    val user: HomeUser? = null,
    val mediaUrls: List<String> = emptyList(),
    val mediaThumbnails: List<String> = emptyList()
) {
    val displayTitle: String
        get() = if (isPersonal) {
            workout.name
        } else {
            "${user?.displayName ?: "Unknown User"}'s ${workout.name}"
        }

    val displayUser: HomeUser?
        get() = if (isPersonal) null else user

    fun getSummary(): String = buildString {
        workout.getDuration()?.let { duration ->
            append("${duration.toMinutes()}m - ")
        }
        append("${workout.exerciseCount} exercises")
        if (workout.completedSetCount > 0) {
            append(" - ${workout.completedSetCount} sets")
        }
    }
}

data class HomeWorkoutStats(
    val totalWorkouts: Int = 0,
    val currentStreak: Int = 0,
    val weeklyVolume: Duration = Duration.ZERO,
    val averageWorkoutDuration: Duration = Duration.ZERO,
    val weeklyWorkouts: Int = 0,
    val averagePerWeek: Double = 0.0,
    val workoutsThisWeek: Int = 0,
    val totalMinutesThisWeek: Int = 0,
    val daysSinceLastWorkout: Int? = null
) {
    fun getStreakDescription(): String = when (currentStreak) {
        0 -> "No active streak"
        1 -> "1 day"
        else -> "$currentStreak days"
    }

    fun hasSignificantStreak(): Boolean = currentStreak >= 3

    fun getFormattedAverageDuration(): String {
        val minutes = averageWorkoutDuration.toMinutes()
        return if (minutes > 0) "${minutes}m" else "0m"
    }

    companion object {
        val EMPTY = HomeWorkoutStats()
    }
}

data class HomeMediaUploadRequest(
    val uri: Uri,
    val type: MediaType,
    val caption: String? = null,
    val compressionQuality: Int = 85,
    val maxFileSizeMB: Float = if (type == MediaType.VIDEO) 50.0f else 2.0f
)

data class PostWorkoutSummary(
    val id: String,
    val name: String,
    val durationMinutes: Int,
    val totalVolume: Double,
    val exerciseCount: Int,
    val prsCount: Int = 0
)
