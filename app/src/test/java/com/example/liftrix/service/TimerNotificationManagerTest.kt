package com.example.liftrix.service

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.liftrix.domain.model.RestTimer
import com.example.liftrix.service.WorkoutTimerService.TimerState
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.datetime.Clock
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class TimerNotificationManagerTest {

    private lateinit var context: Context
    private lateinit var notificationManager: NotificationManager
    private lateinit var timerNotificationManager: TimerNotificationManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        notificationManager = mockk(relaxed = true)
        timerNotificationManager = TimerNotificationManager(context, notificationManager)
    }

    @Test
    fun `createNotification creates valid notification for stopped state`() {
        val timerState = TimerState.Stopped
        
        val notification = timerNotificationManager.createNotification(timerState)
        
        assertNotNull(notification)
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
    }

    @Test
    fun `createNotification creates valid notification for session running state`() {
        val startTime = Clock.System.now()
        val timerState = TimerState.SessionRunning(startTime, 300L) // 5 minutes
        
        val notification = timerNotificationManager.createNotification(timerState)
        
        assertNotNull(notification)
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
    }

    @Test
    fun `createNotification creates valid notification for session paused state`() {
        val startTime = Clock.System.now()
        val timerState = TimerState.SessionPaused(startTime, 150L) // 2.5 minutes
        
        val notification = timerNotificationManager.createNotification(timerState)
        
        assertNotNull(notification)
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
    }

    @Test
    fun `createNotification creates valid notification for rest active state`() {
        val restTimer = RestTimer(durationSeconds = 60, isEnabled = true)
        val timerState = TimerState.RestActive(restTimer, 30)
        
        val notification = timerNotificationManager.createNotification(timerState)
        
        assertNotNull(notification)
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
    }

    @Test
    fun `createNotification creates valid notification for rest paused state`() {
        val restTimer = RestTimer(durationSeconds = 60, isEnabled = true)
        val timerState = TimerState.RestPaused(restTimer, 45)
        
        val notification = timerNotificationManager.createNotification(timerState)
        
        assertNotNull(notification)
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
    }

    @Test
    fun `updateNotification calls notify with correct parameters`() {
        val timerState = TimerState.Stopped
        
        timerNotificationManager.updateNotification(timerState)
        
        verify { notificationManager.notify(TimerNotificationManager.NOTIFICATION_ID, any()) }
    }

    @Test
    fun `clearNotification calls cancel with correct notification ID`() {
        timerNotificationManager.clearNotification()
        
        verify { notificationManager.cancel(TimerNotificationManager.NOTIFICATION_ID) }
    }

    @Test
    fun `notification actions are added correctly for session running state`() {
        val startTime = Clock.System.now()
        val timerState = TimerState.SessionRunning(startTime, 300L)
        
        val notification = timerNotificationManager.createNotification(timerState)
        
        assertNotNull(notification.actions)
        assertEquals(2, notification.actions.size) // Pause + Stop
        assertEquals("Pause", notification.actions[0].title)
        assertEquals("Stop", notification.actions[1].title)
    }

    @Test
    fun `notification actions are added correctly for session paused state`() {
        val startTime = Clock.System.now()
        val timerState = TimerState.SessionPaused(startTime, 150L)
        
        val notification = timerNotificationManager.createNotification(timerState)
        
        assertNotNull(notification.actions)
        assertEquals(2, notification.actions.size) // Resume + Stop
        assertEquals("Resume", notification.actions[0].title)
        assertEquals("Stop", notification.actions[1].title)
    }

    @Test
    fun `notification actions are added correctly for rest active state`() {
        val restTimer = RestTimer(durationSeconds = 60, isEnabled = true)
        val timerState = TimerState.RestActive(restTimer, 30)
        
        val notification = timerNotificationManager.createNotification(timerState)
        
        assertNotNull(notification.actions)
        assertEquals(3, notification.actions.size) // Pause + Skip Rest + Stop
        assertEquals("Pause", notification.actions[0].title)
        assertEquals("Skip Rest", notification.actions[1].title)
        assertEquals("Stop", notification.actions[2].title)
    }

    @Test
    fun `notification actions are added correctly for rest paused state`() {
        val restTimer = RestTimer(durationSeconds = 60, isEnabled = true)
        val timerState = TimerState.RestPaused(restTimer, 45)
        
        val notification = timerNotificationManager.createNotification(timerState)
        
        assertNotNull(notification.actions)
        assertEquals(3, notification.actions.size) // Resume + Skip Rest + Stop
        assertEquals("Resume", notification.actions[0].title)
        assertEquals("Skip Rest", notification.actions[1].title)
        assertEquals("Stop", notification.actions[2].title)
    }

    @Test
    fun `notification actions are added correctly for stopped state`() {
        val timerState = TimerState.Stopped
        
        val notification = timerNotificationManager.createNotification(timerState)
        
        assertNotNull(notification.actions)
        assertEquals(1, notification.actions.size) // Stop only
        assertEquals("Stop", notification.actions[0].title)
    }

    @Test
    fun `notification has correct title for different states`() {
        val stoppedNotification = timerNotificationManager.createNotification(TimerState.Stopped)
        assertEquals("Workout Timer", getNotificationTitle(stoppedNotification))

        val sessionNotification = timerNotificationManager.createNotification(
            TimerState.SessionRunning(Clock.System.now(), 300L)
        )
        assertEquals("Workout Session", getNotificationTitle(sessionNotification))

        val restTimer = RestTimer(durationSeconds = 60, isEnabled = true)
        val restNotification = timerNotificationManager.createNotification(
            TimerState.RestActive(restTimer, 30)
        )
        assertEquals("Rest Timer", getNotificationTitle(restNotification))
    }

    @Test
    fun `notification time formatting works correctly`() {
        // Test short duration (MM:SS format)
        val shortSession = TimerState.SessionRunning(Clock.System.now(), 150L) // 2:30
        val shortNotification = timerNotificationManager.createNotification(shortSession)
        assertTrue(getNotificationText(shortNotification).contains("2:30"))

        // Test long duration (H:MM:SS format)  
        val longSession = TimerState.SessionRunning(Clock.System.now(), 3900L) // 1:05:00
        val longNotification = timerNotificationManager.createNotification(longSession)
        assertTrue(getNotificationText(longNotification).contains("1:05:00"))
    }

    @Test
    fun `notification is configured as ongoing and silent`() {
        val timerState = TimerState.SessionRunning(Clock.System.now(), 300L)
        
        val notification = timerNotificationManager.createNotification(timerState)
        
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertTrue(notification.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
    }

    // Helper methods to extract notification content for testing
    private fun getNotificationTitle(notification: Notification): String {
        return notification.extras.getString(Notification.EXTRA_TITLE) ?: ""
    }

    private fun getNotificationText(notification: Notification): String {
        return notification.extras.getString(Notification.EXTRA_TEXT) ?: ""
    }
}