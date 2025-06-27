package com.example.liftrix.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.liftrix.data.local.converter.DateTimeConverters
import com.example.liftrix.data.local.converter.UserProfileConverters
import com.example.liftrix.data.local.converter.WorkoutConverters
import com.example.liftrix.data.local.converter.ExerciseConverters
import com.example.liftrix.data.local.dao.UserProfileDao
import com.example.liftrix.data.local.dao.WorkoutDao
import com.example.liftrix.data.local.dao.CustomExerciseDao
import com.example.liftrix.data.local.dao.WorkoutTemplateDao
import com.example.liftrix.data.local.dao.DailyWorkoutDao
import com.example.liftrix.data.local.dao.ExerciseLibraryDao
import com.example.liftrix.data.local.dao.ExerciseDao
import com.example.liftrix.data.local.dao.ExerciseSetDao
import com.example.liftrix.data.local.dao.ExerciseWeightMemoryDao
import com.example.liftrix.data.local.dao.ExerciseUsageHistoryDao

import com.example.liftrix.data.local.entity.UserProfileEntity
import com.example.liftrix.data.local.entity.WorkoutEntity
import com.example.liftrix.data.local.entity.CustomExerciseEntity
import com.example.liftrix.data.local.entity.WorkoutTemplateEntity
import com.example.liftrix.data.local.entity.DailyWorkoutEntity
import com.example.liftrix.data.local.entity.ExerciseLibraryEntity
import com.example.liftrix.data.local.entity.ExerciseEntity
import com.example.liftrix.data.local.entity.ExerciseSetEntity
import com.example.liftrix.data.local.entity.ExerciseWeightMemoryEntity
import com.example.liftrix.data.local.entity.ExerciseUsageHistoryEntity

import com.example.liftrix.data.local.migration.MIGRATION_6_7
import com.example.liftrix.data.local.migration.MIGRATION_7_8
import com.example.liftrix.data.local.migration.MIGRATION_8_9
import com.example.liftrix.data.local.migration.MIGRATION_9_10
import com.example.liftrix.data.local.migration.MIGRATION_10_11
import com.example.liftrix.data.local.migration.MIGRATION_11_12
import com.example.liftrix.data.local.migration.MIGRATION_12_13
import com.example.liftrix.data.local.migration.MIGRATION_13_14

@Database(
    entities = [
        WorkoutEntity::class,
        UserProfileEntity::class,
        CustomExerciseEntity::class,
        WorkoutTemplateEntity::class,
        DailyWorkoutEntity::class,
        ExerciseLibraryEntity::class,
        ExerciseEntity::class,
        ExerciseSetEntity::class,
        ExerciseWeightMemoryEntity::class,
        ExerciseUsageHistoryEntity::class,
    ],
    version = 14,
    exportSchema = true
)
@TypeConverters(
    DateTimeConverters::class,
    WorkoutConverters::class,
    UserProfileConverters::class,
    ExerciseConverters::class
)
abstract class LiftrixDatabase : RoomDatabase() {
    
    abstract fun workoutDao(): WorkoutDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun customExerciseDao(): CustomExerciseDao
    abstract fun workoutTemplateDao(): WorkoutTemplateDao
    abstract fun dailyWorkoutDao(): DailyWorkoutDao
    abstract fun exerciseLibraryDao(): ExerciseLibraryDao
    abstract fun exerciseDao(): ExerciseDao
    abstract fun exerciseSetDao(): ExerciseSetDao
    abstract fun exerciseWeightMemoryDao(): ExerciseWeightMemoryDao
    abstract fun exerciseUsageHistoryDao(): ExerciseUsageHistoryDao
} 