package com.example.liftrix

import com.google.firebase.appcheck.debug.InternalDebugSecretProvider
import com.google.firebase.components.Component
import com.google.firebase.components.ComponentRegistrar

class FirebaseAppCheckDebugSecretRegistrar : ComponentRegistrar {
    override fun getComponents(): List<Component<*>> {
        val debugSecret = BuildConfig.FIREBASE_APP_CHECK_DEBUG_SECRET
        if (debugSecret.isBlank()) return emptyList()

        return listOf(
            Component.builder(InternalDebugSecretProvider::class.java)
                .factory {
                    InternalDebugSecretProvider { debugSecret }
                }
                .build()
        )
    }
}
