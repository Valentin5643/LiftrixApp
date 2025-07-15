package com.example.liftrix.di.module

import com.example.liftrix.data.repository.session.SessionRepositoryImpl
import com.example.liftrix.domain.repository.session.SessionRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for session feature dependencies.
 * 
 * This module encapsulates all dependency injection requirements for the session domain:
 * - Repository interface binding
 * - Feature-specific scoping
 * 
 * Following the feature-based DI organization pattern for improved modularity
 * and maintainability. All session-related dependencies are centralized here.
 * 
 * Note: Session-specific use cases will be added as they are implemented
 * in future iterations of the architecture modernization.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SessionModule {

    /**
     * Binds the concrete SessionRepositoryImpl to the SessionRepository interface.
     * 
     * This enables dependency injection of the repository interface throughout
     * the application while keeping the implementation details abstracted.
     * 
     * @param sessionRepositoryImpl The concrete repository implementation
     * @return The repository interface bound to the implementation
     */
    @Binds
    @Singleton
    abstract fun bindSessionRepository(
        sessionRepositoryImpl: SessionRepositoryImpl
    ): SessionRepository
}