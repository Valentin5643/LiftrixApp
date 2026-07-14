package com.example.liftrix.di

import android.content.Context
import com.example.liftrix.BuildConfig
import com.example.liftrix.config.OfflineArchitectureFlags
import com.example.liftrix.data.serialization.ExerciseDeserializer
import com.example.liftrix.data.serialization.ExerciseSetDeserializer
import com.example.liftrix.domain.model.Exercise
import com.example.liftrix.domain.model.ExerciseSet
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.perf.FirebasePerformance
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.google.firebase.storage.FirebaseStorage
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {
    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun provideFirebaseFirestore(): FirebaseFirestore {
        return FirebaseFirestore.getInstance().apply {
            validateFirestorePersistenceConfig()

            val persistenceEnabled = !OfflineArchitectureFlags.DISABLE_FIRESTORE_PERSISTENCE
            firestoreSettings = com.google.firebase.firestore.FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(persistenceEnabled)
                .apply {
                    if (persistenceEnabled) {
                        setCacheSizeBytes(com.google.firebase.firestore.FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                    }
                }
                .build()

            if (OfflineArchitectureFlags.DISABLE_FIRESTORE_PERSISTENCE) {
                Timber.i("ROOM-FIRST MODE: Firestore offline persistence DISABLED (Room is authority)")
                if (OfflineArchitectureFlags.VERBOSE_SYNC_LOGGING) {
                    Timber.d(OfflineArchitectureFlags.getConfigSummary())
                }
            } else {
                Timber.w("LEGACY MODE: Firestore offline persistence ENABLED (dual authority)")
            }
        }
    }

    internal fun validateFirestorePersistenceConfig(
        roomFirstEnabled: Boolean = OfflineArchitectureFlags.ROOM_FIRST_ENABLED,
        disableFirestorePersistence: Boolean = OfflineArchitectureFlags.DISABLE_FIRESTORE_PERSISTENCE
    ) {
        if (roomFirstEnabled && !disableFirestorePersistence) {
            throw IllegalStateException(
                "ARCHITECTURAL VIOLATION: Firestore persistence must be disabled when ROOM_FIRST_ENABLED=true"
            )
        }
    }

    @Provides
    @Singleton
    fun provideFirebaseStorage(): FirebaseStorage {
        return FirebaseStorage.getInstance().apply {
            maxUploadRetryTimeMillis = 30000
            maxDownloadRetryTimeMillis = 15000
            maxOperationRetryTimeMillis = 45000
        }
    }

    @Provides
    @Singleton
    fun provideFirebaseFunctions(): FirebaseFunctions = FirebaseFunctions.getInstance()

    @Provides
    @Singleton
    fun provideFirebaseMessaging(): FirebaseMessaging = FirebaseMessaging.getInstance()

    @Provides
    @Singleton
    fun provideFirebaseAnalytics(): FirebaseAnalytics {
        return Firebase.analytics.apply {
            setAnalyticsCollectionEnabled(false)
        }
    }

    @Provides
    @Singleton
    fun provideFirebaseCrashlytics(): FirebaseCrashlytics {
        return FirebaseCrashlytics.getInstance().apply {
            setCrashlyticsCollectionEnabled(false)
            setCustomKey("build_type", BuildConfig.BUILD_TYPE)
            setCustomKey("environment", BuildConfig.ENVIRONMENT)
            setCustomKey("app_version_name", BuildConfig.APP_VERSION_NAME)
            setCustomKey("app_version_code", BuildConfig.APP_VERSION_CODE)
            setCustomKey("debug_tools_enabled", BuildConfig.ENABLE_DEBUG_TOOLS)
            setCustomKey("strict_mode_enabled", BuildConfig.ENABLE_STRICT_MODE)
            setCustomKey("firebase_performance_enabled", BuildConfig.ENABLE_FIREBASE_PERFORMANCE)
        }
    }

    @Provides
    @Singleton
    fun provideFirebasePerformance(): FirebasePerformance {
        return FirebasePerformance.getInstance().apply {
            isPerformanceCollectionEnabled = false
        }
    }

    @Provides
    @Singleton
    fun provideFirebaseRemoteConfig(): FirebaseRemoteConfig = Firebase.remoteConfig

    @Provides
    @Singleton
    fun provideGson(): Gson {
        return GsonBuilder()
            .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
            .setLenient()
            .registerTypeAdapter(ExerciseSet::class.java, ExerciseSetDeserializer())
            .registerTypeAdapter(Exercise::class.java, ExerciseDeserializer())
            .create()
    }

    @Provides
    @Singleton
    fun provideJson(): Json {
        return Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = false
            prettyPrint = false
            coerceInputValues = true
        }
    }

    @Provides
    @Singleton
    fun provideContext(@ApplicationContext context: Context): Context = context
}
