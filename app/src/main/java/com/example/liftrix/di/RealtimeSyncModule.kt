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

@Module
@InstallIn(SingletonComponent::class)
object RealtimeSyncModule {
    @Provides
    @Singleton
    fun provideSyncStrategy(): SyncStrategy = SyncStrategy.SmartPollingStrategy.forModerateWidgets()

    @Provides
    @Singleton
    fun provideConflictResolver(): ConflictResolver = ConflictResolver()

    @Provides
    @Singleton
    fun provideRealtimeSyncManager(
        firestore: FirebaseFirestore,
        auth: FirebaseAuth,
        @ApplicationContext context: Context,
        syncStrategy: SyncStrategy,
        conflictResolver: ConflictResolver
    ): RealtimeSyncManager = RealtimeSyncManager(
        firestore = firestore,
        auth = auth,
        context = context,
        syncStrategy = syncStrategy,
        conflictResolver = conflictResolver
    )
}
