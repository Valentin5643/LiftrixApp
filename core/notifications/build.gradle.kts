plugins {
    id("liftrix.android.library")
    id("com.google.dagger.hilt.android")
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.example.liftrix.core.notifications"
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:domain-common"))
    implementation(project(":core:model"))
    implementation(project(":core:ui"))

    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.messaging)
    implementation(libs.hilt.work)
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation(libs.androidx.core.ktx)
    implementation(libs.hilt.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.timber)
    implementation("javax.inject:javax.inject:1")

    add("kspDebug", libs.hilt.compiler)
    add("kspRelease", libs.hilt.compiler)

    testImplementation(libs.junit)
}
