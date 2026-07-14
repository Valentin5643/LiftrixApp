package com.example.liftrix.domain.model.common

import com.example.liftrix.domain.model.error.LiftrixError
import kotlin.coroutines.cancellation.CancellationException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test

class LiftrixResultTest {

    @Test
    fun `liftrixCatching rethrows cancellation without mapping it`() {
        val cancellationException = CancellationException("cancelled")
        var mapperInvoked = false

        val thrown = runCatching {
            liftrixCatching(
                errorMapper = {
                    mapperInvoked = true
                    LiftrixError.UnknownError(it.message.orEmpty())
                }
            ) {
                throw cancellationException
            }
        }.exceptionOrNull()

        assertSame(cancellationException, thrown)
        assertFalse(mapperInvoked)
    }

    @Test
    fun `liftrixCatching maps ordinary failures`() {
        val sourceException = IllegalStateException("failed")
        val mappedError = LiftrixError.UnknownError("mapped")

        val result = liftrixCatching(
            errorMapper = { throwable ->
                assertSame(sourceException, throwable)
                mappedError
            }
        ) {
            throw sourceException
        }

        assertSame(mappedError, result.exceptionOrNull())
    }
}
