package dev.pon.fractionalindexing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@OptIn(ExperimentalUnsignedTypes::class)
class FractionalIndexExtensionsTest {
    @Test
    fun beforeAndAfter_delegateToGenerator() {
        val default = FractionalIndex.default()

        assertEquals("8180", default.after().toHexString())
        assertEquals("7f80", default.before().toHexString())
    }

    @Test
    fun between_acceptsUnorderedBounds() {
        val lower = FractionalIndex.default().before()
        val upper = FractionalIndex.default()

        val forward = lower.between(upper).getOrThrow()
        val reversed = upper.between(lower).getOrThrow()

        assertEquals(forward, reversed)
    }

    @Test
    fun betweenOrThrow_acceptsUnorderedBounds() {
        val lower = FractionalIndex.default().before()
        val upper = FractionalIndex.default()

        val forward = lower.betweenOrThrow(upper)
        val reversed = upper.betweenOrThrow(lower)

        assertEquals(forward, reversed)
    }

    @Test
    fun betweenOrThrow_withIdenticalBounds_throws() {
        val index = FractionalIndex.default()

        assertFailsWith<IllegalArgumentException> {
            index.betweenOrThrow(index)
        }
    }
}
