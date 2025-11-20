package com.example.liftrix.ui.admin

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.liftrix.domain.model.admin.*
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.usecase.admin.*
import com.example.liftrix.domain.usecase.auth.AuthQueryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for admin ban management functionality.
 * 
 * Handles all admin operations including user search, banning, unbanning,
 * and viewing ban history. Integrates with Firebase Admin SDK for secure
 * user management operations.
 * 
 * ★ Insight ─────────────────────────────────────
 * - Implements admin-only operations with proper permission validation
 * - Provides comprehensive ban management with audit trail support
 * - Uses Firebase Admin SDK for secure server-side user management
 * ─────────────────────────────────────────────────
 */
@HiltViewModel
class AdminBanViewModel @Inject constructor(
    private val authQueryUseCase: AuthQueryUseCase,
    private val checkAdminPermissionsUseCase: CheckAdminPermissionsUseCase,
    private val searchUsersUseCase: SearchUsersUseCase,
    private val banUserUseCase: BanUserUseCase,
    private val unbanUserUseCase: UnbanUserUseCase,
    private val getUserBanInfoUseCase: GetUserBanInfoUseCase,
    private val listBannedUsersUseCase: ListBannedUsersUseCase,
    private val getAdminLogsUseCase: GetAdminLogsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AdminBanUiState(
            isAdmin = false,
            isLoading = false,
            searchResults = emptyList(),
            bannedUsers = emptyList(),
            adminLogs = emptyList(),
            currentUserBanInfo = null,
            error = null
        )
    )
    val uiState: StateFlow<AdminBanUiState> = _uiState.asStateFlow()

    fun handleEvent(event: AdminBanEvent) {
        when (event) {
            is AdminBanEvent.CheckAdminPermissions -> checkAdminPermissions()
            is AdminBanEvent.SearchUsers -> searchUsers(event.query)
            is AdminBanEvent.BanUser -> banUser(event.userId, event.reason, event.severity, event.banDuration)
            is AdminBanEvent.UnbanUser -> unbanUser(event.userId, event.reason)
            is AdminBanEvent.GetUserBanInfo -> getUserBanInfo(event.userId)
            is AdminBanEvent.LoadBannedUsers -> loadBannedUsers()
            is AdminBanEvent.LoadAdminLogs -> loadAdminLogs()
            is AdminBanEvent.RefreshSearch -> refreshCurrentSearch()
            is AdminBanEvent.ClearError -> clearError()
        }
    }

    /**
     * Check if current user has admin permissions
     */
    fun checkAdminPermissions() {
        viewModelScope.launch {
            val userId = authQueryUseCase(waitForAuth = false).fold(
                onSuccess = { it },
                onFailure = {
                    Timber.e("Authentication failed")
                    return@launch
                }
            )
            
            checkAdminPermissionsUseCase(userId).fold(
                onSuccess = { isAdmin ->
                    _uiState.value = _uiState.value.copy(
                        isAdmin = isAdmin,
                        error = if (!isAdmin) {
                            LiftrixError.BusinessLogicError(
                                code = "ADMIN_ACCESS_DENIED",
                                errorMessage = "You don't have admin permissions to access this feature"
                            )
                        } else null
                    )
                    
                    if (isAdmin) {
                        // Load initial data if user is admin
                        loadBannedUsers()
                    }
                },
                onFailure = { throwable ->
                    _uiState.value = _uiState.value.copy(
                        isAdmin = false,
                        error = throwable as? LiftrixError ?: LiftrixError.BusinessLogicError(
                            code = "CHECK_ADMIN_PERMISSIONS_FAILED",
                            errorMessage = throwable.message ?: "Unknown error"
                        )
                    )
                }
            )
        }
    }

    /**
     * Search for users by email or display name
     */
    private fun searchUsers(query: String) {
        if (query.length < 3) {
            _uiState.value = _uiState.value.copy(
                error = LiftrixError.ValidationError(
                    field = "search_query",
                    violations = listOf("Search query must be at least 3 characters long")
                )
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            searchUsersUseCase(SearchUsersRequest(query = query, limit = 20)).fold(
                onSuccess = { response ->
                    _uiState.value = _uiState.value.copy(
                        searchResults = response.users,
                        isLoading = false
                    )
                    
                    Timber.d("Found ${response.users.size} users for query: $query")
                },
                onFailure = { throwable ->
                    _uiState.value = _uiState.value.copy(
                        error = throwable as? LiftrixError ?: LiftrixError.BusinessLogicError(
                            code = "SEARCH_USERS_FAILED",
                            errorMessage = throwable.message ?: "Unknown error"
                        ),
                        isLoading = false
                    )
                    
                    Timber.e("Error searching users: $throwable")
                }
            )
        }
    }

    /**
     * Ban a user with specified reason and severity
     */
    private fun banUser(userId: String, reason: String, severity: BanSeverity, banDuration: String?) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            val request = BanUserRequest(
                userId = userId,
                reason = reason,
                severity = severity,
                banDuration = banDuration
            )
            
            banUserUseCase(request).fold(
                onSuccess = { response ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        // Update search results to reflect banned status
                        searchResults = _uiState.value.searchResults.map { user ->
                            if (user.uid == userId) {
                                user.copy(currentlyBanned = true)
                            } else user
                        }
                    )
                    
                    Timber.i("Successfully banned user: $userId")
                    
                    // Refresh banned users list
                    loadBannedUsers()
                },
                onFailure = { throwable ->
                    _uiState.value = _uiState.value.copy(
                        error = throwable as? LiftrixError ?: LiftrixError.BusinessLogicError(
                            code = "BAN_USER_FAILED",
                            errorMessage = throwable.message ?: "Unknown error"
                        ),
                        isLoading = false
                    )
                    
                    Timber.e("Error banning user: $throwable")
                }
            )
        }
    }

    /**
     * Unban a user with specified reason
     */
    private fun unbanUser(userId: String, reason: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            val request = UnbanUserRequest(
                userId = userId,
                reason = reason.ifBlank { "Appeal approved" }
            )
            
            unbanUserUseCase(request).fold(
                onSuccess = { response ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        // Update search results to reflect unbanned status
                        searchResults = _uiState.value.searchResults.map { user ->
                            if (user.uid == userId) {
                                user.copy(currentlyBanned = false)
                            } else user
                        },
                        // Remove from banned users list
                        bannedUsers = _uiState.value.bannedUsers.filter { it.userId != userId }
                    )
                    
                    Timber.i("Successfully unbanned user: $userId")
                    
                    // Refresh banned users list
                    loadBannedUsers()
                },
                onFailure = { throwable ->
                    _uiState.value = _uiState.value.copy(
                        error = throwable as? LiftrixError ?: LiftrixError.BusinessLogicError(
                            code = "UNBAN_USER_FAILED",
                            errorMessage = throwable.message ?: "Unknown error"
                        ),
                        isLoading = false
                    )
                    
                    Timber.e("Error unbanning user: $throwable")
                }
            )
        }
    }

    /**
     * Get detailed ban information for a specific user
     */
    private fun getUserBanInfo(userId: String) {
        viewModelScope.launch {
            getUserBanInfoUseCase(userId).fold(
                onSuccess = { response ->
                    _uiState.value = _uiState.value.copy(
                        currentUserBanInfo = response
                    )
                    
                    Timber.d("Retrieved ban info for user: $userId")
                },
                onFailure = { throwable ->
                    _uiState.value = _uiState.value.copy(
                        error = throwable as? LiftrixError ?: LiftrixError.BusinessLogicError(
                            code = "GET_USER_BAN_INFO_FAILED",
                            errorMessage = throwable.message ?: "Unknown error"
                        )
                    )
                    
                    Timber.e("Error getting user ban info: $throwable")
                }
            )
        }
    }

    /**
     * Load list of all currently banned users
     */
    private fun loadBannedUsers() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            val request = ListBannedUsersRequest(
                limit = 100,
                offset = 0,
                severity = null // Load all severities
            )
            
            listBannedUsersUseCase(request).fold(
                onSuccess = { response ->
                    _uiState.value = _uiState.value.copy(
                        bannedUsers = response.bannedUsers,
                        isLoading = false
                    )
                    
                    Timber.d("Loaded ${response.bannedUsers.size} banned users")
                },
                onFailure = { throwable ->
                    _uiState.value = _uiState.value.copy(
                        error = throwable as? LiftrixError ?: LiftrixError.BusinessLogicError(
                            code = "LIST_BANNED_USERS_FAILED",
                            errorMessage = throwable.message ?: "Unknown error"
                        ),
                        isLoading = false
                    )
                    
                    Timber.e("Error loading banned users: $throwable")
                }
            )
        }
    }

    /**
     * Load admin action logs
     */
    private fun loadAdminLogs() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            getAdminLogsUseCase(GetAdminLogsRequest(limit = 100)).fold(
                onSuccess = { response ->
                    _uiState.value = _uiState.value.copy(
                        adminLogs = response,
                        isLoading = false
                    )
                    
                    Timber.d("Loaded ${response.size} admin log entries")
                },
                onFailure = { throwable ->
                    _uiState.value = _uiState.value.copy(
                        error = throwable as? LiftrixError ?: LiftrixError.BusinessLogicError(
                            code = "GET_ADMIN_LOGS_FAILED",
                            errorMessage = throwable.message ?: "Unknown error"
                        ),
                        isLoading = false
                    )
                    
                    Timber.e("Error loading admin logs: $throwable")
                }
            )
        }
    }

    /**
     * Refresh current search results
     */
    private fun refreshCurrentSearch() {
        // This would need to track the last search query
        // For now, just clear results
        _uiState.value = _uiState.value.copy(
            searchResults = emptyList()
        )
    }

    /**
     * Clear current error state
     */
    private fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}

/**
 * UI state for admin ban management screen
 */
@Stable
data class AdminBanUiState(
    val isAdmin: Boolean,
    val isLoading: Boolean,
    val searchResults: List<AdminUserInfo>,
    val bannedUsers: List<AdminBanInfo>,
    val adminLogs: List<AdminBanInfo>,
    val currentUserBanInfo: UserBanInfoResponse?,
    val error: LiftrixError?
)

/**
 * Events that can be triggered in the admin ban management screen
 */
sealed class AdminBanEvent {
    object CheckAdminPermissions : AdminBanEvent()
    data class SearchUsers(val query: String) : AdminBanEvent()
    data class BanUser(
        val userId: String,
        val reason: String,
        val severity: BanSeverity,
        val banDuration: String?
    ) : AdminBanEvent()
    data class UnbanUser(val userId: String, val reason: String) : AdminBanEvent()
    data class GetUserBanInfo(val userId: String) : AdminBanEvent()
    object LoadBannedUsers : AdminBanEvent()
    object LoadAdminLogs : AdminBanEvent()
    object RefreshSearch : AdminBanEvent()
    object ClearError : AdminBanEvent()
}