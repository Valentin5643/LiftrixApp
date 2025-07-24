package com.example.liftrix.service

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
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
 */
@Singleton
class QRCodeServiceImpl @Inject constructor() : QRCodeService {

    companion object {
        private const val QR_CODE_MARGIN = 1
        private const val MIN_QR_SIZE = 100
        private const val MAX_QR_SIZE = 1000
        private const val PROFILE_URL_PREFIX = "liftrix://profile/"
        private const val WEB_URL_PREFIX = "https://liftrix.app/profile?qr="
    }

    override suspend fun generateQRCode(data: String, size: Int): LiftrixResult<Bitmap> {
        return liftrixCatching {
            Timber.d("Generating QR code: data length=${data.length}, size=$size")
            
            // Validate input parameters
            validateQRCodeParameters(data, size)
            
            // Generate QR code bit matrix
            val writer = MultiFormatWriter()
            val bitMatrix = try {
                writer.encode(
                    data,
                    BarcodeFormat.QR_CODE,
                    size,
                    size,
                    mapOf(
                        com.google.zxing.EncodeHintType.MARGIN to QR_CODE_MARGIN,
                        com.google.zxing.EncodeHintType.CHARACTER_SET to "UTF-8"
                    )
                )
            } catch (e: WriterException) {
                throw LiftrixError.ValidationError("Failed to encode QR code data: ${e.message}")
            }
            
            // Convert bit matrix to bitmap
            val bitmap = createBitmapFromBitMatrix(bitMatrix)
            
            Timber.d("QR code generated successfully: ${bitmap.width}x${bitmap.height}")
            bitmap
        }
    }

    override suspend fun parseQRCode(bitmap: Bitmap): LiftrixResult<String> {
        return liftrixCatching {
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
                throw LiftrixError.ValidationError("Failed to decode QR code: ${e.message}")
            }
            
            val decodedText = result.text
            Timber.d("QR code parsed successfully: data length=${decodedText.length}")
            
            // Validate decoded data
            if (!validateQRCodeData(decodedText)) {
                throw LiftrixError.ValidationError("Invalid QR code data format")
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
               data.matches(Regex("^[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}$")) // UUID format
    }

    override suspend fun generateProfileQRCode(profileUrl: String, size: Int): LiftrixResult<Bitmap> {
        return liftrixCatching {
            Timber.d("Generating profile QR code: url=$profileUrl, size=$size")
            
            // Generate base QR code
            val baseQRResult = generateQRCode(profileUrl, size)
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

    /**
     * Validates QR code generation parameters
     */
    private fun validateQRCodeParameters(data: String, size: Int) {
        if (data.isBlank()) {
            throw LiftrixError.ValidationError("QR code data cannot be empty")
        }
        
        if (data.length > 2000) {
            throw LiftrixError.ValidationError("QR code data too long (max 2000 characters)")
        }
        
        if (size < MIN_QR_SIZE || size > MAX_QR_SIZE) {
            throw LiftrixError.ValidationError("QR code size must be between $MIN_QR_SIZE and $MAX_QR_SIZE pixels")
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