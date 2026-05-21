package com.example.liftrix.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Compatibility module retained for historical references.
 *
 * Data persistence bindings now live in DatabaseModule, DaoModule,
 * DataStoreModule, and SerializationModule.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule
