package com.example.liftrix.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SessionModule {
    @Provides
    fun provideStartWorkoutSessionUseCase(
        sessionManager: com.example.liftrix.service.UnifiedWorkoutSessionManager,
        workoutTemplateRepository: com.example.liftrix.domain.repository.template.TemplateRepository
    ): com.example.liftrix.domain.usecase.session.StartWorkoutSessionUseCase =
        com.example.liftrix.domain.usecase.session.StartWorkoutSessionUseCase(sessionManager, workoutTemplateRepository)

    @Provides
    @Singleton
    fun provideUnifiedWorkoutSessionManager(
        @ApplicationContext context: android.content.Context,
        completeWorkoutSessionUseCase: com.example.liftrix.domain.usecase.session.CompleteWorkoutSessionUseCase,
        authRepository: com.example.liftrix.domain.repository.AuthRepository
    ): com.example.liftrix.service.UnifiedWorkoutSessionManager =
        com.example.liftrix.service.UnifiedWorkoutSessionManager(
            context = context,
            completeWorkoutSessionUseCase = completeWorkoutSessionUseCase,
            authRepository = authRepository
        )

    @Provides
    @Singleton
    fun provideWorkoutSessionManagerPort(
        sessionManager: com.example.liftrix.service.UnifiedWorkoutSessionManager
    ): com.example.liftrix.service.WorkoutSessionManagerPort = sessionManager

    @Provides
    @Singleton
    fun provideStateCleanupManager(): com.example.liftrix.domain.service.SessionStateCleanup =
        com.example.liftrix.ui.common.state.StateCleanupManager()
}
