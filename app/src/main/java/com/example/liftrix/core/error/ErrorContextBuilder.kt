package com.example.liftrix.core.error

import kotlinx.datetime.Clock

/**
 * Builder pattern for creating comprehensive error context with user, session, and operational metadata.
 * 
 * Provides a fluent interface for building error context maps with support for:
 * - User identification and tracking
 * - Session correlation across operations
 * - Operational metadata for debugging
 * - Custom metadata for specific use cases
 * - Automatic timestamp and system information
 * 
 * The builder pattern allows for flexible context creation while maintaining consistency
 * across all error handling scenarios in the application.
 * 
 * Usage:
 * ```
 * val context = ErrorContextBuilder()
 *     .withUser("user_123")
 *     .withSession("session_456")
 *     .withOperation("workout_creation")
 *     .withMetadata("workout_type", "strength_training")
 *     .withMetadata("template_id", "template_789")
 *     .build()
 * ```
 */
class ErrorContextBuilder {
    
    internal val context = mutableMapOf<String, String>()
    
    /**
     * Adds user identification to the error context.
     * 
     * User information is essential for tracking user-specific error patterns
     * and providing personalized error recovery flows.
     * 
     * @param userId The unique identifier for the user
     * @return Builder instance for method chaining
     * 
     * Example:
     * ```
     * builder.withUser("user_123")
     * ```
     */
    fun withUser(userId: String): ErrorContextBuilder {
        context["user_id"] = userId
        return this
    }
    
    /**
     * Adds session identification to the error context.
     * 
     * Session information enables correlation of errors across multiple operations
     * within a single user session, facilitating debugging and pattern analysis.
     * 
     * @param sessionId The unique identifier for the current session
     * @return Builder instance for method chaining
     * 
     * Example:
     * ```
     * builder.withSession("session_456")
     * ```
     */
    fun withSession(sessionId: String): ErrorContextBuilder {
        context["session_id"] = sessionId
        return this
    }
    
    /**
     * Adds operation identification to the error context.
     * 
     * Operation information provides context about what the application was attempting
     * to do when the error occurred, essential for categorizing and debugging errors.
     * 
     * @param operation Description of the operation being performed
     * @return Builder instance for method chaining
     * 
     * Example:
     * ```
     * builder.withOperation("workout_creation")
     * ```
     */
    fun withOperation(operation: String): ErrorContextBuilder {
        context["operation"] = operation
        return this
    }
    
    /**
     * Adds custom metadata to the error context.
     * 
     * Metadata allows for addition of operation-specific context information
     * that can be valuable for debugging and analytics purposes.
     * 
     * @param key The metadata key
     * @param value The metadata value
     * @return Builder instance for method chaining
     * 
     * Example:
     * ```
     * builder.withMetadata("workout_type", "strength_training")
     *        .withMetadata("template_id", "template_789")
     * ```
     */
    fun withMetadata(key: String, value: String): ErrorContextBuilder {
        context[key] = value
        return this
    }
    
    /**
     * Builds the final error context map with all provided information.
     * 
     * Automatically adds system-level context information including:
     * - Current timestamp for error occurrence tracking
     * - Error source identification
     * - Platform information
     * - Error handler identification
     * - Context size validation
     * 
     * @return Immutable map containing comprehensive error context
     * 
     * Example:
     * ```
     * val context = ErrorContextBuilder()
     *     .withUser("user_123")
     *     .withOperation("workout_creation")
     *     .build()
     * ```
     * 
     * Returns a map containing:
     * - All user-provided context (user_id, session_id, operation, custom metadata)
     * - System context (timestamp, error_source, platform, error_handler)
     * - Validation context (context_size)
     */
    fun build(): Map<String, String> {
        return buildMap {
            // Add all user-provided context
            putAll(context)
            
            // Add system-level context if not already present
            if (!containsKey("timestamp")) {
                put("timestamp", Clock.System.now().toString())
            }
            
            if (!containsKey("error_source")) {
                put("error_source", "liftrix_app")
            }
            
            if (!containsKey("platform")) {
                put("platform", "android")
            }
            
            if (!containsKey("error_handler")) {
                put("error_handler", "ErrorContextBuilder")
            }
            
            // Add builder-specific metadata
            put("context_builder_version", "1.0.0")
            put("context_build_method", "builder_pattern")
            
            // Add context validation (must be last to get accurate size)
            // Note: Add 1 to size because putting this entry will increase the size by 1
            put("context_size", (size + 1).toString())
        }
    }
    
    /**
     * Creates a copy of this builder with the same context.
     * 
     * Useful for creating multiple similar contexts or for preserving
     * a base context while adding specific metadata.
     * 
     * @return New ErrorContextBuilder instance with copied context
     */
    fun copy(): ErrorContextBuilder {
        val newBuilder = ErrorContextBuilder()
        newBuilder.context.putAll(this.context)
        return newBuilder
    }
    
    /**
     * Clears all context information from the builder.
     * 
     * Useful for reusing a builder instance for multiple error contexts.
     * 
     * @return Builder instance for method chaining
     */
    fun clear(): ErrorContextBuilder {
        context.clear()
        return this
    }
    
    /**
     * Checks if the builder has any context information.
     * 
     * @return true if context is empty, false otherwise
     */
    fun isEmpty(): Boolean {
        return context.isEmpty()
    }
    
    /**
     * Returns the current number of context entries.
     * 
     * @return Number of context entries currently in the builder
     */
    fun size(): Int {
        return context.size
    }
    
    /**
     * Checks if a specific key exists in the context.
     * 
     * @param key The key to check for
     * @return true if the key exists, false otherwise
     */
    fun containsKey(key: String): Boolean {
        return context.containsKey(key)
    }
    
    /**
     * Gets the current value for a specific key.
     * 
     * @param key The key to get the value for
     * @return The value associated with the key, or null if not found
     */
    fun getValue(key: String): String? {
        return context[key]
    }
    
    /**
     * Removes a specific key from the context.
     * 
     * @param key The key to remove
     * @return Builder instance for method chaining
     */
    fun removeKey(key: String): ErrorContextBuilder {
        context.remove(key)
        return this
    }
    
    companion object {
        /**
         * Creates a new ErrorContextBuilder instance.
         * 
         * @return New ErrorContextBuilder instance
         */
        fun create(): ErrorContextBuilder {
            return ErrorContextBuilder()
        }
        
        /**
         * Creates an ErrorContextBuilder with initial user context.
         * 
         * @param userId The user ID to initialize with
         * @return ErrorContextBuilder instance with user context
         */
        fun withUser(userId: String): ErrorContextBuilder {
            return ErrorContextBuilder().withUser(userId)
        }
        
        /**
         * Creates an ErrorContextBuilder with initial operation context.
         * 
         * @param operation The operation to initialize with
         * @return ErrorContextBuilder instance with operation context
         */
        fun withOperation(operation: String): ErrorContextBuilder {
            return ErrorContextBuilder().withOperation(operation)
        }
        
        /**
         * Creates an ErrorContextBuilder with initial user and operation context.
         * 
         * @param userId The user ID to initialize with
         * @param operation The operation to initialize with
         * @return ErrorContextBuilder instance with user and operation context
         */
        fun withUserAndOperation(userId: String, operation: String): ErrorContextBuilder {
            return ErrorContextBuilder()
                .withUser(userId)
                .withOperation(operation)
        }
    }
}

/**
 * Extension function to create an ErrorContextBuilder from existing context.
 * 
 * Useful for enhancing existing error contexts with additional information.
 * 
 * @param existingContext The existing context to start with
 * @return ErrorContextBuilder instance with existing context
 */
fun ErrorContextBuilder.Companion.fromExisting(existingContext: Map<String, String>): ErrorContextBuilder {
    val builder = ErrorContextBuilder()
    builder.context.putAll(existingContext)
    return builder
}

/**
 * Extension function to merge context from another builder.
 * 
 * @param other The other builder to merge context from
 * @return Builder instance for method chaining
 */
fun ErrorContextBuilder.merge(other: ErrorContextBuilder): ErrorContextBuilder {
    context.putAll(other.context)
    return this
}

/**
 * Extension function to add multiple metadata entries at once.
 * 
 * @param metadata The metadata map to add
 * @return Builder instance for method chaining
 */
fun ErrorContextBuilder.withMetadata(metadata: Map<String, String>): ErrorContextBuilder {
    context.putAll(metadata)
    return this
}