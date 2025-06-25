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
            availableEquipment = entity.availableEquipmentJson?.toEquipmentList() ?: emptyList(),
            otherEquipment = entity.otherEquipment,
            fitnessGoals = entity.fitnessGoalsJson?.toFitnessGoalList() ?: emptyList(),
            goalsPriority = entity.goalsPriorityJson?.toGoalsPriorityMap(),
            completedAt = entity.completedAt,
            updatedAt = entity.updatedAt,
            profileVersion = entity.profileVersion
        )
    }

    fun toEntity(profile: UserProfile, isSynced: Boolean = false): UserProfileEntity {
        return UserProfileEntity(
            userId = profile.userId,
            age = profile.age,
            weightKg = profile.weight?.kilograms,
            availableEquipmentJson = profile.availableEquipment.toJson(),
            otherEquipment = profile.otherEquipment,
            fitnessGoalsJson = profile.fitnessGoals.toJson(),
            goalsPriorityJson = profile.goalsPriority?.toJson(),
            completedAt = profile.completedAt,
            updatedAt = profile.updatedAt,
            profileVersion = profile.profileVersion,
            isSynced = isSynced,
            syncVersion = System.currentTimeMillis()
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
            syncVersion = System.currentTimeMillis()
        )
    }

    private fun <T> T.toJson(): String? {
        return gson.toJson(this)
    }

    private fun String.toEquipmentList(): List<Equipment> {
        val type = object : TypeToken<List<Equipment>>() {}.type
        return gson.fromJson(this, type)
    }

    private fun String.toFitnessGoalList(): List<FitnessGoal> {
        val type = object : TypeToken<List<FitnessGoal>>() {}.type
        return gson.fromJson(this, type)
    }

    private fun String.toGoalsPriorityMap(): Map<FitnessGoal, Int> {
        val type = object : TypeToken<Map<FitnessGoal, Int>>() {}.type
        return gson.fromJson(this, type)
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