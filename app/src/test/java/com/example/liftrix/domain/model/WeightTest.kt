package com.example.liftrix.domain.model

import org.junit.Assert.*
import org.junit.Test

class WeightTest {

    @Test
    fun `fromKilograms should create weight with correct value`() {
        val weight = Weight.fromKilograms(80.5)
        
        assertEquals(80.5, weight.kilograms, 0.01)
    }

    @Test
    fun `fromPounds should create weight with correct conversion`() {
        val weight = Weight.fromPounds(176.37)
        
        assertEquals(80.0, weight.kilograms, 0.01)
    }

    @Test
    fun `pounds property should return correct conversion`() {
        val weight = Weight.fromKilograms(80.0)
        
        assertEquals(176.37, weight.pounds, 0.01)
    }

    @Test
    fun `weight with negative value should throw exception`() {
        assertThrows(IllegalArgumentException::class.java) {
            Weight.fromKilograms(-10.0)
        }
    }

    @Test
    fun `weight with zero value should be allowed`() {
        val weight = Weight.fromKilograms(0.0)
        
        assertEquals(0.0, weight.kilograms, 0.01)
    }

    @Test
    fun `weight with max value should be allowed`() {
        val weight = Weight.fromKilograms(Weight.MAX_WEIGHT_KG)
        
        assertEquals(Weight.MAX_WEIGHT_KG, weight.kilograms, 0.01)
    }

    @Test
    fun `weight exceeding max value should throw exception`() {
        assertThrows(IllegalArgumentException::class.java) {
            Weight.fromKilograms(Weight.MAX_WEIGHT_KG + 1)
        }
    }

    @Test
    fun `addition should work correctly`() {
        val weight1 = Weight.fromKilograms(50.0)
        val weight2 = Weight.fromKilograms(30.0)
        
        val result = weight1 + weight2
        
        assertEquals(80.0, result.kilograms, 0.01)
    }

    @Test
    fun `subtraction should work correctly`() {
        val weight1 = Weight.fromKilograms(80.0)
        val weight2 = Weight.fromKilograms(30.0)
        
        val result = weight1 - weight2
        
        assertEquals(50.0, result.kilograms, 0.01)
    }

    @Test
    fun `multiplication should work correctly`() {
        val weight = Weight.fromKilograms(40.0)
        
        val result = weight * 2
        
        assertEquals(80.0, result.kilograms, 0.01)
    }

    @Test
    fun `division should work correctly`() {
        val weight = Weight.fromKilograms(80.0)
        
        val result = weight / 2
        
        assertEquals(40.0, result.kilograms, 0.01)
    }

    @Test
    fun `compareTo should work correctly`() {
        val weight1 = Weight.fromKilograms(50.0)
        val weight2 = Weight.fromKilograms(80.0)
        val weight3 = Weight.fromKilograms(50.0)
        
        assertTrue(weight1 < weight2)
        assertTrue(weight2 > weight1)
        assertEquals(0, weight1.compareTo(weight3))
    }

    @Test
    fun `ZERO constant should be zero weight`() {
        assertEquals(0.0, Weight.ZERO.kilograms, 0.01)
    }

    @Test
    fun `toString should format correctly for kilograms`() {
        val weight = Weight.fromKilograms(80.5)
        
        assertEquals("80.5 kg", weight.toString())
    }

    @Test
    fun `toString should format correctly for zero weight`() {
        val weight = Weight.ZERO
        
        assertEquals("0.0 kg", weight.toString())
    }

    @Test
    fun `isZero should return true for zero weight`() {
        assertTrue(Weight.ZERO.isZero())
    }

    @Test
    fun `isZero should return false for non-zero weight`() {
        val weight = Weight.fromKilograms(80.0)
        
        assertFalse(weight.isZero())
    }

    @Test
    fun `equals should work correctly`() {
        val weight1 = Weight.fromKilograms(80.0)
        val weight2 = Weight.fromKilograms(80.0)
        val weight3 = Weight.fromKilograms(75.0)
        
        assertEquals(weight1, weight2)
        assertNotEquals(weight1, weight3)
    }

    @Test
    fun `hashCode should be consistent`() {
        val weight1 = Weight.fromKilograms(80.0)
        val weight2 = Weight.fromKilograms(80.0)
        
        assertEquals(weight1.hashCode(), weight2.hashCode())
    }
} 