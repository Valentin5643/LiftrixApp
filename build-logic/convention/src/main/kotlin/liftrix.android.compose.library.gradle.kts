import com.android.build.gradle.LibraryExtension
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension

plugins {
    id("liftrix.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
}

extensions.configure<LibraryExtension> {
    buildFeatures {
        compose = true
    }
}

extensions.configure<ComposeCompilerGradlePluginExtension> {
    enableStrongSkippingMode.set(true)
    enableIntrinsicRemember.set(true)
    enableNonSkippingGroupOptimization.set(true)
}
