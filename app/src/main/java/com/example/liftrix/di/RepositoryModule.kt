package com.example.liftrix.di

import com.example.liftrix.data.repository.AuthRepositoryImpl
import com.example.liftrix.data.repository.CustomExerciseRepositoryImpl
import com.example.liftrix.data.repository.ProfileRepositoryImpl
import com.example.liftrix.data.repository.ProgressStatsRepositoryImpl
import com.example.liftrix.data.repository.SocialRepositoryImpl
import com.example.liftrix.data.repository.WorkoutTemplateRepositoryImpl
import com.example.liftrix.data.repository.ExerciseLibraryRepositoryImpl
import com.example.liftrix.data.repository.FolderRepositoryImpl
import com.example.liftrix.data.repository.GuestSessionRepositoryImpl
import com.example.liftrix.data.repository.AnomalyDetectionRepositoryImpl
import com.example.liftrix.data.repository.MetDataRepositoryImpl
import com.example.liftrix.data.repository.WidgetPreferencesRepositoryImpl
import com.example.liftrix.data.repository.UserRepositoryImpl
import com.example.liftrix.data.repository.UserSearchRepositoryImpl
import com.example.liftrix.data.repository.AchievementRepositoryImpl
import com.example.liftrix.data.repository.PRNotificationRepositoryImpl
import com.example.liftrix.data.repository.social.BlockRepositoryImpl
import com.example.liftrix.data.repository.social.ReportRepositoryImpl
import com.example.liftrix.data.repository.SyncPreferencesRepositoryImpl
import com.example.liftrix.data.repository.PersonalRecordRepositoryImpl
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.repository.CustomExerciseRepository
import com.example.liftrix.domain.repository.ProfileRepository
import com.example.liftrix.domain.repository.ProgressStatsRepository
import com.example.liftrix.domain.repository.SocialRepository
import com.example.liftrix.domain.repository.WorkoutTemplateRepository
import com.example.liftrix.domain.repository.ExerciseLibraryRepository
import com.example.liftrix.domain.repository.FolderRepository
import com.example.liftrix.domain.repository.GuestSessionRepository
import com.example.liftrix.domain.repository.AnomalyDetectionRepository
import com.example.liftrix.domain.repository.MetDataRepository
import com.example.liftrix.domain.repository.WidgetPreferencesRepository
import com.example.liftrix.domain.repository.UserRepository
import com.example.liftrix.domain.repository.UserSearchRepository
import com.example.liftrix.domain.repository.AchievementRepository
import com.example.liftrix.domain.repository.PRNotificationRepository
import com.example.liftrix.domain.repository.social.BlockRepository
import com.example.liftrix.domain.repository.social.ReportRepository
import com.example.liftrix.domain.repository.SyncPreferencesRepository
import com.example.liftrix.domain.repository.PersonalRecordRepository
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
    abstract fun bindProgressStatsRepository(
        progressStatsRepositoryImpl: ProgressStatsRepositoryImpl
    ): ProgressStatsRepository


    @Binds
    @Singleton
    abstract fun bindSocialRepository(
        socialRepositoryImpl: SocialRepositoryImpl
    ): SocialRepository


    @Binds
    @Singleton
    abstract fun bindFolderRepository(
        folderRepositoryImpl: FolderRepositoryImpl
    ): FolderRepository

    // Legacy compatibility bindings for gradual migration
    
    @Binds
    @Singleton
    abstract fun bindWorkoutTemplateRepository(
        workoutTemplateRepositoryImpl: WorkoutTemplateRepositoryImpl
    ): WorkoutTemplateRepository

    @Binds
    @Singleton
    abstract fun bindExerciseLibraryRepository(
        exerciseLibraryRepositoryImpl: ExerciseLibraryRepositoryImpl
    ): ExerciseLibraryRepository

    @Binds
    @Singleton
    abstract fun bindGuestSessionRepository(
        guestSessionRepositoryImpl: GuestSessionRepositoryImpl
    ): GuestSessionRepository

    @Binds
    @Singleton
    abstract fun bindAnomalyDetectionRepository(
        anomalyDetectionRepositoryImpl: AnomalyDetectionRepositoryImpl
    ): AnomalyDetectionRepository

    @Binds
    @Singleton
    abstract fun bindMetDataRepository(
        metDataRepositoryImpl: MetDataRepositoryImpl
    ): MetDataRepository

    @Binds
    @Singleton
    abstract fun bindWidgetPreferencesRepository(
        widgetPreferencesRepositoryImpl: WidgetPreferencesRepositoryImpl
    ): WidgetPreferencesRepository


    @Binds
    @Singleton
    abstract fun bindUserRepository(
        userRepositoryImpl: UserRepositoryImpl
    ): UserRepository

    @Binds
    @Singleton
    abstract fun bindUserSearchRepository(
        userSearchRepositoryImpl: UserSearchRepositoryImpl
    ): UserSearchRepository

    @Binds
    @Singleton
    abstract fun bindAchievementRepository(
        achievementRepositoryImpl: AchievementRepositoryImpl
    ): AchievementRepository

    @Binds
    @Singleton
    abstract fun bindPRNotificationRepository(
        prNotificationRepositoryImpl: PRNotificationRepositoryImpl
    ): PRNotificationRepository

    @Binds
    @Singleton
    abstract fun bindBlockRepository(
        blockRepositoryImpl: BlockRepositoryImpl
    ): BlockRepository

    @Binds
    @Singleton
    abstract fun bindReportRepository(
        reportRepositoryImpl: ReportRepositoryImpl
    ): ReportRepository

    @Binds
    @Singleton
    abstract fun bindSyncPreferencesRepository(
        syncPreferencesRepositoryImpl: SyncPreferencesRepositoryImpl
    ): SyncPreferencesRepository

    @Binds
    @Singleton
    abstract fun bindPersonalRecordRepository(
        personalRecordRepositoryImpl: PersonalRecordRepositoryImpl
    ): PersonalRecordRepository

} 