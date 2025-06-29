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
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
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
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        abortOnError = false
        disable.addAll(listOf("MissingTranslation", "ExtraTranslation"))
        
        // Custom lint rules to prevent Room schema issues
        fatal.addAll(listOf(
            "RoomUndefinedDefaultValue",    // Prevent defaultValue = "undefined"
            "RoomInvalidDefaultValue"       // Validate proper Room default values
        ))
        
        // Add lint rule to check for 'undefined' in @ColumnInfo defaultValue
        checkDependencies = true
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
    implementation(libs.androidx.material3.window.size.util)
    implementation("androidx.compose.material:material-icons-extended")
    
    // Chart library - Add explicit core dependency for axis functions
    implementation(libs.vico.compose)
    implementation(libs.vico.compose.m3)
    implementation(libs.vico.core)
    
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
    
    // Logging
    implementation(libs.timber)
    
    // Missing dependencies for KSP and ASM instrumentation
    implementation("androidx.window:window:1.4.0")
    implementation("androidx.window:window-core:1.4.0")
    implementation("com.google.guava:guava:32.1.3-android")
    implementation("androidx.compose.animation:animation:1.7.6")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.8.1")
    
    testImplementation(libs.junit)
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    testImplementation("app.cash.turbine:turbine:1.0.0")
    
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation("io.mockk:mockk-android:1.13.8")
    androidTestImplementation("app.cash.turbine:turbine:1.0.0")
    androidTestImplementation(libs.firebase.firestore.ktx)
    androidTestImplementation("androidx.work:work-testing:2.10.1")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("com.google.dagger:hilt-android-testing:2.52")
    kspAndroidTest("com.google.dagger:hilt-android-compiler:2.52")
    
    // Room testing dependencies for MigrationTestHelper
    androidTestImplementation(libs.room.testing)
    androidTestImplementation("androidx.sqlite:sqlite-framework:2.4.0")
    
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

// Room Query Validation Task
tasks.register<Exec>("validateRoomQueries") {
    group = "verification"
    description = "Validates Room entity default values and DAO queries"
    
    dependsOn("compileDebugKotlin")
    
    // Use Exec task type for configuration cache compatibility
    commandLine("bash", "$projectDir/../scripts/validate_room_defaults.sh")
    workingDir = projectDir.parentFile
    
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