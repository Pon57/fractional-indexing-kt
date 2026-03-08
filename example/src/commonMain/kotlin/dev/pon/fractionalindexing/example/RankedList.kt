package dev.pon.fractionalindexing.example

import dev.pon.fractionalindexing.FractionalIndex
import dev.pon.fractionalindexing.FractionalIndexGenerator
import dev.pon.fractionalindexing.after
import dev.pon.fractionalindexing.before
import dev.pon.fractionalindexing.between

data class RankedItem(
    val id: String,
    val label: String,
    val key: FractionalIndex,
)

data class MoveResult(
    val id: String,
    val label: String,
    val oldKey: FractionalIndex,
    val newKey: FractionalIndex,
    val newIndex: Int,
)

data class RebalanceChange(
    val id: String,
    val label: String,
    val oldKey: FractionalIndex,
    val newKey: FractionalIndex,
)

enum class RebalanceMode {
    ALL,
    WITHIN_CURRENT_RANGE,
}

data class RebalanceResult(
    val mode: RebalanceMode,
    val lowerEndpoint: FractionalIndex?,
    val upperEndpoint: FractionalIndex?,
    val changes: List<RebalanceChange>,
)

enum class SortDirection {
    ASCENDING,
    DESCENDING,
}

class RankedList(
    labels: List<String>,
) {
    private val initialLabels: List<String> = labels.toList()
    private val rankedItems: MutableList<RankedItem> = buildInitialItems(initialLabels).toMutableList()
    private var currentSortDirection: SortDirection = SortDirection.ASCENDING
    private var nextItemNumber: Int = rankedItems.size + 1

    fun items(): List<RankedItem> = rankedItems.toList()

    fun sortDirection(): SortDirection = currentSortDirection

    fun sortByKey(direction: SortDirection) {
        currentSortDirection = direction
        val comparator = when (direction) {
            SortDirection.ASCENDING -> compareBy<RankedItem> { it.key }
            SortDirection.DESCENDING -> compareByDescending<RankedItem> { it.key }
        }
        rankedItems.sortWith(comparator)
    }

    fun addItem(label: String): RankedItem {
        val newItem = RankedItem(
            id = "item-$nextItemNumber",
            label = label,
            key = keyForAppend(),
        )
        nextItemNumber += 1
        rankedItems.add(newItem)
        return newItem
    }

    fun rebalanceAll(): RebalanceResult = rebalance(
        mode = RebalanceMode.ALL,
        lowerEndpoint = null,
        upperEndpoint = null,
    )

    fun rebalanceWithinCurrentRange(): RebalanceResult? {
        if (rankedItems.isEmpty()) {
            return null
        }

        val orderedByKey = rankedItems.sortedBy { it.key }
        val lowerEndpoint = orderedByKey.first().key
        val upperEndpoint = orderedByKey.last().key
        return rebalance(
            mode = RebalanceMode.WITHIN_CURRENT_RANGE,
            lowerEndpoint = lowerEndpoint,
            upperEndpoint = upperEndpoint,
        )
    }

    fun reset() {
        rankedItems.clear()
        rankedItems.addAll(buildInitialItems(initialLabels))
        currentSortDirection = SortDirection.ASCENDING
        nextItemNumber = rankedItems.size + 1
    }

    fun moveByDropIndex(fromIndex: Int, dropIndex: Int): MoveResult? {
        if (fromIndex !in rankedItems.indices) {
            return null
        }

        val insertIndex = normalizeDropIndex(
            fromIndex = fromIndex,
            dropIndex = dropIndex,
            size = rankedItems.size,
        ) ?: return null

        return moveToIndex(fromIndex, insertIndex)
    }

    private fun moveToIndex(fromIndex: Int, toIndex: Int): MoveResult? {
        if (fromIndex !in rankedItems.indices || toIndex !in rankedItems.indices || fromIndex == toIndex) {
            return null
        }

        val updatedOrder = rankedItems.toMutableList()
        val moved = updatedOrder.removeAt(fromIndex)
        updatedOrder.add(toIndex, moved)

        val newKey = keyForIndex(index = toIndex, orderedItems = updatedOrder)
        val updatedMovedItem = moved.copy(key = newKey)
        updatedOrder[toIndex] = updatedMovedItem

        rankedItems.clear()
        rankedItems.addAll(updatedOrder)

        return MoveResult(
            id = updatedMovedItem.id,
            label = updatedMovedItem.label,
            oldKey = moved.key,
            newKey = updatedMovedItem.key,
            newIndex = toIndex,
        )
    }

    private fun rebalance(
        mode: RebalanceMode,
        lowerEndpoint: FractionalIndex?,
        upperEndpoint: FractionalIndex?,
    ): RebalanceResult {
        if (rankedItems.isEmpty()) {
            return RebalanceResult(
                mode = mode,
                lowerEndpoint = lowerEndpoint,
                upperEndpoint = upperEndpoint,
                changes = emptyList(),
            )
        }

        val generatedKeys = FractionalIndexGenerator.rebalanceOrThrow(
            count = rankedItems.size,
            lowerEndpoint = lowerEndpoint,
            upperEndpoint = upperEndpoint,
        )
        val orderedKeys = when (currentSortDirection) {
            SortDirection.ASCENDING -> generatedKeys
            SortDirection.DESCENDING -> generatedKeys.asReversed()
        }
        val updatedItems = rankedItems.mapIndexed { index, item ->
            item.copy(key = orderedKeys[index])
        }

        return commitRebalance(
            mode = mode,
            lowerEndpoint = lowerEndpoint,
            upperEndpoint = upperEndpoint,
            updatedItems = updatedItems,
        )
    }

    private fun commitRebalance(
        mode: RebalanceMode,
        lowerEndpoint: FractionalIndex?,
        upperEndpoint: FractionalIndex?,
        updatedItems: List<RankedItem>,
    ): RebalanceResult {
        val changes = rankedItems.zip(updatedItems).mapNotNull { (before, after) ->
            if (before.key == after.key) {
                null
            } else {
                RebalanceChange(
                    id = before.id,
                    label = before.label,
                    oldKey = before.key,
                    newKey = after.key,
                )
            }
        }

        rankedItems.clear()
        rankedItems.addAll(updatedItems)

        return RebalanceResult(
            mode = mode,
            lowerEndpoint = lowerEndpoint,
            upperEndpoint = upperEndpoint,
            changes = changes,
        )
    }

    private fun keyForAppend(): FractionalIndex {
        if (rankedItems.isEmpty()) {
            return FractionalIndex.default()
        }
        val lastKey = rankedItems.last().key
        return when (currentSortDirection) {
            SortDirection.ASCENDING -> lastKey.after()
            SortDirection.DESCENDING -> lastKey.before()
        }
    }

    private fun keyForIndex(index: Int, orderedItems: List<RankedItem>): FractionalIndex {
        return when {
            orderedItems.size == 1 -> FractionalIndex.default()
            index == 0 -> when (currentSortDirection) {
                SortDirection.ASCENDING -> orderedItems[1].key.before()
                SortDirection.DESCENDING -> orderedItems[1].key.after()
            }

            index == orderedItems.lastIndex -> when (currentSortDirection) {
                SortDirection.ASCENDING -> orderedItems[index - 1].key.after()
                SortDirection.DESCENDING -> orderedItems[index - 1].key.before()
            }

            else -> orderedItems[index - 1].key.between(orderedItems[index + 1].key).getOrThrow()
        }
    }

    private fun normalizeDropIndex(fromIndex: Int, dropIndex: Int, size: Int): Int? {
        val clampedDropIndex = dropIndex.coerceIn(0, size)
        val adjustedIndex = if (clampedDropIndex > fromIndex) clampedDropIndex - 1 else clampedDropIndex
        val boundedIndex = adjustedIndex.coerceIn(0, size - 1)
        return if (boundedIndex == fromIndex) null else boundedIndex
    }

    private fun buildInitialItems(labels: List<String>): List<RankedItem> {
        var key = FractionalIndex.default()
        return labels.mapIndexed { index, label ->
            if (index > 0) {
                key = key.after()
            }
            RankedItem(
                id = "item-${index + 1}",
                label = label,
                key = key,
            )
        }
    }
}
