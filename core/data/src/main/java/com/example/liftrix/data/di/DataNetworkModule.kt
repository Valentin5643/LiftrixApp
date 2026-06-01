package com.example.liftrix.data.di

import com.example.liftrix.data.service.NetworkConnectivityMonitorImpl
import com.example.liftrix.domain.service.NetworkConnectivityMonitor
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataNetworkModule {
    @Binds
    @Singleton
    abstract fun bindNetworkConnectivityMonitor(impl: NetworkConnectivityMonitorImpl): NetworkConnectivityMonitor
}
