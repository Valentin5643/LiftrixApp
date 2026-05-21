package com.example.liftrix.di

import com.example.liftrix.data.repository.CustomExerciseRepositoryImpl
import com.example.liftrix.data.repository.ExerciseLibraryRepositoryImpl
import com.example.liftrix.data.repository.FolderRepositoryImpl
import com.example.liftrix.data.repository.MetDataRepositoryImpl
import com.example.liftrix.data.repository.exercise.ExerciseRepositoryImpl
import com.example.liftrix.data.repository.sharing.TemplateShareRepositoryImpl
import com.example.liftrix.data.repository.template.TemplateRepositoryImpl
import com.example.liftrix.data.repository.workout.WorkoutRepositoryImpl
import com.example.liftrix.domain.repository.CustomExerciseRepository
import com.example.liftrix.domain.repository.ExerciseLibraryRepository
import com.example.liftrix.domain.repository.FolderRepository
import com.example.liftrix.domain.repository.MetDataRepository
import com.example.liftrix.domain.repository.exercise.ExerciseRepository
import com.example.liftrix.domain.repository.sharing.TemplateShareRepository
import com.example.liftrix.domain.repository.template.TemplateRepository
import com.example.liftrix.domain.repository.workout.PreviousSetRepository
import com.example.liftrix.domain.repository.workout.WorkoutAnalyticsDataRepository
import com.example.liftrix.domain.repository.workout.WorkoutFeedDataRepository
import com.example.liftrix.domain.repository.workout.WorkoutHistoryRepository
import com.example.liftrix.domain.repository.workout.WorkoutRepository
import com.example.liftrix.domain.repository.workout.WorkoutSyncStatusRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class WorkoutRepositoryModule {
    @Binds
    @Singleton
    abstract fun bindCustomExerciseRepository(impl: CustomExerciseRepositoryImpl): CustomExerciseRepository

    @Binds
    @Singleton
    abstract fun bindFolderRepository(impl: FolderRepositoryImpl): FolderRepository

    @Binds
    @Singleton
    abstract fun bindExerciseLibraryRepository(impl: ExerciseLibraryRepositoryImpl): ExerciseLibraryRepository

    @Binds
    @Singleton
    abstract fun bindMetDataRepository(impl: MetDataRepositoryImpl): MetDataRepository

    @Binds
    @Singleton
    abstract fun bindTemplateShareRepository(impl: TemplateShareRepositoryImpl): TemplateShareRepository

    @Binds
    @Singleton
    abstract fun bindExerciseRepository(impl: ExerciseRepositoryImpl): ExerciseRepository

    @Binds
    @Singleton
    abstract fun bindTemplateRepository(impl: TemplateRepositoryImpl): TemplateRepository

    @Binds
    @Singleton
    abstract fun bindWorkoutRepository(impl: WorkoutRepositoryImpl): WorkoutRepository

    @Binds
    @Singleton
    abstract fun bindWorkoutHistoryRepository(impl: WorkoutRepositoryImpl): WorkoutHistoryRepository

    @Binds
    @Singleton
    abstract fun bindWorkoutAnalyticsDataRepository(impl: WorkoutRepositoryImpl): WorkoutAnalyticsDataRepository

    @Binds
    @Singleton
    abstract fun bindWorkoutSyncStatusRepository(impl: WorkoutRepositoryImpl): WorkoutSyncStatusRepository

    @Binds
    @Singleton
    abstract fun bindWorkoutFeedDataRepository(impl: WorkoutRepositoryImpl): WorkoutFeedDataRepository

    @Binds
    @Singleton
    abstract fun bindPreviousSetRepository(impl: WorkoutRepositoryImpl): PreviousSetRepository
}
