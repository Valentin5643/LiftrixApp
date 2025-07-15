package com.example.liftrix.di.module

import com.example.liftrix.data.repository.exercise.ExerciseRepositoryImpl
import com.example.liftrix.domain.repository.exercise.ExerciseRepository
import com.example.liftrix.domain.repository.CustomExerciseRepository
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.usecase.common.ErrorHandler
import com.example.liftrix.domain.usecase.exercise.SearchExercisesUseCase
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for exercise feature dependencies.
 * 
 * This module provides exercise-specific dependency injection requirements:
 * - Repository interface binding
 * - Use case provider functions
 * - Feature-specific scoping
 * 
 * Following the feature-based DI organization pattern for improved modularity
 * and maintainability. All exercise-related dependencies are centralized here.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ExerciseModule {

    /**
     * Binds the concrete ExerciseRepositoryImpl to the ExerciseRepository interface.
     * 
     * This enables dependency injection of the repository interface throughout
     * the application while keeping the implementation details abstracted.
     * 
     * @param exerciseRepositoryImpl The concrete repository implementation
     * @return The repository interface bound to the implementation
     */
    @Binds
    @Singleton
    abstract fun bindExerciseRepository(
        exerciseRepositoryImpl: ExerciseRepositoryImpl
    ): ExerciseRepository

    companion object {
        
        /**
         * Provides SearchExercisesUseCase with all required dependencies.
         * 
         * The SearchExercisesUseCase requires multiple repositories and services
         * that are provided by other modules, so we use a @Provides method
         * to inject all the dependencies correctly.
         * 
         * @param exerciseRepository Repository for exercise library operations
         * @param customExerciseRepository Repository for custom exercise operations
         * @param authRepository Repository for authentication operations
         * @param errorHandler Service for centralized error handling
         * @return Configured SearchExercisesUseCase instance
         */
        @Provides
        @Singleton
        fun provideSearchExercisesUseCase(
            exerciseRepository: ExerciseRepository,
            customExerciseRepository: CustomExerciseRepository,
            authRepository: AuthRepository,
            errorHandler: ErrorHandler
        ): SearchExercisesUseCase {
            return SearchExercisesUseCase(
                exerciseRepository = exerciseRepository,
                customExerciseRepository = customExerciseRepository,
                authRepository = authRepository,
                errorHandler = errorHandler
            )
        }
    }
}