plugins {
    id("liftrix.android.library")
    id("com.google.dagger.hilt.android")
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.example.liftrix.core.analytics"
}

dependencies {
    implementation(project(":core:model"))

    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.config)
    implementation(libs.hilt.android)
    implementation(libs.timber)
    implementation("javax.inject:javax.inject:1")

    ksp(libs.hilt.compiler)
}
