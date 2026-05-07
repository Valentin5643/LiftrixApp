package com.example.liftrix.di

import com.example.liftrix.domain.sync.SyncScheduler
import com.example.liftrix.sync.WorkManagerSyncScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SyncSchedulerModule {
    @Binds
    @Singleton
    abstract fun bindSyncScheduler(
        scheduler: WorkManagerSyncScheduler
    ): SyncScheduler
}
