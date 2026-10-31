package com.example.core.command

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Immutable snapshot of a single command in the Undo or Redo stack for UI timeline display.
 */
data class CommandHistoryEntry(
    val stepsFromCurrent: Int,
    val title: String,
    val description: String,
    val category: CommandCategory
)

/**
 * Reactive Undo/Redo UI state exposed to the 2D floor plan component and studio toolbar.
 */
data class UndoRedoState(
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val lastActionTitle: String = "",
    val lastActionDescription: String = "",
    val nextRedoTitle: String = "",
    val nextRedoDescription: String = "",
    val undoCount: Int = 0,
    val redoCount: Int = 0,
    val undoStackEntries: List<CommandHistoryEntry> = emptyList(),
    val redoStackEntries: List<CommandHistoryEntry> = emptyList()
)

/**
 * Command history stack manager implementing the GoF Command Pattern.
 * Provides single-step and multi-step undo/redo capability for 2D floor plan furniture
 * placements, 1mm drag/nudge movements, rotations, deletions, and room layout modifications.
 */
class CommandHistory(private val maxCapacity: Int = 100) {

    private val undoStack = mutableListOf<Command>()
    private val redoStack = mutableListOf<Command>()

    private val _state = MutableStateFlow(UndoRedoState())
    val state: StateFlow<UndoRedoState> = _state.asStateFlow()

    /**
     * Executes [command], pushes it onto the undo stack, and clears the redo stack.
     */
    fun execute(command: Command) {
        command.execute()
        pushUndo(command)
    }

    /**
     * Records a [command] whose state mutation has already been applied interactively
     * (e.g. live 2D floor plan dragging), pushing it onto the undo stack and clearing redo.
     */
    fun recordExecuted(command: Command) {
        pushUndo(command)
    }

    private fun pushUndo(command: Command) {
        undoStack.add(command)
        if (undoStack.size > maxCapacity) {
            undoStack.removeAt(0)
        }
        redoStack.clear()
        updateState()
    }

    /**
     * Reverts the most recent command on the undo stack and moves it to the redo stack.
     */
    fun undo(): Boolean {
        if (undoStack.isEmpty()) return false
        val command = undoStack.removeAt(undoStack.lastIndex)
        command.undo()
        redoStack.add(command)
        updateState()
        return true
    }

    /**
     * Re-applies the most recent undone command on the redo stack.
     */
    fun redo(): Boolean {
        if (redoStack.isEmpty()) return false
        val command = redoStack.removeAt(redoStack.lastIndex)
        command.redo()
        undoStack.add(command)
        updateState()
        return true
    }

    /**
     * Reverts up to [steps] commands sequentially in LIFO order.
     */
    fun undoSteps(steps: Int): Int {
        val targetSteps = steps.coerceAtLeast(0)
        var completed = 0
        while (completed < targetSteps && undoStack.isNotEmpty()) {
            val command = undoStack.removeAt(undoStack.lastIndex)
            command.undo()
            redoStack.add(command)
            completed++
        }
        if (completed > 0) {
            updateState()
        }
        return completed
    }

    /**
     * Re-applies up to [steps] commands sequentially from the redo stack.
     */
    fun redoSteps(steps: Int): Int {
        val targetSteps = steps.coerceAtLeast(0)
        var completed = 0
        while (completed < targetSteps && redoStack.isNotEmpty()) {
            val command = redoStack.removeAt(redoStack.lastIndex)
            command.redo()
            undoStack.add(command)
            completed++
        }
        if (completed > 0) {
            updateState()
        }
        return completed
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
        updateState()
    }

    private fun updateState() {
        val lastUndo = undoStack.lastOrNull()
        val lastRedo = redoStack.lastOrNull()

        // Most recent undo commands first (1 step back, 2 steps back, ...)
        val undoEntries = undoStack.asReversed().take(25).mapIndexed { idx, cmd ->
            CommandHistoryEntry(
                stepFromCurrent = idx + 1,
                title = cmd.title,
                description = cmd.description,
                category = cmd.category
            )
        }

        // Next redo commands first (1 step forward, 2 steps forward, ...)
        val redoEntries = redoStack.asReversed().take(25).mapIndexed { idx, cmd ->
            CommandHistoryEntry(
                stepFromCurrent = idx + 1,
                title = cmd.title,
                description = cmd.description,
                category = cmd.category
            )
        }

        _state.value = UndoRedoState(
            canUndo = undoStack.isNotEmpty(),
            canRedo = redoStack.isNotEmpty(),
            lastActionTitle = lastUndo?.title ?: "",
            lastActionDescription = lastUndo?.description ?: "",
            nextRedoTitle = lastRedo?.title ?: "",
            nextRedoDescription = lastRedo?.description ?: "",
            undoCount = undoStack.size,
            redoCount = redoStack.size,
            undoStackEntries = undoEntries,
            redoStackEntries = redoEntries
        )
    }

    private fun CommandHistoryEntry(
        stepFromCurrent: Int,
        title: String,
        description: String,
        category: CommandCategory
    ): com.example.core.command.CommandHistoryEntry = com.example.core.command.CommandHistoryEntry(
        stepsFromCurrent = stepFromCurrent,
        title = title,
        description = description,
        category = category
    )
}
