package com.example.liftrix.util

import com.example.liftrix.data.mock.ProgressMockDataGenerator
import com.example.liftrix.domain.model.progress.DurationDataPoint
import com.example.liftrix.domain.model.progress.FrequencyDataPoint
import com.example.liftrix.domain.model.progress.ProgressSummary
import com.example.liftrix.domain.model.progress.VolumeDataPoint
import java.time.LocalDate

/**
 * Enhanced test data factory that provides access to both existing test utilities
 * and the extracted mock data generators for comprehensive testing.
 */
object TestDataFactory {
    
    /**
     * Generate mock volume data using the extracted generator
     */
    fun createMockVolumeData(
        startDate: LocalDate = LocalDate.now().minusDays(30),
        endDate: LocalDate = LocalDate.now()
    ): List<VolumeDataPoint> {
        return ProgressMockDataGenerator.generateMockVolumeData(startDate, endDate)
    }
    
    /**
     * Generate mock duration data using the extracted generator
     */
    fun createMockDurationData(
        startDate: LocalDate = LocalDate.now().minusDays(30),
        endDate: LocalDate = LocalDate.now()
    ): List<DurationDataPoint> {
        return ProgressMockDataGenerator.generateMockDurationData(startDate, endDate)
    }
    
    /**
     * Generate mock frequency data using the extracted generator
     */
    fun createMockFrequencyData(
        startDate: LocalDate = LocalDate.now().minusDays(30),
        endDate: LocalDate = LocalDate.now()
    ): List<FrequencyDataPoint> {
        return ProgressMockDataGenerator.generateMockFrequencyData(startDate, endDate)
    }
    
    /**
     * Generate mock progress summary using the extracted generator
     */
    fun createMockProgressSummary(): ProgressSummary {
        return ProgressMockDataGenerator.generateMockProgressSummary()
    }
    
    /**
     * Create empty progress data for testing edge cases
     */
    fun createEmptyProgressSummary(): ProgressSummary {
        return ProgressSummary(
            totalWorkouts = 0,
            totalVolume = 0f,
            averageDuration = 0,
            currentStreak = 0,
            longestStreak = 0,
            averageWorkoutsPerWeek = 0f,
            totalActiveTime = 0
        )
    }
}