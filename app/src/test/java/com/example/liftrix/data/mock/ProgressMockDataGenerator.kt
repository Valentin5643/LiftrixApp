package com.example.liftrix.data.mock

import com.example.liftrix.domain.model.progress.DurationDataPoint
import com.example.liftrix.domain.model.progress.FrequencyDataPoint
import com.example.liftrix.domain.model.progress.ProgressSummary
import com.example.liftrix.domain.model.progress.VolumeDataPoint
import java.time.LocalDate
import kotlin.random.Random

/**
 * Utility class for generating mock progress data for testing purposes.
 * Extracted from production code to maintain separation of concerns.
 */
object ProgressMockDataGenerator {
    
    /**
     * Generate mock volume data for demonstration and testing purposes
     */
    fun generateMockVolumeData(startDate: LocalDate, endDate: LocalDate): List<VolumeDataPoint> {
        val mockData = mutableListOf<VolumeDataPoint>()
        val random = Random(42) // Fixed seed for consistent mock data
        
        var currentDate = startDate
        var baseVolume = 1200f // Starting volume
        
        while (currentDate <= endDate) {
            // Simulate workout every 2-3 days with some randomness
            if (random.nextFloat() > 0.4f) {
                // Add some progression and variation
                val volumeVariation = random.nextFloat() * 400f - 200f // ±200kg variation
                val progressionBonus = ((currentDate.toEpochDays() - startDate.toEpochDays()) * 2f) // 2kg per day progression
                val totalVolume = (baseVolume + volumeVariation + progressionBonus).coerceAtLeast(800f)
                
                mockData.add(
                    VolumeDataPoint(
                        date = currentDate,
                        totalVolume = totalVolume,
                        exerciseCount = random.nextInt(4, 8) // 4-7 exercises per workout
                    )
                )
            }
            
            currentDate = LocalDate.fromEpochDays(currentDate.toEpochDays() + 1)
        }
        
        return mockData.sortedBy { it.date }
    }
    
    /**
     * Generate mock duration data for demonstration and testing purposes
     */
    fun generateMockDurationData(startDate: LocalDate, endDate: LocalDate): List<DurationDataPoint> {
        val mockData = mutableListOf<DurationDataPoint>()
        val random = Random(42) // Fixed seed for consistent mock data
        
        var currentDate = startDate
        val baseDuration = 75 // Base duration in minutes
        
        while (currentDate <= endDate) {
            // Simulate workout every 2-3 days with some randomness
            if (random.nextFloat() > 0.4f) {
                val durationVariation = random.nextInt(-20, 30) // ±20-30 minutes variation
                val duration = (baseDuration + durationVariation).coerceIn(45, 120) // 45-120 minutes
                
                mockData.add(
                    DurationDataPoint(
                        date = currentDate,
                        durationMinutes = duration,
                        workoutCount = 1
                    )
                )
            }
            
            currentDate = LocalDate.fromEpochDays(currentDate.toEpochDays() + 1)
        }
        
        return mockData.sortedBy { it.date }
    }
    
    /**
     * Generate mock frequency data for demonstration and testing purposes
     */
    fun generateMockFrequencyData(startDate: LocalDate, endDate: LocalDate): List<FrequencyDataPoint> {
        val mockData = mutableListOf<FrequencyDataPoint>()
        val random = Random(42) // Fixed seed for consistent mock data
        
        var currentDate = startDate
        
        while (currentDate <= endDate) {
            val workoutCount = when {
                random.nextFloat() > 0.7f -> 2 // 30% chance of 2 workouts
                random.nextFloat() > 0.4f -> 1 // 30% chance of 1 workout  
                else -> 0 // 40% chance of no workout
            }
            
            if (workoutCount > 0) {
                mockData.add(
                    FrequencyDataPoint(
                        date = currentDate,
                        workoutCount = workoutCount,
                        intensity = when (workoutCount) {
                            2 -> 1.0f // High intensity for 2 workouts
                            1 -> 0.6f // Medium intensity for 1 workout
                            else -> 0f
                        }
                    )
                )
            }
            
            currentDate = LocalDate.fromEpochDays(currentDate.toEpochDays() + 1)
        }
        
        return mockData.sortedBy { it.date }
    }
    
    /**
     * Generate mock progress summary for demonstration and testing purposes
     */
    fun generateMockProgressSummary(): ProgressSummary {
        return ProgressSummary(
            totalWorkouts = 28,
            totalVolume = 35420f, // Total volume in kg
            averageDuration = 78, // Average duration in minutes
            currentStreak = 3, // Current streak of workout days
            longestStreak = 7, // Longest streak of workout days
            averageWorkoutsPerWeek = 3.5f, // Average workouts per week
            totalActiveTime = 2184 // Total active time in minutes (36.4 hours)
        )
    }
}