package com.example.liftrix.di.module

import com.example.liftrix.data.repository.template.TemplateRepositoryImpl
import com.example.liftrix.domain.repository.template.TemplateRepository
import com.example.liftrix.domain.usecase.common.ErrorHandler
// Consolidated TemplateQueryUseCase and TemplateCommandUseCase are provided automatically via @Inject constructor
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for template feature dependencies.
 * 
 * This module encapsulates all dependency injection requirements for the template domain:
 * - Repository interface binding
 * - Use case provider functions
 * - Feature-specific scoping
 * 
 * Following the feature-based DI organization pattern for improved modularity
 * and maintainability. All template-related dependencies are centralized here.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class TemplateModule {

    /**
     * Binds the concrete TemplateRepositoryImpl to the TemplateRepository interface.
     * 
     * This enables dependency injection of the repository interface throughout
     * the application while keeping the implementation details abstracted.
     * 
     * @param templateRepositoryImpl The concrete repository implementation
     * @return The repository interface bound to the implementation
     */
    @Binds
    @Singleton
    abstract fun bindTemplateRepository(
        templateRepositoryImpl: TemplateRepositoryImpl
    ): TemplateRepository

    // Note: Consolidated TemplateQueryUseCase and TemplateCommandUseCase are provided
    // automatically via @Inject constructor and don't need explicit @Provides methods.
}