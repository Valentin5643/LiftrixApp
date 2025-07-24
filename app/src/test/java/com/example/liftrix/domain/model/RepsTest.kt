package com.example.liftrix.domain.model

import org.junit.Assert.*
import org.junit.Test

class RepsTest {

    @Test
    fun `of should create reps with valid count`() {
        val reps = Reps.of(10)
        
        assertEquals(10, reps.count)
    }

    @Test
    fun `reps with negative count should throw exception`() {
        assertThrows(IllegalArgumentException::class.java) {
            Reps.of(-1)
        }
    }

    @Test
    fun `reps with zero count should be allowed`() {
        val reps = Reps.of(0)
        
        assertEquals(0, reps.count)
    }

    @Test
    fun `reps with max count should be allowed`() {
        val reps = Reps.of(Reps.MAX_REPS)
        
        assertEquals(Reps.MAX_REPS, reps.count)
    }

    @Test
    fun `reps exceeding max count should throw exception`() {
        assertThrows(IllegalArgumentException::class.java) {
            Reps.of(Reps.MAX_REPS + 1)
        }
    }

    @Test
    fun `addition should work correctly`() {
        val reps1 = Reps.of(5)
        val reps2 = Reps.of(3)
        
        val result = reps1 + reps2
        
        assertEquals(8, result.count)
    }

    @Test
    fun `subtraction should work correctly`() {
        val reps1 = Reps.of(10)
        val reps2 = Reps.of(3)
        
        val result = reps1 - reps2
        
        assertEquals(7, result.count)
    }

    @Test
    fun `subtraction resulting in negative should throw exception`() {
        val reps1 = Reps.of(3)
        val reps2 = Reps.of(5)
        
        assertThrows(IllegalArgumentException::class.java) {
            reps1 - reps2
        }
    }

    @Test
    fun `multiplication should work correctly`() {
        val reps = Reps.of(5)
        
        val result = reps * 3
        
        assertEquals(15, result.count)
    }

    @Test
    fun `multiplication by zero should result in zero reps`() {
        val reps = Reps.of(10)
        
        val result = reps * 0
        
        assertEquals(0, result.count)
    }

    @Test
    fun `multiplication by negative should throw exception`() {
        val reps = Reps.of(5)
        
        assertThrows(IllegalArgumentException::class.java) {
            reps * -2
        }
    }

    @Test
    fun `division should work correctly`() {
        val reps = Reps.of(10)
        
        val result = reps / 2
        
        assertEquals(5, result.count)
    }

    @Test
    fun `division by zero should throw exception`() {
        val reps = Reps.of(10)
        
        assertThrows(IllegalArgumentException::class.java) {
            reps / 0
        }
    }

    @Test
    fun `division by negative should throw exception`() {
        val reps = Reps.of(10)
        
        assertThrows(IllegalArgumentException::class.java) {
            reps / -2
        }
    }

    @Test
    fun `compareTo should work correctly`() {
        val reps1 = Reps.of(5)
        val reps2 = Reps.of(10)
        val reps3 = Reps.of(5)
        
        assertTrue(reps1 < reps2)
        assertTrue(reps2 > reps1)
        assertEquals(0, reps1.compareTo(reps3))
    }

    @Test
    fun `ZERO constant should be zero reps`() {
        assertEquals(0, Reps.ZERO.count)
    }

    @Test
    fun `toString should format correctly`() {
        val reps = Reps.of(10)
        
        assertEquals("10 reps", reps.toString())
    }

    @Test
    fun `toString should format correctly for singular`() {
        val reps = Reps.of(1)
        
        assertEquals("1 rep", reps.toString())
    }

    @Test
    fun `toString should format correctly for zero`() {
        val reps = Reps.ZERO
        
        assertEquals("0 reps", reps.toString())
    }

    @Test
    fun `isZero should return true for zero reps`() {
        assertTrue(Reps.ZERO.isZero())
    }

    @Test
    fun `isZero should return false for non-zero reps`() {
        val reps = Reps.of(5)
        
        assertFalse(reps.isZero())
    }

    @Test
    fun `equals should work correctly`() {
        val reps1 = Reps.of(10)
        val reps2 = Reps.of(10)
        val reps3 = Reps.of(5)
        
        assertEquals(reps1, reps2)
        assertNotEquals(reps1, reps3)
    }

    @Test
    fun `hashCode should be consistent`() {
        val reps1 = Reps.of(10)
        val reps2 = Reps.of(10)
        
        assertEquals(reps1.hashCode(), reps2.hashCode())
    }

    @Test
    fun `increment should add one rep`() {
        val reps = Reps.of(9)
        
        val result = reps.increment()
        
        assertEquals(10, result.count)
    }

    @Test
    fun `decrement should subtract one rep`() {
        val reps = Reps.of(10)
        
        val result = reps.decrement()
        
        assertEquals(9, result.count)
    }

    @Test
    fun `decrement from zero should throw exception`() {
        assertThrows(IllegalArgumentException::class.java) {
            Reps.ZERO.decrement()
        }
    }

    @Test
    fun `range function should create correct range`() {
        val startReps = Reps.of(5)
        val endReps = Reps.of(10)
        
        val range = startReps..endReps
        
        assertTrue(Reps.of(7) in range)
        assertTrue(Reps.of(5) in range)
        assertTrue(Reps.of(10) in range)
        assertFalse(Reps.of(4) in range)
        assertFalse(Reps.of(11) in range)
    }
} 