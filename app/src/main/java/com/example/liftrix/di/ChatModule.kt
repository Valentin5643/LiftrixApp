package com.example.liftrix.di

import com.example.liftrix.data.local.dao.AiUsageDao
import com.example.liftrix.data.repository.ChatRepositoryImpl
import com.example.liftrix.data.service.AIChatServiceImpl
import com.example.liftrix.data.service.AIMessageReportServiceImpl
import com.example.liftrix.data.service.AbusePreventionService
import com.example.liftrix.data.service.RateLimitingService
import com.example.liftrix.data.service.WorkoutProgramGenerationServiceImpl
import com.example.liftrix.domain.repository.ChatRepository
import com.example.liftrix.domain.repository.SubscriptionRepository
import com.example.liftrix.domain.service.AIChatService
import com.example.liftrix.domain.service.AIMessageReportService
import com.example.liftrix.domain.service.AbusePreventionServiceContract
import com.example.liftrix.domain.service.AnalyticsTracker
import com.example.liftrix.domain.service.RateLimitingServiceContract
import com.example.liftrix.domain.service.WorkoutProgramGenerationService
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ChatModule {
    @Binds
    @Singleton
    abstract fun bindChatRepository(impl: ChatRepositoryImpl): ChatRepository

    @Binds
    @Singleton
    abstract fun bindAIChatService(impl: AIChatServiceImpl): AIChatService

    @Binds
    @Singleton
    abstract fun bindWorkoutProgramGenerationService(
        impl: WorkoutProgramGenerationServiceImpl
    ): WorkoutProgramGenerationService

    @Binds
    @Singleton
    abstract fun bindAIMessageReportService(impl: AIMessageReportServiceImpl): AIMessageReportService

    @Binds
    @Singleton
    abstract fun bindRateLimitingService(impl: RateLimitingService): RateLimitingServiceContract

    @Binds
    @Singleton
    abstract fun bindAbusePreventionService(impl: AbusePreventionService): AbusePreventionServiceContract

    companion object {
        @Provides
        @Singleton
        fun provideAbusePreventionService(
            chatRepository: ChatRepository,
            remoteConfig: com.example.liftrix.data.remote.config.RemoteConfigManager,
            analyticsTracker: AnalyticsTracker
        ): AbusePreventionService = AbusePreventionService(
            chatRepository = chatRepository,
            remoteConfig = remoteConfig,
            analyticsTracker = analyticsTracker
        )

        @Provides
        @Singleton
        fun provideRateLimitingService(
            chatRepository: ChatRepository,
            aiUsageDao: AiUsageDao,
            subscriptionRepository: SubscriptionRepository,
            remoteConfig: com.example.liftrix.data.remote.config.RemoteConfigManager,
            analyticsTracker: AnalyticsTracker
        ): RateLimitingService = RateLimitingService(
            chatRepository = chatRepository,
            aiUsageDao = aiUsageDao,
            subscriptionRepository = subscriptionRepository,
            remoteConfig = remoteConfig,
            analyticsTracker = analyticsTracker
        )
    }
}
