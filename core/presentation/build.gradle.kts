plugins {
    id("liftrix.android.library")
    id("com.google.dagger.hilt.android")
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.example.liftrix.core.presentation"
}

dependencies {
    implementation(project(":core:domain"))
    api(project(":core:domain-common"))

    implementation(project(":core:model"))
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-savedstate:2.8.7")
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)
    implementation(libs.androidx.exifinterface)
    implementation(libs.timber)
    implementation(libs.hilt.android)
    compileOnly("javax.inject:javax.inject:1")

    add("kspDebug", libs.hilt.compiler)
    add("kspRelease", libs.hilt.compiler)
}
