package com.example.liftrix.data.remote.di

import com.example.liftrix.data.remote.FirebaseDataSource
import com.example.liftrix.data.remote.FirebaseDataSourceImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkBindingsModule {
    @Binds
    @Singleton
    abstract fun bindFirebaseDataSource(impl: FirebaseDataSourceImpl): FirebaseDataSource
}
