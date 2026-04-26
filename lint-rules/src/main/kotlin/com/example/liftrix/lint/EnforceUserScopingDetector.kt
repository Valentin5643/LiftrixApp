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
        val queryMatch = Regex(
            "@Query\\s*\\(\\s*(\"\"\"([\\s\\S]*?)\"\"\"|\"([^\"]+)\")",
            setOf(RegexOption.DOT_MATCHES_ALL)
        ).find(methodText)
        val queryValue = queryMatch?.groups?.get(2)?.value
            ?: queryMatch?.groups?.get(3)?.value
            ?: return

        checkQueryForUserScoping(context, method, queryValue, className)
    }

    private fun checkQueryForUserScoping(
        context: JavaContext,
        method: UMethod,
        queryValue: String,
        className: String
    ) {
        val queryUpper = queryValue.uppercase()

        val queryType = queryUpper.trimStart()
        val isSelect = queryType.startsWith("SELECT")
        val isUpdate = queryType.startsWith("UPDATE")
        val isDelete = queryType.startsWith("DELETE")

        if (!isSelect && !isUpdate && !isDelete) return

        // Tables that require user scoping
        val userScopedTables = listOf(
            "WORKOUTS",
            "WORKOUT_SESSIONS",
            "EXERCISES",
            "EXERCISE_SETS",
            "TEMPLATES",
            "FOLDERS",
            "ACHIEVEMENTS",
            "ANALYTICS_CACHE",
            "CHAT_HISTORY",
            "SOCIAL_PROFILES",
            "WORKOUT_POSTS",
            "SYNC_QUEUE",
            "USER_SETTINGS",
            "NOTIFICATIONS",
            "FOLLOW_RELATIONSHIPS",
            "BLOCKED_USERS",
            "GYM_BUDDIES",
            "PERSONAL_RECORDS",
            "PROGRESS_PHOTOS",
            "DATA_EXPORTS"
        )

        // Check if query references user-scoped table
        val referencesUserScopedTable = userScopedTables.any { table ->
            queryUpper.contains(table)
        }

        if (!referencesUserScopedTable) return

        // Check for user scoping in WHERE clause
        val hasUserScoping = queryValue.contains("user_id") ||
                queryValue.contains("userId") ||
                queryValue.contains(":userId") ||
                queryValue.contains(":user_id")

        val hasUserIdParameter = method.uastParameters.any { param ->
            val paramName = param.name?.lowercase() ?: ""
            paramName.contains("userid") || paramName.contains("user_id")
        }

        // Enhanced validation for UPDATE queries
        if (isUpdate) {
            // Check if UPDATE attempts to modify user_id column (security violation)
            val setClausePattern = Regex("SET\\s+.*?user_id\\s*=", RegexOption.IGNORE_CASE)
            if (setClausePattern.containsMatchIn(queryValue)) {
                val element: UElement = method
                val location = context.getLocation(element)
                context.report(
                    ISSUE_UPDATE_USER_ID,
                    location,
                    "UPDATE query in '$className.${method.name}' attempts to modify user_id column. " +
                    "This is a critical security violation - user_id must be immutable"
                )
                return
            }

            // Validate WHERE clause contains user_id
            if (!hasUserScoping && !hasUserIdParameter) {
                val element: UElement = method
                val location = context.getLocation(element)
                context.report(
                    ISSUE_UPDATE_DELETE,
                    location,
                    "UPDATE query in '$className.${method.name}' is missing user scoping in WHERE clause. " +
                    "Add 'WHERE user_id = :userId' to prevent cross-user data modification (SEC-003)"
                )
                return
            }
        }

        // Enhanced validation for DELETE queries
        if (isDelete) {
            if (!hasUserScoping && !hasUserIdParameter) {
                val element: UElement = method
                val location = context.getLocation(element)
                context.report(
                    ISSUE_UPDATE_DELETE,
                    location,
                    "DELETE query in '$className.${method.name}' is missing user scoping in WHERE clause. " +
                    "Add 'WHERE user_id = :userId' to prevent cross-user data deletion (SEC-003)"
                )
                return
            }
        }

        // Standard validation for SELECT queries
        if (isSelect && !hasUserScoping && !hasUserIdParameter) {
            val element: UElement = method
            val location = context.getLocation(element)
            context.report(
                ISSUE,
                location,
                "SELECT query in '$className.${method.name}' is missing user scoping. " +
                "Add 'WHERE user_id = :userId' to prevent data leakage"
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

        @JvmField
        val ISSUE_UPDATE_DELETE: Issue = Issue.create(
            id = "MissingUserScopingUpdateDelete",
            briefDescription = "UPDATE/DELETE query missing user_id filter",
            explanation = """
                UPDATE and DELETE queries on user-scoped tables MUST include `user_id` in the WHERE clause \
                to prevent cross-user data modification or deletion. This is a critical security requirement \
                (SEC-003) that prevents privilege escalation attacks.

                Add 'WHERE user_id = :userId' to all UPDATE and DELETE queries.
            """,
            category = Category.SECURITY,
            priority = 10,
            severity = Severity.ERROR,
            implementation = Implementation(
                EnforceUserScopingDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )

        @JvmField
        val ISSUE_UPDATE_USER_ID: Issue = Issue.create(
            id = "UpdateUserIdViolation",
            briefDescription = "UPDATE query attempts to modify user_id column",
            explanation = """
                Modifying the `user_id` column in an UPDATE query is a critical security violation. \
                The user_id field is immutable and defines data ownership. Allowing modification could \
                enable unauthorized access to other users' data.

                Remove any SET clause that modifies user_id.
            """,
            category = Category.SECURITY,
            priority = 10,
            severity = Severity.ERROR,
            implementation = Implementation(
                EnforceUserScopingDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}
