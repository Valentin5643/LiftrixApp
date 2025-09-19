package com.example.liftrix.di

import com.example.liftrix.core.security.JsonInputValidator
import com.example.liftrix.core.performance.ObjectPoolManager
import com.example.liftrix.core.performance.StreamingJsonParser
import com.example.liftrix.core.performance.SerializationPerformanceMonitor
import com.example.liftrix.core.performance.SerializationCacheManager
import com.example.liftrix.data.service.KotlinxWorkoutSerializationService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {

    @Provides
    @Singleton
    fun provideJsonInputValidator(): JsonInputValidator {
        return JsonInputValidator()
    }

    @Provides
    @Singleton
    fun provideObjectPoolManager(): ObjectPoolManager {
        return ObjectPoolManager()
    }

    @Provides
    @Singleton
    fun provideStreamingJsonParser(
        objectPoolManager: ObjectPoolManager
    ): StreamingJsonParser {
        return StreamingJsonParser(objectPoolManager)
    }

    @Provides
    @Singleton
    fun provideSerializationPerformanceMonitor(): SerializationPerformanceMonitor {
        return SerializationPerformanceMonitor()
    }

    @Provides
    @Singleton
    fun provideSerializationCacheManager(
        performanceMonitor: SerializationPerformanceMonitor
    ): SerializationCacheManager {
        return SerializationCacheManager(performanceMonitor)
    }

    @Provides
    @Singleton
    fun provideKotlinxWorkoutSerializationService(
        jsonValidator: JsonInputValidator,
        performanceMonitor: SerializationPerformanceMonitor,
        cacheManager: SerializationCacheManager
    ): KotlinxWorkoutSerializationService {
        return KotlinxWorkoutSerializationService(jsonValidator, performanceMonitor, cacheManager)
    }
}