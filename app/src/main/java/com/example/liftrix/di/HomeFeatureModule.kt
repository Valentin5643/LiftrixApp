package com.example.liftrix.di

import com.example.liftrix.di.feature.home.AppHomeAnalyticsAdapter
import com.example.liftrix.di.feature.home.AppHomeAuthAdapter
import com.example.liftrix.di.feature.home.AppHomeFeedAdapter
import com.example.liftrix.di.feature.home.AppHomeSocialAdapter
import com.example.liftrix.di.feature.home.AppHomeWorkoutAdapter
import com.example.liftrix.di.feature.home.AppPostCreationAdapter
import com.example.liftrix.di.feature.home.DemoAwareHomeFeedAdapter
import com.example.liftrix.di.feature.home.DemoAwareHomeSocialAdapter
import com.example.liftrix.di.feature.home.DemoAwareHomeWorkoutAdapter
import com.example.liftrix.feature.home.ports.HomeAnalyticsPort
import com.example.liftrix.feature.home.ports.HomeAuthPort
import com.example.liftrix.feature.home.ports.HomeFeedPort
import com.example.liftrix.feature.home.ports.HomeSocialPort
import com.example.liftrix.feature.home.ports.HomeWorkoutPort
import com.example.liftrix.feature.home.ports.PostCreationPort
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class HomeFeatureModule {
    @Binds
    abstract fun bindHomeAuthPort(adapter: AppHomeAuthAdapter): HomeAuthPort

    @Binds
    abstract fun bindHomeWorkoutPort(adapter: DemoAwareHomeWorkoutAdapter): HomeWorkoutPort

    @Binds
    abstract fun bindHomeSocialPort(adapter: DemoAwareHomeSocialAdapter): HomeSocialPort

    @Binds
    abstract fun bindHomeAnalyticsPort(adapter: AppHomeAnalyticsAdapter): HomeAnalyticsPort

    @Binds
    abstract fun bindHomeFeedPort(adapter: DemoAwareHomeFeedAdapter): HomeFeedPort

    @Binds
    abstract fun bindPostCreationPort(adapter: AppPostCreationAdapter): PostCreationPort
}
