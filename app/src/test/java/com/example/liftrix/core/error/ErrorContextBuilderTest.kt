package com.example.liftrix.core.error

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Comprehensive unit tests for ErrorContextBuilder with proper mocking and validation.
 * 
 * Tests cover all builder methods, edge cases, and integration scenarios to ensure
 * reliable error context creation across all use cases.
 */
class ErrorContextBuilderTest {
    
    private lateinit var builder: ErrorContextBuilder
    
    @Before
    fun setup() {
        builder = ErrorContextBuilder()
    }
    
    // Basic Builder Operations
        
        @Test
        fun `given new builder, when checking isEmpty, then returns true`() {
            // Given: fresh builder instance
            
            // When: checking if empty
            val isEmpty = builder.isEmpty()
            
            // Then: should be empty
            assertTrue(isEmpty)
            assertEquals(0, builder.size())
        }
        
        @Test
        fun `given builder with user, when building context, then includes user information`() {
            // Given: builder with user
            val userId = "user_123"
            
            // When: building context
            val context = builder.withUser(userId).build()
            
            // Then: should include user information
            assertEquals(userId, context["user_id"])
            assertTrue(context.containsKey("user_id"))
            assertFalse(builder.isEmpty())
        }
        
        @Test
        fun `given builder with session, when building context, then includes session information`() {
            // Given: builder with session
            val sessionId = "session_456"
            
            // When: building context
            val context = builder.withSession(sessionId).build()
            
            // Then: should include session information
            assertEquals(sessionId, context["session_id"])
            assertTrue(context.containsKey("session_id"))
        }
        
        @Test
        fun `given builder with operation, when building context, then includes operation information`() {
            // Given: builder with operation
            val operation = "workout_creation"
            
            // When: building context
            val context = builder.withOperation(operation).build()
            
            // Then: should include operation information
            assertEquals(operation, context["operation"])
            assertTrue(context.containsKey("operation"))
        }
        
        @Test
        fun `given builder with metadata, when building context, then includes custom metadata`() {
            // Given: builder with metadata
            val key = "workout_type"
            val value = "strength_training"
            
            // When: building context
            val context = builder.withMetadata(key, value).build()
            
            // Then: should include custom metadata
            assertEquals(value, context[key])
            assertTrue(context.containsKey(key))
        }
    // Method Chaining Tests
        
        @Test
        fun `given builder, when chaining multiple operations, then all operations are applied`() {
            // Given: builder instance
            val userId = "user_123"
            val sessionId = "session_456"
            val operation = "workout_creation"
            val metadataKey = "workout_type"
            val metadataValue = "strength_training"
            
            // When: chaining multiple operations
            val context = builder
                .withUser(userId)
                .withSession(sessionId)
                .withOperation(operation)
                .withMetadata(metadataKey, metadataValue)
                .build()
            
            // Then: all operations should be applied
            assertEquals(userId, context["user_id"])
            assertEquals(sessionId, context["session_id"])
            assertEquals(operation, context["operation"])
            assertEquals(metadataValue, context[metadataKey])
        }
        
        @Test
        fun `given builder, when chaining operations, then returns same builder instance`() {
            // Given: builder instance
            val userId = "user_123"
            
            // When: chaining operations
            val result = builder.withUser(userId)
            
            // Then: should return same builder instance
            assertSame(builder, result)
        }
        
        @Test
        fun `given builder, when adding multiple metadata entries, then all are included`() {
            // Given: builder instance
            val metadata1 = Pair("key1", "value1")
            val metadata2 = Pair("key2", "value2")
            val metadata3 = Pair("key3", "value3")
            
            // When: adding multiple metadata entries
            val context = builder
                .withMetadata(metadata1.first, metadata1.second)
                .withMetadata(metadata2.first, metadata2.second)
                .withMetadata(metadata3.first, metadata3.second)
                .build()
            
            // Then: all metadata should be included
            assertEquals(metadata1.second, context[metadata1.first])
            assertEquals(metadata2.second, context[metadata2.first])
            assertEquals(metadata3.second, context[metadata3.first])
        }
    // System Context Generation Tests
        
        @Test
        fun `given builder, when building context, then includes system-level context`() {
            // Given: builder with some user context
            val userId = "user_123"
            
            // When: building context
            val context = builder.withUser(userId).build()
            
            // Then: should include system-level context
            assertTrue(context.containsKey("timestamp"))
            assertEquals("liftrix_app", context["error_source"])
            assertEquals("android", context["platform"])
            assertEquals("ErrorContextBuilder", context["error_handler"])
            assertTrue(context.containsKey("context_size"))
        }
        
        @Test
        fun `given builder, when building context, then timestamp is valid ISO format`() {
            // Given: builder with user context
            val userId = "user_123"
            
            // When: building context
            val context = builder.withUser(userId).build()
            
            // Then: timestamp should be parseable
            val timestamp = context["timestamp"]
            assertNotNull(timestamp)
            
            // Verify timestamp is parseable as Instant
            try {
                Instant.parse(timestamp!!)
                // If we get here, parsing succeeded
            } catch (e: Exception) {
                fail("Timestamp should be parseable: ${e.message}")
            }
        }
        
        @Test
        fun `given builder, when building context, then context size is accurate`() {
            // Given: builder with specific amount of context
            val context = builder
                .withUser("user_123")
                .withSession("session_456")
                .withOperation("test_operation")
                .build()
            
            // When: getting context size
            val contextSize = context["context_size"]?.toIntOrNull()
            
            // Then: context size should be accurate
            assertNotNull(contextSize)
            assertEquals(context.size, contextSize)
        }
        
        @Test
        fun `given builder, when building context, then includes builder version information`() {
            // Given: builder instance
            
            // When: building context
            val context = builder.withUser("user_123").build()
            
            // Then: should include builder version information
            assertEquals("1.0.0", context["context_builder_version"])
            assertEquals("builder_pattern", context["context_build_method"])
        }
    // Builder State Management Tests
        
        @Test
        fun `given builder with context, when copying, then creates independent copy`() {
            // Given: builder with context
            val originalContext = builder
                .withUser("user_123")
                .withSession("session_456")
            
            // When: copying builder
            val copiedBuilder = originalContext.copy()
            copiedBuilder.withMetadata("additional_key", "additional_value")
            
            // Then: original and copy should be independent
            assertFalse(originalContext.containsKey("additional_key"))
            assertTrue(copiedBuilder.containsKey("additional_key"))
            assertTrue(copiedBuilder.containsKey("user_id"))
            assertTrue(copiedBuilder.containsKey("session_id"))
        }
        
        @Test
        fun `given builder with context, when clearing, then removes all context`() {
            // Given: builder with context
            builder.withUser("user_123")
                .withSession("session_456")
                .withOperation("test_operation")
            
            // When: clearing builder
            val clearedBuilder = builder.clear()
            
            // Then: should remove all context
            assertTrue(builder.isEmpty())
            assertEquals(0, builder.size())
            assertSame(builder, clearedBuilder)
        }
        
        @Test
        fun `given builder with context, when getting value, then returns correct value`() {
            // Given: builder with context
            val userId = "user_123"
            builder.withUser(userId)
            
            // When: getting value
            val retrievedUserId = builder.getValue("user_id")
            
            // Then: should return correct value
            assertEquals(userId, retrievedUserId)
        }
        
        @Test
        fun `given builder with context, when removing key, then key is removed`() {
            // Given: builder with context
            builder.withUser("user_123")
                .withSession("session_456")
            
            // When: removing key
            val result = builder.removeKey("user_id")
            
            // Then: key should be removed
            assertFalse(builder.containsKey("user_id"))
            assertTrue(builder.containsKey("session_id"))
            assertSame(builder, result)
        }
        
        @Test
        fun `given builder with context, when checking containsKey, then returns correct status`() {
            // Given: builder with context
            builder.withUser("user_123")
            
            // When: checking key existence
            val hasUserId = builder.containsKey("user_id")
            val hasSessionId = builder.containsKey("session_id")
            
            // Then: should return correct status
            assertTrue(hasUserId)
            assertFalse(hasSessionId)
        }
    // Extension Functions Tests
        
        @Test
        fun `given builder, when adding metadata map, then all entries are added`() {
            // Given: builder instance
            val metadata = mapOf(
                "key1" to "value1",
                "key2" to "value2",
                "key3" to "value3"
            )
            
            // When: adding metadata map
            val context = builder.withMetadata(metadata).build()
            
            // Then: all entries should be added
            assertEquals("value1", context["key1"])
            assertEquals("value2", context["key2"])
            assertEquals("value3", context["key3"])
        }
        
        @Test
        fun `given two builders, when merging, then contexts are combined`() {
            // Given: two builders with different context
            val otherBuilder = ErrorContextBuilder()
                .withUser("user_456")
                .withMetadata("other_key", "other_value")
            
            builder.withSession("session_123")
                .withMetadata("builder_key", "builder_value")
            
            // When: merging builders
            val result = builder.merge(otherBuilder)
            val context = result.build()
            
            // Then: contexts should be combined
            assertEquals("user_456", context["user_id"])
            assertEquals("session_123", context["session_id"])
            assertEquals("other_value", context["other_key"])
            assertEquals("builder_value", context["builder_key"])
            assertSame(builder, result)
        }
        
        @Test
        fun `given existing context, when creating builder from existing, then context is preserved`() {
            // Given: existing context
            val existingContext = mapOf(
                "user_id" to "user_123",
                "session_id" to "session_456",
                "custom_key" to "custom_value"
            )
            
            // When: creating builder from existing context
            val builder = ErrorContextBuilder.fromExisting(existingContext)
            val context = builder.build()
            
            // Then: existing context should be preserved
            assertEquals("user_123", context["user_id"])
            assertEquals("session_456", context["session_id"])
            assertEquals("custom_value", context["custom_key"])
        }
    // Companion Object Factory Methods Tests
        
        @Test
        fun `given companion object, when creating builder, then returns new instance`() {
            // Given: companion object
            
            // When: creating builder
            val builder = ErrorContextBuilder.create()
            
            // Then: should return new instance
            assertNotNull(builder)
            assertTrue(builder.isEmpty())
        }
        
        @Test
        fun `given companion object, when creating with user, then includes user context`() {
            // Given: companion object
            val userId = "user_123"
            
            // When: creating with user
            val builder = ErrorContextBuilder.withUser(userId)
            val context = builder.build()
            
            // Then: should include user context
            assertEquals(userId, context["user_id"])
        }
        
        @Test
        fun `given companion object, when creating with operation, then includes operation context`() {
            // Given: companion object
            val operation = "test_operation"
            
            // When: creating with operation
            val builder = ErrorContextBuilder.withOperation(operation)
            val context = builder.build()
            
            // Then: should include operation context
            assertEquals(operation, context["operation"])
        }
        
        @Test
        fun `given companion object, when creating with user and operation, then includes both contexts`() {
            // Given: companion object
            val userId = "user_123"
            val operation = "test_operation"
            
            // When: creating with user and operation
            val builder = ErrorContextBuilder.withUserAndOperation(userId, operation)
            val context = builder.build()
            
            // Then: should include both contexts
            assertEquals(userId, context["user_id"])
            assertEquals(operation, context["operation"])
        }
    // Edge Cases and Error Scenarios Tests
        
        @Test
        fun `given builder, when adding empty string values, then values are preserved`() {
            // Given: builder instance
            val emptyValue = ""
            
            // When: adding empty string values
            val context = builder
                .withUser(emptyValue)
                .withSession(emptyValue)
                .withOperation(emptyValue)
                .withMetadata("empty_key", emptyValue)
                .build()
            
            // Then: empty values should be preserved
            assertEquals(emptyValue, context["user_id"])
            assertEquals(emptyValue, context["session_id"])
            assertEquals(emptyValue, context["operation"])
            assertEquals(emptyValue, context["empty_key"])
        }
        
        @Test
        fun `given builder, when adding same key multiple times, then last value wins`() {
            // Given: builder instance
            val key = "test_key"
            val firstValue = "first_value"
            val secondValue = "second_value"
            
            // When: adding same key multiple times
            val context = builder
                .withMetadata(key, firstValue)
                .withMetadata(key, secondValue)
                .build()
            
            // Then: last value should win
            assertEquals(secondValue, context[key])
        }
        
        @Test
        fun `given builder, when overriding system context, then user values are preserved`() {
            // Given: builder instance
            val customTimestamp = "2023-01-01T00:00:00Z"
            val customSource = "custom_source"
            
            // When: overriding system context
            val context = builder
                .withMetadata("timestamp", customTimestamp)
                .withMetadata("error_source", customSource)
                .build()
            
            // Then: user values should be preserved (not overridden by system)
            assertEquals(customTimestamp, context["timestamp"])
            assertEquals(customSource, context["error_source"])
        }
        
        @Test
        fun `given builder, when getting non-existent key, then returns null`() {
            // Given: builder with some context
            builder.withUser("user_123")
            
            // When: getting non-existent key
            val value = builder.getValue("non_existent_key")
            
            // Then: should return null
            assertNull(value)
        }
        
        @Test
        fun `given builder, when removing non-existent key, then no error occurs`() {
            // Given: builder with some context
            builder.withUser("user_123")
            
            // When: removing non-existent key
            val result = builder.removeKey("non_existent_key")
            
            // Then: no error should occur
            assertSame(builder, result)
            assertTrue(builder.containsKey("user_id"))
        }
    // Performance and Memory Tests
        
        @Test
        fun `given builder, when building multiple times, then each call creates new map`() {
            // Given: builder with context
            builder.withUser("user_123")
            
            // When: building multiple times
            val context1 = builder.build()
            val context2 = builder.build()
            
            // Then: each call should create new map
            assertNotSame(context1, context2)
            assertEquals(context1, context2)
        }
        
        @Test
        fun `given builder, when adding large amount of metadata, then all is preserved`() {
            // Given: builder instance
            val metadataCount = 100
            
            // When: adding large amount of metadata
            for (i in 1..metadataCount) {
                builder.withMetadata("key_$i", "value_$i")
            }
            val context = builder.build()
            
            // Then: all metadata should be preserved
            // Check that the context contains all user-provided metadata
            for (i in 1..metadataCount) {
                assertEquals("value_$i", context["key_$i"])
            }
            // Verify the builder size is correct (user metadata only, not system metadata)
            assertEquals(metadataCount + 1, builder.size()) // +1 for timestamp added during build()
        }
}