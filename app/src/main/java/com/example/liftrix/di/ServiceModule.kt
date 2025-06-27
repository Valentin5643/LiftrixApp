package com.example.liftrix.di

import com.example.liftrix.data.service.WeightMemoryServiceImpl
import com.example.liftrix.domain.service.WeightMemoryService
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ServiceModule {
    
    @Binds
    @Singleton
    abstract fun bindWeightMemoryService(
        weightMemoryServiceImpl: WeightMemoryServiceImpl
    ): WeightMemoryService
} 