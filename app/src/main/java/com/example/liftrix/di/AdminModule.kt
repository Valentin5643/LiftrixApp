package com.example.liftrix.di

import com.example.liftrix.data.service.AdminFirebaseServiceImpl
import com.example.liftrix.domain.service.AdminFirebaseService
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Dagger Hilt module for admin functionality.
 * 
 * Provides dependency injection bindings for admin-related services including
 * user management, banning operations, and Firebase Admin SDK integration.
 * 
 * ★ Insight ─────────────────────────────────────
 * - Provides secure admin service bindings for Firebase Admin SDK integration
 * - Uses singleton scope to ensure consistent admin state management
 * - Integrates with existing Firebase modules for authentication and functions
 * ─────────────────────────────────────────────────
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AdminModule {
    
    /**
     * Binds AdminFirebaseService interface to its implementation.
     * 
     * Provides singleton AdminFirebaseService instance for secure admin operations
     * through Firebase Cloud Functions with Admin SDK integration.
     */
    @Binds
    @Singleton
    abstract fun bindAdminFirebaseService(
        adminFirebaseServiceImpl: AdminFirebaseServiceImpl
    ): AdminFirebaseService
}