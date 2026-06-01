plugins {
    id("liftrix.android.library")
}

android {
    namespace = "com.example.liftrix.feature.home.api"
}

dependencies {
    api(project(":core:domain"))
    api(project(":core:domain-common"))
    api(project(":core:model"))

    api("androidx.paging:paging-runtime:3.2.1")
    implementation(libs.kotlinx.coroutines.android)
}
