package com.example.liftrix.domain.interactor.admin

import com.example.liftrix.domain.model.admin.AdminBanInfo
import com.example.liftrix.domain.model.admin.BanUserRequest
import com.example.liftrix.domain.model.admin.BanUserResponse
import com.example.liftrix.domain.model.admin.GetAdminLogsRequest
import com.example.liftrix.domain.model.admin.ListBannedUsersRequest
import com.example.liftrix.domain.model.admin.ListBannedUsersResponse
import com.example.liftrix.domain.model.admin.SearchUsersRequest
import com.example.liftrix.domain.model.admin.SearchUsersResponse
import com.example.liftrix.domain.model.admin.UnbanUserRequest
import com.example.liftrix.domain.model.admin.UnbanUserResponse
import com.example.liftrix.domain.model.admin.UserBanInfoResponse
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.usecase.admin.BanUserUseCase
import com.example.liftrix.domain.usecase.admin.CheckAdminPermissionsUseCase
import com.example.liftrix.domain.usecase.admin.GetAdminLogsUseCase
import com.example.liftrix.domain.usecase.admin.GetUserBanInfoUseCase
import com.example.liftrix.domain.usecase.admin.ListBannedUsersUseCase
import com.example.liftrix.domain.usecase.admin.SearchUsersUseCase
import com.example.liftrix.domain.usecase.admin.UnbanUserUseCase
import javax.inject.Inject

class AdminInteractor @Inject constructor(
    private val checkAdminPermissionsUseCase: CheckAdminPermissionsUseCase,
    private val searchUsersUseCase: SearchUsersUseCase,
    private val banUserUseCase: BanUserUseCase,
    private val unbanUserUseCase: UnbanUserUseCase,
    private val getUserBanInfoUseCase: GetUserBanInfoUseCase,
    private val listBannedUsersUseCase: ListBannedUsersUseCase,
    private val getAdminLogsUseCase: GetAdminLogsUseCase
) {
    suspend fun checkPermissions(userId: String): LiftrixResult<Boolean> =
        checkAdminPermissionsUseCase(userId)

    suspend fun searchUsers(request: SearchUsersRequest): LiftrixResult<SearchUsersResponse> =
        searchUsersUseCase(request)

    suspend fun banUser(request: BanUserRequest): LiftrixResult<BanUserResponse> =
        banUserUseCase(request)

    suspend fun unbanUser(request: UnbanUserRequest): LiftrixResult<UnbanUserResponse> =
        unbanUserUseCase(request)

    suspend fun getUserBanInfo(userId: String): LiftrixResult<UserBanInfoResponse> =
        getUserBanInfoUseCase(userId)

    suspend fun listBannedUsers(request: ListBannedUsersRequest): LiftrixResult<ListBannedUsersResponse> =
        listBannedUsersUseCase(request)

    suspend fun getAdminLogs(request: GetAdminLogsRequest): LiftrixResult<List<AdminBanInfo>> =
        getAdminLogsUseCase(request)
}
