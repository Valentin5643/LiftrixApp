package com.example.liftrix.domain.model.analytics

import com.example.liftrix.domain.model.Weight
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class VolumeDataPointTest {
    @Test
    fun fromKgDouble_preservesAggregateVolumeAboveWeightLimit() {
        val point = VolumeDataPoint.fromKgDouble(
            date = LocalDate(2026, 5, 26),
            volumeKg = 12_500.0,
            workoutCount = 2
        )

        assertEquals(Weight.MAX_WEIGHT_KG, point.volume.kilograms, 0.0)
        assertEquals(12_500.0, point.getVolumeAsDouble(), 0.0)
        assertEquals(12_500f, point.getVolumeInKg(), 0.0f)
    }

    @Test
    fun fromKgDouble_sanitizesInvalidAggregateVolume() {
        val point = VolumeDataPoint.fromKgDouble(
            date = LocalDate(2026, 5, 26),
            volumeKg = Double.POSITIVE_INFINITY
        )

        assertEquals(0.0, point.getVolumeAsDouble(), 0.0)
        assertEquals(0.0, point.volume.kilograms, 0.0)
    }
}
