package com.example.liftrix.domain.validation

import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.usecase.social.ProfileValidator
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertTrue

/**
 * Unit tests for ProfileValidator.
 * Tests validation rules for social profile fields.
 * Part of social infrastructure testing from SPEC-20250113-social-infrastructure.
 */
class ProfileValidatorTest {

    private lateinit var validator: ProfileValidator

    @Before
    fun setup() {
        validator = ProfileValidator()
    }

    // Username validation tests
    @Test
    fun `validateUsername succeeds with valid usernames`() = runTest {
        val validUsernames = listOf(
            "user123",
            "test_user",
            "UserName",
            "a".repeat(3), // minimum length
            "a".repeat(20) // maximum length
        )

        validUsernames.forEach { username ->
            val result = validator.validateUsername(username)
            assertTrue(result.isSuccess, "Username '$username' should be valid")
        }
    }

    @Test
    fun `validateUsername fails with blank username`() = runTest {
        val invalidUsernames = listOf("", "   ", "\t", "\n")

        invalidUsernames.forEach { username ->
            val result = validator.validateUsername(username)
            assertTrue(result.isFailure)
            val error = result.exceptionOrNull() as LiftrixError.ValidationError
            assertTrue(error.errorMessage.contains("blank"))
        }
    }

    @Test
    fun `validateUsername fails with too short username`() = runTest {
        val shortUsernames = listOf("a", "ab", "12")

        shortUsernames.forEach { username ->
            val result = validator.validateUsername(username)
            assertTrue(result.isFailure)
            val error = result.exceptionOrNull() as LiftrixError.ValidationError
            assertTrue(error.errorMessage.contains("3 characters"))
        }
    }

    @Test
    fun `validateUsername fails with too long username`() = runTest {
        val longUsername = "a".repeat(21)

        val result = validator.validateUsername(longUsername)
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull() as LiftrixError.ValidationError
        assertTrue(error.errorMessage.contains("20 characters"))
    }

    @Test
    fun `validateUsername fails with invalid characters`() = runTest {
        val invalidUsernames = listOf(
            "user@name",
            "user-name",
            "user.name",
            "user name",
            "user#name",
            "user!name",
            "user+name",
            "user=name"
        )

        invalidUsernames.forEach { username ->
            val result = validator.validateUsername(username)
            assertTrue(result.isFailure, "Username '$username' should be invalid")
            val error = result.exceptionOrNull() as LiftrixError.ValidationError
            assertTrue(error.errorMessage.contains("letters, numbers, and underscores"))
        }
    }

    @Test
    fun `validateUsername allows underscores and mixed case`() = runTest {
        val validUsernames = listOf(
            "user_name",
            "User_Name_123",
            "_username",
            "username_",
            "user__name"
        )

        validUsernames.forEach { username ->
            val result = validator.validateUsername(username)
            assertTrue(result.isSuccess, "Username '$username' should be valid")
        }
    }

    // Display name validation tests
    @Test
    fun `validateDisplayName succeeds with valid names`() = runTest {
        val validNames = listOf(
            "John Doe",
            "Test User",
            "User123",
            "A",
            "a".repeat(50), // maximum length
            "Name with 🎯 emoji",
            "Name-with-hyphens",
            "Name.with.dots"
        )

        validNames.forEach { name ->
            val result = validator.validateDisplayName(name)
            assertTrue(result.isSuccess, "Display name '$name' should be valid")
        }
    }

    @Test
    fun `validateDisplayName fails with blank name`() = runTest {
        val invalidNames = listOf("", "   ", "\t", "\n")

        invalidNames.forEach { name ->
            val result = validator.validateDisplayName(name)
            assertTrue(result.isFailure)
            val error = result.exceptionOrNull() as LiftrixError.ValidationError
            assertTrue(error.errorMessage.contains("blank"))
        }
    }

    @Test
    fun `validateDisplayName fails with too long name`() = runTest {
        val longName = "a".repeat(51)

        val result = validator.validateDisplayName(longName)
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull() as LiftrixError.ValidationError
        assertTrue(error.errorMessage.contains("50 characters"))
    }

    // Bio validation tests
    @Test
    fun `validateBio succeeds with valid bios`() = runTest {
        val validBios = listOf(
            "Short bio",
            "A".repeat(500), // maximum length
            "Bio with emojis 🎯💪🏋️",
            "Bio with\nnewlines\nand special chars!@#$%^&*()",
            ""
        )

        validBios.forEach { bio ->
            val result = validator.validateBio(bio)
            assertTrue(result.isSuccess, "Bio '$bio' should be valid")
        }
    }

    @Test
    fun `validateBio allows empty bio`() = runTest {
        val result = validator.validateBio("")
        assertTrue(result.isSuccess)
    }

    @Test
    fun `validateBio fails with too long bio`() = runTest {
        val longBio = "a".repeat(501)

        val result = validator.validateBio(longBio)
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull() as LiftrixError.ValidationError
        assertTrue(error.errorMessage.contains("500 characters"))
    }

    @Test
    fun `validateBio allows exactly 500 characters`() = runTest {
        val maxLengthBio = "a".repeat(500)

        val result = validator.validateBio(maxLengthBio)
        assertTrue(result.isSuccess)
    }

    // Edge case tests
    @Test
    fun `validation preserves unicode characters`() = runTest {
        // Test with various Unicode characters
        val unicodeUsername = "user_名前123"
        val unicodeDisplayName = "用户 名字 🎯"
        val unicodeBio = "This is my bio with 中文 and emojis 🎯💪"

        // Username should fail (only allows letters, numbers, underscores)
        val usernameResult = validator.validateUsername(unicodeUsername)
        assertTrue(usernameResult.isFailure)

        // Display name should succeed
        val displayNameResult = validator.validateDisplayName(unicodeDisplayName)
        assertTrue(displayNameResult.isSuccess)

        // Bio should succeed
        val bioResult = validator.validateBio(unicodeBio)
        assertTrue(bioResult.isSuccess)
    }

    @Test
    fun `validation handles null and boundary values correctly`() = runTest {
        // Test exact boundary values
        val minValidUsername = "abc"
        val maxValidUsername = "a".repeat(20)
        val maxValidDisplayName = "a".repeat(50)
        val maxValidBio = "a".repeat(500)

        assertTrue(validator.validateUsername(minValidUsername).isSuccess)
        assertTrue(validator.validateUsername(maxValidUsername).isSuccess)
        assertTrue(validator.validateDisplayName(maxValidDisplayName).isSuccess)
        assertTrue(validator.validateBio(maxValidBio).isSuccess)

        // Test just over boundary values
        val tooShortUsername = "ab"
        val tooLongUsername = "a".repeat(21)
        val tooLongDisplayName = "a".repeat(51)
        val tooLongBio = "a".repeat(501)

        assertTrue(validator.validateUsername(tooShortUsername).isFailure)
        assertTrue(validator.validateUsername(tooLongUsername).isFailure)
        assertTrue(validator.validateDisplayName(tooLongDisplayName).isFailure)
        assertTrue(validator.validateBio(tooLongBio).isFailure)
    }
}