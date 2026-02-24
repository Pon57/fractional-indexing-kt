package dev.pon.fractionalindexing.example

import dev.pon.fractionalindexing.FractionalIndex
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import java.awt.FlowLayout
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.util.Locale
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.JButton
import javax.swing.DefaultListCellRenderer
import javax.swing.DefaultListModel
import javax.swing.DropMode
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSplitPane
import javax.swing.JTextArea
import javax.swing.ListSelectionModel
import javax.swing.SwingUtilities
import javax.swing.TransferHandler

fun main() {
    SwingUtilities.invokeLater {
        FractionalIndexExampleFrame().isVisible = true
    }
}

private class FractionalIndexExampleFrame : JFrame("Fractional Indexing Drag-and-Drop Example") {
    private val rankedList = RankedList(
        labels = listOf(
            "Backlog",
            "Plan",
            "Develop",
            "Review",
            "Release",
            "Follow-up",
        ),
    )
    private val model = DefaultListModel<RankedItem>()
    private val list = JList(model)
    private val eventLog = JTextArea()
    private val sortButton = JButton()
    private val addButton = JButton("Add item")
    private val resetButton = JButton("Reset")
    private var nextAddedItemNumber: Int = 1

    init {
        defaultCloseOperation = EXIT_ON_CLOSE
        minimumSize = Dimension(980, 620)
        setLocationRelativeTo(null)

        layout = BorderLayout()
        add(createContent(), BorderLayout.CENTER)
        updateSortButtonLabel()
        refreshList()
        log("Drag items to reorder. Key updates are logged below.")
    }

    private fun createContent(): JComponent {
        list.selectionMode = ListSelectionModel.SINGLE_SELECTION
        list.visibleRowCount = -1
        list.dragEnabled = true
        list.dropMode = DropMode.INSERT
        list.cellRenderer = RankedItemRenderer()
        list.transferHandler = RankedItemTransferHandler(list) { fromIndex, dropIndex ->
            val move = rankedList.moveByDropIndex(fromIndex, dropIndex) ?: return@RankedItemTransferHandler
            refreshList(move.newIndex)
            log(
                "${move.label}: ${move.oldKey.toHexString()} (${keyByteLength(move.oldKey)}B) -> " +
                    "${move.newKey.toHexString()} (${keyByteLength(move.newKey)}B) (new index=${move.newIndex})",
            )
        }

        eventLog.isEditable = false
        eventLog.font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        eventLog.lineWrap = true
        eventLog.wrapStyleWord = true

        val listPane = JScrollPane(list)
        val logPane = JScrollPane(eventLog)
        logPane.minimumSize = Dimension(220, 120)
        val split = JSplitPane(JSplitPane.VERTICAL_SPLIT, listPane, logPane)
        split.resizeWeight = 0.72

        sortButton.isFocusable = false
        sortButton.addActionListener {
            val nextDirection = when (rankedList.sortDirection()) {
                SortDirection.ASCENDING -> SortDirection.DESCENDING
                SortDirection.DESCENDING -> SortDirection.ASCENDING
            }
            rankedList.sortByKey(nextDirection)
            updateSortButtonLabel()
            refreshList()
            log("Sorted by key: ${nextDirection.name.lowercase(Locale.ROOT)}")
        }

        addButton.isFocusable = false
        addButton.addActionListener {
            val label = "Added-$nextAddedItemNumber"
            nextAddedItemNumber += 1
            val added = rankedList.addItem(label)
            refreshList(rankedList.items().lastIndex)
            log(
                "Added ${added.label}: key=${added.key.toHexString()} (${keyByteLength(added.key)}B, " +
                    "${rankedList.sortDirection().name.lowercase(Locale.ROOT)})",
            )
        }

        resetButton.isFocusable = false
        resetButton.addActionListener {
            rankedList.reset()
            nextAddedItemNumber = 1
            updateSortButtonLabel()
            refreshList()
            log("Reset to initial items.")
        }

        val controls = JPanel(FlowLayout(FlowLayout.LEFT, 8, 4)).apply {
            border = BorderFactory.createEmptyBorder(0, 4, 0, 0)
            add(sortButton)
            add(addButton)
            add(resetButton)
            add(Box.createHorizontalGlue())
        }

        return JPanel(BorderLayout()).apply {
            add(controls, BorderLayout.NORTH)
            add(split, BorderLayout.CENTER)
        }
    }

    private fun refreshList(selectedIndex: Int? = null) {
        model.clear()
        rankedList.items().forEach(model::addElement)
        val clamped = selectedIndex?.coerceIn(0, model.size - 1) ?: -1
        if (clamped >= 0) {
            list.selectedIndex = clamped
        }
    }

    private fun log(message: String) {
        if (eventLog.text.isNotEmpty()) {
            eventLog.append("\n")
        }
        eventLog.append(message)
        eventLog.caretPosition = eventLog.document.length
    }

    private fun updateSortButtonLabel() {
        val label = when (rankedList.sortDirection()) {
            SortDirection.ASCENDING -> "Sort: ascending"
            SortDirection.DESCENDING -> "Sort: descending"
        }
        sortButton.text = label
    }

    private fun keyByteLength(key: FractionalIndex): Int = key.getByteSize()
}

private class RankedItemRenderer : DefaultListCellRenderer() {
    private val monoFont = Font(Font.MONOSPACED, Font.PLAIN, 13)

    override fun getListCellRendererComponent(
        list: JList<*>,
        value: Any?,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean,
    ): Component {
        val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
        val item = value as RankedItem
        val keyHex = item.key.toHexString()
        text = "${index + 1}. ${item.label.padEnd(12)} key=$keyHex bytes=${item.key.getByteSize()}"
        font = monoFont
        return component
    }
}

private class RankedItemTransferHandler(
    private val list: JList<RankedItem>,
    private val onMove: (fromIndex: Int, dropIndex: Int) -> Unit,
) : TransferHandler() {
    private var fromIndex: Int = -1

    override fun getSourceActions(c: JComponent): Int = MOVE

    override fun createTransferable(c: JComponent): Transferable {
        fromIndex = list.selectedIndex
        return StringSelection(fromIndex.toString())
    }

    override fun canImport(support: TransferSupport): Boolean {
        return support.component === list &&
            support.isDrop &&
            support.isDataFlavorSupported(DataFlavor.stringFlavor)
    }

    override fun importData(support: TransferSupport): Boolean {
        if (!canImport(support) || fromIndex < 0) {
            return false
        }
        val dropLocation = support.dropLocation as? JList.DropLocation ?: return false
        onMove(fromIndex, dropLocation.index)
        return true
    }

    override fun exportDone(source: JComponent, data: Transferable?, action: Int) {
        fromIndex = -1
    }
}
