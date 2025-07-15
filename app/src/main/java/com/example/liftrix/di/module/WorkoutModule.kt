package com.example.liftrix.di.module

import com.example.liftrix.data.repository.workout.WorkoutRepositoryImpl
import com.example.liftrix.domain.repository.workout.WorkoutRepository
import com.example.liftrix.domain.usecase.common.ErrorHandler
import com.example.liftrix.domain.usecase.workout.CreateWorkoutUseCase
import com.example.liftrix.domain.usecase.workout.GetWorkoutByIdUseCase
import dagger.Binds
import dagger.Module
import dagger.Provides
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

    companion object {
        
        /**
         * Provides CreateWorkoutUseCase with proper dependency injection.
         * 
         * This use case handles workout creation with comprehensive business logic
         * validation, error handling, and integration with centralized ErrorHandler.
         * 
         * @param workoutRepository The workout repository dependency
         * @param errorHandler The centralized error handler dependency
         * @return Configured CreateWorkoutUseCase instance
         */
        @Provides
        @Singleton
        fun provideCreateWorkoutUseCase(
            workoutRepository: WorkoutRepository,
            errorHandler: ErrorHandler
        ): CreateWorkoutUseCase {
            return CreateWorkoutUseCase(
                workoutRepository = workoutRepository,
                errorHandler = errorHandler
            )
        }

        /**
         * Provides GetWorkoutByIdUseCase with proper dependency injection.
         * 
         * This use case handles workout retrieval by ID with user authorization,
         * security validation, and proper error handling.
         * 
         * @param workoutRepository The workout repository dependency
         * @param errorHandler The centralized error handler dependency
         * @return Configured GetWorkoutByIdUseCase instance
         */
        @Provides
        @Singleton
        fun provideGetWorkoutByIdUseCase(
            workoutRepository: WorkoutRepository,
            errorHandler: ErrorHandler
        ): GetWorkoutByIdUseCase {
            return GetWorkoutByIdUseCase(
                workoutRepository = workoutRepository,
                errorHandler = errorHandler
            )
        }
    }
}