package com.example.liftrix

import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import timber.log.Timber

object FirebaseAppCheckProviderInstaller {
    const val providerName: String = "PlayIntegrityAppCheckProvider"

    fun logStartup(firebaseApp: FirebaseApp, packageName: String) {
        Timber.i(
            "Installing Firebase App Check Play Integrity provider. projectId=%s, appId=%s, package=%s",
            firebaseApp.options.projectId,
            firebaseApp.options.applicationId,
            packageName
        )
    }

    fun install(appCheck: FirebaseAppCheck) {
        appCheck.installAppCheckProviderFactory(
            PlayIntegrityAppCheckProviderFactory.getInstance()
        )
    }
}
