package com.example.liftrix

import android.content.Context
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.appCheck
import kotlinx.coroutines.tasks.await
import timber.log.Timber

object AppCheckInitializer {
    private var initialized = false

    fun initialize(context: Context): FirebaseApp {
        val firebaseApp = FirebaseApp.initializeApp(context.applicationContext)
            ?: FirebaseApp.getInstance()

        if (!initialized) {
            FirebaseAppCheckProviderInstaller.logStartup(firebaseApp, context.packageName)
            FirebaseAppCheckProviderInstaller.install(Firebase.appCheck)
            initialized = true
            Timber.i("Firebase App Check provider installed: ${FirebaseAppCheckProviderInstaller.providerName}")
        } else {
            Timber.d("Firebase App Check provider already installed")
        }

        return firebaseApp
    }

    suspend fun verifyTokenOnce(): Result<Unit> = runCatching {
        Firebase.appCheck.getAppCheckToken(false).await()
        Unit
    }
}
