package com.example.liftrix.di

import com.example.liftrix.data.repository.AuthRepositoryImpl
import com.example.liftrix.data.repository.ProfileImageRepositoryImpl
import com.example.liftrix.data.repository.ProfileRepositoryImpl
import com.example.liftrix.data.repository.UserRepositoryImpl
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.repository.ProfileImageRepository
import com.example.liftrix.domain.repository.ProfileRepository
import com.example.liftrix.domain.repository.UserRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthRepositoryModule {
    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    @Singleton
    abstract fun bindProfileRepository(impl: ProfileRepositoryImpl): ProfileRepository

    @Binds
    @Singleton
    abstract fun bindProfileImageRepository(impl: ProfileImageRepositoryImpl): ProfileImageRepository

    @Binds
    @Singleton
    abstract fun bindUserRepository(impl: UserRepositoryImpl): UserRepository
}
