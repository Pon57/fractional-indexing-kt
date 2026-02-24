package dev.pon.fractionalindexing.example

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RankedListTest {
    @Test
    fun addItem_inAscendingOrder_appendsItemAndKeepsStrictOrder() {
        val ranked = RankedList(
            labels = listOf("A", "B", "C"),
        )

        val added = ranked.addItem("X")
        val after = ranked.items()

        assertEquals("X", after.last().label)
        assertEquals(added.id, after.last().id)
        assertTrue(after[after.lastIndex - 1].key < after.last().key)
        assertStrictlyOrderedByDirection(after, SortDirection.ASCENDING)
    }

    @Test
    fun addItem_inDescendingOrder_appendsItemAndKeepsStrictOrder() {
        val ranked = RankedList(
            labels = listOf("A", "B", "C"),
        )
        ranked.sortByKey(SortDirection.DESCENDING)

        val added = ranked.addItem("X")
        val after = ranked.items()

        assertEquals("X", after.last().label)
        assertEquals(added.id, after.last().id)
        assertTrue(after[after.lastIndex - 1].key > after.last().key)
        assertStrictlyOrderedByDirection(after, SortDirection.DESCENDING)
    }

    @Test
    fun reset_restoresInitialState() {
        val labels = listOf("A", "B", "C")
        val ranked = RankedList(labels = labels)
        ranked.sortByKey(SortDirection.DESCENDING)
        ranked.addItem("X")
        ranked.moveByDropIndex(fromIndex = 0, dropIndex = 4)

        ranked.reset()
        val afterReset = ranked.items()
        val expected = RankedList(labels = labels).items()

        assertEquals(SortDirection.ASCENDING, ranked.sortDirection())
        assertEquals(expected.map { it.label }, afterReset.map { it.label })
        assertEquals(expected.map { it.key }, afterReset.map { it.key })
    }

    @Test
    fun moveByDropIndex_withDropIndexSize_movesToTailAndPreservesStrictKeyOrder() {
        val ranked = RankedList(
            labels = listOf("A", "B", "C", "D"),
        )
        val before = ranked.items()

        val moveResult = ranked.moveByDropIndex(
            fromIndex = 0,
            dropIndex = 4,
        )

        requireNotNull(moveResult)
        val after = ranked.items()

        assertEquals("A", after.last().label)
        assertEquals(after.lastIndex, moveResult.newIndex)
        assertNotEquals(before.first().key, moveResult.newKey)
        assertStrictlyOrderedByDirection(after, SortDirection.ASCENDING)
    }

    @Test
    fun moveByDropIndex_clampsNegativeDropIndexToHead() {
        val ranked = RankedList(
            labels = listOf("A", "B", "C", "D"),
        )

        val moveResult = ranked.moveByDropIndex(
            fromIndex = 2,
            dropIndex = -1,
        )

        requireNotNull(moveResult)
        val after = ranked.items()

        assertEquals("C", after.first().label)
        assertEquals(0, moveResult.newIndex)
        assertStrictlyOrderedByDirection(after, SortDirection.ASCENDING)
    }

    @Test
    fun moveByDropIndex_clampsLargeDropIndexToTail() {
        val ranked = RankedList(
            labels = listOf("A", "B", "C", "D"),
        )

        val moveResult = ranked.moveByDropIndex(
            fromIndex = 1,
            dropIndex = 999,
        )

        requireNotNull(moveResult)
        val after = ranked.items()

        assertEquals("B", after.last().label)
        assertEquals(after.lastIndex, moveResult.newIndex)
        assertStrictlyOrderedByDirection(after, SortDirection.ASCENDING)
    }

    @Test
    fun sortByKey_descending_reordersListByKey() {
        val ranked = RankedList(
            labels = listOf("A", "B", "C", "D"),
        )

        ranked.sortByKey(SortDirection.DESCENDING)
        val after = ranked.items()

        assertEquals(SortDirection.DESCENDING, ranked.sortDirection())
        assertEquals("D", after.first().label)
        assertEquals("A", after.last().label)
        assertStrictlyOrderedByDirection(after, SortDirection.DESCENDING)
    }

    @Test
    fun moveByDropIndex_inDescendingOrder_preservesDescendingKeyOrder() {
        val ranked = RankedList(
            labels = listOf("A", "B", "C", "D"),
        )
        ranked.sortByKey(SortDirection.DESCENDING)

        val moveResult = ranked.moveByDropIndex(
            fromIndex = 0,
            dropIndex = 4,
        )

        requireNotNull(moveResult)
        val after = ranked.items()

        assertEquals("D", after.last().label)
        assertEquals(after.lastIndex, moveResult.newIndex)
        assertStrictlyOrderedByDirection(after, SortDirection.DESCENDING)
    }

    @Test
    fun moveByDropIndex_returnsNullWhenDropResolvesToSameIndex() {
        val ranked = RankedList(
            labels = listOf("A", "B", "C", "D"),
        )
        val before = ranked.items()

        val moveResult = ranked.moveByDropIndex(
            fromIndex = 1,
            dropIndex = 2,
        )

        assertNull(moveResult)
        assertEquals(before, ranked.items())
    }

    @Test
    fun moveByDropIndex_returnsNullWhenFromIndexIsOutOfRange() {
        val ranked = RankedList(
            labels = listOf("A", "B", "C", "D"),
        )
        val before = ranked.items()

        assertNull(ranked.moveByDropIndex(fromIndex = -1, dropIndex = 0))
        assertNull(ranked.moveByDropIndex(fromIndex = 4, dropIndex = 0))
        assertEquals(before, ranked.items())
    }

    private fun assertStrictlyOrderedByDirection(items: List<RankedItem>, direction: SortDirection) {
        for (i in 1 until items.size) {
            when (direction) {
                SortDirection.ASCENDING -> assertTrue(items[i - 1].key < items[i].key)
                SortDirection.DESCENDING -> assertTrue(items[i - 1].key > items[i].key)
            }
        }
    }
}
