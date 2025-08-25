package com.example.liftrix

import com.example.liftrix.domain.model.*
import com.example.liftrix.sync.SyncStatus
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

object TestDataFactory {
    
    // User test data
    val testUser = User(
        uid = "test-user-id",
        email = "test@example.com",
        displayName = "Test User",
        photoUrl = null,
        isAnonymous = false,
        subscriptionTier = SubscriptionTier.FREE,
        subscriptionStatus = SubscriptionStatus.ACTIVE,
        subscriptionExpiresAt = null,
        premiumFeaturesEnabled = false,
        onboardingCompleted = true,
        profileVersion = 1L,
        createdAt = LocalDateTime.now().minusHours(1),
        lastSignInAt = LocalDateTime.now(),
        updatedAt = LocalDateTime.now()
    )
    
    val anonymousUser = User(
        uid = "anonymous-user-id",
        email = "",
        displayName = null,
        photoUrl = null,
        isAnonymous = true,
        subscriptionTier = SubscriptionTier.FREE,
        subscriptionStatus = SubscriptionStatus.ACTIVE,
        subscriptionExpiresAt = null,
        premiumFeaturesEnabled = false,
        onboardingCompleted = false,
        profileVersion = 1L,
        createdAt = LocalDateTime.now().minusMinutes(30),
        lastSignInAt = LocalDateTime.now(),
        updatedAt = LocalDateTime.now()
    )
    
    // Exercise library test data
    val benchPressLibraryExercise = ExerciseLibrary(
        id = "bench-press-id",
        name = "Bench Press",
        primaryMuscleGroup = ExerciseCategory.CHEST,
        equipment = Equipment.BARBELL,
        secondaryMuscleGroups = listOf(ExerciseCategory.SHOULDERS, ExerciseCategory.TRICEPS),
        movementPattern = "Push",
        difficultyLevel = 5,
        instructions = "Lie on bench, grip barbell shoulder-width apart, lower to chest, press up",
        isCompound = true,
        searchableTerms = listOf("bench", "press", "chest", "barbell")
    )
    
    val squatLibraryExercise = ExerciseLibrary(
        id = "squat-id",
        name = "Back Squat",
        primaryMuscleGroup = ExerciseCategory.LEGS,
        equipment = Equipment.BARBELL,
        secondaryMuscleGroups = listOf(ExerciseCategory.GLUTES, ExerciseCategory.QUADRICEPS),
        movementPattern = "Squat",
        difficultyLevel = 6,
        instructions = "Bar on shoulders, feet shoulder-width apart, squat down, drive up through heels",
        isCompound = true,
        searchableTerms = listOf("squat", "legs", "barbell", "back")
    )
    
    // Exercise test data with sets
    val benchPressExercise = Exercise(
        id = ExerciseId.generate(),
        workoutId = WorkoutId.generate(),
        libraryExercise = benchPressLibraryExercise,
        orderIndex = 0,
        targetSets = 3,
        targetReps = 8,
        targetWeight = Weight.fromKilograms(80.0),
        sets = listOf(
            ExerciseSet(
                id = ExerciseSetId.generate(),
                setNumber = 1,
                reps = Reps.of(8),
                weight = Weight.fromKilograms(80.0),
                completedAt = Instant.now()
            ),
            ExerciseSet(
                id = ExerciseSetId.generate(),
                setNumber = 2,
                reps = Reps.of(8),
                weight = Weight.fromKilograms(80.0)
            )
        ),
        notes = "Focus on form",
        createdAt = Instant.now()
    )
    
    val squatExercise = Exercise(
        id = ExerciseId.generate(),
        workoutId = WorkoutId.generate(),
        libraryExercise = squatLibraryExercise,
        orderIndex = 1,
        targetSets = 4,
        targetReps = 6,
        targetWeight = Weight.fromKilograms(100.0),
        sets = listOf(
            ExerciseSet(
                id = ExerciseSetId.generate(),
                setNumber = 1,
                reps = Reps.of(6),
                weight = Weight.fromKilograms(100.0),
                completedAt = Instant.now()
            )
        ),
        notes = "Deep squats",
        createdAt = Instant.now()
    )
    
    // Workout test data
    val sampleWorkout = WorkoutExample.createSampleWorkout().copy(
        userId = "test-user-id",
        name = "Upper Body Workout"
    )
    
    val completedWorkout = Workout(
        userId = "test-user-id",
        id = WorkoutId.generate(),
        name = "Push Day Workout",
        date = LocalDate.now(),
        exercises = listOf(benchPressExercise, squatExercise),
        status = WorkoutStatus.COMPLETED,
        startTime = Instant.now().minusSeconds(3600),
        endTime = Instant.now().minusSeconds(600),
        notes = "Great workout, felt strong",
        templateId = null,
        createdAt = Instant.now().minusSeconds(7200),
        updatedAt = Instant.now().minusSeconds(600)
    )
    
    // Sync status test data
    val idleSyncStatus = SyncStatus.Idle
    val syncingSyncStatus = SyncStatus.Syncing
    val successSyncStatus = SyncStatus.Success(syncedCount = 5)
    val errorSyncStatus = SyncStatus.Error("Network error")
    
    // Sample workouts for testing
    val inProgressWorkout = Workout(
        userId = "test-user-id",
        id = WorkoutId.generate(),
        name = "Current Training Session",
        date = LocalDate.now(),
        exercises = listOf(benchPressExercise),
        status = WorkoutStatus.IN_PROGRESS,
        startTime = Instant.now().minusSeconds(1800),
        endTime = null,
        notes = null,
        templateId = null,
        createdAt = Instant.now().minusSeconds(1800),
        updatedAt = Instant.now().minusSeconds(60)
    )
    
    val plannedWorkout = Workout(
        userId = "test-user-id",
        id = WorkoutId.generate(),
        name = "Tomorrow's Workout",
        date = LocalDate.now().plusDays(1),
        exercises = listOf(squatExercise),
        status = WorkoutStatus.PLANNED,
        startTime = null,
        endTime = null,
        notes = "Remember to warm up properly",
        templateId = WorkoutId.generate(),
        createdAt = Instant.now(),
        updatedAt = Instant.now()
    )
    
    // Factory methods for creating variations
    fun createWorkout(
        userId: String = "test-user-id",
        name: String = "Test Workout",
        status: WorkoutStatus = WorkoutStatus.PLANNED,
        exerciseCount: Int = 2
    ): Workout {
        val workoutId = WorkoutId.generate()
        val exercises = (0 until exerciseCount).map { index ->
            Exercise(
                id = ExerciseId.generate(),
                workoutId = workoutId,
                libraryExercise = if (index % 2 == 0) benchPressLibraryExercise else squatLibraryExercise,
                orderIndex = index,
                targetSets = 3,
                targetReps = 8,
                targetWeight = Weight.fromKilograms(75.0),
                sets = listOf(
                    ExerciseSet(
                        id = ExerciseSetId.generate(),
                        setNumber = 1,
                        reps = Reps.of(8),
                        weight = Weight.fromKilograms(75.0)
                    )
                ),
                notes = null,
                createdAt = Instant.now()
            )
        }
        
        return Workout(
            userId = userId,
            id = workoutId,
            name = name,
            date = LocalDate.now(),
            exercises = exercises,
            status = status,
            startTime = if (status == WorkoutStatus.IN_PROGRESS) Instant.now().minusSeconds(1800) else null,
            endTime = if (status == WorkoutStatus.COMPLETED) Instant.now().minusSeconds(300) else null,
            notes = null,
            templateId = null,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
    }
    
    fun createExercise(
        workoutId: WorkoutId = WorkoutId.generate(),
        libraryExercise: ExerciseLibrary = benchPressLibraryExercise,
        orderIndex: Int = 0,
        setsCount: Int = 3
    ): Exercise {
        val exerciseId = ExerciseId.generate()
        val sets = (1..setsCount).map { setNumber ->
            ExerciseSet(
                id = ExerciseSetId.generate(),
                setNumber = setNumber,
                reps = Reps.of(8),
                weight = Weight.fromKilograms(70.0)
            )
        }
        
        return Exercise(
            id = exerciseId,
            workoutId = workoutId,
            libraryExercise = libraryExercise,
            orderIndex = orderIndex,
            targetSets = setsCount,
            targetReps = 8,
            targetWeight = Weight.fromKilograms(70.0),
            sets = sets,
            notes = null,
            createdAt = Instant.now()
        )
    }
    
    fun createExerciseSet(
        setNumber: Int = 1,
        reps: Int = 8,
        weightKg: Double = 70.0,
        isCompleted: Boolean = false
    ): ExerciseSet {
        return ExerciseSet(
            id = ExerciseSetId.generate(),
            setNumber = setNumber,
            reps = Reps.of(reps),
            weight = Weight.fromKilograms(weightKg),
            completedAt = if (isCompleted) Instant.now() else null
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
            subscriptionTier = SubscriptionTier.FREE,
            subscriptionStatus = SubscriptionStatus.ACTIVE,
            subscriptionExpiresAt = null,
            premiumFeaturesEnabled = false,
            onboardingCompleted = !isAnonymous,
            profileVersion = 1L,
            createdAt = LocalDateTime.now().minusHours(1),
            lastSignInAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
    }
} 