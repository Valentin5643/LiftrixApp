package com.example.liftrix.service

interface HomeWidgetUpdateNotifier {
    suspend fun enqueueWorkoutCompletedRefresh(userId: String)
}
