package com.example.liftrix.di

import android.content.Context
import androidx.work.WorkManager
import com.example.liftrix.data.service.NetworkConnectivityMonitorImpl
import com.example.liftrix.domain.service.NetworkConnectivityMonitor
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.storage.FirebaseStorage
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.example.liftrix.domain.model.Exercise
import com.example.liftrix.domain.model.ExerciseSet
import com.example.liftrix.data.serialization.ExerciseDeserializer
import com.example.liftrix.data.serialization.ExerciseSetDeserializer
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkModule {

    companion object {
        @Provides
        @Singleton
        fun provideFirebaseAuth(): FirebaseAuth {
            return FirebaseAuth.getInstance()
        }

        @Provides
        @Singleton
        fun provideFirebaseFirestore(): FirebaseFirestore {
            return FirebaseFirestore.getInstance().apply {
                firestoreSettings = com.google.firebase.firestore.FirebaseFirestoreSettings.Builder()
                    .setPersistenceEnabled(true)
                    .setCacheSizeBytes(com.google.firebase.firestore.FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                    .build()
            }
        }

        @Provides
        @Singleton
        fun provideFirebaseStorage(): FirebaseStorage {
            return FirebaseStorage.getInstance().apply {
                // Configure maximum upload/download timeout for profile images
                maxUploadRetryTimeMillis = 30000 // 30 seconds for uploads
                maxDownloadRetryTimeMillis = 15000 // 15 seconds for downloads
                maxOperationRetryTimeMillis = 45000 // 45 seconds total operation timeout
            }
        }

        @Provides
        @Singleton
        fun provideFirebaseFunctions(): FirebaseFunctions {
            return FirebaseFunctions.getInstance().apply {
                // Configure timeout for Cloud Functions calls
                // Admin operations may take longer than default
                // Default timeout is 70 seconds which should be sufficient for admin operations
            }
        }

        // REMOVED: WorkManager provider
        // WorkManager MUST NOT be provided through Hilt to prevent early initialization.
        // Components that need WorkManager should use WorkManagerProvider.getInstance()
        // which ensures proper initialization with HiltWorkerFactory.

        @Provides
        @Singleton
        fun provideGson(): Gson {
            return GsonBuilder()
                .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
                .setLenient() // Add lenient parsing for cache recovery and backward compatibility
                .registerTypeAdapter(ExerciseSet::class.java, ExerciseSetDeserializer())
                .registerTypeAdapter(Exercise::class.java, ExerciseDeserializer())
                .create()
        }

        @Provides
        @Singleton
        fun provideContext(@ApplicationContext context: Context): Context {
            return context
        }
    }

    @Binds
    @Singleton
    abstract fun bindNetworkConnectivityMonitor(
        networkConnectivityMonitorImpl: NetworkConnectivityMonitorImpl
    ): NetworkConnectivityMonitor
} 