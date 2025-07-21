package com.example.liftrix.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.liftrix.data.local.converter.DateTimeConverters
import com.example.liftrix.data.local.converter.UserProfileConverters
import com.example.liftrix.data.local.converter.WorkoutConverters
import com.example.liftrix.data.local.converter.ExerciseConverters
import com.example.liftrix.data.local.converter.SubscriptionConverters
import com.example.liftrix.data.local.converter.WeightUnitConverter
import com.example.liftrix.data.local.dao.UserProfileDao
import com.example.liftrix.data.local.dao.WorkoutDao
import com.example.liftrix.data.local.dao.CustomExerciseDao
import com.example.liftrix.data.local.dao.WorkoutTemplateDao
import com.example.liftrix.data.local.dao.ExerciseLibraryDao
import com.example.liftrix.data.local.dao.ExerciseDao
import com.example.liftrix.data.local.dao.ExerciseSetDao
import com.example.liftrix.data.local.dao.ExerciseWeightMemoryDao
import com.example.liftrix.data.local.dao.ExerciseUsageHistoryDao
import com.example.liftrix.data.local.dao.FriendDao
import com.example.liftrix.data.local.dao.PrivacySettingsDao
import com.example.liftrix.data.local.dao.FolderDao
import com.example.liftrix.data.local.dao.SettingsDao
import com.example.liftrix.data.local.dao.SubscriptionDao
import com.example.liftrix.data.local.dao.MetDataDao
import com.example.liftrix.data.local.dao.AnalyticsCacheDao
import com.example.liftrix.data.local.dao.GuestSessionDao
import com.example.liftrix.data.local.dao.WorkoutAnomalyDao
import com.example.liftrix.data.local.dao.AnomalyDetectionSettingsDao
import com.example.liftrix.data.local.dao.ExerciseHistoryDao
import com.example.liftrix.data.local.dao.WidgetPreferencesDao

import com.example.liftrix.data.local.entity.UserProfileEntity
import com.example.liftrix.data.local.entity.FolderEntity
import com.example.liftrix.data.local.entity.SettingsEntity
import com.example.liftrix.data.local.entity.SubscriptionEntity
import com.example.liftrix.data.local.entity.WorkoutEntity
import com.example.liftrix.data.local.entity.CustomExerciseEntity
import com.example.liftrix.data.local.entity.WorkoutTemplateEntity
import com.example.liftrix.data.local.entity.ExerciseLibraryEntity
import com.example.liftrix.data.local.entity.ExerciseEntity
import com.example.liftrix.data.local.entity.ExerciseSetEntity
import com.example.liftrix.data.local.entity.ExerciseWeightMemoryEntity
import com.example.liftrix.data.local.entity.ExerciseUsageHistoryEntity
import com.example.liftrix.data.local.entity.FriendEntity
import com.example.liftrix.data.local.entity.PrivacySettingsEntity
import com.example.liftrix.data.local.entity.AnalyticsCacheEntity
import com.example.liftrix.data.local.entity.MetDataEntity
import com.example.liftrix.data.local.entity.GuestSessionEntity
import com.example.liftrix.data.local.entity.WorkoutAnomalyEntity
import com.example.liftrix.data.local.entity.AnomalyDetectionSettingsEntity
import com.example.liftrix.data.local.entity.ExerciseHistoryEntity
import com.example.liftrix.data.local.entity.WidgetPreferenceEntity
import com.example.liftrix.data.local.entity.DashboardConfigurationEntity



@Database(
    entities = [
        WorkoutEntity::class,
        UserProfileEntity::class,
        CustomExerciseEntity::class,
        WorkoutTemplateEntity::class,
        ExerciseLibraryEntity::class,
        ExerciseEntity::class,
        ExerciseSetEntity::class,
        ExerciseWeightMemoryEntity::class,
        ExerciseUsageHistoryEntity::class,
        FriendEntity::class,
        PrivacySettingsEntity::class,
        FolderEntity::class,
        SettingsEntity::class,
        SubscriptionEntity::class,
        AnalyticsCacheEntity::class,
        MetDataEntity::class,
        GuestSessionEntity::class,
        WorkoutAnomalyEntity::class,
        AnomalyDetectionSettingsEntity::class,
        ExerciseHistoryEntity::class,
        WidgetPreferenceEntity::class,
        DashboardConfigurationEntity::class,
    ],
    version = 34,
    exportSchema = true
)
@TypeConverters(
    DateTimeConverters::class,
    WorkoutConverters::class,
    UserProfileConverters::class,
    ExerciseConverters::class,
    SubscriptionConverters::class,
    WeightUnitConverter::class
)
abstract class LiftrixDatabase : RoomDatabase() {
    
    abstract fun workoutDao(): WorkoutDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun customExerciseDao(): CustomExerciseDao
    abstract fun workoutTemplateDao(): WorkoutTemplateDao
    abstract fun exerciseLibraryDao(): ExerciseLibraryDao
    abstract fun exerciseDao(): ExerciseDao
    abstract fun exerciseSetDao(): ExerciseSetDao
    abstract fun exerciseWeightMemoryDao(): ExerciseWeightMemoryDao
    abstract fun exerciseUsageHistoryDao(): ExerciseUsageHistoryDao
    abstract fun friendDao(): FriendDao
    abstract fun privacySettingsDao(): PrivacySettingsDao
    abstract fun folderDao(): FolderDao
    abstract fun settingsDao(): SettingsDao
    abstract fun subscriptionDao(): SubscriptionDao
    abstract fun metDataDao(): MetDataDao
    abstract fun analyticsCacheDao(): AnalyticsCacheDao
    abstract fun guestSessionDao(): GuestSessionDao
    abstract fun workoutAnomalyDao(): WorkoutAnomalyDao
    abstract fun anomalyDetectionSettingsDao(): AnomalyDetectionSettingsDao
    abstract fun exerciseHistoryDao(): ExerciseHistoryDao
    abstract fun widgetPreferencesDao(): WidgetPreferencesDao
} 