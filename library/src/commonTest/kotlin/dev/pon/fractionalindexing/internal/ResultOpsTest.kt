package dev.pon.fractionalindexing.internal

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class ResultOpsTest {
    @Test
    fun invalidArgumentToResult_convertsExpectedValidationFailure() {
        val result = invalidArgumentToResult<Unit> {
            throw IllegalArgumentException("invalid input")
        }

        assertIs<IllegalArgumentException>(result.exceptionOrNull())
    }

    @Test
    fun invalidArgumentToResult_doesNotCatchFatalErrors() {
        assertFailsWith<AssertionError> {
            invalidArgumentToResult<Unit> {
                throw AssertionError("fatal")
            }
        }
    }
}
