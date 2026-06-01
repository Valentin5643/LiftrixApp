package com.example.liftrix.di

import com.example.liftrix.data.local.dao.CustomExerciseDao
import com.example.liftrix.data.local.dao.WorkoutDao
import com.example.liftrix.data.mapper.WorkoutPostMapper
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DaoModule {
    @Provides
    @Singleton
    fun provideWorkoutPostMapper(
        workoutDao: WorkoutDao,
        customExerciseDao: CustomExerciseDao
    ): WorkoutPostMapper = WorkoutPostMapper(workoutDao, customExerciseDao)
}
