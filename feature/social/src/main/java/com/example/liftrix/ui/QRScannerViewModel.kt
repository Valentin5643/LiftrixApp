package com.example.liftrix.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.repository.UserSearchRepository
import com.example.liftrix.domain.repository.social.GymBuddyRepository
import com.example.liftrix.domain.qr.GymBuddyQrCodec
import com.example.liftrix.domain.qr.GymBuddyQrParseResult
import com.example.liftrix.domain.service.AnalyticsService
import com.example.liftrix.domain.usecase.template.TemplateQueryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

/**
 * ViewModel for QR scanner screen state management and QR code processing.
 * 
 * Manages scanner UI state including camera status, QR code validation,
 * gym buddy pairing, loading states, and error handling. Uses the shared QR
 * protocol codec for validation and GymBuddyRepository for pairing.
 */
@HiltViewModel
class QRScannerViewModel @Inject constructor(
    private val gymBuddyRepository: GymBuddyRepository,
    private val authRepository: AuthRepository,
    private val userSearchRepository: UserSearchRepository,
    private val templateQueryUseCase: TemplateQueryUseCase,
    private val analyticsService: AnalyticsService
) : ViewModel() {

    private val _uiState = MutableStateFlow(QRScannerUiState())
    val uiState: StateFlow<QRScannerUiState> = _uiState.asStateFlow()

    private var currentUserId: String? = null
    private val scanInFlight = AtomicBoolean(false)

    init {
        observeAuthState()
        trackScannerOpened()
    }

    /**
     * Handles UI events from the QR scanner screen
     */
    fun handleEvent(event: QRScannerEvent) {
        when (event) {
            is QRScannerEvent.InitializeScanner -> {
                initializeScanner()
            }
            is QRScannerEvent.PermissionGranted -> {
                updateState { copy(hasCameraPermission = true, error = null) }
                trackCameraPermissionGranted()
            }
            is QRScannerEvent.PermissionDenied -> {
                updateState { 
                    copy(
                        hasCameraPermission = false,
                        error = "Camera permission is required to scan QR codes"
                    ) 
                }
                trackCameraPermissionDenied()
            }
            is QRScannerEvent.CodeScanned -> {
                processScannedCode(event.qrCode)
            }
            is QRScannerEvent.RetryScanning -> {
                retryScanning()
            }
            is QRScannerEvent.ScannerClosed -> {
                trackScannerClosed()
            }
        }
    }

    /**
     * Initializes the QR scanner
     */
    private fun initializeScanner() {
        updateState { copy(isInitializing = true, error = null) }
        
        viewModelScope.launch {
            try {
                // Initialize scanner components
                updateState { 
                    copy(
                        isInitializing = false,
                        isReady = true,
                        error = null
                    ) 
                }
            } catch (exception: Exception) {
                Timber.e(exception, "Error initializing scanner")
                updateState { 
                    copy(
                        isInitializing = false,
                        isReady = false,
                        error = "Failed to initialize scanner: ${exception.message}"
                    ) 
                }
            }
        }
    }

    /**
     * Processes a scanned QR code
     */
    private fun processScannedCode(qrCode: String) {
        if (uiState.value.connectionSuccess || !scanInFlight.compareAndSet(false, true)) return
        
        viewModelScope.launch {
            try {
                updateState { copy(isProcessing = true, error = null) }

                // Check if current user is available
                val scannerUserId = currentUserId ?: authRepository.getCurrentUserId()?.value.also {
                    currentUserId = it
                }

                if (scannerUserId == null) {
                    updateState { 
                        copy(
                            isProcessing = false,
                            error = "User not authenticated. Please log in first."
                        ) 
                    }
                    return@launch
                }

                when (val parseResult = GymBuddyQrCodec.parse(qrCode)) {
                    is GymBuddyQrParseResult.Valid -> connectGymBuddy(
                        currentUserId = scannerUserId,
                        buddyUserId = parseResult.payload.userId,
                        expiresAt = parseResult.payload.expiresAt,
                        qrCode = qrCode,
                        verifyProfile = true
                    )
                    else -> {
                        Timber.w(
                            "Gym Buddy QR rejected during parsing: ${parseResult.javaClass.simpleName}"
                        )
                        updateState {
                            copy(
                                isProcessing = false,
                                error = parseResult.toUserFacingMessage()
                            )
                        }
                        trackInvalidQrCode(qrCode)
                    }
                }

            } catch (exception: Exception) {
                Timber.e(exception, "Error processing QR code")
                updateState { 
                    copy(
                        isProcessing = false,
                        error = "Failed to process QR code: ${exception.message}"
                    ) 
                }
                trackQrProcessingError(qrCode, exception)
            } finally {
                scanInFlight.set(false)
            }
        }
    }

    /**
     * Retries scanning after an error
     */
    private fun retryScanning() {
        updateState { 
            copy(
                error = null,
                isProcessing = false,
                scannedCode = null,
                connectionSuccess = false,
                connectedBuddyId = null,
                successMessage = null,
                pendingTemplateShareId = null,
                pendingTemplateShareSenderId = null,
                pendingTemplateShareCount = 0
            ) 
        }
        trackRetryScanning()
    }

    /**
     * Observes authentication state
     */
    private fun observeAuthState() {
        viewModelScope.launch {
            authRepository.currentUser
                .catch { throwable ->
                    Timber.e(throwable, "Error observing auth state in scanner")
                    updateState { copy(error = "Authentication error") }
                }
                .collect { user ->
                    currentUserId = user?.uid
                }
        }
    }

    /**
     * Updates the UI state using the provided transform function
     */
    private fun updateState(transform: QRScannerUiState.() -> QRScannerUiState) {
        _uiState.value = _uiState.value.transform()
    }

    private fun GymBuddyQrParseResult.toUserFacingMessage(): String = when (this) {
        is GymBuddyQrParseResult.UnsupportedVersion ->
            "This QR code uses a newer Liftrix format (v$version). Update Liftrix to scan it."
        GymBuddyQrParseResult.Expired ->
            "This gym buddy QR code has expired. Ask them to show a new code."
        GymBuddyQrParseResult.InsecureLegacy ->
            "This QR code was created by an older Liftrix version and is missing required security data. Ask them to update Liftrix and generate a new code."
        GymBuddyQrParseResult.NotGymBuddyCode ->
            "This is not a Liftrix gym buddy QR code."
        GymBuddyQrParseResult.Invalid ->
            "This gym buddy QR code is invalid, incomplete, or has been changed. Ask them to generate a new code."
        is GymBuddyQrParseResult.Valid -> error("Valid QR payloads do not have an error message")
    }

    private suspend fun connectGymBuddy(
        currentUserId: String,
        buddyUserId: String,
        expiresAt: Long,
        qrCode: String,
        verifyProfile: Boolean
    ) {
        val profileExists = if (verifyProfile) {
            val verificationResult = userSearchRepository.verifyProfileForPairing(buddyUserId)
            val verificationError = verificationResult.exceptionOrNull()
            if (verificationError != null) {
                Timber.w(verificationError, "Could not verify scanned user profile")
                updateState {
                    copy(
                        isProcessing = false,
                        error = toUserFacingProfileVerificationError(verificationError)
                    )
                }
                trackQrProcessingError(qrCode, verificationError)
                return
            }
            verificationResult.getOrThrow()
        } else {
            true
        }

        if (!profileExists) {
            updateState {
                copy(
                    isProcessing = false,
                    error = "This QR code does not match an active Liftrix user."
                )
            }
            trackInvalidQrCode(qrCode)
            return
        }

        if (System.currentTimeMillis() >= expiresAt) {
            updateState {
                copy(
                    isProcessing = false,
                    error = "This gym buddy QR code has expired. Ask them to show a new code."
                )
            }
            trackInvalidQrCode(qrCode)
            return
        }

        if (authRepository.getCurrentUserId()?.value != currentUserId) {
            updateState {
                copy(
                    isProcessing = false,
                    error = "Your session changed while the code was being verified. Please sign in and scan again."
                )
            }
            return
        }

        val alreadyConnectedResult = gymBuddyRepository.areMutualGymBuddies(
            userId = currentUserId,
            buddyId = buddyUserId
        )
        val alreadyConnected = alreadyConnectedResult.getOrElse { false }

        if (alreadyConnected) {
            if (resolvePendingTemplateShare(currentUserId, buddyUserId, qrCode)) {
                return
            }

            updateState {
                copy(
                    isProcessing = false,
                    scannedCode = qrCode,
                    connectedBuddyId = buddyUserId,
                    connectionSuccess = true,
                    successMessage = "You are already gym buddies."
                )
            }
            trackValidQrCodeScanned(qrCode)
            return
        }

        val connectionResult = gymBuddyRepository.createMutualConnection(
            userId1 = currentUserId,
            userId2 = buddyUserId,
            viaQr = true
        )

        connectionResult.fold(
            onSuccess = {
                updateState {
                    copy(
                        isProcessing = false,
                        scannedCode = qrCode,
                        connectedBuddyId = buddyUserId,
                        connectionSuccess = true,
                        successMessage = "Gym buddy connected."
                    )
                }
                trackValidQrCodeScanned(qrCode)
            },
            onFailure = { throwable ->
                Timber.e(throwable, "Failed to create gym buddy connection")
                updateState {
                    copy(
                        isProcessing = false,
                        error = toUserFacingConnectionError(throwable)
                    )
                }
                trackQrProcessingError(qrCode, throwable)
            }
        )
    }

    private fun toUserFacingProfileVerificationError(throwable: Throwable): String {
        val message = throwable.message.orEmpty()
        return when {
            message.contains("UNAUTHENTICATED", ignoreCase = true) ||
                message.contains("PERMISSION_DENIED", ignoreCase = true) ||
                message.contains("authentication", ignoreCase = true) ->
                "Your session could not be verified. Please sign in again and retry."
            else ->
                "Could not verify this Liftrix user right now. Check your connection and try again."
        }
    }

    private suspend fun resolvePendingTemplateShare(
        currentUserId: String,
        buddyUserId: String,
        qrCode: String
    ): Boolean {
        val shares = templateQueryUseCase.getPendingSharesFromBuddy(
            senderId = buddyUserId,
            receiverId = currentUserId
        ).getOrElse { throwable ->
            Timber.w(throwable, "No pending template shares resolved after gym buddy scan")
            emptyList()
        }

        if (shares.isEmpty()) return false

        updateState {
            copy(
                isProcessing = false,
                scannedCode = qrCode,
                connectedBuddyId = buddyUserId,
                connectionSuccess = false,
                successMessage = "Workout shared with you.",
                pendingTemplateShareId = if (shares.size == 1) shares.first().id else null,
                pendingTemplateShareSenderId = buddyUserId,
                pendingTemplateShareCount = shares.size
            )
        }
        trackValidQrCodeScanned(qrCode)
        return true
    }

    private fun toUserFacingConnectionError(throwable: Throwable): String {
        val message = throwable.message.orEmpty()
        return when {
            message.contains("ALREADY_CONNECTED", ignoreCase = true) ||
                message.contains("already gym buddies", ignoreCase = true) ->
                "You are already gym buddies."
            message.contains("BUDDY_LIMIT_EXCEEDED", ignoreCase = true) ||
                message.contains("maximum gym buddies", ignoreCase = true) ->
                "One of you has reached the 5 gym buddy limit."
            else -> "Could not connect gym buddies. Please try again."
        }
    }

    // Analytics tracking methods

    /**
     * Tracks scanner opened event
     */
    private fun trackScannerOpened() {
        viewModelScope.launch {
            try {
                currentUserId?.let { userId ->
                    analyticsService.logSocialFeedEvent(
                        userId = userId,
                        eventType = "qr_scanner_opened",
                        additionalData = mapOf<String, Any>(
                            "timestamp" to System.currentTimeMillis()
                        )
                    )
                }
            } catch (exception: Exception) {
                Timber.w(exception, "Failed to track scanner opened")
            }
        }
    }

    /**
     * Tracks scanner closed event
     */
    private fun trackScannerClosed() {
        viewModelScope.launch {
            try {
                currentUserId?.let { userId ->
                    analyticsService.logSocialFeedEvent(
                        userId = userId,
                        eventType = "qr_scanner_closed",
                        additionalData = mapOf<String, Any>(
                            "timestamp" to System.currentTimeMillis(),
                            "successful_scan" to (uiState.value.scannedCode != null)
                        )
                    )
                }
            } catch (exception: Exception) {
                Timber.w(exception, "Failed to track scanner closed")
            }
        }
    }

    /**
     * Tracks camera permission granted event
     */
    private fun trackCameraPermissionGranted() {
        viewModelScope.launch {
            try {
                currentUserId?.let { userId ->
                    analyticsService.logSocialFeedEvent(
                        userId = userId,
                        eventType = "camera_permission_granted",
                        additionalData = mapOf<String, Any>(
                            "context" to "qr_scanner",
                            "timestamp" to System.currentTimeMillis()
                        )
                    )
                }
            } catch (exception: Exception) {
                Timber.w(exception, "Failed to track camera permission granted")
            }
        }
    }

    /**
     * Tracks camera permission denied event
     */
    private fun trackCameraPermissionDenied() {
        viewModelScope.launch {
            try {
                currentUserId?.let { userId ->
                    analyticsService.logSocialFeedEvent(
                        userId = userId,
                        eventType = "camera_permission_denied",
                        additionalData = mapOf<String, Any>(
                            "context" to "qr_scanner",
                            "timestamp" to System.currentTimeMillis()
                        )
                    )
                }
            } catch (exception: Exception) {
                Timber.w(exception, "Failed to track camera permission denied")
            }
        }
    }

    /**
     * Tracks valid QR code scanned event
     */
    private fun trackValidQrCodeScanned(qrCode: String) {
        viewModelScope.launch {
            try {
                currentUserId?.let { userId ->
                    analyticsService.logSocialFeedEvent(
                        userId = userId,
                        eventType = "qr_code_scanned_success",
                        additionalData = mapOf<String, Any>(
                            "qr_format" to when {
                                qrCode.startsWith("liftrix://gym-buddy?") -> "gym_buddy_url"
                                else -> "other"
                            },
                            "qr_length" to qrCode.length,
                            "timestamp" to System.currentTimeMillis()
                        )
                    )
                }
            } catch (exception: Exception) {
                Timber.w(exception, "Failed to track valid QR code scanned")
            }
        }
    }

    /**
     * Tracks invalid QR code event
     */
    private fun trackInvalidQrCode(qrCode: String) {
        viewModelScope.launch {
            try {
                currentUserId?.let { userId ->
                    analyticsService.logSocialFeedEvent(
                        userId = userId,
                        eventType = "qr_code_invalid",
                        additionalData = mapOf<String, Any>(
                            "qr_length" to qrCode.length,
                            "qr_prefix" to qrCode.take(10),
                            "timestamp" to System.currentTimeMillis()
                        )
                    )
                }
            } catch (exception: Exception) {
                Timber.w(exception, "Failed to track invalid QR code")
            }
        }
    }

    /**
     * Tracks QR processing error event
     */
    private fun trackQrProcessingError(qrCode: String, error: Throwable) {
        viewModelScope.launch {
            try {
                currentUserId?.let { userId ->
                    analyticsService.logSocialFeedEvent(
                        userId = userId,
                        eventType = "qr_processing_error",
                        additionalData = mapOf<String, Any>(
                            "error_message" to (error.message ?: "Unknown error") as Any,
                            "error_type" to error.javaClass.simpleName,
                            "qr_length" to qrCode.length,
                            "timestamp" to System.currentTimeMillis()
                        )
                    )
                }
            } catch (exception: Exception) {
                Timber.w(exception, "Failed to track QR processing error")
            }
        }
    }

    /**
     * Tracks retry scanning event
     */
    private fun trackRetryScanning() {
        viewModelScope.launch {
            try {
                currentUserId?.let { userId ->
                    analyticsService.logSocialFeedEvent(
                        userId = userId,
                        eventType = "qr_scan_retry",
                        additionalData = mapOf<String, Any>(
                            "timestamp" to System.currentTimeMillis()
                        )
                    )
                }
            } catch (exception: Exception) {
                Timber.w(exception, "Failed to track retry scanning")
            }
        }
    }

}

/**
 * UI state for the QR scanner screen
 */
data class QRScannerUiState(
    val isInitializing: Boolean = false,
    val isReady: Boolean = false,
    val hasCameraPermission: Boolean = false,
    val isProcessing: Boolean = false,
    val scannedCode: String? = null,
    val connectedBuddyId: String? = null,
    val connectionSuccess: Boolean = false,
    val successMessage: String? = null,
    val pendingTemplateShareId: String? = null,
    val pendingTemplateShareSenderId: String? = null,
    val pendingTemplateShareCount: Int = 0,
    val error: String? = null
)

/**
 * Events that can be triggered from the QR scanner screen UI
 */
sealed class QRScannerEvent {
    object InitializeScanner : QRScannerEvent()
    object PermissionGranted : QRScannerEvent()
    object PermissionDenied : QRScannerEvent()
    data class CodeScanned(val qrCode: String) : QRScannerEvent()
    object RetryScanning : QRScannerEvent()
    object ScannerClosed : QRScannerEvent()
}
