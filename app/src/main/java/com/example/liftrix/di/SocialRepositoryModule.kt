package com.example.liftrix.di

import com.example.liftrix.data.repository.PRNotificationRepositoryImpl
import com.example.liftrix.data.repository.SocialRepositoryImpl
import com.example.liftrix.data.repository.UserSearchRepositoryImpl
import com.example.liftrix.data.repository.social.BlockRepositoryImpl
import com.example.liftrix.data.repository.social.ReportRepositoryImpl
import com.example.liftrix.domain.repository.PRNotificationRepository
import com.example.liftrix.domain.repository.SocialRepository
import com.example.liftrix.domain.repository.UserSearchRepository
import com.example.liftrix.domain.repository.social.BlockRepository
import com.example.liftrix.domain.repository.social.ReportRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SocialRepositoryModule {
    @Binds
    @Singleton
    abstract fun bindSocialRepository(impl: SocialRepositoryImpl): SocialRepository

    @Binds
    @Singleton
    abstract fun bindUserSearchRepository(impl: UserSearchRepositoryImpl): UserSearchRepository

    @Binds
    @Singleton
    abstract fun bindPRNotificationRepository(impl: PRNotificationRepositoryImpl): PRNotificationRepository

    @Binds
    @Singleton
    abstract fun bindBlockRepository(impl: BlockRepositoryImpl): BlockRepository

    @Binds
    @Singleton
    abstract fun bindReportRepository(impl: ReportRepositoryImpl): ReportRepository
}
