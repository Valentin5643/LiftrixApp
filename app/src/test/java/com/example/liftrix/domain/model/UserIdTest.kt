package com.example.liftrix.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for UserId inline value class.
 *
 * Tests verify:
 * - Creation with valid values
 * - Validation of blank/empty values
 * - Nullable conversion behavior
 * - Value extraction
 */
class UserIdTest {

    @Test
    fun `UserId creation with valid Firebase UID succeeds`() {
        // Given
        val validUid = "valid-firebase-uid-12345"

        // When
        val userId = UserId(validUid)

        // Then
        assertEquals(validUid, userId.value)
    }

    @Test
    fun `UserId creation with valid alphanumeric UID succeeds`() {
        // Given
        val validUid = "abc123XYZ789"

        // When
        val userId = UserId(validUid)

        // Then
        assertEquals(validUid, userId.value)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `UserId creation with blank value throws exception`() {
        // When - Then (exception expected)
        UserId("")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `UserId creation with whitespace-only value throws exception`() {
        // When - Then (exception expected)
        UserId("   ")
    }

    @Test
    fun `fromNullable returns UserId for valid non-null value`() {
        // Given
        val validUid = "test-uid-123"

        // When
        val userId = UserId.fromNullable(validUid)

        // Then
        assertNotNull(userId)
        assertEquals(validUid, userId?.value)
    }

    @Test
    fun `fromNullable returns null for null input`() {
        // When
        val userId = UserId.fromNullable(null)

        // Then
        assertNull(userId)
    }

    @Test
    fun `fromNullable returns null for blank input`() {
        // When
        val userId = UserId.fromNullable("")

        // Then
        assertNull(userId)
    }

    @Test
    fun `fromNullable returns null for whitespace input`() {
        // When
        val userId = UserId.fromNullable("   ")

        // Then
        assertNull(userId)
    }

    @Test
    fun `fromStringOrNull returns UserId for valid non-null value`() {
        // Given
        val validUid = "test-uid-456"

        // When
        val userId = UserId.fromStringOrNull(validUid)

        // Then
        assertNotNull(userId)
        assertEquals(validUid, userId?.value)
    }

    @Test
    fun `fromStringOrNull returns null for null input`() {
        // When
        val userId = UserId.fromStringOrNull(null)

        // Then
        assertNull(userId)
    }

    @Test
    fun `fromStringOrNull returns null for blank input`() {
        // When
        val userId = UserId.fromStringOrNull("")

        // Then
        assertNull(userId)
    }

    @Test
    fun `fromString creates UserId from valid string`() {
        // Given
        val validUid = "test-uid-789"

        // When
        val userId = UserId.fromString(validUid)

        // Then
        assertEquals(validUid, userId.value)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `fromString throws exception for blank input`() {
        // When - Then (exception expected)
        UserId.fromString("")
    }

    @Test
    fun `toString returns underlying value`() {
        // Given
        val validUid = "test-uid-toString"
        val userId = UserId(validUid)

        // When
        val stringValue = userId.toString()

        // Then
        assertEquals(validUid, stringValue)
    }

    @Test
    fun `UserId instances with same value are equal`() {
        // Given
        val uid = "same-uid-123"
        val userId1 = UserId(uid)
        val userId2 = UserId(uid)

        // Then
        assertEquals(userId1, userId2)
        assertEquals(userId1.hashCode(), userId2.hashCode())
    }

    @Test
    fun `UserId extraction from value works correctly`() {
        // Given
        val uid = "firebase-uid-abc123"

        // When
        val userId = UserId(uid)
        val extractedValue = userId.value

        // Then
        assertEquals(uid, extractedValue)
    }

    @Test
    fun `UserId with complex Firebase UID format succeeds`() {
        // Given - Real Firebase UID format
        val firebaseUid = "AbCd1234EfGh5678IjKl9012MnOp3456"

        // When
        val userId = UserId(firebaseUid)

        // Then
        assertEquals(firebaseUid, userId.value)
    }
}
