package com.example.liftrix.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Compatibility module retained for historical references.
 *
 * Domain, service, session, settings, progress, and export bindings now live
 * in focused modules.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DomainModule
