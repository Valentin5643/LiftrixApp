package com.example.liftrix.data.mapper

import com.example.liftrix.data.local.entity.WorkoutEntity
import com.example.liftrix.data.remote.dto.WorkoutDto
import com.example.liftrix.domain.model.*
import com.google.firebase.Timestamp
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkoutMapper @Inject constructor(
    private val exerciseMapper: ExerciseMapper,
    private val gson: Gson
) {

    companion object {
        private val DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE
    }

    /**
     * Convert Room entity to domain model
     */
    fun toDomain(entity: WorkoutEntity): Workout = entity.run {
        val exercisesType = object : TypeToken<List<Exercise>>() {}.type
        val exercises: List<Exercise> = gson.fromJson(exercisesJson, exercisesType) ?: emptyList()

        Workout(
            userId = userId,
            id = WorkoutId(id),
            name = name,
            date = date,
            exercises = exercises,
            status = status,
            startTime = startTime,
            endTime = endTime,
            notes = notes,
            templateId = templateId?.let(::WorkoutId),
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    /**
     * Convert domain model to Room entity
     */
    fun toEntity(workout: Workout, isSynced: Boolean = false): WorkoutEntity = workout.run {
        WorkoutEntity(
            id = id.value,
            userId = userId,
            name = name,
            date = date,
            exercisesJson = gson.toJson(exercises),
            status = status,
            startTime = startTime,
            endTime = endTime,
            notes = notes,
            templateId = templateId?.value,
            createdAt = createdAt,
            updatedAt = updatedAt,
            isSynced = isSynced,
            syncVersion = System.currentTimeMillis()
        )
    }

    /**
     * Convert domain model to Firestore DTO
     */
    fun toFirestoreDto(workout: Workout, userId: String): WorkoutDto = workout.run {
        WorkoutDto(
            id = id.value,
            name = name,
            date = date.format(DATE_FORMATTER),
            exercises = exercises.map(exerciseMapper::toFirestoreDto),
            status = status.name,
            startTime = startTime?.let { Timestamp(it.epochSecond, it.nano) },
            endTime = endTime?.let { Timestamp(it.epochSecond, it.nano) },
            notes = notes,
            templateId = templateId?.value,
            createdAt = Timestamp(createdAt.epochSecond, createdAt.nano),
            updatedAt = Timestamp(updatedAt.epochSecond, updatedAt.nano),
            userId = userId,
            version = System.currentTimeMillis()
        )
    }

    /**
     * Convert Firestore DTO to domain model
     */
    fun fromFirestoreDto(dto: WorkoutDto): Workout = dto.run {
        Workout(
            userId = userId,
            id = WorkoutId(id),
            name = name,
            date = LocalDate.parse(date, DATE_FORMATTER),
            exercises = emptyList(), // TODO: Implement proper exercise conversion with library lookup
            status = WorkoutStatus.valueOf(status),
            startTime = startTime?.toInstant(),
            endTime = endTime?.toInstant(),
            notes = notes,
            templateId = templateId?.let(::WorkoutId),
            createdAt = createdAt.toInstant(),
            updatedAt = updatedAt?.toInstant() ?: createdAt.toInstant()
        )
    }

    /**
     * Convert Firestore DTO to Room entity
     */
    fun firestoreDtoToEntity(dto: WorkoutDto, isSynced: Boolean = true): WorkoutEntity {
        return WorkoutEntity(
            id = dto.id,
            userId = dto.userId,
            name = dto.name,
            date = LocalDate.parse(dto.date, DATE_FORMATTER),
            exercisesJson = gson.toJson(emptyList<Exercise>()), // TODO: Implement proper exercise conversion
            status = WorkoutStatus.valueOf(dto.status),
            startTime = dto.startTime?.toInstant(),
            endTime = dto.endTime?.toInstant(),
            notes = dto.notes,
            templateId = dto.templateId,
            createdAt = dto.createdAt.toInstant(),
            updatedAt = dto.updatedAt?.toInstant() ?: dto.createdAt.toInstant(),
            isSynced = isSynced,
            syncVersion = dto.version
        )
    }

    /**
     * Update entity sync status
     */
    fun updateEntitySyncStatus(entity: WorkoutEntity, isSynced: Boolean): WorkoutEntity {
        return entity.copy(
            isSynced = isSynced,
            syncVersion = System.currentTimeMillis()
        )
    }

    /**
     * Merge remote changes with local entity
     */
    fun mergeRemoteChanges(localEntity: WorkoutEntity, remoteDto: WorkoutDto): WorkoutEntity {
        return if (remoteDto.version > localEntity.syncVersion) {
            // Remote version is newer, use remote data
            firestoreDtoToEntity(remoteDto, isSynced = true)
        } else {
            // Local version is newer or same, keep local but mark as synced
            localEntity.copy(isSynced = true)
        }
    }

    private fun Timestamp.toInstant(): Instant {
        return Instant.ofEpochSecond(seconds, nanoseconds.toLong())
    }
} 