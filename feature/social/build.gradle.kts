plugins {
    id("liftrix.android.compose.library")
    id("com.google.dagger.hilt.android")
    id("kotlin-parcelize")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.example.liftrix.feature.social"
}

dependencies {
    implementation(project(":core:analytics"))
    implementation(project(":core:design-system"))
    implementation(project(":core:domain"))
    implementation(project(":core:domain-common"))
    implementation(project(":core:model"))
    implementation(project(":core:notifications"))
    implementation(project(":core:presentation"))
    implementation(project(":core:ui"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.navigation.compose)
    implementation(libs.hilt.android)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)
    implementation(libs.coil.compose)
    implementation(libs.timber)
    implementation("androidx.paging:paging-runtime:3.2.1")
    implementation("androidx.paging:paging-compose:3.2.1")
    implementation("eu.bambooapps:compose-material3-pullrefresh:1.1.1")
    implementation("com.google.android.material:material:1.13.0")
    implementation(libs.zxing.core)
    implementation(libs.zxing.android.embedded) {
        isTransitive = false
    }
    implementation("androidx.camera:camera-core:1.4.0")
    implementation("androidx.camera:camera-camera2:1.4.0")
    implementation("androidx.camera:camera-lifecycle:1.4.0")
    implementation("androidx.camera:camera-view:1.4.0")
    implementation(libs.guava)
    implementation("javax.inject:javax.inject:1")

    ksp(libs.hilt.compiler)
    debugImplementation(libs.androidx.ui.tooling)
}
