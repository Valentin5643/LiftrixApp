plugins {
    id("liftrix.android.library")
    id("com.google.dagger.hilt.android")
    id("kotlin-parcelize")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.example.liftrix.core.data"

    defaultConfig {
        buildConfigField("String", "VERSION_NAME", "\"0.0.0\"")
        buildConfigField("int", "VERSION_CODE", "1")
        buildConfigField("String", "GIT_COMMIT_HASH", "\"unknown\"")
        buildConfigField("String", "BUILD_TIME_MILLIS", "\"0\"")
        buildConfigField("String", "BUILD_MACHINE", "\"unknown\"")
        buildConfigField("int", "TARGET_SDK_VERSION", "35")
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core:analytics"))
    implementation(project(":core:database"))
    implementation(project(":core:domain"))
    implementation(project(":core:domain-common"))
    implementation(project(":core:model"))
    implementation(project(":core:network"))
    implementation(project(":core:presentation"))
    implementation(project(":core:notifications"))
    implementation(project(":feature:home-api"))
    implementation(project(":feature:settings-api"))

    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.config)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.functions)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.perf)
    implementation(libs.firebase.storage)
    implementation(libs.firebase.ai)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation("androidx.room:room-paging:2.5.2")
    implementation("androidx.paging:paging-runtime:3.2.1")
    implementation("androidx.paging:paging-common:3.2.1")
    implementation(libs.hilt.android)
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation(libs.gson)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.jdk8)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)
    implementation(libs.timber)
    implementation(libs.itext7.core)
    implementation(libs.itext7.layout)
    implementation(libs.commons.csv)
    implementation(libs.androidx.exifinterface)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation(libs.zxing.core)
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("com.android.billingclient:billing-ktx:6.0.1")
    implementation("javax.inject:javax.inject:1")

    add("kspDebug", libs.hilt.compiler)
    add("kspRelease", libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}
