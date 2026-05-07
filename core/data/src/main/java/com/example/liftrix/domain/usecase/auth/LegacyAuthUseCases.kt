package com.example.liftrix.domain.usecase.auth

import com.example.liftrix.domain.model.User
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.repository.AuthRepository
import javax.inject.Inject

class GetCurrentUserIdUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(): String? = authRepository.getCurrentUserId()?.value
}

class SignUpWithEmailUseCase @Inject constructor(
    private val authCommandUseCase: AuthCommandUseCase
) {
    suspend operator fun invoke(
        email: String,
        password: String,
        username: String
    ): LiftrixResult<User> = authCommandUseCase.signUpWithEmail(email, password, username)
}
