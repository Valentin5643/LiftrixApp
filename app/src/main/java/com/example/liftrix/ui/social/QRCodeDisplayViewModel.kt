package com.example.liftrix.ui.social

import android.graphics.Bitmap
import android.graphics.Color
import androidx.lifecycle.viewModelScope
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.usecase.common.ErrorHandler
import com.example.liftrix.domain.usecase.social.QRCodeGenerationUseCase
import com.example.liftrix.domain.usecase.social.QRCodeGenerationRequest
import com.example.liftrix.ui.common.event.ViewModelEvent
import com.example.liftrix.ui.common.state.UiState
import com.example.liftrix.ui.common.viewmodel.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for QR code display and sharing functionality
 * 
 * Manages QR code generation, sharing, and saving operations with proper
 * error handling and loading states for optimal user experience.
 */
@HiltViewModel
class QRCodeDisplayViewModel @Inject constructor(
    private val qrCodeGenerationUseCase: QRCodeGenerationUseCase,
    errorHandler: ErrorHandler
) : BaseViewModel<QRCodeDisplayUiState, QRCodeDisplayEvent>(errorHandler) {

    override val _uiState = MutableStateFlow(
        QRCodeDisplayUiState(
            qrCodeBitmap = null,
            profileUrl = null,
            isLoading = false,
            error = null,
            currentUserId = null
        )
    )

    override fun handleEvent(event: QRCodeDisplayEvent) {
        when (event) {
            is QRCodeDisplayEvent.GenerateQRCode -> {
                generateQRCode(event.userId)
            }
            is QRCodeDisplayEvent.RefreshQRCode -> {
                refreshQRCode()
            }
            is QRCodeDisplayEvent.RetryGeneration -> {
                retryGeneration()
            }
            is QRCodeDisplayEvent.ShareQRCode -> {
                shareQRCode(event.bitmap)
            }
            is QRCodeDisplayEvent.SaveQRCode -> {
                saveQRCode(event.bitmap)
            }
        }
    }

    /**
     * Generates QR code for the specified user profile
     */
    private fun generateQRCode(userId: String) {
        if (_uiState.value.currentUserId == userId && _uiState.value.qrCodeBitmap != null) {
            // QR code already generated for this user
            return
        }

        updateState { currentState ->
            currentState.copy(
                isLoading = true,
                error = null,
                currentUserId = userId
            )
        }

        executeUseCase(
            useCase = {
                qrCodeGenerationUseCase(
                    QRCodeGenerationRequest(
                        targetUserId = userId,
                        expirationHours = 0
                    )
                )
            },
            onSuccess = { result ->
                // Convert QR code data string to bitmap
                val qrCodeBitmap = generateQRCodeBitmap(result.qrCodeData)
                
                if (qrCodeBitmap != null) {
                    updateState { currentState ->
                        currentState.copy(
                            qrCodeBitmap = qrCodeBitmap,
                            profileUrl = result.shareableUrl,
                            isLoading = false,
                            error = null
                        )
                    }
                    
                    Timber.d("QR code generated successfully for user: $userId")
                } else {
                    val error = LiftrixError.UnknownError("Failed to generate QR code bitmap")
                    updateState { currentState ->
                        currentState.copy(
                            isLoading = false,
                            error = error
                        )
                    }
                    Timber.e("Failed to create QR code bitmap for user: $userId")
                }
            },
            onError = { error ->
                updateState { currentState ->
                    currentState.copy(
                        isLoading = false,
                        error = error
                    )
                }
                
                Timber.e("Failed to generate QR code for user: $userId - ${error.message}")
            },
            showLoading = false // We handle loading state manually
        )
    }

    /**
     * Refreshes the current QR code
     */
    private fun refreshQRCode() {
        val currentUserId = _uiState.value.currentUserId
        if (currentUserId != null) {
            // Clear current QR code and regenerate
            updateState { currentState ->
                currentState.copy(
                    qrCodeBitmap = null,
                    profileUrl = null
                )
            }
            generateQRCode(currentUserId)
        } else {
            Timber.w("Cannot refresh QR code - no user ID available")
        }
    }

    /**
     * Retries QR code generation after an error
     */
    private fun retryGeneration() {
        val currentUserId = _uiState.value.currentUserId
        if (currentUserId != null) {
            generateQRCode(currentUserId)
        } else {
            Timber.w("Cannot retry QR code generation - no user ID available")
        }
    }

    /**
     * Shares the QR code bitmap
     */
    private fun shareQRCode(bitmap: Bitmap) {
        viewModelScope.launch {
            try {
                // TODO: Implement QR code sharing functionality
                // This would typically involve:
                // 1. Saving the bitmap to a temporary file
                // 2. Creating a share intent with the image
                // 3. Launching the system share sheet
                
                Timber.d("QR code sharing initiated")
                
                // For now, we'll just log the action
                // The actual implementation would depend on the specific sharing requirements
                
            } catch (exception: Exception) {
                val error = LiftrixError.FileSystemError(
                    errorMessage = "Failed to share QR code"
                )
                handleError(error)
                Timber.e(exception, "Failed to share QR code")
            }
        }
    }

    /**
     * Saves the QR code bitmap to device storage
     */
    private fun saveQRCode(bitmap: Bitmap) {
        viewModelScope.launch {
            try {
                // TODO: Implement QR code saving functionality
                // This would typically involve:
                // 1. Requesting storage permissions if needed
                // 2. Saving the bitmap to the device's Pictures/Downloads folder
                // 3. Showing a success/failure message to the user
                
                Timber.d("QR code saving initiated")
                
                // For now, we'll just log the action
                // The actual implementation would depend on the specific saving requirements
                
            } catch (exception: Exception) {
                val error = LiftrixError.FileSystemError(
                    errorMessage = "Failed to save QR code"
                )
                handleError(error)
                Timber.e(exception, "Failed to save QR code")
            }
        }
    }

    override fun setLoadingState() {
        updateState { currentState ->
            currentState.copy(isLoading = true)
        }
    }

    override fun updateErrorState(error: LiftrixError) {
        updateState { currentState ->
            currentState.copy(
                error = error,
                isLoading = false
            )
        }
    }

    /**
     * Generates a QR code bitmap from the given data string
     */
    private fun generateQRCodeBitmap(data: String): Bitmap? {
        return try {
            val writer = QRCodeWriter()
            val hints = mapOf(
                EncodeHintType.MARGIN to 1,
                EncodeHintType.ERROR_CORRECTION to com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.M
            )
            
            val bitMatrix = writer.encode(data, BarcodeFormat.QR_CODE, QR_CODE_SIZE, QR_CODE_SIZE, hints)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
            
            bitmap
        } catch (e: Exception) {
            Timber.e(e, "Failed to generate QR code bitmap")
            null
        }
    }

    companion object {
        private const val QR_CODE_SIZE = 512 // Size in pixels
    }
}

/**
 * UI state for QR code display screen
 */
data class QRCodeDisplayUiState(
    val qrCodeBitmap: Bitmap?,
    val profileUrl: String?,
    val isLoading: Boolean,
    val error: LiftrixError?,
    val currentUserId: String?
) : UiState<Bitmap?>() {
    
    /**
     * Whether QR code can be displayed
     */
    val canShowQRCode: Boolean
        get() = qrCodeBitmap != null && error == null
    
    /**
     * Whether we're in a loading state
     */
    val isLoadingState: Boolean
        get() = isLoading && qrCodeBitmap == null
    
    /**
     * Whether sharing/saving actions are available
     */
    val canPerformActions: Boolean
        get() = qrCodeBitmap != null && !isLoading
}

/**
 * Events for QR code display screen
 */
sealed class QRCodeDisplayEvent : ViewModelEvent {
    
    /**
     * Generate QR code for the specified user
     */
    data class GenerateQRCode(val userId: String) : QRCodeDisplayEvent()
    
    /**
     * Refresh the current QR code
     */
    object RefreshQRCode : QRCodeDisplayEvent()
    
    /**
     * Retry QR code generation after error
     */
    object RetryGeneration : QRCodeDisplayEvent()
    
    /**
     * Share the QR code bitmap
     */
    data class ShareQRCode(val bitmap: Bitmap) : QRCodeDisplayEvent()
    
    /**
     * Save the QR code bitmap to device storage
     */
    data class SaveQRCode(val bitmap: Bitmap) : QRCodeDisplayEvent()
}