package com.example.liftrix.widget

data class StreakWidgetSnapshot(
    val currentStreakWeeks: Int,
    val hasData: Boolean,
    val lastUpdatedMillis: Long
)

data class ConsistencyWidgetSnapshot(
    val currentStreakWeeks: Int,
    val lastSevenDays: List<ConsistencyDay>,
    val totalVolumeLabel: String,
    val totalVolumeTrend: String?,
    val lastUpdatedMillis: Long
)

data class DashboardWidgetSnapshot(
    val currentStreakWeeks: Int,
    val featuredExercise: FeaturedExercise?,
    val recentVolumeDays: List<ConsistencyDay>,
    val totalVolumeLabel: String,
    val recentPrState: String?,
    val hasActiveSession: Boolean,
    val lastUpdatedMillis: Long
)

data class ConsistencyDay(
    val dateLabel: String,
    val isActive: Boolean,
    val volumeLabel: String? = null,
    val isToday: Boolean = false
)

data class FeaturedExercise(
    val name: String,
    val weightLabel: String,
    val repsLabel: String
)

data class LiftrixHomeWidgetSnapshots(
    val streak: StreakWidgetSnapshot,
    val consistency: ConsistencyWidgetSnapshot,
    val dashboard: DashboardWidgetSnapshot
) {
    companion object {
        fun empty(now: Long = System.currentTimeMillis()): LiftrixHomeWidgetSnapshots {
            val emptyDays = emptyConsistencyDays()
            return LiftrixHomeWidgetSnapshots(
                streak = StreakWidgetSnapshot(
                    currentStreakWeeks = 0,
                    hasData = false,
                    lastUpdatedMillis = now
                ),
                consistency = ConsistencyWidgetSnapshot(
                    currentStreakWeeks = 0,
                    lastSevenDays = emptyDays,
                    totalVolumeLabel = "0 kg",
                    totalVolumeTrend = null,
                    lastUpdatedMillis = now
                ),
                dashboard = DashboardWidgetSnapshot(
                    currentStreakWeeks = 0,
                    featuredExercise = null,
                    recentVolumeDays = emptyDays,
                    totalVolumeLabel = "0 kg",
                    recentPrState = null,
                    hasActiveSession = false,
                    lastUpdatedMillis = now
                )
            )
        }
    }
}

internal fun emptyConsistencyDays(): List<ConsistencyDay> {
    return listOf("M", "T", "W", "T", "F").map { label ->
        ConsistencyDay(dateLabel = label, isActive = false)
    }
}
