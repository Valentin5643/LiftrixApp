package com.example.liftrix.domain.service

import com.example.liftrix.domain.model.common.LiftrixResult

/**
 * Service interface for processing notification batches.
 * Part of notification system from SPEC-20250113-notifications-privacy.
 */
interface BatchProcessor {

    /**
     * Processes all pending batches for a user that are ready for delivery
     */
    suspend fun processBatch(userId: String): LiftrixResult<ProcessingResult>

    /**
     * Schedules batch processing after a delay
     */
    suspend fun scheduleProcessing(userId: String, delayMillis: Long): LiftrixResult<Unit>

    /**
     * Processes a specific batch by batch key
     */
    suspend fun processSpecificBatch(
        userId: String,
        batchKey: String
    ): LiftrixResult<ProcessingResult>

    /**
     * Gets batch statistics for a user
     */
    suspend fun getBatchStatistics(userId: String): LiftrixResult<BatchStatistics>

    /**
     * Cancels scheduled batch processing for a user
     */
    suspend fun cancelScheduledProcessing(userId: String): LiftrixResult<Unit>

    data class ProcessingResult(
        val processed: Int,
        val delivered: Int,
        val queued: Int,
        val failed: Int,
        val batchesProcessed: Int = 0,
        val errors: List<String> = emptyList()
    )

    data class BatchStatistics(
        val totalBatches: Int,
        val pendingBatches: Int,
        val averageBatchSize: Double,
        val oldestPendingBatch: Long?,
        val mostRecentBatch: Long?
    )
}