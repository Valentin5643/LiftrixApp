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

/**
 * Lint detector that prevents direct FirebaseFirestore injection in UseCase classes.
 */
class NoDirectFirebaseInjectionDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UClass::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitClass(node: UClass) {
                val className = node.name ?: return
                if (!className.endsWith("UseCase")) return

                // Check constructor parameters
                node.methods.filter { it.isConstructor }.forEach { constructor ->
                    constructor.uastParameters.forEach { param ->
                        val paramTypeName = param.type.canonicalText

                        // Check for Firebase direct injection
                        if (paramTypeName.contains("FirebaseFirestore") ||
                            paramTypeName.contains("FirebaseStorage") ||
                            paramTypeName.contains("FirebaseDatabase")) {

                            val element: UElement = param
                            val location = context.getLocation(element)
                            context.report(
                                ISSUE,
                                location,
                                "Direct Firebase injection in `UseCase` violates Room-First architecture. " +
                                "Use Repository pattern instead"
                            )
                        }
                    }
                }
            }
        }
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "DirectFirebaseInjection",
            briefDescription = "Direct Firebase injection in UseCase",
            explanation = """
                `UseCases` should not directly inject Firebase services. \
                Use Repository pattern to ensure offline-first behavior.
            """,
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.ERROR,
            implementation = Implementation(
                NoDirectFirebaseInjectionDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}
