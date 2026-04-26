package com.example.liftrix.domain.repository

/**
 * Room-first compatibility helpers for analytics sync.
 */
suspend fun ProgressStatsRepository.markCalculationsClean(
    userId: String,
    calculationIds: List<String>
) {
    markCalculationsAsSynced(userId, calculationIds)
}
