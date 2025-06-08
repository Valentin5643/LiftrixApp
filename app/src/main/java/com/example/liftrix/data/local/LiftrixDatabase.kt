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
import com.example.liftrix.data.local.entity.UserProfileEntity
import com.example.liftrix.data.local.entity.WorkoutEntity
import com.example.liftrix.data.local.entity.CustomExerciseEntity
import com.example.liftrix.data.local.entity.WorkoutTemplateEntity
import com.example.liftrix.data.local.entity.DailyWorkoutEntity
import com.example.liftrix.data.local.entity.ExerciseLibraryEntity

@Database(
    entities = [
        WorkoutEntity::class,
        UserProfileEntity::class,
        CustomExerciseEntity::class,
        WorkoutTemplateEntity::class,
        DailyWorkoutEntity::class,
        ExerciseLibraryEntity::class
    ],
    version = 5,
    exportSchema = false
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
} 