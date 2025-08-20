package com.example.liftrix.di

import com.example.liftrix.data.local.dao.ChatHistoryDao
import com.example.liftrix.data.remote.config.RemoteConfigManager
import com.example.liftrix.data.repository.ChatRepositoryImpl
import com.example.liftrix.data.service.AIChatServiceImpl
import com.example.liftrix.data.service.AbusePreventionService
import com.example.liftrix.data.service.RateLimitingService
import com.example.liftrix.domain.repository.ChatRepository
import com.example.liftrix.domain.service.AIChatService
import com.example.liftrix.domain.service.AnalyticsTracker
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Singleton

/**
 * Dagger Hilt module for AI chat service dependencies.
 * 
 * This module provides dependency injection bindings for AI chat functionality
 * including the main AI service, abuse prevention, and rate limiting services.
 * 
 * Services Provided:
 * - AIChatService: Main AI conversation interface using Firebase AI with Gemini 2.5 Flash Lite
 * - AbusePreventionService: Jailbreak detection and abuse mitigation with fitness context awareness
 * - RateLimitingService: Three-tier rate limiting (daily messages, monthly tokens, hourly cost)
 * 
 * All services are provided as Singleton instances to ensure consistent state and
 * optimal resource management throughout the application lifecycle.
 * 
 * Technical Implementation:
 * - Integrates with Firebase AI for natural language processing
 * - Uses Remote Config for dynamic configuration management
 * - Follows Clean Architecture with proper abstraction layers
 * - Provides comprehensive error handling and analytics integration
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ChatModule {
    
    /**
     * Binds ChatRepository interface to its implementation.
     * 
     * Provides chat persistence and management with offline-first architecture,
     * background sync to Firebase, and comprehensive user scoping for data isolation.
     */
    @Binds
    @Singleton
    abstract fun bindChatRepository(
        chatRepositoryImpl: ChatRepositoryImpl
    ): ChatRepository
    
    /**
     * Binds AIChatService interface to its implementation.
     * 
     * Provides AI conversation capabilities with Firebase AI integration,
     * language detection (English/Romanian), and context-aware responses.
     */
    @Binds
    @Singleton
    abstract fun bindAIChatService(
        aiChatServiceImpl: AIChatServiceImpl
    ): AIChatService
    
    companion object {
        
        /**
         * Provides AbusePreventionService for detecting and preventing AI abuse.
         * 
         * The AbusePreventionService implements comprehensive abuse detection including:
         * - Context-aware jailbreak detection with fitness keyword filtering
         * - Rate anomaly detection with similarity analysis
         * - Excessive input validation with workout plan exceptions
         * - Nonsense pattern detection with keyboard mashing identification
         * 
         * Uses sophisticated algorithms including Levenshtein distance for message
         * similarity analysis and dynamic scoring based on fitness context.
         * 
         * @param chatRepository Repository for accessing recent messages
         * @param remoteConfig Remote config manager for dynamic thresholds
         * @param analyticsTracker Analytics tracker for logging abuse attempts
         * @return AbusePreventionService singleton instance
         */
        @Provides
        @Singleton
        fun provideAbusePreventionService(
            chatRepository: ChatRepository,
            remoteConfig: RemoteConfigManager,
            analyticsTracker: AnalyticsTracker
        ): AbusePreventionService = AbusePreventionService(
            chatRepository = chatRepository,
            remoteConfig = remoteConfig,
            analyticsTracker = analyticsTracker
        )
        
        /**
         * Provides RateLimitingService for comprehensive usage limit enforcement.
         * 
         * The RateLimitingService implements three-tier protection:
         * - Daily message limits: Configurable per-user daily message quotas
         * - Monthly token limits: Token-based usage tracking for cost control
         * - Hourly cost guardrails: Real-time cost monitoring with automatic throttling
         * 
         * Integrates with Remote Config for dynamic limit adjustments and provides
         * detailed usage analytics for cost optimization and abuse prevention.
         * 
         * @param chatRepository Repository for accessing usage statistics
         * @param remoteConfig Remote config manager for dynamic limits
         * @param analyticsTracker Analytics tracker for usage and cost tracking
         * @return RateLimitingService singleton instance
         */
        @Provides
        @Singleton
        fun provideRateLimitingService(
            chatRepository: ChatRepository,
            chatHistoryDao: ChatHistoryDao,
            remoteConfig: RemoteConfigManager,
            analyticsTracker: AnalyticsTracker
        ): RateLimitingService = RateLimitingService(
            chatRepository = chatRepository,
            chatHistoryDao = chatHistoryDao,
            remoteConfig = remoteConfig,
            analyticsTracker = analyticsTracker
        )
    }
}