plugins {
    id("liftrix.android.library")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.example.liftrix.core.data"
}

dependencies {
    implementation(project(":core:database"))
    implementation(project(":core:domain"))
    implementation(project(":core:domain-common"))
    implementation(project(":core:model"))
    implementation(project(":core:network"))

    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.config)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.functions)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.storage)
    implementation(libs.firebase.ai)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation("androidx.room:room-paging:2.5.2")
    implementation("androidx.paging:paging-runtime:3.2.1")
    implementation("androidx.paging:paging-common:3.2.1")
    implementation(libs.hilt.android)
    implementation(libs.hilt.work)
    implementation(libs.work.runtime.ktx)
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation(libs.gson)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.jdk8)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)
    implementation(libs.timber)
    implementation(libs.itext7.core)
    implementation(libs.itext7.layout)
    implementation(libs.commons.csv)
    implementation(libs.androidx.exifinterface)
    implementation(libs.zxing.core)
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("javax.inject:javax.inject:1")

    add("kspDebug", libs.hilt.compiler)
    add("kspRelease", libs.hilt.compiler)
}
