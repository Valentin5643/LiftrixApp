package com.example.liftrix.di

import android.content.Context
import androidx.room.Room
import com.example.liftrix.data.local.LiftrixDatabase
import com.example.liftrix.data.local.dao.CustomExerciseDao
import com.example.liftrix.data.local.dao.ExerciseLibraryDao
import com.example.liftrix.data.local.dao.UserProfileDao
import com.example.liftrix.data.local.dao.WorkoutDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideLiftrixDatabase(@ApplicationContext context: Context): LiftrixDatabase {
        return Room.databaseBuilder(
            context,
            LiftrixDatabase::class.java,
            "liftrix_database"
        )
            .enableMultiInstanceInvalidation()
            .fallbackToDestructiveMigration() // For development - remove in production
            .build()
    }

    @Provides
    fun provideWorkoutDao(database: LiftrixDatabase): WorkoutDao {
        return database.workoutDao()
    }

    @Provides
    fun provideUserProfileDao(database: LiftrixDatabase): UserProfileDao {
        return database.userProfileDao()
    }

    @Provides
    fun provideCustomExerciseDao(database: LiftrixDatabase): CustomExerciseDao {
        return database.customExerciseDao()
    }

    @Provides
    fun provideExerciseLibraryDao(database: LiftrixDatabase): ExerciseLibraryDao {
        return database.exerciseLibraryDao()
    }
} 