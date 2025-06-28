package com.example.liftrix.di

import android.app.NotificationManager
import android.content.Context
import org.junit.Test
import org.junit.Assert.*
import org.mockito.Mockito.*

/**
 * Unit tests for TimerModule dependency injection.
 */
class TimerModuleTest {

    @Test
    fun `provideNotificationManager returns NotificationManager`() {
        // Arrange
        val mockContext = mock(Context::class.java)
        val mockNotificationManager = mock(NotificationManager::class.java)
        `when`(mockContext.getSystemService(Context.NOTIFICATION_SERVICE))
            .thenReturn(mockNotificationManager)
        
        val timerModule = TimerModule
        
        // Act
        val result = timerModule.provideNotificationManager(mockContext)
        
        // Assert
        assertNotNull("NotificationManager should not be null", result)
        assertEquals("Should return the mocked NotificationManager", mockNotificationManager, result)
        verify(mockContext).getSystemService(Context.NOTIFICATION_SERVICE)
    }

    @Test
    fun `provideNotificationManager throws exception for null service`() {
        // Arrange
        val mockContext = mock(Context::class.java)
        `when`(mockContext.getSystemService(Context.NOTIFICATION_SERVICE))
            .thenReturn(null)
        
        val timerModule = TimerModule
        
        // Act & Assert
        try {
            timerModule.provideNotificationManager(mockContext)
            fail("Should throw ClassCastException for null service")
        } catch (e: ClassCastException) {
            // Expected behavior
        } catch (e: NullPointerException) {
            // Also acceptable
        }
        
        verify(mockContext).getSystemService(Context.NOTIFICATION_SERVICE)
    }
} 