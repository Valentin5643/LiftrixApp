package com.example.liftrix.ui.profile

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertFalse
import org.junit.Test

class ProfileFirebaseBoundaryTest {
    @Test
    fun profileUiFilesDoNotUseFirebaseAuthSingletonFallback() {
        val auditedFiles = listOf(
            "feature/profile/src/main/java/com/example/liftrix/ui/profile/ProfileViewModel.kt",
            "feature/profile/src/main/java/com/example/liftrix/ui/profile/components/ModernProfileHeader.kt"
        )

        auditedFiles.forEach { relativePath ->
            val source = String(Files.readAllBytes(resolveSourcePath(relativePath)))
            assertFalse(
                "$relativePath must not call FirebaseAuth.getInstance() from profile UI code",
                source.contains("FirebaseAuth.getInstance()")
            )
        }
    }

    private fun resolveSourcePath(rootRelativePath: String): Path {
        val rootPath = Paths.get(rootRelativePath)
        if (Files.exists(rootPath)) return rootPath

        val moduleRelativePath = Paths.get(
            rootRelativePath.removePrefix("feature/profile/")
        )
        if (Files.exists(moduleRelativePath)) return moduleRelativePath

        error("Could not resolve source path: $rootRelativePath")
    }
}
