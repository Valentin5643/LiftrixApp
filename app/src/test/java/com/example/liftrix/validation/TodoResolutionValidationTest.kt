package com.example.liftrix.validation

import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.File

/**
 * Automated validation test to ensure no TODO comments remain in production code
 * 
 * This test scans the entire codebase for TODO comments and fails if any are found
 * in production code (excluding test files). This ensures all placeholder code
 * and incomplete implementations have been properly resolved.
 * 
 * The test is critical for:
 * - Code quality assurance
 * - Production readiness verification  
 * - Preventing incomplete features from reaching users
 * - Maintaining professional code standards
 */
@RunWith(JUnit4::class)
class TodoResolutionValidationTest {

    companion object {
        // Patterns to identify TODO comments (case-insensitive)
        private val TODO_PATTERNS = listOf(
            Regex("//\\s*TODO", RegexOption.IGNORE_CASE),
            Regex("/\\*\\s*TODO", RegexOption.IGNORE_CASE),
            Regex("\\*\\s*TODO", RegexOption.IGNORE_CASE),
            Regex("//\\s*FIXME", RegexOption.IGNORE_CASE),
            Regex("//\\s*HACK", RegexOption.IGNORE_CASE),
            Regex("//\\s*XXX", RegexOption.IGNORE_CASE)
        )

        // Directories to exclude from TODO scanning
        private val EXCLUDED_DIRECTORIES = setOf(
            "test",           // Unit tests
            "androidTest",    // Instrumentation tests
            "build",          // Build artifacts
            ".gradle",        // Gradle cache
            ".git",           // Git metadata
            "docs"            // Documentation
        )

        // File extensions to scan
        private val SCANNED_EXTENSIONS = setOf(
            "kt",             // Kotlin files
            "java",           // Java files
            "xml",            // Layout and manifest files
            "gradle",         // Build scripts
            "kts"             // Kotlin script files
        )

        // Allowed TODO patterns (for legitimate use cases)
        private val ALLOWED_TODO_PATTERNS = listOf(
            // Example: TODO comments in configuration files might be acceptable
            Regex("TODO.*@suppress.*validation", RegexOption.IGNORE_CASE)
        )
    }

    data class TodoInstance(
        val file: File,
        val lineNumber: Int,
        val line: String,
        val todoText: String
    )

    @Test
    fun `production code should not contain TODO comments`() {
        // Given: Project source directory
        val projectRoot = findProjectRoot()
        assertNotNull("Project root should be found", projectRoot)

        val srcDir = File(projectRoot, "app/src/main")
        assertTrue("Source directory should exist: ${srcDir.absolutePath}", srcDir.exists())

        // When: Scanning for TODO comments
        val todoInstances = scanForTodos(srcDir)

        // Filter out allowed TODOs
        val unresolved = todoInstances.filter { todo ->
            !isAllowedTodo(todo)
        }

        // Then: No unresolved TODOs should remain
        if (unresolved.isNotEmpty()) {
            val errorMessage = buildString {
                appendLine("❌ Found ${unresolved.size} unresolved TODO comment(s) in production code:")
                appendLine()
                
                unresolved.groupBy { it.file }.forEach { (file, todos) ->
                    val relativePath = file.relativeTo(projectRoot).path
                    appendLine("📁 $relativePath")
                    todos.forEach { todo ->
                        appendLine("   Line ${todo.lineNumber}: ${todo.todoText.trim()}")
                    }
                    appendLine()
                }
                
                appendLine("All TODO comments must be resolved before production release.")
                appendLine("Either complete the implementation or remove the TODO comment.")
            }
            
            fail(errorMessage)
        }

        // Success message
        println("✅ TODO Resolution Validation: All ${todoInstances.size} TODO comments have been resolved or are in allowed locations")
    }

    @Test
    fun `test files may contain TODO comments for future test cases`() {
        // Given: Test directories (this test allows TODOs in test code)
        val projectRoot = findProjectRoot()
        assertNotNull("Project root should be found", projectRoot)

        val testDirs = listOf(
            File(projectRoot, "app/src/test"),
            File(projectRoot, "app/src/androidTest")
        )

        // When: Scanning test directories
        val testTodos = testDirs.filter { it.exists() }
            .flatMap { scanForTodos(it) }

        // Then: Report test TODOs for visibility but don't fail
        if (testTodos.isNotEmpty()) {
            println("ℹ️ Found ${testTodos.size} TODO comment(s) in test files (allowed):")
            testTodos.take(5).forEach { todo ->
                val relativePath = todo.file.relativeTo(projectRoot!!).path
                println("   $relativePath:${todo.lineNumber} - ${todo.todoText.trim()}")
            }
            if (testTodos.size > 5) {
                println("   ... and ${testTodos.size - 5} more")
            }
        }

        // This test always passes - it's just for reporting
        assertTrue("Test TODO reporting completed", true)
    }

    @Test
    fun `specific social system files should be TODO-free`() {
        // Given: Critical social system files that must be complete
        val projectRoot = findProjectRoot()
        assertNotNull("Project root should be found", projectRoot)

        val criticalFiles = listOf(
            "app/src/main/java/com/example/liftrix/ui/feed/FeedScreen.kt",
            "app/src/main/java/com/example/liftrix/ui/feed/FeedViewModel.kt",
            "app/src/main/java/com/example/liftrix/data/repository/social/FeedRepositoryImpl.kt",
            "app/src/main/java/com/example/liftrix/data/repository/social/EngagementRepositoryImpl.kt",
            "app/src/main/java/com/example/liftrix/service/QRCodeServiceImpl.kt",
            "app/src/main/java/com/example/liftrix/service/NotificationRouterImpl.kt",
            "app/src/main/java/com/example/liftrix/service/PRDetectionServiceImpl.kt"
        )

        // When: Scanning critical files
        val criticalTodos = mutableListOf<TodoInstance>()
        
        criticalFiles.forEach { filePath ->
            val file = File(projectRoot, filePath)
            if (file.exists()) {
                criticalTodos.addAll(scanFileForTodos(file))
            }
        }

        // Then: Critical files must be completely TODO-free
        if (criticalTodos.isNotEmpty()) {
            val errorMessage = buildString {
                appendLine("❌ Critical social system files contain TODO comments:")
                appendLine()
                
                criticalTodos.groupBy { it.file }.forEach { (file, todos) ->
                    val relativePath = file.relativeTo(projectRoot!!).path
                    appendLine("🚨 $relativePath")
                    todos.forEach { todo ->
                        appendLine("   Line ${todo.lineNumber}: ${todo.todoText.trim()}")
                    }
                    appendLine()
                }
                
                appendLine("These critical files must be 100% complete for production release.")
            }
            
            fail(errorMessage)
        }

        println("✅ All critical social system files are TODO-free")
    }

    private fun findProjectRoot(): File? {
        var current = File("").absoluteFile
        
        // Walk up directory tree to find project root (contains app/build.gradle.kts)
        while (current.parent != null) {
            val buildGradle = File(current, "app/build.gradle.kts")
            if (buildGradle.exists()) {
                return current
            }
            current = current.parentFile
        }
        
        return null
    }

    private fun scanForTodos(directory: File): List<TodoInstance> {
        val todos = mutableListOf<TodoInstance>()
        
        directory.walkTopDown()
            .filter { file ->
                file.isFile &&
                file.extension in SCANNED_EXTENSIONS &&
                !shouldExcludeFile(file)
            }
            .forEach { file ->
                todos.addAll(scanFileForTodos(file))
            }
        
        return todos
    }

    private fun scanFileForTodos(file: File): List<TodoInstance> {
        val todos = mutableListOf<TodoInstance>()
        
        try {
            file.readLines().forEachIndexed { index, line ->
                val lineNumber = index + 1
                
                TODO_PATTERNS.forEach { pattern ->
                    val match = pattern.find(line)
                    if (match != null) {
                        todos.add(
                            TodoInstance(
                                file = file,
                                lineNumber = lineNumber,
                                line = line,
                                todoText = line.substring(match.range.first).take(100)
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            // Skip files that can't be read
            println("Warning: Could not scan file ${file.absolutePath}: ${e.message}")
        }
        
        return todos
    }

    private fun shouldExcludeFile(file: File): Boolean {
        val path = file.absolutePath
        
        return EXCLUDED_DIRECTORIES.any { excludedDir ->
            path.contains("/$excludedDir/") || path.contains("\\$excludedDir\\")
        }
    }

    private fun isAllowedTodo(todo: TodoInstance): Boolean {
        return ALLOWED_TODO_PATTERNS.any { pattern ->
            pattern.containsMatchIn(todo.line)
        }
    }
}