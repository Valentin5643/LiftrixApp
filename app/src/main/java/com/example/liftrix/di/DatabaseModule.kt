package com.example.liftrix.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
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
import com.example.liftrix.data.local.seed.ExerciseLibrarySeedData

import com.example.liftrix.data.local.migration.MIGRATION_6_7
import com.example.liftrix.data.local.migration.MIGRATION_7_8
import com.example.liftrix.data.local.migration.MIGRATION_8_9
import com.example.liftrix.data.local.migration.MIGRATION_9_10
import com.example.liftrix.data.local.migration.MIGRATION_10_11
import com.example.liftrix.data.local.migration.MIGRATION_11_12
import com.example.liftrix.data.local.migration.MIGRATION_12_13
import com.example.liftrix.data.local.migration.MIGRATION_13_14
import com.example.liftrix.data.local.migration.MIGRATION_14_15
import com.example.liftrix.data.local.migration.MIGRATION_15_16
import com.example.liftrix.data.local.MigrationValidator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.asExecutor
import timber.log.Timber
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideLiftrixDatabase(
        @ApplicationContext context: Context,
        exerciseLibrarySeedData: ExerciseLibrarySeedData
    ): LiftrixDatabase {
        // Validate migration chain at build time
        val availableMigrations = listOf(
            6 to 7, 7 to 8, 8 to 9, 9 to 10, 10 to 11, 11 to 12, 12 to 13, 13 to 14, 14 to 15, 15 to 16
        )
        MigrationValidator.validateMigrationChain(16, availableMigrations)
        
        lateinit var database: LiftrixDatabase
        
        database = Room.databaseBuilder(
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
                MIGRATION_13_14,
                MIGRATION_14_15,
                MIGRATION_15_16
            )
            .setTransactionExecutor(Dispatchers.IO.asExecutor())
            .setQueryExecutor(Dispatchers.IO.asExecutor())
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    Timber.i("🏗️ Database created from scratch at version 16")
                }
                
                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    Timber.d("📖 Database connection opened (routine operation)")
                }
            })
            .fallbackToDestructiveMigrationOnDowngrade(true)
            .build()
            
        // Pre-warm the database to establish stable connection and complete any migrations
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                // Force database initialization and verify version
                val version = database.openHelper.readableDatabase.version
                Timber.i("🔥 Database pre-warmed at version: $version")
                
                // Verify critical tables exist
                val cursor = database.openHelper.readableDatabase.query(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name='exercise_usage_history'"
                )
                val tableExists = cursor.moveToFirst()
                cursor.close()
                
                if (version == 16 && tableExists) {
                    Timber.i("✅ Database ready - all migrations complete, exercise_usage_history confirmed")
                } else {
                    Timber.w("⚠️ Database initialization issue - version: $version, table exists: $tableExists")
                }
                
                // Populate exercise library if needed
                exerciseLibrarySeedData.populateExerciseLibraryIfNeeded(database)
                
            } catch (e: Exception) {
                Timber.e(e, "❌ Database pre-warming failed")
            }
        }
            
        return database
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