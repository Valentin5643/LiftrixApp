plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.gms.google.services)
    alias(libs.plugins.google.firebase.crashlytics)
    alias(libs.plugins.google.firebase.firebase.perf)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
    id("kotlin-parcelize")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-Xjvm-default=all"
        )
    }
}

android {
    namespace = "com.example.liftrix"
    compileSdk = 35
    buildToolsVersion = "34.0.0"

    defaultConfig {
        applicationId = "com.example.liftrix"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // Build optimizations
        vectorDrawables.useSupportLibrary = true
        multiDexEnabled = false
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            
            // Performance optimizations for release builds
            isDebuggable = false
            isJniDebuggable = false
            renderscriptOptimLevel = 3
            
            // Firebase Performance Monitoring configuration
            manifestPlaceholders["firebase_performance_logcat_enabled"] = false
            manifestPlaceholders["firebase_performance_collection_enabled"] = true
        }
        
        debug {
            // Debug build optimizations for faster builds
            isMinifyEnabled = false
            isShrinkResources = false
            isDebuggable = true
            
            // Disable unnecessary features for debug builds
            renderscriptOptimLevel = 0
            isJniDebuggable = false
            
            // Firebase Performance Monitoring configuration for debug
            manifestPlaceholders["firebase_performance_logcat_enabled"] = true
            manifestPlaceholders["firebase_performance_collection_enabled"] = false
            
            versionNameSuffix = "-DEBUG"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
        // Disable unused features for faster builds
        aidl = false
        renderScript = false
        resValues = false
        shaders = false
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
        disable.addAll(listOf("MissingTranslation", "ExtraTranslation"))
        
        // Custom lint rules to prevent Room schema issues
        fatal.addAll(listOf(
            "RoomUndefinedDefaultValue",    // Prevent defaultValue = "undefined"
            "RoomInvalidDefaultValue"       // Validate proper Room default values
        ))
        
        // Optimize lint for faster builds
        checkDependencies = false
        checkGeneratedSources = false
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation("androidx.compose.runtime:runtime-livedata")
    implementation("androidx.compose.runtime:runtime-tracing")
    implementation(libs.androidx.material3.window.size.util)
    implementation("androidx.compose.material:material-icons-extended")
    
    // Chart library - Add explicit core dependency for axis functions
    implementation(libs.vico.compose)
    implementation(libs.vico.compose.m3)
    implementation(libs.vico.core)
    
    // Pull-to-refresh for Material 3
    implementation("eu.bambooapps:compose-material3-pullrefresh:1.1.1")
    
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.auth)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    implementation("com.google.android.gms:play-services-auth:21.3.0")
    implementation(libs.firebase.database)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.ai)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.perf)
    implementation(libs.firebase.config)
    
    // Google Play Billing
    implementation("com.android.billingclient:billing-ktx:6.0.1")
    
    // Room dependencies
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.firebase.storage)
    // Switch to KSP for better compatibility with SDK 35
    ksp(libs.room.compiler)
    // kapt(libs.room.compiler)
    
    // Navigation
    implementation(libs.navigation.compose)
    
    // JSON serialization
    implementation(libs.gson)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)
    
    // Dependency Injection
    implementation(libs.hilt.android)
    implementation(libs.hilt.work)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)
    
    // WorkManager
    implementation(libs.work.runtime.ktx)
    
    // DataStore for preferences
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    
    // Logging
    implementation(libs.timber)
    
    // Export Libraries
    implementation(libs.itext7.core)
    implementation(libs.itext7.layout)
    implementation(libs.commons.csv)
    
    // Image processing dependencies
    implementation(libs.coil.compose)
    implementation(libs.androidx.exifinterface)
    
    // QR Code generation dependencies
    implementation(libs.zxing.core)
    implementation(libs.zxing.android.embedded) {
        isTransitive = false
    }
    
    // Missing dependencies for KSP and ASM instrumentation
    implementation(libs.androidx.window)
    implementation(libs.androidx.window.core)
    implementation(libs.guava)
    implementation(libs.androidx.animation)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.jdk8)
    
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation(libs.androidx.core.testing)
    testImplementation(libs.app.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.test.core)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.room.testing)
    testImplementation(libs.androidx.sqlite.framework)
    testImplementation(libs.androidx.runner)
    
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.benchmark.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.mockk.android)
    androidTestImplementation(libs.app.turbine)
    androidTestImplementation(libs.firebase.firestore.ktx)
    androidTestImplementation(libs.androidx.work.testing)
    androidTestImplementation(libs.test.core)
    androidTestImplementation(libs.androidx.runner)
    androidTestImplementation(libs.androidx.rules)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.android.compiler)
    
    // Room testing dependencies for MigrationTestHelper
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.androidx.sqlite.framework)
    
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

// Room Query Validation Task
tasks.register<Exec>("validateRoomQueries") {
    group = "verification"
    description = "Validates Room entity default values and DAO queries"
    
    dependsOn("compileDebugKotlin")
    
    // Use Exec task type for configuration cache compatibility
    commandLine("bash", "../scripts/validate_room_defaults.sh")
    workingDir = projectDir
    
    doLast {
        println("✅ Room query validation completed successfully")
    }
}

// Make Room validation run before tests (using correct task names)
// Use afterEvaluate to ensure tasks exist before configuring dependencies
afterEvaluate {
    tasks.findByName("testDebugUnitTest")?.dependsOn("validateRoomQueries")
    tasks.findByName("testReleaseUnitTest")?.dependsOn("validateRoomQueries")
    tasks.findByName("connectedDebugAndroidTest")?.dependsOn("validateRoomQueries")
}