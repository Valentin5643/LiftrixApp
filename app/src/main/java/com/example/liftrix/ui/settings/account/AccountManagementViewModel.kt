package com.example.liftrix.ui.settings.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.service.AnalyticsService
import com.example.liftrix.domain.usecase.account.AccountQueryUseCase
import com.example.liftrix.domain.usecase.account.AccountCommandUseCase
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.repository.UserAccountRepository
import com.example.liftrix.domain.usecase.auth.AuthQueryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for account management operations following MVI pattern.
 * Handles email, password, username updates and account deletion.
 * Part of SPEC-20250116-account-management implementation.
 */
@HiltViewModel
class AccountManagementViewModel @Inject constructor(
    private val accountCommandUseCase: AccountCommandUseCase,
    private val accountQueryUseCase: AccountQueryUseCase,
    private val authQueryUseCase: AuthQueryUseCase,
    private val authRepository: AuthRepository,
    private val userAccountRepository: UserAccountRepository,
    private val analyticsService: AnalyticsService
) : ViewModel() {

    private val _uiState = MutableStateFlow(AccountManagementUiState())
    val uiState: StateFlow<AccountManagementUiState> = _uiState.asStateFlow()

    private var currentUserId: String? = null

    init {
        loadAccountInfo()
        observeAuthenticationState()
        trackScreenViewed()
    }

    /**
     * Handles all events from the UI layer
     */
    fun onEvent(event: AccountManagementEvent) {
        when (event) {
            is AccountManagementEvent.LoadAccountInfo -> loadAccountInfo()
            is AccountManagementEvent.UpdateEmail -> updateEmail(event.newEmail, event.currentPassword)
            is AccountManagementEvent.UpdatePassword -> updatePassword(event.currentPassword, event.newPassword)
            is AccountManagementEvent.UpdateUsername -> updateUsername(event.newUsername)
            is AccountManagementEvent.DeleteAccount -> initiateAccountDeletion(event.currentPassword)
            is AccountManagementEvent.ConfirmAccountDeletion -> confirmAccountDeletion()
            is AccountManagementEvent.CancelAccountDeletion -> cancelAccountDeletion()
            is AccountManagementEvent.DismissError -> dismissError()
            is AccountManagementEvent.DismissSuccess -> dismissSuccess()
            is AccountManagementEvent.CheckUsernameAvailability -> checkUsernameAvailability(event.username)
            is AccountManagementEvent.ValidateEmail -> validateEmail(event.email)
            is AccountManagementEvent.ValidatePassword -> validatePassword(event.password)
        }
    }

    /**
     * Loads account information from repository
     */
    private fun loadAccountInfo() {
        viewModelScope.launch {
            updateState { copy(isLoading = true, error = null) }

            try {
                val userId = authQueryUseCase(waitForAuth = false).fold(
                    onSuccess = { it.value },
                    onFailure = { throw LiftrixError.AuthenticationError(
                        errorMessage = "User not authenticated",
                        errorCode = "NO_USER"
                    ) }
                )

                currentUserId = userId

                val result = accountQueryUseCase()

                result.fold(
                    onSuccess = { accountInfo ->
                        updateState {
                            copy(
                                isLoading = false,
                                accountInfo = accountInfo,
                                error = null
                            )
                        }
                    },
                    onFailure = { throwable ->
                        Timber.e("Account info loading failed: $throwable")
                        val liftrixError = if (throwable is LiftrixError) {
                            throwable
                        } else {
                            LiftrixError.UnknownError(
                                errorMessage = "Failed to load account information: ${throwable.message}"
                            )
                        }
                        updateState {
                            copy(
                                isLoading = false,
                                error = getErrorMessage(liftrixError, "Failed to load account information")
                            )
                        }
                    }
                )
            } catch (exception: Exception) {
                Timber.e(exception, "Exception loading account info")
                updateState {
                    copy(
                        isLoading = false,
                        error = "Authentication error: ${exception.message}"
                    )
                }
            }
        }
    }

    /**
     * Updates user email address with reauthentication
     */
    private fun updateEmail(newEmail: String, currentPassword: String) {
        viewModelScope.launch {
            updateState { copy(isUpdatingEmail = true, error = null) }
            
            try {
                val result = accountCommandUseCase.updateEmail(newEmail, currentPassword)
                
                result.fold(
                    onSuccess = {
                        Timber.d("Email updated successfully")
                        trackAccountAction("email_updated")
                        updateState {
                            copy(
                                isUpdatingEmail = false,
                                successMessage = "Email updated successfully! Please check your new email for verification.",
                                error = null
                            )
                        }
                        // Reload account info to reflect changes
                        loadAccountInfo()
                    },
                    onFailure = { throwable ->
                        Timber.e("Email update failed: $throwable")
                        trackAccountAction("email_update_failed")
                        val liftrixError = if (throwable is LiftrixError) {
                            throwable
                        } else {
                            LiftrixError.UnknownError(
                                errorMessage = "Failed to update email: ${throwable.message}"
                            )
                        }
                        updateState {
                            copy(
                                isUpdatingEmail = false,
                                error = getErrorMessage(liftrixError, "Failed to update email")
                            )
                        }
                    }
                )
            } catch (exception: Exception) {
                Timber.e(exception, "Exception updating email")
                updateState {
                    copy(
                        isUpdatingEmail = false,
                        error = "Failed to update email: ${exception.message}"
                    )
                }
            }
        }
    }

    /**
     * Updates user password with current password verification
     */
    private fun updatePassword(currentPassword: String, newPassword: String) {
        viewModelScope.launch {
            updateState { copy(isUpdatingPassword = true, error = null) }
            
            try {
                val result = accountCommandUseCase.updatePassword(currentPassword, newPassword)
                
                result.fold(
                    onSuccess = {
                        Timber.d("Password updated successfully")
                        trackAccountAction("password_updated")
                        updateState {
                            copy(
                                isUpdatingPassword = false,
                                successMessage = "Password updated successfully!",
                                error = null
                            )
                        }
                    },
                    onFailure = { throwable ->
                        Timber.e("Password update failed: $throwable")
                        trackAccountAction("password_update_failed")
                        val liftrixError = if (throwable is LiftrixError) {
                            throwable
                        } else {
                            LiftrixError.UnknownError(
                                errorMessage = "Failed to update password: ${throwable.message}"
                            )
                        }
                        updateState {
                            copy(
                                isUpdatingPassword = false,
                                error = getErrorMessage(liftrixError, "Failed to update password")
                            )
                        }
                    }
                )
            } catch (exception: Exception) {
                Timber.e(exception, "Exception updating password")
                updateState {
                    copy(
                        isUpdatingPassword = false,
                        error = "Failed to update password: ${exception.message}"
                    )
                }
            }
        }
    }

    /**
     * Updates username with availability check
     */
    private fun updateUsername(newUsername: String) {
        viewModelScope.launch {
            updateState { copy(isUpdatingUsername = true, error = null) }
            
            try {
                val userId = currentUserId
                    ?: throw LiftrixError.AuthenticationError(
                        errorMessage = "User not authenticated",
                        errorCode = "NO_USER"
                    )
                
                val result = accountCommandUseCase.updateUsername(newUsername)
                
                result.fold(
                    onSuccess = {
                        Timber.d("Username updated successfully")
                        trackAccountAction("username_updated")
                        updateState {
                            copy(
                                isUpdatingUsername = false,
                                successMessage = "Username updated successfully!",
                                error = null
                            )
                        }
                        // Reload account info to reflect changes
                        loadAccountInfo()
                    },
                    onFailure = { throwable ->
                        Timber.e("Username update failed: $throwable")
                        trackAccountAction("username_update_failed")
                        val liftrixError = if (throwable is LiftrixError) {
                            throwable
                        } else {
                            LiftrixError.UnknownError(
                                errorMessage = "Failed to update username: ${throwable.message}"
                            )
                        }
                        updateState {
                            copy(
                                isUpdatingUsername = false,
                                error = getErrorMessage(liftrixError, "Failed to update username")
                            )
                        }
                    }
                )
            } catch (exception: Exception) {
                Timber.e(exception, "Exception updating username")
                updateState {
                    copy(
                        isUpdatingUsername = false,
                        error = "Failed to update username: ${exception.message}"
                    )
                }
            }
        }
    }

    /**
     * Initiates account deletion process with confirmation dialog
     */
    private fun initiateAccountDeletion(currentPassword: String) {
        updateState { 
            copy(
                showDeleteConfirmation = true,
                pendingDeletionPassword = currentPassword,
                error = null
            )
        }
        trackAccountAction("delete_account_initiated")
    }

    /**
     * Confirms and executes account deletion
     */
    private fun confirmAccountDeletion() {
        viewModelScope.launch {
            updateState { 
                copy(
                    isDeletingAccount = true,
                    showDeleteConfirmation = false,
                    error = null
                )
            }
            
            try {
                val password = _uiState.value.pendingDeletionPassword
                if (password.isBlank()) {
                    throw LiftrixError.ValidationError(
                        field = "password",
                        violations = listOf("Password required for account deletion")
                    )
                }
                
                val result = accountCommandUseCase.deleteAccount(password)
                
                result.fold(
                    onSuccess = {
                        Timber.d("Account deleted successfully")
                        trackAccountAction("account_deleted")
                        updateState {
                            copy(
                                isDeletingAccount = false,
                                accountDeleted = true,
                                successMessage = "Account deleted successfully",
                                error = null
                            )
                        }
                    },
                    onFailure = { throwable ->
                        Timber.e("Account deletion failed: $throwable")
                        trackAccountAction("account_deletion_failed")
                        val liftrixError = if (throwable is LiftrixError) {
                            throwable
                        } else {
                            LiftrixError.UnknownError(
                                errorMessage = "Failed to delete account: ${throwable.message}"
                            )
                        }
                        updateState {
                            copy(
                                isDeletingAccount = false,
                                error = getErrorMessage(liftrixError, "Failed to delete account")
                            )
                        }
                    }
                )
            } catch (exception: Exception) {
                Timber.e(exception, "Exception deleting account")
                updateState {
                    copy(
                        isDeletingAccount = false,
                        error = "Failed to delete account: ${exception.message}"
                    )
                }
            }
        }
    }

    /**
     * Cancels account deletion process
     */
    private fun cancelAccountDeletion() {
        updateState { 
            copy(
                showDeleteConfirmation = false,
                pendingDeletionPassword = "",
                error = null
            )
        }
        trackAccountAction("delete_account_cancelled")
    }

    /**
     * Checks username availability in real-time
     */
    private fun checkUsernameAvailability(username: String) {
        viewModelScope.launch {
            try {
                // Basic validation first
                if (username.length < 3 || username.length > 20) {
                    updateState { 
                        copy(usernameValidation = UsernameValidation.Invalid("Username must be 3-20 characters"))
                    }
                    return@launch
                }
                
                if (!username.matches(Regex("^[a-zA-Z0-9_]+$"))) {
                    updateState { 
                        copy(usernameValidation = UsernameValidation.Invalid("Username can only contain letters, numbers, and underscores"))
                    }
                    return@launch
                }
                
                updateState { copy(usernameValidation = UsernameValidation.Checking) }
                
                // Debounce to avoid excessive API calls
                kotlinx.coroutines.delay(500)
                
                // Check availability through repository
                val availabilityResult = userAccountRepository.checkUsernameAvailability(username)
                
                availabilityResult.fold(
                    onSuccess = { isAvailable ->
                        updateState {
                            copy(
                                usernameValidation = if (isAvailable) {
                                    UsernameValidation.Available
                                } else {
                                    UsernameValidation.Invalid("Username is already taken")
                                }
                            )
                        }
                    },
                    onFailure = { error ->
                        Timber.e("Failed to check username availability: $error")
                        updateState {
                            copy(
                                usernameValidation = UsernameValidation.Invalid("Failed to check availability")
                            )
                        }
                    }
                )
            } catch (exception: Exception) {
                Timber.e(exception, "Error checking username availability")
                updateState { 
                    copy(usernameValidation = UsernameValidation.Invalid("Error checking availability"))
                }
            }
        }
    }

    /**
     * Validates email format in real-time
     */
    private fun validateEmail(email: String) {
        val isValid = android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
        updateState {
            copy(
                emailValidation = if (email.isBlank()) {
                    EmailValidation.None
                } else if (isValid) {
                    EmailValidation.Valid
                } else {
                    EmailValidation.Invalid("Invalid email format")
                }
            )
        }
    }

    /**
     * Validates password strength in real-time
     */
    private fun validatePassword(password: String) {
        val validation = when {
            password.isBlank() -> PasswordValidation.None
            password.length < 6 -> PasswordValidation.Weak("Password must be at least 6 characters")
            password.length < 8 -> PasswordValidation.Fair("Consider using 8+ characters for better security")
            password.matches(Regex("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$")) -> PasswordValidation.Strong
            else -> PasswordValidation.Good
        }
        
        updateState { copy(passwordValidation = validation) }
    }

    /**
     * Dismisses current error message
     */
    private fun dismissError() {
        updateState { copy(error = null) }
    }

    /**
     * Dismisses current success message
     */
    private fun dismissSuccess() {
        updateState { copy(successMessage = null) }
    }

    /**
     * Observes authentication state changes
     */
    private fun observeAuthenticationState() {
        viewModelScope.launch {
            try {
                // Observe authentication state changes from the repository
                authRepository.currentUser.collectLatest { user ->
                    if (user == null) {
                        // User has been logged out, clear state
                        Timber.d("User logged out - clearing account management state")
                        updateState { 
                            AccountManagementUiState() // Reset to initial state
                        }
                        currentUserId = null
                    } else {
                        // User state changed, reload account info if user ID changed
                        if (currentUserId != user.uid) {
                            Timber.d("User changed from $currentUserId to ${user.uid} - reloading account info")
                            currentUserId = user.uid
                            loadAccountInfo()
                        }
                    }
                }
            } catch (exception: Exception) {
                Timber.e(exception, "Error observing authentication state")
            }
        }
    }

    /**
     * Updates UI state safely
     */
    private fun updateState(update: AccountManagementUiState.() -> AccountManagementUiState) {
        val currentState = _uiState.value
        val newState = currentState.update()
        
        if (currentState != newState) {
            _uiState.value = newState
        }
    }

    /**
     * Extracts user-friendly error message from LiftrixError
     */
    private fun getErrorMessage(error: LiftrixError, fallback: String): String {
        return when (error) {
            is LiftrixError.ValidationError -> {
                error.violations.firstOrNull() ?: fallback
            }
            is LiftrixError.AuthenticationError -> {
                error.errorMessage
            }
            is LiftrixError.BusinessLogicError -> {
                error.errorMessage
            }
            is LiftrixError.NetworkError -> {
                error.errorMessage
            }
            is LiftrixError.DatabaseError -> {
                error.errorMessage
            }
            is LiftrixError.UnknownError -> {
                error.errorMessage
            }
            is LiftrixError.CalculationError -> {
                error.errorMessage
            }
            is LiftrixError.DataRetrievalError -> {
                error.errorMessage
            }
            is LiftrixError.ConfigurationError -> {
                error.errorMessage
            }
            is LiftrixError.ExportError -> {
                error.errorMessage
            }
            is LiftrixError.FileSystemError -> {
                error.errorMessage
            }
            is LiftrixError.NotFoundError -> {
                error.errorMessage
            }
            is LiftrixError.PermissionError -> {
                error.errorMessage
            }
            is LiftrixError.CacheError -> {
                error.errorMessage
            }
        }
    }

    // Analytics tracking methods
    private fun trackScreenViewed() {
        viewModelScope.launch {
            try {
                analyticsService.logEvent(
                    "account_management_screen_viewed",
                    mapOf("timestamp" to System.currentTimeMillis())
                )
            } catch (exception: Exception) {
                Timber.w(exception, "Failed to track screen viewed")
            }
        }
    }

    private fun trackAccountAction(action: String) {
        viewModelScope.launch {
            try {
                analyticsService.logEvent(
                    "account_management_action",
                    mapOf(
                        "action" to action,
                        "timestamp" to System.currentTimeMillis()
                    )
                )
            } catch (exception: Exception) {
                Timber.w(exception, "Failed to track account action")
            }
        }
    }
}

/**
 * UI State for account management screen
 */
data class AccountManagementUiState(
    val isLoading: Boolean = false,
    val accountInfo: com.example.liftrix.domain.model.UserAccount? = null,
    val error: String? = null,
    val successMessage: String? = null,
    
    // Email update state
    val isUpdatingEmail: Boolean = false,
    val emailValidation: EmailValidation = EmailValidation.None,
    
    // Password update state
    val isUpdatingPassword: Boolean = false,
    val passwordValidation: PasswordValidation = PasswordValidation.None,
    
    // Username update state
    val isUpdatingUsername: Boolean = false,
    val usernameValidation: UsernameValidation = UsernameValidation.None,
    
    // Account deletion state
    val isDeletingAccount: Boolean = false,
    val showDeleteConfirmation: Boolean = false,
    val pendingDeletionPassword: String = "",
    val accountDeleted: Boolean = false
) {
    val shouldShowContent: Boolean
        get() = !isLoading && !accountDeleted
    
    val shouldShowError: Boolean
        get() = error != null
    
    val shouldShowSuccess: Boolean
        get() = successMessage != null
    
    val isAnyOperationInProgress: Boolean
        get() = isUpdatingEmail || isUpdatingPassword || isUpdatingUsername || isDeletingAccount
}

/**
 * Events for account management
 */
sealed class AccountManagementEvent {
    data object LoadAccountInfo : AccountManagementEvent()
    data class UpdateEmail(val newEmail: String, val currentPassword: String) : AccountManagementEvent()
    data class UpdatePassword(val currentPassword: String, val newPassword: String) : AccountManagementEvent()
    data class UpdateUsername(val newUsername: String) : AccountManagementEvent()
    data class DeleteAccount(val currentPassword: String) : AccountManagementEvent()
    data object ConfirmAccountDeletion : AccountManagementEvent()
    data object CancelAccountDeletion : AccountManagementEvent()
    data object DismissError : AccountManagementEvent()
    data object DismissSuccess : AccountManagementEvent()
    data class CheckUsernameAvailability(val username: String) : AccountManagementEvent()
    data class ValidateEmail(val email: String) : AccountManagementEvent()
    data class ValidatePassword(val password: String) : AccountManagementEvent()
}

/**
 * Email validation states
 */
sealed class EmailValidation {
    data object None : EmailValidation()
    data object Valid : EmailValidation()
    data class Invalid(val message: String) : EmailValidation()
}

/**
 * Password validation states
 */
sealed class PasswordValidation {
    data object None : PasswordValidation()
    data class Weak(val message: String) : PasswordValidation()
    data class Fair(val message: String) : PasswordValidation()
    data object Good : PasswordValidation()
    data object Strong : PasswordValidation()
}

/**
 * Username validation states
 */
sealed class UsernameValidation {
    data object None : UsernameValidation()
    data object Checking : UsernameValidation()
    data object Available : UsernameValidation()
    data class Invalid(val message: String) : UsernameValidation()
}