package com.example.liftrix.processor

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSValueArgument
import com.google.devtools.ksp.validate

class UserScopedProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return UserScopedProcessor(environment.logger)
    }
}

class UserScopedProcessor(
    private val logger: KSPLogger
) : SymbolProcessor {

    override fun process(resolver: Resolver): List<KSClassDeclaration> {
        val deferred = mutableListOf<KSClassDeclaration>()

        resolver.getSymbolsWithAnnotation(USER_SCOPED_DAO)
            .filterIsInstance<KSClassDeclaration>()
            .forEach { dao ->
                if (!dao.validate()) {
                    deferred.add(dao)
                    return@forEach
                }
                validateDaoClass(dao)
            }

        resolver.getSymbolsWithAnnotation(USER_SCOPED)
            .filterIsInstance<KSFunctionDeclaration>()
            .forEach { method ->
                if (!method.validate()) {
                    return@forEach
                }
                validateMethod(method)
            }

        return deferred
    }

    private fun validateDaoClass(daoElement: KSClassDeclaration) {
        daoElement.getAllFunctions()
            .filter { method -> method.parentDeclaration == daoElement }
            .filter { method -> method.annotations.any { it.shortName.asString() == "Query" } }
            .forEach { method -> validateMethod(method) }
    }

    private fun validateMethod(method: KSFunctionDeclaration) {
        val queryAnnotation = method.annotations.firstOrNull {
            it.shortName.asString() == "Query" &&
                it.annotationType.resolve().declaration.qualifiedName?.asString() == QUERY_ANNOTATION
        } ?: return

        val queryValue = getQueryValue(queryAnnotation.arguments) ?: return
        val queryUpper = queryValue.uppercase()

        val isUserScopedTable = USER_SCOPED_TABLES.any { queryUpper.contains(it) }
        if (!isUserScopedTable) return

        // Determine query type
        val trimmedQuery = queryUpper.trimStart()
        val isSelect = trimmedQuery.startsWith("SELECT")
        val isUpdate = trimmedQuery.startsWith("UPDATE")
        val isDelete = trimmedQuery.startsWith("DELETE")

        val expectedParam = getExpectedUserParam(method)
        val hasUserIdParam = method.parameters.any { param ->
            param.name?.asString()?.equals(expectedParam, ignoreCase = true) == true
        }
        val hasUserIdInQuery = queryUpper.contains("USER_ID") ||
            queryUpper.contains(":${expectedParam.uppercase()}")

        // Enhanced validation for UPDATE queries
        if (isUpdate) {
            // Check if UPDATE attempts to modify user_id column (critical security violation)
            val setClausePattern = Regex("SET\\s+.*?USER_ID\\s*=")
            if (setClausePattern.containsMatchIn(queryUpper)) {
                logger.error(
                    "[SECURITY VIOLATION] Method ${method.simpleName.asString()} attempts to modify user_id column. " +
                        "This is forbidden - user_id must be immutable to prevent unauthorized data access.",
                    method
                )
                return
            }

            // Validate WHERE clause contains user_id for UPDATE
            if (!hasUserIdInQuery) {
                logger.error(
                    "[SEC-003] UPDATE query in ${method.simpleName.asString()} is missing user_id in WHERE clause. " +
                        "This could allow cross-user data modification. Add 'WHERE user_id = :$expectedParam'.",
                    method
                )
            }

            if (!hasUserIdParam) {
                logger.error(
                    "UPDATE method ${method.simpleName.asString()} is missing '$expectedParam' parameter. " +
                        "Required for user scoping validation.",
                    method
                )
            }
            return
        }

        // Enhanced validation for DELETE queries
        if (isDelete) {
            if (!hasUserIdInQuery) {
                logger.error(
                    "[SEC-003] DELETE query in ${method.simpleName.asString()} is missing user_id in WHERE clause. " +
                        "This could allow cross-user data deletion. Add 'WHERE user_id = :$expectedParam'.",
                    method
                )
            }

            if (!hasUserIdParam) {
                logger.error(
                    "DELETE method ${method.simpleName.asString()} is missing '$expectedParam' parameter. " +
                        "Required for user scoping validation.",
                    method
                )
            }
            return
        }

        // Standard validation for SELECT queries
        if (isSelect) {
            if (!hasUserIdParam) {
                logger.error(
                    "SELECT method ${method.simpleName.asString()} is missing '$expectedParam' parameter. " +
                        "All user-scoped queries must include user ID filtering.",
                    method
                )
            }

            if (!hasUserIdInQuery) {
                logger.error(
                    "SELECT query in ${method.simpleName.asString()} is missing user_id filter. " +
                        "Add 'WHERE user_id = :$expectedParam' to prevent data leakage.",
                    method
                )
            }
        }
    }

    private fun getExpectedUserParam(method: KSFunctionDeclaration): String {
        val annotation = method.annotations.firstOrNull {
            it.annotationType.resolve().declaration.qualifiedName?.asString() == USER_SCOPED
        }
        if (annotation == null) {
            return DEFAULT_USER_PARAM
        }

        val argument = annotation.arguments.firstOrNull { it.name?.asString() == "userIdParam" }
        return argument?.value as? String ?: DEFAULT_USER_PARAM
    }

    private fun getQueryValue(arguments: List<KSValueArgument>): String? {
        val valueArg = arguments.firstOrNull { it.name?.asString() == "value" } ?: arguments.firstOrNull()
        return valueArg?.value as? String
    }

    companion object {
        private const val QUERY_ANNOTATION = "androidx.room.Query"
        private const val USER_SCOPED = "com.example.liftrix.annotations.UserScoped"
        private const val USER_SCOPED_DAO = "com.example.liftrix.annotations.UserScopedDao"
        private const val DEFAULT_USER_PARAM = "userId"

        private val USER_SCOPED_TABLES = listOf(
            "WORKOUTS",
            "WORKOUT_SESSIONS",
            "EXERCISES",
            "EXERCISE_SETS",
            "TEMPLATES",
            "FOLDERS",
            "ACHIEVEMENTS",
            "ANALYTICS_CACHE",
            "CHAT_HISTORY",
            "CHAT_CONVERSATIONS",
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
    }
}
