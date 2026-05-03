plugins {
    id("liftrix.android.library")
}

android {
    namespace = "com.example.liftrix.core.notifications"
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:domain-common"))
    implementation(project(":core:model"))

    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation(libs.firebase.messaging)
    implementation(libs.androidx.core.ktx)
    implementation(libs.hilt.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.timber)
    implementation("javax.inject:javax.inject:1")
}
