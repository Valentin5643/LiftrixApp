package com.example.liftrix.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsDomainModule {
    @Binds
    @Singleton
    abstract fun bindWeightMemoryService(
        impl: com.example.liftrix.data.service.WeightMemoryServiceImpl
    ): com.example.liftrix.domain.service.WeightMemoryService

    @Binds
    @Singleton
    abstract fun bindBackupService(
        impl: com.example.liftrix.data.service.BackupServiceImpl
    ): com.example.liftrix.domain.repository.backup.BackupService

    @Binds
    @Singleton
    abstract fun bindSyncService(
        impl: com.example.liftrix.data.service.SyncServiceImpl
    ): com.example.liftrix.domain.repository.sync.SyncService

    @Binds
    @Singleton
    abstract fun bindHelpCenterService(
        impl: com.example.liftrix.data.service.HelpCenterServiceImpl
    ): com.example.liftrix.domain.service.HelpCenterService

    @Binds
    @Singleton
    abstract fun bindLegalDocumentService(
        impl: com.example.liftrix.data.service.LegalDocumentServiceImpl
    ): com.example.liftrix.domain.service.LegalDocumentService

    @Binds
    @Singleton
    abstract fun bindSupportService(
        impl: com.example.liftrix.data.service.SupportServiceImpl
    ): com.example.liftrix.domain.service.SupportService

    @Binds
    @Singleton
    abstract fun bindAppInfoService(
        impl: com.example.liftrix.data.service.AppInfoServiceImpl
    ): com.example.liftrix.domain.service.AppInfoService

    @Binds
    @Singleton
    abstract fun bindAdminFirebaseService(
        impl: com.example.liftrix.data.service.AdminFirebaseServiceImpl
    ): com.example.liftrix.domain.service.AdminFirebaseService

    @Binds
    @Singleton
    abstract fun bindDemoModeSettingsPort(
        impl: com.example.liftrix.di.feature.settings.DemoModeSettingsAdapter
    ): com.example.liftrix.feature.settings.ports.DemoModeSettingsPort

    companion object {
        @Provides
        @Singleton
        fun provideFeatureFlagService(
            firebaseRemoteConfig: com.google.firebase.remoteconfig.FirebaseRemoteConfig,
            @IoDispatcher ioDispatcher: kotlinx.coroutines.CoroutineDispatcher
        ): com.example.liftrix.service.FeatureFlagService =
            com.example.liftrix.service.FeatureFlagServiceImpl(
                firebaseRemoteConfig = firebaseRemoteConfig,
                dispatcher = ioDispatcher
            )

        @Provides
        @Singleton
        fun provideWeightUnitManager(
            settingsRepository: com.example.liftrix.domain.repository.SettingsRepository,
            authQueryUseCase: com.example.liftrix.domain.usecase.auth.AuthQueryUseCase
        ): com.example.liftrix.domain.service.WeightUnitManager =
            com.example.liftrix.domain.service.WeightUnitManager(settingsRepository, authQueryUseCase)

        @Provides
        @Singleton
        fun provideSettingsPersistenceManager(
            dataStore: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>,
            settingsDao: com.example.liftrix.data.local.dao.SettingsDao,
            firestore: com.google.firebase.firestore.FirebaseFirestore,
            auditDao: com.example.liftrix.data.local.dao.SettingsAuditDao
        ): com.example.liftrix.domain.service.SettingsPersistenceManager =
            com.example.liftrix.domain.service.SettingsPersistenceManager(dataStore, settingsDao, firestore, auditDao)

        @Provides
        @Singleton
        fun provideSettingsValidator(): com.example.liftrix.domain.service.SettingsValidator =
            com.example.liftrix.domain.service.SettingsValidator()

        @Provides
        @Singleton
        fun provideInitializeUserThemeUseCase(
            settingsRepository: com.example.liftrix.domain.repository.SettingsRepository,
            @ApplicationContext context: android.content.Context
        ): com.example.liftrix.domain.usecase.settings.InitializeUserThemeUseCase =
            com.example.liftrix.domain.usecase.settings.InitializeUserThemeUseCase(settingsRepository, context)

        @Provides
        @Singleton
        fun provideSettingsCommandUseCase(
            settingsRepository: com.example.liftrix.domain.repository.SettingsRepository,
            preferencesService: com.example.liftrix.service.PreferencesService
        ): com.example.liftrix.domain.usecase.settings.SettingsCommandUseCase =
            com.example.liftrix.domain.usecase.settings.SettingsCommandUseCase(settingsRepository, preferencesService)

        @Provides
        @Singleton
        fun provideEvaluateFeatureFlagUseCase(
            featureFlagService: com.example.liftrix.service.FeatureFlagService
        ): com.example.liftrix.domain.usecase.settings.EvaluateFeatureFlagUseCase =
            com.example.liftrix.domain.usecase.settings.EvaluateFeatureFlagUseCase(featureFlagService)

        @Provides
        @Singleton
        fun provideBillingClientManager(
            @ApplicationContext context: android.content.Context
        ): com.example.liftrix.billing.BillingClientManager =
            com.example.liftrix.billing.BillingClientManager(context)
    }
}
