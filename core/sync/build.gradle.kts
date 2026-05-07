plugins {
    id("liftrix.android.library")
    id("com.google.dagger.hilt.android")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.example.liftrix.core.sync"
}

dependencies {
    implementation(project(":core:analytics"))
    implementation(project(":core:data"))
    implementation(project(":core:database"))
    implementation(project(":core:domain"))
    implementation(project(":core:domain-common"))
    implementation(project(":core:model"))
    implementation(project(":core:network"))
    implementation(project(":core:notifications"))

    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.functions)
    implementation(libs.firebase.messaging)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.hilt.android)
    implementation(libs.hilt.work)
    implementation(libs.work.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)
    implementation(libs.gson)
    implementation(libs.guava)
    implementation(libs.timber)
    implementation(libs.room.ktx)

    ksp(libs.hilt.compiler)
    ksp(libs.androidx.hilt.compiler)
}
