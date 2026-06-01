plugins {
    id("liftrix.android.library")
    id("com.google.dagger.hilt.android")
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.example.liftrix.core.network"
}

dependencies {
    implementation(project(":core:domain-common"))
    implementation(project(":core:model"))

    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)

    implementation(libs.gson)
    implementation(libs.hilt.android)
    implementation(libs.kotlinx.coroutines.android)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
    implementation(libs.kotlinx.serialization.json)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation(libs.timber)
    implementation("javax.inject:javax.inject:1")

    ksp(libs.hilt.compiler)
}
