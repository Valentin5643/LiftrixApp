package com.example.liftrix

import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

object FirebaseAppCheckProviderInstaller {
    private const val TAG = "AppCheckInitializer"
    const val providerName: String = "PlayIntegrityAppCheckProvider"

    fun logStartup(firebaseApp: FirebaseApp, packageName: String) {
        Log.i(
            TAG,
            "Installing Firebase App Check Play Integrity provider. " +
                "projectId=${firebaseApp.options.projectId}, " +
                "appId=${firebaseApp.options.applicationId}, package=$packageName"
        )
    }

    fun install(appCheck: FirebaseAppCheck) {
        appCheck.installAppCheckProviderFactory(
            PlayIntegrityAppCheckProviderFactory.getInstance()
        )
    }
}
