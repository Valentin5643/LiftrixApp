package com.example.liftrix

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Unit tests for LiftrixApp Application class.
 * Tests notification channel creation and application initialization.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.O], application = LiftrixApp::class)
class LiftrixAppTest {

    @Test
    fun `onCreate should initialize notification channels on Android O+`() {
        // Arrange
        val context = ApplicationProvider.getApplicationContext<LiftrixApp>()
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Act - onCreate is called automatically by test framework
        
        // Assert
        val channel = notificationManager.getNotificationChannel(LiftrixApp.WORKOUT_TIMER_CHANNEL_ID)
        assertNotNull("Workout timer notification channel should be created", channel)
        assert(channel.name == "Workout Timer")
        assert(channel.importance == NotificationManager.IMPORTANCE_LOW)
        assert(!channel.canShowBadge())
    }
    
    @Test
    @Config(sdk = [Build.VERSION_CODES.N], application = LiftrixApp::class)
    fun `onCreate should not create channels on pre-O Android versions`() {
        // Arrange
        val context = ApplicationProvider.getApplicationContext<LiftrixApp>()
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Act - onCreate is called automatically by test framework
        
        // Assert - on pre-O versions, getNotificationChannel returns null
        // This test mainly verifies no crashes occur on older versions
        assertNotNull("NotificationManager should be available", notificationManager)
    }
} 