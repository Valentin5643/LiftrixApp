package com.example.liftrix.sync

interface ProgressStatsSyncRepository {
    suspend fun getPendingSyncCalculations(userId: String): List<AnalyticsCalculation>
    suspend fun markCalculationsAsSynced(userId: String, calculationIds: List<String>)
    suspend fun queueCalculationForSync(calculation: AnalyticsCalculation)
    suspend fun getUnsyncedCalculationsCount(userId: String): Int
}

suspend fun ProgressStatsSyncRepository.markCalculationsClean(
    userId: String,
    calculationIds: List<String>
) {
    markCalculationsAsSynced(userId, calculationIds)
}
