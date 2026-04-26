package com.example.liftrix.ui.social

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.lifecycle.viewModelScope
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import dagger.hilt.android.qualifiers.ApplicationContext
import com.example.liftrix.domain.model.error.LiftrixError
import java.io.File
import com.example.liftrix.domain.usecase.social.QRCodeGenerationUseCase
import com.example.liftrix.domain.usecase.social.QRCodeGenerationRequest
import com.example.liftrix.domain.service.AnalyticsTracker
import com.example.liftrix.ui.common.event.ViewModelEvent
import com.example.liftrix.ui.common.viewmodel.ModernBaseViewModel
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
    private val analyticsTracker: AnalyticsTracker,
    @ApplicationContext private val context: Context
) : ModernBaseViewModel<QRCodeDisplayUiState>(
    initialState = QRCodeDisplayUiState(
        qrCodeBitmap = null,
        profileUrl = null,
        isLoading = false,
        error = null,
        currentUserId = null,
        saveSuccess = false
    )
) {

    fun handleEvent(event: QRCodeDisplayEvent) {
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
        if (uiState.value.currentUserId == userId && uiState.value.qrCodeBitmap != null) {
            // QR code already generated for this user
            return
        }

        viewModelScope.launch {
            updateState { currentState ->
                currentState.copy(
                    isLoading = true,
                    error = null,
                    currentUserId = userId
                )
            }

            val result = qrCodeGenerationUseCase(
                QRCodeGenerationRequest(
                    targetUserId = userId,
                    expirationHours = 0
                )
            )

            result.onSuccess { qrCodeResult ->
                // Convert QR code data string to bitmap
                val qrCodeBitmap = generateQRCodeBitmap(qrCodeResult.qrCodeData)

                if (qrCodeBitmap != null) {
                    // Track QR code generation analytics
                    analyticsTracker.trackQRCodeEvent(
                        action = "GENERATE",
                        userId = userId,
                        qrType = "GYM_BUDDY",
                        additionalProperties = mapOf(
                            "payload_type" to "in_app"
                        )
                    )

                    updateState { currentState ->
                        currentState.copy(
                            qrCodeBitmap = qrCodeBitmap,
                            profileUrl = null,
                            isLoading = false,
                            error = null
                        )
                    }

                    Timber.d("QR code generated successfully for user: $userId")
                } else {
                    val error = LiftrixError.UnknownError(errorMessage = "Failed to generate QR code bitmap")
                    updateState { currentState ->
                        currentState.copy(
                            isLoading = false,
                            error = error
                        )
                    }
                    Timber.e("Failed to create QR code bitmap for user: $userId")
                }
            }.onFailure { error ->
                (error as? Throwable)?.let { logError(it, "generateQRCode") }
                updateState { currentState ->
                    currentState.copy(
                        isLoading = false,
                        error = error as? LiftrixError ?: LiftrixError.UnknownError(errorMessage = error.message ?: "Failed to generate QR code")
                    )
                }

                Timber.e("Failed to generate QR code for user: $userId - ${error.message}")
            }
        }
    }

    /**
     * Refreshes the current QR code
     */
    private fun refreshQRCode() {
        val currentUserId = uiState.value.currentUserId
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
        val currentUserId = uiState.value.currentUserId
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
                Timber.d("QR code sharing initiated")
                
                // Save bitmap to temporary cache file
                val filename = "liftrix_qr_${System.currentTimeMillis()}.png"
                val cacheDir = File(context.cacheDir, "shared_qr_codes")
                if (!cacheDir.exists()) {
                    cacheDir.mkdirs()
                }
                
                val file = File(cacheDir, filename)
                val outputStream = file.outputStream()
                
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                outputStream.flush()
                outputStream.close()
                
                // Create content URI using FileProvider
                val contentUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                
                // Create share intent
                val shareIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    putExtra(Intent.EXTRA_TEXT, "Scan my Liftrix QR code in the app to connect.")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                
                // Launch system share sheet
                val chooserIntent = Intent.createChooser(shareIntent, "Share QR Code")
                chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooserIntent)

                // Track QR code share analytics
                val currentUserId = uiState.value.currentUserId ?: "unknown"
                analyticsTracker.trackQRCodeEvent(
                    action = "SHARE",
                    userId = currentUserId,
                    qrType = "GYM_BUDDY",
                    additionalProperties = mapOf(
                        "method" to "native_share"
                    )
                )
                
                Timber.d("QR code share intent launched successfully")
                
            } catch (exception: Exception) {
                val error = LiftrixError.FileSystemError(
                    errorMessage = "Failed to share QR code: ${exception.message}"
                )
                logError(exception, "shareQRCode")
                updateState { currentState ->
                    currentState.copy(error = error)
                }
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
                Timber.d("QR code saving initiated")
                
                // Create filename with timestamp
                val filename = "Liftrix_QR_${System.currentTimeMillis()}.png"
                val mimeType = "image/png"
                
                // Use MediaStore to save to Pictures directory
                val contentResolver = context.contentResolver
                val imageCollection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                
                val imageDetails = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                    put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Liftrix")
                }
                
                // Insert the image and get URI
                val imageUri = contentResolver.insert(imageCollection, imageDetails)
                
                if (imageUri != null) {
                    // Write bitmap to the URI
                    contentResolver.openOutputStream(imageUri)?.use { outputStream ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                        outputStream.flush()
                    }
                    
                    Timber.d("QR code saved successfully to Pictures/Liftrix/$filename")

                    // Track QR code save analytics
                    val currentUserId = uiState.value.currentUserId ?: "unknown"
                    analyticsTracker.trackQRCodeEvent(
                        action = "SAVE",
                        userId = currentUserId,
                        qrType = "GYM_BUDDY",
                        additionalProperties = mapOf(
                            "storage_location" to "Pictures/Liftrix"
                        )
                    )
                    
                    // Update UI state to show success
                    updateState { currentState ->
                        currentState.copy(
                            saveSuccess = true
                        )
                    }
                } else {
                    throw Exception("Failed to create media store entry")
                }
                
            } catch (exception: Exception) {
                val error = LiftrixError.FileSystemError(
                    errorMessage = "Failed to save QR code: ${exception.message}"
                )
                logError(exception, "saveQRCode")
                updateState { currentState ->
                    currentState.copy(error = error)
                }
                Timber.e(exception, "Failed to save QR code")
            }
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
    val currentUserId: String?,
    val saveSuccess: Boolean = false
) {
    
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
