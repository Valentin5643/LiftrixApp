package com.example.liftrix.di

import com.example.liftrix.domain.usecase.ai.WorkoutProgramGateway
import com.example.liftrix.domain.usecase.ai.WorkoutProgramGatewayImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class WorkoutFeatureModule {
    @Binds
    abstract fun bindWorkoutProgramGateway(
        impl: WorkoutProgramGatewayImpl
    ): WorkoutProgramGateway
}
