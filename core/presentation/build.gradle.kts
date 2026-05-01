plugins {
    id("liftrix.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.example.liftrix.core.presentation"
}

dependencies {
    api(project(":core:domain-common"))

    implementation(project(":core:model"))
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-savedstate:2.8.7")
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)
    implementation(libs.timber)
    compileOnly("javax.inject:javax.inject:1")
}
