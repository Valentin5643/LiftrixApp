package com.example.liftrix.startup

import java.nio.file.Files
import java.nio.file.Paths
import java.nio.charset.StandardCharsets
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StartupInitializationPolicyTest {

    private val root = generateSequence(Paths.get("").toAbsolutePath()) { it.parent }
        .firstOrNull { Files.exists(it.resolve("settings.gradle.kts")) }
        ?: error("Could not locate repository root")

    @Test
    fun databaseProviderDoesNotBlockForSeedInitialization() {
        val appSource = readSource("app/src/main/java/com/example/liftrix/di/DatabaseModule.kt")
        val coreSource = readSource(
            "core/database/src/main/java/com/example/liftrix/data/local/di/CoreDatabaseModule.kt"
        )
        val startupSource = readSource(
            "app/src/main/java/com/example/liftrix/startup/PostFirstScreenStartupCoordinator.kt"
        )

        assertFalse(
            coreSource.contains("runBlocking(Dispatchers.IO)") ||
                appSource.contains("runBlocking(Dispatchers.IO)"),
            "Database provider must not block startup on library or MET seed initialization."
        )
        assertTrue(
            startupSource.contains("databaseSeedInitializer.runDeferredSeeds"),
            "Database seed initialization should be explicitly triggered after first authenticated content."
        )
    }

    @Test
    fun startupUsesInjectedApplicationScopeForBackgroundWork() {
        val source = readSource("app/src/main/java/com/example/liftrix/LiftrixApp.kt")

        assertFalse(
            source.contains("CoroutineScope(SupervisorJob()"),
            "Application startup must use the injected application scope instead of a private long-lived scope."
        )
        assertTrue(
            source.contains("@ApplicationScope"),
            "LiftrixApp should receive the canonical application scope from DI."
        )
    }

    @Test
    fun connectivityRestartDoesNotBlockThread() {
        val source = readSource(
            "core/data/src/main/java/com/example/liftrix/data/service/NetworkConnectivityMonitorImpl.kt"
        )

        assertFalse(
            source.contains("Thread.sleep("),
            "Connectivity restart should use non-blocking scheduling."
        )
        assertTrue(
            source.contains("delay(100)"),
            "Connectivity restart should preserve the short restart gap without blocking a thread."
        )
    }

    @Test
    fun debugStartupVerificationIsDebugGated() {
        val source = readSource("app/src/main/java/com/example/liftrix/LiftrixApp.kt")

        assertTrue(
            source.contains("if (BuildConfig.DEBUG)") && source.contains("debugVerifyWorkerFactory()"),
            "WorkerFactory debug verification should stay gated to debug builds."
        )
        assertTrue(
            source.contains("ReleaseLoggingTree()"),
            "Release startup should use the release logging tree when verbose logging is disabled."
        )
    }

    private fun readSource(path: String): String =
        String(Files.readAllBytes(root.resolve(path)), StandardCharsets.UTF_8)
}
