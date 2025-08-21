package com.example.liftrix.di

import android.content.Context
import com.example.liftrix.service.sync.ConflictResolver
import com.example.liftrix.service.sync.RealtimeSyncManager
import com.example.liftrix.service.sync.SyncStrategy
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Dagger Hilt module for widget synchronization components.
 * 
 * This module provides dependency injection bindings for real-time synchronization
 * components following Clean Architecture principles with proper scoping and integration.
 * 
 * Components Provided:
 * - SyncStrategy: Smart polling and sync pattern management based on widget complexity
 * - ConflictResolver: Timestamp-based conflict resolution for sync operations
 * - RealtimeSyncManager: Hybrid sync strategy with Firestore listeners
 * 
 * Dependencies from Other Modules:
 * - FirebaseFirestore: Provided by NetworkModule for real-time listeners
 * 
 * All sync components are provided as Singleton instances to ensure:
 * - Consistent sync state management across the application
 * - Proper listener lifecycle management to prevent memory leaks
 * - Coordinated background sync operations with network optimization
 * - Performance optimization for real-time data updates
 * 
 * Technical Implementation:
 * - Leverages Firebase Firestore for real-time listeners
 * - Uses WorkManagerProvider for background sync with network constraints
 * - Follows established DI patterns for testability and maintainability
 * - Integrates with existing analytics and cache infrastructure
 */
@Module
@InstallIn(SingletonComponent::class)
object WidgetSyncModule {
    
    /**
     * Provides SyncStrategy implementation for widget complexity-based sync patterns.
     * 
     * The SyncStrategy provides intelligent sync scheduling based on:
     * - Widget complexity levels (SIMPLE: 30s-5min, MODERATE: 5-15min, COMPLEX: 15-60min)
     * - User activity patterns and network conditions
     * - Battery optimization and background restrictions
     * - Data freshness requirements and cache invalidation
     * 
     * @return Configured SyncStrategy instance with complexity mappings
     */
    @Provides
    @Singleton
    fun provideSyncStrategy(): SyncStrategy {
        return SyncStrategy.SmartPollingStrategy.forModerateWidgets()
    }
    
    /**
     * Provides ConflictResolver implementation for sync conflict management.
     * 
     * The ConflictResolver handles sync conflicts with:
     * - Timestamp-based conflict resolution for data consistency
     * - User preference preservation during conflicts
     * - Data integrity validation and error recovery
     * - Audit trail for conflict resolution decisions
     * 
     * @return Configured ConflictResolver instance
     */
    @Provides
    @Singleton
    fun provideConflictResolver(): ConflictResolver {
        return ConflictResolver()
    }
    
    /**
     * Provides RealtimeSyncManager implementation for hybrid sync strategy.
     * 
     * The RealtimeSyncManager coordinates real-time synchronization with:
     * - Firestore listeners for sub-second updates on critical data
     * - Smart polling intervals based on widget complexity and activity
     * - WorkManagerProvider integration for background sync recovery
     * - Memory leak prevention with proper listener management
     * 
     * @param firestore Firebase Firestore instance for real-time listeners
     * @param auth Firebase Auth instance for authentication validation
     * Uses WorkManagerProvider.getInstance() for background sync operations
     * @param syncStrategy Strategy for complexity-based sync patterns
     * @param conflictResolver Resolver for handling sync conflicts
     * @return Configured RealtimeSyncManager instance
     */
    @Provides
    @Singleton
    fun provideRealtimeSyncManager(
        firestore: FirebaseFirestore,
        auth: FirebaseAuth,
        @ApplicationContext context: Context,
        syncStrategy: SyncStrategy,
        conflictResolver: ConflictResolver
    ): RealtimeSyncManager {
        return RealtimeSyncManager(
            firestore = firestore,
            auth = auth,
            context = context,
            syncStrategy = syncStrategy,
            conflictResolver = conflictResolver
        )
    }
}