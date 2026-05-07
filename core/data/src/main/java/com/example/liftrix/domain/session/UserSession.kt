package com.example.liftrix.domain.session

import com.example.liftrix.domain.model.UserId
import com.example.liftrix.domain.model.error.LiftrixError
import com.google.firebase.auth.FirebaseAuth
import javax.inject.Inject

class UserSession @Inject constructor(
    private val firebaseAuth: FirebaseAuth
) {
    val currentUserId: UserId?
        get() = firebaseAuth.currentUser?.uid?.let(UserId::fromStringOrNull)

    suspend fun requireUserId(): UserId {
        return currentUserId ?: throw LiftrixError.UnauthenticatedError()
    }

    fun isAuthenticated(): Boolean = currentUserId != null
}
