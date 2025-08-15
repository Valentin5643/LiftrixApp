package com.example.liftrix.service

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Base64
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.service.QRCodeService
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.MultiFormatWriter
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import com.google.zxing.common.HybridBinarizer
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of QRCodeService using ZXing library
 * 
 * Provides QR code generation and parsing with error handling and validation.
 * Supports custom styling and branding for profile QR codes.
 * 
 * Implements the domain service interface following Clean Architecture principles.
 */
@Singleton
class QRCodeServiceImpl @Inject constructor() : QRCodeService {

    companion object {
        private const val QR_CODE_MARGIN = 1
        private const val MIN_QR_SIZE = 100
        private const val MAX_QR_SIZE = 1000
        private const val PROFILE_URL_PREFIX = "liftrix://profile/"
        private const val WEB_URL_PREFIX = "https://liftrix.app/profile?qr="
        private const val GYM_BUDDY_PREFIX = "liftrix://gym-buddy/"
        private const val ENCRYPTION_ALGORITHM = "AES/CBC/PKCS5Padding"
        private const val KEY_ALGORITHM = "AES"
    }

    override suspend fun generateQRCode(data: String, size: Int, margin: Int): LiftrixResult<Bitmap> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.ExportError(
                    errorMessage = "Failed to generate QR code: ${throwable.message}",
                    analyticsContext = mapOf("operation" to "generate_qr_code")
                )
            }
        ) {
            Timber.d("Generating QR code: data length=${data.length}, size=$size, margin=$margin")
            
            // Validate input parameters
            validateQRCodeParameters(data, size, margin)
            
            // Generate QR code bit matrix
            val writer = MultiFormatWriter()
            val bitMatrix = try {
                writer.encode(
                    data,
                    BarcodeFormat.QR_CODE,
                    size,
                    size,
                    mapOf(
                        com.google.zxing.EncodeHintType.MARGIN to margin,
                        com.google.zxing.EncodeHintType.CHARACTER_SET to "UTF-8"
                    )
                )
            } catch (e: WriterException) {
                throw LiftrixError.ValidationError(
                    field = "data",
                    violations = listOf("Failed to encode QR code data: ${e.message}")
                )
            }
            
            // Convert bit matrix to bitmap
            val bitmap = createBitmapFromBitMatrix(bitMatrix)
            
            Timber.d("QR code generated successfully: ${bitmap.width}x${bitmap.height}")
            bitmap
        }
    }

    override suspend fun parseQRCode(bitmap: Bitmap): LiftrixResult<String> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DataRetrievalError(
                    errorMessage = "Failed to parse QR code: ${throwable.message}",
                    analyticsContext = mapOf("operation" to "parse_qr_code")
                )
            }
        ) {
            Timber.d("Parsing QR code from bitmap: ${bitmap.width}x${bitmap.height}")
            
            // Convert bitmap to RGB array
            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            
            // Create luminance source
            val source = RGBLuminanceSource(width, height, pixels)
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
            
            // Configure decoder hints
            val hints = mapOf(
                DecodeHintType.TRY_HARDER to true,
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                DecodeHintType.CHARACTER_SET to "UTF-8"
            )
            
            // Decode QR code
            val reader = MultiFormatReader()
            val result = try {
                reader.decode(binaryBitmap, hints)
            } catch (e: Exception) {
                throw LiftrixError.ValidationError(
                    field = "qr_code_bitmap",
                    violations = listOf("Failed to decode QR code: ${e.message}")
                )
            }
            
            val decodedText = result.text
            Timber.d("QR code parsed successfully: data length=${decodedText.length}")
            
            // Validate decoded data
            if (!validateQRCodeData(decodedText)) {
                throw LiftrixError.ValidationError(
                    field = "qr_code_bitmap",
                    violations = listOf("Invalid QR code data format")
                )
            }
            
            decodedText
        }
    }

    override fun validateQRCodeData(data: String): Boolean {
        if (data.isBlank()) {
            return false
        }
        
        // Check for supported URL formats
        return data.startsWith(PROFILE_URL_PREFIX) || 
               data.startsWith(WEB_URL_PREFIX) ||
               data.startsWith(GYM_BUDDY_PREFIX) ||
               data.matches(Regex("^[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}$")) || // UUID format
               data.startsWith("eyJ") // Base64 encoded JSON (encrypted payload)
    }

    override suspend fun generateProfileQRCode(profileUrl: String, size: Int): LiftrixResult<Bitmap> {
        return liftrixCatching(
            errorMapper = { exception ->
                when (exception) {
                    is LiftrixError -> exception
                    else -> LiftrixError.UnknownError(
                        errorMessage = "QR code generation failed: ${exception.message}"
                    )
                }
            }
        ) {
            Timber.d("Generating profile QR code: url=$profileUrl, size=$size")
            
            // Generate base QR code
            val baseQRResult = generateQRCode(profileUrl, size, QR_CODE_MARGIN)
            if (baseQRResult.isFailure) {
                return baseQRResult
            }
            
            val baseQR = baseQRResult.getOrThrow()
            
            // Add branding and styling
            val brandedQR = addProfileBranding(baseQR)
            
            Timber.d("Profile QR code generated with branding")
            brandedQR
        }
    }

    override suspend fun encryptQRData(data: String, key: String): LiftrixResult<String> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.PermissionError(
                    errorMessage = "Failed to encrypt QR data: ${throwable.message}",
                    analyticsContext = mapOf("operation" to "encrypt_qr_data")
                )
            }
        ) {
            val secretKey = SecretKeySpec(key.toByteArray().take(16).toByteArray(), KEY_ALGORITHM)
            val cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM)
            
            // Generate random IV
            val iv = ByteArray(16)
            SecureRandom().nextBytes(iv)
            val ivSpec = IvParameterSpec(iv)
            
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec)
            val encryptedBytes = cipher.doFinal(data.toByteArray())
            
            // Combine IV and encrypted data
            val combined = iv + encryptedBytes
            Base64.encodeToString(combined, Base64.NO_WRAP)
        }
    }
    
    override suspend fun decryptQRData(encryptedData: String, key: String): LiftrixResult<String> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.PermissionError(
                    errorMessage = "Failed to decrypt QR data: ${throwable.message}",
                    analyticsContext = mapOf("operation" to "decrypt_qr_data")
                )
            }
        ) {
            val combined = Base64.decode(encryptedData, Base64.NO_WRAP)
            
            // Extract IV and encrypted data
            val iv = combined.take(16).toByteArray()
            val encryptedBytes = combined.drop(16).toByteArray()
            
            val secretKey = SecretKeySpec(key.toByteArray().take(16).toByteArray(), KEY_ALGORITHM)
            val cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM)
            val ivSpec = IvParameterSpec(iv)
            
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)
            val decryptedBytes = cipher.doFinal(encryptedBytes)
            
            String(decryptedBytes)
        }
    }

    /**
     * Validates QR code generation parameters
     */
    private fun validateQRCodeParameters(data: String, size: Int, margin: Int) {
        if (data.isBlank()) {
            throw LiftrixError.ValidationError(
                field = "data",
                violations = listOf("QR code data cannot be empty")
            )
        }
        
        if (data.length > 2000) {
            throw LiftrixError.ValidationError(
                field = "data",
                violations = listOf("QR code data too long (max 2000 characters)")
            )
        }
        
        if (size < MIN_QR_SIZE || size > MAX_QR_SIZE) {
            throw LiftrixError.ValidationError(
                field = "size",
                violations = listOf("QR code size must be between $MIN_QR_SIZE and $MAX_QR_SIZE pixels")
            )
        }
        
        if (margin < 0 || margin > 4) {
            throw LiftrixError.ValidationError(
                field = "margin",
                violations = listOf("QR code margin must be between 0 and 4")
            )
        }
    }

    /**
     * Creates a bitmap from a ZXing BitMatrix
     */
    private fun createBitmapFromBitMatrix(bitMatrix: BitMatrix): Bitmap {
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        
        for (x in 0 until width) {
            for (y in 0 until height) {
                val color = if (bitMatrix[x, y]) Color.BLACK else Color.WHITE
                bitmap.setPixel(x, y, color)
            }
        }
        
        return bitmap
    }

    /**
     * Adds Liftrix branding to a QR code bitmap
     */
    private fun addProfileBranding(qrBitmap: Bitmap): Bitmap {
        val brandedBitmap = qrBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(brandedBitmap)
        
        // Add subtle branding without interfering with QR code scanning
        val paint = Paint().apply {
            color = Color.BLACK
            textSize = 12f
            isAntiAlias = true
        }
        
        // Add small "Liftrix" text at the bottom (outside the QR code)
        // This is optional and should not interfere with scanning
        val text = "Liftrix Profile"
        val textWidth = paint.measureText(text)
        val x = (brandedBitmap.width - textWidth) / 2
        val y = brandedBitmap.height - 5f
        
        // Only add text if there's enough space
        if (y > brandedBitmap.height - 20) {
            // Don't add text if it would interfere with QR code
            return brandedBitmap
        }
        
        canvas.drawText(text, x, y, paint)
        
        return brandedBitmap
    }
}