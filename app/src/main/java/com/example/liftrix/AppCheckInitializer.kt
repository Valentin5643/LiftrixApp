package com.example.liftrix

import android.content.Context
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.appCheck
import kotlinx.coroutines.tasks.await

object AppCheckInitializer {
    private const val TAG = "AppCheckInitializer"

    @Volatile
    private var initialized = false

    @Synchronized
    fun initialize(context: Context): FirebaseApp {
        val firebaseApp = FirebaseApp.initializeApp(context.applicationContext)
            ?: FirebaseApp.getInstance()

        if (!initialized) {
            FirebaseAppCheckProviderInstaller.logStartup(firebaseApp, context.packageName)
            val appCheck = Firebase.appCheck
            FirebaseAppCheckProviderInstaller.install(appCheck)
            appCheck.setTokenAutoRefreshEnabled(true)
            initialized = true
            Log.i(TAG, "Firebase App Check provider installed: ${FirebaseAppCheckProviderInstaller.providerName}")
        } else {
            Log.d(TAG, "Firebase App Check provider already installed")
        }

        return firebaseApp
    }

    suspend fun verifyTokenOnce(): Result<Unit> = runCatching {
        Firebase.appCheck.getAppCheckToken(false).await()
        Unit
    }
}
