@file:OptIn(ExperimentalUnsignedTypes::class)

package dev.pon.fractionalindexing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FractionalIndexGeneratorRebalanceContractTest {

    private data class InvalidCase(
        val count: Int,
        val lowerEndpoint: FractionalIndex?,
        val upperEndpoint: FractionalIndex?,
        val expectedMessage: String,
    )

    private val invalidCases: List<InvalidCase> by lazy {
        val lower = FractionalIndex.default().before()
        val upper = FractionalIndex.default().after()
        val default = FractionalIndex.default()
        listOf(
            InvalidCase(12, upper, lower, "lowerEndpoint must be before upperEndpoint"),
            InvalidCase(3, default, default, "lowerEndpoint and upperEndpoint must define a valid range for count"),
            InvalidCase(1, lower, upper, "lowerEndpoint and upperEndpoint must define a valid range for count"),
            InvalidCase(-1, null, null, "count must be non-negative"),
        )
    }

    @Test
    fun rebalance_withInvalidInputs_returnsFailure() {
        for (case in invalidCases) {
            val result = FractionalIndexGenerator.rebalance(
                count = case.count,
                lowerEndpoint = case.lowerEndpoint,
                upperEndpoint = case.upperEndpoint,
            )
            assertTrue(result.isFailure, "Expected failure for $case")
            assertEquals(case.expectedMessage, result.exceptionOrNull()?.message, "Wrong message for $case")
        }
    }

    @Test
    fun rebalanceOrThrow_withInvalidInputs_throws() {
        for (case in invalidCases) {
            assertFailsWith<IllegalArgumentException>("Expected throw for $case") {
                FractionalIndexGenerator.rebalanceOrThrow(
                    count = case.count,
                    lowerEndpoint = case.lowerEndpoint,
                    upperEndpoint = case.upperEndpoint,
                )
            }
        }
    }

    private data class ParityCase(
        val count: Int,
        val lowerEndpoint: FractionalIndex?,
        val upperEndpoint: FractionalIndex?,
    )

    @Test
    fun rebalance_andRebalanceOrThrow_withValidInputs_returnSameSequence() {
        val lower = FractionalIndex.default().before()
        val upper = FractionalIndex.default().after()

        for (case in listOf(
            ParityCase(16, lower, upper),
            ParityCase(16, lower, null),
            ParityCase(16, null, upper),
            ParityCase(16, null, null),
            ParityCase(1, lower, lower),
            ParityCase(0, lower, upper),
        )) {
            assertRebalanceParity(
                count = case.count,
                lowerEndpoint = case.lowerEndpoint,
                upperEndpoint = case.upperEndpoint,
            )
        }
    }

    @Test
    fun rebalance_withLargeCount_keepsOrderAndEndpoints() {
        val lower = FractionalIndex.default().before()
        val upper = FractionalIndex.default().after()
        val count = 10_000

        val generated = FractionalIndexGenerator.rebalanceOrThrow(
            count = count,
            lowerEndpoint = lower,
            upperEndpoint = upper,
        )

        assertEquals(count, generated.size)
        assertEquals(lower, generated.first())
        assertEquals(upper, generated.last())
        assertStrictlySorted(generated)
    }

    @Test
    fun rebalance_withSameInputs_isDeterministic() {
        val lower = FractionalIndex.default().before()
        val upper = FractionalIndex.default().after()
        val count = 180

        val firstRebalanced = FractionalIndexGenerator.rebalanceOrThrow(
            count = count,
            lowerEndpoint = lower,
            upperEndpoint = upper,
        )
        val secondRebalanced = FractionalIndexGenerator.rebalanceOrThrow(
            count = count,
            lowerEndpoint = lower,
            upperEndpoint = upper,
        )

        assertEquals(firstRebalanced, secondRebalanced)
    }

    @Test
    fun rebalance_withIdenticalEndpointsAndCountOne_returnsSingleEndpoint() {
        val index = FractionalIndex.default()

        val result = FractionalIndexGenerator.rebalance(
            count = 1,
            lowerEndpoint = index,
            upperEndpoint = index,
        ).getOrThrow()

        assertEquals(listOf(index), result)
    }

    @Test
    fun rebalance_withLowerOnly_generatesSortedKeysStartingAtLower() {
        val lower = FractionalIndex.default()

        val generated = FractionalIndexGenerator.rebalance(
            count = 10,
            lowerEndpoint = lower,
            upperEndpoint = null,
        ).getOrThrow()

        assertEquals(10, generated.size)
        assertEquals(lower, generated.first())
        assertStrictlySorted(generated)
    }

    @Test
    fun rebalance_withUpperOnly_generatesSortedKeysEndingAtUpper() {
        val upper = FractionalIndex.default()

        val generated = FractionalIndexGenerator.rebalance(
            count = 10,
            lowerEndpoint = null,
            upperEndpoint = upper,
        ).getOrThrow()

        assertEquals(10, generated.size)
        assertEquals(upper, generated.last())
        assertStrictlySorted(generated)
    }

    @Test
    fun rebalance_withoutEndpoints_startsFromDefaultAndAscending() {
        val generated = FractionalIndexGenerator.rebalance(
            count = 5,
            lowerEndpoint = null,
            upperEndpoint = null,
        ).getOrThrow()

        assertEquals(5, generated.size)
        assertEquals(FractionalIndex.default(), generated.first())
        assertStrictlySorted(generated)
    }

    @Test
    fun rebalance_withZeroCount_returnsEmptyList() {
        val generated = FractionalIndexGenerator.rebalance(
            count = 0,
            lowerEndpoint = FractionalIndex.default(),
            upperEndpoint = null,
        ).getOrThrow()

        assertTrue(generated.isEmpty())
    }

    @Test
    fun rebalance_withZeroCountAndIdenticalEndpoints_returnsEmptyList() {
        val index = FractionalIndex.default()
        val result = FractionalIndexGenerator.rebalance(
            count = 0,
            lowerEndpoint = index,
            upperEndpoint = index,
        ).getOrThrow()

        assertTrue(result.isEmpty())
    }

    @Test
    fun rebalanceOrThrow_withZeroCountAndIdenticalEndpoints_returnsEmptyList() {
        val index = FractionalIndex.default()
        val result = FractionalIndexGenerator.rebalanceOrThrow(
            count = 0,
            lowerEndpoint = index,
            upperEndpoint = index,
        )

        assertTrue(result.isEmpty())
    }
}
