package com.example.liftrix.data.mapper

import com.example.liftrix.data.local.entity.UserProfileEntity
import com.example.liftrix.data.remote.dto.UserProfileDto
import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.FitnessGoal
import com.example.liftrix.domain.model.UserProfile
import com.example.liftrix.domain.model.Weight
import com.google.firebase.Timestamp
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserProfileMapper @Inject constructor(
    private val gson: Gson
) {

    fun toDomain(entity: UserProfileEntity): UserProfile {
        return UserProfile(
            userId = entity.userId,
            age = entity.age,
            weight = entity.weightKg?.let { Weight.fromKilograms(it) },
            availableEquipment = entity.availableEquipment?.toEquipmentList() ?: emptyList(),
            otherEquipment = null, // Note: otherEquipment not stored separately in new entity structure
            fitnessGoals = entity.goals?.toFitnessGoalList() ?: emptyList(),
            goalsPriority = null, // Note: goalsPriority not stored in new entity structure
            completedAt = entity.completedAt,
            updatedAt = entity.updatedAt,
            profileVersion = entity.syncVersion // Using syncVersion as profileVersion
        )
    }

    fun toEntity(profile: UserProfile, isSynced: Boolean = false): UserProfileEntity {
        return UserProfileEntity(
            id = UUID.randomUUID().toString(), // Generate unique ID for entity
            userId = profile.userId,
            displayName = "User", // Default display name, can be updated later
            age = profile.age,
            weightKg = profile.weight?.kilograms,
            heightCm = null, // Height not in domain model, set to null
            fitnessLevel = determineFitnessLevel(profile.fitnessGoals),
            goals = profile.fitnessGoals.toJson(),
            availableEquipment = profile.availableEquipment.toJson(),
            workoutFrequency = null, // Not in domain model, set to null
            preferredWorkoutDuration = null, // Not in domain model, set to null
            completedAt = profile.completedAt,
            createdAt = LocalDateTime.now(), // Set current time for new entities
            updatedAt = profile.updatedAt,
            isSynced = isSynced,
            syncVersion = profile.profileVersion
        )
    }

    fun toEntityWithId(profile: UserProfile, entityId: String, isSynced: Boolean = false): UserProfileEntity {
        return UserProfileEntity(
            id = entityId,
            userId = profile.userId,
            displayName = "User", // Default display name
            age = profile.age,
            weightKg = profile.weight?.kilograms,
            heightCm = null,
            fitnessLevel = determineFitnessLevel(profile.fitnessGoals),
            goals = profile.fitnessGoals.toJson(),
            availableEquipment = profile.availableEquipment.toJson(),
            workoutFrequency = null,
            preferredWorkoutDuration = null,
            completedAt = profile.completedAt,
            createdAt = LocalDateTime.now(),
            updatedAt = profile.updatedAt,
            isSynced = isSynced,
            syncVersion = profile.profileVersion
        )
    }

    fun toFirestoreDto(profile: UserProfile): UserProfileDto {
        return UserProfileDto(
            userId = profile.userId,
            age = profile.age,
            weight = profile.weight?.let { UserProfileDto.WeightDto(it.kilograms, "kg") },
            availableEquipment = profile.availableEquipment.map { it.name },
            otherEquipment = profile.otherEquipment,
            fitnessGoals = profile.fitnessGoals.map { it.name },
            goalsPriority = profile.goalsPriority?.mapKeys { it.key.name },
            completedAt = profile.completedAt?.toTimestamp(),
            updatedAt = profile.updatedAt.toTimestamp(),
            profileVersion = profile.profileVersion
        )
    }

    fun fromFirestoreDto(dto: UserProfileDto): UserProfile {
        return UserProfile(
            userId = dto.userId,
            age = dto.age,
            weight = dto.weight?.let { Weight.fromKilograms(it.value) },
            availableEquipment = dto.availableEquipment.mapNotNull { it.toEquipment() },
            otherEquipment = dto.otherEquipment,
            fitnessGoals = dto.fitnessGoals.mapNotNull { it.toFitnessGoal() },
            goalsPriority = dto.goalsPriority?.mapKeys { it.key.toFitnessGoal()!! },
            completedAt = dto.completedAt?.toLocalDateTime(),
            updatedAt = dto.updatedAt?.toLocalDateTime() ?: LocalDateTime.now(),
            profileVersion = dto.profileVersion
        )
    }

    fun firestoreDtoToEntity(dto: UserProfileDto, isSynced: Boolean = true): UserProfileEntity {
        val domain = fromFirestoreDto(dto)
        return toEntity(domain, isSynced)
    }

    fun updateEntitySyncStatus(entity: UserProfileEntity, isSynced: Boolean): UserProfileEntity {
        return entity.copy(
            isSynced = isSynced,
            syncVersion = System.currentTimeMillis(),
            updatedAt = LocalDateTime.now()
        )
    }

    private fun determineFitnessLevel(goals: List<FitnessGoal>): String? {
        // Simple heuristic to determine fitness level based on goals
        return when {
            goals.contains(FitnessGoal.BUILD_MUSCLE) -> "intermediate"
            goals.contains(FitnessGoal.LOSE_WEIGHT) -> "beginner"
            goals.contains(FitnessGoal.INCREASE_STRENGTH) -> "intermediate"
            goals.contains(FitnessGoal.IMPROVE_ENDURANCE) -> "intermediate"
            goals.contains(FitnessGoal.SPORT_SPECIFIC) -> "advanced"
            else -> "beginner"
        }
    }

    private fun <T> T.toJson(): String? {
        return gson.toJson(this)
    }

    private fun String.toEquipmentList(): List<Equipment> {
        return try {
            val type = object : TypeToken<List<Equipment>>() {}.type
            gson.fromJson(this, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun String.toFitnessGoalList(): List<FitnessGoal> {
        return try {
            val type = object : TypeToken<List<FitnessGoal>>() {}.type
            gson.fromJson(this, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun String.toGoalsPriorityMap(): Map<FitnessGoal, Int> {
        return try {
            val type = object : TypeToken<Map<FitnessGoal, Int>>() {}.type
            gson.fromJson(this, type) ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun String.toEquipment(): Equipment? {
        return try {
            Equipment.valueOf(this)
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    private fun String.toFitnessGoal(): FitnessGoal? {
        return try {
            FitnessGoal.valueOf(this)
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    private fun LocalDateTime.toTimestamp(): Timestamp {
        return Timestamp(this.toEpochSecond(ZoneOffset.UTC), this.nano)
    }

    private fun Timestamp.toLocalDateTime(): LocalDateTime {
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(this.seconds, this.nanoseconds.toLong()), ZoneOffset.UTC)
    }
} 