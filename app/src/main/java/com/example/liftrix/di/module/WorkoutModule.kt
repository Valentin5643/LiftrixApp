package com.example.liftrix.di.module

import com.example.liftrix.data.repository.workout.WorkoutRepositoryImpl
import com.example.liftrix.domain.repository.workout.WorkoutRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for workout feature dependencies.
 * 
 * This module encapsulates all dependency injection requirements for the workout domain:
 * - Repository interface binding
 * - Use case provider functions
 * - Feature-specific scoping
 * 
 * Following the feature-based DI organization pattern for improved modularity
 * and maintainability. All workout-related dependencies are centralized here.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class WorkoutModule {

    /**
     * Binds the concrete WorkoutRepositoryImpl to the WorkoutRepository interface.
     * 
     * This enables dependency injection of the repository interface throughout
     * the application while keeping the implementation details abstracted.
     * 
     * @param workoutRepositoryImpl The concrete repository implementation
     * @return The repository interface bound to the implementation
     */
    @Binds
    @Singleton
    abstract fun bindWorkoutRepository(
        workoutRepositoryImpl: WorkoutRepositoryImpl
    ): WorkoutRepository

    // Note: WorkoutQueryUseCase and WorkoutCommandUseCase are provided automatically via @Inject constructor
    // Legacy use cases deleted (CLEANUP-002 completed):
    //   - CreateWorkoutUseCase
    //   - GetWorkoutSessionForEditingUseCase
    //   - SaveWorkoutUseCase
    //   - EstimateWorkoutDurationUseCase
    //   - GetWorkoutHistoryUseCase
    // All functionality consolidated into WorkoutQueryUseCase and WorkoutCommandUseCase
}