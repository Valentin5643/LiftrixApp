package com.example.liftrix.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index
import com.example.liftrix.domain.service.PRType

/**
 * Room entity for storing personal records with proper user scoping
 * 
 * Implements SyncableEntity interface for Firebase sync support
 * Includes composite indexes for efficient querying by user and exercise
 * 
 * Critical: ALL queries MUST filter by user_id to prevent data leakage
 */
@Entity(
    tableName = "personal_records",
    indices = [
        Index(value = ["user_id"]),
        Index(value = ["user_id", "exercise_name"]),
        Index(value = ["user_id", "exercise_name", "pr_type"]),
        Index(value = ["user_id", "achieved_at"])
    ]
)
data class PersonalRecordEntity(
    @PrimaryKey
    val id: String,
    
    @ColumnInfo(name = "user_id")
    val userId: String,
    
    @ColumnInfo(name = "exercise_name")
    val exerciseName: String,
    
    @ColumnInfo(name = "pr_type")
    val prType: String, // PRType enum name
    
    @ColumnInfo(name = "weight_kg")
    val weightKg: Double?, // Null for bodyweight exercises
    
    @ColumnInfo(name = "reps")
    val reps: Int,
    
    @ColumnInfo(name = "volume")
    val volume: Double?, // weight * reps, null for bodyweight
    
    @ColumnInfo(name = "estimated_one_rm")
    val estimatedOneRM: Double?,
    
    @ColumnInfo(name = "achieved_at")
    val achievedAt: Long, // Timestamp in milliseconds
    
    @ColumnInfo(name = "workout_id")
    val workoutId: String,
    
    @ColumnInfo(name = "previous_best")
    val previousBest: Double?, // Previous record value for comparison
    
    @ColumnInfo(name = "improvement_percent")
    val improvementPercent: Double?, // Percentage improvement over previous
    
    // Sync metadata for Firebase integration
    @ColumnInfo(name = "is_synced")
    val isSynced: Boolean = false,
    
    @ColumnInfo(name = "sync_version")
    val syncVersion: Long = 0,
    
    @ColumnInfo(name = "last_modified")
    val lastModified: Long = System.currentTimeMillis()
) {
    init {
        require(id.isNotBlank()) { "PersonalRecord ID cannot be blank" }
        require(userId.isNotBlank()) { "User ID cannot be blank" }
        require(exerciseName.isNotBlank()) { "Exercise name cannot be blank" }
        require(reps > 0) { "Reps must be positive: $reps" }
        require(achievedAt > 0) { "Achievement timestamp must be positive: $achievedAt" }
        require(workoutId.isNotBlank()) { "Workout ID cannot be blank" }
        
        // Validate weight for weighted exercises
        if (prType != PRType.REPS.name && weightKg != null) {
            require(weightKg > 0) { "Weight must be positive for weighted exercises: $weightKg" }
        }
        
        // Validate 1RM calculation
        if (estimatedOneRM != null && weightKg != null) {
            require(estimatedOneRM >= weightKg) { 
                "Estimated 1RM must be >= actual weight: $estimatedOneRM < $weightKg" 
            }
        }
        
        // Validate improvement
        if (improvementPercent != null) {
            require(improvementPercent >= 0) { 
                "Improvement percent must be non-negative: $improvementPercent" 
            }
        }
    }
    
    companion object {
        /**
         * Creates a PersonalRecordEntity from domain PersonalRecord
         */
        fun fromDomain(
            domainRecord: com.example.liftrix.domain.service.PersonalRecord,
            userId: String,
            workoutId: String
        ): PersonalRecordEntity {
            return PersonalRecordEntity(
                id = generateId(),
                userId = userId,
                exerciseName = domainRecord.exerciseName,
                prType = domainRecord.prType.name,
                weightKg = domainRecord.weight,
                reps = domainRecord.reps,
                volume = domainRecord.volume,
                estimatedOneRM = domainRecord.estimatedOneRM,
                achievedAt = domainRecord.achievedAt,
                workoutId = workoutId,
                previousBest = domainRecord.previousBest,
                improvementPercent = domainRecord.improvementPercent
            )
        }
        
        /**
         * Generates unique ID for personal record
         */
        private fun generateId(): String = "pr_${java.util.UUID.randomUUID()}"
    }
    
    /**
     * Converts to domain PersonalRecord
     */
    fun toDomain(): com.example.liftrix.domain.service.PersonalRecord {
        return com.example.liftrix.domain.service.PersonalRecord(
            exerciseName = exerciseName,
            prType = PRType.valueOf(prType),
            weight = weightKg,
            reps = reps,
            estimatedOneRM = estimatedOneRM,
            volume = volume,
            achievedAt = achievedAt,
            previousBest = previousBest,
            improvementPercent = improvementPercent
        )
    }
    
    /**
     * Creates a copy with updated sync metadata
     */
    fun markAsSynced(syncVersion: Long = this.syncVersion + 1): PersonalRecordEntity {
        return copy(
            isSynced = true,
            syncVersion = syncVersion,
            lastModified = System.currentTimeMillis()
        )
    }
    
    /**
     * Creates a copy with updated sync status for modifications
     */
    fun markAsModified(): PersonalRecordEntity {
        return copy(
            isSynced = false,
            lastModified = System.currentTimeMillis()
        )
    }
}