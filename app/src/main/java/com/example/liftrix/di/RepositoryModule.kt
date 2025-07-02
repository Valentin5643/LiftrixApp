package com.example.liftrix.di

import com.example.liftrix.data.repository.ActiveWorkoutSessionRepositoryImpl
import com.example.liftrix.data.repository.AuthRepositoryImpl
import com.example.liftrix.data.repository.CustomExerciseRepositoryImpl
import com.example.liftrix.data.repository.ExerciseLibraryRepositoryImpl
import com.example.liftrix.data.repository.ProfileRepositoryImpl
import com.example.liftrix.data.repository.ProgressStatsRepositoryImpl
import com.example.liftrix.data.repository.SocialRepositoryImpl
import com.example.liftrix.data.repository.WorkoutRepository
import com.example.liftrix.data.repository.WorkoutRepositoryImpl
import com.example.liftrix.data.repository.WorkoutTemplateRepositoryImpl
import com.example.liftrix.data.repository.ExerciseRepositoryImpl
import com.example.liftrix.domain.repository.ActiveWorkoutSessionRepository
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.repository.CustomExerciseRepository
import com.example.liftrix.domain.repository.ExerciseLibraryRepository
import com.example.liftrix.domain.repository.ProfileRepository
import com.example.liftrix.domain.repository.ProgressStatsRepository
import com.example.liftrix.domain.repository.SocialRepository
import com.example.liftrix.domain.repository.WorkoutTemplateRepository
import com.example.liftrix.domain.repository.ExerciseRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(
        authRepositoryImpl: AuthRepositoryImpl
    ): AuthRepository

    @Binds
    @Singleton
    abstract fun bindWorkoutRepository(
        workoutRepositoryImpl: WorkoutRepositoryImpl
    ): WorkoutRepository

    @Binds
    @Singleton
    abstract fun bindProfileRepository(
        profileRepositoryImpl: ProfileRepositoryImpl
    ): ProfileRepository

    @Binds
    @Singleton
    abstract fun bindCustomExerciseRepository(
        customExerciseRepositoryImpl: CustomExerciseRepositoryImpl
    ): CustomExerciseRepository

    @Binds
    @Singleton
    abstract fun bindExerciseLibraryRepository(
        exerciseLibraryRepositoryImpl: ExerciseLibraryRepositoryImpl
    ): ExerciseLibraryRepository

    @Binds
    @Singleton
    abstract fun bindProgressStatsRepository(
        progressStatsRepositoryImpl: ProgressStatsRepositoryImpl
    ): ProgressStatsRepository

    @Binds
    @Singleton
    abstract fun bindExerciseRepository(
        exerciseRepositoryImpl: ExerciseRepositoryImpl
    ): ExerciseRepository

    @Binds
    @Singleton
    abstract fun bindWorkoutTemplateRepository(
        workoutTemplateRepositoryImpl: WorkoutTemplateRepositoryImpl
    ): WorkoutTemplateRepository

    @Binds
    @Singleton
    abstract fun bindSocialRepository(
        socialRepositoryImpl: SocialRepositoryImpl
    ): SocialRepository

    @Binds
    @Singleton
    abstract fun bindActiveWorkoutSessionRepository(
        activeWorkoutSessionRepositoryImpl: ActiveWorkoutSessionRepositoryImpl
    ): ActiveWorkoutSessionRepository

} 