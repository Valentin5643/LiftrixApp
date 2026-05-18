package com.example.liftrix.service

import com.example.liftrix.widget.LiftrixWidgetUpdateScheduler
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HomeWidgetUpdateNotifierImpl @Inject constructor(
    private val scheduler: LiftrixWidgetUpdateScheduler
) : HomeWidgetUpdateNotifier {
    override suspend fun enqueueWorkoutCompletedRefresh(userId: String) {
        scheduler.enqueuePostWorkoutRefresh(userId)
    }
}
