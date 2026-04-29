package com.example.liftrix

import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

object FirebaseAppCheckProviderInstaller {
    private const val TAG = "AppCheckInitializer"
    const val providerName: String = "DebugAppCheckProvider"

    fun logStartup(firebaseApp: FirebaseApp, packageName: String) {
        Log.w(TAG, "Installing Firebase App Check DEBUG provider.")
        Log.w(TAG, "Firebase projectId=${firebaseApp.options.projectId}, appId=${firebaseApp.options.applicationId}, package=$packageName")
        Log.w(TAG, "If token exchange returns 403 App attestation failed, the debug token is not registered for this Firebase app/project.")
        Log.w(TAG, "Find the token in Logcat by searching: Enter this debug secret into the allow list in the Firebase Console")
        Log.w(TAG, "Register it at Firebase Console > App Check > com.example.liftrix > Manage debug tokens.")
        Log.w(TAG, "After registration, uninstall/reinstall the debug app or clear app data and run again.")
    }

    fun install(appCheck: FirebaseAppCheck) {
        appCheck.installAppCheckProviderFactory(
            DebugAppCheckProviderFactory.getInstance()
        )
    }
}
