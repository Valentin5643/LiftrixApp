package com.example.liftrix.domain.model

import java.time.Instant
import java.time.LocalDate

/**
 * Example demonstrating how to create and use workout models with validation
 */
object WorkoutExample {
    
    /**
     * Creates a sample workout with proper validation
     */
    fun createSampleWorkout(): Workout {
        val now = Instant.now()
        
        // Create exercise sets with validation
        val benchPressSet1 = ExerciseSet(
            setNumber = 1,
            weight = Weight.fromKilograms(80.0),
            reps = Reps.of(10),
            isCompleted = true,
            restTimeSeconds = 120,
            completedAt = now
        )
        
        val benchPressSet2 = ExerciseSet(
            setNumber = 2,
            weight = Weight.fromKilograms(85.0),
            reps = Reps.of(8),
            isCompleted = true,
            restTimeSeconds = 120,
            completedAt = now.plusSeconds(180)
        )
        
        val benchPressSet3 = ExerciseSet(
            setNumber = 3,
            weight = Weight.fromKilograms(90.0),
            reps = Reps.of(6),
            isCompleted = false
        )
        
        // Create bench press exercise
        val benchPress = Exercise(
            id = ExerciseId.generate(),
            name = "Bench Press",
            category = ExerciseCategory.CHEST,
            sets = listOf(benchPressSet1, benchPressSet2, benchPressSet3),
            notes = "Focus on controlled movement",
            targetSets = 3,
            targetReps = Reps.of(8),
            targetWeight = Weight.fromKilograms(85.0),
            createdAt = now,
            updatedAt = now
        )
        
        // Create squat sets
        val squatSet1 = ExerciseSet(
            setNumber = 1,
            weight = Weight.fromKilograms(100.0),
            reps = Reps.of(12),
            isCompleted = true,
            restTimeSeconds = 180,
            completedAt = now.plusSeconds(300)
        )
        
        val squatSet2 = ExerciseSet(
            setNumber = 2,
            weight = Weight.fromKilograms(110.0),
            reps = Reps.of(10),
            isCompleted = true,
            restTimeSeconds = 180,
            completedAt = now.plusSeconds(600)
        )
        
        // Create squat exercise
        val squat = Exercise(
            id = ExerciseId.generate(),
            name = "Back Squat",
            category = ExerciseCategory.LEGS,
            sets = listOf(squatSet1, squatSet2),
            notes = "Keep chest up, knees tracking over toes",
            targetSets = 3,
            targetReps = Reps.of(10),
            targetWeight = Weight.fromKilograms(105.0),
            createdAt = now,
            updatedAt = now
        )
        
        // Create workout
        return Workout(
            userId = "sample-user-id",
            id = WorkoutId.generate(),
            name = "Upper/Lower Split - Day 1",
            date = LocalDate.now(),
            exercises = listOf(benchPress, squat),
            status = WorkoutStatus.IN_PROGRESS,
            startTime = now.minusSeconds(900), // Started 15 minutes ago
            endTime = null,
            notes = "Feeling strong today, might increase weight next session",
            templateId = null,
            createdAt = now.minusSeconds(900),
            updatedAt = now
        )
    }
    
    /**
     * Demonstrates workout operations and calculations
     */
    fun demonstrateWorkoutOperations() {
        val workout = createSampleWorkout()
        
        println("=== Workout Statistics ===")
        println("Workout: ${workout.name}")
        println("Date: ${workout.date}")
        println("Status: ${workout.status.displayName}")
        println("Total exercises: ${workout.exercises.size}")
        println("Total sets: ${workout.getTotalSets()}")
        println("Completed sets: ${workout.getCompletedSets()}")
        println("Completion: ${String.format("%.1f", workout.getCompletionPercentage())}%")
        println("Total volume: ${String.format("%.1f", workout.calculateTotalVolume().kilograms)} kg")
        println("Total reps completed: ${workout.getTotalRepsCompleted().count}")
        println("Duration: ${workout.getDuration()?.toMinutes() ?: "In progress"} minutes")
        println("Categories: ${workout.getExerciseCategories().joinToString { it.displayName }}")
        
        println("\n=== Exercise Details ===")
        workout.exercises.forEach { exercise ->
            println("\n${exercise.name} (${exercise.category.displayName}):")
            println("  Sets completed: ${exercise.getCompletedSetsCount()}/${exercise.sets.size}")
            println("  Total volume: ${String.format("%.1f", exercise.calculateTotalVolume().kilograms)} kg")
            println("  Max weight: ${exercise.getMaxWeight()?.kilograms ?: "N/A"} kg")
            println("  Total reps: ${exercise.getTotalRepsCompleted().count}")
            println("  Completed: ${if (exercise.isCompleted()) "Yes" else "No"}")
            
            exercise.sets.forEach { set ->
                val status = if (set.isCompleted) "✓" else "○"
                println("    Set ${set.setNumber}: $status ${set.weight.kilograms}kg × ${set.reps.count}")
            }
        }
        
        println("\n=== Workout Operations ===")
        
        // Add a new set to bench press
        val benchPress = workout.exercises.first { it.name == "Bench Press" }
        val updatedBenchPress = benchPress.addSet(
            Weight.fromKilograms(75.0),
            Reps.of(12)
        )
        println("Added new set to bench press: ${updatedBenchPress.sets.last().weight.kilograms}kg × ${updatedBenchPress.sets.last().reps.count}")
        
        // Complete the workout
        val completedWorkout = workout.complete()
        println("Workout completed at: ${completedWorkout.endTime}")
        println("Final status: ${completedWorkout.status.displayName}")
    }
    
    /**
     * Demonstrates validation scenarios
     */
    fun demonstrateValidation() {
        println("\n=== Validation Examples ===")
        
        try {
            // This will throw an exception - negative weight
            Weight.fromKilograms(-10.0)
        } catch (e: IllegalArgumentException) {
            println("✓ Caught negative weight validation: ${e.message}")
        }
        
        try {
            // This will throw an exception - too many reps
            Reps.of(2000)
        } catch (e: IllegalArgumentException) {
            println("✓ Caught excessive reps validation: ${e.message}")
        }
        
        try {
            // This will throw an exception - blank exercise name
            Exercise(
                id = ExerciseId.generate(),
                name = "",
                category = ExerciseCategory.CHEST,
                sets = listOf(
                    ExerciseSet(
                        setNumber = 1,
                        weight = Weight.fromKilograms(50.0),
                        reps = Reps.of(10)
                    )
                ),
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )
        } catch (e: IllegalArgumentException) {
            println("✓ Caught blank exercise name validation: ${e.message}")
        }
        
        println("All validations working correctly!")
    }
} 