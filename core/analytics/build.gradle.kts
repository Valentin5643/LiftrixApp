plugins {
    id("liftrix.android.library")
}

android {
    namespace = "com.example.liftrix.core.analytics"
}

dependencies {
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation(libs.firebase.config)
    implementation(libs.timber)
    implementation("javax.inject:javax.inject:1")
}
