plugins {
    id("liftrix.android.library")
    id("com.google.dagger.hilt.android")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.example.liftrix.core.database"

    defaultConfig {
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
            arg("room.incremental", "true")
            arg("room.expandProjection", "true")
        }
    }
}

val cleanKspByRounds by tasks.registering(org.gradle.api.tasks.Delete::class) {
    delete(
        fileTree(layout.buildDirectory.dir("generated/ksp")) {
            include("**/java/byRounds/**")
        }
    )
    mustRunAfter(
        tasks.matching {
            it.name.startsWith("ksp")
        }
    )
}

tasks.withType<org.gradle.api.tasks.compile.JavaCompile>().configureEach {
    dependsOn(cleanKspByRounds)
}

dependencies {
    api(project(":core:model"))
    api(project(":core:domain"))
    implementation(project(":core:domain-common"))
    implementation(project(":user-scoping-annotations"))

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation("androidx.room:room-paging:2.5.2")
    implementation("androidx.paging:paging-runtime:3.2.1")
    implementation(libs.gson)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)
    implementation(libs.timber)
    implementation(libs.hilt.android)
    implementation("net.zetetic:sqlcipher-android:4.6.0@aar")
    implementation("androidx.sqlite:sqlite:2.4.0")
    implementation("javax.inject:javax.inject:1")

    add("kspDebug", libs.room.compiler)
    add("kspRelease", libs.room.compiler)
    add("kspDebug", libs.hilt.compiler)
    add("kspRelease", libs.hilt.compiler)
    add("kspDebug", project(":user-scoping-processor"))
    add("kspRelease", project(":user-scoping-processor"))

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.runner)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.androidx.sqlite.framework)
    androidTestImplementation("androidx.annotation:annotation-jvm:1.8.1")
    androidTestImplementation("androidx.tracing:tracing:1.2.0")
}
