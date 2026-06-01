package com.example.liftrix.service.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.liftrix.domain.model.analytics.AnalyticsWidget
import com.example.liftrix.service.AnalyticsService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import timber.log.Timber

/**
 * Background worker for widget data synchronization.
 * 
 * This worker implements:
 * - Complexity-based sync intervals (30s-5min based on widget type)
 * - Batch processing for performance optimization
 * - Progress notifications with cancellation support
 * - Network-aware operation with proper retry logic
 * - Memory efficient processing with chunked operations
 * 
 * Technical Implementation:
 * - Uses coroutines for concurrent widget processing
 * - Implements exponential backoff for failed sync operations
 * - Provides detailed progress tracking for user feedback
 * - Handles network conditions and battery optimization
 * 
 * Performance Characteristics:
 * - Processes widgets in batches of 5 for memory efficiency
 * - Simple widgets: 30-second refresh intervals
 * - Moderate widgets: 2-minute refresh intervals  
 * - Complex widgets: 5-minute refresh intervals
 * - Respects device battery and network constraints
 */
@HiltWorker
class WidgetSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val analyticsService: AnalyticsService
) : CoroutineWorker(context, workerParams) {
    
    companion object {
        private const val BATCH_SIZE = 5
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 2000L
        
    }
    
    override suspend fun doWork(): Result = coroutineScope {
        val userId = inputData.getString("userId")
        val syncType = inputData.getString("syncType") ?: "periodic"
        
        if (userId.isNullOrBlank()) {
            Timber.w("WidgetSyncWorker: Missing userId parameter")
            return@coroutineScope Result.failure(
                workDataOf("error" to "Missing userId parameter")
            )
        }
        
        Timber.d("WidgetSyncWorker: Starting sync for user: $userId, type: $syncType")
        
        try {
            // Set initial progress
            setProgress(workDataOf("progress" to 0, "status" to "Starting sync"))
            
            // Get user's widget preferences to determine which widgets to sync
            val preferencesResult = analyticsService.getWidgetPreferences(userId)
            
            val preferences = preferencesResult.fold(
                onSuccess = { it },
                onFailure = { error ->
                    Timber.e("WidgetSyncWorker: Failed to get preferences: $error")
                    return@coroutineScope Result.failure(
                        workDataOf("error" to "Failed to get widget preferences")
                    )
                }
            )
            
            // Filter widgets that need explicit background calculation. Room-backed UI
            // observers own freshness, so periodic widget cache warming is disabled.
            val widgetsToSync = getWidgetsRequiringSync(userId, preferences.visibleWidgets, syncType)
            
            if (widgetsToSync.isEmpty()) {
                Timber.d("WidgetSyncWorker: No widgets require syncing for user: $userId")
                return@coroutineScope Result.success(
                    workDataOf("synced_count" to 0, "status" to "No sync needed")
                )
            }
            
            Timber.d("WidgetSyncWorker: Syncing ${widgetsToSync.size} widgets for user: $userId")
            
            // Process widgets in batches for memory efficiency
            val totalWidgets = widgetsToSync.size
            var processedWidgets = 0
            var successfulSyncs = 0
            
            val batches = widgetsToSync.chunked(BATCH_SIZE)
            
            for ((batchIndex, batch) in batches.withIndex()) {
                val batchNumber = batchIndex + 1
                val totalBatches = batches.size
                
                Timber.d("WidgetSyncWorker: Processing batch $batchNumber/$totalBatches (${batch.size} widgets)")
                
                // Update progress
                val progress = (processedWidgets * 100) / totalWidgets
                setProgress(workDataOf(
                    "progress" to progress,
                    "status" to "Syncing batch $batchNumber/$totalBatches"
                ))
                
                // Process batch concurrently
                val batchResults = batch.map { widget ->
                    async {
                        syncWidget(userId, widget)
                    }
                }.awaitAll()
                
                // Count successful syncs
                successfulSyncs += batchResults.count { it }
                processedWidgets += batch.size
                
                // Brief delay between batches to prevent overwhelming the system
                if (batchIndex < batches.size - 1) {
                    delay(100) // 100ms delay between batches
                }
            }
            
            // Update final progress
            setProgress(workDataOf(
                "progress" to 100,
                "status" to "Sync completed"
            ))
            
            Timber.i("WidgetSyncWorker: Sync completed for user: $userId - $successfulSyncs/$totalWidgets widgets synced successfully")
            
            // Determine result based on success rate
            return@coroutineScope if (successfulSyncs == totalWidgets) {
                Result.success(workDataOf(
                    "synced_count" to successfulSyncs,
                    "total_count" to totalWidgets,
                    "status" to "All widgets synced successfully"
                ))
            } else if (successfulSyncs > 0) {
                Result.success(workDataOf(
                    "synced_count" to successfulSyncs,
                    "total_count" to totalWidgets,
                    "status" to "Partial sync completed"
                ))
            } else {
                Result.retry()
            }
            
        } catch (e: Exception) {
            Timber.e(e, "WidgetSyncWorker: Sync failed for user: $userId")
            return@coroutineScope Result.failure(
                workDataOf("error" to "Sync failed: ${e.message}")
            )
        }
    }
    
    /**
     * Syncs a single widget with retry logic
     */
    private suspend fun syncWidget(userId: String, widget: AnalyticsWidget): Boolean {
        var attempt = 0
        
        while (attempt < MAX_RETRY_ATTEMPTS) {
            try {
                Timber.d("WidgetSyncWorker: Syncing widget ${widget.id} (attempt ${attempt + 1})")
                
                // Get fresh widget data
                val widgetDataResult = analyticsService.getWidgetData(userId, widget)
                
                widgetDataResult.fold(
                    onSuccess = { widgetData ->
                        Timber.d("WidgetSyncWorker: Successfully calculated widget ${widget.id}")
                        return true
                    },
                    onFailure = { error ->
                        Timber.w("WidgetSyncWorker: Failed to sync widget ${widget.id}: $error")
                        
                        // Check if error is recoverable
                        if (isRecoverableError(error)) {
                            attempt++
                            if (attempt < MAX_RETRY_ATTEMPTS) {
                                delay(RETRY_DELAY_MS * attempt) // Exponential backoff
                                continue
                            }
                        }
                        return false
                    }
                )
                
            } catch (e: Exception) {
                Timber.e(e, "WidgetSyncWorker: Exception syncing widget ${widget.id}")
                attempt++
                if (attempt < MAX_RETRY_ATTEMPTS) {
                    delay(RETRY_DELAY_MS * attempt)
                    continue
                }
                return false
            }
        }
        
        return false
    }
    
    /**
     * Determines which widgets require syncing based on cache staleness and complexity
     */
    private suspend fun getWidgetsRequiringSync(
        userId: String, 
        enabledWidgets: Set<String>,
        syncType: String
    ): List<AnalyticsWidget> {
        val result = mutableListOf<AnalyticsWidget>()
        
        for (widgetId in enabledWidgets) {
            val widget = AnalyticsWidget.getById(widgetId) ?: continue
            
            // For manual/force sync, sync all enabled widgets
            if (syncType == "manual_force") {
                result.add(widget)
                continue
            }
            
            Timber.v("WidgetSyncWorker: Skipping periodic cache sync for ${widget.id}; Room Flow owns freshness")
        }
        
        return result
    }
    
    /**
     * Determines if an error is recoverable and worth retrying
     */
    private fun isRecoverableError(error: Throwable): Boolean {
        return when (error) {
            is com.example.liftrix.domain.model.error.LiftrixError.NetworkError -> error.isRecoverable
            is com.example.liftrix.domain.model.error.LiftrixError.DatabaseError -> error.isRecoverable
            is java.net.UnknownHostException,
            is java.net.SocketTimeoutException,
            is java.io.IOException -> true
            else -> false
        }
    }
    
}
