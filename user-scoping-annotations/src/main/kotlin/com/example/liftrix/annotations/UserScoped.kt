package com.example.liftrix.annotations

/**
 * Marks a DAO method as requiring user scoping.
 * The annotation processor validates that the method:
 * 1. Has a userId: String parameter
 * 2. The @Query string contains user_id filtering
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class UserScoped(
    val userIdParam: String = "userId"
)

/**
 * Marks an entire DAO as requiring user scoping on all query methods.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class UserScopedDao
