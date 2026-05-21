package com.example.liftrix.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Compatibility module retained for historical references.
 *
 * Repository bindings now live in focused repository modules.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule
