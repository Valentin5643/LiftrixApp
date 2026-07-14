package com.example.liftrix

import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.debug.internal.StorageHelper

object FirebaseAppCheckProviderInstaller {
    private const val TAG = "AppCheckInitializer"
    private const val DEBUG_TOKEN_LOG_FILTER = "LIFTRIX_APP_CHECK_TOKEN"

    @Volatile
    private var debugTokenLogged = false

    const val providerName: String = "DebugAppCheckProvider"

    fun logStartup(firebaseApp: FirebaseApp, packageName: String) {
        Log.w(TAG, "Installing Firebase App Check DEBUG provider.")
        Log.w(TAG, "Firebase projectId=${firebaseApp.options.projectId}, appId=${firebaseApp.options.applicationId}, package=$packageName")
        logDebugSecretIfAvailable(firebaseApp)
    }

    fun install(appCheck: FirebaseAppCheck) {
        appCheck.installAppCheckProviderFactory(
            DebugAppCheckProviderFactory.getInstance()
        )
    }

    fun logDebugSecretIfAvailable(firebaseApp: FirebaseApp) {
        val configuredSecret = BuildConfig.FIREBASE_APP_CHECK_DEBUG_SECRET
        if (configuredSecret.isNotBlank()) {
            logCopyReadySecret(configuredSecret)
            return
        }

        val storedSecret = StorageHelper(
            firebaseApp.applicationContext,
            firebaseApp.persistenceKey
        ).retrieveDebugSecret()

        if (!storedSecret.isNullOrBlank()) logCopyReadySecret(storedSecret)
    }

    @Synchronized
    private fun logCopyReadySecret(secret: String) {
        if (debugTokenLogged) return
        debugTokenLogged = true
        Log.e(
            DEBUG_TOKEN_LOG_FILTER,
            "$DEBUG_TOKEN_LOG_FILTER FIREBASE_APP_CHECK_DEBUG_SECRET=$secret"
        )
    }
}
