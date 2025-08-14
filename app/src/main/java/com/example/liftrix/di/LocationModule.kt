package com.example.liftrix.di

import com.example.liftrix.domain.service.LocationService
import com.example.liftrix.service.LocationServiceImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for location-related dependencies
 * Provides LocationService implementation for gym buddy location features
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class LocationModule {

    /**
     * Provides LocationService implementation
     */
    @Binds
    @Singleton
    abstract fun bindLocationService(
        locationServiceImpl: LocationServiceImpl
    ): LocationService
}