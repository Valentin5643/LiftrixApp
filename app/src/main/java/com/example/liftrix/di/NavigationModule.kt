package com.example.liftrix.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt module for navigation-related dependencies.
 * 
 * This module provides dependency injection bindings for navigation components
 * and services. ViewModels are automatically handled by Hilt with @HiltViewModel
 * annotation, so this module focuses on other navigation-related dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object NavigationModule {
    
    // Navigation-related dependencies will be added here as needed
    // Currently, ViewModels are handled automatically by @HiltViewModel
    // This module is prepared for future navigation services or utilities
} 