package com.example.liftrix.di

import com.example.liftrix.data.remote.FirebaseDataSource
import com.example.liftrix.data.remote.FirebaseDataSourceImpl
import com.example.liftrix.data.service.NetworkConnectivityMonitorImpl
import com.example.liftrix.domain.service.NetworkConnectivityMonitor
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkModule {
    @Binds
    @Singleton
    abstract fun bindNetworkConnectivityMonitor(impl: NetworkConnectivityMonitorImpl): NetworkConnectivityMonitor

    @Binds
    @Singleton
    abstract fun bindFirebaseDataSource(impl: FirebaseDataSourceImpl): FirebaseDataSource
}
