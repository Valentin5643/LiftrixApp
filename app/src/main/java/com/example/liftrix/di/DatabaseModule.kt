package com.example.liftrix.di

import android.content.Context
import androidx.room.Room
import com.example.liftrix.data.local.LiftrixDatabase
import com.example.liftrix.data.local.dao.CustomExerciseDao
import com.example.liftrix.data.local.dao.ExerciseLibraryDao
import com.example.liftrix.data.local.dao.UserProfileDao
import com.example.liftrix.data.local.dao.WorkoutDao
import com.example.liftrix.data.local.dao.ExerciseDao
import com.example.liftrix.data.local.dao.ExerciseSetDao
import com.example.liftrix.data.local.dao.ExerciseWeightMemoryDao
import com.example.liftrix.data.local.dao.ExerciseUsageHistoryDao
import com.example.liftrix.data.local.dao.DailyWorkoutDao
import com.example.liftrix.data.local.dao.WorkoutTemplateDao

import com.example.liftrix.data.local.migration.MIGRATION_6_7
import com.example.liftrix.data.local.migration.MIGRATION_7_8
import com.example.liftrix.data.local.migration.MIGRATION_8_9
import com.example.liftrix.data.local.migration.MIGRATION_9_10
import com.example.liftrix.data.local.migration.MIGRATION_10_11
import com.example.liftrix.data.local.migration.MIGRATION_11_12
import com.example.liftrix.data.local.migration.MIGRATION_12_13
import com.example.liftrix.data.local.migration.MIGRATION_13_14
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
            .addMigrations(
                MIGRATION_6_7, 
                MIGRATION_7_8, 
                MIGRATION_8_9,
                MIGRATION_9_10,
                MIGRATION_10_11,
                MIGRATION_11_12,
                MIGRATION_12_13,
                MIGRATION_13_14
            )
            .enableMultiInstanceInvalidation()
            .fallbackToDestructiveMigrationOnDowngrade(true)
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

    @Provides
    fun provideExerciseDao(database: LiftrixDatabase): ExerciseDao {
        return database.exerciseDao()
    }

    @Provides
    fun provideExerciseSetDao(database: LiftrixDatabase): ExerciseSetDao {
        return database.exerciseSetDao()
    }

    @Provides
    fun provideExerciseWeightMemoryDao(database: LiftrixDatabase): ExerciseWeightMemoryDao {
        return database.exerciseWeightMemoryDao()
    }

    @Provides
    fun provideDailyWorkoutDao(database: LiftrixDatabase): DailyWorkoutDao {
        return database.dailyWorkoutDao()
    }

    @Provides
    fun provideWorkoutTemplateDao(database: LiftrixDatabase): WorkoutTemplateDao {
        return database.workoutTemplateDao()
    }

    @Provides
    fun provideExerciseUsageHistoryDao(database: LiftrixDatabase): ExerciseUsageHistoryDao {
        return database.exerciseUsageHistoryDao()
    }


} 