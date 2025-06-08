package com.example.liftrix

import com.example.liftrix.domain.model.*
import com.example.liftrix.sync.SyncStatus
import java.time.Instant
import java.time.LocalDate

object TestDataFactory {
    
    // User test data
    val testUser = User(
        uid = "test-user-id",
        email = "test@example.com",
        displayName = "Test User",
        photoUrl = null,
        isAnonymous = false,
        createdAt = Instant.now().minusSeconds(3600),
        lastSignInAt = Instant.now()
    )
    
    val anonymousUser = User(
        uid = "anonymous-user-id",
        email = "",
        displayName = null,
        photoUrl = null,
        isAnonymous = true,
        createdAt = Instant.now().minusSeconds(1800),
        lastSignInAt = Instant.now()
    )
    
    // Exercise test data
    val benchPressExercise = Exercise(
        id = ExerciseId.generate(),
        name = "Bench Press",
        category = ExerciseCategory.CHEST,
        sets = listOf(
            ExerciseSet(
                setNumber = 1,
                weight = Weight.fromKilograms(80.0),
                reps = Reps.of(10),
                isCompleted = true,
                restTimeSeconds = 120,
                completedAt = Instant.now()
            ),
            ExerciseSet(
                setNumber = 2,
                weight = Weight.fromKilograms(85.0),
                reps = Reps.of(8),
                isCompleted = false
            )
        ),
        notes = "Focus on controlled movement",
        targetSets = 3,
        targetReps = Reps.of(8),
        targetWeight = Weight.fromKilograms(85.0),
        createdAt = Instant.now().minusSeconds(300),
        updatedAt = Instant.now()
    )
    
    val squatExercise = Exercise(
        id = ExerciseId.generate(),
        name = "Back Squat",
        category = ExerciseCategory.LEGS,
        sets = listOf(
            ExerciseSet(
                setNumber = 1,
                weight = Weight.fromKilograms(100.0),
                reps = Reps.of(12),
                isCompleted = true,
                restTimeSeconds = 180,
                completedAt = Instant.now().minusSeconds(180)
            )
        ),
        notes = "Keep chest up",
        targetSets = 2,
        targetReps = Reps.of(10),
        targetWeight = Weight.fromKilograms(105.0),
        createdAt = Instant.now().minusSeconds(300),
        updatedAt = Instant.now()
    )
    
    // Workout test data
    val sampleWorkout = Workout(
        userId = testUser.uid,
        id = WorkoutId.generate(),
        name = "Upper Body Workout",
        date = LocalDate.now(),
        exercises = listOf(benchPressExercise, squatExercise),
        status = WorkoutStatus.IN_PROGRESS,
        startTime = Instant.now().minusSeconds(900),
        endTime = null,
        notes = "Feeling strong today",
        templateId = null,
        createdAt = Instant.now().minusSeconds(900),
        updatedAt = Instant.now()
    )
    
    val completedWorkout = sampleWorkout.copy(
        status = WorkoutStatus.COMPLETED,
        endTime = Instant.now(),
        exercises = listOf(
            benchPressExercise.copy(
                sets = benchPressExercise.sets.map { it.copy(isCompleted = true) }
            ),
            squatExercise.copy(
                sets = squatExercise.sets.map { it.copy(isCompleted = true) }
            )
        )
    )
    
    // Sync status test data
    val idleSyncStatus = SyncStatus.Idle
    val syncingSyncStatus = SyncStatus.Syncing
    val successSyncStatus = SyncStatus.Success(syncedCount = 5)
    val errorSyncStatus = SyncStatus.Error("Network error")
    
    // Workout UI state test data
    val initialWorkoutUiState = WorkoutUiState(
        workouts = emptyList(),
        syncStatus = SyncStatus.Idle,
        unsyncedCount = 0,
        isLoading = true,
        isSaving = false,
        errorMessage = null
    )
    
    val loadedWorkoutUiState = WorkoutUiState(
        workouts = listOf(sampleWorkout, completedWorkout),
        syncStatus = SyncStatus.Success(2),
        unsyncedCount = 1,
        isLoading = false,
        isSaving = false,
        errorMessage = null
    )
    
    val errorWorkoutUiState = WorkoutUiState(
        workouts = emptyList(),
        syncStatus = SyncStatus.Error("Sync failed"),
        unsyncedCount = 3,
        isLoading = false,
        isSaving = false,
        errorMessage = "Failed to save workout"
    )
    
    // Factory methods for creating variations
    fun createWorkout(
        userId: String = testUser.uid,
        name: String = "Test Workout",
        status: WorkoutStatus = WorkoutStatus.IN_PROGRESS,
        exerciseCount: Int = 2
    ): Workout {
        val exercises = (1..exerciseCount).map { index ->
            Exercise(
                id = ExerciseId.generate(),
                name = "Exercise $index",
                category = ExerciseCategory.CHEST,
                sets = listOf(
                    ExerciseSet(
                        setNumber = 1,
                        weight = Weight.fromKilograms(50.0 + index * 5),
                        reps = Reps.of(10),
                        isCompleted = status == WorkoutStatus.COMPLETED
                    )
                ),
                targetSets = 3,
                targetReps = Reps.of(10),
                targetWeight = Weight.fromKilograms(50.0 + index * 5),
                createdAt = Instant.now().minusSeconds(300),
                updatedAt = Instant.now()
            )
        }
        
        return Workout(
            userId = userId,
            id = WorkoutId.generate(),
            name = name,
            date = LocalDate.now(),
            exercises = exercises,
            status = status,
            startTime = Instant.now().minusSeconds(600),
            endTime = if (status == WorkoutStatus.COMPLETED) Instant.now() else null,
            notes = "Test workout notes",
            templateId = null,
            createdAt = Instant.now().minusSeconds(600),
            updatedAt = Instant.now()
        )
    }
    
    fun createUser(
        uid: String = "test-user",
        email: String = "test@example.com",
        isAnonymous: Boolean = false
    ): User {
        return User(
            uid = uid,
            email = if (isAnonymous) "" else email,
            displayName = if (isAnonymous) null else "Test User",
            photoUrl = null,
            isAnonymous = isAnonymous,
            createdAt = Instant.now().minusSeconds(3600),
            lastSignInAt = Instant.now()
        )
    }
} 