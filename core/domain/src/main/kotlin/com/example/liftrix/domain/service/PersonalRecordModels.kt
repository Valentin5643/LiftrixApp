package com.example.liftrix.domain.service

data class PersonalRecord(
    val exerciseName: String,
    val prType: PRType,
    val weight: Double?,
    val reps: Int,
    val estimatedOneRM: Double?,
    val volume: Double?,
    val achievedAt: Long,
    val previousBest: Double?,
    val improvementPercent: Double?
)

enum class PRType {
    ONE_RM,
    VOLUME,
    REPS,
    MAX_WEIGHT
}
