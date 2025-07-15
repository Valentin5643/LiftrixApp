package com.example.liftrix.di

import com.example.liftrix.domain.usecase.session.AddExerciseToSessionUseCase
import com.example.liftrix.domain.usecase.session.StartWorkoutSessionUseCase
import com.example.liftrix.service.UnifiedWorkoutSessionManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 🔥 NEW: Dependency injection module for unified workout session system
 * 
 * This module provides the dependencies for the new unified workout session
 * architecture. It replaces the complex multi-manager system with a single
 * unified approach.
 * 
 * Key features:
 * - Single session manager instance
 * - Session-scoped use cases
 * - Clean dependency graph
 * - Proper singleton management
 */
@Module
@InstallIn(SingletonComponent::class)
object UnifiedWorkoutSessionModule {
    
    @Provides
    @Singleton
    fun provideUnifiedWorkoutSessionManager(
        context: android.content.Context,
        workoutRepository: com.example.liftrix.domain.repository.workout.WorkoutRepository
    ): UnifiedWorkoutSessionManager {
        return UnifiedWorkoutSessionManager(context, workoutRepository)
    }
    
    @Provides
    fun provideStartWorkoutSessionUseCase(
        sessionManager: UnifiedWorkoutSessionManager,
        workoutTemplateRepository: com.example.liftrix.domain.repository.WorkoutTemplateRepository
    ): StartWorkoutSessionUseCase {
        return StartWorkoutSessionUseCase(sessionManager, workoutTemplateRepository)
    }
    
    @Provides
    fun provideAddExerciseToSessionUseCase(
        sessionManager: UnifiedWorkoutSessionManager,
        exerciseRepository: com.example.liftrix.domain.repository.exercise.ExerciseRepository
    ): AddExerciseToSessionUseCase {
        return AddExerciseToSessionUseCase(sessionManager, exerciseRepository)
    }
}