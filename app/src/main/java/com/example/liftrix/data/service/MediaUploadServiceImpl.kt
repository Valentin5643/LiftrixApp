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
import com.google.firebase.storage.FirebaseStorage
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
    private val storage: FirebaseStorage
) : MediaUploadService {

    private val uploadTasks = mutableMapOf<String, com.google.firebase.storage.UploadTask>()

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
            request.uri,
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
            request.uri,
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
            LiftrixError.NetworkError(
                errorMessage = "Failed to upload profile image: ${throwable.message}",
                analyticsContext = mapOf("user_id" to userId)
            )
        }
    ) {
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
}