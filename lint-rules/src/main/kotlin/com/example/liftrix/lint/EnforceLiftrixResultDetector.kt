package com.example.liftrix.lint

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod

/**
 * Lint detector that enforces the use of LiftrixResult<T> for UseCase return types.
 */
class EnforceLiftrixResultDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UClass::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitClass(node: UClass) {
                val className = node.name ?: return
                if (!className.endsWith("UseCase")) return

                node.methods.forEach { method ->
                    checkMethod(context, method)
                }
            }
        }
    }

    private fun checkMethod(context: JavaContext, method: UMethod) {
        if (method.isConstructor) return

        val methodName = method.name

        // Focus on business methods
        val isBusinessMethod = methodName == "invoke" ||
                methodName.startsWith("save") ||
                methodName.startsWith("update") ||
                methodName.startsWith("delete") ||
                methodName.startsWith("fetch") ||
                methodName.startsWith("sync") ||
                methodName.startsWith("execute")

        if (!isBusinessMethod) return

        // Check if method is suspend
        val methodText = method.text ?: return
        if (!methodText.contains("suspend")) return

        // Get return type
        val returnType = method.returnType?.canonicalText ?: return

        // Skip Unit returns
        if (returnType == "void" || returnType.contains("Unit")) return

        // Check for LiftrixResult
        val hasLiftrixResult = returnType.contains("LiftrixResult")

        // Check for legacy Result
        val hasLegacyResult = returnType.startsWith("Result<") && !hasLiftrixResult

        if (hasLegacyResult) {
            val element: UElement = method
            val location = context.getLocation(element)
            context.report(
                ISSUE_LEGACY_RESULT,
                location,
                "`UseCase` method '$methodName' uses legacy `Result<T>`. Migrate to `LiftrixResult<T>`"
            )
        } else if (!hasLiftrixResult) {
            val element: UElement = method
            val location = context.getLocation(element)
            context.report(
                ISSUE,
                location,
"`UseCase` method '$methodName' should return `LiftrixResult<T>`"
            )
        }
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "MissingLiftrixResult",
            briefDescription = "UseCase method should return LiftrixResult",
            explanation = "`UseCase` business methods should return `LiftrixResult<T>` for consistent error handling",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                EnforceLiftrixResultDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )

        @JvmField
        val ISSUE_LEGACY_RESULT: Issue = Issue.create(
            id = "LegacyResultUsage",
            briefDescription = "UseCase uses legacy Result<T>",
            explanation = "`UseCase` methods should use `LiftrixResult<T>` instead of `kotlin.Result<T>`",
            category = Category.CORRECTNESS,
            priority = 7,
            severity = Severity.WARNING,
            implementation = Implementation(
                EnforceLiftrixResultDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}
