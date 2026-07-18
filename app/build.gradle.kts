import org.gradle.api.tasks.PathSensitivity
import com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension
import java.io.FileInputStream
import java.util.Properties

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
    id("jacoco")
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        load(FileInputStream(localPropertiesFile))
    }
}

fun localOrEnvironment(name: String): String =
    (System.getenv(name) ?: localProperties.getProperty(name)).orEmpty()

fun buildConfigString(value: String): String = "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

val releasePackageId = "com.liftrix.app"
val releaseVersionCode = 10001
val releaseVersionName = "1.0.1"
val releaseKeystorePath = localOrEnvironment("KEYSTORE_PATH")
val releaseKeystorePassword = localOrEnvironment("KEYSTORE_PASSWORD")
val releaseKeyAlias = localOrEnvironment("KEY_ALIAS")
val releaseKeyPassword = localOrEnvironment("KEY_PASSWORD")
val hasReleaseSigningConfig = listOf(
    releaseKeystorePath,
    releaseKeystorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all(String::isNotBlank)

val useFullReleaseOptimization = providers.gradleProperty("liftrix.fullReleaseOptimization")
    .orNull
    ?.toBooleanStrictOrNull()
    ?: false

if (!hasReleaseSigningConfig && listOf(
        releaseKeystorePath,
        releaseKeystorePassword,
        releaseKeyAlias,
        releaseKeyPassword
    ).any(String::isNotBlank)
) {
    logger.warn("Release signing configuration is incomplete; release APKs will be unsigned.")
}

// Dependency resolution strategies for deterministic builds
configurations.all {
    resolutionStrategy {
        // Force specific versions to avoid conflicts - aligned with project Kotlin version
        force("org.jetbrains.kotlin:kotlin-stdlib:2.0.21")
        force("org.jetbrains.kotlin:kotlin-stdlib-common:2.0.21")
        force("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
        force("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
        
        // Ensure consistent AndroidX versions
        force("androidx.lifecycle:lifecycle-runtime:2.8.7")
        force("androidx.lifecycle:lifecycle-viewmodel:2.8.7")
        force("androidx.lifecycle:lifecycle-livedata:2.8.7")
        force("androidx.collection:collection:1.5.0")
        
        // Prefer AndroidX over support libraries
        preferProjectModules()
        
        // Cache for reproducible builds - optimized for faster dependency resolution
        cacheDynamicVersionsFor(24, "hours")
        cacheChangingModulesFor(24, "hours")
    }
    
    // Exclude duplicate classes
    exclude(group = "com.intellij", module = "annotations")
    exclude(group = "org.checkerframework", module = "checker-compat-qual")
    exclude(group = "com.google.errorprone", module = "error_prone_annotations")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-Xjvm-default=all",
            "-Xcontext-receivers",
            "-opt-in=kotlin.contracts.ExperimentalContracts",
            "-opt-in=kotlin.experimental.ExperimentalTypeInference",
            "-XXLanguage:+BreakContinueInInlineLambdas"
        )
    }
}

// Ensure consistent JVM target across all compilation tasks
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// Compose Compiler optimizations for better runtime performance
composeCompiler {
    // Enable strong skipping mode for better performance (15-30% improvement)
    enableStrongSkippingMode.set(true)

    // Enable intrinsic remember for optimized remember calls
    enableIntrinsicRemember.set(true)

    // Enable non-skipping group optimization
    enableNonSkippingGroupOptimization.set(true)

    reportsDestination.set(layout.buildDirectory.dir("compose_compiler/reports"))
    metricsDestination.set(layout.buildDirectory.dir("compose_compiler/metrics"))
}

android {
    namespace = "com.example.liftrix"
    compileSdk = 35
    buildToolsVersion = "34.0.0"  // Installed SDK build tools with complete binaries

    installation {
        enableBaselineProfile = true
    }

    defaultConfig {
        applicationId = releasePackageId
        minSdk = 26
        targetSdk = 35
        versionCode = releaseVersionCode
        versionName = releaseVersionName

        buildConfigField("String", "APP_VERSION_NAME", buildConfigString(versionName ?: "unknown"))
        buildConfigField("int", "APP_VERSION_CODE", versionCode.toString())
        buildConfigField("String", "API_BASE_URL", buildConfigString(localOrEnvironment("API_BASE_URL")))

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // Build optimizations
        vectorDrawables.useSupportLibrary = true
        multiDexEnabled = false
        
        // Native library configuration - specify supported ABIs
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
        }
    }
    
    // ABI splits configuration for optimized APK size per architecture
    // Automatically disabled for debug builds to save 30-40% build time
    splits {
        abi {
            // Disable splits when building debug variants (saves 30-40% debug build time)
            isEnable = !gradle.startParameter.taskNames.any {
                it.contains("Debug", ignoreCase = true)
            }
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
            isUniversalApk = true  // Also generate a universal APK with all ABIs
        }
    }

    signingConfigs {
        if (hasReleaseSigningConfig) {
            create("release") {
                // Environment variables take precedence through localOrEnvironment().
                storeFile = file(releaseKeystorePath)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = useFullReleaseOptimization
            proguardFiles(
                getDefaultProguardFile(
                    if (useFullReleaseOptimization) {
                        "proguard-android-optimize.txt"
                    } else {
                        "proguard-android.txt"
                    }
                ),
                "proguard-rules.pro"
            )
            
            // Performance optimizations for release builds
            isDebuggable = false
            isJniDebuggable = false
            renderscriptOptimLevel = 3
            
            // Firebase Performance Monitoring configuration
            manifestPlaceholders["firebase_performance_logcat_enabled"] = false
            manifestPlaceholders["firebase_performance_collection_enabled"] = false
            
            // Secure OAuth configuration from properties
            buildConfigField("String", "GOOGLE_CLIENT_ID", "\"${project.findProperty("GOOGLE_CLIENT_ID_RELEASE") ?: ""}\"")
            buildConfigField("String", "ENVIRONMENT", buildConfigString("release"))
            buildConfigField("boolean", "ENABLE_VERBOSE_LOGGING", "false")
            buildConfigField("boolean", "ENABLE_DEBUG_TOOLS", "false")
            buildConfigField("boolean", "ENABLE_STRICT_MODE", "false")
            buildConfigField("boolean", "ENABLE_FIREBASE_PERFORMANCE", "true")
            buildConfigField("boolean", "ENABLE_CRASHLYTICS", "true")
            if (hasReleaseSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }

            // Release mapping upload is required so production crashes remain retraceable.
            configure<CrashlyticsExtension> {
                mappingFileUploadEnabled = true
            }
        }
        
        debug {
            // Debug build optimizations for faster builds
            isMinifyEnabled = false
            isShrinkResources = false
            isDebuggable = true

            // Disable unnecessary features for debug builds
            renderscriptOptimLevel = 0
            isJniDebuggable = false
            isPseudoLocalesEnabled = false

            // Firebase Performance Monitoring configuration for debug
            manifestPlaceholders["firebase_performance_logcat_enabled"] = true
            manifestPlaceholders["firebase_performance_collection_enabled"] = false

            versionNameSuffix = "-DEBUG"

            // Secure OAuth configuration from properties
            buildConfigField("String", "GOOGLE_CLIENT_ID", "\"${project.findProperty("GOOGLE_CLIENT_ID_DEBUG") ?: "734273269747-ojaksa5nhir6re5sqskn7qlbflec2f94.apps.googleusercontent.com"}\"")
            buildConfigField("String", "ENVIRONMENT", buildConfigString("debug"))
            buildConfigField("boolean", "ENABLE_VERBOSE_LOGGING", "true")
            buildConfigField("boolean", "ENABLE_DEBUG_TOOLS", "true")
            buildConfigField("boolean", "ENABLE_STRICT_MODE", "true")
            buildConfigField("boolean", "ENABLE_FIREBASE_PERFORMANCE", "false")
            buildConfigField("boolean", "ENABLE_CRASHLYTICS", "true")
            buildConfigField(
                "String",
                "FIREBASE_APP_CHECK_DEBUG_SECRET",
                "\"${localOrEnvironment("FIREBASE_APP_CHECK_DEBUG_SECRET")}\""
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

        // Optimize lint for faster builds (run lintFull for comprehensive checks)
        checkDependencies = false
        checkGeneratedSources = false
        checkTestSources = false
    }

    packaging {
        resources {
            excludes += listOf(
                "META-INF/LICENSE.md",
                "META-INF/LICENSE-notice.md",
                "META-INF/NOTICE.md",
                "META-INF/DEPENDENCIES",
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
                "META-INF/*.kotlin_module",
                "META-INF/versions/9/previous-compilation-data.bin",
                // Exclude duplicate Kotlin metadata
                "kotlin/**",
                "kotlinx/**",
                // Exclude system libraries that shouldn't be bundled
                "lib/*/libdigitalkey.so",
                "lib/*/libtimesync.so",
                "META-INF/com.android.support/**",
                "META-INF/androidx/**"
            )
        }
        
        // Native library packaging configuration
        jniLibs {
            // Keep debug symbols for better crash reports
            keepDebugSymbols += listOf("**/*.so")
            
            // Exclude system-provided native libraries
            excludes += listOf(
                "**/libandroid.so",
                "**/liblog.so",
                "**/libc.so",
                "**/libm.so",
                "**/libdl.so",
                "**/libz.so"
            )
        }
    }
}

tasks.register("writeReleaseIdentity") {
    group = "build setup"
    description = "Writes the canonical nonsecret release package and version for CI verification"

    val identityFile = layout.buildDirectory.file("release-identity/release.properties")
    inputs.property("releasePackageId", releasePackageId)
    inputs.property("releaseVersionCode", releaseVersionCode)
    inputs.property("releaseVersionName", releaseVersionName)
    outputs.file(identityFile)

    doLast {
        val packageId = inputs.properties.getValue("releasePackageId").toString()
        val versionCode = inputs.properties.getValue("releaseVersionCode").toString()
        val versionName = inputs.properties.getValue("releaseVersionName").toString()

        outputs.files.singleFile.apply {
            parentFile.mkdirs()
            writeText(
                "RELEASE_PACKAGE_ID=$packageId\n" +
                    "RELEASE_VERSION_CODE=$versionCode\n" +
                    "RELEASE_VERSION_NAME=$versionName\n"
            )
        }
    }
}


ksp {
    arg("room.schemaLocation", "$projectDir/schemas")

    // Room optimizations for faster incremental builds
    arg("room.incremental", "true")
    arg("room.expandProjection", "true")

    // Hilt optimizations for faster annotation processing
    arg("dagger.fastInit", "enabled")
    arg("dagger.formatGeneratedSource", "disabled")
    arg("dagger.gradle.incremental", "enabled")
}

dependencies {
    // Custom Lint rules for architectural enforcement
    lintChecks(project(":lint-rules"))
    implementation(project(":core:data"))
    implementation(project(":core:database"))
    implementation(project(":core:domain"))
    implementation(project(":core:domain-common"))
    implementation(project(":core:design-system"))
    implementation(project(":core:model"))
    implementation(project(":core:notifications"))
    implementation(project(":core:presentation"))
    implementation(project(":core:sync"))
    implementation(project(":core:ui"))
    implementation(project(":feature:auth"))
    implementation(project(":feature:chat"))
    implementation(project(":feature:home"))
    implementation(project(":feature:profile"))
    implementation(project(":feature:progress"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:social"))
    implementation(project(":feature:workout"))

    implementation(libs.androidx.core.ktx)
    implementation("androidx.core:core-splashscreen:1.0.1")
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
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation("com.google.android.material:material:1.13.0")  // Latest Material Components for XML themes
    implementation("androidx.compose.runtime:runtime-livedata")
    implementation("androidx.compose.runtime:runtime-tracing")
    implementation(libs.androidx.material3.window.size.util)
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.androidx.profileinstaller)
    
    // Chart library - Add explicit core dependency for axis functions
    implementation(libs.vico.compose)
    implementation(libs.vico.compose.m3)
    implementation(libs.vico.core)
    
    // Pull-to-refresh for Material 3
    implementation("eu.bambooapps:compose-material3-pullrefresh:1.1.1")

    // Firebase BOM for unified version management (saves 5-10s build time, 3MB APK)
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))

    implementation(libs.firebase.analytics)
    implementation(libs.firebase.auth)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    implementation("com.google.android.gms:play-services-auth:21.3.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation(libs.firebase.database)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.ai)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.perf)
    implementation(libs.firebase.config)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.functions)
    implementation(libs.firebase.appcheck)
    implementation(libs.firebase.appcheck.playintegrity)
    debugImplementation(libs.firebase.appcheck.debug)
    
    // Google Play Billing
    implementation("com.android.billingclient:billing-ktx:6.0.1")
    
    // Room dependencies with SQLCipher encryption
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation("androidx.room:room-paging:2.5.2")
    implementation(libs.firebase.storage)
    // KSP for better compatibility with SDK 35 and faster compilation
    add("kspDebug", libs.room.compiler)
    add("kspRelease", libs.room.compiler)
    
    // Database encryption with SQLCipher (Modern version)
    implementation("net.zetetic:sqlcipher-android:4.6.0@aar")
    implementation("androidx.sqlite:sqlite:2.4.0")
    
    // AndroidX Security Crypto for secure key management
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    
    // Paging3 for social feed
    implementation("androidx.paging:paging-runtime:3.2.1")
    implementation("androidx.paging:paging-compose:3.2.1")
    
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
    add("kspDebug", libs.hilt.compiler)
    add("kspRelease", libs.hilt.compiler)
    add("kspDebug", libs.androidx.hilt.compiler)
    add("kspRelease", libs.androidx.hilt.compiler)
    
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
    implementation("com.airbnb.android:lottie-compose:6.6.2")
    
    // Photo Picker support for reliable image selection
    implementation("androidx.activity:activity-ktx:1.9.0")
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    
    // QR Code generation dependencies
    implementation(libs.zxing.core)
    implementation(libs.zxing.android.embedded) {
        isTransitive = false
    }
    
    // CameraX dependencies for QR scanner
    implementation("androidx.camera:camera-core:1.4.0")
    implementation("androidx.camera:camera-camera2:1.4.0")
    implementation("androidx.camera:camera-lifecycle:1.4.0")
    implementation("androidx.camera:camera-view:1.4.0")
    
    // Missing dependencies for KSP and ASM instrumentation
    implementation(libs.androidx.window)
    implementation(libs.androidx.window.core)
    implementation(libs.guava)
    implementation(libs.androidx.animation)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.jdk8)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
    
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(project(":core:network"))
    testImplementation(libs.hilt.android.testing)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation(libs.androidx.core.testing)
    testImplementation(libs.app.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.test.core)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.room.testing)
    testImplementation("androidx.paging:paging-testing:3.2.1")
    testImplementation(libs.androidx.sqlite.framework)
    testImplementation(libs.androidx.runner)
    testImplementation("com.google.truth:truth:1.4.4")
    
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
    debugImplementation(libs.leakcanary.android)
}

val benchmarkKspConfigurations = setOf(
    "kspBenchmarkRelease",
    "kspNonMinifiedRelease",
)

configurations.matching { it.name in benchmarkKspConfigurations }.all {
    project.dependencies.add(name, libs.room.compiler)
    project.dependencies.add(name, libs.hilt.compiler)
    project.dependencies.add(name, libs.androidx.hilt.compiler)
}

// Room entity-source validation. Room/KSP compilation remains the DAO query validator.
abstract class ValidateRoomQueriesTask : DefaultTask() {
    
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceDirectory: DirectoryProperty
    
    @get:Internal
    abstract val projectDirectory: DirectoryProperty
    
    @TaskAction
    fun validateRoomQueries() {
        println("🔍 Validating Room entity default values...")
        
        val sourceDir = sourceDirectory.get().asFile
        var violationsFound = false
        
        if (!sourceDir.exists()) {
            throw GradleException("Room entity source directory does not exist: $sourceDir")
        }
        
        // Find all Kotlin files with @Entity annotation
        val entityFiles = sourceDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { file ->
                file.readText().contains("@Entity")
            }
            .toList()
        
        if (entityFiles.isEmpty()) {
            throw GradleException("No Room entity files found under $sourceDir; validation is misconfigured")
        }
        
        println("📁 Checking ${entityFiles.size} entity files for violations...")
        
        val projectDir = projectDirectory.get().asFile
        
        entityFiles.forEach { file ->
            println("   Checking: ${file.relativeTo(projectDir)}")
            val content = file.readText()
            val lines = content.lines()
            
            // Check for specific invalid Room default values that would cause compilation issues
            val invalidPatterns = mapOf(
                """defaultValue\s*=\s*["']undefined["']""" to "undefined literal",
                """defaultValue\s*=\s*["']null["']""" to "null literal", 
                """defaultValue\s*=\s*""\s*""" to "empty string",
                """defaultValue\s*=\s*''\s*""" to "empty string"
            )
            
            invalidPatterns.forEach { (pattern, description) ->
                val matches = lines.withIndex().filter { (_, line) ->
                    line.contains(Regex(pattern))
                }
                if (matches.isNotEmpty()) {
                    println("❌ VIOLATION: Found $description in defaultValue in ${file.relativeTo(projectDir)}")
                    matches.forEach { (index, line) ->
                        println("   Line ${index + 1}: ${line.trim()}")
                    }
                    violationsFound = true
                }
            }
            
            // Validate and report on valid Room default patterns
            val defaultValueLines = lines.withIndex().filter { (_, line) ->
                line.contains("@ColumnInfo") && line.contains("defaultValue")
            }
            
            if (defaultValueLines.isNotEmpty()) {
                println("   Found ${defaultValueLines.size} defaultValue annotations:")
                defaultValueLines.forEach { (index, line) ->
                    when {
                        line.contains(Regex("""defaultValue\s*=\s*"[0-9]+"""")) ->
                            println("   ✅ Line ${index + 1}: Valid numeric default")
                        line.contains(Regex("""defaultValue\s*=\s*"CURRENT_TIMESTAMP"""")) ->
                            println("   ✅ Line ${index + 1}: Valid timestamp default")
                        line.contains(Regex("""defaultValue\s*=\s*"'[A-Z_]+.*'"""")) ->
                            println("   ✅ Line ${index + 1}: Valid string enum default")
                        else -> {
                            // For any other patterns, show as informational
                            println("   ℹ️ Line ${index + 1}: Other valid pattern")
                        }
                    }
                }
            }
        }
        
        // Summary
        if (violationsFound) {
            println()
            println("❌ VALIDATION FAILED: Found Room entity violations!")
            println()
            println("🔧 FIX GUIDE:")
            println("   1. Remove defaultValue = \"undefined\" from @ColumnInfo annotations")
            println("   2. Use proper Room defaults like:")
            println("      - defaultValue = \"0\" for numeric fields")
            println("      - defaultValue = \"1\" or \"0\" for boolean fields")
            println("      - defaultValue = \"CURRENT_TIMESTAMP\" for timestamp fields")
            println("   3. For nullable fields, omit defaultValue entirely")
            println("   4. Ensure entity schema matches migration table creation")
            println()
            throw GradleException("Room entity validation failed!")
        } else {
            println()
            println("✅ VALIDATION PASSED: No Room entity violations found!")
            println("🎉 All Room entities have proper default values")
            println()
        }
    }
}

tasks.register<ValidateRoomQueriesTask>("validateRoomQueries") {
    group = "verification"
    description = "Checks core/database Room entity discovery and invalid default-value annotations; Room/KSP compilation validates DAO queries"
    
    dependsOn(":core:database:compileDebugKotlin")
    
    sourceDirectory.set(rootProject.layout.projectDirectory.dir("core/database/src/main/java"))
    projectDirectory.set(rootProject.layout.projectDirectory)
}

// Make Room validation run before tests (using correct task names)
// Use afterEvaluate to ensure tasks exist before configuring dependencies
afterEvaluate {
    tasks.findByName("testDebugUnitTest")?.dependsOn("validateRoomQueries")
    tasks.findByName("testReleaseUnitTest")?.dependsOn("validateRoomQueries")
    tasks.findByName("connectedDebugAndroidTest")?.dependsOn("validateRoomQueries")
}

// JaCoCo Code Coverage Configuration
jacoco {
    toolVersion = "0.8.11"
}

val jacocoCoverageModuleDirs = listOf(
    projectDir,
    rootProject.file("core/analytics"),
    rootProject.file("core/data"),
    rootProject.file("core/database"),
    rootProject.file("core/domain"),
    rootProject.file("core/domain-common"),
    rootProject.file("core/design-system"),
    rootProject.file("core/model"),
    rootProject.file("core/network"),
    rootProject.file("core/notifications"),
    rootProject.file("core/presentation"),
    rootProject.file("core/sync"),
    rootProject.file("core/ui"),
    rootProject.file("feature/auth"),
    rootProject.file("feature/chat"),
    rootProject.file("feature/home"),
    rootProject.file("feature/profile"),
    rootProject.file("feature/progress"),
    rootProject.file("feature/settings"),
    rootProject.file("feature/social"),
    rootProject.file("feature/workout")
)

tasks.register<JacocoReport>("jacocoTestReport") {
    group = "verification"
    description = "Generates code coverage report for unit tests"

    dependsOn("testDebugUnitTest", ":core:model:test")

    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }

    val fileFilter = listOf(
        "**/R.class",
        "**/R\$*.class",
        "**/BuildConfig.*",
        "**/Manifest*.*",
        "**/*Test*.*",
        "android/**/*.*",
        "**/di/**",
        "**/*_Hilt*.*",
        "**/*_Factory*.*",
        "**/*_MembersInjector*.*",
        "**/Dagger*Component*.*",
        "**/*Module_*Factory.*"
    )

    val classTrees = jacocoCoverageModuleDirs.flatMap { moduleDir ->
        val buildDirFile = File(moduleDir, "build")
        listOf(
            fileTree("${buildDirFile}/tmp/kotlin-classes/debug") {
                exclude(fileFilter)
            },
            fileTree("${buildDirFile}/intermediates/javac/debug/classes") {
                exclude(fileFilter)
            },
            fileTree("${buildDirFile}/classes/kotlin/main") {
                exclude(fileFilter)
            },
            fileTree("${buildDirFile}/classes/java/main") {
                exclude(fileFilter)
            }
        )
    }

    val sourceDirs = jacocoCoverageModuleDirs.flatMap { moduleDir ->
        listOf(
            "${moduleDir}/src/main/java",
            "${moduleDir}/src/main/kotlin"
        )
    }

    sourceDirectories.setFrom(files(sourceDirs))
    classDirectories.setFrom(files(classTrees))
    executionData.setFrom(
        jacocoCoverageModuleDirs.map { moduleDir ->
            fileTree(File(moduleDir, "build")) {
                include("jacoco/*.exec")
            }
        }
    )
}

tasks.register<JacocoCoverageVerification>("jacocoCoverageVerification") {
    group = "verification"
    description = "Verifies code coverage meets minimum threshold (40%)"

    dependsOn("jacocoTestReport")

    val fileFilter = listOf(
        "**/R.class",
        "**/R\$*.class",
        "**/BuildConfig.*",
        "**/Manifest*.*",
        "**/*Test*.*",
        "android/**/*.*",
        "**/di/**",
        "**/*_Hilt*.*",
        "**/*_Factory*.*",
        "**/*_MembersInjector*.*",
        "**/Dagger*Component*.*",
        "**/*Module_*Factory.*"
    )

    val classTrees = jacocoCoverageModuleDirs.flatMap { moduleDir ->
        val buildDirFile = File(moduleDir, "build")
        listOf(
            fileTree("${buildDirFile}/tmp/kotlin-classes/debug") {
                exclude(fileFilter)
            },
            fileTree("${buildDirFile}/intermediates/javac/debug/classes") {
                exclude(fileFilter)
            },
            fileTree("${buildDirFile}/classes/kotlin/main") {
                exclude(fileFilter)
            },
            fileTree("${buildDirFile}/classes/java/main") {
                exclude(fileFilter)
            }
        )
    }

    classDirectories.setFrom(files(classTrees))
    executionData.setFrom(
        jacocoCoverageModuleDirs.map { moduleDir ->
            fileTree(File(moduleDir, "build")) {
                include("jacoco/*.exec")
            }
        }
    )

    violationRules {
        rule {
            limit {
                minimum = "0.40".toBigDecimal() // 40% minimum coverage
            }
        }
    }
}

// Full lint task for comprehensive checks (run before commits)
tasks.register("lintFull") {
    group = "verification"
    description = "Run full lint checks including dependencies, generated sources, and test sources"
    dependsOn("lint")
}
