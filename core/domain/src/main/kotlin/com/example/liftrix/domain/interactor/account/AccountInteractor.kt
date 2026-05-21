package com.example.liftrix.domain.interactor.account

import com.example.liftrix.domain.model.UserAccount
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.usecase.account.AccountCommandUseCase
import com.example.liftrix.domain.usecase.account.AccountQueryUseCase
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class AccountInteractor @Inject constructor(
    private val accountQueryUseCase: AccountQueryUseCase,
    private val accountCommandUseCase: AccountCommandUseCase
) {
    suspend fun accountInfoFlow(): Flow<UserAccount?> =
        accountQueryUseCase.asFlow()

    suspend fun accountInfo(): LiftrixResult<UserAccount?> =
        accountQueryUseCase()

    suspend fun updateEmail(newEmail: String, currentPassword: String): LiftrixResult<Unit> =
        accountCommandUseCase.updateEmail(newEmail, currentPassword)

    suspend fun updatePassword(currentPassword: String, newPassword: String): LiftrixResult<Unit> =
        accountCommandUseCase.updatePassword(currentPassword, newPassword)

    suspend fun updateUsername(username: String?): LiftrixResult<Unit> =
        accountCommandUseCase.updateUsername(username)

    suspend fun deleteAccount(
        reauthProvider: String,
        reauthPayload: String,
        exportDataFirst: Boolean = false
    ): LiftrixResult<String> = accountCommandUseCase.deleteAccount(
        reauthProvider = reauthProvider,
        reauthPayload = reauthPayload,
        exportDataFirst = exportDataFirst
    )
}
