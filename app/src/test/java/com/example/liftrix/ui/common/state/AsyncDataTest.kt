package com.example.liftrix.ui.common.state

import com.example.liftrix.domain.model.error.LiftrixError
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.Test
import org.junit.Assert.*
import org.junit.Before
import kotlin.test.assertFailsWith

/**
 * Comprehensive unit tests for AsyncData sealed class and utility methods.
 * 
 * Tests cover all four states (NotAsked, Loading, Success, Failure) and all
 * utility methods including state checking, data access, functional programming
 * utilities, time-based operations, and combination methods.
 */
class AsyncDataTest {

    private lateinit var testData: String
    private lateinit var testError: LiftrixError
    private lateinit var testTimestamp: Instant

    @Before
    fun setUp() {
        testData = "Test Data"
        testError = LiftrixError.NetworkError("Test network error")
        testTimestamp = Clock.System.now()
    }

    // =========================================================================================
    // State Creation Tests
    // =========================================================================================

    @Test
    fun `NotAsked state creates correctly`() {
        val asyncData = AsyncData.NotAsked
        
        assertTrue(asyncData.isNotAsked())
        assertFalse(asyncData.isLoading())
        assertFalse(asyncData.isSuccess())
        assertFalse(asyncData.isFailure())
    }

    @Test
    fun `Loading state creates correctly with timestamp`() {
        val asyncData = AsyncData.Loading(testTimestamp)
        
        assertFalse(asyncData.isNotAsked())
        assertTrue(asyncData.isLoading())
        assertFalse(asyncData.isSuccess())
        assertFalse(asyncData.isFailure())
        assertEquals(testTimestamp, asyncData.timestamp)
    }

    @Test
    fun `Loading state creates with current timestamp by default`() {
        val beforeCreation = Clock.System.now()
        val asyncData = AsyncData.Loading()
        val afterCreation = Clock.System.now()
        
        assertTrue(asyncData.isLoading())
        assertTrue(asyncData.timestamp >= beforeCreation)
        assertTrue(asyncData.timestamp <= afterCreation)
    }

    @Test
    fun `Success state creates correctly with data and timestamp`() {
        val asyncData = AsyncData.Success(testData, testTimestamp)
        
        assertFalse(asyncData.isNotAsked())
        assertFalse(asyncData.isLoading())
        assertTrue(asyncData.isSuccess())
        assertFalse(asyncData.isFailure())
        assertEquals(testData, asyncData.data)
        assertEquals(testTimestamp, asyncData.timestamp)
    }

    @Test
    fun `Success state creates with current timestamp by default`() {
        val beforeCreation = Clock.System.now()
        val asyncData = AsyncData.Success(testData)
        val afterCreation = Clock.System.now()
        
        assertTrue(asyncData.isSuccess())
        assertEquals(testData, asyncData.data)
        assertTrue(asyncData.timestamp >= beforeCreation)
        assertTrue(asyncData.timestamp <= afterCreation)
    }

    @Test
    fun `Failure state creates correctly with error and timestamp`() {
        val asyncData = AsyncData.Failure(testError, testTimestamp)
        
        assertFalse(asyncData.isNotAsked())
        assertFalse(asyncData.isLoading())
        assertFalse(asyncData.isSuccess())
        assertTrue(asyncData.isFailure())
        assertEquals(testError, asyncData.error)
        assertEquals(testTimestamp, asyncData.timestamp)
    }

    @Test
    fun `Failure state creates with current timestamp by default`() {
        val beforeCreation = Clock.System.now()
        val asyncData = AsyncData.Failure(testError)
        val afterCreation = Clock.System.now()
        
        assertTrue(asyncData.isFailure())
        assertEquals(testError, asyncData.error)
        assertTrue(asyncData.timestamp >= beforeCreation)
        assertTrue(asyncData.timestamp <= afterCreation)
    }

    // =========================================================================================
    // State Checking Tests
    // =========================================================================================

    @Test
    fun `isComplete returns true for Success and Failure states`() {
        assertTrue(AsyncData.Success(testData).isComplete())
        assertTrue(AsyncData.Failure(testError).isComplete())
        assertFalse(AsyncData.NotAsked.isComplete())
        assertFalse(AsyncData.Loading().isComplete())
    }

    @Test
    fun `isPending returns true for NotAsked and Loading states`() {
        assertTrue(AsyncData.NotAsked.isPending())
        assertTrue(AsyncData.Loading().isPending())
        assertFalse(AsyncData.Success(testData).isPending())
        assertFalse(AsyncData.Failure(testError).isPending())
    }

    // =========================================================================================
    // Data Access Tests
    // =========================================================================================

    @Test
    fun `getOrNull returns data for Success state and null for others`() {
        assertEquals(testData, AsyncData.Success(testData).getOrNull())
        assertNull(AsyncData.NotAsked.getOrNull())
        assertNull(AsyncData.Loading().getOrNull())
        assertNull(AsyncData.Failure(testError).getOrNull())
    }

    @Test
    fun `getOrThrow returns data for Success state`() {
        assertEquals(testData, AsyncData.Success(testData).getOrThrow())
    }

    @Test
    fun `getOrThrow throws error for Failure state`() {
        val asyncData = AsyncData.Failure(testError)
        assertFailsWith<LiftrixError.NetworkError> {
            asyncData.getOrThrow()
        }
    }

    @Test
    fun `getOrThrow throws IllegalStateException for Loading state`() {
        val asyncData = AsyncData.Loading()
        assertFailsWith<IllegalStateException> {
            asyncData.getOrThrow()
        }
    }

    @Test
    fun `getOrThrow throws IllegalStateException for NotAsked state`() {
        val asyncData = AsyncData.NotAsked
        assertFailsWith<IllegalStateException> {
            asyncData.getOrThrow()
        }
    }

    @Test
    fun `getOrDefault returns data for Success state and default for others`() {
        val defaultValue = "Default"
        
        assertEquals(testData, AsyncData.Success(testData).getOrDefault(defaultValue))
        assertEquals(defaultValue, AsyncData.NotAsked.getOrDefault(defaultValue))
        assertEquals(defaultValue, AsyncData.Loading().getOrDefault(defaultValue))
        assertEquals(defaultValue, AsyncData.Failure(testError).getOrDefault(defaultValue))
    }

    @Test
    fun `getOrElse returns data for Success state and function result for others`() {
        val defaultValue = "Default"
        val defaultProvider = { defaultValue }
        
        assertEquals(testData, AsyncData.Success(testData).getOrElse(defaultProvider))
        assertEquals(defaultValue, AsyncData.NotAsked.getOrElse(defaultProvider))
        assertEquals(defaultValue, AsyncData.Loading().getOrElse(defaultProvider))
        assertEquals(defaultValue, AsyncData.Failure(testError).getOrElse(defaultProvider))
    }

    @Test
    fun `errorOrNull returns error for Failure state and null for others`() {
        assertEquals(testError, AsyncData.Failure(testError).errorOrNull())
        assertNull(AsyncData.NotAsked.errorOrNull())
        assertNull(AsyncData.Loading().errorOrNull())
        assertNull(AsyncData.Success(testData).errorOrNull())
    }

    @Test
    fun `timestampOrNull returns timestamp for states with timestamps`() {
        assertNull(AsyncData.NotAsked.timestampOrNull())
        assertEquals(testTimestamp, AsyncData.Loading(testTimestamp).timestampOrNull())
        assertEquals(testTimestamp, AsyncData.Success(testData, testTimestamp).timestampOrNull())
        assertEquals(testTimestamp, AsyncData.Failure(testError, testTimestamp).timestampOrNull())
    }

    // =========================================================================================
    // Functional Programming Tests
    // =========================================================================================

    @Test
    fun `map transforms data in Success state and preserves others`() {
        val transform = { data: String -> data.length }
        
        val successResult = AsyncData.Success(testData).map(transform)
        assertTrue(successResult.isSuccess())
        assertEquals(testData.length, successResult.getOrNull())
        
        assertTrue(AsyncData.NotAsked.map(transform).isNotAsked())
        assertTrue(AsyncData.Loading(testTimestamp).map(transform).isLoading())
        assertTrue(AsyncData.Failure(testError).map(transform).isFailure())
    }

    @Test
    fun `map preserves timestamps`() {
        val transform = { data: String -> data.length }
        
        val loadingResult = AsyncData.Loading(testTimestamp).map(transform) as AsyncData.Loading
        assertEquals(testTimestamp, loadingResult.timestamp)
        
        val successResult = AsyncData.Success(testData, testTimestamp).map(transform) as AsyncData.Success
        assertEquals(testTimestamp, successResult.timestamp)
        
        val failureResult = AsyncData.Failure(testError, testTimestamp).map(transform) as AsyncData.Failure
        assertEquals(testTimestamp, failureResult.timestamp)
    }

    @Test
    fun `flatMap transforms data in Success state and preserves others`() {
        val transform = { data: String -> AsyncData.Success(data.length) }
        
        val successResult = AsyncData.Success(testData).flatMap(transform)
        assertTrue(successResult.isSuccess())
        assertEquals(testData.length, successResult.getOrNull())
        
        assertTrue(AsyncData.NotAsked.flatMap(transform).isNotAsked())
        assertTrue(AsyncData.Loading(testTimestamp).flatMap(transform).isLoading())
        assertTrue(AsyncData.Failure(testError).flatMap(transform).isFailure())
    }

    @Test
    fun `flatMap can transform success to failure`() {
        val transform = { _: String -> AsyncData.Failure(testError) }
        
        val result = AsyncData.Success(testData).flatMap(transform)
        assertTrue(result.isFailure())
        assertEquals(testError, result.errorOrNull())
    }

    @Test
    fun `onSuccess executes action for Success state only`() {
        var actionExecuted = false
        val action = { _: String -> actionExecuted = true }
        
        AsyncData.Success(testData).onSuccess(action)
        assertTrue(actionExecuted)
        
        actionExecuted = false
        AsyncData.NotAsked.onSuccess(action)
        assertFalse(actionExecuted)
        
        AsyncData.Loading().onSuccess(action)
        assertFalse(actionExecuted)
        
        AsyncData.Failure(testError).onSuccess(action)
        assertFalse(actionExecuted)
    }

    @Test
    fun `onFailure executes action for Failure state only`() {
        var actionExecuted = false
        val action = { _: LiftrixError -> actionExecuted = true }
        
        AsyncData.Failure(testError).onFailure(action)
        assertTrue(actionExecuted)
        
        actionExecuted = false
        AsyncData.NotAsked.onFailure(action)
        assertFalse(actionExecuted)
        
        AsyncData.Loading().onFailure(action)
        assertFalse(actionExecuted)
        
        AsyncData.Success(testData).onFailure(action)
        assertFalse(actionExecuted)
    }

    @Test
    fun `onLoading executes action for Loading state only`() {
        var actionExecuted = false
        val action = { _: Instant -> actionExecuted = true }
        
        AsyncData.Loading(testTimestamp).onLoading(action)
        assertTrue(actionExecuted)
        
        actionExecuted = false
        AsyncData.NotAsked.onLoading(action)
        assertFalse(actionExecuted)
        
        AsyncData.Success(testData).onLoading(action)
        assertFalse(actionExecuted)
        
        AsyncData.Failure(testError).onLoading(action)
        assertFalse(actionExecuted)
    }

    @Test
    fun `fold handles all states correctly`() {
        val notAskedValue = "not-asked"
        val loadingValue = "loading"
        val successValue = "success"
        val failureValue = "failure"
        
        val notAsked = { notAskedValue }
        val loading = { _: Instant -> loadingValue }
        val success = { _: String, _: Instant -> successValue }
        val failure = { _: LiftrixError, _: Instant -> failureValue }
        
        assertEquals(notAskedValue, AsyncData.NotAsked.fold(notAsked, loading, success, failure))
        assertEquals(loadingValue, AsyncData.Loading(testTimestamp).fold(notAsked, loading, success, failure))
        assertEquals(successValue, AsyncData.Success(testData, testTimestamp).fold(notAsked, loading, success, failure))
        assertEquals(failureValue, AsyncData.Failure(testError, testTimestamp).fold(notAsked, loading, success, failure))
    }

    @Test
    fun `foldSimple handles all states correctly without timestamps`() {
        val notAskedValue = "not-asked"
        val loadingValue = "loading"
        val successValue = "success"
        val failureValue = "failure"
        
        val notAsked = { notAskedValue }
        val loading = { loadingValue }
        val success = { _: String -> successValue }
        val failure = { _: LiftrixError -> failureValue }
        
        assertEquals(notAskedValue, AsyncData.NotAsked.foldSimple(notAsked, loading, success, failure))
        assertEquals(loadingValue, AsyncData.Loading(testTimestamp).foldSimple(notAsked, loading, success, failure))
        assertEquals(successValue, AsyncData.Success(testData, testTimestamp).foldSimple(notAsked, loading, success, failure))
        assertEquals(failureValue, AsyncData.Failure(testError, testTimestamp).foldSimple(notAsked, loading, success, failure))
    }

    // =========================================================================================
    // Convenience Factory Tests
    // =========================================================================================

    @Test
    fun `asAsyncSuccess creates Success state`() {
        val result = testData.asAsyncSuccess()
        assertTrue(result.isSuccess())
        assertEquals(testData, result.getOrNull())
    }

    @Test
    fun `asAsyncFailure creates Failure state`() {
        val result = testError.asAsyncFailure()
        assertTrue(result.isFailure())
        assertEquals(testError, result.errorOrNull())
    }

    @Test
    fun `asyncLoading creates Loading state`() {
        val result = asyncLoading()
        assertTrue(result.isLoading())
    }

    @Test
    fun `asyncNotAsked creates NotAsked state`() {
        val result = asyncNotAsked()
        assertTrue(result.isNotAsked())
    }

    // =========================================================================================
    // Time-based Utility Tests
    // =========================================================================================

    @Test
    fun `isFresh returns true for recent data`() {
        val recentTimestamp = Clock.System.now()
        val asyncData = AsyncData.Success(testData, recentTimestamp)
        
        assertTrue(asyncData.isFresh(maxAge = 5000)) // 5 seconds
    }

    @Test
    fun `isFresh returns false for old data`() {
        val oldTimestamp = Instant.fromEpochMilliseconds(
            Clock.System.now().toEpochMilliseconds() - 10000 // 10 seconds ago
        )
        val asyncData = AsyncData.Success(testData, oldTimestamp)
        
        assertFalse(asyncData.isFresh(maxAge = 5000)) // 5 seconds
    }

    @Test
    fun `isFresh returns false for NotAsked state`() {
        assertFalse(AsyncData.NotAsked.isFresh(maxAge = 5000))
    }

    @Test
    fun `isStale returns opposite of isFresh`() {
        val recentTimestamp = Clock.System.now()
        val asyncData = AsyncData.Success(testData, recentTimestamp)
        
        assertFalse(asyncData.isStale(maxAge = 5000))
        assertTrue(asyncData.isFresh(maxAge = 5000))
    }

    @Test
    fun `ageInMillis returns correct age`() {
        val pastTimestamp = Instant.fromEpochMilliseconds(
            Clock.System.now().toEpochMilliseconds() - 1000 // 1 second ago
        )
        val asyncData = AsyncData.Success(testData, pastTimestamp)
        
        val age = asyncData.ageInMillis()
        assertNotNull(age)
        assertTrue(age!! >= 1000) // At least 1 second
        assertTrue(age <= 2000) // But not too old (account for test execution time)
    }

    @Test
    fun `ageInMillis returns null for NotAsked state`() {
        assertNull(AsyncData.NotAsked.ageInMillis())
    }

    // =========================================================================================
    // Combination Tests
    // =========================================================================================

    @Test
    fun `combineWith returns NotAsked if either is NotAsked`() {
        val success = AsyncData.Success(testData)
        val notAsked = AsyncData.NotAsked
        
        assertTrue(success.combineWith(notAsked).isNotAsked())
        assertTrue(notAsked.combineWith(success).isNotAsked())
    }

    @Test
    fun `combineWith returns Loading if either is Loading`() {
        val success = AsyncData.Success(testData)
        val loading = AsyncData.Loading()
        
        assertTrue(success.combineWith(loading).isLoading())
        assertTrue(loading.combineWith(success).isLoading())
    }

    @Test
    fun `combineWith returns first Failure if either is Failure`() {
        val success = AsyncData.Success(testData)
        val failure = AsyncData.Failure(testError)
        val otherError = LiftrixError.DatabaseError("DB error")
        val otherFailure = AsyncData.Failure(otherError)
        
        val result1 = failure.combineWith(success)
        assertTrue(result1.isFailure())
        assertEquals(testError, result1.errorOrNull())
        
        val result2 = failure.combineWith(otherFailure)
        assertTrue(result2.isFailure())
        assertEquals(testError, result2.errorOrNull()) // First error
    }

    @Test
    fun `combineWith returns Success with Pair if both are Success`() {
        val success1 = AsyncData.Success(testData)
        val success2 = AsyncData.Success(42)
        
        val result = success1.combineWith(success2)
        assertTrue(result.isSuccess())
        assertEquals(Pair(testData, 42), result.getOrNull())
    }

    // =========================================================================================
    // Recovery Tests
    // =========================================================================================

    @Test
    fun `recover returns Success with default value for Failure state`() {
        val defaultValue = "Default"
        val failure = AsyncData.Failure(testError)
        
        val result = failure.recover(defaultValue)
        assertTrue(result.isSuccess())
        assertEquals(defaultValue, result.getOrNull())
    }

    @Test
    fun `recover preserves non-Failure states`() {
        val defaultValue = "Default"
        
        val success = AsyncData.Success(testData)
        assertEquals(success, success.recover(defaultValue))
        
        val notAsked = AsyncData.NotAsked
        assertEquals(notAsked, notAsked.recover(defaultValue))
        
        val loading = AsyncData.Loading()
        assertEquals(loading, loading.recover(defaultValue))
    }

    @Test
    fun `recoverWith transforms error to value for Failure state`() {
        val failure = AsyncData.Failure(testError)
        val recovery = { error: LiftrixError -> "Recovered from: ${error.message}" }
        
        val result = failure.recoverWith(recovery)
        assertTrue(result.isSuccess())
        assertEquals("Recovered from: ${testError.message}", result.getOrNull())
    }

    @Test
    fun `recoverWith preserves non-Failure states`() {
        val recovery = { error: LiftrixError -> "Recovered from: ${error.message}" }
        
        val success = AsyncData.Success(testData)
        assertEquals(success, success.recoverWith(recovery))
        
        val notAsked = AsyncData.NotAsked
        assertEquals(notAsked, notAsked.recoverWith(recovery))
        
        val loading = AsyncData.Loading()
        assertEquals(loading, loading.recoverWith(recovery))
    }

    // =========================================================================================
    // Filter Tests
    // =========================================================================================

    @Test
    fun `filter preserves Success state when predicate passes`() {
        val success = AsyncData.Success(testData)
        val predicate = { data: String -> data.isNotEmpty() }
        val errorProvider = { LiftrixError.ValidationError(field = "field", violations = listOf("empty")) }
        
        val result = success.filter(predicate, errorProvider)
        assertTrue(result.isSuccess())
        assertEquals(testData, result.getOrNull())
    }

    @Test
    fun `filter converts Success to Failure when predicate fails`() {
        val success = AsyncData.Success("")
        val predicate = { data: String -> data.isNotEmpty() }
        val error = LiftrixError.ValidationError(field = "field", violations = listOf("empty"))
        val errorProvider = { error }
        
        val result = success.filter(predicate, errorProvider)
        assertTrue(result.isFailure())
        assertEquals(error, result.errorOrNull())
    }

    @Test
    fun `filter preserves non-Success states`() {
        val predicate = { data: String -> data.isNotEmpty() }
        val errorProvider = { LiftrixError.ValidationError(field = "field", violations = listOf("empty")) }
        
        val notAsked = AsyncData.NotAsked
        assertEquals(notAsked, notAsked.filter(predicate, errorProvider))
        
        val loading = AsyncData.Loading()
        assertEquals(loading, loading.filter(predicate, errorProvider))
        
        val failure = AsyncData.Failure(testError)
        assertEquals(failure, failure.filter(predicate, errorProvider))
    }

    // =========================================================================================
    // Thread Safety Tests
    // =========================================================================================

    @Test
    fun `AsyncData instances are immutable`() {
        val success = AsyncData.Success(testData, testTimestamp)
        
        // All properties should be immutable
        assertEquals(testData, success.data)
        assertEquals(testTimestamp, success.timestamp)
        
        // Map operation should return new instance
        val mapped = success.map { it.uppercase() }
        assertNotEquals(success, mapped)
        assertEquals(testData, success.data) // Original unchanged
    }

    @Test
    fun `utility methods do not modify original instances`() {
        val original = AsyncData.Success(testData, testTimestamp)
        
        // Various operations should not modify original
        original.map { it.uppercase() }
        original.flatMap { AsyncData.Success(it.length) }
        original.onSuccess { /* side effect */ }
        original.recover("default")
        original.filter({ it.isNotEmpty() }) { testError }
        
        // Original should be unchanged
        assertEquals(testData, original.data)
        assertEquals(testTimestamp, original.timestamp)
    }
}