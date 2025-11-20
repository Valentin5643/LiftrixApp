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
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.toUElement

/**
 * Lint detector that enforces user scoping in DAO queries.
 */
class EnforceUserScopingDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UClass::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitClass(node: UClass) {
                val className = node.name ?: return
                if (!className.endsWith("Dao")) return

                node.methods.forEach { method ->
                    checkDaoMethod(context, method, className)
                }
            }
        }
    }

    private fun checkDaoMethod(context: JavaContext, method: UMethod, className: String) {
        // Find @Query annotation by checking source text
        val methodText = method.text ?: return
        if (!methodText.contains("@Query")) return

        // Extract query string from annotation
        val queryMatch = Regex("@Query\\s*\\(\\s*\"([^\"]+)\"").find(methodText)
        val queryValue = queryMatch?.groupValues?.get(1) ?: return

        checkQueryForUserScoping(context, method, queryValue, className)
    }

    private fun checkQueryForUserScoping(
        context: JavaContext,
        method: UMethod,
        queryValue: String,
        className: String
    ) {
        val queryUpper = queryValue.uppercase()

        // Only check SELECT queries
        if (!queryUpper.trimStart().startsWith("SELECT")) return

        // Tables that require user scoping
        val userScopedTables = listOf(
            "WORKOUTS", "WORKOUT_SESSIONS", "EXERCISES", "SETS",
            "TEMPLATES", "FOLDERS", "ACHIEVEMENTS", "ANALYTICS_CACHE",
            "CHAT_HISTORY", "SOCIAL_PROFILES", "WORKOUT_POSTS",
            "SYNC_QUEUE", "USER_SETTINGS", "NOTIFICATIONS"
        )

        // Check if query references user-scoped table
        val referencesUserScopedTable = userScopedTables.any { table ->
            queryUpper.contains(table)
        }

        if (!referencesUserScopedTable) return

        // Check for user scoping in query or parameters
        val hasUserScoping = queryValue.contains("user_id") ||
                queryValue.contains("userId") ||
                queryValue.contains(":userId") ||
                queryValue.contains(":user_id")

        val hasUserIdParameter = method.uastParameters.any { param ->
            val paramName = param.name?.lowercase() ?: ""
            paramName.contains("userid") || paramName.contains("user_id")
        }

        if (!hasUserScoping && !hasUserIdParameter) {
            val element: UElement = method
            val location = context.getLocation(element)
            context.report(
                ISSUE,
                location,
"DAO query in '$className.${method.name}' is missing user scoping. " +
                "Add 'WHERE user_id = `:userId`' to prevent data leakage"
            )
        }
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "MissingUserScoping",
            briefDescription = "DAO query missing user_id filter",
            explanation = """
                DAO queries accessing user data must filter by `user_id` to prevent data leakage. \
                Add 'WHERE user_id = `:userId`' to all queries on user-scoped tables.
            """,
            category = Category.SECURITY,
            priority = 9,
            severity = Severity.ERROR,
            implementation = Implementation(
                EnforceUserScopingDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}
