package com.example.core.command

import com.example.core.geometry.Vector3
import com.example.core.geometry.OrientedBoundingBox

interface EditorCommand {
    val description: String
    fun execute(): Boolean
    fun undo(): Boolean
    fun redo(): Boolean = execute()
}

data class CabinetEntityModel(
    val id: String,
    val name: String,
    val modelAssetPath: String,
    var position: Vector3,
    var rotationY: Float = 0f,
    var width: Float = 0.60f,
    var height: Float = 0.82f,
    var depth: Float = 0.60f,
    var materialId: String = "wood_oak_natural",
    val isLocked: Boolean = false
) {
    fun toOBB(): OrientedBoundingBox {
        return OrientedBoundingBox(
            center = Vector3(position.x, position.y + height * 0.5f, position.z),
            halfExtents = Vector3(width * 0.5f, height * 0.5f, depth * 0.5f),
            rotationYDegrees = rotationY
        )
    }

    fun copyEntity(): CabinetEntityModel {
        return copy(
            position = position.copy(),
            rotationY = rotationY,
            width = width,
            height = height,
            depth = depth
        )
    }
}

class CommandManager(private val maxHistorySize: Int = 50) {
    private val undoStack = ArrayDeque<EditorCommand>()
    private val redoStack = ArrayDeque<EditorCommand>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun executeCommand(command: EditorCommand): Boolean {
        val success = command.execute()
        if (success) {
            undoStack.addLast(command)
            redoStack.clear()
            if (undoStack.size > maxHistorySize) {
                undoStack.removeFirst()
            }
        }
        return success
    }

    fun undo(): Boolean {
        if (!canUndo) return false
        val cmd = undoStack.removeLast()
        val success = cmd.undo()
        if (success) {
            redoStack.addLast(cmd)
        } else {
            undoStack.addLast(cmd)
        }
        return success
    }

    fun redo(): Boolean {
        if (!canRedo) return false
        val cmd = redoStack.removeLast()
        val success = cmd.redo()
        if (success) {
            undoStack.addLast(cmd)
        } else {
            redoStack.addLast(cmd)
        }
        return success
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }
}

class AddCabinetCommand(
    private val sceneEntities: MutableList<CabinetEntityModel>,
    private val entityToAdd: CabinetEntityModel,
    private val onSceneUpdated: (List<CabinetEntityModel>) -> Unit
) : EditorCommand {
    override val description: String = "إضافة وحدة: ${entityToAdd.name}"

    override fun execute(): Boolean {
        sceneEntities.add(entityToAdd)
        onSceneUpdated(sceneEntities.toList())
        return true
    }

    override fun undo(): Boolean {
        val removed = sceneEntities.removeAll { it.id == entityToAdd.id }
        if (removed) {
            onSceneUpdated(sceneEntities.toList())
        }
        return removed
    }
}

class RemoveCabinetCommand(
    private val sceneEntities: MutableList<CabinetEntityModel>,
    private val entityToRemove: CabinetEntityModel,
    private val onSceneUpdated: (List<CabinetEntityModel>) -> Unit
) : EditorCommand {
    private var removedIndex: Int = -1
    override val description: String = "حذف وحدة: ${entityToRemove.name}"

    override fun execute(): Boolean {
        removedIndex = sceneEntities.indexOfFirst { it.id == entityToRemove.id }
        if (removedIndex >= 0) {
            sceneEntities.removeAt(removedIndex)
            onSceneUpdated(sceneEntities.toList())
            return true
        }
        return false
    }

    override fun undo(): Boolean {
        if (removedIndex in 0..sceneEntities.size) {
            sceneEntities.add(removedIndex, entityToRemove)
        } else {
            sceneEntities.add(entityToRemove)
        }
        onSceneUpdated(sceneEntities.toList())
        return true
    }
}
