package com.example.liftrix.service

import android.graphics.Bitmap
import com.example.liftrix.domain.model.common.LiftrixResult

/**
 * Service interface for QR code generation and parsing operations
 * 
 * Provides QR code functionality for profile sharing and deep linking.
 * Handles QR code generation, parsing, and validation with proper error handling.
 */
interface QRCodeService {
    
    /**
     * Generates a QR code bitmap from data string
     * 
     * @param data The data to encode in the QR code
     * @param size The size of the QR code bitmap (width and height)
     * @return LiftrixResult containing the generated bitmap or error
     */
    suspend fun generateQRCode(data: String, size: Int = 400): LiftrixResult<Bitmap>
    
    /**
     * Parses QR code data from a bitmap
     * 
     * @param bitmap The bitmap containing the QR code
     * @return LiftrixResult containing the parsed data string or error
     */
    suspend fun parseQRCode(bitmap: Bitmap): LiftrixResult<String>
    
    /**
     * Validates QR code data format
     * 
     * @param data The QR code data to validate
     * @return True if the data format is valid, false otherwise
     */
    fun validateQRCodeData(data: String): Boolean
    
    /**
     * Creates a profile QR code with Liftrix branding
     * 
     * @param profileUrl The profile URL to encode
     * @param size The size of the QR code bitmap
     * @return LiftrixResult containing the branded QR code bitmap or error
     */
    suspend fun generateProfileQRCode(profileUrl: String, size: Int = 400): LiftrixResult<Bitmap>
}