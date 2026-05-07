package com.example.liftrix.data.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.net.Uri
import android.provider.MediaStore
import android.util.Size
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.social.MediaItem
import com.example.liftrix.domain.model.social.MediaType
import com.example.liftrix.domain.model.social.MediaUploadRequest
import com.example.liftrix.domain.service.MediaUploadService
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.StorageReference
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaUploadServiceImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val storage: FirebaseStorage,
    private val firebaseAuth: FirebaseAuth
) : MediaUploadService {

    private val uploadTasks = mutableMapOf<String, com.google.firebase.storage.UploadTask>()
    
    /**
     * Retrieves download URL with exponential backoff and proper error handling.
     * Fails fast on authentication/authorization errors without retrying.
     * 
     * @param storageRef The storage reference to get URL for
     * @return The download URL as a string
     * @throws StorageException if permission denied or other storage errors
     */
    private suspend fun getDownloadUrlWithBackoff(storageRef: StorageReference): String {
        var delayMs = 250L
        repeat(5) { attempt ->
            try {
                val url = storageRef.downloadUrl.await()
                val urlString = url.toString()
                Timber.d("Download URL obtained on attempt ${attempt + 1}: $urlString")
                return urlString
            } catch (e: StorageException) {
                // Log detailed error information
                Timber.e(
                    "GET-DOWNLOAD-URL failed (attempt ${attempt + 1}): code=%s, http=%s, message=%s",
                    e.errorCode,
                    e.httpResultCode,
                    e.message
                )
                
                // Don't retry on permission/auth errors - these won't resolve with retries
                if (e.errorCode == StorageException.ERROR_NOT_AUTHORIZED ||
                    e.errorCode == StorageException.ERROR_NOT_AUTHENTICATED ||
                    e.httpResultCode == 403 || 
                    e.httpResultCode == 401) {
                    Timber.e("Storage permission denied - check Firebase Storage rules for read access")
                    throw LiftrixError.AuthenticationError(
                        errorMessage = "Permission denied. Unable to access uploaded image. Please check storage rules.",
                        analyticsContext = mapOf(
                            "storage_path" to storageRef.path,
                            "error_code" to e.errorCode.toString(),
                            "http_code" to e.httpResultCode.toString()
                        )
                    )
                }
                
                // For other errors, retry with backoff
                if (attempt == 4) {
                    // Final attempt failed
                    throw LiftrixError.NetworkError(
                        errorMessage = "Failed to get download URL after 5 attempts",
                        analyticsContext = mapOf(
                            "storage_path" to storageRef.path,
                            "final_error" to e.message.orEmpty()
                        )
                    )
                }
                
                Timber.d("Retrying download URL retrieval after ${delayMs}ms delay (attempt ${attempt + 1}/5)")
                kotlinx.coroutines.delay(delayMs)
                delayMs *= 2 // Exponential backoff
            } catch (e: Exception) {
                // Non-storage exception
                Timber.e(e, "Unexpected error getting download URL on attempt ${attempt + 1}")
                if (attempt == 4) throw e
                kotlinx.coroutines.delay(delayMs)
                delayMs *= 2
            }
        }
        
        // Should never reach here
        error("Unreachable code in getDownloadUrlWithBackoff")
    }
    
    /**
     * Ensures the user is authenticated with a valid token before storage operations.
     * This prevents 403 errors caused by expired tokens in long-running sessions.
     * 
     * @return The authenticated user's ID
     * @throws LiftrixError.AuthenticationError if authentication fails
     */
    private suspend fun ensureAuthenticatedForStorage(): String {
        // Get current user and force token refresh
        val currentUser = firebaseAuth.currentUser
        if (currentUser == null) {
            throw LiftrixError.AuthenticationError(
                errorMessage = "User not authenticated. Please sign in again.",
                analyticsContext = mapOf("operation" to "storage_upload")
            )
        }
        
        // Check if user is anonymous - they might have limited permissions
        if (currentUser.isAnonymous) {
            Timber.w("Anonymous user attempting storage upload - this may fail due to permissions")
        }
        
        return try {
            // Force token refresh to ensure it's valid for Storage operations
            // This is critical for preventing 403 errors
            val tokenResult = currentUser.getIdToken(true).await()
            
            if (tokenResult?.token.isNullOrBlank()) {
                throw LiftrixError.AuthenticationError(
                    errorMessage = "Failed to refresh authentication. Please sign in again.",
                    analyticsContext = mapOf(
                        "user_id" to currentUser.uid,
                        "is_anonymous" to currentUser.isAnonymous.toString()
                    )
                )
            }
            
            Timber.d("Auth token refreshed successfully for storage operation: ${currentUser.uid}")
            currentUser.uid
        } catch (e: Exception) {
            Timber.e(e, "Failed to refresh auth token for storage operation")
            throw LiftrixError.AuthenticationError(
                errorMessage = "Authentication expired. Please sign in again to upload images.",
                analyticsContext = mapOf(
                    "error" to e.message.orEmpty(),
                    "user_id" to currentUser.uid
                )
            )
        }
    }

    override suspend fun uploadMediaItems(
        userId: String,
        mediaRequests: List<MediaUploadRequest>
    ): LiftrixResult<List<MediaItem>> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.NetworkError(
                errorMessage = "Failed to upload media items: ${throwable.message}",
                analyticsContext = mapOf(
                    "user_id" to userId,
                    "item_count" to mediaRequests.size.toString()
                )
            )
        }
    ) {
        mediaRequests.map { request ->
            uploadMediaItem(userId, request).getOrThrow()
        }
    }

    override suspend fun uploadMediaItem(
        userId: String,
        mediaRequest: MediaUploadRequest
    ): LiftrixResult<MediaItem> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.NetworkError(
                errorMessage = "Failed to upload media: ${throwable.message}",
                analyticsContext = mapOf(
                    "user_id" to userId,
                    "media_type" to mediaRequest.type.name
                )
            )
        }
    ) {
        val mediaId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()
        
        when (mediaRequest.type) {
            MediaType.IMAGE -> uploadImage(userId, mediaId, mediaRequest, timestamp)
            MediaType.VIDEO -> uploadVideo(userId, mediaId, mediaRequest, timestamp)
        }
    }

    private suspend fun uploadImage(
        userId: String,
        mediaId: String,
        request: MediaUploadRequest,
        timestamp: Long
    ): MediaItem = withContext(Dispatchers.IO) {
        // Compress original image
        val compressedUri = compressImage(
            request.uri as Uri,
            request.maxFileSizeMB,
            request.compressionQuality
        ).getOrThrow()
        
        // Generate thumbnail
        val thumbnailUri = generateThumbnail(compressedUri).getOrThrow()
        
        // Get image dimensions
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(compressedUri)?.use { inputStream ->
            BitmapFactory.decodeStream(inputStream, null, options)
        }
        
        // Upload original and thumbnail
        val originalRef = getImageStorageRef(userId, mediaId, "original")
        val thumbnailRef = getImageStorageRef(userId, mediaId, "thumbnail")
        
        val originalUploadTask = uploadFile(originalRef, compressedUri, "image/jpeg")
        val thumbnailUploadTask = uploadFile(thumbnailRef, thumbnailUri, "image/jpeg")
        
        val originalUrl = originalUploadTask.await().storage.downloadUrl.await().toString()
        val thumbnailUrl = thumbnailUploadTask.await().storage.downloadUrl.await().toString()
        
        // Get file size
        val fileSize = getFileSize(compressedUri)
        
        MediaItem(
            id = mediaId,
            type = MediaType.IMAGE,
            originalUrl = originalUrl,
            thumbnailUrl = thumbnailUrl,
            compressedUrl = originalUrl,
            width = options.outWidth.takeIf { it > 0 },
            height = options.outHeight.takeIf { it > 0 },
            fileSizeBytes = fileSize,
            uploadedAt = timestamp
        )
    }

    private suspend fun uploadVideo(
        userId: String,
        mediaId: String,
        request: MediaUploadRequest,
        timestamp: Long
    ): MediaItem = withContext(Dispatchers.IO) {
        // Compress video
        val compressedUri = compressVideo(
            request.uri as Uri,
            request.maxFileSizeMB
        ).getOrThrow()
        
        // Generate video thumbnail
        val thumbnailUri = generateVideoThumbnail(compressedUri).getOrThrow()
        
        // Get video metadata
        val retriever = MediaMetadataRetriever().apply {
            setDataSource(context, compressedUri)
        }
        
        val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
        val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
        val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        
        retriever.release()
        
        // Upload video and thumbnail
        val videoRef = getVideoStorageRef(userId, mediaId, "video")
        val thumbnailRef = getImageStorageRef(userId, mediaId, "thumbnail")
        
        val videoUploadTask = uploadFile(videoRef, compressedUri, "video/mp4")
        val thumbnailUploadTask = uploadFile(thumbnailRef, thumbnailUri, "image/jpeg")
        
        val videoUrl = videoUploadTask.await().storage.downloadUrl.await().toString()
        val thumbnailUrl = thumbnailUploadTask.await().storage.downloadUrl.await().toString()
        
        // Get file size
        val fileSize = getFileSize(compressedUri)
        
        MediaItem(
            id = mediaId,
            type = MediaType.VIDEO,
            originalUrl = videoUrl,
            thumbnailUrl = thumbnailUrl,
            compressedUrl = videoUrl,
            width = width,
            height = height,
            fileSizeBytes = fileSize,
            duration = duration,
            uploadedAt = timestamp
        )
    }

    override suspend fun uploadProfileImage(
        userId: String,
        imageUri: Uri
    ): LiftrixResult<String> = liftrixCatching(
        errorMapper = { throwable ->
            if (throwable is LiftrixError.AuthenticationError) {
                throwable
            } else {
                LiftrixError.NetworkError(
                    errorMessage = "Failed to upload profile image: ${throwable.message}",
                    analyticsContext = mapOf("user_id" to userId)
                )
            }
        }
    ) {
        // Ensure authentication is valid before attempting upload
        val authenticatedUserId = ensureAuthenticatedForStorage()
        
        // Verify the userId matches the authenticated user for security
        if (authenticatedUserId != userId) {
            Timber.w("Profile upload userId mismatch: requested=$userId, auth=$authenticatedUserId")
            throw LiftrixError.AuthenticationError(
                errorMessage = "Cannot upload profile image for another user",
                analyticsContext = mapOf(
                    "requested_user" to userId,
                    "auth_user" to authenticatedUserId
                )
            )
        }
        
        val compressedUri = compressImage(imageUri, maxSizeMB = 1.0f, quality = 90).getOrThrow()
        
        val storageRef = storage.reference
            .child("users")
            .child(userId)
            .child("profile")
            .child("avatar.jpg")
        
        val uploadTask = uploadFile(storageRef, compressedUri, "image/jpeg")
        val taskSnapshot = uploadTask.await()
        
        taskSnapshot.storage.downloadUrl.await().toString()
    }

    override suspend fun deleteMedia(mediaUrl: String): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.NetworkError(
                errorMessage = "Failed to delete media: ${throwable.message}",
                analyticsContext = mapOf("media_url" to mediaUrl)
            )
        }
    ) {
        val storageRef = storage.getReferenceFromUrl(mediaUrl)
        storageRef.delete().await()
    }

    override suspend fun compressImage(
        imageUri: Uri,
        maxSizeMB: Float,
        quality: Int
    ): LiftrixResult<Uri> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "COMPRESS_IMAGE",
                errorMessage = "Failed to compress image: ${throwable.message}",
                analyticsContext = emptyMap()
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            val bitmap = context.contentResolver.openInputStream(imageUri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            } ?: throw IllegalArgumentException("Cannot decode image")
            
            // Calculate target size
            val maxSizeBytes = (maxSizeMB * 1024 * 1024).toLong()
            var currentQuality = quality
            
            val outputFile = File(context.cacheDir, "compressed_${UUID.randomUUID()}.jpg")
            
            do {
                val outputStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, currentQuality, outputStream)
                val compressedData = outputStream.toByteArray()
                
                if (compressedData.size <= maxSizeBytes || currentQuality <= 10) {
                    FileOutputStream(outputFile).use { fileOutputStream ->
                        fileOutputStream.write(compressedData)
                    }
                    break
                }
                
                currentQuality -= 10
            } while (true)
            
            Uri.fromFile(outputFile)
        }
    }

    override suspend fun generateThumbnail(
        imageUri: Uri,
        thumbnailSize: Int
    ): LiftrixResult<Uri> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "GENERATE_THUMBNAIL",
                errorMessage = "Failed to generate thumbnail: ${throwable.message}",
                analyticsContext = emptyMap()
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            val bitmap = context.contentResolver.openInputStream(imageUri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            } ?: throw IllegalArgumentException("Cannot decode image")
            
            val thumbnailBitmap = ThumbnailUtils.extractThumbnail(
                bitmap,
                thumbnailSize,
                thumbnailSize,
                ThumbnailUtils.OPTIONS_RECYCLE_INPUT
            )
            
            val outputFile = File(context.cacheDir, "thumbnail_${UUID.randomUUID()}.jpg")
            FileOutputStream(outputFile).use { outputStream ->
                thumbnailBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
            }
            
            thumbnailBitmap.recycle()
            
            Uri.fromFile(outputFile)
        }
    }

    override suspend fun compressVideo(
        videoUri: Uri,
        maxSizeMB: Float,
        resolution: String
    ): LiftrixResult<Uri> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "COMPRESS_VIDEO",
                errorMessage = "Failed to compress video: ${throwable.message}",
                analyticsContext = emptyMap()
            )
        }
    ) {
        // For now, return the original URI
        // In a production app, you would use FFmpeg or MediaCodec for video compression
        Timber.w("Video compression not implemented, returning original URI")
        videoUri
    }

    override suspend fun generateVideoThumbnail(
        videoUri: Uri,
        timeUs: Long
    ): LiftrixResult<Uri> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "GENERATE_VIDEO_THUMBNAIL",
                errorMessage = "Failed to generate video thumbnail: ${throwable.message}",
                analyticsContext = emptyMap()
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, videoUri)
                val bitmap = retriever.getFrameAtTime(
                    timeUs,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                ) ?: throw IllegalArgumentException("Cannot extract frame from video")
                
                // Scale bitmap to 300x300 while maintaining aspect ratio
                val scaledBitmap = Bitmap.createScaledBitmap(
                    bitmap,
                    300,
                    (300 * bitmap.height / bitmap.width.toFloat()).toInt(),
                    true
                )
                
                val outputFile = File(context.cacheDir, "video_thumbnail_${UUID.randomUUID()}.jpg")
                FileOutputStream(outputFile).use { outputStream ->
                    scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
                }
                
                // Clean up bitmaps
                if (bitmap != scaledBitmap) bitmap.recycle()
                scaledBitmap.recycle()
                
                Uri.fromFile(outputFile)
            } finally {
                runCatching { retriever.release() }
            }
        }
    }

    override fun getUploadProgress(uploadId: String): Flow<Float> = callbackFlow {
        val uploadTask = uploadTasks[uploadId]
        if (uploadTask == null) {
            trySend(0f)
            close()
            return@callbackFlow
        }
        
        val listener = uploadTask.addOnProgressListener { taskSnapshot ->
            val progress = (100.0 * taskSnapshot.bytesTransferred) / taskSnapshot.totalByteCount
            trySend(progress.toFloat() / 100f)
        }
        
        uploadTask.addOnCompleteListener {
            trySend(1f)
            close()
        }
        
        awaitClose {
            listener
        }
    }

    override suspend fun cancelUpload(uploadId: String): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "CANCEL_UPLOAD",
                errorMessage = "Failed to cancel upload: ${throwable.message}",
                analyticsContext = emptyMap()
            )
        }
    ) {
        uploadTasks[uploadId]?.cancel()
        uploadTasks.remove(uploadId)
    }

    private fun uploadFile(
        storageRef: StorageReference,
        fileUri: Uri,
        contentType: String
    ): com.google.firebase.storage.UploadTask {
        val metadata = StorageMetadata.Builder()
            .setContentType(contentType)
            .build()
        
        return storageRef.putFile(fileUri, metadata)
    }

    private fun getImageStorageRef(userId: String, mediaId: String, variant: String): StorageReference {
        return storage.reference
            .child("users")
            .child(userId)
            .child("media")
            .child("images")
            .child(mediaId)
            .child("$variant.jpg")
    }

    private fun getVideoStorageRef(userId: String, mediaId: String, variant: String): StorageReference {
        return storage.reference
            .child("users")
            .child(userId)
            .child("media")
            .child("videos")
            .child(mediaId)
            .child("$variant.mp4")
    }

    private fun getFileSize(uri: Uri): Long {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.available().toLong()
            } ?: 0L
        } catch (e: Exception) {
            Timber.w(e, "Failed to get file size")
            0L
        }
    }

    /**
     * Uploads an image to Firebase Storage after compression.
     * 
     * @param uri The URI of the image to upload
     * @param path The storage path WITHOUT file extension (e.g., "workout_images/userId/workoutId/filename")
     *             The method will append .jpg automatically
     * @param maxSizeKb Maximum size in KB for the compressed image
     * @return URL of the uploaded image
     */
    override suspend fun uploadImage(
        uri: Uri,
        path: String,
        maxSizeKb: Int
    ): LiftrixResult<String> = liftrixCatching(
        errorMapper = { throwable ->
            // Check if it's an authentication error and provide better message
            if (throwable is LiftrixError.AuthenticationError) {
                throwable
            } else {
                LiftrixError.NetworkError(
                    errorMessage = "Failed to upload image: ${throwable.message}",
                    analyticsContext = mapOf(
                        "path" to path,
                        "max_size_kb" to maxSizeKb.toString(),
                        "error_type" to throwable.javaClass.simpleName
                    )
                )
            }
        }
    ) {
        withContext(Dispatchers.IO) {
            // CRITICAL: Ensure authentication is valid before attempting upload
            // This prevents 403 errors from expired tokens
            val authenticatedUserId = ensureAuthenticatedForStorage()
            
            // Validate the path includes the authenticated user's ID for security
            val pathSegments = path.split("/")
            if (pathSegments.size >= 2 && pathSegments[1] != authenticatedUserId) {
                Timber.w("Path userId mismatch: path=${pathSegments[1]}, auth=$authenticatedUserId")
                // Optionally, you could auto-correct the path here or throw an error
                // For now, we'll log the warning and continue
            }
            
            // Compress image to target size
            val maxSizeMB = maxSizeKb / 1024.0f
            val compressedUri = compressImage(
                imageUri = uri,
                maxSizeMB = maxSizeMB,
                quality = 90
            ).getOrThrow()

            // Create storage reference
            val storageRef = storage.reference.child("$path.jpg")
            
            // Create upload metadata with auth info
            val metadata = StorageMetadata.Builder()
                .setContentType("image/jpeg")
                .setCustomMetadata("compressed", "true")
                .setCustomMetadata("original_uri", uri.toString())
                .setCustomMetadata("uploaded_by", authenticatedUserId)
                .setCustomMetadata("upload_timestamp", System.currentTimeMillis().toString())
                .build()

            // Upload file with progress tracking
            // Use putFile() instead of putStream() for better reliability
            // putFile() handles the file reading internally and is more robust
            // for local file URIs (especially cache files)
            val uploadTask = storageRef.putFile(compressedUri, metadata)

            // Wait for upload completion
            val taskSnapshot = uploadTask.await()
            
            // Verify upload was successful
            if (!taskSnapshot.task.isSuccessful) {
                val exception = taskSnapshot.task.exception
                Timber.e(exception, "Upload task failed for path: $path")
                throw LiftrixError.NetworkError(
                    errorMessage = "Upload failed: ${exception?.message ?: "Unknown error"}",
                    analyticsContext = mapOf(
                        "path" to path,
                        "error" to (exception?.message ?: "unknown")
                    )
                )
            }
            
            Timber.d("Image uploaded successfully to: $path.jpg")
            
            // CRITICAL READ PROBE: Verify we have read permissions
            // This will fail with StorageException (403) if read isn't allowed
            try {
                val metadata = storageRef.metadata.await()
                Timber.d("READ-CHECK: Metadata accessible - size=${metadata.sizeBytes} bytes, contentType=${metadata.contentType}")
            } catch (e: Exception) {
                val storageException = e as? StorageException
                if (storageException != null) {
                    Timber.e(
                        e,
                        "READ-CHECK FAILED: You can write but cannot read this object. " +
                        "Storage rules issue! Code=%s, HTTP=%s",
                        storageException.errorCode,
                        storageException.httpResultCode
                    )
                    
                    // Provide clear error message about the rules issue
                    throw LiftrixError.AuthenticationError(
                        errorMessage = "Storage configuration error: Upload succeeded but cannot access the uploaded file. " +
                                      "Please update Firebase Storage rules to allow read access for workout_images.",
                        analyticsContext = mapOf(
                            "storage_path" to storageRef.path,
                            "error_code" to storageException.errorCode.toString(),
                            "http_code" to storageException.httpResultCode.toString(),
                            "upload_success" to "true",
                            "read_permission" to "false"
                        )
                    )
                } else {
                    Timber.e(e, "READ-CHECK failed with unexpected error")
                    throw e
                }
            }
            
            // Get download URL using robust retry mechanism
            val downloadUrl = try {
                // Use the storage reference from the task snapshot for better reliability
                val urlRef = taskSnapshot.storage
                getDownloadUrlWithBackoff(urlRef)
            } catch (e: LiftrixError) {
                // Re-throw LiftrixErrors as-is
                throw e
            } catch (e: Exception) {
                // Wrap other exceptions
                Timber.e(e, "Failed to get download URL after upload")
                throw LiftrixError.NetworkError(
                    errorMessage = "Image uploaded but could not retrieve download URL. Storage rules may need updating.",
                    analyticsContext = mapOf(
                        "path" to path,
                        "upload_success" to "true",
                        "error_type" to e.javaClass.simpleName,
                        "error_message" to (e.message ?: "unknown")
                    )
                )
            }
            
            Timber.d("Upload complete with download URL: $downloadUrl")
            downloadUrl
        }
    }
}
