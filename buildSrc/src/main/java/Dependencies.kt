object Versions {
    const val kotlin = "1.9.10"
    const val compose = "1.5.0"
    const val composeCompiler = "1.5.0"
    const val coroutines = "1.7.3"
    const val lifecycle = "2.7.0"
    const val hilt = "2.47"
    const val room = "2.6.0"
    const val retrofit = "2.9.0"
    const val firebase = "32.6.0"
    const val accompanist = "0.32.0"
    const val work = "2.8.1"
    const val navigation = "2.7.5"
    const val material3 = "1.1.2"
    const val datastore = "1.0.0"
    const val junit = "4.13.2"
    const val mockk = "1.13.8"
    const val truth = "1.1.4"
    const val turbine = "1.0.0"
}

object Dependencies {
    const val compileSdk = 34
    const val minSdk = 21
    const val targetSdk = 34
    const val versionCode = 1
    const val versionName = "1.0"

    const val androidGradlePlugin = "8.1.4"
    const val kotlin = "1.8.20"
    const val ksp = "1.8.20-1.0.11"

    object AndroidX {
        const val coreKtx = "1.10.1"
        const val lifecycleRuntimeKtx = "2.6.1"
        const val lifecycleViewmodelCompose = "2.6.1"
        const val activityCompose = "1.7.2"
        const val compose = "2023.05.01"
        const val navigation = "2.6.0"
        const val room = "2.5.0"
        const val work = "2.8.1"
        const val constraintlayout = "1.0.1"
        const val hilt = "2.47"
        const val hiltNavigation = "1.0.0"
        const val datastore = "1.0.0"
        const val junit = "1.1.5"
        const val espresso = "3.5.1"
    }

    object Google {
        const val material = "1.9.0"
        const val accompanist = "0.30.1"
        const val firebase = "32.2.2"
        const val playServicesAuth = "20.6.0"
        const val playServicesLocation = "21.0.1"
        const val mlkitBarcode = "17.2.0"
        const val zxing = "3.5.1"
        const val gson = "2.10.1"
    }

    object Testing {
        const val junit = "4.13.2"
        const val mockk = "1.13.5"
        const val coroutines = "1.7.1"
        const val turbine = "1.0.0"
        const val robolectric = "4.10.3"
    }

    object Timber {
        const val timber = "5.0.1"
    }

    // Lint Rules for Room Entity Validation
    object LintRules {
        const val customLint = "1.0.0"
        
        // Custom lint rules to prevent Room schema issues
        val roomLintRules = listOf(
            "no-undefined-defaults",    // Prevent defaultValue = "undefined"
            "room-entity-validation",   // Ensure proper Room annotations
            "nullable-vs-defaults"      // Check nullable fields vs default values
        )
    }

    // Core Android
    const val coreKtx = "androidx.core:core-ktx:1.12.0"
    const val appCompat = "androidx.appcompat:appcompat:1.6.1"
    const val material = "com.google.android.material:material:1.10.0"
    const val activityCompose = "androidx.activity:activity-compose:1.8.1"
    
    // Compose BOM and UI
    const val composeBom = "androidx.compose:compose-bom:2023.10.01"
    const val composeUi = "androidx.compose.ui:ui"
    const val composeUiTooling = "androidx.compose.ui:ui-tooling"
    const val composeUiToolingPreview = "androidx.compose.ui:ui-tooling-preview"
    const val composeMaterial3 = "androidx.compose.material3:material3:${Versions.material3}"
    const val composeNavigation = "androidx.navigation:navigation-compose:${Versions.navigation}"
    const val composeHiltNavigation = "androidx.hilt:hilt-navigation-compose:1.1.0"
    
    // Lifecycle
    const val lifecycleViewmodel = "androidx.lifecycle:lifecycle-viewmodel-ktx:${Versions.lifecycle}"
    const val lifecycleRuntime = "androidx.lifecycle:lifecycle-runtime-ktx:${Versions.lifecycle}"
    const val lifecycleRuntimeCompose = "androidx.lifecycle:lifecycle-runtime-compose:${Versions.lifecycle}"
    
    // Coroutines
    const val coroutinesCore = "org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.coroutines}"
    const val coroutinesAndroid = "org.jetbrains.kotlinx:kotlinx-coroutines-android:${Versions.coroutines}"
    
    // Dependency Injection
    const val hiltAndroid = "com.google.dagger:hilt-android:${Versions.hilt}"
    const val hiltCompiler = "com.google.dagger:hilt-compiler:${Versions.hilt}"
    
    // Room Database
    const val roomRuntime = "androidx.room:room-runtime:${Versions.room}"
    const val roomKtx = "androidx.room:room-ktx:${Versions.room}"
    const val roomCompiler = "androidx.room:room-compiler:${Versions.room}"
    
    // Network
    const val retrofit = "com.squareup.retrofit2:retrofit:${Versions.retrofit}"
    const val retrofitGson = "com.squareup.retrofit2:converter-gson:${Versions.retrofit}"
    const val okhttp = "com.squareup.okhttp3:okhttp:4.12.0"
    const val okhttpLogging = "com.squareup.okhttp3:logging-interceptor:4.12.0"
    
    // Firebase
    const val firebaseBom = "com.google.firebase:firebase-bom:${Versions.firebase}"
    const val firebaseAuth = "com.google.firebase:firebase-auth-ktx"
    const val firebaseFirestore = "com.google.firebase:firebase-firestore-ktx"
    const val firebaseStorage = "com.google.firebase:firebase-storage-ktx"
    const val firebaseAnalytics = "com.google.firebase:firebase-analytics-ktx"
    const val firebaseCrashlytics = "com.google.firebase:firebase-crashlytics-ktx"
    const val firebaseMessaging = "com.google.firebase:firebase-messaging-ktx"
    const val firebaseFunctions = "com.google.firebase:firebase-functions-ktx"
    
    // DataStore
    const val datastore = "androidx.datastore:datastore-preferences:${Versions.datastore}"
    
    // WorkManager
    const val workManager = "androidx.work:work-runtime-ktx:${Versions.work}"
    const val workManagerHilt = "androidx.hilt:hilt-work:1.1.0"
    
    // Testing
    const val junitExt = "androidx.test.ext:junit:1.1.5"
    const val composeUiTest = "androidx.compose.ui:ui-test-junit4"
    const val composeUiTestManifest = "androidx.compose.ui:ui-test-manifest"
    const val mockkAndroid = "io.mockk:mockk-android:${Versions.mockk}"
    const val truth = "com.google.truth:truth:${Versions.truth}"
    const val coroutinesTest = "org.jetbrains.kotlinx:kotlinx-coroutines-test:${Versions.coroutines}"
    const val roomTesting = "androidx.room:room-testing:${Versions.room}"
    const val workTesting = "androidx.work:work-testing:${Versions.work}"
    const val hiltTesting = "com.google.dagger:hilt-android-testing:${Versions.hilt}"
}

object Plugins {
    const val androidApplication = "com.android.application"
    const val androidLibrary = "com.android.library"
    const val kotlinAndroid = "org.jetbrains.kotlin.android"
    const val kotlinKapt = "kotlin-kapt"
    const val kotlinParcelize = "kotlin-parcelize"
    const val hilt = "dagger.hilt.android.plugin"
    const val googleServices = "com.google.gms.google-services"
    const val crashlytics = "com.google.firebase.crashlytics"
    const val ksp = "com.google.devtools.ksp"
}

object Libs {
    const val hiltGradlePlugin = "com.google.dagger:hilt-android-gradle-plugin:${Versions.hilt}"
    
    object Compose {
        const val ui = "androidx.compose.ui:ui:${Versions.compose}"
        const val material3 = "androidx.compose.material3:material3:1.1.1"
        const val navigation = "androidx.navigation:navigation-compose:2.7.0"
    }
    
    object Hilt {
        const val android = "com.google.dagger:hilt-android:${Versions.hilt}"
        const val compiler = "com.google.dagger:hilt-android-compiler:${Versions.hilt}"
        const val compose = "androidx.hilt:hilt-navigation-compose:1.0.0"
    }
} 