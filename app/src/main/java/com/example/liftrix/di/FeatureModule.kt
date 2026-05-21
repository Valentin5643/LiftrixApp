package com.example.liftrix.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Compatibility module retained for historical references.
 *
 * Feature bindings now live in SocialFeatureModule, ChatModule,
 * NotificationModule, RealtimeSyncModule, and existing feature port modules.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class FeatureModule
